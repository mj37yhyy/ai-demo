package com.textaudit.preprocessor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文本特征DTO
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextFeatures {

    /**
     * 统计特征
     */
    private StatisticalFeatures statistical;

    /**
     * TF-IDF特征向量
     */
    private Map<String, Double> tfidfVector;

    /**
     * Word2Vec特征向量
     */
    private List<Double> word2vecVector;

    /**
     * N-gram特征
     */
    private Map<String, Integer> ngramFeatures;

    /**
     * 情感特征
     */
    private SentimentFeatures sentiment;

    /**
     * 语言特征
     */
    private LanguageFeatures language;

    /**
     * 自定义特征
     */
    private Map<String, Object> customFeatures;

    /**
     * 统计特征内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticalFeatures {
        /**
         * 文本长度
         */
        private int textLength;

        /**
         * 词汇数量
         */
        private int wordCount;

        /**
         * 句子数量
         */
        private int sentenceCount;

        /**
         * 段落数量
         */
        private int paragraphCount;

        /**
         * 平均词长
         */
        private double averageWordLength;

        /**
         * 平均句长
         */
        private double averageSentenceLength;

        /**
         * 标点符号数量
         */
        private int punctuationCount;

        /**
         * 数字数量
         */
        private int digitCount;

        /**
         * 大写字母数量
         */
        private int uppercaseCount;

        /**
         * 小写字母数量
         */
        private int lowercaseCount;

        /**
         * 空白字符数量
         */
        private int whitespaceCount;

        /**
         * 特殊字符数量
         */
        private int specialCharCount;

        /**
         * 词汇密度
         */
        private double lexicalDensity;

        /**
         * 词汇多样性（TTR）
         */
        private double typeTokenRatio;
    }

    /**
     * 情感特征内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentFeatures {
        /**
         * 情感极性分数 (-1到1)
         */
        private double polarityScore;

        /**
         * 情感强度分数 (0到1)
         */
        private double intensityScore;

        /**
         * 情感分类 (positive, negative, neutral)
         */
        private String sentimentClass;

        /**
         * 情感置信度
         */
        private double confidence;

        /**
         * 积极词汇数量
         */
        private int positiveWordCount;

        /**
         * 消极词汇数量
         */
        private int negativeWordCount;

        /**
         * 情感词汇总数
         */
        private int totalSentimentWords;
    }

    /**
     * 语言特征内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageFeatures {
        /**
         * 检测到的语言
         */
        private String detectedLanguage;

        /**
         * 语言置信度
         */
        private double languageConfidence;

        /**
         * 中文字符比例
         */
        private double chineseCharRatio;

        /**
         * 英文字符比例
         */
        private double englishCharRatio;

        /**
         * 数字字符比例
         */
        private double digitCharRatio;

        /**
         * 标点符号比例
         */
        private double punctuationRatio;

        /**
         * 是否包含敏感词
         */
        private boolean containsSensitiveWords;

        /**
         * 敏感词数量
         */
        private int sensitiveWordCount;

        /**
         * 文本复杂度分数
         */
        private double complexityScore;

        /**
         * 可读性分数
         */
        private double readabilityScore;
    }

    /**
     * 获取特征向量维度
     */
    public int getFeatureDimension() {
        int dimension = 0;
        
        if (tfidfVector != null) {
            dimension += tfidfVector.size();
        }
        
        if (word2vecVector != null) {
            dimension += word2vecVector.size();
        }
        
        if (ngramFeatures != null) {
            dimension += ngramFeatures.size();
        }
        
        // 统计特征维度
        if (statistical != null) {
            dimension += 14; // StatisticalFeatures中的字段数量
        }
        
        // 情感特征维度
        if (sentiment != null) {
            dimension += 6; // SentimentFeatures中的数值字段数量
        }
        
        // 语言特征维度
        if (language != null) {
            dimension += 8; // LanguageFeatures中的数值字段数量
        }
        
        return dimension;
    }

    /**
     * 是否包含TF-IDF特征
     */
    public boolean hasTfidfFeatures() {
        return tfidfVector != null && !tfidfVector.isEmpty();
    }

    /**
     * 是否包含Word2Vec特征
     */
    public boolean hasWord2vecFeatures() {
        return word2vecVector != null && !word2vecVector.isEmpty();
    }

    /**
     * 是否包含N-gram特征
     */
    public boolean hasNgramFeatures() {
        return ngramFeatures != null && !ngramFeatures.isEmpty();
    }

    /**
     * 是否包含情感特征
     */
    public boolean hasSentimentFeatures() {
        return sentiment != null;
    }

    /**
     * 是否包含语言特征
     */
    public boolean hasLanguageFeatures() {
        return language != null;
    }

    /**
     * 获取特征摘要
     */
    public FeatureSummary getSummary() {
        return FeatureSummary.builder()
            .totalDimension(getFeatureDimension())
            .hasTfidf(hasTfidfFeatures())
            .hasWord2vec(hasWord2vecFeatures())
            .hasNgram(hasNgramFeatures())
            .hasSentiment(hasSentimentFeatures())
            .hasLanguage(hasLanguageFeatures())
            .tfidfDimension(tfidfVector != null ? tfidfVector.size() : 0)
            .word2vecDimension(word2vecVector != null ? word2vecVector.size() : 0)
            .ngramDimension(ngramFeatures != null ? ngramFeatures.size() : 0)
            .build();
    }

    /**
     * 特征摘要内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureSummary {
        private int totalDimension;
        private boolean hasTfidf;
        private boolean hasWord2vec;
        private boolean hasNgram;
        private boolean hasSentiment;
        private boolean hasLanguage;
        private int tfidfDimension;
        private int word2vecDimension;
        private int ngramDimension;
    }
}