package com.textaudit.trainer.exception;

/**
 * 存储异常类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
public class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public StorageException(Throwable cause) {
        super(cause);
    }
}