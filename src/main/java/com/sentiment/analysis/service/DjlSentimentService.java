package com.sentiment.analysis.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.sentiment.analysis.config.ModelProperties;
import com.sentiment.analysis.dto.LanguageInfo;
import com.sentiment.analysis.dto.SentimentResult;
import com.sentiment.analysis.exception.ModelInferenceException;
import com.sentiment.analysis.exception.ModelNotReadyException;
import com.sentiment.analysis.model.enums.Sentiment;
import com.sentiment.analysis.util.MathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the exported ONNX model with DJL.
 *
 * Pipeline: clean text -> tokenize (tokenizer.json) -> ONNX Runtime -> logits -> softmax (Java)
 * -> labels from config.json (id2label), so the label order always matches the model.
 *
 * If the model files are missing the application still starts; analyze() then throws
 * ModelNotReadyException and the admin dashboard shows "Not loaded".
 */
@Service
public class DjlSentimentService implements SentimentService {

    private static final Logger log = LoggerFactory.getLogger(DjlSentimentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelProperties props;
    private final PreprocessingService preprocessing;
    private final LanguageDetectionService languageDetection;

    private volatile ZooModel<String, float[]> model;
    private volatile HuggingFaceTokenizer tokenizer;
    private volatile List<String> labels = List.of();

    public DjlSentimentService(ModelProperties props,
                               PreprocessingService preprocessing,
                               LanguageDetectionService languageDetection) {
        this.props = props;
        this.preprocessing = preprocessing;
        this.languageDetection = languageDetection;
    }

    @PostConstruct
    void load() {
        Path dir = Paths.get(props.path()).toAbsolutePath().normalize();

        for (String required : List.of("model.onnx", "tokenizer.json", "config.json")) {
            if (!Files.isRegularFile(dir.resolve(required))) {
                log.warn("Model file '{}' not found in {}. Run python/export_model.py first. "
                        + "The application will start WITHOUT a model.", required, dir);
                return;
            }
        }

        try {
            List<String> loadedLabels = readLabels(dir.resolve("config.json"));

            this.tokenizer = HuggingFaceTokenizer.newInstance(dir.resolve("tokenizer.json"));

            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(dir)
                    .optModelName("model")                    // loads model.onnx
                    .optEngine("OnnxRuntime")
                    .optTranslator(new SentimentTranslator(tokenizer, props.maxLength()))
                    .build();

            this.labels = loadedLabels;
            this.model = criteria.loadModel();

            analyze("hello");   // warm-up: the first call is slow (native libraries, graph optimization)
            log.info("Sentiment model loaded from {} (labels in model order: {})", dir, labels);
        } catch (Exception e) {
            log.error("Failed to load the sentiment model from {}: {}", dir, e.toString(), e);
            close();
        }
    }

    /** Reads id2label from config.json, e.g. {"0":"negative","1":"neutral","2":"positive"}. */
    private static List<String> readLabels(Path configFile) throws IOException {
        JsonNode id2label = MAPPER.readTree(configFile.toFile()).get("id2label");
        if (id2label == null || !id2label.isObject() || id2label.size() == 0) {
            throw new IOException("config.json has no id2label section");
        }

        TreeMap<Integer, String> byIndex = new TreeMap<>();
        for (Iterator<String> it = id2label.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            byIndex.put(Integer.parseInt(key), id2label.get(key).asText());
        }
        if (byIndex.firstKey() != 0 || byIndex.lastKey() != byIndex.size() - 1) {
            throw new IOException("id2label keys must be 0.." + (byIndex.size() - 1) + " but were " + byIndex.keySet());
        }

        List<String> result = new ArrayList<>(byIndex.values());
        result.forEach(Sentiment::fromLabel);   // fail fast if a label is not negative/neutral/positive
        return result;
    }

    @Override
    public SentimentResult analyze(String text) {
        ZooModel<String, float[]> m = this.model;
        if (m == null) {
            throw new ModelNotReadyException(
                    "Sentiment model is not loaded. Check app.model.path and the startup log.");
        }

        String cleaned = preprocessing.clean(text);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Text is empty after cleaning");
        }
        LanguageInfo language = languageDetection.detect(cleaned);

        long start = System.nanoTime();
        float[] logits;
        // A Predictor is not thread-safe, so every call gets its own (cheap to create).
        try (Predictor<String, float[]> predictor = m.newPredictor()) {
            logits = predictor.predict(cleaned);
        } catch (TranslateException e) {
            throw new ModelInferenceException("Inference failed", e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<String> modelLabels = this.labels;
        if (logits.length != modelLabels.size()) {
            throw new ModelInferenceException("Model returned " + logits.length
                    + " scores but config.json defines " + modelLabels.size() + " labels", null);
        }

        double[] probs = MathUtils.softmax(logits);
        Map<Sentiment, Double> probabilities = new EnumMap<>(Sentiment.class);
        int bestIndex = 0;
        for (int i = 0; i < probs.length; i++) {
            probabilities.put(Sentiment.fromLabel(modelLabels.get(i)), probs[i]);
            if (probs[i] > probs[bestIndex]) {
                bestIndex = i;
            }
        }

        Sentiment best = Sentiment.fromLabel(modelLabels.get(bestIndex));
        double confidence = probs[bestIndex];
        return new SentimentResult(best, confidence, probabilities, language,
                confidence < props.lowConfidenceThreshold(), latencyMs, cleaned);
    }

    @Override
    public boolean isReady() {
        return model != null;
    }

    @Override
    public String modelPath() {
        return Paths.get(props.path()).toAbsolutePath().normalize().toString();
    }

    @PreDestroy
    void close() {
        ZooModel<String, float[]> m = this.model;
        this.model = null;
        if (m != null) {
            m.close();
        }
        HuggingFaceTokenizer t = this.tokenizer;
        this.tokenizer = null;
        if (t != null) {
            t.close();
        }
    }
}
