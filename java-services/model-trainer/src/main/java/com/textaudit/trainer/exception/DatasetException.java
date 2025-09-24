package com.textaudit.trainer.exception;

/**
 * 数据集异常类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class DatasetException extends RuntimeException {
    
    public DatasetException(String message) {
        super(message);
    }
    
    public DatasetException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DatasetException(Throwable cause) {
        super(cause);
    }
}