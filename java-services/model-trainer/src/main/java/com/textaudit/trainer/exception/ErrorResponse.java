package com.textaudit.trainer.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 错误响应实体类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * 错误发生时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * HTTP状态码
     */
    private Integer status;
    
    /**
     * 错误类型
     */
    private String error;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 详细错误信息
     */
    private Map<String, Object> details;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 堆栈跟踪（仅在开发环境显示）
     */
    private String stackTrace;
}