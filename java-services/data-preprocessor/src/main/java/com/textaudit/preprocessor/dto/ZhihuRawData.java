package com.textaudit.preprocessor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知乎原始数据DTO
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhihuRawData {

    /**
     * 数据ID
     */
    private String id;

    /**
     * 数据类型 (question/answer/article/topic)
     */
    private String type;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 作者信息
     */
    private String author;

    /**
     * 作者等级
     */
    @JsonProperty("author_level")
    private String authorLevel;

    /**
     * 点赞数
     */
    @JsonProperty("vote_count")
    private Integer voteCount;

    /**
     * 评论数
     */
    @JsonProperty("comment_count")
    private Integer commentCount;

    /**
     * 关注数
     */
    @JsonProperty("follow_count")
    private Integer followCount;

    /**
     * 收藏数
     */
    @JsonProperty("favorite_count")
    private Integer favoriteCount;

    /**
     * 浏览数
     */
    @JsonProperty("view_count")
    private Integer viewCount;

    /**
     * 分享数
     */
    @JsonProperty("share_count")
    private Integer shareCount;

    /**
     * 原始URL
     */
    private String url;

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 话题标签
     */
    private List<String> topics;

    /**
     * 问题ID (如果是回答)
     */
    @JsonProperty("question_id")
    private String questionId;

    /**
     * 问题标题 (如果是回答)
     */
    @JsonProperty("question_title")
    private String questionTitle;

    /**
     * 是否匿名
     */
    @JsonProperty("is_anonymous")
    private Boolean isAnonymous;

    /**
     * 是否置顶
     */
    @JsonProperty("is_sticky")
    private Boolean isSticky;

    /**
     * 是否精华
     */
    @JsonProperty("is_featured")
    private Boolean isFeatured;

    /**
     * 内容长度
     */
    @JsonProperty("content_length")
    private Integer contentLength;

    /**
     * 图片数量
     */
    @JsonProperty("image_count")
    private Integer imageCount;

    /**
     * 链接数量
     */
    @JsonProperty("link_count")
    private Integer linkCount;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    /**
     * 采集时间
     */
    @JsonProperty("collected_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectedAt;

    /**
     * 采集来源
     */
    @JsonProperty("collection_source")
    private String collectionSource;

    /**
     * 数据版本
     */
    @JsonProperty("data_version")
    private String dataVersion;

    /**
     * 获取显示标题
     */
    public String getDisplayTitle() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (questionTitle != null && !questionTitle.trim().isEmpty()) {
            return questionTitle;
        }
        return "无标题";
    }

    /**
     * 获取内容摘要
     */
    public String getContentSummary() {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        String cleanContent = content.replaceAll("<[^>]+>", "").trim();
        if (cleanContent.length() <= 100) {
            return cleanContent;
        }
        
        return cleanContent.substring(0, 100) + "...";
    }

    /**
     * 获取互动总数
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
     * 是否为高质量内容
     */
    public boolean isHighQuality() {
        // 简单的质量判断逻辑
        if (content == null || content.length() < 100) {
            return false;
        }
        
        int interactions = getTotalInteractions();
        return interactions >= 10 || (voteCount != null && voteCount >= 5);
    }

    /**
     * 获取内容类型描述
     */
    public String getTypeDescription() {
        if (type == null) {
            return "未知";
        }
        
        switch (type.toLowerCase()) {
            case "question":
                return "问题";
            case "answer":
                return "回答";
            case "article":
                return "文章";
            case "topic":
                return "话题";
            default:
                return type;
        }
    }

    /**
     * 验证数据完整性
     */
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() &&
               content != null && !content.trim().isEmpty() &&
               type != null && !type.trim().isEmpty();
    }
}