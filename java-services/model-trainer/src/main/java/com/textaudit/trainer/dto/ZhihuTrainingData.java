package com.textaudit.trainer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知乎训练数据DTO
 * 用于ChatGLM-6B微调训练
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhihuTrainingData {
    
    /**
     * 问题ID
     */
    private String questionId;
    
    /**
     * 问题标题
     */
    private String question;
    
    /**
     * 回答内容
     */
    private String answer;
    
    /**
     * 回答ID
     */
    private String answerId;
    
    /**
     * 作者信息
     */
    private String author;
    
    /**
     * 点赞数
     */
    private Integer upvotes;
    
    /**
     * 评论数
     */
    private Integer comments;
    
    /**
     * 收藏数
     */
    private Integer collections;
    
    /**
     * 问题话题标签
     */
    private List<String> topics;
    
    /**
     * 回答质量评分 (0-1)
     */
    private Double qualityScore;
    
    /**
     * 内容长度
     */
    private Integer contentLength;
    
    /**
     * 是否匿名回答
     */
    private Boolean anonymous;
    
    /**
     * 是否置顶回答
     */
    private Boolean pinned;
    
    /**
     * 是否精华回答
     */
    private Boolean featured;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 数据采集时间
     */
    private LocalDateTime collectedAt;
    
    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 训练数据类型
     */
    private TrainingDataType dataType;
    
    /**
     * 数据来源
     */
    private String source;
    
    /**
     * 数据版本
     */
    private String version;
    
    /**
     * 训练数据类型枚举
     */
    public enum TrainingDataType {
        QUESTION_ANSWER("问答对"),
        INSTRUCTION_FOLLOWING("指令跟随"),
        CONVERSATION("对话"),
        KNOWLEDGE_QA("知识问答"),
        REASONING("推理"),
        CREATIVE_WRITING("创意写作");
        
        private final String description;
        
        TrainingDataType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 获取显示标题
     */
    public String getDisplayTitle() {
        if (question != null && question.length() > 50) {
            return question.substring(0, 50) + "...";
        }
        return question;
    }
    
    /**
     * 获取回答摘要
     */
    public String getAnswerSummary() {
        if (answer != null && answer.length() > 100) {
            return answer.substring(0, 100) + "...";
        }
        return answer;
    }
    
    /**
     * 获取总互动数
     */
    public Integer getTotalInteractions() {
        int total = 0;
        if (upvotes != null) total += upvotes;
        if (comments != null) total += comments;
        if (collections != null) total += collections;
        return total;
    }
    
    /**
     * 判断是否为高质量内容
     */
    public boolean isHighQuality() {
        // 质量评分大于0.8
        if (qualityScore != null && qualityScore > 0.8) {
            return true;
        }
        
        // 点赞数大于50
        if (upvotes != null && upvotes > 50) {
            return true;
        }
        
        // 总互动数大于100
        if (getTotalInteractions() > 100) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取内容复杂度评分
     */
    public double getComplexityScore() {
        double score = 0.0;
        
        // 基于内容长度
        if (contentLength != null) {
            if (contentLength > 1000) {
                score += 0.3;
            } else if (contentLength > 500) {
                score += 0.2;
            } else if (contentLength > 200) {
                score += 0.1;
            }
        }
        
        // 基于话题数量
        if (topics != null && !topics.isEmpty()) {
            score += Math.min(topics.size() * 0.1, 0.3);
        }
        
        // 基于互动数
        int interactions = getTotalInteractions();
        if (interactions > 100) {
            score += 0.2;
        } else if (interactions > 50) {
            score += 0.1;
        }
        
        // 基于质量评分
        if (qualityScore != null) {
            score += qualityScore * 0.2;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 验证数据完整性
     */
    public boolean isValid() {
        // 必须有问题和回答
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        
        if (answer == null || answer.trim().isEmpty()) {
            return false;
        }
        
        // 内容长度检查
        if (question.length() < 5 || question.length() > 1000) {
            return false;
        }
        
        if (answer.length() < 20 || answer.length() > 5000) {
            return false;
        }
        
        // 质量评分检查
        if (qualityScore != null && (qualityScore < 0 || qualityScore > 1)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取训练格式的指令
     */
    public String getTrainingInstruction() {
        StringBuilder instruction = new StringBuilder();
        
        if (dataType != null) {
            switch (dataType) {
                case QUESTION_ANSWER:
                    instruction.append("请根据以下问题提供准确、详细的回答：");
                    break;
                case INSTRUCTION_FOLLOWING:
                    instruction.append("请按照以下指令执行任务：");
                    break;
                case CONVERSATION:
                    instruction.append("请参与以下对话并提供合适的回复：");
                    break;
                case KNOWLEDGE_QA:
                    instruction.append("请基于你的知识回答以下问题：");
                    break;
                case REASONING:
                    instruction.append("请分析以下问题并提供逻辑推理：");
                    break;
                case CREATIVE_WRITING:
                    instruction.append("请根据以下要求进行创意写作：");
                    break;
                default:
                    instruction.append("请回答以下问题：");
            }
        } else {
            instruction.append("请回答以下问题：");
        }
        
        return instruction.toString();
    }
    
    /**
     * 获取训练格式的输入
     */
    public String getTrainingInput() {
        StringBuilder input = new StringBuilder();
        
        // 添加问题
        input.append("问题：").append(question);
        
        // 添加话题信息
        if (topics != null && !topics.isEmpty()) {
            input.append("\n话题：").append(String.join(", ", topics));
        }
        
        // 添加上下文信息
        if (metadata != null && !metadata.isEmpty()) {
            Object context = metadata.get("context");
            if (context != null) {
                input.append("\n背景：").append(context.toString());
            }
        }
        
        return input.toString();
    }
    
    /**
     * 获取训练格式的输出
     */
    public String getTrainingOutput() {
        return "回答：" + answer;
    }
    
    /**
     * 转换为ChatGLM训练格式
     */
    public Map<String, Object> toChatGLMFormat() {
        Map<String, Object> format = new java.util.HashMap<>();
        format.put("instruction", getTrainingInstruction());
        format.put("input", getTrainingInput());
        format.put("output", getTrainingOutput());
        
        // 添加元数据
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("question_id", questionId);
        meta.put("answer_id", answerId);
        meta.put("quality_score", qualityScore);
        meta.put("upvotes", upvotes);
        meta.put("data_type", dataType != null ? dataType.name() : "QUESTION_ANSWER");
        meta.put("complexity_score", getComplexityScore());
        meta.put("high_quality", isHighQuality());
        format.put("metadata", meta);
        
        return format;
    }
    
    /**
     * 创建用于指令跟随的训练数据
     */
    public static ZhihuTrainingData createInstructionData(String instruction, String response) {
        return ZhihuTrainingData.builder()
                .question(instruction)
                .answer(response)
                .dataType(TrainingDataType.INSTRUCTION_FOLLOWING)
                .qualityScore(0.8)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建用于对话的训练数据
     */
    public static ZhihuTrainingData createConversationData(String context, String response) {
        return ZhihuTrainingData.builder()
                .question(context)
                .answer(response)
                .dataType(TrainingDataType.CONVERSATION)
                .qualityScore(0.7)
                .createdAt(LocalDateTime.now())
                .build();
    }
}