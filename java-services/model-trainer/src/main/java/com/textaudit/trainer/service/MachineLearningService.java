package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.exception.ModelTrainingException;
import com.textaudit.trainer.model.TrainingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.classification.*;
import smile.clustering.DBSCAN;
import smile.clustering.HierarchicalClustering;
import smile.clustering.KMeans;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.validation.CrossValidation;
import smile.validation.metric.Accuracy;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SMO;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;

import java.util.Map;

/**
 * 传统机器学习模型训练服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineLearningService {

    private final DatasetService datasetService;

    /**
     * 训练随机森林模型
     */
    public TrainingResult trainRandomForest(TrainingJob job) {
        log.info("开始训练随机森林模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int numTrees = Integer.parseInt(hyperparams.getOrDefault("num_trees", "100"));
            int maxDepth = Integer.parseInt(hyperparams.getOrDefault("max_depth", "20"));
            int minSamplesSplit = Integer.parseInt(hyperparams.getOrDefault("min_samples_split", "2"));
            
            // 加载数据集
            DataFrame trainData = datasetService.getTrainingDataFrame(job);
            DataFrame testData = datasetService.getTestDataFrame(job);
            
            // 构建公式
            Formula formula = datasetService.getFormula(job);
            
            // 训练随机森林模型
            RandomForest model = RandomForest.fit(formula, trainData, numTrees, maxDepth, 
                    minSamplesSplit, trainData.ncol() - 1, 1.0, null);
            
            // 评估模型
            int[] predictions = model.predict(testData);
            int[] actual = datasetService.getActualLabels(testData, job);
            double accuracy = Accuracy.of(actual, predictions);
            
            log.info("随机森林模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("随机森林模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("随机森林模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练SVM模型
     */
    public TrainingResult trainSVM(TrainingJob job) {
        log.info("开始训练SVM模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            double c = Double.parseDouble(hyperparams.getOrDefault("c", "1.0"));
            String kernel = hyperparams.getOrDefault("kernel", "rbf");
            double gamma = Double.parseDouble(hyperparams.getOrDefault("gamma", "0.1"));
            
            // 使用Weka的SVM实现
            Instances trainInstances = datasetService.getWekaTrainingInstances(job);
            Instances testInstances = datasetService.getWekaTestInstances(job);
            
            // 创建SVM分类器
            SMO svm = new SMO();
            svm.setC(c);
            
            // 训练模型
            svm.buildClassifier(trainInstances);
            
            // 评估模型
            double accuracy = evaluateWekaModel(svm, testInstances);
            
            log.info("SVM模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(svm)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("SVM模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("SVM模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练逻辑回归模型
     */
    public TrainingResult trainLogisticRegression(TrainingJob job) {
        log.info("开始训练逻辑回归模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            double lambda = Double.parseDouble(hyperparams.getOrDefault("lambda", "0.01"));
            double tolerance = Double.parseDouble(hyperparams.getOrDefault("tolerance", "1e-6"));
            int maxIter = Integer.parseInt(hyperparams.getOrDefault("max_iter", "1000"));
            
            // 加载数据集
            DataFrame trainData = datasetService.getTrainingDataFrame(job);
            DataFrame testData = datasetService.getTestDataFrame(job);
            
            // 构建公式
            Formula formula = datasetService.getFormula(job);
            
            // 训练逻辑回归模型
            LogisticRegression model = LogisticRegression.fit(formula, trainData, lambda, tolerance, maxIter);
            
            // 评估模型
            int[] predictions = model.predict(testData);
            int[] actual = datasetService.getActualLabels(testData, job);
            double accuracy = Accuracy.of(actual, predictions);
            
            log.info("逻辑回归模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("逻辑回归模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("逻辑回归模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练朴素贝叶斯模型
     */
    public TrainingResult trainNaiveBayes(TrainingJob job) {
        log.info("开始训练朴素贝叶斯模型: {}", job.getJobId());
        
        try {
            // 使用Weka的朴素贝叶斯实现
            Instances trainInstances = datasetService.getWekaTrainingInstances(job);
            Instances testInstances = datasetService.getWekaTestInstances(job);
            
            // 创建朴素贝叶斯分类器
            NaiveBayes nb = new NaiveBayes();
            
            // 训练模型
            nb.buildClassifier(trainInstances);
            
            // 评估模型
            double accuracy = evaluateWekaModel(nb, testInstances);
            
            log.info("朴素贝叶斯模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(nb)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("朴素贝叶斯模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("朴素贝叶斯模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练决策树模型
     */
    public TrainingResult trainDecisionTree(TrainingJob job) {
        log.info("开始训练决策树模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int maxDepth = Integer.parseInt(hyperparams.getOrDefault("max_depth", "20"));
            int minSamplesSplit = Integer.parseInt(hyperparams.getOrDefault("min_samples_split", "2"));
            
            // 加载数据集
            DataFrame trainData = datasetService.getTrainingDataFrame(job);
            DataFrame testData = datasetService.getTestDataFrame(job);
            
            // 构建公式
            Formula formula = datasetService.getFormula(job);
            
            // 训练决策树模型
            DecisionTree model = DecisionTree.fit(formula, trainData, maxDepth, minSamplesSplit, 5);
            
            // 评估模型
            int[] predictions = model.predict(testData);
            int[] actual = datasetService.getActualLabels(testData, job);
            double accuracy = Accuracy.of(actual, predictions);
            
            log.info("决策树模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("决策树模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("决策树模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练梯度提升模型
     */
    public TrainingResult trainGradientBoosting(TrainingJob job) {
        log.info("开始训练梯度提升模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int numTrees = Integer.parseInt(hyperparams.getOrDefault("num_trees", "100"));
            double learningRate = Double.parseDouble(hyperparams.getOrDefault("learning_rate", "0.1"));
            int maxDepth = Integer.parseInt(hyperparams.getOrDefault("max_depth", "6"));
            double subsample = Double.parseDouble(hyperparams.getOrDefault("subsample", "1.0"));
            
            // 加载数据集
            DataFrame trainData = datasetService.getTrainingDataFrame(job);
            DataFrame testData = datasetService.getTestDataFrame(job);
            
            // 构建公式
            Formula formula = datasetService.getFormula(job);
            
            // 训练梯度提升模型
            GradientTreeBoost model = GradientTreeBoost.fit(formula, trainData, numTrees, 
                    maxDepth, 5, 2, learningRate, subsample);
            
            // 评估模型
            int[] predictions = model.predict(testData);
            int[] actual = datasetService.getActualLabels(testData, job);
            double accuracy = Accuracy.of(actual, predictions);
            
            log.info("梯度提升模型训练完成: {} - 准确率: {:.4f}", job.getJobId(), accuracy);
            
            return TrainingResult.builder()
                    .model(model)
                    .accuracy(accuracy)
                    .build();

        } catch (Exception e) {
            log.error("梯度提升模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("梯度提升模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练XGBoost模型（简化实现）
     */
    public TrainingResult trainXGBoost(TrainingJob job) {
        log.info("开始训练XGBoost模型: {}", job.getJobId());
        
        try {
            // 由于XGBoost需要额外的依赖，这里使用梯度提升作为替代
            log.warn("使用梯度提升模型替代XGBoost");
            return trainGradientBoosting(job);

        } catch (Exception e) {
            log.error("XGBoost模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("XGBoost模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练LightGBM模型（简化实现）
     */
    public TrainingResult trainLightGBM(TrainingJob job) {
        log.info("开始训练LightGBM模型: {}", job.getJobId());
        
        try {
            // 由于LightGBM需要额外的依赖，这里使用梯度提升作为替代
            log.warn("使用梯度提升模型替代LightGBM");
            return trainGradientBoosting(job);

        } catch (Exception e) {
            log.error("LightGBM模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("LightGBM模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练K-Means聚类模型
     */
    public TrainingResult trainKMeans(TrainingJob job) {
        log.info("开始训练K-Means聚类模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int k = Integer.parseInt(hyperparams.getOrDefault("k", "3"));
            int maxIter = Integer.parseInt(hyperparams.getOrDefault("max_iter", "100"));
            
            // 加载数据集
            double[][] data = datasetService.getClusteringData(job);
            
            // 训练K-Means模型
            KMeans model = KMeans.fit(data, k, maxIter);
            
            // 计算轮廓系数
            double silhouetteScore = calculateSilhouetteScore(data, model.y);
            
            log.info("K-Means聚类模型训练完成: {} - 轮廓系数: {:.4f}", job.getJobId(), silhouetteScore);
            
            return TrainingResult.builder()
                    .model(model)
                    .silhouetteScore(silhouetteScore)
                    .build();

        } catch (Exception e) {
            log.error("K-Means聚类模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("K-Means聚类模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练DBSCAN聚类模型
     */
    public TrainingResult trainDBSCAN(TrainingJob job) {
        log.info("开始训练DBSCAN聚类模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            double eps = Double.parseDouble(hyperparams.getOrDefault("eps", "0.5"));
            int minPts = Integer.parseInt(hyperparams.getOrDefault("min_pts", "5"));
            
            // 加载数据集
            double[][] data = datasetService.getClusteringData(job);
            
            // 训练DBSCAN模型
            DBSCAN<double[]> model = DBSCAN.fit(data, (x, y) -> {
                double sum = 0.0;
                for (int i = 0; i < x.length; i++) {
                    double diff = x[i] - y[i];
                    sum += diff * diff;
                }
                return Math.sqrt(sum);
            }, minPts, eps);
            
            // 计算轮廓系数
            double silhouetteScore = calculateSilhouetteScore(data, model.y);
            
            log.info("DBSCAN聚类模型训练完成: {} - 轮廓系数: {:.4f}", job.getJobId(), silhouetteScore);
            
            return TrainingResult.builder()
                    .model(model)
                    .silhouetteScore(silhouetteScore)
                    .build();

        } catch (Exception e) {
            log.error("DBSCAN聚类模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("DBSCAN聚类模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练层次聚类模型
     */
    public TrainingResult trainHierarchical(TrainingJob job) {
        log.info("开始训练层次聚类模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int k = Integer.parseInt(hyperparams.getOrDefault("k", "3"));
            String linkage = hyperparams.getOrDefault("linkage", "ward");
            
            // 加载数据集
            double[][] data = datasetService.getClusteringData(job);
            
            // 训练层次聚类模型
            HierarchicalClustering model = HierarchicalClustering.fit(
                    HierarchicalClustering.Linkage.WARD, data);
            
            // 获取聚类结果
            int[] clusters = model.partition(k);
            
            // 计算轮廓系数
            double silhouetteScore = calculateSilhouetteScore(data, clusters);
            
            log.info("层次聚类模型训练完成: {} - 轮廓系数: {:.4f}", job.getJobId(), silhouetteScore);
            
            return TrainingResult.builder()
                    .model(model)
                    .silhouetteScore(silhouetteScore)
                    .build();

        } catch (Exception e) {
            log.error("层次聚类模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("层次聚类模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 评估Weka模型
     */
    private double evaluateWekaModel(Classifier classifier, Instances testInstances) {
        try {
            int correct = 0;
            for (int i = 0; i < testInstances.numInstances(); i++) {
                double predicted = classifier.classifyInstance(testInstances.instance(i));
                double actual = testInstances.instance(i).classValue();
                if (predicted == actual) {
                    correct++;
                }
            }
            return (double) correct / testInstances.numInstances();
        } catch (Exception e) {
            log.error("评估Weka模型失败", e);
            return 0.0;
        }
    }

    /**
     * 计算轮廓系数
     */
    private double calculateSilhouetteScore(double[][] data, int[] clusters) {
        // 简化的轮廓系数计算
        // 实际应用中应该使用更精确的实现
        int n = data.length;
        double totalScore = 0.0;
        
        for (int i = 0; i < n; i++) {
            double a = calculateIntraClusterDistance(data, clusters, i);
            double b = calculateNearestClusterDistance(data, clusters, i);
            
            double silhouette = (b - a) / Math.max(a, b);
            totalScore += silhouette;
        }
        
        return totalScore / n;
    }

    /**
     * 计算簇内距离
     */
    private double calculateIntraClusterDistance(double[][] data, int[] clusters, int index) {
        int cluster = clusters[index];
        double totalDistance = 0.0;
        int count = 0;
        
        for (int i = 0; i < data.length; i++) {
            if (i != index && clusters[i] == cluster) {
                totalDistance += euclideanDistance(data[index], data[i]);
                count++;
            }
        }
        
        return count > 0 ? totalDistance / count : 0.0;
    }

    /**
     * 计算最近簇距离
     */
    private double calculateNearestClusterDistance(double[][] data, int[] clusters, int index) {
        int currentCluster = clusters[index];
        double minDistance = Double.MAX_VALUE;
        
        // 找到所有其他簇
        for (int cluster = 0; cluster < getMaxCluster(clusters) + 1; cluster++) {
            if (cluster == currentCluster) continue;
            
            double totalDistance = 0.0;
            int count = 0;
            
            for (int i = 0; i < data.length; i++) {
                if (clusters[i] == cluster) {
                    totalDistance += euclideanDistance(data[index], data[i]);
                    count++;
                }
            }
            
            if (count > 0) {
                double avgDistance = totalDistance / count;
                minDistance = Math.min(minDistance, avgDistance);
            }
        }
        
        return minDistance == Double.MAX_VALUE ? 0.0 : minDistance;
    }

    /**
     * 计算欧几里得距离
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * 获取最大簇编号
     */
    private int getMaxCluster(int[] clusters) {
        int max = 0;
        for (int cluster : clusters) {
            max = Math.max(max, cluster);
        }
        return max;
    }
}