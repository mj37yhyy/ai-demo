package com.textaudit.preprocessor.exception;

/**
 * 数据增强异常
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class DataAugmentationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAugmentationException(String message) {
        super(message);
    }

    public DataAugmentationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAugmentationException(Throwable cause) {
        super(cause);
    }
}