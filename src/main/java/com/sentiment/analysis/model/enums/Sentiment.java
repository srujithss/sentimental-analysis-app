package com.sentiment.analysis.model.enums;

import java.util.Locale;

public enum Sentiment {
    NEGATIVE, NEUTRAL, POSITIVE;

    /**
     * Converts the label text coming from the model's config.json (id2label)
     * into our enum. Case-insensitive, so "negative" and "Negative" both work.
     */
    public static Sentiment fromLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Model returned a null label");
        }
        return switch (label.trim().toUpperCase(Locale.ROOT)) {
            case "NEGATIVE", "NEG" -> NEGATIVE;
            case "NEUTRAL", "NEU" -> NEUTRAL;
            case "POSITIVE", "POS" -> POSITIVE;
            default -> throw new IllegalArgumentException(
                    "Unsupported label '" + label + "'. The model's config.json id2label must "
                            + "contain negative / neutral / positive.");
        };
    }
}
