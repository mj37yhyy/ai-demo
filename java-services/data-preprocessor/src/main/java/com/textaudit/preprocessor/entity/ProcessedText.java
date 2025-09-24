package com.textaudit.preprocessor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * 预处理文本实体类
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_texts", indexes = {
    @Index(name = "idx_raw_text_id", columnList = "raw_text_id"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_label", columnList = "label"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class ProcessedText {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "id", length = 36)
    private String id;

    /**
     * 原始文本ID
     */
    @Column(name = "raw_text_id", length = 36, nullable = false)
    private String rawTextId;

    /**
     * 预处理后的文本内容
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 分词结果（JSON格式）
     */
    @Column(name = "tokens", columnDefinition = "JSON")
    private String tokens;

    /**
     * 特征向量（JSON格式）
     */
    @Column(name = "features", columnDefinition = "JSON")
    private String features;

    /**
     * 标签（0: 正常, 1: 违规）
     */
    @Column(name = "label")
    private Integer label;

    /**
     * 数据源
     */
    @Column(name = "source", length = 100, nullable = false)
    private String source;

    /**
     * 时间戳
     */
    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    /**
     * 处理元数据（JSON格式）
     */
    @Column(name = "processing_metadata", columnDefinition = "JSON")
    private String processingMetadata;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 文本长度
     */
    @Transient
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 是否有标签
     */
    @Transient
    public boolean hasLabel() {
        return label != null;
    }

    /**
     * 是否违规
     */
    @Transient
    public boolean isViolation() {
        return label != null && label == 1;
    }
}