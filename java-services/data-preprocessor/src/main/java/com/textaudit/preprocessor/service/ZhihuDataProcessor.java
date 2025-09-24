package com.textaudit.preprocessor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textaudit.preprocessor.dto.*;
import com.textaudit.preprocessor.entity.ProcessedText;
import com.textaudit.preprocessor.repository.ProcessedTextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知乎数据处理服务
 * 专门处理来自知乎的问答数据，包括去重、质量评估、特征提取等
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZhihuDataProcessor {

    private final TextProcessingService textProcessingService;
    private final FeatureExtractionService featureExtractionService;
    private final ProcessedTextRepository processedTextRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 去重缓存
    private final Map<String, String> contentHashCache = new ConcurrentHashMap<>();
    
    // 正则表达式模式
    private static final Pattern ZHIHU_URL_PATTERN = Pattern.compile("https?://[\\w\\.-]*zhihu\\.com[^\\s]*");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@[\\u4e00-\\u9fa5\\w]+");
    private static final Pattern TOPIC_PATTERN = Pattern.compile("#[^#\\s]+#");
    
    @Value("${app.zhihu.processing.content-filter.min-length:50}")
    private int minContentLength;
    
    @Value("${app.zhihu.processing.content-filter.max-length:20000}")
    private int maxContentLength;
    
    @Value("${app.zhihu.processing.content-filter.min-vote-count:5}")
    private int minVoteCount;
    
    @Value("${app.zhihu.processing.deduplication.similarity-threshold:0.85}")
    private double similarityThreshold;
    
    @Value("${app.zhihu.processing.quality-assessment.min-quality-score:0.6}")
    private double minQualityScore;
    
    @Value("${kafka.topics.processed-text:processed_text}")
    private String processedTextTopic;
    
    @Value("${kafka.topics.text-features:text_features}")
    private String textFeaturesTopic;

    /**
     * 监听原始知乎数据
     */
    @KafkaListener(topics = "raw_text", groupId = "zhihu-processor-group")
    public void processZhihuData(String rawData) {
        try {
            log.debug("接收到知乎原始数据: {}", rawData.substring(0, Math.min(100, rawData.length())));
            
            // 解析原始数据
            ZhihuRawData zhihuData = objectMapper.readValue(rawData, ZhihuRawData.class);
            
            // 异步处理
            CompletableFuture.runAsync(() -> processZhihuDataAsync(zhihuData))
                .exceptionally(throwable -> {
                    log.error("处理知乎数据异常", throwable);
                    return null;
                });
                
        } catch (Exception e) {
            log.error("解析知乎原始数据失败", e);
        }
    }

    /**
     * 异步处理知乎数据
     */
    private void processZhihuDataAsync(ZhihuRawData rawData) {
        try {
            // 1. 内容过滤
            if (!isContentValid(rawData)) {
                log.debug("内容不符合过滤条件，跳过处理: {}", rawData.getId());
                return;
            }
            
            // 2. 去重检查
            if (isDuplicate(rawData.getContent())) {
                log.debug("发现重复内容，跳过处理: {}", rawData.getId());
                return;
            }
            
            // 3. 质量评估
            double qualityScore = assessContentQuality(rawData);
            if (qualityScore < minQualityScore) {
                log.debug("内容质量评分过低: {} < {}, 跳过处理", qualityScore, minQualityScore);
                return;
            }
            
            // 4. 文本处理
            ProcessingResult processingResult = textProcessingService.processText(
                rawData.getContent(), 
                "zhihu", 
                buildMetadata(rawData, qualityScore)
            );
            
            if (!processingResult.isSuccess()) {
                log.warn("文本处理失败: {}", processingResult.getErrorMessage());
                return;
            }
            
            // 5. 知乎特有特征提取
            ZhihuFeatures zhihuFeatures = extractZhihuFeatures(rawData, processingResult);
            
            // 6. 保存处理结果
            ProcessedText processedText = saveProcessedText(rawData, processingResult, zhihuFeatures, qualityScore);
            
            // 7. 发送到下游服务
            sendToDownstream(processedText, zhihuFeatures);
            
            log.info("知乎数据处理完成: id={}, quality={}, tokens={}", 
                rawData.getId(), qualityScore, processingResult.getTokenizationResult().getTokens().size());
                
        } catch (Exception e) {
            log.error("处理知乎数据异常: id={}", rawData.getId(), e);
        }
    }

    /**
     * 内容有效性检查
     */
    private boolean isContentValid(ZhihuRawData rawData) {
        String content = rawData.getContent();
        
        // 长度检查
        if (content.length() < minContentLength || content.length() > maxContentLength) {
            return false;
        }
        
        // 点赞数检查
        if (rawData.getVoteCount() != null && rawData.getVoteCount() < minVoteCount) {
            return false;
        }
        
        // 排除模式检查
        List<String> excludePatterns = Arrays.asList("广告", "推广", "spam", "删除");
        String lowerContent = content.toLowerCase();
        for (String pattern : excludePatterns) {
            if (lowerContent.contains(pattern)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 去重检查
     */
    private boolean isDuplicate(String content) {
        try {
            // 计算内容哈希
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes("UTF-8"));
            String hashString = Base64.getEncoder().encodeToString(hash);
            
            // 检查缓存
            if (contentHashCache.containsKey(hashString)) {
                return true;
            }
            
            // 检查数据库
            // 这里可以添加数据库查询逻辑
            
            // 添加到缓存
            contentHashCache.put(hashString, content);
            
            // 限制缓存大小
            if (contentHashCache.size() > 10000) {
                contentHashCache.clear();
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("去重检查异常", e);
            return false;
        }
    }

    /**
     * 内容质量评估
     */
    private double assessContentQuality(ZhihuRawData rawData) {
        double score = 0.0;
        
        // 长度因子 (0-0.3)
        int length = rawData.getContent().length();
        if (length >= 100 && length <= 2000) {
            score += 0.3;
        } else if (length > 50) {
            score += 0.15;
        }
        
        // 点赞数因子 (0-0.3)
        if (rawData.getVoteCount() != null) {
            int votes = rawData.getVoteCount();
            if (votes >= 100) {
                score += 0.3;
            } else if (votes >= 10) {
                score += 0.2;
            } else if (votes >= 1) {
                score += 0.1;
            }
        }
        
        // 评论数因子 (0-0.2)
        if (rawData.getCommentCount() != null) {
            int comments = rawData.getCommentCount();
            if (comments >= 50) {
                score += 0.2;
            } else if (comments >= 5) {
                score += 0.1;
            }
        }
        
        // 可读性因子 (0-0.2)
        score += assessReadability(rawData.getContent());
        
        return Math.min(1.0, score);
    }

    /**
     * 可读性评估
     */
    private double assessReadability(String content) {
        // 简单的可读性评估
        double score = 0.0;
        
        // 句子数量
        int sentences = content.split("[。！？]").length;
        if (sentences >= 3 && sentences <= 20) {
            score += 0.1;
        }
        
        // 标点符号使用
        long punctuationCount = content.chars()
            .filter(ch -> "，。！？；：\"\"''（）【】".indexOf(ch) >= 0)
            .count();
        if (punctuationCount > 0) {
            score += 0.1;
        }
        
        return score;
    }

    /**
     * 提取知乎特有特征
     */
    private ZhihuFeatures extractZhihuFeatures(ZhihuRawData rawData, ProcessingResult processingResult) {
        ZhihuFeatures.ZhihuFeaturesBuilder builder = ZhihuFeatures.builder();
        
        String content = rawData.getContent();
        
        // 提取@用户
        List<String> mentions = MENTION_PATTERN.matcher(content)
            .results()
            .map(match -> match.group())
            .collect(Collectors.toList());
        builder.mentions(mentions);
        
        // 提取话题标签
        List<String> topics = TOPIC_PATTERN.matcher(content)
            .results()
            .map(match -> match.group())
            .collect(Collectors.toList());
        builder.topics(topics);
        
        // 提取知乎链接
        List<String> zhihuUrls = ZHIHU_URL_PATTERN.matcher(content)
            .results()
            .map(match -> match.group())
            .collect(Collectors.toList());
        builder.zhihuUrls(zhihuUrls);
        
        // 统计特征
        builder.voteCount(rawData.getVoteCount() != null ? rawData.getVoteCount() : 0)
               .commentCount(rawData.getCommentCount() != null ? rawData.getCommentCount() : 0)
               .followCount(rawData.getFollowCount() != null ? rawData.getFollowCount() : 0)
               .authorLevel(rawData.getAuthorLevel())
               .isAnswer(rawData.getType() != null && "answer".equals(rawData.getType()))
               .isQuestion(rawData.getType() != null && "question".equals(rawData.getType()));
        
        return builder.build();
    }

    /**
     * 构建元数据
     */
    private Map<String, Object> buildMetadata(ZhihuRawData rawData, double qualityScore) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "zhihu");
        metadata.put("type", rawData.getType());
        metadata.put("author", rawData.getAuthor());
        metadata.put("url", rawData.getUrl());
        metadata.put("createdAt", rawData.getCreatedAt());
        metadata.put("qualityScore", qualityScore);
        metadata.put("voteCount", rawData.getVoteCount());
        metadata.put("commentCount", rawData.getCommentCount());
        return metadata;
    }

    /**
     * 保存处理结果
     */
    @Transactional
    private ProcessedText saveProcessedText(ZhihuRawData rawData, ProcessingResult processingResult, 
                                         ZhihuFeatures zhihuFeatures, double qualityScore) {
        try {
            ProcessedText processedText = new ProcessedText();
            processedText.setRawTextId(rawData.getId());
            processedText.setSource("zhihu");
            processedText.setContent(processingResult.getCleanedText());
            
            // 设置tokens
            if (processingResult.getTokenizationResult() != null) {
                processedText.setTokens(objectMapper.writeValueAsString(
                    processingResult.getTokenizationResult().getTokens()));
            }
            
            // 设置特征向量
            if (processingResult.getFeatures() != null) {
                processedText.setFeatures(objectMapper.writeValueAsString(
                    processingResult.getFeatures()));
            }
            
            // 设置标签 - 使用label字段而不是labels
            if (qualityScore >= 0.8) {
                processedText.setLabel(1); // 高质量内容
            } else {
                processedText.setLabel(0); // 普通内容
            }
            
            // 设置元数据
            Map<String, Object> metadata = buildMetadata(rawData, qualityScore);
            metadata.put("zhihuFeatures", zhihuFeatures);
            processedText.setProcessingMetadata(objectMapper.writeValueAsString(metadata));
            
            processedText.setTimestamp(System.currentTimeMillis());
            
            return processedTextRepository.save(processedText);
            
        } catch (Exception e) {
            log.error("保存处理结果失败", e);
            throw new RuntimeException("保存处理结果失败", e);
        }
    }

    /**
     * 发送到下游服务
     */
    private void sendToDownstream(ProcessedText processedText, ZhihuFeatures zhihuFeatures) {
        try {
            // 发送处理后的文本
            kafkaTemplate.send(processedTextTopic, processedText.getId().toString(), processedText);
            
            // 发送特征数据
            Map<String, Object> featureData = new HashMap<>();
            featureData.put("textId", processedText.getId());
            featureData.put("zhihuFeatures", zhihuFeatures);
            featureData.put("textFeatures", processedText.getFeatures());
            
            kafkaTemplate.send(textFeaturesTopic, processedText.getId().toString(), featureData);
            
            log.debug("数据已发送到下游服务: textId={}", processedText.getId());
            
        } catch (Exception e) {
            log.error("发送数据到下游服务失败", e);
        }
    }

    /**
     * 获取处理统计信息
     */
    public Map<String, Object> getProcessingStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 缓存统计
        stats.put("cacheSize", contentHashCache.size());
        
        // 数据库统计
        long totalProcessed = processedTextRepository.countBySource("zhihu");
        stats.put("totalProcessed", totalProcessed);
        
        // 今日处理量
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime tomorrow = today.plusDays(1);
        long todayProcessed = processedTextRepository.countByCreatedAtBetween(today, tomorrow);
        stats.put("todayProcessed", todayProcessed);
        
        return stats;
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        contentHashCache.clear();
        log.info("知乎数据处理缓存已清理");
    }
}