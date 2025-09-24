package com.textaudit.preprocessor.exception;

/**
 * 文本处理异常
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class TextProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TextProcessingException(String message) {
        super(message);
    }

    public TextProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public TextProcessingException(Throwable cause) {
        super(cause);
    }
}