package com.textaudit.preprocessor.exception;

/**
 * 特征提取异常
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class FeatureExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FeatureExtractionException(String message) {
        super(message);
    }

    public FeatureExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeatureExtractionException(Throwable cause) {
        super(cause);
    }
}