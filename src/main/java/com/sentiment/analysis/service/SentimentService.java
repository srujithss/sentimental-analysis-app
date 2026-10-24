package com.sentiment.analysis.service;

import com.sentiment.analysis.dto.SentimentResult;

public interface SentimentService {

    /** @throws com.sentiment.analysis.exception.ModelNotReadyException if the model is not loaded */
    SentimentResult analyze(String text);

    boolean isReady();

    String modelPath();
}
