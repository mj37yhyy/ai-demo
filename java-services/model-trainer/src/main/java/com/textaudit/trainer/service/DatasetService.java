package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.exception.ModelTrainingException;
import com.textaudit.trainer.model.Dataset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.StandardDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 数据集处理服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    @Value("${model-training.dataset.cache-dir:/tmp/dataset-cache}")
    private String cacheDir;

    @Value("${model-training.dataset.max-cache-size:1000}")
    private int maxCacheSize;

    private final Map<String, Dataset> datasetCache = new LinkedHashMap<String, Dataset>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Dataset> eldest) {
            return size() > maxCacheSize;
        }
    };

    /**
     * 加载数据集
     */
    public Dataset loadDataset(String datasetPath, TrainingJob.ModelType modelType) {
        log.info("加载数据集: {} - 模型类型: {}", datasetPath, modelType);
        
        try {
            // 检查缓存
            String cacheKey = datasetPath + "_" + modelType;
            if (datasetCache.containsKey(cacheKey)) {
                log.debug("从缓存加载数据集: {}", datasetPath);
                return datasetCache.get(cacheKey);
            }

            // 验证文件存在
            Path path = Paths.get(datasetPath);
            if (!Files.exists(path)) {
                throw new ModelTrainingException("数据集文件不存在: " + datasetPath);
            }

            // 根据文件扩展名选择加载方法
            String extension = getFileExtension(datasetPath).toLowerCase();
            Dataset dataset = switch (extension) {
                case "csv" -> loadCSVDataset(datasetPath, modelType);
                case "json" -> loadJSONDataset(datasetPath, modelType);
                case "txt" -> loadTextDataset(datasetPath, modelType);
                case "arff" -> loadARFFDataset(datasetPath, modelType);
                default -> throw new ModelTrainingException("不支持的数据集格式: " + extension);
            };

            // 缓存数据集
            datasetCache.put(cacheKey, dataset);
            
            log.info("数据集加载完成: {} - 样本数: {}, 特征数: {}", 
                    datasetPath, dataset.getTotalSamples(), dataset.getFeatureCount());
            
            return dataset;

        } catch (Exception e) {
            log.error("加载数据集失败: {}", datasetPath, e);
            throw new ModelTrainingException("加载数据集失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载CSV数据集
     */
    private Dataset loadCSVDataset(String datasetPath, TrainingJob.ModelType modelType) throws Exception {
        log.debug("加载CSV数据集: {}", datasetPath);
        
        // 使用Smile加载CSV
        DataFrame dataFrame = Read.csv(datasetPath);
        
        // 创建数据集对象
        Dataset dataset = new Dataset();
        dataset.setDataFrame(dataFrame);
        dataset.setTotalSamples(dataFrame.nrow());
        dataset.setFeatureCount(dataFrame.ncol() - 1); // 假设最后一列是标签
        dataset.setModelType(modelType);
        dataset.setDatasetPath(datasetPath);
        
        // 根据模型类型处理数据
        if (modelType == TrainingJob.ModelType.CLASSIFICATION) {
            processClassificationDataset(dataset);
        } else if (modelType == TrainingJob.ModelType.REGRESSION) {
            processRegressionDataset(dataset);
        } else if (modelType == TrainingJob.ModelType.CLUSTERING) {
            processClusteringDataset(dataset);
        }
        
        return dataset;
    }

    /**
     * 加载JSON数据集
     */
    private Dataset loadJSONDataset(String datasetPath, TrainingJob.ModelType modelType) throws Exception {
        log.debug("加载JSON数据集: {}", datasetPath);
        
        // 简化实现，实际应该解析JSON格式
        throw new ModelTrainingException("JSON数据集格式暂不支持，请转换为CSV格式");
    }

    /**
     * 加载文本数据集
     */
    private Dataset loadTextDataset(String datasetPath, TrainingJob.ModelType modelType) throws Exception {
        log.debug("加载文本数据集: {}", datasetPath);
        
        List<String> lines = Files.readAllLines(Paths.get(datasetPath));
        
        // 创建数据集对象
        Dataset dataset = new Dataset();
        dataset.setTotalSamples(lines.size());
        dataset.setModelType(modelType);
        dataset.setDatasetPath(datasetPath);
        dataset.setTextData(lines);
        
        // 对于文本数据，需要进行特征提取
        processTextDataset(dataset);
        
        return dataset;
    }

    /**
     * 加载ARFF数据集
     */
    private Dataset loadARFFDataset(String datasetPath, TrainingJob.ModelType modelType) throws Exception {
        log.debug("加载ARFF数据集: {}", datasetPath);
        
        // 使用Weka加载ARFF文件
        CSVLoader loader = new CSVLoader();
        loader.setSource(new FileInputStream(datasetPath));
        Instances instances = loader.getDataSet();
        
        if (instances.classIndex() == -1) {
            instances.setClassIndex(instances.numAttributes() - 1);
        }
        
        // 创建数据集对象
        Dataset dataset = new Dataset();
        dataset.setWekaInstances(instances);
        dataset.setTotalSamples(instances.numInstances());
        dataset.setFeatureCount(instances.numAttributes() - 1);
        dataset.setModelType(modelType);
        dataset.setDatasetPath(datasetPath);
        
        return dataset;
    }

    /**
     * 处理分类数据集
     */
    private void processClassificationDataset(Dataset dataset) {
        DataFrame df = dataset.getDataFrame();
        
        // 获取类别信息
        String[] columnNames = df.names();
        String targetColumn = columnNames[columnNames.length - 1]; // 假设最后一列是目标
        
        // 统计类别数量
        Set<Object> uniqueClasses = new HashSet<>();
        for (int i = 0; i < df.nrow(); i++) {
            uniqueClasses.add(df.get(i, targetColumn));
        }
        
        dataset.setClassCount(uniqueClasses.size());
        dataset.setTargetColumn(targetColumn);
        
        log.debug("分类数据集处理完成 - 类别数: {}", uniqueClasses.size());
    }

    /**
     * 处理回归数据集
     */
    private void processRegressionDataset(Dataset dataset) {
        DataFrame df = dataset.getDataFrame();
        String[] columnNames = df.names();
        String targetColumn = columnNames[columnNames.length - 1];
        
        dataset.setTargetColumn(targetColumn);
        
        log.debug("回归数据集处理完成");
    }

    /**
     * 处理聚类数据集
     */
    private void processClusteringDataset(Dataset dataset) {
        // 聚类不需要目标列
        dataset.setFeatureCount(dataset.getDataFrame().ncol());
        
        log.debug("聚类数据集处理完成");
    }

    /**
     * 处理文本数据集
     */
    private void processTextDataset(Dataset dataset) {
        List<String> textData = dataset.getTextData();
        
        // 简化的文本特征提取（实际应该使用TF-IDF或词嵌入）
        Map<String, Integer> vocabulary = new HashMap<>();
        int vocabIndex = 0;
        
        // 构建词汇表
        for (String text : textData) {
            String[] words = text.toLowerCase().split("\\s+");
            for (String word : words) {
                if (!vocabulary.containsKey(word)) {
                    vocabulary.put(word, vocabIndex++);
                }
            }
        }
        
        dataset.setFeatureCount(vocabulary.size());
        dataset.setVocabulary(vocabulary);
        
        log.debug("文本数据集处理完成 - 词汇表大小: {}", vocabulary.size());
    }

    /**
     * 分割数据集
     */
    public void splitDataset(Dataset dataset, double validationSplit, double testSplit) {
        log.debug("分割数据集 - 验证集: {}, 测试集: {}", validationSplit, testSplit);
        
        int totalSamples = dataset.getTotalSamples();
        int testSize = (int) (totalSamples * testSplit);
        int validationSize = (int) (totalSamples * validationSplit);
        int trainSize = totalSamples - testSize - validationSize;
        
        // 创建随机索引
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < totalSamples; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, new Random(42)); // 固定种子确保可重现
        
        // 分割索引
        dataset.setTrainIndices(indices.subList(0, trainSize));
        dataset.setValidationIndices(indices.subList(trainSize, trainSize + validationSize));
        dataset.setTestIndices(indices.subList(trainSize + validationSize, totalSamples));
        
        log.debug("数据集分割完成 - 训练: {}, 验证: {}, 测试: {}", 
                trainSize, validationSize, testSize);
    }

    /**
     * 获取训练数据迭代器
     */
    public DataSetIterator getTrainingIterator(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return createDataSetIterator(dataset, dataset.getTrainIndices(), job.getBatchSize());
    }

    /**
     * 获取验证数据迭代器
     */
    public DataSetIterator getValidationIterator(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return createDataSetIterator(dataset, dataset.getValidationIndices(), job.getBatchSize());
    }

    /**
     * 获取测试数据迭代器
     */
    public DataSetIterator getTestIterator(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return createDataSetIterator(dataset, dataset.getTestIndices(), job.getBatchSize());
    }

    /**
     * 获取训练数据框
     */
    public DataFrame getTrainingDataFrame(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return filterDataFrame(dataset.getDataFrame(), dataset.getTrainIndices());
    }

    /**
     * 获取测试数据框
     */
    public DataFrame getTestDataFrame(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return filterDataFrame(dataset.getDataFrame(), dataset.getTestIndices());
    }

    /**
     * 获取Weka训练实例
     */
    public Instances getWekaTrainingInstances(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return filterWekaInstances(dataset.getWekaInstances(), dataset.getTrainIndices());
    }

    /**
     * 获取Weka测试实例
     */
    public Instances getWekaTestInstances(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        return filterWekaInstances(dataset.getWekaInstances(), dataset.getTestIndices());
    }

    /**
     * 获取聚类数据
     */
    public double[][] getClusteringData(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        DataFrame df = dataset.getDataFrame();
        
        double[][] data = new double[df.nrow()][df.ncol()];
        for (int i = 0; i < df.nrow(); i++) {
            for (int j = 0; j < df.ncol(); j++) {
                data[i][j] = df.getDouble(i, j);
            }
        }
        
        return data;
    }

    /**
     * 获取公式
     */
    public Formula getFormula(TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        String targetColumn = dataset.getTargetColumn();
        return Formula.lhs(targetColumn);
    }

    /**
     * 获取实际标签
     */
    public int[] getActualLabels(DataFrame dataFrame, TrainingJob job) {
        Dataset dataset = loadDataset(job.getDatasetPath(), job.getModelType());
        String targetColumn = dataset.getTargetColumn();
        
        int[] labels = new int[dataFrame.nrow()];
        for (int i = 0; i < dataFrame.nrow(); i++) {
            labels[i] = dataFrame.getInt(i, targetColumn);
        }
        
        return labels;
    }

    /**
     * 创建数据集迭代器
     */
    private DataSetIterator createDataSetIterator(Dataset dataset, List<Integer> indices, int batchSize) {
        // 简化实现，实际应该根据数据类型创建合适的迭代器
        List<DataSet> dataSets = new ArrayList<>();
        
        DataFrame df = dataset.getDataFrame();
        String targetColumn = dataset.getTargetColumn();
        
        for (int index : indices) {
            // 创建特征向量
            INDArray features = Nd4j.create(1, dataset.getFeatureCount());
            for (int j = 0; j < dataset.getFeatureCount(); j++) {
                features.putScalar(0, j, df.getDouble(index, j));
            }
            
            // 创建标签向量
            INDArray labels = Nd4j.create(1, dataset.getClassCount());
            int labelIndex = df.getInt(index, targetColumn);
            labels.putScalar(0, labelIndex, 1.0);
            
            dataSets.add(new DataSet(features, labels));
        }
        
        return new StandardDataSetIterator(dataSets, batchSize);
    }

    /**
     * 过滤数据框
     */
    private DataFrame filterDataFrame(DataFrame original, List<Integer> indices) {
        // 简化实现，实际应该创建过滤后的数据框
        return original; // 暂时返回原始数据框
    }

    /**
     * 过滤Weka实例
     */
    private Instances filterWekaInstances(Instances original, List<Integer> indices) {
        Instances filtered = new Instances(original, indices.size());
        for (int index : indices) {
            filtered.add(original.instance(index));
        }
        return filtered;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        datasetCache.clear();
        log.info("数据集缓存已清理");
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_size", datasetCache.size());
        stats.put("max_cache_size", maxCacheSize);
        stats.put("cached_datasets", new ArrayList<>(datasetCache.keySet()));
        return stats;
    }
}