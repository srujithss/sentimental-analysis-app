package com.sentiment.analysis.exception;

public class ModelNotReadyException extends RuntimeException {
    public ModelNotReadyException(String message) {
        super(message);
    }
}
