package com.textaudit.preprocessor.repository;

import com.textaudit.preprocessor.entity.ProcessedText;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 预处理文本数据仓库接口
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Repository
public interface ProcessedTextRepository extends JpaRepository<ProcessedText, Long> {

    /**
     * 根据原始文本ID查找预处理文本
     */
    Optional<ProcessedText> findByRawTextId(Long rawTextId);

    /**
     * 根据原始文本ID列表查找预处理文本
     */
    List<ProcessedText> findByRawTextIdIn(List<Long> rawTextIds);

    /**
     * 根据数据源查找文本
     */
    Page<ProcessedText> findBySource(String source, Pageable pageable);

    /**
     * 根据标签查找文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.label = :label")
    Page<ProcessedText> findByLabelsContaining(@Param("label") String label, Pageable pageable);

    /**
     * 根据创建时间范围查找预处理文本
     */
    Page<ProcessedText> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据处理时间范围查找预处理文本
     */
    Page<ProcessedText> findByProcessedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 查找包含特定词汇的预处理文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.tokens LIKE %:token%")
    Page<ProcessedText> findByTokensContaining(@Param("token") String token, Pageable pageable);

    /**
     * 查找有特征向量的文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.features IS NOT NULL")
    Page<ProcessedText> findByFeatureVectorNotNull(Pageable pageable);

    /**
     * 查找清洗后内容长度在指定范围内的文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE LENGTH(p.content) BETWEEN :minLength AND :maxLength")
    Page<ProcessedText> findByCleanedContentLengthBetween(@Param("minLength") int minLength, 
                                                         @Param("maxLength") int maxLength, 
                                                         Pageable pageable);

    /**
     * 统计各数据源的文本数量
     */
    @Query("SELECT p.source, COUNT(p) FROM ProcessedText p GROUP BY p.source")
    List<Object[]> countBySource();

    /**
     * 统计各标签的文本数量
     */
    @Query("SELECT p.label, COUNT(p) FROM ProcessedText p WHERE p.label IS NOT NULL GROUP BY p.label")
    List<Object[]> countByLabels();

    /**
     * 查找最近处理的文本
     */
    List<ProcessedText> findTop10ByOrderByCreatedAtDesc();

    /**
     * 查找处理时间最长的文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.processingMetadata LIKE '%processingTime%' ORDER BY CAST(JSON_EXTRACT(p.processingMetadata, '$.processingTime') AS DECIMAL) DESC")
    List<ProcessedText> findTopByProcessingTimeDesc(Pageable pageable);

    /**
     * 查找特征向量维度在指定范围内的文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.features IS NOT NULL")
    Page<ProcessedText> findByFeatureVectorDimensionBetween(@Param("minDim") int minDim, 
                                                           @Param("maxDim") int maxDim, 
                                                           Pageable pageable);

    /**
     * 删除指定时间之前的预处理文本
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffTime);

    /**
     * 删除指定原始文本ID的预处理文本
     */
    void deleteByRawTextId(Long rawTextId);

    /**
     * 删除指定数据源的文本
     */
    void deleteBySource(String source);

    /**
     * 统计总文本数量
     */
    @Query("SELECT COUNT(p) FROM ProcessedText p")
    long countTotal();

    /**
     * 统计指定时间范围内的文本数量
     */
    long countByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定数据源的文本数量
     */
    long countBySource(String source);

    /**
     * 获取平均文本长度
     */
    @Query("SELECT AVG(LENGTH(p.content)) FROM ProcessedText p")
    Double getAverageTextLength();

    /**
     * 获取平均词汇数量
     */
    @Query("SELECT COUNT(p) FROM ProcessedText p WHERE p.tokens IS NOT NULL")
    Double getAverageTokenCount();

    /**
     * 获取平均特征维度
     */
    @Query("SELECT COUNT(p) FROM ProcessedText p WHERE p.features IS NOT NULL")
    Double getAverageFeatureDimension();

    /**
     * 查找相似文本（基于特征向量）
     * 注意：这是一个简化的相似度查询，实际应用中可能需要更复杂的向量相似度计算
     */
    @Query(value = "SELECT * FROM processed_text p WHERE p.id != :excludeId ORDER BY (SELECT COUNT(*) FROM JSON_TABLE(p.feature_vector, '$[*]' COLUMNS (value DOUBLE PATH '$')) AS jt1 JOIN JSON_TABLE(:targetVector, '$[*]' COLUMNS (value DOUBLE PATH '$')) AS jt2 ON ABS(jt1.value - jt2.value) < :threshold) DESC LIMIT :limit", 
           nativeQuery = true)
    List<ProcessedText> findSimilarTexts(@Param("excludeId") Long excludeId, 
                                        @Param("targetVector") String targetVector, 
                                        @Param("threshold") double threshold, 
                                        @Param("limit") int limit);

    /**
     * 根据关键词搜索文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.content LIKE %:keyword% OR p.tokens LIKE %:keyword%")
    Page<ProcessedText> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 查找多标签文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.label IS NOT NULL")
    Page<ProcessedText> findMultiLabelTexts(@Param("minLabels") int minLabels, Pageable pageable);

    /**
     * 查找未标记的文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.label IS NULL")
    Page<ProcessedText> findUnlabeledTexts(Pageable pageable);

    /**
     * 获取数据质量统计
     */
    @Query("SELECT " +
           "COUNT(*) as total, " +
           "COUNT(CASE WHEN p.content IS NOT NULL AND LENGTH(p.content) > 0 THEN 1 END) as withCleanedContent, " +
           "COUNT(CASE WHEN p.tokens IS NOT NULL AND LENGTH(p.tokens) > 0 THEN 1 END) as withTokens, " +
           "COUNT(CASE WHEN p.features IS NOT NULL THEN 1 END) as withFeatures, " +
           "COUNT(CASE WHEN p.label IS NOT NULL THEN 1 END) as withLabels " +
           "FROM ProcessedText p")
    Object[] getDataQualityStatistics();

    /**
     * 检查是否存在指定原始文本ID的预处理文本
     */
    boolean existsByRawTextId(Long rawTextId);

    /**
     * 批量查找预处理文本
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.id IN :ids")
    List<ProcessedText> findByIds(@Param("ids") List<Long> ids);

    /**
     * 查找需要重新处理的文本（基于处理元数据）
     */
    @Query("SELECT p FROM ProcessedText p WHERE p.processingMetadata LIKE '%needsReprocessing\":true%'")
    Page<ProcessedText> findTextsNeedingReprocessing(Pageable pageable);

    /**
     * 更新处理状态
     */
    @Query("UPDATE ProcessedText p SET p.processingMetadata = :metadata WHERE p.id = :id")
    void updateProcessingMetadata(@Param("id") Long id, @Param("metadata") String metadata);
}