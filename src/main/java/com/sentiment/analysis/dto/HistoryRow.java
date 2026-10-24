package com.sentiment.analysis.dto;

import com.sentiment.analysis.model.entity.PredictionRecord;

import java.time.Instant;

public record HistoryRow(
        Long id,
        String text,
        String label,
        double confidence,
        String languageCode,
        String script,
        boolean codeMixed,
        boolean lowConfidence,
        Instant createdAt) {

    public static HistoryRow from(PredictionRecord p) {
        return new HistoryRow(p.getId(), p.getText(), p.getPredictedLabel().name(), p.getConfidence(),
                p.getLanguageCode(), p.getScript(), p.isCodeMixed(), p.isLowConfidence(), p.getCreatedAt());
    }
}
