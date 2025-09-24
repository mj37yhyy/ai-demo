package com.textaudit.trainer.repository;

import com.textaudit.trainer.entity.TrainingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 训练任务数据仓库
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Repository
public interface TrainingJobRepository extends JpaRepository<TrainingJob, String>, 
                                              JpaSpecificationExecutor<TrainingJob> {
    
    /**
     * 根据名称查找训练任务
     */
    Optional<TrainingJob> findByName(String name);
    
    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 根据状态查找训练任务
     */
    List<TrainingJob> findByStatus(TrainingJob.JobStatus status);
    
    /**
     * 根据状态统计数量
     */
    long countByStatus(TrainingJob.JobStatus status);
    
    /**
     * 根据模型类型查找训练任务
     */
    List<TrainingJob> findByModelType(TrainingJob.ModelType modelType);
    
    /**
     * 根据算法查找训练任务
     */
    List<TrainingJob> findByAlgorithm(TrainingJob.Algorithm algorithm);
    
    /**
     * 根据创建者查找训练任务
     */
    List<TrainingJob> findByCreatedBy(String createdBy);
    
    /**
     * 统计创建者的任务数量
     */
    long countByCreatedBy(String createdBy);
    
    /**
     * 根据创建时间范围查找训练任务
     */
    List<TrainingJob> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 查找指定时间之前创建的任务
     */
    List<TrainingJob> findByCreatedAtBefore(LocalDateTime before);
    
    /**
     * 查找指定时间之前创建且状态在指定列表中的任务
     */
    List<TrainingJob> findByCreatedAtBeforeAndStatusIn(LocalDateTime before, 
                                                       List<TrainingJob.JobStatus> statuses);
    
    /**
     * 根据更新时间范围查找训练任务
     */
    List<TrainingJob> findByUpdatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 按创建时间倒序查找最近的任务
     */
    Page<TrainingJob> findTopByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 按更新时间倒序查找最近更新的任务
     */
    Page<TrainingJob> findTopByOrderByUpdatedAtDesc(Pageable pageable);
    
    /**
     * 查找正在运行的任务（按开始时间排序）
     */
    List<TrainingJob> findByStatusOrderByStartTimeAsc(TrainingJob.JobStatus status);
    
    /**
     * 查找已完成的任务（按完成时间倒序）
     */
    List<TrainingJob> findByStatusOrderByEndTimeDesc(TrainingJob.JobStatus status);
    
    /**
     * 根据模型类型和状态查找任务
     */
    List<TrainingJob> findByModelTypeAndStatus(TrainingJob.ModelType modelType, 
                                              TrainingJob.JobStatus status);
    
    /**
     * 根据算法和状态查找任务
     */
    List<TrainingJob> findByAlgorithmAndStatus(TrainingJob.Algorithm algorithm, 
                                              TrainingJob.JobStatus status);
    
    /**
     * 根据创建者和状态查找任务
     */
    List<TrainingJob> findByCreatedByAndStatus(String createdBy, TrainingJob.JobStatus status);
    
    /**
     * 查找训练时长超过指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.trainingDuration > :duration")
    List<TrainingJob> findByTrainingDurationGreaterThan(@Param("duration") Long duration);
    
    /**
     * 查找准确率高于指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.testAccuracy > :accuracy")
    List<TrainingJob> findByTestAccuracyGreaterThan(@Param("accuracy") Double accuracy);
    
    /**
     * 查找模型大小超过指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.modelSize > :size")
    List<TrainingJob> findByModelSizeGreaterThan(@Param("size") Long size);
    
    /**
     * 根据数据集路径查找任务
     */
    List<TrainingJob> findByDatasetPath(String datasetPath);
    
    /**
     * 查找有模型路径的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.modelPath IS NOT NULL")
    List<TrainingJob> findJobsWithModel();
    
    /**
     * 查找没有模型路径的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.modelPath IS NULL")
    List<TrainingJob> findJobsWithoutModel();
    
    /**
     * 根据名称模糊查找任务
     */
    List<TrainingJob> findByNameContainingIgnoreCase(String name);
    
    /**
     * 根据描述模糊查找任务
     */
    List<TrainingJob> findByDescriptionContainingIgnoreCase(String description);
    
    /**
     * 查找进度大于指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.progress > :progress")
    List<TrainingJob> findByProgressGreaterThan(@Param("progress") Double progress);
    
    /**
     * 查找当前epoch大于指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.currentEpoch > :epoch")
    List<TrainingJob> findByCurrentEpochGreaterThan(@Param("epoch") Integer epoch);
    
    /**
     * 查找训练样本数在指定范围内的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.trainingSamples BETWEEN :min AND :max")
    List<TrainingJob> findByTrainingSamplesBetween(@Param("min") Integer min, @Param("max") Integer max);
    
    /**
     * 查找验证样本数在指定范围内的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.validationSamples BETWEEN :min AND :max")
    List<TrainingJob> findByValidationSamplesBetween(@Param("min") Integer min, @Param("max") Integer max);
    
    /**
     * 查找测试样本数在指定范围内的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.testSamples BETWEEN :min AND :max")
    List<TrainingJob> findByTestSamplesBetween(@Param("min") Integer min, @Param("max") Integer max);
    
    /**
     * 查找有错误信息的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.errorMessage IS NOT NULL")
    List<TrainingJob> findJobsWithError();
    
    /**
     * 查找启用早停的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.earlyStopping = true")
    List<TrainingJob> findJobsWithEarlyStopping();
    
    /**
     * 查找最佳epoch大于指定值的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.bestEpoch > :epoch")
    List<TrainingJob> findByBestEpochGreaterThan(@Param("epoch") Integer epoch);
    
    /**
     * 根据验证分数范围查找任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.bestValidationScore BETWEEN :min AND :max")
    List<TrainingJob> findByBestValidationScoreBetween(@Param("min") Double min, @Param("max") Double max);
    
    /**
     * 统计各状态的任务数量
     */
    @Query("SELECT j.status, COUNT(j) FROM TrainingJob j GROUP BY j.status")
    List<Object[]> countJobsByStatus();
    
    /**
     * 统计各模型类型的任务数量
     */
    @Query("SELECT j.modelType, COUNT(j) FROM TrainingJob j GROUP BY j.modelType")
    List<Object[]> countJobsByModelType();
    
    /**
     * 统计各算法的任务数量
     */
    @Query("SELECT j.algorithm, COUNT(j) FROM TrainingJob j GROUP BY j.algorithm")
    List<Object[]> countJobsByAlgorithm();
    
    /**
     * 统计各创建者的任务数量
     */
    @Query("SELECT j.createdBy, COUNT(j) FROM TrainingJob j GROUP BY j.createdBy")
    List<Object[]> countJobsByCreatedBy();
    
    /**
     * 获取平均训练时长
     */
    @Query("SELECT AVG(j.trainingDuration) FROM TrainingJob j WHERE j.trainingDuration IS NOT NULL")
    Double getAverageTrainingDuration();
    
    /**
     * 获取平均准确率
     */
    @Query("SELECT AVG(j.testAccuracy) FROM TrainingJob j WHERE j.testAccuracy IS NOT NULL")
    Double getAverageTestAccuracy();
    
    /**
     * 获取平均模型大小
     */
    @Query("SELECT AVG(j.modelSize) FROM TrainingJob j WHERE j.modelSize IS NOT NULL")
    Double getAverageModelSize();
    
    /**
     * 查找最高准确率的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.testAccuracy = (SELECT MAX(j2.testAccuracy) FROM TrainingJob j2)")
    List<TrainingJob> findJobsWithHighestAccuracy();
    
    /**
     * 查找最短训练时长的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.trainingDuration = (SELECT MIN(j2.trainingDuration) FROM TrainingJob j2 WHERE j2.trainingDuration IS NOT NULL)")
    List<TrainingJob> findJobsWithShortestTrainingTime();
    
    /**
     * 查找最小模型大小的任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE j.modelSize = (SELECT MIN(j2.modelSize) FROM TrainingJob j2 WHERE j2.modelSize IS NOT NULL)")
    List<TrainingJob> findJobsWithSmallestModel();
    
    /**
     * 根据多个条件查找任务
     */
    @Query("SELECT j FROM TrainingJob j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:modelType IS NULL OR j.modelType = :modelType) AND " +
           "(:algorithm IS NULL OR j.algorithm = :algorithm) AND " +
           "(:createdBy IS NULL OR j.createdBy = :createdBy) AND " +
           "(:startTime IS NULL OR j.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR j.createdAt <= :endTime)")
    Page<TrainingJob> findJobsByMultipleConditions(
            @Param("status") TrainingJob.JobStatus status,
            @Param("modelType") TrainingJob.ModelType modelType,
            @Param("algorithm") TrainingJob.Algorithm algorithm,
            @Param("createdBy") String createdBy,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
    
    /**
     * 删除指定时间之前的任务
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
    
    /**
     * 删除指定状态的任务
     */
    void deleteByStatus(TrainingJob.JobStatus status);
    
    /**
     * 删除指定创建者的任务
     */
    void deleteByCreatedBy(String createdBy);
}