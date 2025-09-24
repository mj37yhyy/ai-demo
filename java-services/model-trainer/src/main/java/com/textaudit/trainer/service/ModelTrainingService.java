package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.exception.ModelTrainingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 模型训练核心服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainingService {

    private final TrainingJobService trainingJobService;
    private final DeepLearningService deepLearningService;
    private final MachineLearningService machineLearningService;
    private final ModelEvaluationService modelEvaluationService;
    private final ModelStorageService modelStorageService;
    private final DatasetService datasetService;

    @Value("${model-training.training.batch-size:32}")
    private int defaultBatchSize;

    @Value("${model-training.training.max-epochs:100}")
    private int defaultMaxEpochs;

    @Value("${model-training.training.learning-rate:0.001}")
    private double defaultLearningRate;

    @Value("${model-training.training.validation-split:0.2}")
    private double validationSplit;

    @Value("${model-training.training.test-split:0.1}")
    private double testSplit;

    /**
     * 开始训练任务
     */
    @Async
    public CompletableFuture<TrainingJob> startTraining(String jobId) {
        log.info("开始训练任务: {}", jobId);
        
        try {
            TrainingJob job = trainingJobService.getJobById(jobId);
            if (job == null) {
                throw new ModelTrainingException("训练任务不存在: " + jobId);
            }

            // 检查任务状态
            if (job.isRunning()) {
                throw new ModelTrainingException("训练任务已在运行中: " + jobId);
            }

            // 更新任务状态为准备中
            job.setStatus(TrainingJob.JobStatus.PREPARING);
            job.setStartTime(LocalDateTime.now());
            trainingJobService.updateJob(job);

            // 准备数据集
            prepareDataset(job);

            // 根据算法类型选择训练方法
            TrainingJob result = switch (job.getAlgorithm()) {
                case LSTM, GRU, CNN, TRANSFORMER, BERT, RNN, AUTOENCODER -> 
                    trainDeepLearningModel(job);
                case RANDOM_FOREST, SVM, LOGISTIC_REGRESSION, NAIVE_BAYES, 
                     DECISION_TREE, GRADIENT_BOOSTING, XGBOOST, LIGHTGBM -> 
                    trainMachineLearningModel(job);
                case KMEANS, DBSCAN, HIERARCHICAL -> 
                    trainClusteringModel(job);
                default -> throw new ModelTrainingException("不支持的算法类型: " + job.getAlgorithm());
            };

            log.info("训练任务完成: {}", jobId);
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("训练任务失败: {}", jobId, e);
            
            TrainingJob job = trainingJobService.getJobById(jobId);
            if (job != null) {
                job.setTrainingFailed(e.getMessage());
                trainingJobService.updateJob(job);
            }
            
            throw new ModelTrainingException("训练任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 准备数据集
     */
    private void prepareDataset(TrainingJob job) {
        log.info("准备数据集: {}", job.getJobId());
        
        try {
            // 验证数据集路径
            Path datasetPath = Paths.get(job.getDatasetPath());
            if (!Files.exists(datasetPath)) {
                throw new ModelTrainingException("数据集文件不存在: " + job.getDatasetPath());
            }

            // 加载和预处理数据集
            var dataset = datasetService.loadDataset(job.getDatasetPath(), job.getModelType());
            
            // 数据集统计
            job.setDatasetSize((long) dataset.getTotalSamples());
            job.setTrainingSamples((long) (dataset.getTotalSamples() * (1 - validationSplit - testSplit)));
            job.setValidationSamples((long) (dataset.getTotalSamples() * validationSplit));
            job.setTestSamples((long) (dataset.getTotalSamples() * testSplit));

            // 分割数据集
            datasetService.splitDataset(dataset, validationSplit, testSplit);
            
            log.info("数据集准备完成 - 总样本: {}, 训练: {}, 验证: {}, 测试: {}", 
                    job.getDatasetSize(), job.getTrainingSamples(), 
                    job.getValidationSamples(), job.getTestSamples());

        } catch (Exception e) {
            throw new ModelTrainingException("数据集准备失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练深度学习模型
     */
    private TrainingJob trainDeepLearningModel(TrainingJob job) {
        log.info("开始训练深度学习模型: {} - {}", job.getJobId(), job.getAlgorithm());
        
        job.setStatus(TrainingJob.JobStatus.TRAINING);
        trainingJobService.updateJob(job);

        try {
            // 设置训练参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int batchSize = Integer.parseInt(hyperparams.getOrDefault("batch_size", String.valueOf(defaultBatchSize)));
            int maxEpochs = Integer.parseInt(hyperparams.getOrDefault("max_epochs", String.valueOf(defaultMaxEpochs)));
            double learningRate = Double.parseDouble(hyperparams.getOrDefault("learning_rate", String.valueOf(defaultLearningRate)));

            job.setBatchSize(batchSize);
            job.setTotalEpochs(maxEpochs);
            job.setLearningRate(learningRate);

            // 根据具体算法训练
            var trainingResult = switch (job.getAlgorithm()) {
                case LSTM -> deepLearningService.trainLSTM(job);
                case GRU -> deepLearningService.trainGRU(job);
                case CNN -> deepLearningService.trainCNN(job);
                case TRANSFORMER -> deepLearningService.trainTransformer(job);
                case BERT -> deepLearningService.trainBERT(job);
                case RNN -> deepLearningService.trainRNN(job);
                case AUTOENCODER -> deepLearningService.trainAutoencoder(job);
                default -> throw new ModelTrainingException("不支持的深度学习算法: " + job.getAlgorithm());
            };

            // 保存模型
            String modelPath = modelStorageService.saveModel(job.getJobId(), trainingResult.getModel());
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));

            // 评估模型
            var evaluation = modelEvaluationService.evaluateModel(trainingResult.getModel(), job);
            job.setTrainingAccuracy(evaluation.getTrainingAccuracy());
            job.setValidationAccuracy(evaluation.getValidationAccuracy());
            job.setTestAccuracy(evaluation.getTestAccuracy());
            job.setTrainingLoss(evaluation.getTrainingLoss());
            job.setValidationLoss(evaluation.getValidationLoss());

            // 设置训练完成
            job.setTrainingCompleted(evaluation.getTrainingAccuracy(), evaluation.getTrainingLoss());
            job.setEarlyStopped(trainingResult.isEarlyStopped());
            job.setBestEpoch(trainingResult.getBestEpoch());
            job.setBestValidationScore(trainingResult.getBestValidationScore());

            trainingJobService.updateJob(job);
            
            log.info("深度学习模型训练完成: {} - 准确率: {:.4f}", 
                    job.getJobId(), job.getTestAccuracy());

            return job;

        } catch (Exception e) {
            job.setTrainingFailed("深度学习模型训练失败: " + e.getMessage());
            trainingJobService.updateJob(job);
            throw new ModelTrainingException("深度学习模型训练失败", e);
        }
    }

    /**
     * 训练传统机器学习模型
     */
    private TrainingJob trainMachineLearningModel(TrainingJob job) {
        log.info("开始训练机器学习模型: {} - {}", job.getJobId(), job.getAlgorithm());
        
        job.setStatus(TrainingJob.JobStatus.TRAINING);
        trainingJobService.updateJob(job);

        try {
            // 根据具体算法训练
            var trainingResult = switch (job.getAlgorithm()) {
                case RANDOM_FOREST -> machineLearningService.trainRandomForest(job);
                case SVM -> machineLearningService.trainSVM(job);
                case LOGISTIC_REGRESSION -> machineLearningService.trainLogisticRegression(job);
                case NAIVE_BAYES -> machineLearningService.trainNaiveBayes(job);
                case DECISION_TREE -> machineLearningService.trainDecisionTree(job);
                case GRADIENT_BOOSTING -> machineLearningService.trainGradientBoosting(job);
                case XGBOOST -> machineLearningService.trainXGBoost(job);
                case LIGHTGBM -> machineLearningService.trainLightGBM(job);
                default -> throw new ModelTrainingException("不支持的机器学习算法: " + job.getAlgorithm());
            };

            // 保存模型
            String modelPath = modelStorageService.saveModel(job.getJobId(), trainingResult.getModel());
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));

            // 评估模型
            var evaluation = modelEvaluationService.evaluateModel(trainingResult.getModel(), job);
            job.setTrainingAccuracy(evaluation.getTrainingAccuracy());
            job.setValidationAccuracy(evaluation.getValidationAccuracy());
            job.setTestAccuracy(evaluation.getTestAccuracy());

            // 设置训练完成
            job.setTrainingCompleted(evaluation.getTestAccuracy(), null);
            job.setEpochsCompleted(1); // 传统ML通常只需要一轮训练

            trainingJobService.updateJob(job);
            
            log.info("机器学习模型训练完成: {} - 准确率: {:.4f}", 
                    job.getJobId(), job.getTestAccuracy());

            return job;

        } catch (Exception e) {
            job.setTrainingFailed("机器学习模型训练失败: " + e.getMessage());
            trainingJobService.updateJob(job);
            throw new ModelTrainingException("机器学习模型训练失败", e);
        }
    }

    /**
     * 训练聚类模型
     */
    private TrainingJob trainClusteringModel(TrainingJob job) {
        log.info("开始训练聚类模型: {} - {}", job.getJobId(), job.getAlgorithm());
        
        job.setStatus(TrainingJob.JobStatus.TRAINING);
        trainingJobService.updateJob(job);

        try {
            // 根据具体算法训练
            var trainingResult = switch (job.getAlgorithm()) {
                case KMEANS -> machineLearningService.trainKMeans(job);
                case DBSCAN -> machineLearningService.trainDBSCAN(job);
                case HIERARCHICAL -> machineLearningService.trainHierarchical(job);
                default -> throw new ModelTrainingException("不支持的聚类算法: " + job.getAlgorithm());
            };

            // 保存模型
            String modelPath = modelStorageService.saveModel(job.getJobId(), trainingResult.getModel());
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));

            // 评估聚类模型（使用轮廓系数等指标）
            var evaluation = modelEvaluationService.evaluateClusteringModel(trainingResult.getModel(), job);
            job.setTrainingAccuracy(evaluation.getSilhouetteScore());

            // 设置训练完成
            job.setTrainingCompleted(evaluation.getSilhouetteScore(), null);
            job.setEpochsCompleted(1);

            trainingJobService.updateJob(job);
            
            log.info("聚类模型训练完成: {} - 轮廓系数: {:.4f}", 
                    job.getJobId(), job.getTrainingAccuracy());

            return job;

        } catch (Exception e) {
            job.setTrainingFailed("聚类模型训练失败: " + e.getMessage());
            trainingJobService.updateJob(job);
            throw new ModelTrainingException("聚类模型训练失败", e);
        }
    }

    /**
     * 停止训练任务
     */
    public void stopTraining(String jobId) {
        log.info("停止训练任务: {}", jobId);
        
        TrainingJob job = trainingJobService.getJobById(jobId);
        if (job == null) {
            throw new ModelTrainingException("训练任务不存在: " + jobId);
        }

        if (!job.isRunning()) {
            throw new ModelTrainingException("训练任务未在运行: " + jobId);
        }

        // 设置任务状态为已取消
        job.setStatus(TrainingJob.JobStatus.CANCELLED);
        job.setEndTime(LocalDateTime.now());
        trainingJobService.updateJob(job);

        log.info("训练任务已停止: {}", jobId);
    }

    /**
     * 暂停训练任务
     */
    public void pauseTraining(String jobId) {
        log.info("暂停训练任务: {}", jobId);
        
        TrainingJob job = trainingJobService.getJobById(jobId);
        if (job == null) {
            throw new ModelTrainingException("训练任务不存在: " + jobId);
        }

        if (job.getStatus() != TrainingJob.JobStatus.TRAINING) {
            throw new ModelTrainingException("只能暂停正在训练的任务: " + jobId);
        }

        job.setStatus(TrainingJob.JobStatus.PAUSED);
        trainingJobService.updateJob(job);

        log.info("训练任务已暂停: {}", jobId);
    }

    /**
     * 恢复训练任务
     */
    public void resumeTraining(String jobId) {
        log.info("恢复训练任务: {}", jobId);
        
        TrainingJob job = trainingJobService.getJobById(jobId);
        if (job == null) {
            throw new ModelTrainingException("训练任务不存在: " + jobId);
        }

        if (job.getStatus() != TrainingJob.JobStatus.PAUSED) {
            throw new ModelTrainingException("只能恢复已暂停的任务: " + jobId);
        }

        job.setStatus(TrainingJob.JobStatus.TRAINING);
        trainingJobService.updateJob(job);

        log.info("训练任务已恢复: {}", jobId);
    }

    /**
     * 获取文件大小
     */
    private Long getFileSize(String filePath) {
        try {
            File file = new File(filePath);
            return file.exists() ? file.length() : null;
        } catch (Exception e) {
            log.warn("获取文件大小失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 更新训练进度
     */
    public void updateTrainingProgress(String jobId, int currentEpoch, int currentBatch, 
                                     int totalBatches, double currentLoss, double currentAccuracy) {
        try {
            TrainingJob job = trainingJobService.getJobById(jobId);
            if (job != null && job.isRunning()) {
                job.updateProgress(currentEpoch, currentBatch, totalBatches);
                job.setTrainingLoss(currentLoss);
                job.setTrainingAccuracy(currentAccuracy);
                trainingJobService.updateJob(job);
                
                log.debug("更新训练进度: {} - Epoch: {}/{}, Batch: {}/{}, Loss: {:.4f}, Accuracy: {:.4f}", 
                        jobId, currentEpoch, job.getTotalEpochs(), currentBatch, totalBatches, 
                        currentLoss, currentAccuracy);
            }
        } catch (Exception e) {
            log.error("更新训练进度失败: {}", jobId, e);
        }
    }
}