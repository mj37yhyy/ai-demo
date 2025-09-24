package com.textaudit.trainer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.model.TrainingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import smile.io.Read;
import smile.io.Write;
import weka.core.SerializationHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 模型存储服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelStorageService {
    
    private final ObjectMapper objectMapper;
    
    @Value("${app.model.storage.path:/app/models}")
    private String modelStoragePath;
    
    @Value("${app.model.storage.compress:true}")
    private boolean compressModels;
    
    @Value("${app.model.storage.backup:true}")
    private boolean enableBackup;
    
    @Value("${app.model.storage.max-versions:5}")
    private int maxVersions;
    
    /**
     * 保存深度学习模型
     */
    public String saveDeepLearningModel(MultiLayerNetwork model, TrainingJob job, 
                                       TrainingResult result) {
        try {
            String modelPath = generateModelPath(job, "dl4j");
            Path fullPath = Paths.get(modelPath);
            
            // 创建目录
            Files.createDirectories(fullPath.getParent());
            
            // 保存模型
            if (compressModels) {
                try (FileOutputStream fos = new FileOutputStream(modelPath + ".gz");
                     GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    ModelSerializer.writeModel(model, gzos, true);
                }
                modelPath += ".gz";
            } else {
                ModelSerializer.writeModel(model, new File(modelPath), true);
            }
            
            // 保存模型元数据
            saveModelMetadata(job, result, modelPath);
            
            // 更新训练任务
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));
            
            log.info("深度学习模型保存成功: {}", modelPath);
            return modelPath;
            
        } catch (Exception e) {
            log.error("保存深度学习模型失败", e);
            throw new RuntimeException("保存模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 保存传统机器学习模型
     */
    public String saveMachineLearningModel(Object model, TrainingJob job, 
                                          TrainingResult result) {
        try {
            String modelPath = generateModelPath(job, "smile");
            Path fullPath = Paths.get(modelPath);
            
            // 创建目录
            Files.createDirectories(fullPath.getParent());
            
            // 保存模型
            if (compressModels) {
                try (FileOutputStream fos = new FileOutputStream(modelPath + ".gz");
                     GZIPOutputStream gzos = new GZIPOutputStream(fos);
                     ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
                    oos.writeObject(model);
                }
                modelPath += ".gz";
            } else {
                try (FileOutputStream fos = new FileOutputStream(modelPath);
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeObject(model);
                }
            }
            
            // 保存模型元数据
            saveModelMetadata(job, result, modelPath);
            
            // 更新训练任务
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));
            
            log.info("传统机器学习模型保存成功: {}", modelPath);
            return modelPath;
            
        } catch (Exception e) {
            log.error("保存传统机器学习模型失败", e);
            throw new RuntimeException("保存模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 保存Weka模型
     */
    public String saveWekaModel(weka.classifiers.Classifier model, TrainingJob job, 
                               TrainingResult result) {
        try {
            String modelPath = generateModelPath(job, "weka");
            Path fullPath = Paths.get(modelPath);
            
            // 创建目录
            Files.createDirectories(fullPath.getParent());
            
            // 保存模型
            if (compressModels) {
                try (FileOutputStream fos = new FileOutputStream(modelPath + ".gz");
                     GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    SerializationHelper.write(gzos, model);
                }
                modelPath += ".gz";
            } else {
                SerializationHelper.write(modelPath, model);
            }
            
            // 保存模型元数据
            saveModelMetadata(job, result, modelPath);
            
            // 更新训练任务
            job.setModelPath(modelPath);
            job.setModelSize(getFileSize(modelPath));
            
            log.info("Weka模型保存成功: {}", modelPath);
            return modelPath;
            
        } catch (Exception e) {
            log.error("保存Weka模型失败", e);
            throw new RuntimeException("保存模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载深度学习模型
     */
    public MultiLayerNetwork loadDeepLearningModel(String modelPath) {
        try {
            log.info("加载深度学习模型: {}", modelPath);
            
            if (modelPath.endsWith(".gz")) {
                try (FileInputStream fis = new FileInputStream(modelPath);
                     GZIPInputStream gzis = new GZIPInputStream(fis)) {
                    return ModelSerializer.restoreMultiLayerNetwork(gzis);
                }
            } else {
                return ModelSerializer.restoreMultiLayerNetwork(new File(modelPath));
            }
            
        } catch (Exception e) {
            log.error("加载深度学习模型失败: {}", modelPath, e);
            throw new RuntimeException("加载模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载传统机器学习模型
     */
    public Object loadMachineLearningModel(String modelPath) {
        try {
            log.info("加载传统机器学习模型: {}", modelPath);
            
            if (modelPath.endsWith(".gz")) {
                try (FileInputStream fis = new FileInputStream(modelPath);
                     GZIPInputStream gzis = new GZIPInputStream(fis);
                     ObjectInputStream ois = new ObjectInputStream(gzis)) {
                    return ois.readObject();
                }
            } else {
                try (FileInputStream fis = new FileInputStream(modelPath);
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    return ois.readObject();
                }
            }
            
        } catch (Exception e) {
            log.error("加载传统机器学习模型失败: {}", modelPath, e);
            throw new RuntimeException("加载模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载Weka模型
     */
    public weka.classifiers.Classifier loadWekaModel(String modelPath) {
        try {
            log.info("加载Weka模型: {}", modelPath);
            
            if (modelPath.endsWith(".gz")) {
                try (FileInputStream fis = new FileInputStream(modelPath);
                     GZIPInputStream gzis = new GZIPInputStream(fis)) {
                    return (weka.classifiers.Classifier) SerializationHelper.read(gzis);
                }
            } else {
                return (weka.classifiers.Classifier) SerializationHelper.read(modelPath);
            }
            
        } catch (Exception e) {
            log.error("加载Weka模型失败: {}", modelPath, e);
            throw new RuntimeException("加载模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除模型
     */
    public boolean deleteModel(String modelPath) {
        try {
            Path path = Paths.get(modelPath);
            boolean deleted = Files.deleteIfExists(path);
            
            // 删除元数据文件
            String metadataPath = modelPath.replace(".model", ".metadata.json");
            if (metadataPath.endsWith(".gz")) {
                metadataPath = metadataPath.replace(".gz", "");
            }
            Files.deleteIfExists(Paths.get(metadataPath));
            
            log.info("模型删除{}: {}", deleted ? "成功" : "失败", modelPath);
            return deleted;
            
        } catch (Exception e) {
            log.error("删除模型失败: {}", modelPath, e);
            return false;
        }
    }
    
    /**
     * 备份模型
     */
    public String backupModel(String modelPath) {
        if (!enableBackup) {
            return null;
        }
        
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupPath = modelPath.replace(".model", "_backup_" + timestamp + ".model");
            
            Files.copy(Paths.get(modelPath), Paths.get(backupPath));
            
            // 备份元数据
            String metadataPath = modelPath.replace(".model", ".metadata.json");
            String backupMetadataPath = backupPath.replace(".model", ".metadata.json");
            if (Files.exists(Paths.get(metadataPath))) {
                Files.copy(Paths.get(metadataPath), Paths.get(backupMetadataPath));
            }
            
            log.info("模型备份成功: {} -> {}", modelPath, backupPath);
            
            // 清理旧备份
            cleanupOldBackups(modelPath);
            
            return backupPath;
            
        } catch (Exception e) {
            log.error("备份模型失败: {}", modelPath, e);
            return null;
        }
    }
    
    /**
     * 获取模型信息
     */
    public ModelInfo getModelInfo(String modelPath) {
        try {
            String metadataPath = modelPath.replace(".model", ".metadata.json");
            if (metadataPath.endsWith(".gz")) {
                metadataPath = metadataPath.replace(".gz", "");
            }
            
            if (!Files.exists(Paths.get(metadataPath))) {
                return ModelInfo.builder()
                        .modelPath(modelPath)
                        .exists(Files.exists(Paths.get(modelPath)))
                        .size(getFileSize(modelPath))
                        .build();
            }
            
            String metadataJson = Files.readString(Paths.get(metadataPath));
            ModelMetadata metadata = objectMapper.readValue(metadataJson, ModelMetadata.class);
            
            return ModelInfo.builder()
                    .modelPath(modelPath)
                    .exists(Files.exists(Paths.get(modelPath)))
                    .size(getFileSize(modelPath))
                    .metadata(metadata)
                    .build();
            
        } catch (Exception e) {
            log.error("获取模型信息失败: {}", modelPath, e);
            return ModelInfo.builder()
                    .modelPath(modelPath)
                    .exists(false)
                    .error("获取模型信息失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 列出所有模型
     */
    public List<ModelInfo> listModels() {
        List<ModelInfo> models = new ArrayList<>();
        
        try {
            Path storagePath = Paths.get(modelStoragePath);
            if (!Files.exists(storagePath)) {
                return models;
            }
            
            Files.walk(storagePath)
                    .filter(path -> path.toString().endsWith(".model") || 
                                  path.toString().endsWith(".model.gz"))
                    .forEach(path -> {
                        ModelInfo info = getModelInfo(path.toString());
                        models.add(info);
                    });
            
        } catch (Exception e) {
            log.error("列出模型失败", e);
        }
        
        return models;
    }
    
    /**
     * 生成模型路径
     */
    private String generateModelPath(TrainingJob job, String framework) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s_%s_%s_%s.model", 
                job.getName().replaceAll("[^a-zA-Z0-9]", "_"),
                job.getModelType().toString().toLowerCase(),
                framework,
                timestamp);
        
        return Paths.get(modelStoragePath, job.getModelType().toString().toLowerCase(), filename)
                .toString();
    }
    
    /**
     * 保存模型元数据
     */
    private void saveModelMetadata(TrainingJob job, TrainingResult result, String modelPath) {
        try {
            ModelMetadata metadata = ModelMetadata.builder()
                    .jobId(job.getId())
                    .jobName(job.getName())
                    .modelType(job.getModelType())
                    .algorithm(job.getAlgorithm())
                    .modelPath(modelPath)
                    .createdAt(LocalDateTime.now())
                    .trainingSamples(job.getTrainingSamples())
                    .validationSamples(job.getValidationSamples())
                    .testSamples(job.getTestSamples())
                    .hyperparameters(job.getHyperparameters())
                    .accuracy(result.getAccuracy())
                    .precision(result.getPrecision())
                    .recall(result.getRecall())
                    .f1Score(result.getF1Score())
                    .trainingDuration(result.getTrainingDuration())
                    .modelSize(getFileSize(modelPath))
                    .build();
            
            String metadataPath = modelPath.replace(".model", ".metadata.json");
            if (metadataPath.endsWith(".gz")) {
                metadataPath = metadataPath.replace(".gz", "");
            }
            
            String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata);
            Files.writeString(Paths.get(metadataPath), metadataJson);
            
        } catch (Exception e) {
            log.error("保存模型元数据失败", e);
        }
    }
    
    /**
     * 获取文件大小
     */
    private Long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 清理旧备份
     */
    private void cleanupOldBackups(String modelPath) {
        try {
            Path parentDir = Paths.get(modelPath).getParent();
            String baseFileName = Paths.get(modelPath).getFileName().toString()
                    .replace(".model", "");
            
            List<Path> backupFiles = new ArrayList<>();
            Files.list(parentDir)
                    .filter(path -> path.getFileName().toString().startsWith(baseFileName + "_backup_"))
                    .forEach(backupFiles::add);
            
            if (backupFiles.size() > maxVersions) {
                backupFiles.sort((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                    } catch (Exception e) {
                        return 0;
                    }
                });
                
                int toDelete = backupFiles.size() - maxVersions;
                for (int i = 0; i < toDelete; i++) {
                    Files.deleteIfExists(backupFiles.get(i));
                    log.info("删除旧备份: {}", backupFiles.get(i));
                }
            }
            
        } catch (Exception e) {
            log.error("清理旧备份失败", e);
        }
    }
    
    /**
     * 模型元数据
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelMetadata {
        private String jobId;
        private String jobName;
        private TrainingJob.ModelType modelType;
        private TrainingJob.Algorithm algorithm;
        private String modelPath;
        private LocalDateTime createdAt;
        private Integer trainingSamples;
        private Integer validationSamples;
        private Integer testSamples;
        private Map<String, Object> hyperparameters;
        private Double accuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
        private Long trainingDuration;
        private Long modelSize;
        private String version;
        private String description;
        private Map<String, Object> additionalInfo;
    }
    
    /**
     * 模型信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelInfo {
        private String modelPath;
        private Boolean exists;
        private Long size;
        private ModelMetadata metadata;
        private String error;
        private LocalDateTime lastModified;
        
        public String getFormattedSize() {
            if (size == null) return "未知";
            
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}