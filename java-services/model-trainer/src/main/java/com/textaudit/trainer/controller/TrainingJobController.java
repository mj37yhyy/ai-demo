package com.textaudit.trainer.controller;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.service.ModelTrainingService;
import com.textaudit.trainer.service.TrainingJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 训练任务控制器
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/training/jobs")
@RequiredArgsConstructor
@Tag(name = "训练任务管理", description = "训练任务的创建、查询、控制和管理")
public class TrainingJobController {
    
    private final TrainingJobService trainingJobService;
    private final ModelTrainingService modelTrainingService;
    
    /**
     * 创建训练任务
     */
    @PostMapping
    @Operation(summary = "创建训练任务", description = "创建新的模型训练任务")
    public ResponseEntity<TrainingJob> createJob(@Valid @RequestBody TrainingJob job) {
        log.info("创建训练任务: {}", job.getName());
        
        // 检查任务名称是否已存在
        if (trainingJobService.existsByName(job.getName())) {
            return ResponseEntity.badRequest().build();
        }
        
        TrainingJob createdJob = trainingJobService.createJob(job);
        return ResponseEntity.ok(createdJob);
    }
    
    /**
     * 开始训练任务
     */
    @PostMapping("/{jobId}/start")
    @Operation(summary = "开始训练任务", description = "启动指定的训练任务")
    public ResponseEntity<String> startJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("开始训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TrainingJob job = jobOpt.get();
        if (job.getStatus() != TrainingJob.JobStatus.PENDING && 
            job.getStatus() != TrainingJob.JobStatus.PAUSED) {
            return ResponseEntity.badRequest().body("任务状态不允许启动");
        }
        
        try {
            modelTrainingService.startTraining(jobId);
            return ResponseEntity.ok("训练任务已启动");
        } catch (Exception e) {
            log.error("启动训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("启动训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止训练任务
     */
    @PostMapping("/{jobId}/stop")
    @Operation(summary = "停止训练任务", description = "停止指定的训练任务")
    public ResponseEntity<String> stopJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("停止训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            modelTrainingService.stopTraining(jobId);
            return ResponseEntity.ok("训练任务已停止");
        } catch (Exception e) {
            log.error("停止训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("停止训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 暂停训练任务
     */
    @PostMapping("/{jobId}/pause")
    @Operation(summary = "暂停训练任务", description = "暂停指定的训练任务")
    public ResponseEntity<String> pauseJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("暂停训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            modelTrainingService.pauseTraining(jobId);
            return ResponseEntity.ok("训练任务已暂停");
        } catch (Exception e) {
            log.error("暂停训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("暂停训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 恢复训练任务
     */
    @PostMapping("/{jobId}/resume")
    @Operation(summary = "恢复训练任务", description = "恢复暂停的训练任务")
    public ResponseEntity<String> resumeJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("恢复训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TrainingJob job = jobOpt.get();
        if (job.getStatus() != TrainingJob.JobStatus.PAUSED) {
            return ResponseEntity.badRequest().body("任务未处于暂停状态");
        }
        
        try {
            modelTrainingService.resumeTraining(jobId);
            return ResponseEntity.ok("训练任务已恢复");
        } catch (Exception e) {
            log.error("恢复训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("恢复训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消训练任务
     */
    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "取消训练任务", description = "取消指定的训练任务")
    public ResponseEntity<String> cancelJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("取消训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            trainingJobService.cancelJob(jobId);
            return ResponseEntity.ok("训练任务已取消");
        } catch (Exception e) {
            log.error("取消训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("取消训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取训练任务详情
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "获取训练任务详情", description = "根据ID获取训练任务的详细信息")
    public ResponseEntity<TrainingJob> getJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        return jobOpt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 更新训练任务
     */
    @PutMapping("/{jobId}")
    @Operation(summary = "更新训练任务", description = "更新训练任务的信息")
    public ResponseEntity<TrainingJob> updateJob(
            @Parameter(description = "任务ID") @PathVariable String jobId,
            @Valid @RequestBody TrainingJob job) {
        
        Optional<TrainingJob> existingJobOpt = trainingJobService.getJobById(jobId);
        if (existingJobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TrainingJob existingJob = existingJobOpt.get();
        
        // 只允许更新某些字段
        existingJob.setName(job.getName());
        existingJob.setDescription(job.getDescription());
        existingJob.setHyperparameters(job.getHyperparameters());
        
        TrainingJob updatedJob = trainingJobService.updateJob(existingJob);
        return ResponseEntity.ok(updatedJob);
    }
    
    /**
     * 删除训练任务
     */
    @DeleteMapping("/{jobId}")
    @Operation(summary = "删除训练任务", description = "删除指定的训练任务")
    public ResponseEntity<String> deleteJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        log.info("删除训练任务: {}", jobId);
        
        Optional<TrainingJob> jobOpt = trainingJobService.getJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TrainingJob job = jobOpt.get();
        if (job.getStatus() == TrainingJob.JobStatus.RUNNING) {
            return ResponseEntity.badRequest().body("无法删除正在运行的任务");
        }
        
        try {
            trainingJobService.deleteJob(jobId);
            return ResponseEntity.ok("训练任务已删除");
        } catch (Exception e) {
            log.error("删除训练任务失败: {}", jobId, e);
            return ResponseEntity.internalServerError().body("删除训练任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 分页获取训练任务列表
     */
    @GetMapping
    @Operation(summary = "获取训练任务列表", description = "分页获取训练任务列表")
    public ResponseEntity<Page<TrainingJob>> getJobs(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<TrainingJob> jobs = trainingJobService.getJobs(pageable);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 搜索训练任务
     */
    @GetMapping("/search")
    @Operation(summary = "搜索训练任务", description = "根据条件搜索训练任务")
    public ResponseEntity<Page<TrainingJob>> searchJobs(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "任务状态") @RequestParam(required = false) TrainingJob.JobStatus status,
            @Parameter(description = "模型类型") @RequestParam(required = false) TrainingJob.ModelType modelType,
            @Parameter(description = "创建者") @RequestParam(required = false) String createdBy,
            @Parameter(description = "开始时间") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<TrainingJob> jobs = trainingJobService.searchJobs(
            keyword, status, modelType, createdBy, startTime, endTime, pageable);
        
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 根据状态获取训练任务
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "根据状态获取训练任务", description = "获取指定状态的所有训练任务")
    public ResponseEntity<List<TrainingJob>> getJobsByStatus(
            @Parameter(description = "任务状态") @PathVariable TrainingJob.JobStatus status) {
        List<TrainingJob> jobs = trainingJobService.getJobsByStatus(status);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 根据模型类型获取训练任务
     */
    @GetMapping("/model-type/{modelType}")
    @Operation(summary = "根据模型类型获取训练任务", description = "获取指定模型类型的所有训练任务")
    public ResponseEntity<List<TrainingJob>> getJobsByModelType(
            @Parameter(description = "模型类型") @PathVariable TrainingJob.ModelType modelType) {
        List<TrainingJob> jobs = trainingJobService.getJobsByModelType(modelType);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 根据创建者获取训练任务
     */
    @GetMapping("/created-by/{createdBy}")
    @Operation(summary = "根据创建者获取训练任务", description = "获取指定创建者的所有训练任务")
    public ResponseEntity<List<TrainingJob>> getJobsByCreatedBy(
            @Parameter(description = "创建者") @PathVariable String createdBy) {
        List<TrainingJob> jobs = trainingJobService.getJobsByCreatedBy(createdBy);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取正在运行的任务
     */
    @GetMapping("/running")
    @Operation(summary = "获取正在运行的任务", description = "获取所有正在运行的训练任务")
    public ResponseEntity<List<TrainingJob>> getRunningJobs() {
        List<TrainingJob> jobs = trainingJobService.getRunningJobs();
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取待处理的任务
     */
    @GetMapping("/pending")
    @Operation(summary = "获取待处理的任务", description = "获取所有待处理的训练任务")
    public ResponseEntity<List<TrainingJob>> getPendingJobs() {
        List<TrainingJob> jobs = trainingJobService.getPendingJobs();
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取已完成的任务
     */
    @GetMapping("/completed")
    @Operation(summary = "获取已完成的任务", description = "获取所有已完成的训练任务")
    public ResponseEntity<List<TrainingJob>> getCompletedJobs() {
        List<TrainingJob> jobs = trainingJobService.getCompletedJobs();
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取失败的任务
     */
    @GetMapping("/failed")
    @Operation(summary = "获取失败的任务", description = "获取所有失败的训练任务")
    public ResponseEntity<List<TrainingJob>> getFailedJobs() {
        List<TrainingJob> jobs = trainingJobService.getFailedJobs();
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取最近的任务
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近的任务", description = "获取最近创建的训练任务")
    public ResponseEntity<List<TrainingJob>> getRecentJobs(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        List<TrainingJob> jobs = trainingJobService.getRecentJobs(limit);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * 获取任务统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取任务统计信息", description = "获取训练任务的统计信息")
    public ResponseEntity<TrainingJobService.JobStatistics> getJobStatistics() {
        TrainingJobService.JobStatistics statistics = trainingJobService.getJobStatistics();
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * 清理旧任务
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理旧任务", description = "清理指定时间之前的旧任务")
    public ResponseEntity<String> cleanupOldJobs(
            @Parameter(description = "清理时间点") @RequestParam 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @Parameter(description = "要清理的状态") @RequestParam(required = false) 
            TrainingJob.JobStatus[] statuses) {
        
        int cleanedCount = trainingJobService.cleanupOldJobs(before, statuses);
        return ResponseEntity.ok(String.format("已清理 %d 个旧任务", cleanedCount));
    }
}