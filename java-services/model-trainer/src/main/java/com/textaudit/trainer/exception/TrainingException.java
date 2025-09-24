package com.textaudit.trainer.exception;

/**
 * 训练异常类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class TrainingException extends RuntimeException {
    
    public TrainingException(String message) {
        super(message);
    }
    
    public TrainingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public TrainingException(Throwable cause) {
        super(cause);
    }
}