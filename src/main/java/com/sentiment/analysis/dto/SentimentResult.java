package com.sentiment.analysis.dto;

import com.sentiment.analysis.model.enums.Sentiment;

import java.util.Map;

public record SentimentResult(
        Sentiment label,
        double confidence,
        Map<Sentiment, Double> probabilities,
        LanguageInfo language,
        boolean lowConfidence,
        long latencyMs,
        String cleanedText) {
}
