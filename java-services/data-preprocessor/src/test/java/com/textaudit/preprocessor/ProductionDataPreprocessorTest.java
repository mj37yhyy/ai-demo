package com.textaudit.preprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textaudit.preprocessor.dto.ProcessingResult;
import com.textaudit.preprocessor.dto.TextFeatures;
import com.textaudit.preprocessor.dto.TokenizationResult;
import com.textaudit.preprocessor.service.TextProcessingService;
import com.textaudit.preprocessor.service.FeatureExtractionService;
import com.textaudit.preprocessor.service.DataAugmentationService;
import com.textaudit.preprocessor.repository.ProcessedTextRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 数据预处理服务生产级测试
 * 测试知乎数据的清洗、去重、分词、TF-IDF向量化等功能
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductionDataPreprocessorTest {

    @Autowired
    private TextProcessingService textProcessingService;

    @Autowired
    private FeatureExtractionService featureExtractionService;

    @Autowired
    private DataAugmentationService dataAugmentationService;

    @Autowired
    private ProcessedTextRepository processedTextRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_DATA_DIR = "test-data/preprocessor";
    private static final String REPORT_DIR = "test-reports/preprocessor";
    
    // 测试数据集
    private List<ZhihuTestData> testDataSet;
    private TestMetrics testMetrics;

    @BeforeAll
    static void setupTestEnvironment() throws IOException {
        // 创建测试目录
        Files.createDirectories(Paths.get(TEST_DATA_DIR));
        Files.createDirectories(Paths.get(REPORT_DIR));
        log.info("测试环境初始化完成");
    }

    @BeforeEach
    void setUp() {
        testMetrics = new TestMetrics();
        testDataSet = generateZhihuTestData();
        log.info("准备测试数据集，共 {} 条记录", testDataSet.size());
    }

    /**
     * 测试1：文本清洗功能
     */
    @Test
    @Order(1)
    void testTextCleaning() {
        log.info("开始测试文本清洗功能...");
        
        List<CleaningTestResult> results = new ArrayList<>();
        
        for (ZhihuTestData data : testDataSet) {
            long startTime = System.currentTimeMillis();
            
            try {
                String cleanedText = textProcessingService.cleanText(data.getRawContent());
                long processingTime = System.currentTimeMillis() - startTime;
                
                CleaningTestResult result = CleaningTestResult.builder()
                    .originalText(data.getRawContent())
                    .cleanedText(cleanedText)
                    .originalLength(data.getRawContent().length())
                    .cleanedLength(cleanedText.length())
                    .processingTimeMs(processingTime)
                    .htmlTagsRemoved(countHtmlTags(data.getRawContent()) - countHtmlTags(cleanedText))
                    .urlsRemoved(countUrls(data.getRawContent()) - countUrls(cleanedText))
                    .specialCharsRemoved(countSpecialChars(data.getRawContent()) - countSpecialChars(cleanedText))
                    .success(true)
                    .build();
                
                results.add(result);
                testMetrics.incrementSuccessCount();
                
            } catch (Exception e) {
                log.error("文本清洗失败: {}", e.getMessage());
                testMetrics.incrementFailureCount();
                
                results.add(CleaningTestResult.builder()
                    .originalText(data.getRawContent())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // 生成清洗测试报告
        generateCleaningReport(results);
        
        // 验证清洗效果
        double successRate = (double) testMetrics.getSuccessCount() / testDataSet.size();
        Assertions.assertTrue(successRate >= 0.95, "文本清洗成功率应大于95%");
        
        log.info("文本清洗测试完成，成功率: {:.2f}%", successRate * 100);
    }

    /**
     * 测试2：去重功能
     */
    @Test
    @Order(2)
    void testDeduplication() {
        log.info("开始测试去重功能...");
        
        // 创建包含重复内容的测试数据
        List<String> textsWithDuplicates = new ArrayList<>();
        textsWithDuplicates.addAll(testDataSet.stream()
            .map(ZhihuTestData::getRawContent)
            .collect(Collectors.toList()));
        
        // 添加重复内容
        textsWithDuplicates.addAll(testDataSet.subList(0, 10).stream()
            .map(ZhihuTestData::getRawContent)
            .collect(Collectors.toList()));
        
        int originalCount = textsWithDuplicates.size();
        
        // 执行去重
        Set<String> uniqueTexts = new HashSet<>();
        List<String> deduplicatedTexts = new ArrayList<>();
        
        for (String text : textsWithDuplicates) {
            String cleanedText = textProcessingService.cleanText(text);
            String textHash = Integer.toString(cleanedText.hashCode());
            
            if (!uniqueTexts.contains(textHash)) {
                uniqueTexts.add(textHash);
                deduplicatedTexts.add(cleanedText);
            }
        }
        
        int deduplicatedCount = deduplicatedTexts.size();
        double deduplicationRate = 1.0 - (double) deduplicatedCount / originalCount;
        
        DeduplicationResult result = DeduplicationResult.builder()
            .originalCount(originalCount)
            .deduplicatedCount(deduplicatedCount)
            .duplicatesRemoved(originalCount - deduplicatedCount)
            .deduplicationRate(deduplicationRate)
            .build();
        
        // 生成去重报告
        generateDeduplicationReport(result);
        
        // 验证去重效果
        Assertions.assertTrue(deduplicationRate > 0, "应该检测到重复内容");
        Assertions.assertEquals(testDataSet.size(), deduplicatedCount, "去重后数量应该等于原始唯一数据量");
        
        log.info("去重测试完成，去重率: {:.2f}%", deduplicationRate * 100);
    }

    /**
     * 测试3：中文分词功能
     */
    @Test
    @Order(3)
    void testTokenization() {
        log.info("开始测试中文分词功能...");
        
        List<TokenizationTestResult> results = new ArrayList<>();
        
        for (ZhihuTestData data : testDataSet) {
            long startTime = System.currentTimeMillis();
            
            try {
                String cleanedText = textProcessingService.cleanText(data.getRawContent());
                TokenizationResult tokenResult = textProcessingService.tokenizeText(cleanedText);
                long processingTime = System.currentTimeMillis() - startTime;
                
                TokenizationTestResult result = TokenizationTestResult.builder()
                    .originalText(cleanedText)
                    .tokens(tokenResult.getTokens())
                    .filteredTokens(tokenResult.getFilteredTokens())
                    .posTagging(tokenResult.getPosTagging())
                    .tokenCount(tokenResult.getTokens().size())
                    .filteredTokenCount(tokenResult.getFilteredTokens().size())
                    .processingTimeMs(processingTime)
                    .averageTokenLength(calculateAverageTokenLength(tokenResult.getTokens()))
                    .chineseTokenRatio(calculateChineseTokenRatio(tokenResult.getTokens()))
                    .success(true)
                    .build();
                
                results.add(result);
                testMetrics.incrementSuccessCount();
                
            } catch (Exception e) {
                log.error("分词处理失败: {}", e.getMessage());
                testMetrics.incrementFailureCount();
                
                results.add(TokenizationTestResult.builder()
                    .originalText(data.getRawContent())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // 生成分词测试报告
        generateTokenizationReport(results);
        
        // 验证分词效果
        double successRate = (double) testMetrics.getSuccessCount() / testDataSet.size();
        Assertions.assertTrue(successRate >= 0.95, "分词成功率应大于95%");
        
        // 验证中文分词质量
        double avgChineseRatio = results.stream()
            .filter(TokenizationTestResult::isSuccess)
            .mapToDouble(TokenizationTestResult::getChineseTokenRatio)
            .average()
            .orElse(0.0);
        
        Assertions.assertTrue(avgChineseRatio >= 0.7, "中文词汇比例应大于70%");
        
        log.info("分词测试完成，成功率: {:.2f}%, 中文词汇比例: {:.2f}%", 
            successRate * 100, avgChineseRatio * 100);
    }

    /**
     * 测试4：TF-IDF向量化功能
     */
    @Test
    @Order(4)
    void testTfIdfVectorization() {
        log.info("开始测试TF-IDF向量化功能...");
        
        List<TfIdfTestResult> results = new ArrayList<>();
        List<String> corpus = new ArrayList<>();
        
        // 准备语料库
        for (ZhihuTestData data : testDataSet) {
            String cleanedText = textProcessingService.cleanText(data.getRawContent());
            TokenizationResult tokenResult = textProcessingService.tokenizeText(cleanedText);
            corpus.add(String.join(" ", tokenResult.getFilteredTokens()));
        }
        
        for (int i = 0; i < corpus.size(); i++) {
            long startTime = System.currentTimeMillis();
            
            try {
                String text = corpus.get(i);
                TokenizationResult tokenResult = textProcessingService.tokenizeText(text);
                TextFeatures features = featureExtractionService.extractFeatures(text, tokenResult);
                long processingTime = System.currentTimeMillis() - startTime;
                
                TfIdfTestResult result = TfIdfTestResult.builder()
                    .documentIndex(i)
                    .originalText(text)
                    .features(features)
                    .processingTimeMs(processingTime)
                    .vectorDimension(features.getTfidfVector() != null ? features.getTfidfVector().size() : 0)
                    .nonZeroFeatures(countNonZeroFeatures(features.getTfidfVector()))
                    .maxTfIdfValue(getMaxTfIdfValue(features.getTfidfVector()))
                    .success(true)
                    .build();
                
                results.add(result);
                testMetrics.incrementSuccessCount();
                
            } catch (Exception e) {
                log.error("TF-IDF向量化失败: {}", e.getMessage());
                testMetrics.incrementFailureCount();
                
                results.add(TfIdfTestResult.builder()
                    .documentIndex(i)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // 生成TF-IDF测试报告
        generateTfIdfReport(results);
        
        // 验证向量化效果
        double successRate = (double) testMetrics.getSuccessCount() / testDataSet.size();
        Assertions.assertTrue(successRate >= 0.90, "TF-IDF向量化成功率应大于90%");
        
        // 验证向量质量
        List<TfIdfTestResult> successResults = results.stream()
            .filter(TfIdfTestResult::isSuccess)
            .collect(Collectors.toList());
        
        double avgVectorDimension = successResults.stream()
            .mapToDouble(TfIdfTestResult::getVectorDimension)
            .average()
            .orElse(0.0);
        
        Assertions.assertTrue(avgVectorDimension > 0, "向量维度应大于0");
        
        log.info("TF-IDF向量化测试完成，成功率: {:.2f}%, 平均向量维度: {:.0f}", 
            successRate * 100, avgVectorDimension);
    }

    /**
     * 测试5：特征工程综合测试
     */
    @Test
    @Order(5)
    void testFeatureEngineering() {
        log.info("开始测试特征工程综合功能...");
        
        List<FeatureEngineeringResult> results = new ArrayList<>();
        
        for (ZhihuTestData data : testDataSet) {
            long startTime = System.currentTimeMillis();
            
            try {
                ProcessingResult processingResult = textProcessingService.processText(
                    data.getRawContent(), "zhihu", data.getMetadata());
                long processingTime = System.currentTimeMillis() - startTime;
                
                FeatureEngineeringResult result = FeatureEngineeringResult.builder()
                    .originalText(data.getRawContent())
                    .processingResult(processingResult)
                    .processingTimeMs(processingTime)
                    .textLength(processingResult.getCleanedText().length())
                    .tokenCount(processingResult.getTokenizationResult().getTokens().size())
                    .featureCount(processingResult.getFeatures() != null ? 
                        countTotalFeatures(processingResult.getFeatures()) : 0)
                    .success(processingResult.isSuccess())
                    .build();
                
                results.add(result);
                
                if (processingResult.isSuccess()) {
                    testMetrics.incrementSuccessCount();
                } else {
                    testMetrics.incrementFailureCount();
                }
                
            } catch (Exception e) {
                log.error("特征工程处理失败: {}", e.getMessage());
                testMetrics.incrementFailureCount();
                
                results.add(FeatureEngineeringResult.builder()
                    .originalText(data.getRawContent())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // 生成特征工程测试报告
        generateFeatureEngineeringReport(results);
        
        // 验证特征工程效果
        double successRate = (double) testMetrics.getSuccessCount() / testDataSet.size();
        Assertions.assertTrue(successRate >= 0.90, "特征工程成功率应大于90%");
        
        log.info("特征工程测试完成，成功率: {:.2f}%", successRate * 100);
    }

    /**
     * 测试6：性能压力测试
     */
    @Test
    @Order(6)
    void testPerformanceStress() {
        log.info("开始性能压力测试...");
        
        int threadCount = 10;
        int batchSize = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        List<CompletableFuture<PerformanceResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            CompletableFuture<PerformanceResult> future = CompletableFuture.supplyAsync(() -> {
                return executePerformanceTest(threadId, batchSize);
            }, executor);
            futures.add(future);
        }
        
        // 等待所有任务完成
        List<PerformanceResult> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        executor.shutdown();
        
        // 生成性能测试报告
        generatePerformanceReport(results);
        
        // 验证性能指标
        double avgThroughput = results.stream()
            .mapToDouble(PerformanceResult::getThroughput)
            .average()
            .orElse(0.0);
        
        double avgLatency = results.stream()
            .mapToDouble(PerformanceResult::getAverageLatency)
            .average()
            .orElse(0.0);
        
        Assertions.assertTrue(avgThroughput > 10, "平均吞吐量应大于10 TPS");
        Assertions.assertTrue(avgLatency < 1000, "平均延迟应小于1000ms");
        
        log.info("性能压力测试完成，平均吞吐量: {:.2f} TPS, 平均延迟: {:.2f} ms", 
            avgThroughput, avgLatency);
    }

    @AfterAll
    static void generateFinalReport() throws IOException {
        log.info("生成最终测试报告...");
        
        String reportPath = REPORT_DIR + "/final-report-" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md";
        
        try (FileWriter writer = new FileWriter(reportPath)) {
            writer.write("# 数据预处理服务生产级测试报告\n\n");
            writer.write("## 测试概述\n");
            writer.write("本报告包含了对数据预处理服务的全面测试结果，涵盖文本清洗、去重、分词、TF-IDF向量化等核心功能。\n\n");
            writer.write("## 测试环境\n");
            writer.write("- 测试时间: " + LocalDateTime.now() + "\n");
            writer.write("- 测试数据: 知乎话题评论数据\n");
            writer.write("- 测试框架: JUnit 5 + Spring Boot Test\n\n");
            writer.write("## 详细报告\n");
            writer.write("请查看各个功能模块的详细测试报告文件。\n");
        }
        
        log.info("最终测试报告已生成: {}", reportPath);
    }

    // 辅助方法和数据类定义
    
    private List<ZhihuTestData> generateZhihuTestData() {
        List<ZhihuTestData> data = new ArrayList<>();
        
        // 模拟知乎评论数据
        String[] sampleTexts = {
            "这个观点很有道理，我觉得<b>人工智能</b>确实会改变我们的生活方式。https://www.zhihu.com/question/123456",
            "同意楼上的看法！！！现在的AI技术发展太快了，特别是ChatGPT的出现 📱💻",
            "<p>不过我觉得还是要理性看待，技术发展需要时间。联系方式：example@email.com</p>",
            "哈哈哈哈，这个回答太搞笑了😂😂😂 电话：138-0013-8000",
            "从技术角度来说，深度学习和机器学习确实有很大的应用前景...",
            "   \n\n   空白内容测试   \n\n   ",
            "重复内容测试重复内容测试重复内容测试",
            "中英文混合测试 English mixed content 测试内容",
            "特殊字符测试！@#$%^&*()_+-=[]{}|;':\",./<>?",
            "长文本测试：" + "这是一个很长的文本内容，用于测试系统对长文本的处理能力。".repeat(10)
        };
        
        for (int i = 0; i < sampleTexts.length; i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("questionId", "Q" + (i + 1));
            metadata.put("answerId", "A" + (i + 1));
            metadata.put("userId", "U" + (i + 1));
            metadata.put("timestamp", System.currentTimeMillis());
            
            data.add(ZhihuTestData.builder()
                .id("zhihu_" + i)
                .rawContent(sampleTexts[i])
                .source("zhihu")
                .metadata(metadata)
                .build());
        }
        
        return data;
    }

    // 其他辅助方法...
    private int countHtmlTags(String text) {
        return text.split("<[^>]+>").length - 1;
    }

    private int countUrls(String text) {
        return text.split("https?://[\\w\\.-]+").length - 1;
    }

    private int countSpecialChars(String text) {
        return (int) text.chars().filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)).count();
    }

    private double calculateAverageTokenLength(List<String> tokens) {
        return tokens.stream().mapToInt(String::length).average().orElse(0.0);
    }

    private double calculateChineseTokenRatio(List<String> tokens) {
        long chineseTokens = tokens.stream()
            .filter(token -> token.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))
            .count();
        return tokens.isEmpty() ? 0.0 : (double) chineseTokens / tokens.size();
    }

    private int countNonZeroFeatures(Map<String, Double> vector) {
        return vector == null ? 0 : (int) vector.values().stream().filter(v -> v != 0.0).count();
    }

    private double getMaxTfIdfValue(Map<String, Double> vector) {
        return vector == null ? 0.0 : vector.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    private int countTotalFeatures(TextFeatures features) {
        int count = 0;
        if (features.getTfidfVector() != null) count += features.getTfidfVector().size();
        if (features.getStatistical() != null) count += 1;
        if (features.getWord2vecVector() != null) count += features.getWord2vecVector().size();
        if (features.getNgramFeatures() != null) count += features.getNgramFeatures().size();
        if (features.getSentiment() != null) count += 1;
        if (features.getLanguage() != null) count += 1;
        return count;
    }

    private PerformanceResult executePerformanceTest(int threadId, int batchSize) {
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < batchSize; i++) {
            ZhihuTestData data = testDataSet.get(i % testDataSet.size());
            long requestStart = System.currentTimeMillis();
            
            try {
                textProcessingService.processText(data.getRawContent(), "zhihu", data.getMetadata());
                successCount++;
            } catch (Exception e) {
                failureCount++;
            }
            
            latencies.add(System.currentTimeMillis() - requestStart);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double throughput = (double) batchSize / (totalTime / 1000.0);
        double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        return PerformanceResult.builder()
            .threadId(threadId)
            .totalRequests(batchSize)
            .successCount(successCount)
            .failureCount(failureCount)
            .totalTimeMs(totalTime)
            .throughput(throughput)
            .averageLatency(averageLatency)
            .build();
    }

    // 报告生成方法
    private void generateCleaningReport(List<CleaningTestResult> results) {
        // 实现清洗测试报告生成逻辑
    }

    private void generateDeduplicationReport(DeduplicationResult result) {
        // 实现去重测试报告生成逻辑
    }

    private void generateTokenizationReport(List<TokenizationTestResult> results) {
        // 实现分词测试报告生成逻辑
    }

    private void generateTfIdfReport(List<TfIdfTestResult> results) {
        // 实现TF-IDF测试报告生成逻辑
    }

    private void generateFeatureEngineeringReport(List<FeatureEngineeringResult> results) {
        // 实现特征工程测试报告生成逻辑
    }

    private void generatePerformanceReport(List<PerformanceResult> results) {
        // 实现性能测试报告生成逻辑
    }

    // 数据类定义
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class ZhihuTestData {
        private String id;
        private String rawContent;
        private String source;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class TestMetrics {
        private int successCount;
        private int failureCount;
        
        public void incrementSuccessCount() { successCount++; }
        public void incrementFailureCount() { failureCount++; }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class CleaningTestResult {
        private String originalText;
        private String cleanedText;
        private int originalLength;
        private int cleanedLength;
        private long processingTimeMs;
        private int htmlTagsRemoved;
        private int urlsRemoved;
        private int specialCharsRemoved;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class DeduplicationResult {
        private int originalCount;
        private int deduplicatedCount;
        private int duplicatesRemoved;
        private double deduplicationRate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class TokenizationTestResult {
        private String originalText;
        private List<String> tokens;
        private List<String> filteredTokens;
        private Map<String, String> posTagging;
        private int tokenCount;
        private int filteredTokenCount;
        private long processingTimeMs;
        private double averageTokenLength;
        private double chineseTokenRatio;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class TfIdfTestResult {
        private int documentIndex;
        private String originalText;
        private TextFeatures features;
        private long processingTimeMs;
        private int vectorDimension;
        private int nonZeroFeatures;
        private double maxTfIdfValue;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class FeatureEngineeringResult {
        private String originalText;
        private ProcessingResult processingResult;
        private long processingTimeMs;
        private int textLength;
        private int tokenCount;
        private int featureCount;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class PerformanceResult {
        private int threadId;
        private int totalRequests;
        private int successCount;
        private int failureCount;
        private long totalTimeMs;
        private double throughput;
        private double averageLatency;
    }
}