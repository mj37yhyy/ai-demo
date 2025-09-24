package com.textaudit.trainer.controller;

import com.textaudit.trainer.entity.Dataset;
import com.textaudit.trainer.service.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 数据集管理控制器
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
@Tag(name = "数据集管理", description = "数据集的上传、处理、分割和管理")
public class DatasetController {
    
    private final DatasetService datasetService;
    
    /**
     * 上传数据集文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传数据集文件", description = "上传数据集文件并进行预处理")
    public ResponseEntity<Dataset> uploadDataset(
            @Parameter(description = "数据集文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "数据集名称") @RequestParam("name") String name,
            @Parameter(description = "模型类型") @RequestParam("modelType") Dataset.ModelType modelType,
            @Parameter(description = "目标列名") @RequestParam(value = "targetColumn", required = false) String targetColumn,
            @Parameter(description = "是否包含标题行") @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader,
            @Parameter(description = "分隔符") @RequestParam(value = "separator", defaultValue = ",") String separator) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Dataset dataset = datasetService.uploadAndProcessDataset(
                file, name, modelType, targetColumn, hasHeader, separator);
            log.info("数据集上传成功: {}", name);
            return ResponseEntity.ok(dataset);
        } catch (IOException e) {
            log.error("数据集上传失败: {}", name, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取数据集信息
     */
    @GetMapping("/{datasetPath:.+}")
    @Operation(summary = "获取数据集信息", description = "根据路径获取数据集的详细信息")
    public ResponseEntity<Dataset> getDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            Dataset dataset = datasetService.loadDataset(datasetPath);
            return ResponseEntity.ok(dataset);
        } catch (Exception e) {
            log.error("获取数据集信息失败: {}", datasetPath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取数据集列表
     */
    @GetMapping
    @Operation(summary = "获取数据集列表", description = "获取所有可用的数据集列表")
    public ResponseEntity<List<String>> getDatasetList() {
        try {
            List<String> datasets = datasetService.listDatasets();
            return ResponseEntity.ok(datasets);
        } catch (Exception e) {
            log.error("获取数据集列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据模型类型获取数据集列表
     */
    @GetMapping("/type/{modelType}")
    @Operation(summary = "根据模型类型获取数据集列表", description = "获取指定模型类型的数据集列表")
    public ResponseEntity<List<String>> getDatasetsByType(
            @Parameter(description = "模型类型") @PathVariable Dataset.ModelType modelType) {
        
        try {
            List<String> datasets = datasetService.listDatasetsByType(modelType);
            return ResponseEntity.ok(datasets);
        } catch (Exception e) {
            log.error("根据模型类型获取数据集列表失败: {}", modelType, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 分割数据集
     */
    @PostMapping("/{datasetPath:.+}/split")
    @Operation(summary = "分割数据集", description = "将数据集分割为训练集、验证集和测试集")
    public ResponseEntity<Dataset> splitDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath,
            @Parameter(description = "训练集比例") @RequestParam(defaultValue = "0.7") double trainRatio,
            @Parameter(description = "验证集比例") @RequestParam(defaultValue = "0.15") double validationRatio,
            @Parameter(description = "测试集比例") @RequestParam(defaultValue = "0.15") double testRatio,
            @Parameter(description = "随机种子") @RequestParam(defaultValue = "42") long seed) {
        
        if (Math.abs(trainRatio + validationRatio + testRatio - 1.0) > 0.001) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Dataset dataset = datasetService.loadDataset(datasetPath);
            Dataset splitDataset = datasetService.splitDataset(dataset, trainRatio, validationRatio, testRatio, seed);
            log.info("数据集分割成功: {}", datasetPath);
            return ResponseEntity.ok(splitDataset);
        } catch (Exception e) {
            log.error("数据集分割失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 预处理数据集
     */
    @PostMapping("/{datasetPath:.+}/preprocess")
    @Operation(summary = "预处理数据集", description = "对数据集进行预处理操作")
    public ResponseEntity<Dataset> preprocessDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath,
            @Parameter(description = "预处理配置") @RequestBody Dataset.DataPreprocessingConfig config) {
        
        try {
            Dataset dataset = datasetService.loadDataset(datasetPath);
            Dataset preprocessedDataset = datasetService.preprocessDataset(dataset, config);
            log.info("数据集预处理成功: {}", datasetPath);
            return ResponseEntity.ok(preprocessedDataset);
        } catch (Exception e) {
            log.error("数据集预处理失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取数据集统计信息
     */
    @GetMapping("/{datasetPath:.+}/statistics")
    @Operation(summary = "获取数据集统计信息", description = "获取数据集的详细统计信息")
    public ResponseEntity<Dataset.DatasetStatistics> getDatasetStatistics(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            Dataset dataset = datasetService.loadDataset(datasetPath);
            Dataset.DatasetStatistics statistics = dataset.getStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取数据集统计信息失败: {}", datasetPath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取数据集样本
     */
    @GetMapping("/{datasetPath:.+}/sample")
    @Operation(summary = "获取数据集样本", description = "获取数据集的样本数据")
    public ResponseEntity<Map<String, Object>> getDatasetSample(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath,
            @Parameter(description = "样本数量") @RequestParam(defaultValue = "10") int sampleSize) {
        
        try {
            Map<String, Object> sample = datasetService.getDatasetSample(datasetPath, sampleSize);
            return ResponseEntity.ok(sample);
        } catch (Exception e) {
            log.error("获取数据集样本失败: {}", datasetPath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 验证数据集
     */
    @PostMapping("/{datasetPath:.+}/validate")
    @Operation(summary = "验证数据集", description = "验证数据集的格式和完整性")
    public ResponseEntity<Map<String, Object>> validateDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath,
            @Parameter(description = "模型类型") @RequestParam Dataset.ModelType modelType) {
        
        try {
            Map<String, Object> validationResult = datasetService.validateDataset(datasetPath, modelType);
            return ResponseEntity.ok(validationResult);
        } catch (Exception e) {
            log.error("验证数据集失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 下载数据集
     */
    @GetMapping("/{datasetPath:.+}/download")
    @Operation(summary = "下载数据集", description = "下载指定的数据集文件")
    public ResponseEntity<Resource> downloadDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            Resource resource = datasetService.loadDatasetAsResource(datasetPath);
            String filename = datasetPath.substring(datasetPath.lastIndexOf('/') + 1);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载数据集失败: {}", datasetPath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除数据集
     */
    @DeleteMapping("/{datasetPath:.+}")
    @Operation(summary = "删除数据集", description = "删除指定的数据集文件")
    public ResponseEntity<String> deleteDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            boolean deleted = datasetService.deleteDataset(datasetPath);
            if (deleted) {
                log.info("数据集删除成功: {}", datasetPath);
                return ResponseEntity.ok("数据集删除成功");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除数据集失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().body("删除数据集失败: " + e.getMessage());
        }
    }
    
    /**
     * 缓存数据集
     */
    @PostMapping("/{datasetPath:.+}/cache")
    @Operation(summary = "缓存数据集", description = "将数据集加载到缓存中")
    public ResponseEntity<String> cacheDataset(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            datasetService.cacheDataset(datasetPath);
            log.info("数据集缓存成功: {}", datasetPath);
            return ResponseEntity.ok("数据集缓存成功");
        } catch (Exception e) {
            log.error("缓存数据集失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().body("缓存数据集失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除数据集缓存
     */
    @DeleteMapping("/{datasetPath:.+}/cache")
    @Operation(summary = "清除数据集缓存", description = "从缓存中移除数据集")
    public ResponseEntity<String> clearDatasetCache(
            @Parameter(description = "数据集路径") @PathVariable String datasetPath) {
        
        try {
            datasetService.clearDatasetCache(datasetPath);
            log.info("数据集缓存清除成功: {}", datasetPath);
            return ResponseEntity.ok("数据集缓存清除成功");
        } catch (Exception e) {
            log.error("清除数据集缓存失败: {}", datasetPath, e);
            return ResponseEntity.internalServerError().body("清除数据集缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除所有缓存
     */
    @DeleteMapping("/cache/clear-all")
    @Operation(summary = "清除所有缓存", description = "清除所有数据集缓存")
    public ResponseEntity<String> clearAllCache() {
        try {
            datasetService.clearAllCache();
            log.info("所有数据集缓存清除成功");
            return ResponseEntity.ok("所有数据集缓存清除成功");
        } catch (Exception e) {
            log.error("清除所有数据集缓存失败", e);
            return ResponseEntity.internalServerError().body("清除所有数据集缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/cache/statistics")
    @Operation(summary = "获取缓存统计信息", description = "获取数据集缓存的统计信息")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            Map<String, Object> statistics = datasetService.getCacheStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}