package com.textaudit.preprocessor.controller;

import com.textaudit.preprocessor.dto.ProcessingResult;
import com.textaudit.preprocessor.dto.TextFeatures;
import com.textaudit.preprocessor.dto.TokenizationResult;
import com.textaudit.preprocessor.entity.ProcessedText;
import com.textaudit.preprocessor.service.DataAugmentationService;
import com.textaudit.preprocessor.service.FeatureExtractionService;
import com.textaudit.preprocessor.service.TextProcessingService;
import com.textaudit.preprocessor.repository.ProcessedTextRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据预处理控制器
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/preprocessor")
@RequiredArgsConstructor
@Tag(name = "数据预处理", description = "文本数据预处理相关接口")
public class PreprocessorController {

    private final TextProcessingService textProcessingService;
    private final FeatureExtractionService featureExtractionService;
    private final DataAugmentationService dataAugmentationService;
    private final ProcessedTextRepository processedTextRepository;

    /**
     * 处理单个文本
     */
    @PostMapping("/process")
    @Operation(summary = "处理单个文本", description = "对输入文本进行清洗、分词和特征提取")
    public ResponseEntity<ProcessingResult> processText(@Valid @RequestBody ProcessTextRequest request) {
        log.info("开始处理文本，长度: {}", request.getText().length());
        
        try {
            ProcessingResult result = textProcessingService.processText(
                request.getText(), 
                request.getDataSource()
            );
            
            log.info("文本处理完成，处理耗时: {}ms", result.getProcessingTime());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("文本处理失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                ProcessingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build()
            );
        }
    }

    /**
     * 批量处理文本
     */
    @PostMapping("/process/batch")
    @Operation(summary = "批量处理文本", description = "批量处理多个文本")
    public ResponseEntity<List<ProcessingResult>> batchProcessTexts(@Valid @RequestBody BatchProcessRequest request) {
        log.info("开始批量处理文本，数量: {}", request.getTexts().size());
        
        try {
            List<ProcessingResult> results = textProcessingService.batchProcessTexts(
                request.getTexts(), 
                request.getDataSource()
            );
            
            long successCount = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            log.info("批量处理完成，成功: {}, 失败: {}", successCount, results.size() - successCount);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("批量处理失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 提取文本特征
     */
    @PostMapping("/features/extract")
    @Operation(summary = "提取文本特征", description = "提取文本的各种特征")
    public ResponseEntity<TextFeatures> extractFeatures(@Valid @RequestBody ExtractFeaturesRequest request) {
        log.info("开始提取文本特征");
        
        try {
            // 先进行分词
            ProcessingResult processingResult = textProcessingService.processText(
                request.getText(), 
                "feature_extraction"
            );
            
            if (!processingResult.isSuccess()) {
                return ResponseEntity.badRequest().build();
            }
            
            // 提取特征
            TextFeatures features = featureExtractionService.extractFeatures(
                request.getText(), 
                processingResult.getTokenizationResult()
            );
            
            log.info("特征提取完成，特征维度: {}", features.getFeatureDimension());
            return ResponseEntity.ok(features);
        } catch (Exception e) {
            log.error("特征提取失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 数据增强
     */
    @PostMapping("/augment")
    @Operation(summary = "数据增强", description = "对文本进行数据增强")
    public ResponseEntity<List<String>> augmentText(@Valid @RequestBody AugmentTextRequest request) {
        log.info("开始数据增强，目标数量: {}", request.getNumAugmentations());
        
        try {
            List<String> augmentedTexts = dataAugmentationService.augmentText(
                request.getText(), 
                request.getNumAugmentations()
            );
            
            log.info("数据增强完成，生成数量: {}", augmentedTexts.size());
            return ResponseEntity.ok(augmentedTexts);
        } catch (Exception e) {
            log.error("数据增强失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 查询预处理文本
     */
    @GetMapping("/texts")
    @Operation(summary = "查询预处理文本", description = "分页查询预处理文本")
    public ResponseEntity<Page<ProcessedText>> getProcessedTexts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") @Max(100) int size,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "数据源") @RequestParam(required = false) String dataSource,
            @Parameter(description = "标签") @RequestParam(required = false) String label) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ProcessedText> result;
        if (dataSource != null) {
            result = processedTextRepository.findByDataSource(dataSource, pageable);
        } else if (label != null) {
            result = processedTextRepository.findByLabelsContaining(label, pageable);
        } else {
            result = processedTextRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 根据ID查询预处理文本
     */
    @GetMapping("/texts/{id}")
    @Operation(summary = "根据ID查询预处理文本", description = "根据ID查询单个预处理文本")
    public ResponseEntity<ProcessedText> getProcessedTextById(@PathVariable Long id) {
        Optional<ProcessedText> text = processedTextRepository.findById(id);
        return text.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据原始文本ID查询预处理文本
     */
    @GetMapping("/texts/raw/{rawTextId}")
    @Operation(summary = "根据原始文本ID查询", description = "根据原始文本ID查询预处理文本")
    public ResponseEntity<ProcessedText> getProcessedTextByRawId(@PathVariable Long rawTextId) {
        Optional<ProcessedText> text = processedTextRepository.findByRawTextId(rawTextId);
        return text.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 搜索文本
     */
    @GetMapping("/texts/search")
    @Operation(summary = "搜索文本", description = "根据关键词搜索文本")
    public ResponseEntity<Page<ProcessedText>> searchTexts(
            @Parameter(description = "关键词") @RequestParam @NotBlank String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ProcessedText> result = processedTextRepository.findByKeyword(keyword, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取统计信息", description = "获取预处理数据的统计信息")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = Map.of(
                "totalTexts", processedTextRepository.countTotal(),
                "averageTextLength", processedTextRepository.getAverageTextLength(),
                "averageTokenCount", processedTextRepository.getAverageTokenCount(),
                "averageFeatureDimension", processedTextRepository.getAverageFeatureDimension(),
                "dataSourceCounts", processedTextRepository.countByDataSource(),
                "labelCounts", processedTextRepository.countByLabels(),
                "dataQuality", processedTextRepository.getDataQualityStatistics(),
                "featureExtractionStats", featureExtractionService.getFeatureStatistics(),
                "augmentationStats", dataAugmentationService.getAugmentationStatistics()
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除预处理文本
     */
    @DeleteMapping("/texts/{id}")
    @Operation(summary = "删除预处理文本", description = "根据ID删除预处理文本")
    public ResponseEntity<Void> deleteProcessedText(@PathVariable Long id) {
        try {
            if (processedTextRepository.existsById(id)) {
                processedTextRepository.deleteById(id);
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除预处理文本失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查服务健康状态")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now(),
            "service", "data-preprocessor",
            "version", "1.0.0"
        );
        return ResponseEntity.ok(health);
    }

    // DTO类定义

    /**
     * 处理文本请求
     */
    public static class ProcessTextRequest {
        @NotBlank(message = "文本内容不能为空")
        private String text;
        
        private String dataSource = "api";

        // getters and setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }

    /**
     * 批量处理请求
     */
    public static class BatchProcessRequest {
        @NotNull(message = "文本列表不能为空")
        private List<String> texts;
        
        private String dataSource = "api";

        // getters and setters
        public List<String> getTexts() { return texts; }
        public void setTexts(List<String> texts) { this.texts = texts; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }

    /**
     * 特征提取请求
     */
    public static class ExtractFeaturesRequest {
        @NotBlank(message = "文本内容不能为空")
        private String text;

        // getters and setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /**
     * 数据增强请求
     */
    public static class AugmentTextRequest {
        @NotBlank(message = "文本内容不能为空")
        private String text;
        
        @Min(value = 1, message = "增强数量至少为1")
        @Max(value = 10, message = "增强数量最多为10")
        private int numAugmentations = 3;

        // getters and setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public int getNumAugmentations() { return numAugmentations; }
        public void setNumAugmentations(int numAugmentations) { this.numAugmentations = numAugmentations; }
    }
}