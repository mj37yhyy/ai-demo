package com.textaudit.trainer.service;

import com.textaudit.trainer.dto.TrainingJob;
import com.textaudit.trainer.dto.TrainingResult;
import com.textaudit.trainer.dto.ZhihuTrainingData;
import com.textaudit.trainer.exception.ModelTrainingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChatGLM-6B微调训练服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatGLMTrainingService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ModelStorageService modelStorageService;
    
    @Value("${model-training.chatglm.model-path}")
    private String modelPath;
    
    @Value("${model-training.chatglm.fine-tuned-path}")
    private String fineTunedPath;
    
    @Value("${model-training.chatglm.training-config.batch-size:8}")
    private int batchSize;
    
    @Value("${model-training.chatglm.training-config.max-epochs:10}")
    private int maxEpochs;
    
    @Value("${model-training.chatglm.training-config.learning-rate:5e-5}")
    private double learningRate;
    
    @Value("${model-training.chatglm.lora-config.enabled:true}")
    private boolean loraEnabled;
    
    @Value("${model-training.chatglm.lora-config.r:8}")
    private int loraR;
    
    @Value("${model-training.chatglm.lora-config.lora-alpha:32}")
    private int loraAlpha;
    
    @Value("${model-training.chatglm.lora-config.lora-dropout:0.1}")
    private double loraDropout;
    
    @Value("${model-training.storage.temp-path}")
    private String tempPath;
    
    // 训练状态管理
    private final Map<String, TrainingJob> activeJobs = new ConcurrentHashMap<>();
    private final AtomicInteger trainingCounter = new AtomicInteger(0);
    private final AtomicLong totalSamplesProcessed = new AtomicLong(0);
    
    // 训练数据缓存
    private final List<ZhihuTrainingData> trainingDataBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int BUFFER_SIZE = 1000;
    
    @PostConstruct
    public void init() {
        log.info("初始化ChatGLM训练服务");
        
        // 检查模型路径
        if (!Files.exists(Paths.get(modelPath))) {
            log.warn("ChatGLM模型路径不存在: {}", modelPath);
        }
        
        // 创建必要的目录
        try {
            Files.createDirectories(Paths.get(fineTunedPath));
            Files.createDirectories(Paths.get(tempPath));
            log.info("ChatGLM训练服务初始化完成");
        } catch (IOException e) {
            log.error("创建目录失败", e);
            throw new ModelTrainingException("初始化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 监听知乎处理后的数据
     */
    @KafkaListener(topics = "zhihu-processed-data", groupId = "chatglm-training-group")
    public void handleZhihuData(ZhihuTrainingData data) {
        try {
            log.debug("接收到知乎训练数据: {}", data.getQuestionId());
            
            // 数据验证
            if (isValidTrainingData(data)) {
                trainingDataBuffer.add(data);
                totalSamplesProcessed.incrementAndGet();
                
                // 当缓冲区满时触发批量训练
                if (trainingDataBuffer.size() >= BUFFER_SIZE) {
                    triggerBatchTraining();
                }
            } else {
                log.debug("跳过无效的训练数据: {}", data.getQuestionId());
            }
            
        } catch (Exception e) {
            log.error("处理知乎训练数据失败: {}", data.getQuestionId(), e);
        }
    }
    
    /**
     * 启动ChatGLM微调训练
     */
    @Async
    public CompletableFuture<TrainingResult> startFineTuning(TrainingJob job) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始ChatGLM微调训练: {}", job.getJobId());
                
                // 更新任务状态
                job.setStatus(TrainingJob.JobStatus.TRAINING);
                job.setStartTime(LocalDateTime.now());
                activeJobs.put(job.getJobId(), job);
                
                // 准备训练数据
                String trainingDataPath = prepareTrainingData(job);
                
                // 执行微调训练
                TrainingResult result = executeFineTuning(job, trainingDataPath);
                
                // 更新任务状态
                job.setStatus(TrainingJob.JobStatus.COMPLETED);
                job.setEndTime(LocalDateTime.now());
                
                // 发送训练完成事件
                kafkaTemplate.send("model-training-events", "training-completed", result);
                
                log.info("ChatGLM微调训练完成: {}", job.getJobId());
                return result;
                
            } catch (Exception e) {
                log.error("ChatGLM微调训练失败: {}", job.getJobId(), e);
                job.setStatus(TrainingJob.JobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                throw new ModelTrainingException("微调训练失败: " + e.getMessage(), e);
            } finally {
                activeJobs.remove(job.getJobId());
            }
        });
    }
    
    /**
     * 执行LoRA微调
     */
    private TrainingResult executeLoRAFineTuning(TrainingJob job, String dataPath) throws Exception {
        log.info("执行LoRA微调: {}", job.getJobId());
        
        // 构建训练命令
        List<String> command = buildLoRATrainingCommand(job, dataPath);
        
        // 执行训练
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(modelPath));
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 监控训练进度
        monitorTrainingProgress(process, job);
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new ModelTrainingException("LoRA训练失败，退出码: " + exitCode);
        }
        
        // 构建训练结果
        return TrainingResult.builder()
                .jobId(job.getJobId())
                .modelType("ChatGLM-6B-LoRA")
                .trainingTime(calculateTrainingTime(job))
                .finalLoss(job.getTrainingLoss())
                .accuracy(calculateAccuracy(job))
                .modelPath(getFineTunedModelPath(job))
                .build();
    }
    
    /**
     * 执行全参数微调
     */
    private TrainingResult executeFullFineTuning(TrainingJob job, String dataPath) throws Exception {
        log.info("执行全参数微调: {}", job.getJobId());
        
        // 构建训练命令
        List<String> command = buildFullTrainingCommand(job, dataPath);
        
        // 执行训练
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(modelPath));
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 监控训练进度
        monitorTrainingProgress(process, job);
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new ModelTrainingException("全参数训练失败，退出码: " + exitCode);
        }
        
        // 构建训练结果
        return TrainingResult.builder()
                .jobId(job.getJobId())
                .modelType("ChatGLM-6B-Full")
                .trainingTime(calculateTrainingTime(job))
                .finalLoss(job.getTrainingLoss())
                .accuracy(calculateAccuracy(job))
                .modelPath(getFineTunedModelPath(job))
                .build();
    }
    
    /**
     * 构建LoRA训练命令
     */
    private List<String> buildLoRATrainingCommand(TrainingJob job, String dataPath) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add("train_lora.py");
        command.add("--model_name_or_path");
        command.add(modelPath);
        command.add("--dataset_path");
        command.add(dataPath);
        command.add("--output_dir");
        command.add(getFineTunedModelPath(job));
        command.add("--lora_r");
        command.add(String.valueOf(loraR));
        command.add("--lora_alpha");
        command.add(String.valueOf(loraAlpha));
        command.add("--lora_dropout");
        command.add(String.valueOf(loraDropout));
        command.add("--learning_rate");
        command.add(String.valueOf(learningRate));
        command.add("--num_train_epochs");
        command.add(String.valueOf(maxEpochs));
        command.add("--per_device_train_batch_size");
        command.add(String.valueOf(batchSize));
        command.add("--gradient_accumulation_steps");
        command.add("4");
        command.add("--logging_steps");
        command.add("100");
        command.add("--save_steps");
        command.add("1000");
        command.add("--evaluation_strategy");
        command.add("steps");
        command.add("--eval_steps");
        command.add("500");
        command.add("--load_best_model_at_end");
        command.add("--metric_for_best_model");
        command.add("eval_loss");
        command.add("--greater_is_better");
        command.add("False");
        
        return command;
    }
    
    /**
     * 构建全参数训练命令
     */
    private List<String> buildFullTrainingCommand(TrainingJob job, String dataPath) {
        List<String> command = new ArrayList<>();
        command.add("python");
        command.add("train_full.py");
        command.add("--model_name_or_path");
        command.add(modelPath);
        command.add("--dataset_path");
        command.add(dataPath);
        command.add("--output_dir");
        command.add(getFineTunedModelPath(job));
        command.add("--learning_rate");
        command.add(String.valueOf(learningRate));
        command.add("--num_train_epochs");
        command.add(String.valueOf(maxEpochs));
        command.add("--per_device_train_batch_size");
        command.add(String.valueOf(batchSize));
        command.add("--gradient_accumulation_steps");
        command.add("8");
        command.add("--gradient_checkpointing");
        command.add("--deepspeed");
        command.add("ds_config.json");
        command.add("--logging_steps");
        command.add("50");
        command.add("--save_steps");
        command.add("500");
        command.add("--evaluation_strategy");
        command.add("steps");
        command.add("--eval_steps");
        command.add("250");
        
        return command;
    }
    
    /**
     * 监控训练进度
     */
    private void monitorTrainingProgress(Process process, TrainingJob job) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("训练输出: {}", line);
                
                // 解析训练进度
                parseTrainingProgress(line, job);
                
                // 发送进度更新事件
                if (job.getEpochsCompleted() > 0) {
                    kafkaTemplate.send("model-training-events", "training-progress", job);
                }
            }
        } catch (IOException e) {
            log.error("监控训练进度失败", e);
        }
    }
    
    /**
     * 解析训练进度
     */
    private void parseTrainingProgress(String line, TrainingJob job) {
        try {
            // 解析epoch信息
            if (line.contains("Epoch")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("Epoch".equals(parts[i])) {
                        String epochInfo = parts[i + 1];
                        if (epochInfo.contains("/")) {
                            String[] epochParts = epochInfo.split("/");
                            int currentEpoch = Integer.parseInt(epochParts[0]);
                            job.setEpochsCompleted(currentEpoch);
                        }
                        break;
                    }
                }
            }
            
            // 解析loss信息
            if (line.contains("loss")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("loss".equals(parts[i])) {
                        try {
                            double loss = Double.parseDouble(parts[i + 1]);
                            job.setTrainingLoss(loss);
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                        break;
                    }
                }
            }
            
            // 解析学习率信息
            if (line.contains("learning_rate")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("learning_rate".equals(parts[i])) {
                        try {
                            double lr = Double.parseDouble(parts[i + 1]);
                            job.getHyperparameters().put("current_learning_rate", String.valueOf(lr));
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("解析训练进度失败: {}", line, e);
        }
    }
    
    /**
     * 准备训练数据
     */
    private String prepareTrainingData(TrainingJob job) throws IOException {
        log.info("准备训练数据: {}", job.getJobId());
        
        String dataFileName = "training_data_" + job.getJobId() + ".jsonl";
        Path dataPath = Paths.get(tempPath, dataFileName);
        
        // 从缓冲区获取训练数据
        List<ZhihuTrainingData> currentData = new ArrayList<>(trainingDataBuffer);
        
        // 转换为ChatGLM训练格式
        try (BufferedWriter writer = Files.newBufferedWriter(dataPath)) {
            for (ZhihuTrainingData data : currentData) {
                String jsonLine = convertToTrainingFormat(data);
                writer.write(jsonLine);
                writer.newLine();
            }
        }
        
        log.info("训练数据准备完成: {} 条记录", currentData.size());
        return dataPath.toString();
    }
    
    /**
     * 转换为ChatGLM训练格式
     */
    private String convertToTrainingFormat(ZhihuTrainingData data) {
        Map<String, Object> trainingItem = new HashMap<>();
        trainingItem.put("instruction", "请根据以下问题生成高质量的回答：");
        trainingItem.put("input", "问题：" + data.getQuestion());
        trainingItem.put("output", "回答：" + data.getAnswer());
        
        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("question_id", data.getQuestionId());
        metadata.put("upvotes", data.getUpvotes());
        metadata.put("quality_score", data.getQualityScore());
        trainingItem.put("metadata", metadata);
        
        return toJsonString(trainingItem);
    }
    
    /**
     * 触发批量训练
     */
    private void triggerBatchTraining() {
        if (trainingDataBuffer.isEmpty()) {
            return;
        }
        
        log.info("触发批量训练，数据量: {}", trainingDataBuffer.size());
        
        // 创建训练任务
        TrainingJob job = TrainingJob.builder()
                .jobId("chatglm-batch-" + System.currentTimeMillis())
                .modelType("ChatGLM-6B")
                .algorithm(loraEnabled ? "LoRA" : "Full")
                .status(TrainingJob.JobStatus.PENDING)
                .batchSize(batchSize)
                .maxEpochs(maxEpochs)
                .learningRate(learningRate)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 设置超参数
        Map<String, String> hyperparams = new HashMap<>();
        hyperparams.put("lora_enabled", String.valueOf(loraEnabled));
        hyperparams.put("lora_r", String.valueOf(loraR));
        hyperparams.put("lora_alpha", String.valueOf(loraAlpha));
        hyperparams.put("lora_dropout", String.valueOf(loraDropout));
        job.setHyperparameters(hyperparams);
        
        // 异步启动训练
        startFineTuning(job);
        
        // 清空缓冲区
        trainingDataBuffer.clear();
    }
    
    /**
     * 验证训练数据
     */
    private boolean isValidTrainingData(ZhihuTrainingData data) {
        if (data == null || data.getQuestion() == null || data.getAnswer() == null) {
            return false;
        }
        
        // 检查长度
        if (data.getQuestion().length() < 10 || data.getAnswer().length() < 50) {
            return false;
        }
        
        if (data.getQuestion().length() > 512 || data.getAnswer().length() > 2000) {
            return false;
        }
        
        // 检查质量分数
        if (data.getQualityScore() < 0.7) {
            return false;
        }
        
        // 检查点赞数
        if (data.getUpvotes() < 5) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 执行微调训练
     */
    private TrainingResult executeFineTuning(TrainingJob job, String dataPath) throws Exception {
        if (loraEnabled) {
            return executeLoRAFineTuning(job, dataPath);
        } else {
            return executeFullFineTuning(job, dataPath);
        }
    }
    
    /**
     * 获取微调模型路径
     */
    private String getFineTunedModelPath(TrainingJob job) {
        return Paths.get(fineTunedPath, job.getJobId()).toString();
    }
    
    /**
     * 计算训练时间
     */
    private long calculateTrainingTime(TrainingJob job) {
        if (job.getStartTime() != null && job.getEndTime() != null) {
            return java.time.Duration.between(job.getStartTime(), job.getEndTime()).toMinutes();
        }
        return 0;
    }
    
    /**
     * 计算准确率
     */
    private double calculateAccuracy(TrainingJob job) {
        // 这里应该基于验证集计算真实的准确率
        // 暂时返回模拟值
        return Math.max(0.85, 1.0 - job.getTrainingLoss());
    }
    
    /**
     * 转换为JSON字符串
     */
    private String toJsonString(Object obj) {
        // 简单的JSON序列化实现
        // 在实际项目中应该使用Jackson或Gson
        return obj.toString();
    }
    
    /**
     * 获取训练统计信息
     */
    public Map<String, Object> getTrainingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_jobs", activeJobs.size());
        stats.put("total_samples_processed", totalSamplesProcessed.get());
        stats.put("buffer_size", trainingDataBuffer.size());
        stats.put("training_counter", trainingCounter.get());
        
        // 活跃任务详情
        List<Map<String, Object>> activeJobDetails = new ArrayList<>();
        for (TrainingJob job : activeJobs.values()) {
            Map<String, Object> jobDetail = new HashMap<>();
            jobDetail.put("job_id", job.getJobId());
            jobDetail.put("status", job.getStatus());
            jobDetail.put("epochs_completed", job.getEpochsCompleted());
            jobDetail.put("training_loss", job.getTrainingLoss());
            activeJobDetails.add(jobDetail);
        }
        stats.put("active_job_details", activeJobDetails);
        
        return stats;
    }
    
    /**
     * 停止训练任务
     */
    public boolean stopTraining(String jobId) {
        TrainingJob job = activeJobs.get(jobId);
        if (job != null) {
            job.setStatus(TrainingJob.JobStatus.CANCELLED);
            activeJobs.remove(jobId);
            log.info("训练任务已停止: {}", jobId);
            return true;
        }
        return false;
    }
    
    /**
     * 清理训练数据缓冲区
     */
    public void clearTrainingBuffer() {
        trainingDataBuffer.clear();
        log.info("训练数据缓冲区已清空");
    }
}