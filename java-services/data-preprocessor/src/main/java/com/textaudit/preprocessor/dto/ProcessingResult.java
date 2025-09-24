package com.textaudit.preprocessor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文本处理结果DTO
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    /**
     * 处理是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 原始文本
     */
    private String originalText;

    /**
     * 清洗后的文本
     */
    private String cleanedText;

    /**
     * 分词结果
     */
    private TokenizationResult tokenizationResult;

    /**
     * 特征向量
     */
    private TextFeatures features;

    /**
     * 数据源
     */
    private String source;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 时间戳
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    /**
     * 获取文本长度变化比例
     */
    public double getLengthReductionRatio() {
        if (originalText == null || cleanedText == null || originalText.length() == 0) {
            return 0.0;
        }
        return 1.0 - (double) cleanedText.length() / originalText.length();
    }

    /**
     * 获取词汇数量
     */
    public int getTokenCount() {
        return tokenizationResult != null && tokenizationResult.getTokens() != null 
            ? tokenizationResult.getTokens().size() : 0;
    }

    /**
     * 获取过滤后词汇数量
     */
    public int getFilteredTokenCount() {
        return tokenizationResult != null && tokenizationResult.getFilteredTokens() != null 
            ? tokenizationResult.getFilteredTokens().size() : 0;
    }

    /**
     * 获取停用词过滤比例
     */
    public double getStopWordFilterRatio() {
        int totalTokens = getTokenCount();
        int filteredTokens = getFilteredTokenCount();
        
        if (totalTokens == 0) {
            return 0.0;
        }
        
        return 1.0 - (double) filteredTokens / totalTokens;
    }

    /**
     * 是否包含特征
     */
    public boolean hasFeatures() {
        return features != null;
    }

    /**
     * 获取处理摘要
     */
    public ProcessingSummary getSummary() {
        return ProcessingSummary.builder()
            .success(success)
            .originalLength(originalText != null ? originalText.length() : 0)
            .cleanedLength(cleanedText != null ? cleanedText.length() : 0)
            .tokenCount(getTokenCount())
            .filteredTokenCount(getFilteredTokenCount())
            .processingTimeMs(processingTimeMs)
            .hasFeatures(hasFeatures())
            .build();
    }

    /**
     * 处理摘要内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingSummary {
        private boolean success;
        private int originalLength;
        private int cleanedLength;
        private int tokenCount;
        private int filteredTokenCount;
        private long processingTimeMs;
        private boolean hasFeatures;
    }
}