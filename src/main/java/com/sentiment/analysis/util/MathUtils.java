package com.sentiment.analysis.util;

public final class MathUtils {

    private MathUtils() {
    }

    /** Numerically stable softmax: turns raw model scores (logits) into probabilities that sum to 1. */
    public static double[] softmax(float[] logits) {
        if (logits == null || logits.length == 0) {
            throw new IllegalArgumentException("logits must not be empty");
        }
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) {
            max = Math.max(max, v);
        }
        double sum = 0.0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = Math.exp(logits[i] - max);   // subtracting the max avoids overflow
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) {
            exp[i] /= sum;
        }
        return exp;
    }
}
