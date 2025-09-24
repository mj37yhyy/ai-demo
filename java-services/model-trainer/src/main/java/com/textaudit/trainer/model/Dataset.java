package com.textaudit.trainer.model;

import com.textaudit.trainer.entity.TrainingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import smile.data.DataFrame;
import weka.core.Instances;

import java.util.List;
import java.util.Map;

/**
 * 数据集模型
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dataset {
    
    /**
     * 数据集路径
     */
    private String datasetPath;
    
    /**
     * 模型类型
     */
    private TrainingJob.ModelType modelType;
    
    /**
     * 总样本数
     */
    private Integer totalSamples;
    
    /**
     * 特征数量
     */
    private Integer featureCount;
    
    /**
     * 类别数量（分类任务）
     */
    private Integer classCount;
    
    /**
     * 目标列名
     */
    private String targetColumn;
    
    /**
     * Smile数据框
     */
    private DataFrame dataFrame;
    
    /**
     * Weka实例
     */
    private Instances wekaInstances;
    
    /**
     * 文本数据
     */
    private List<String> textData;
    
    /**
     * 词汇表（文本数据）
     */
    private Map<String, Integer> vocabulary;
    
    /**
     * 训练集索引
     */
    private List<Integer> trainIndices;
    
    /**
     * 验证集索引
     */
    private List<Integer> validationIndices;
    
    /**
     * 测试集索引
     */
    private List<Integer> testIndices;
    
    /**
     * 数据集元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 数据预处理配置
     */
    private DataPreprocessingConfig preprocessingConfig;
    
    /**
     * 是否已分割
     */
    private Boolean isSplit;
    
    /**
     * 数据集统计信息
     */
    private DatasetStatistics statistics;
    
    /**
     * 获取训练样本数
     */
    public int getTrainingSamples() {
        return trainIndices != null ? trainIndices.size() : 0;
    }
    
    /**
     * 获取验证样本数
     */
    public int getValidationSamples() {
        return validationIndices != null ? validationIndices.size() : 0;
    }
    
    /**
     * 获取测试样本数
     */
    public int getTestSamples() {
        return testIndices != null ? testIndices.size() : 0;
    }
    
    /**
     * 检查是否为文本数据集
     */
    public boolean isTextDataset() {
        return textData != null && !textData.isEmpty();
    }
    
    /**
     * 检查是否为结构化数据集
     */
    public boolean isStructuredDataset() {
        return dataFrame != null || wekaInstances != null;
    }
    
    /**
     * 获取数据集摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("数据集摘要:\n");
        summary.append(String.format("路径: %s\n", datasetPath));
        summary.append(String.format("模型类型: %s\n", modelType));
        summary.append(String.format("总样本数: %d\n", totalSamples));
        summary.append(String.format("特征数量: %d\n", featureCount));
        
        if (classCount != null) {
            summary.append(String.format("类别数量: %d\n", classCount));
        }
        
        if (isSplit != null && isSplit) {
            summary.append(String.format("训练样本: %d\n", getTrainingSamples()));
            summary.append(String.format("验证样本: %d\n", getValidationSamples()));
            summary.append(String.format("测试样本: %d\n", getTestSamples()));
        }
        
        if (isTextDataset()) {
            summary.append(String.format("词汇表大小: %d\n", vocabulary != null ? vocabulary.size() : 0));
        }
        
        return summary.toString();
    }
    
    /**
     * 数据预处理配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPreprocessingConfig {
        
        /**
         * 是否标准化
         */
        private Boolean normalize;
        
        /**
         * 是否缩放到[0,1]
         */
        private Boolean scale;
        
        /**
         * 是否处理缺失值
         */
        private Boolean handleMissingValues;
        
        /**
         * 缺失值处理策略
         */
        private String missingValueStrategy;
        
        /**
         * 是否进行特征选择
         */
        private Boolean featureSelection;
        
        /**
         * 特征选择方法
         */
        private String featureSelectionMethod;
        
        /**
         * 是否进行降维
         */
        private Boolean dimensionalityReduction;
        
        /**
         * 降维方法
         */
        private String dimensionalityReductionMethod;
        
        /**
         * 目标维度
         */
        private Integer targetDimensions;
        
        /**
         * 文本预处理配置
         */
        private TextPreprocessingConfig textConfig;
    }
    
    /**
     * 文本预处理配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextPreprocessingConfig {
        
        /**
         * 是否转换为小写
         */
        private Boolean toLowerCase;
        
        /**
         * 是否移除标点符号
         */
        private Boolean removePunctuation;
        
        /**
         * 是否移除停用词
         */
        private Boolean removeStopWords;
        
        /**
         * 停用词列表
         */
        private List<String> stopWords;
        
        /**
         * 是否进行词干提取
         */
        private Boolean stemming;
        
        /**
         * 是否进行词形还原
         */
        private Boolean lemmatization;
        
        /**
         * 最小词频
         */
        private Integer minWordFreq;
        
        /**
         * 最大词汇表大小
         */
        private Integer maxVocabSize;
        
        /**
         * N-gram范围
         */
        private int[] ngramRange;
        
        /**
         * 向量化方法
         */
        private String vectorizationMethod;
    }
    
    /**
     * 数据集统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatasetStatistics {
        
        /**
         * 特征统计
         */
        private Map<String, FeatureStatistics> featureStats;
        
        /**
         * 类别分布（分类任务）
         */
        private Map<String, Integer> classDistribution;
        
        /**
         * 缺失值统计
         */
        private Map<String, Integer> missingValues;
        
        /**
         * 数据类型统计
         */
        private Map<String, String> dataTypes;
        
        /**
         * 相关性矩阵
         */
        private double[][] correlationMatrix;
        
        /**
         * 异常值统计
         */
        private Map<String, Integer> outliers;
    }
    
    /**
     * 特征统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureStatistics {
        
        /**
         * 特征名称
         */
        private String featureName;
        
        /**
         * 数据类型
         */
        private String dataType;
        
        /**
         * 最小值
         */
        private Double minValue;
        
        /**
         * 最大值
         */
        private Double maxValue;
        
        /**
         * 平均值
         */
        private Double meanValue;
        
        /**
         * 中位数
         */
        private Double medianValue;
        
        /**
         * 标准差
         */
        private Double standardDeviation;
        
        /**
         * 方差
         */
        private Double variance;
        
        /**
         * 偏度
         */
        private Double skewness;
        
        /**
         * 峰度
         */
        private Double kurtosis;
        
        /**
         * 唯一值数量
         */
        private Integer uniqueValues;
        
        /**
         * 缺失值数量
         */
        private Integer missingValues;
        
        /**
         * 异常值数量
         */
        private Integer outliers;
        
        /**
         * 分位数
         */
        private Map<String, Double> quantiles;
    }
    
    /**
     * 创建分类数据集
     */
    public static Dataset forClassification(String datasetPath, DataFrame dataFrame, 
                                          String targetColumn, int classCount) {
        return Dataset.builder()
                .datasetPath(datasetPath)
                .modelType(TrainingJob.ModelType.CLASSIFICATION)
                .dataFrame(dataFrame)
                .targetColumn(targetColumn)
                .totalSamples(dataFrame.nrow())
                .featureCount(dataFrame.ncol() - 1)
                .classCount(classCount)
                .isSplit(false)
                .build();
    }
    
    /**
     * 创建回归数据集
     */
    public static Dataset forRegression(String datasetPath, DataFrame dataFrame, String targetColumn) {
        return Dataset.builder()
                .datasetPath(datasetPath)
                .modelType(TrainingJob.ModelType.REGRESSION)
                .dataFrame(dataFrame)
                .targetColumn(targetColumn)
                .totalSamples(dataFrame.nrow())
                .featureCount(dataFrame.ncol() - 1)
                .isSplit(false)
                .build();
    }
    
    /**
     * 创建聚类数据集
     */
    public static Dataset forClustering(String datasetPath, DataFrame dataFrame) {
        return Dataset.builder()
                .datasetPath(datasetPath)
                .modelType(TrainingJob.ModelType.CLUSTERING)
                .dataFrame(dataFrame)
                .totalSamples(dataFrame.nrow())
                .featureCount(dataFrame.ncol())
                .isSplit(false)
                .build();
    }
    
    /**
     * 创建文本数据集
     */
    public static Dataset forText(String datasetPath, List<String> textData, 
                                TrainingJob.ModelType modelType) {
        return Dataset.builder()
                .datasetPath(datasetPath)
                .modelType(modelType)
                .textData(textData)
                .totalSamples(textData.size())
                .isSplit(false)
                .build();
    }
}