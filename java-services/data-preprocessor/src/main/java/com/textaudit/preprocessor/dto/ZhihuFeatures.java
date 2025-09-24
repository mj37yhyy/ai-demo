package com.textaudit.preprocessor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 知乎特征数据DTO
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhihuFeatures {

    /**
     * 提及的用户列表
     */
    private List<String> mentions;

    /**
     * 话题标签列表
     */
    private List<String> topics;

    /**
     * 知乎链接列表
     */
    private List<String> zhihuUrls;

    /**
     * 点赞数
     */
    private Integer voteCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 关注数
     */
    private Integer followCount;

    /**
     * 收藏数
     */
    private Integer favoriteCount;

    /**
     * 浏览数
     */
    private Integer viewCount;

    /**
     * 分享数
     */
    private Integer shareCount;

    /**
     * 作者等级
     */
    private String authorLevel;

    /**
     * 是否为回答
     */
    private Boolean isAnswer;

    /**
     * 是否为问题
     */
    private Boolean isQuestion;

    /**
     * 是否为文章
     */
    private Boolean isArticle;

    /**
     * 是否匿名
     */
    private Boolean isAnonymous;

    /**
     * 是否置顶
     */
    private Boolean isSticky;

    /**
     * 是否精华
     */
    private Boolean isFeatured;

    /**
     * 内容长度
     */
    private Integer contentLength;

    /**
     * 图片数量
     */
    private Integer imageCount;

    /**
     * 链接数量
     */
    private Integer linkCount;

    /**
     * 段落数量
     */
    private Integer paragraphCount;

    /**
     * 句子数量
     */
    private Integer sentenceCount;

    /**
     * 中文字符数量
     */
    private Integer chineseCharCount;

    /**
     * 英文单词数量
     */
    private Integer englishWordCount;

    /**
     * 数字数量
     */
    private Integer numberCount;

    /**
     * 标点符号数量
     */
    private Integer punctuationCount;

    /**
     * 表情符号数量
     */
    private Integer emojiCount;

    /**
     * 代码块数量
     */
    private Integer codeBlockCount;

    /**
     * 引用数量
     */
    private Integer quoteCount;

    /**
     * 列表项数量
     */
    private Integer listItemCount;

    /**
     * 互动率 (互动总数/浏览数)
     */
    private Double interactionRate;

    /**
     * 点赞率 (点赞数/浏览数)
     */
    private Double likeRate;

    /**
     * 评论率 (评论数/浏览数)
     */
    private Double commentRate;

    /**
     * 收藏率 (收藏数/浏览数)
     */
    private Double favoriteRate;

    /**
     * 内容质量评分
     */
    private Double qualityScore;

    /**
     * 可读性评分
     */
    private Double readabilityScore;

    /**
     * 情感倾向 (positive/negative/neutral)
     */
    private String sentiment;

    /**
     * 情感强度 (0-1)
     */
    private Double sentimentStrength;

    /**
     * 主题分类
     */
    private List<String> categories;

    /**
     * 关键词列表
     */
    private List<String> keywords;

    /**
     * 实体识别结果
     */
    private Map<String, List<String>> namedEntities;

    /**
     * 语言检测结果
     */
    private String language;

    /**
     * 语言置信度
     */
    private Double languageConfidence;

    /**
     * 扩展特征
     */
    private Map<String, Object> extendedFeatures;

    /**
     * 计算互动率
     */
    public void calculateRates() {
        if (viewCount != null && viewCount > 0) {
            int totalInteractions = 0;
            
            if (voteCount != null) totalInteractions += voteCount;
            if (commentCount != null) totalInteractions += commentCount;
            if (favoriteCount != null) totalInteractions += favoriteCount;
            if (shareCount != null) totalInteractions += shareCount;
            
            this.interactionRate = (double) totalInteractions / viewCount;
            
            if (voteCount != null) {
                this.likeRate = (double) voteCount / viewCount;
            }
            
            if (commentCount != null) {
                this.commentRate = (double) commentCount / viewCount;
            }
            
            if (favoriteCount != null) {
                this.favoriteRate = (double) favoriteCount / viewCount;
            }
        }
    }

    /**
     * 获取总互动数
     */
    public int getTotalInteractions() {
        int total = 0;
        if (voteCount != null) total += voteCount;
        if (commentCount != null) total += commentCount;
        if (favoriteCount != null) total += favoriteCount;
        if (shareCount != null) total += shareCount;
        return total;
    }

    /**
     * 是否为高互动内容
     */
    public boolean isHighEngagement() {
        return getTotalInteractions() >= 100 || 
               (interactionRate != null && interactionRate >= 0.1);
    }

    /**
     * 是否为长文本
     */
    public boolean isLongText() {
        return contentLength != null && contentLength >= 1000;
    }

    /**
     * 是否包含多媒体
     */
    public boolean hasMultimedia() {
        return (imageCount != null && imageCount > 0) ||
               (linkCount != null && linkCount > 0) ||
               (codeBlockCount != null && codeBlockCount > 0);
    }

    /**
     * 获取内容复杂度评分
     */
    public double getComplexityScore() {
        double score = 0.0;
        
        // 长度因子
        if (contentLength != null) {
            if (contentLength >= 1000) score += 0.3;
            else if (contentLength >= 500) score += 0.2;
            else if (contentLength >= 200) score += 0.1;
        }
        
        // 结构因子
        if (paragraphCount != null && paragraphCount >= 3) score += 0.2;
        if (listItemCount != null && listItemCount > 0) score += 0.1;
        if (quoteCount != null && quoteCount > 0) score += 0.1;
        
        // 多媒体因子
        if (hasMultimedia()) score += 0.2;
        
        // 互动因子
        if (isHighEngagement()) score += 0.1;
        
        return Math.min(1.0, score);
    }

    /**
     * 获取特征摘要
     */
    public Map<String, Object> getSummary() {
        return Map.of(
            "totalInteractions", getTotalInteractions(),
            "isHighEngagement", isHighEngagement(),
            "isLongText", isLongText(),
            "hasMultimedia", hasMultimedia(),
            "complexityScore", getComplexityScore(),
            "qualityScore", qualityScore != null ? qualityScore : 0.0,
            "readabilityScore", readabilityScore != null ? readabilityScore : 0.0
        );
    }
}