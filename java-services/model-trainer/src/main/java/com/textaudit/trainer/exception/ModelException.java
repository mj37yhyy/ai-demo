package com.textaudit.trainer.exception;

/**
 * 模型异常类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class ModelException extends RuntimeException {
    
    public ModelException(String message) {
        super(message);
    }
    
    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ModelException(Throwable cause) {
        super(cause);
    }
}