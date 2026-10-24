package com.sentiment.analysis.exception;

public class ModelInferenceException extends RuntimeException {
    public ModelInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
