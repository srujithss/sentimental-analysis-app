package com.sentiment.analysis.startup;

import com.sentiment.analysis.dto.LanguageInfo;
import com.sentiment.analysis.dto.SentimentResult;
import com.sentiment.analysis.model.enums.Sentiment;
import com.sentiment.analysis.service.LanguageDetectionService;
import com.sentiment.analysis.service.PreprocessingService;
import com.sentiment.analysis.service.SentimentService;
import com.sentiment.analysis.service.TransliterationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the sample sentences (src/main/resources/samples/test-sentences.tsv) through the whole
 * pipeline at startup and logs predicted vs expected labels.
 * Disable with app.model.startup-check=false.
 */
@Component
@ConditionalOnProperty(prefix = "app.model", name = "startup-check", havingValue = "true", matchIfMissing = true)
public class ModelStartupCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelStartupCheck.class);

    private record Sample(Sentiment expected, String text, String language) {
    }

    private final SentimentService sentiment;
    private final PreprocessingService preprocessing;
    private final LanguageDetectionService languageDetection;
    private final TransliterationService transliteration;

    public ModelStartupCheck(SentimentService sentiment,
                             PreprocessingService preprocessing,
                             LanguageDetectionService languageDetection,
                             TransliterationService transliteration) {
        this.sentiment = sentiment;
        this.preprocessing = preprocessing;
        this.languageDetection = languageDetection;
        this.transliteration = transliteration;
    }

    @Override
    public void run(String... args) throws IOException {
        if (!sentiment.isReady()) {
            log.warn("Model startup check skipped: model is not loaded.");
            return;
        }

        List<Sample> samples = readSamples();
        if (samples.isEmpty()) {
            log.warn("Model startup check: no samples found.");
            return;
        }

        int[][] confusion = new int[Sentiment.values().length][Sentiment.values().length];
        int correct = 0;
        int nativeTotal = 0, nativeCorrect = 0;
        int latinTotal = 0, latinCorrect = 0;
        int translitTotal = 0, translitCorrect = 0;

        log.info("===== Model startup check ({} sentences) =====", samples.size());
        int n = 0;
        for (Sample s : samples) {
            String textToAnalyze = s.text();
            String tag = "";

            if (s.language() != null && transliteration.canTransliterate(s.language())) {
                String cleaned = preprocessing.clean(s.text());
                LanguageInfo detected = languageDetection.detect(cleaned);
                if (detected.isLatin()) {
                    textToAnalyze = transliteration.transliterate(cleaned, s.language());
                    tag = " [translit→" + s.language() + "]";
                }
            }

            SentimentResult r = sentiment.analyze(textToAnalyze);
            boolean ok = r.label() == s.expected();
            confusion[s.expected().ordinal()][r.label().ordinal()]++;

            if (ok) {
                correct++;
            }
            if (!tag.isEmpty()) {
                translitTotal++;
                if (ok) translitCorrect++;
            } else if (r.language().isLatin()) {
                latinTotal++;
                if (ok) latinCorrect++;
            } else {
                nativeTotal++;
                if (ok) nativeCorrect++;
            }

            log.info("{}. [{}] expected={} predicted={} conf={} lang={}{}{} {}ms | {}",
                    ++n, ok ? "OK  " : "MISS", s.expected(), r.label(),
                    String.format("%.2f", r.confidence()),
                    r.language().code(), r.language().codeMixed() ? " (mixed)" : "",
                    tag, r.latencyMs(), s.text());
        }

        log.info("Accuracy: {}/{} | native script: {}/{} | romanized (Latin): {}/{} | transliterated: {}/{}",
                correct, samples.size(), nativeCorrect, nativeTotal,
                latinCorrect, latinTotal, translitCorrect, translitTotal);
        log.info("Confusion matrix (rows = expected, columns = predicted, order NEGATIVE NEUTRAL POSITIVE):");
        for (Sentiment expected : Sentiment.values()) {
            int[] row = confusion[expected.ordinal()];
            log.info("  {} -> {} {} {}", String.format("%-8s", expected), row[0], row[1], row[2]);
        }
        log.info("===== End of startup check =====");
    }

    private List<Sample> readSamples() throws IOException {
        List<Sample> samples = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource("samples/test-sentences.tsv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", 3);
                if (parts.length < 2) {
                    continue;
                }
                String lang = parts.length >= 3 ? parts[2].trim() : null;
                if (lang != null && lang.isEmpty()) {
                    lang = null;
                }
                samples.add(new Sample(Sentiment.fromLabel(parts[0]), parts[1].trim(), lang));
            }
        }
        return samples;
    }
}
