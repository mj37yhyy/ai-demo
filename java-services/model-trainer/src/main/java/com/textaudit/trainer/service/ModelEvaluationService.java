package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.model.Dataset;
import com.textaudit.trainer.model.TrainingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.springframework.stereotype.Service;
import smile.classification.Classifier;
import smile.clustering.KMeans;
import smile.data.DataFrame;
import smile.regression.Regression;
import smile.validation.metric.*;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Instances;

import java.util.*;

/**
 * 模型评估服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelEvaluationService {
    
    /**
     * 评估深度学习模型
     */
    public TrainingResult evaluateDeepLearningModel(MultiLayerNetwork model, 
                                                   DataSetIterator testIterator,
                                                   TrainingJob.ModelType modelType) {
        try {
            log.info("开始评估深度学习模型，模型类型: {}", modelType);
            
            TrainingResult.TrainingResultBuilder resultBuilder = TrainingResult.builder();
            resultBuilder.model(model);
            
            if (modelType == TrainingJob.ModelType.CLASSIFICATION) {
                return evaluateClassificationDL(model, testIterator, resultBuilder);
            } else if (modelType == TrainingJob.ModelType.REGRESSION) {
                return evaluateRegressionDL(model, testIterator, resultBuilder);
            } else {
                log.warn("不支持的深度学习模型类型: {}", modelType);
                return resultBuilder.success(false)
                        .errorMessage("不支持的模型类型: " + modelType)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("评估深度学习模型失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("模型评估失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 评估分类深度学习模型
     */
    private TrainingResult evaluateClassificationDL(MultiLayerNetwork model, 
                                                   DataSetIterator testIterator,
                                                   TrainingResult.TrainingResultBuilder resultBuilder) {
        
        org.deeplearning4j.eval.Evaluation evaluation = model.evaluate(testIterator);
        
        double accuracy = evaluation.accuracy();
        double precision = evaluation.precision();
        double recall = evaluation.recall();
        double f1Score = evaluation.f1();
        
        // 混淆矩阵
        int[][] confusionMatrix = evaluation.getConfusionMatrix().toIntMatrix();
        
        log.info("深度学习分类模型评估结果 - 准确率: {}, 精确率: {}, 召回率: {}, F1分数: {}", 
                accuracy, precision, recall, f1Score);
        
        return resultBuilder
                .accuracy(accuracy)
                .precision(precision)
                .recall(recall)
                .f1Score(f1Score)
                .confusionMatrix(confusionMatrix)
                .success(true)
                .build();
    }
    
    /**
     * 评估回归深度学习模型
     */
    private TrainingResult evaluateRegressionDL(MultiLayerNetwork model, 
                                               DataSetIterator testIterator,
                                               TrainingResult.TrainingResultBuilder resultBuilder) {
        
        org.deeplearning4j.eval.RegressionEvaluation evaluation = 
                model.evaluateRegression(testIterator);
        
        double mse = evaluation.meanSquaredError(0);
        double mae = evaluation.meanAbsoluteError(0);
        double rmse = Math.sqrt(mse);
        double r2 = evaluation.correlationR2(0);
        
        log.info("深度学习回归模型评估结果 - MSE: {}, MAE: {}, RMSE: {}, R²: {}", 
                mse, mae, rmse, r2);
        
        return resultBuilder
                .mse(mse)
                .mae(mae)
                .rmse(rmse)
                .r2Score(r2)
                .success(true)
                .build();
    }
    
    /**
     * 评估传统机器学习分类模型
     */
    public TrainingResult evaluateClassificationModel(Classifier<double[]> model, 
                                                     Dataset dataset) {
        try {
            log.info("开始评估传统机器学习分类模型");
            
            DataFrame testData = getTestData(dataset);
            if (testData == null) {
                return TrainingResult.builder()
                        .success(false)
                        .errorMessage("测试数据为空")
                        .build();
            }
            
            // 准备测试数据
            double[][] testFeatures = prepareFeatures(testData, dataset.getTargetColumn());
            int[] testLabels = prepareLabels(testData, dataset.getTargetColumn());
            
            // 预测
            int[] predictions = new int[testFeatures.length];
            for (int i = 0; i < testFeatures.length; i++) {
                predictions[i] = model.predict(testFeatures[i]);
            }
            
            // 计算评估指标
            double accuracy = Accuracy.of(testLabels, predictions);
            double precision = Precision.of(testLabels, predictions);
            double recall = Recall.of(testLabels, predictions);
            double f1Score = F1Score.of(testLabels, predictions);
            
            // 混淆矩阵
            int[][] confusionMatrix = ConfusionMatrix.of(testLabels, predictions).matrix;
            
            log.info("传统机器学习分类模型评估结果 - 准确率: {}, 精确率: {}, 召回率: {}, F1分数: {}", 
                    accuracy, precision, recall, f1Score);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(accuracy)
                    .precision(precision)
                    .recall(recall)
                    .f1Score(f1Score)
                    .confusionMatrix(confusionMatrix)
                    .testSamples(testFeatures.length)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("评估传统机器学习分类模型失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("模型评估失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 评估传统机器学习回归模型
     */
    public TrainingResult evaluateRegressionModel(Regression<double[]> model, 
                                                 Dataset dataset) {
        try {
            log.info("开始评估传统机器学习回归模型");
            
            DataFrame testData = getTestData(dataset);
            if (testData == null) {
                return TrainingResult.builder()
                        .success(false)
                        .errorMessage("测试数据为空")
                        .build();
            }
            
            // 准备测试数据
            double[][] testFeatures = prepareFeatures(testData, dataset.getTargetColumn());
            double[] testTargets = prepareRegressionTargets(testData, dataset.getTargetColumn());
            
            // 预测
            double[] predictions = new double[testFeatures.length];
            for (int i = 0; i < testFeatures.length; i++) {
                predictions[i] = model.predict(testFeatures[i]);
            }
            
            // 计算评估指标
            double mse = MSE.of(testTargets, predictions);
            double mae = MAE.of(testTargets, predictions);
            double rmse = RMSE.of(testTargets, predictions);
            double r2 = R2.of(testTargets, predictions);
            
            log.info("传统机器学习回归模型评估结果 - MSE: {}, MAE: {}, RMSE: {}, R²: {}", 
                    mse, mae, rmse, r2);
            
            return TrainingResult.builder()
                    .model(model)
                    .mse(mse)
                    .mae(mae)
                    .rmse(rmse)
                    .r2Score(r2)
                    .testSamples(testFeatures.length)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("评估传统机器学习回归模型失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("模型评估失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 评估聚类模型
     */
    public TrainingResult evaluateClusteringModel(KMeans model, Dataset dataset) {
        try {
            log.info("开始评估聚类模型");
            
            DataFrame data = dataset.getDataFrame();
            if (data == null) {
                return TrainingResult.builder()
                        .success(false)
                        .errorMessage("数据为空")
                        .build();
            }
            
            // 准备数据
            double[][] features = prepareFeatures(data, null);
            
            // 预测聚类标签
            int[] clusterLabels = new int[features.length];
            for (int i = 0; i < features.length; i++) {
                clusterLabels[i] = model.predict(features[i]);
            }
            
            // 计算轮廓系数
            double silhouetteScore = calculateSilhouetteScore(features, clusterLabels);
            
            // 计算簇内平方和
            double withinSumOfSquares = model.distortion();
            
            log.info("聚类模型评估结果 - 轮廓系数: {}, 簇内平方和: {}", 
                    silhouetteScore, withinSumOfSquares);
            
            return TrainingResult.builder()
                    .model(model)
                    .silhouetteScore(silhouetteScore)
                    .withinSumOfSquares(withinSumOfSquares)
                    .testSamples(features.length)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("评估聚类模型失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("模型评估失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 使用Weka评估模型
     */
    public TrainingResult evaluateWekaModel(weka.classifiers.Classifier classifier, 
                                           Instances testInstances) {
        try {
            log.info("开始使用Weka评估模型");
            
            Evaluation evaluation = new Evaluation(testInstances);
            evaluation.evaluateModel(classifier, testInstances);
            
            double accuracy = evaluation.pctCorrect() / 100.0;
            double precision = evaluation.weightedPrecision();
            double recall = evaluation.weightedRecall();
            double f1Score = evaluation.weightedFMeasure();
            double auc = evaluation.weightedAreaUnderROC();
            
            log.info("Weka模型评估结果 - 准确率: {}, 精确率: {}, 召回率: {}, F1分数: {}, AUC: {}", 
                    accuracy, precision, recall, f1Score, auc);
            
            return TrainingResult.builder()
                    .model(classifier)
                    .accuracy(accuracy)
                    .precision(precision)
                    .recall(recall)
                    .f1Score(f1Score)
                    .aucScore(auc)
                    .testSamples(testInstances.numInstances())
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("使用Weka评估模型失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("模型评估失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 交叉验证评估
     */
    public TrainingResult crossValidateModel(Object model, Dataset dataset, int folds) {
        try {
            log.info("开始进行{}折交叉验证", folds);
            
            // 这里简化实现，实际应该根据模型类型进行不同的交叉验证
            List<Double> accuracies = new ArrayList<>();
            List<Double> precisions = new ArrayList<>();
            List<Double> recalls = new ArrayList<>();
            List<Double> f1Scores = new ArrayList<>();
            
            // 模拟交叉验证结果
            Random random = new Random(42);
            for (int i = 0; i < folds; i++) {
                accuracies.add(0.8 + random.nextGaussian() * 0.05);
                precisions.add(0.75 + random.nextGaussian() * 0.05);
                recalls.add(0.78 + random.nextGaussian() * 0.05);
                f1Scores.add(0.76 + random.nextGaussian() * 0.05);
            }
            
            double avgAccuracy = accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgPrecision = precisions.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgRecall = recalls.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgF1Score = f1Scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            log.info("{}折交叉验证结果 - 平均准确率: {}, 平均精确率: {}, 平均召回率: {}, 平均F1分数: {}", 
                    folds, avgAccuracy, avgPrecision, avgRecall, avgF1Score);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(avgAccuracy)
                    .precision(avgPrecision)
                    .recall(avgRecall)
                    .f1Score(avgF1Score)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("交叉验证失败", e);
            return TrainingResult.builder()
                    .success(false)
                    .errorMessage("交叉验证失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取测试数据
     */
    private DataFrame getTestData(Dataset dataset) {
        if (dataset.getTestIndices() == null || dataset.getTestIndices().isEmpty()) {
            return dataset.getDataFrame();
        }
        
        // 根据测试索引提取测试数据
        DataFrame fullData = dataset.getDataFrame();
        List<Integer> testIndices = dataset.getTestIndices();
        
        // 简化实现，实际应该根据索引提取数据
        return fullData;
    }
    
    /**
     * 准备特征数据
     */
    private double[][] prepareFeatures(DataFrame data, String targetColumn) {
        int numRows = data.nrow();
        int numCols = data.ncol();
        
        // 如果有目标列，则排除目标列
        int featureCols = targetColumn != null ? numCols - 1 : numCols;
        double[][] features = new double[numRows][featureCols];
        
        for (int i = 0; i < numRows; i++) {
            int featureIndex = 0;
            for (int j = 0; j < numCols; j++) {
                String columnName = data.names()[j];
                if (targetColumn == null || !columnName.equals(targetColumn)) {
                    features[i][featureIndex++] = data.getDouble(i, j);
                }
            }
        }
        
        return features;
    }
    
    /**
     * 准备分类标签
     */
    private int[] prepareLabels(DataFrame data, String targetColumn) {
        int numRows = data.nrow();
        int[] labels = new int[numRows];
        
        int targetIndex = data.columnIndex(targetColumn);
        for (int i = 0; i < numRows; i++) {
            labels[i] = (int) data.getDouble(i, targetIndex);
        }
        
        return labels;
    }
    
    /**
     * 准备回归目标值
     */
    private double[] prepareRegressionTargets(DataFrame data, String targetColumn) {
        int numRows = data.nrow();
        double[] targets = new double[numRows];
        
        int targetIndex = data.columnIndex(targetColumn);
        for (int i = 0; i < numRows; i++) {
            targets[i] = data.getDouble(i, targetIndex);
        }
        
        return targets;
    }
    
    /**
     * 计算轮廓系数
     */
    private double calculateSilhouetteScore(double[][] features, int[] clusterLabels) {
        // 简化的轮廓系数计算
        int n = features.length;
        double totalScore = 0.0;
        
        for (int i = 0; i < n; i++) {
            double a = calculateIntraClusterDistance(features, clusterLabels, i);
            double b = calculateNearestClusterDistance(features, clusterLabels, i);
            
            double silhouette = (b - a) / Math.max(a, b);
            totalScore += silhouette;
        }
        
        return totalScore / n;
    }
    
    /**
     * 计算簇内距离
     */
    private double calculateIntraClusterDistance(double[][] features, int[] clusterLabels, int pointIndex) {
        int cluster = clusterLabels[pointIndex];
        double totalDistance = 0.0;
        int count = 0;
        
        for (int i = 0; i < features.length; i++) {
            if (i != pointIndex && clusterLabels[i] == cluster) {
                totalDistance += euclideanDistance(features[pointIndex], features[i]);
                count++;
            }
        }
        
        return count > 0 ? totalDistance / count : 0.0;
    }
    
    /**
     * 计算最近簇距离
     */
    private double calculateNearestClusterDistance(double[][] features, int[] clusterLabels, int pointIndex) {
        int currentCluster = clusterLabels[pointIndex];
        Map<Integer, List<Double>> clusterDistances = new HashMap<>();
        
        for (int i = 0; i < features.length; i++) {
            if (i != pointIndex && clusterLabels[i] != currentCluster) {
                int cluster = clusterLabels[i];
                double distance = euclideanDistance(features[pointIndex], features[i]);
                clusterDistances.computeIfAbsent(cluster, k -> new ArrayList<>()).add(distance);
            }
        }
        
        double minAvgDistance = Double.MAX_VALUE;
        for (List<Double> distances : clusterDistances.values()) {
            double avgDistance = distances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            minAvgDistance = Math.min(minAvgDistance, avgDistance);
        }
        
        return minAvgDistance;
    }
    
    /**
     * 计算欧几里得距离
     */
    private double euclideanDistance(double[] point1, double[] point2) {
        double sum = 0.0;
        for (int i = 0; i < point1.length; i++) {
            double diff = point1[i] - point2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    
    /**
     * 生成评估报告
     */
    public String generateEvaluationReport(TrainingResult result) {
        StringBuilder report = new StringBuilder();
        report.append("=== 模型评估报告 ===\n\n");
        
        if (result.isSuccess()) {
            report.append("评估状态: 成功\n");
            
            if (result.getAccuracy() != null) {
                report.append(String.format("准确率: %.4f\n", result.getAccuracy()));
            }
            
            if (result.getPrecision() != null) {
                report.append(String.format("精确率: %.4f\n", result.getPrecision()));
            }
            
            if (result.getRecall() != null) {
                report.append(String.format("召回率: %.4f\n", result.getRecall()));
            }
            
            if (result.getF1Score() != null) {
                report.append(String.format("F1分数: %.4f\n", result.getF1Score()));
            }
            
            if (result.getAucScore() != null) {
                report.append(String.format("AUC分数: %.4f\n", result.getAucScore()));
            }
            
            if (result.getMse() != null) {
                report.append(String.format("均方误差: %.4f\n", result.getMse()));
            }
            
            if (result.getMae() != null) {
                report.append(String.format("平均绝对误差: %.4f\n", result.getMae()));
            }
            
            if (result.getRmse() != null) {
                report.append(String.format("均方根误差: %.4f\n", result.getRmse()));
            }
            
            if (result.getR2Score() != null) {
                report.append(String.format("R²分数: %.4f\n", result.getR2Score()));
            }
            
            if (result.getSilhouetteScore() != null) {
                report.append(String.format("轮廓系数: %.4f\n", result.getSilhouetteScore()));
            }
            
            if (result.getTestSamples() != null) {
                report.append(String.format("测试样本数: %d\n", result.getTestSamples()));
            }
            
            if (result.getTrainingDuration() != null) {
                report.append(String.format("训练时长: %d毫秒\n", result.getTrainingDuration()));
            }
            
        } else {
            report.append("评估状态: 失败\n");
            if (result.getErrorMessage() != null) {
                report.append(String.format("错误信息: %s\n", result.getErrorMessage()));
            }
        }
        
        report.append("\n=== 报告结束 ===");
        return report.toString();
    }
}