package com.textaudit.preprocessor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textaudit.preprocessor.dto.ProcessingResult;
import com.textaudit.preprocessor.dto.TextFeatures;
import com.textaudit.preprocessor.entity.ProcessedText;
import com.textaudit.preprocessor.repository.ProcessedTextRepository;
import com.textaudit.preprocessor.service.FeatureExtractionService;
import com.textaudit.preprocessor.service.TextProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 原始文本Kafka消费者
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawTextConsumer {

    private final TextProcessingService textProcessingService;
    private final FeatureExtractionService featureExtractionService;
    private final ProcessedTextRepository processedTextRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.processed-text}")
    private String processedTextTopic;

    @Value("${kafka.topics.processing-error}")
    private String processingErrorTopic;

    @Value("${processing.enable-feature-extraction:true}")
    private boolean enableFeatureExtraction;

    @Value("${processing.enable-auto-save:true}")
    private boolean enableAutoSave;

    /**
     * 消费原始文本消息
     */
    @KafkaListener(
        topics = "${kafka.topics.raw-text}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void consumeRawText(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String key,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        log.info("接收到原始文本消息，topic: {}, partition: {}, offset: {}, key: {}", 
                topic, partition, offset, key);

        try {
            // 解析消息
            RawTextMessage rawTextMessage = parseMessage(message);
            
            // 检查是否已经处理过
            if (processedTextRepository.existsByRawTextId(rawTextMessage.getId())) {
                log.info("文本ID {} 已经处理过，跳过处理", rawTextMessage.getId());
                acknowledgment.acknowledge();
                return;
            }

            // 处理文本
            ProcessingResult result = processText(rawTextMessage);
            
            if (result.isSuccess()) {
                // 保存处理结果
                ProcessedText processedText = saveProcessedText(rawTextMessage, result);
                
                // 发送处理完成消息
                sendProcessedTextMessage(processedText);
                
                log.info("文本处理完成，ID: {}, 处理耗时: {}ms", 
                        rawTextMessage.getId(), result.getProcessingTimeMs());
            } else {
                // 处理失败，发送错误消息
                sendErrorMessage(rawTextMessage, result.getErrorMessage());
                log.error("文本处理失败，ID: {}, 错误: {}", 
                         rawTextMessage.getId(), result.getErrorMessage());
            }

            // 手动确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("处理原始文本消息时发生异常: {}", e.getMessage(), e);
            
            // 发送错误消息
            try {
                RawTextMessage rawTextMessage = parseMessage(message);
                sendErrorMessage(rawTextMessage, e.getMessage());
            } catch (Exception parseException) {
                log.error("解析错误消息失败: {}", parseException.getMessage());
            }
            
            // 不确认消息，让Kafka重试
            throw e;
        }
    }

    /**
     * 批量消费原始文本消息
     */
    @KafkaListener(
        topics = "${kafka.topics.raw-text-batch}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeRawTextBatch(
            @Payload List<String> messages,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        log.info("接收到批量原始文本消息，数量: {}, topic: {}", messages.size(), topic);

        try {
            // 解析所有消息
            List<RawTextMessage> rawTextMessages = messages.stream()
                .map(this::parseMessage)
                .toList();

            // 批量处理
            batchProcessTexts(rawTextMessages);

            // 确认所有消息
            acknowledgment.acknowledge();
            
            log.info("批量处理完成，处理数量: {}", rawTextMessages.size());

        } catch (Exception e) {
            log.error("批量处理原始文本消息时发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 解析消息
     */
    private RawTextMessage parseMessage(String message) {
        try {
            return objectMapper.readValue(message, RawTextMessage.class);
        } catch (Exception e) {
            log.error("解析原始文本消息失败: {}", e.getMessage());
            throw new RuntimeException("消息解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理单个文本
     */
    private ProcessingResult processText(RawTextMessage rawTextMessage) {
        try {
            // 文本预处理
            ProcessingResult result = textProcessingService.processText(
                rawTextMessage.getContent(), 
                rawTextMessage.getDataSource(),
                rawTextMessage.getMetadata()
            );

            if (!result.isSuccess()) {
                return result;
            }

            // 特征提取
            if (enableFeatureExtraction && result.getTokenizationResult() != null) {
                try {
                    TextFeatures features = featureExtractionService.extractFeatures(
                        rawTextMessage.getContent(), 
                        result.getTokenizationResult()
                    );
                    result.setFeatures(features);
                } catch (Exception e) {
                    log.warn("特征提取失败，继续处理: {}", e.getMessage());
                }
            }

            return result;

        } catch (Exception e) {
            log.error("处理文本时发生异常: {}", e.getMessage(), e);
            return ProcessingResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .originalText(rawTextMessage.getContent())
                .source(rawTextMessage.getDataSource())
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }

    /**
     * 批量处理文本
     */
    private void batchProcessTexts(List<RawTextMessage> rawTextMessages) {
        // 提取文本内容
        List<String> texts = rawTextMessages.stream()
            .map(RawTextMessage::getContent)
            .toList();

        // 批量处理
        List<ProcessingResult> results = textProcessingService.batchProcessTexts(
            texts, 
            rawTextMessages.get(0).getDataSource()
        );

        // 保存结果
        for (int i = 0; i < rawTextMessages.size(); i++) {
            RawTextMessage rawTextMessage = rawTextMessages.get(i);
            ProcessingResult result = results.get(i);

            if (result.isSuccess()) {
                try {
                    ProcessedText processedText = saveProcessedText(rawTextMessage, result);
                    sendProcessedTextMessage(processedText);
                } catch (Exception e) {
                    log.error("保存处理结果失败，ID: {}, 错误: {}", 
                             rawTextMessage.getId(), e.getMessage());
                    sendErrorMessage(rawTextMessage, e.getMessage());
                }
            } else {
                sendErrorMessage(rawTextMessage, result.getErrorMessage());
            }
        }
    }

    /**
     * 保存处理结果
     */
    private ProcessedText saveProcessedText(RawTextMessage rawTextMessage, ProcessingResult result) {
        if (!enableAutoSave) {
            return null;
        }

        try {
            ProcessedText processedText = ProcessedText.builder()
                .rawTextId(rawTextMessage.getId().toString())
                .content(result.getCleanedText())
                .tokens(objectMapper.writeValueAsString(result.getTokenizationResult()))
                .features(result.getFeatures() != null ? 
                    objectMapper.writeValueAsString(result.getFeatures()) : null)
                .label(rawTextMessage.getLabels() != null && !rawTextMessage.getLabels().isEmpty() ? 1 : 0)
                .source(result.getSource())
                .processingMetadata(createProcessingMetadata(result))
                .timestamp(System.currentTimeMillis())
                .build();

            return processedTextRepository.save(processedText);

        } catch (Exception e) {
            log.error("保存处理结果失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存处理结果失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建处理元数据
     */
    private String createProcessingMetadata(ProcessingResult result) {
        try {
            Map<String, Object> metadata = Map.of(
                "processingTime", result.getProcessingTimeMs(),
                "originalLength", result.getOriginalText() != null ? result.getOriginalText().length() : 0,
                "cleanedLength", result.getCleanedText() != null ? result.getCleanedText().length() : 0,
                "tokenCount", result.getTokenizationResult() != null ? 
                    result.getTokenizationResult().getTotalTokenCount() : 0,
                "featureDimension", result.getFeatures() != null ? 
                    result.getFeatures().getFeatureDimension() : 0,
                "reductionRatio", calculateReductionRatio(result),
                "processedAt", LocalDateTime.now().toString()
            );
            
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("创建处理元数据失败: {}", e.getMessage());
            return "{}";
        }
    }
    
    private double calculateReductionRatio(ProcessingResult result) {
        if (result.getOriginalText() == null || result.getCleanedText() == null) {
            return 0.0;
        }
        int originalLength = result.getOriginalText().length();
        int cleanedLength = result.getCleanedText().length();
        return originalLength > 0 ? (double) (originalLength - cleanedLength) / originalLength : 0.0;
    }

    /**
     * 发送处理完成消息
     */
    private void sendProcessedTextMessage(ProcessedText processedText) {
        try {
            ProcessedTextMessage message = ProcessedTextMessage.builder()
                .id(processedText.getId())
                .rawTextId(processedText.getRawTextId())
                .dataSource(processedText.getSource())
                .tokenCount(extractTokenCount(processedText.getTokens()))
                .featureDimension(extractFeatureDimension(processedText.getFeatures()))
                .processedAt(LocalDateTime.ofEpochSecond(processedText.getTimestamp() / 1000, 0, java.time.ZoneOffset.UTC))
                .build();

            kafkaTemplate.send(processedTextTopic, processedText.getRawTextId(), message);
            log.debug("发送处理完成消息，ID: {}", processedText.getId());

        } catch (Exception e) {
            log.error("发送处理完成消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(RawTextMessage rawTextMessage, String errorMessage) {
        try {
            ProcessingErrorMessage message = ProcessingErrorMessage.builder()
                .rawTextId(rawTextMessage.getId())
                .dataSource(rawTextMessage.getDataSource())
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send(processingErrorTopic, rawTextMessage.getId().toString(), message);
            log.debug("发送错误消息，ID: {}", rawTextMessage.getId());

        } catch (Exception e) {
            log.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 提取词汇数量
     */
    private int extractTokenCount(String tokensJson) {
        try {
            if (tokensJson == null) return 0;
            Map<String, Object> tokens = objectMapper.readValue(tokensJson, Map.class);
            List<?> tokenList = (List<?>) tokens.get("tokens");
            return tokenList != null ? tokenList.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 提取特征维度
     */
    private int extractFeatureDimension(String featuresJson) {
        try {
            if (featuresJson == null) return 0;
            Map<String, Object> features = objectMapper.readValue(featuresJson, Map.class);
            // 简化的特征维度计算
            return features.size();
        } catch (Exception e) {
            return 0;
        }
    }

    // 消息类定义

    /**
     * 原始文本消息
     */
    public static class RawTextMessage {
        private Long id;
        private String content;
        private String dataSource;
        private List<String> labels;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;

        // getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /**
     * 处理完成消息
     */
    public static class ProcessedTextMessage {
        private String id;
        private String rawTextId;
        private String dataSource;
        private int tokenCount;
        private int featureDimension;
        private LocalDateTime processedAt;

        public static ProcessedTextMessageBuilder builder() {
            return new ProcessedTextMessageBuilder();
        }

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRawTextId() { return rawTextId; }
        public void setRawTextId(String rawTextId) { this.rawTextId = rawTextId; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
        public int getTokenCount() { return tokenCount; }
        public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
        public int getFeatureDimension() { return featureDimension; }
        public void setFeatureDimension(int featureDimension) { this.featureDimension = featureDimension; }
        public LocalDateTime getProcessedAt() { return processedAt; }
        public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

        public static class ProcessedTextMessageBuilder {
            private String id;
            private String rawTextId;
            private String dataSource;
            private int tokenCount;
            private int featureDimension;
            private LocalDateTime processedAt;

            public ProcessedTextMessageBuilder id(String id) { this.id = id; return this; }
            public ProcessedTextMessageBuilder rawTextId(String rawTextId) { this.rawTextId = rawTextId; return this; }
            public ProcessedTextMessageBuilder dataSource(String dataSource) { this.dataSource = dataSource; return this; }
            public ProcessedTextMessageBuilder tokenCount(int tokenCount) { this.tokenCount = tokenCount; return this; }
            public ProcessedTextMessageBuilder featureDimension(int featureDimension) { this.featureDimension = featureDimension; return this; }
            public ProcessedTextMessageBuilder processedAt(LocalDateTime processedAt) { this.processedAt = processedAt; return this; }

            public ProcessedTextMessage build() {
                ProcessedTextMessage message = new ProcessedTextMessage();
                message.setId(id);
                message.setRawTextId(rawTextId);
                message.setDataSource(dataSource);
                message.setTokenCount(tokenCount);
                message.setFeatureDimension(featureDimension);
                message.setProcessedAt(processedAt);
                return message;
            }
        }
    }

    /**
     * 处理错误消息
     */
    public static class ProcessingErrorMessage {
        private Long rawTextId;
        private String dataSource;
        private String errorMessage;
        private LocalDateTime timestamp;

        public static ProcessingErrorMessageBuilder builder() {
            return new ProcessingErrorMessageBuilder();
        }

        // getters and setters
        public Long getRawTextId() { return rawTextId; }
        public void setRawTextId(Long rawTextId) { this.rawTextId = rawTextId; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public static class ProcessingErrorMessageBuilder {
            private Long rawTextId;
            private String dataSource;
            private String errorMessage;
            private LocalDateTime timestamp;

            public ProcessingErrorMessageBuilder rawTextId(Long rawTextId) { this.rawTextId = rawTextId; return this; }
            public ProcessingErrorMessageBuilder dataSource(String dataSource) { this.dataSource = dataSource; return this; }
            public ProcessingErrorMessageBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public ProcessingErrorMessageBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

            public ProcessingErrorMessage build() {
                ProcessingErrorMessage message = new ProcessingErrorMessage();
                message.setRawTextId(rawTextId);
                message.setDataSource(dataSource);
                message.setErrorMessage(errorMessage);
                message.setTimestamp(timestamp);
                return message;
            }
        }
    }
}