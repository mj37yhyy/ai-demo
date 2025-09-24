package com.textaudit.trainer.controller;

import com.textaudit.trainer.service.ModelStorageService;
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
 * 模型管理控制器
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
@Tag(name = "模型管理", description = "模型的存储、加载、删除和信息查询")
public class ModelController {
    
    private final ModelStorageService modelStorageService;
    
    /**
     * 上传模型文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传模型文件", description = "上传训练好的模型文件")
    public ResponseEntity<String> uploadModel(
            @Parameter(description = "模型文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "模型名称") @RequestParam("modelName") String modelName,
            @Parameter(description = "模型类型") @RequestParam("modelType") String modelType,
            @Parameter(description = "模型描述") @RequestParam(value = "description", required = false) String description) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("模型文件不能为空");
        }
        
        try {
            // 保存上传的模型文件
            String savedPath = modelStorageService.saveUploadedModel(file, modelName, modelType, description);
            log.info("模型文件上传成功: {}", savedPath);
            return ResponseEntity.ok("模型文件上传成功: " + savedPath);
        } catch (IOException e) {
            log.error("模型文件上传失败", e);
            return ResponseEntity.internalServerError().body("模型文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载模型文件
     */
    @GetMapping("/{modelName}/download")
    @Operation(summary = "下载模型文件", description = "下载指定的模型文件")
    public ResponseEntity<Resource> downloadModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            Resource resource = modelStorageService.loadModelAsResource(modelName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + modelName + ".zip\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载模型文件失败: {}", modelName, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取模型信息
     */
    @GetMapping("/{modelName}")
    @Operation(summary = "获取模型信息", description = "获取指定模型的详细信息")
    public ResponseEntity<ModelStorageService.ModelInfo> getModelInfo(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            ModelStorageService.ModelInfo modelInfo = modelStorageService.getModelInfo(modelName);
            return ResponseEntity.ok(modelInfo);
        } catch (Exception e) {
            log.error("获取模型信息失败: {}", modelName, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取模型列表
     */
    @GetMapping
    @Operation(summary = "获取模型列表", description = "获取所有已保存的模型列表")
    public ResponseEntity<List<ModelStorageService.ModelInfo>> getModelList() {
        try {
            List<ModelStorageService.ModelInfo> models = modelStorageService.listModels();
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据类型获取模型列表
     */
    @GetMapping("/type/{modelType}")
    @Operation(summary = "根据类型获取模型列表", description = "获取指定类型的模型列表")
    public ResponseEntity<List<ModelStorageService.ModelInfo>> getModelsByType(
            @Parameter(description = "模型类型") @PathVariable String modelType) {
        
        try {
            List<ModelStorageService.ModelInfo> models = modelStorageService.listModelsByType(modelType);
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("根据类型获取模型列表失败: {}", modelType, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除模型
     */
    @DeleteMapping("/{modelName}")
    @Operation(summary = "删除模型", description = "删除指定的模型文件")
    public ResponseEntity<String> deleteModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            boolean deleted = modelStorageService.deleteModel(modelName);
            if (deleted) {
                log.info("模型删除成功: {}", modelName);
                return ResponseEntity.ok("模型删除成功");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除模型失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("删除模型失败: " + e.getMessage());
        }
    }
    
    /**
     * 备份模型
     */
    @PostMapping("/{modelName}/backup")
    @Operation(summary = "备份模型", description = "创建模型的备份")
    public ResponseEntity<String> backupModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            String backupPath = modelStorageService.backupModel(modelName);
            log.info("模型备份成功: {} -> {}", modelName, backupPath);
            return ResponseEntity.ok("模型备份成功: " + backupPath);
        } catch (Exception e) {
            log.error("备份模型失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("备份模型失败: " + e.getMessage());
        }
    }
    
    /**
     * 恢复模型备份
     */
    @PostMapping("/{modelName}/restore")
    @Operation(summary = "恢复模型备份", description = "从备份恢复模型")
    public ResponseEntity<String> restoreModel(
            @Parameter(description = "模型名称") @PathVariable String modelName,
            @Parameter(description = "备份版本") @RequestParam(required = false) String version) {
        
        try {
            boolean restored = modelStorageService.restoreModel(modelName, version);
            if (restored) {
                log.info("模型恢复成功: {}", modelName);
                return ResponseEntity.ok("模型恢复成功");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("恢复模型失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("恢复模型失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取模型备份列表
     */
    @GetMapping("/{modelName}/backups")
    @Operation(summary = "获取模型备份列表", description = "获取指定模型的所有备份")
    public ResponseEntity<List<String>> getModelBackups(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            List<String> backups = modelStorageService.listModelBackups(modelName);
            return ResponseEntity.ok(backups);
        } catch (Exception e) {
            log.error("获取模型备份列表失败: {}", modelName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 清理旧备份
     */
    @DeleteMapping("/{modelName}/backups/cleanup")
    @Operation(summary = "清理旧备份", description = "清理指定模型的旧备份文件")
    public ResponseEntity<String> cleanupOldBackups(
            @Parameter(description = "模型名称") @PathVariable String modelName,
            @Parameter(description = "保留数量") @RequestParam(defaultValue = "5") int keepCount) {
        
        try {
            int cleanedCount = modelStorageService.cleanupOldBackups(modelName, keepCount);
            log.info("清理旧备份完成: {} 个文件被清理", cleanedCount);
            return ResponseEntity.ok(String.format("已清理 %d 个旧备份文件", cleanedCount));
        } catch (Exception e) {
            log.error("清理旧备份失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("清理旧备份失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证模型文件
     */
    @PostMapping("/{modelName}/validate")
    @Operation(summary = "验证模型文件", description = "验证模型文件的完整性和有效性")
    public ResponseEntity<Map<String, Object>> validateModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            Map<String, Object> validationResult = modelStorageService.validateModel(modelName);
            return ResponseEntity.ok(validationResult);
        } catch (Exception e) {
            log.error("验证模型文件失败: {}", modelName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取模型存储统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取模型存储统计信息", description = "获取模型存储的统计信息")
    public ResponseEntity<Map<String, Object>> getStorageStatistics() {
        try {
            Map<String, Object> statistics = modelStorageService.getStorageStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取模型存储统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 压缩模型
     */
    @PostMapping("/{modelName}/compress")
    @Operation(summary = "压缩模型", description = "压缩模型文件以节省存储空间")
    public ResponseEntity<String> compressModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            String compressedPath = modelStorageService.compressModel(modelName);
            log.info("模型压缩成功: {} -> {}", modelName, compressedPath);
            return ResponseEntity.ok("模型压缩成功: " + compressedPath);
        } catch (Exception e) {
            log.error("压缩模型失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("压缩模型失败: " + e.getMessage());
        }
    }
    
    /**
     * 解压模型
     */
    @PostMapping("/{modelName}/decompress")
    @Operation(summary = "解压模型", description = "解压缩模型文件")
    public ResponseEntity<String> decompressModel(
            @Parameter(description = "模型名称") @PathVariable String modelName) {
        
        try {
            String decompressedPath = modelStorageService.decompressModel(modelName);
            log.info("模型解压成功: {} -> {}", modelName, decompressedPath);
            return ResponseEntity.ok("模型解压成功: " + decompressedPath);
        } catch (Exception e) {
            log.error("解压模型失败: {}", modelName, e);
            return ResponseEntity.internalServerError().body("解压模型失败: " + e.getMessage());
        }
    }
}