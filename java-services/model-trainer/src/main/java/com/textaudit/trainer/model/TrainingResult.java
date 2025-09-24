package com.textaudit.trainer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练结果模型
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingResult {
    
    /**
     * 训练好的模型
     */
    private Object model;
    
    /**
     * 训练准确率
     */
    private Double accuracy;
    
    /**
     * 验证准确率
     */
    private Double validationAccuracy;
    
    /**
     * 测试准确率
     */
    private Double testAccuracy;
    
    /**
     * 训练损失
     */
    private Double trainingLoss;
    
    /**
     * 验证损失
     */
    private Double validationLoss;
    
    /**
     * 测试损失
     */
    private Double testLoss;
    
    /**
     * 最佳验证分数
     */
    private Double bestValidationScore;
    
    /**
     * 最佳epoch
     */
    private Integer bestEpoch;
    
    /**
     * 是否早停
     */
    private Boolean earlyStopped;
    
    /**
     * 轮廓系数（聚类模型）
     */
    private Double silhouetteScore;
    
    /**
     * 训练时长（毫秒）
     */
    private Long trainingDuration;
    
    /**
     * 模型大小（字节）
     */
    private Long modelSize;
    
    /**
     * 训练样本数
     */
    private Long trainingSamples;
    
    /**
     * 验证样本数
     */
    private Long validationSamples;
    
    /**
     * 测试样本数
     */
    private Long testSamples;
    
    /**
     * 特征数量
     */
    private Integer featureCount;
    
    /**
     * 类别数量
     */
    private Integer classCount;
    
    /**
     * 混淆矩阵
     */
    private int[][] confusionMatrix;
    
    /**
     * 精确率
     */
    private Double precision;
    
    /**
     * 召回率
     */
    private Double recall;
    
    /**
     * F1分数
     */
    private Double f1Score;
    
    /**
     * AUC分数
     */
    private Double aucScore;
    
    /**
     * 训练参数
     */
    private java.util.Map<String, Object> trainingParams;
    
    /**
     * 模型元数据
     */
    private java.util.Map<String, Object> modelMetadata;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 是否训练成功
     */
    private Boolean success;
    
    /**
     * 获取主要评估指标
     */
    public Double getPrimaryMetric() {
        if (accuracy != null) {
            return accuracy;
        }
        if (testAccuracy != null) {
            return testAccuracy;
        }
        if (validationAccuracy != null) {
            return validationAccuracy;
        }
        if (silhouetteScore != null) {
            return silhouetteScore;
        }
        if (f1Score != null) {
            return f1Score;
        }
        return 0.0;
    }
    
    /**
     * 检查训练是否成功
     */
    public boolean isSuccessful() {
        return success != null ? success : (errorMessage == null && model != null);
    }
    
    /**
     * 设置分类评估指标
     */
    public void setClassificationMetrics(double accuracy, double precision, double recall, 
                                       double f1Score, int[][] confusionMatrix) {
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
        this.confusionMatrix = confusionMatrix;
    }
    
    /**
     * 设置回归评估指标
     */
    public void setRegressionMetrics(double mse, double rmse, double mae, double r2Score) {
        if (trainingParams == null) {
            trainingParams = new java.util.HashMap<>();
        }
        trainingParams.put("mse", mse);
        trainingParams.put("rmse", rmse);
        trainingParams.put("mae", mae);
        trainingParams.put("r2_score", r2Score);
    }
    
    /**
     * 设置聚类评估指标
     */
    public void setClusteringMetrics(double silhouetteScore, double calinskiHarabaszScore, 
                                   double daviesBouldinScore) {
        this.silhouetteScore = silhouetteScore;
        if (trainingParams == null) {
            trainingParams = new java.util.HashMap<>();
        }
        trainingParams.put("calinski_harabasz_score", calinskiHarabaszScore);
        trainingParams.put("davies_bouldin_score", daviesBouldinScore);
    }
    
    /**
     * 添加模型元数据
     */
    public void addModelMetadata(String key, Object value) {
        if (modelMetadata == null) {
            modelMetadata = new java.util.HashMap<>();
        }
        modelMetadata.put(key, value);
    }
    
    /**
     * 添加训练参数
     */
    public void addTrainingParam(String key, Object value) {
        if (trainingParams == null) {
            trainingParams = new java.util.HashMap<>();
        }
        trainingParams.put(key, value);
    }
    
    /**
     * 获取模型摘要信息
     */
    public String getModelSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("模型训练结果摘要:\n");
        
        if (accuracy != null) {
            summary.append(String.format("准确率: %.4f\n", accuracy));
        }
        if (precision != null) {
            summary.append(String.format("精确率: %.4f\n", precision));
        }
        if (recall != null) {
            summary.append(String.format("召回率: %.4f\n", recall));
        }
        if (f1Score != null) {
            summary.append(String.format("F1分数: %.4f\n", f1Score));
        }
        if (silhouetteScore != null) {
            summary.append(String.format("轮廓系数: %.4f\n", silhouetteScore));
        }
        if (trainingDuration != null) {
            summary.append(String.format("训练时长: %d ms\n", trainingDuration));
        }
        if (trainingSamples != null) {
            summary.append(String.format("训练样本数: %d\n", trainingSamples));
        }
        if (featureCount != null) {
            summary.append(String.format("特征数量: %d\n", featureCount));
        }
        if (earlyStopped != null && earlyStopped) {
            summary.append(String.format("早停于第%d个epoch\n", bestEpoch));
        }
        
        return summary.toString();
    }
    
    /**
     * 创建成功的训练结果
     */
    public static TrainingResult success(Object model) {
        return TrainingResult.builder()
                .model(model)
                .success(true)
                .build();
    }
    
    /**
     * 创建失败的训练结果
     */
    public static TrainingResult failure(String errorMessage) {
        return TrainingResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 创建分类任务的训练结果
     */
    public static TrainingResult forClassification(Object model, double accuracy, 
                                                 double precision, double recall, double f1Score) {
        return TrainingResult.builder()
                .model(model)
                .accuracy(accuracy)
                .precision(precision)
                .recall(recall)
                .f1Score(f1Score)
                .success(true)
                .build();
    }
    
    /**
     * 创建聚类任务的训练结果
     */
    public static TrainingResult forClustering(Object model, double silhouetteScore) {
        return TrainingResult.builder()
                .model(model)
                .silhouetteScore(silhouetteScore)
                .success(true)
                .build();
    }
}