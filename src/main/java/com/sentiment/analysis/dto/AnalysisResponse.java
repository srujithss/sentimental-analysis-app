package com.sentiment.analysis.dto;

import com.sentiment.analysis.model.enums.Sentiment;

import java.util.LinkedHashMap;
import java.util.Map;

public record AnalysisResponse(
        Long id,
        String label,
        double confidence,
        Map<String, Double> probabilities,
        String languageCode,
        String languageName,
        String script,
        boolean codeMixed,
        boolean lowConfidence,
        long latencyMs,
        String transliteratedText) {

    public static AnalysisResponse from(Long id, SentimentResult r, LanguageInfo language,
                                        String transliteratedText) {
        Map<String, Double> probs = new LinkedHashMap<>();
        for (Sentiment s : Sentiment.values()) {          // fixed order: NEGATIVE, NEUTRAL, POSITIVE
            probs.put(s.name(), r.probabilities().getOrDefault(s, 0.0));
        }
        return new AnalysisResponse(id, r.label().name(), r.confidence(), probs,
                language.code(), language.name(), language.script(),
                language.codeMixed(), r.lowConfidence(), r.latencyMs(), transliteratedText);
    }
}
