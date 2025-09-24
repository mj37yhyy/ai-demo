package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.repository.TrainingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 训练任务服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingJobService {
    
    private final TrainingJobRepository trainingJobRepository;
    
    /**
     * 创建训练任务
     */
    @Transactional
    public TrainingJob createJob(TrainingJob job) {
        job.setStatus(TrainingJob.JobStatus.PENDING);
        job.setProgress(0.0);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        
        TrainingJob savedJob = trainingJobRepository.save(job);
        log.info("创建训练任务成功: {}", savedJob.getId());
        return savedJob;
    }
    
    /**
     * 根据ID获取训练任务
     */
    public Optional<TrainingJob> getJobById(String jobId) {
        return trainingJobRepository.findById(jobId);
    }
    
    /**
     * 根据名称获取训练任务
     */
    public Optional<TrainingJob> getJobByName(String jobName) {
        return trainingJobRepository.findByName(jobName);
    }
    
    /**
     * 更新训练任务
     */
    @Transactional
    public TrainingJob updateJob(TrainingJob job) {
        job.setUpdatedAt(LocalDateTime.now());
        TrainingJob updatedJob = trainingJobRepository.save(job);
        log.debug("更新训练任务: {}", job.getId());
        return updatedJob;
    }
    
    /**
     * 更新任务状态
     */
    @Transactional
    public void updateJobStatus(String jobId, TrainingJob.JobStatus status) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setStatus(status);
            job.setUpdatedAt(LocalDateTime.now());
            
            if (status == TrainingJob.JobStatus.RUNNING) {
                job.setStartTime(LocalDateTime.now());
            } else if (status == TrainingJob.JobStatus.COMPLETED || 
                      status == TrainingJob.JobStatus.FAILED) {
                job.setEndTime(LocalDateTime.now());
                if (job.getStartTime() != null) {
                    job.setTrainingDuration(
                        java.time.Duration.between(job.getStartTime(), job.getEndTime()).toMillis()
                    );
                }
            }
            
            trainingJobRepository.save(job);
            log.info("更新任务状态: {} -> {}", jobId, status);
        }
    }
    
    /**
     * 更新任务进度
     */
    @Transactional
    public void updateJobProgress(String jobId, double progress) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setProgress(progress);
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
            log.debug("更新任务进度: {} -> {}%", jobId, progress);
        }
    }
    
    /**
     * 更新当前epoch和batch
     */
    @Transactional
    public void updateJobEpochAndBatch(String jobId, int currentEpoch, int currentBatch) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setCurrentEpoch(currentEpoch);
            job.setCurrentBatch(currentBatch);
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
        }
    }
    
    /**
     * 更新训练指标
     */
    @Transactional
    public void updateTrainingMetrics(String jobId, Double trainAccuracy, Double trainLoss,
                                     Double validationAccuracy, Double validationLoss) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            if (trainAccuracy != null) job.setTrainingAccuracy(trainAccuracy);
            if (trainLoss != null) job.setTrainingLoss(trainLoss);
            if (validationAccuracy != null) job.setValidationAccuracy(validationAccuracy);
            if (validationLoss != null) job.setValidationLoss(validationLoss);
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
        }
    }
    
    /**
     * 更新测试指标
     */
    @Transactional
    public void updateTestMetrics(String jobId, Double testAccuracy, Double testLoss) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setTestAccuracy(testAccuracy);
            job.setTestLoss(testLoss);
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
        }
    }
    
    /**
     * 设置任务完成
     */
    @Transactional
    public void completeJob(String jobId, String modelPath, Long modelSize) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setStatus(TrainingJob.JobStatus.COMPLETED);
            job.setEndTime(LocalDateTime.now());
            job.setModelPath(modelPath);
            job.setModelSize(modelSize);
            job.setProgress(100.0);
            
            if (job.getStartTime() != null) {
                job.setTrainingDuration(
                    java.time.Duration.between(job.getStartTime(), job.getEndTime()).toMillis()
                );
            }
            
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
            log.info("任务完成: {}", jobId);
        }
    }
    
    /**
     * 设置任务失败
     */
    @Transactional
    public void failJob(String jobId, String errorMessage) {
        Optional<TrainingJob> jobOpt = trainingJobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            TrainingJob job = jobOpt.get();
            job.setStatus(TrainingJob.JobStatus.FAILED);
            job.setEndTime(LocalDateTime.now());
            job.setErrorMessage(errorMessage);
            
            if (job.getStartTime() != null) {
                job.setTrainingDuration(
                    java.time.Duration.between(job.getStartTime(), job.getEndTime()).toMillis()
                );
            }
            
            job.setUpdatedAt(LocalDateTime.now());
            trainingJobRepository.save(job);
            log.error("任务失败: {} - {}", jobId, errorMessage);
        }
    }
    
    /**
     * 暂停任务
     */
    @Transactional
    public void pauseJob(String jobId) {
        updateJobStatus(jobId, TrainingJob.JobStatus.PAUSED);
    }
    
    /**
     * 恢复任务
     */
    @Transactional
    public void resumeJob(String jobId) {
        updateJobStatus(jobId, TrainingJob.JobStatus.RUNNING);
    }
    
    /**
     * 取消任务
     */
    @Transactional
    public void cancelJob(String jobId) {
        updateJobStatus(jobId, TrainingJob.JobStatus.CANCELLED);
    }
    
    /**
     * 删除训练任务
     */
    @Transactional
    public void deleteJob(String jobId) {
        trainingJobRepository.deleteById(jobId);
        log.info("删除训练任务: {}", jobId);
    }
    
    /**
     * 获取所有训练任务
     */
    public List<TrainingJob> getAllJobs() {
        return trainingJobRepository.findAll();
    }
    
    /**
     * 分页获取训练任务
     */
    public Page<TrainingJob> getJobs(Pageable pageable) {
        return trainingJobRepository.findAll(pageable);
    }
    
    /**
     * 根据状态获取训练任务
     */
    public List<TrainingJob> getJobsByStatus(TrainingJob.JobStatus status) {
        return trainingJobRepository.findByStatus(status);
    }
    
    /**
     * 根据模型类型获取训练任务
     */
    public List<TrainingJob> getJobsByModelType(TrainingJob.ModelType modelType) {
        return trainingJobRepository.findByModelType(modelType);
    }
    
    /**
     * 根据创建者获取训练任务
     */
    public List<TrainingJob> getJobsByCreatedBy(String createdBy) {
        return trainingJobRepository.findByCreatedBy(createdBy);
    }
    
    /**
     * 获取正在运行的任务
     */
    public List<TrainingJob> getRunningJobs() {
        return trainingJobRepository.findByStatus(TrainingJob.JobStatus.RUNNING);
    }
    
    /**
     * 获取待处理的任务
     */
    public List<TrainingJob> getPendingJobs() {
        return trainingJobRepository.findByStatus(TrainingJob.JobStatus.PENDING);
    }
    
    /**
     * 获取已完成的任务
     */
    public List<TrainingJob> getCompletedJobs() {
        return trainingJobRepository.findByStatus(TrainingJob.JobStatus.COMPLETED);
    }
    
    /**
     * 获取失败的任务
     */
    public List<TrainingJob> getFailedJobs() {
        return trainingJobRepository.findByStatus(TrainingJob.JobStatus.FAILED);
    }
    
    /**
     * 搜索训练任务
     */
    public Page<TrainingJob> searchJobs(String keyword, TrainingJob.JobStatus status,
                                       TrainingJob.ModelType modelType, String createdBy,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       Pageable pageable) {
        
        Specification<TrainingJob> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 关键词搜索（名称或描述）
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.trim() + "%";
                Predicate namePredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")), likePattern.toLowerCase());
                Predicate descPredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")), likePattern.toLowerCase());
                predicates.add(criteriaBuilder.or(namePredicate, descPredicate));
            }
            
            // 状态过滤
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            
            // 模型类型过滤
            if (modelType != null) {
                predicates.add(criteriaBuilder.equal(root.get("modelType"), modelType));
            }
            
            // 创建者过滤
            if (createdBy != null && !createdBy.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy.trim()));
            }
            
            // 时间范围过滤
            if (startTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        return trainingJobRepository.findAll(spec, pageable);
    }
    
    /**
     * 获取任务统计信息
     */
    public JobStatistics getJobStatistics() {
        long totalJobs = trainingJobRepository.count();
        long pendingJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.PENDING);
        long runningJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.RUNNING);
        long completedJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.COMPLETED);
        long failedJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.FAILED);
        long pausedJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.PAUSED);
        long cancelledJobs = trainingJobRepository.countByStatus(TrainingJob.JobStatus.CANCELLED);
        
        return JobStatistics.builder()
                .totalJobs(totalJobs)
                .pendingJobs(pendingJobs)
                .runningJobs(runningJobs)
                .completedJobs(completedJobs)
                .failedJobs(failedJobs)
                .pausedJobs(pausedJobs)
                .cancelledJobs(cancelledJobs)
                .successRate(totalJobs > 0 ? (double) completedJobs / totalJobs * 100 : 0.0)
                .build();
    }
    
    /**
     * 检查任务名称是否存在
     */
    public boolean existsByName(String name) {
        return trainingJobRepository.existsByName(name);
    }
    
    /**
     * 获取用户的任务数量
     */
    public long countJobsByCreatedBy(String createdBy) {
        return trainingJobRepository.countByCreatedBy(createdBy);
    }
    
    /**
     * 获取最近的任务
     */
    public List<TrainingJob> getRecentJobs(int limit) {
        return trainingJobRepository.findTopByOrderByCreatedAtDesc(
            org.springframework.data.domain.PageRequest.of(0, limit)
        ).getContent();
    }
    
    /**
     * 清理旧任务
     */
    @Transactional
    public int cleanupOldJobs(LocalDateTime before, TrainingJob.JobStatus... statuses) {
        List<TrainingJob.JobStatus> statusList = statuses.length > 0 ? 
            List.of(statuses) : 
            List.of(TrainingJob.JobStatus.COMPLETED, TrainingJob.JobStatus.FAILED, 
                   TrainingJob.JobStatus.CANCELLED);
        
        List<TrainingJob> oldJobs = trainingJobRepository.findByCreatedAtBeforeAndStatusIn(before, statusList);
        
        for (TrainingJob job : oldJobs) {
            trainingJobRepository.delete(job);
        }
        
        log.info("清理了 {} 个旧任务", oldJobs.size());
        return oldJobs.size();
    }
    
    /**
     * 任务统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class JobStatistics {
        private Long totalJobs;
        private Long pendingJobs;
        private Long runningJobs;
        private Long completedJobs;
        private Long failedJobs;
        private Long pausedJobs;
        private Long cancelledJobs;
        private Double successRate;
        
        public String getFormattedSuccessRate() {
            return String.format("%.1f%%", successRate);
        }
    }
}