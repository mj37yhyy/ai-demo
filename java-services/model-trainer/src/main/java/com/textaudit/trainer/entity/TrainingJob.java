package com.textaudit.trainer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 训练任务实体
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Entity
@Table(name = "training_jobs", indexes = {
    @Index(name = "idx_job_id", columnList = "jobId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_model_type", columnList = "modelType"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", unique = true, nullable = false, length = 64)
    private String jobId;

    @Column(name = "job_name", nullable = false, length = 255)
    private String jobName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 50)
    private ModelType modelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 50)
    private Algorithm algorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "dataset_path", length = 500)
    private String datasetPath;

    @Column(name = "dataset_size")
    private Long datasetSize;

    @Column(name = "training_samples")
    private Long trainingSamples;

    @Column(name = "validation_samples")
    private Long validationSamples;

    @Column(name = "test_samples")
    private Long testSamples;

    @ElementCollection
    @CollectionTable(name = "training_job_hyperparameters", 
                    joinColumns = @JoinColumn(name = "job_id"))
    @MapKeyColumn(name = "parameter_name")
    @Column(name = "parameter_value", columnDefinition = "TEXT")
    private Map<String, String> hyperparameters;

    @Column(name = "model_path", length = 500)
    private String modelPath;

    @Column(name = "model_size")
    private Long modelSize;

    @Column(name = "training_accuracy")
    private Double trainingAccuracy;

    @Column(name = "validation_accuracy")
    private Double validationAccuracy;

    @Column(name = "test_accuracy")
    private Double testAccuracy;

    @Column(name = "training_loss")
    private Double trainingLoss;

    @Column(name = "validation_loss")
    private Double validationLoss;

    @Column(name = "epochs_completed")
    private Integer epochsCompleted;

    @Column(name = "total_epochs")
    private Integer totalEpochs;

    @Column(name = "training_time_seconds")
    private Long trainingTimeSeconds;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "progress_percentage")
    private Double progressPercentage;

    @Column(name = "current_epoch")
    private Integer currentEpoch;

    @Column(name = "current_batch")
    private Integer currentBatch;

    @Column(name = "total_batches")
    private Integer totalBatches;

    @Column(name = "learning_rate")
    private Double learningRate;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "early_stopped")
    private Boolean earlyStopped;

    @Column(name = "best_epoch")
    private Integer bestEpoch;

    @Column(name = "best_validation_score")
    private Double bestValidationScore;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 模型类型枚举
     */
    public enum ModelType {
        CLASSIFICATION,     // 分类模型
        REGRESSION,         // 回归模型
        CLUSTERING,         // 聚类模型
        ANOMALY_DETECTION,  // 异常检测模型
        SENTIMENT_ANALYSIS, // 情感分析模型
        TEXT_CLASSIFICATION,// 文本分类模型
        NAMED_ENTITY_RECOGNITION, // 命名实体识别
        TOPIC_MODELING     // 主题建模
    }

    /**
     * 算法枚举
     */
    public enum Algorithm {
        // 传统机器学习
        RANDOM_FOREST,
        SVM,
        LOGISTIC_REGRESSION,
        NAIVE_BAYES,
        DECISION_TREE,
        GRADIENT_BOOSTING,
        XGBOOST,
        LIGHTGBM,
        
        // 深度学习
        LSTM,
        GRU,
        CNN,
        TRANSFORMER,
        BERT,
        RNN,
        AUTOENCODER,
        
        // 聚类算法
        KMEANS,
        DBSCAN,
        HIERARCHICAL,
        
        // 其他
        ENSEMBLE,
        CUSTOM
    }

    /**
     * 任务状态枚举
     */
    public enum JobStatus {
        PENDING,        // 等待中
        PREPARING,      // 准备中
        TRAINING,       // 训练中
        EVALUATING,     // 评估中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        CANCELLED,      // 已取消
        PAUSED          // 已暂停
    }

    /**
     * 计算训练进度
     */
    public double calculateProgress() {
        if (totalEpochs == null || totalEpochs == 0) {
            return 0.0;
        }
        
        if (currentEpoch == null) {
            return 0.0;
        }
        
        double epochProgress = (double) currentEpoch / totalEpochs;
        
        if (totalBatches != null && totalBatches > 0 && currentBatch != null) {
            double batchProgress = (double) currentBatch / totalBatches / totalEpochs;
            return Math.min(100.0, (epochProgress + batchProgress) * 100);
        }
        
        return Math.min(100.0, epochProgress * 100);
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return status == JobStatus.TRAINING || 
               status == JobStatus.PREPARING || 
               status == JobStatus.EVALUATING;
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return status == JobStatus.COMPLETED || 
               status == JobStatus.FAILED || 
               status == JobStatus.CANCELLED;
    }

    /**
     * 获取训练时长（秒）
     */
    public Long getTrainingDurationSeconds() {
        if (startTime == null) {
            return null;
        }
        
        LocalDateTime endTimeToUse = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, endTimeToUse).getSeconds();
    }

    /**
     * 更新进度
     */
    public void updateProgress(int currentEpoch, int currentBatch, int totalBatches) {
        this.currentEpoch = currentEpoch;
        this.currentBatch = currentBatch;
        this.totalBatches = totalBatches;
        this.progressPercentage = calculateProgress();
    }

    /**
     * 设置训练完成
     */
    public void setTrainingCompleted(Double finalAccuracy, Double finalLoss) {
        this.status = JobStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        this.trainingAccuracy = finalAccuracy;
        this.trainingLoss = finalLoss;
        this.progressPercentage = 100.0;
        this.epochsCompleted = this.currentEpoch;
        
        if (startTime != null) {
            this.trainingTimeSeconds = getTrainingDurationSeconds();
        }
    }

    /**
     * 设置训练失败
     */
    public void setTrainingFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        
        if (startTime != null) {
            this.trainingTimeSeconds = getTrainingDurationSeconds();
        }
    }
}