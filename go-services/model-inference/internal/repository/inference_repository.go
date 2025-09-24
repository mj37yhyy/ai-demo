package repository

import (
	"fmt"
	"time"

	"gorm.io/gorm"

	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/model"
)

// InferenceRepository 推理仓库接口
type InferenceRepository interface {
	Create(request *model.InferenceRequest) error
	GetByRequestID(requestID string) (*model.InferenceRequest, error)
	GetByID(id uint) (*model.InferenceRequest, error)
	List(limit, offset int) ([]*model.InferenceRequest, error)
	ListByStatus(status model.InferenceStatus, limit, offset int) ([]*model.InferenceRequest, error)
	ListByModelName(modelName string, limit, offset int) ([]*model.InferenceRequest, error)
	Update(request *model.InferenceRequest) error
	UpdateStatus(requestID string, status model.InferenceStatus) error
	UpdateResult(requestID string, result string, endTime time.Time, duration int64) error
	UpdateError(requestID string, errorMsg string, endTime time.Time, duration int64) error
	Delete(id uint) error
	DeleteOldRecords(before time.Time) error
	GetStatistics() (*model.InferenceStatistics, error)
	Count() (int64, error)
	CountByStatus(status model.InferenceStatus) (int64, error)
	CountByModelName(modelName string) (int64, error)
	GetAverageLatency() (float64, error)
	GetRequestsPerSecond(duration time.Duration) (float64, error)
}

// inferenceRepository 推理仓库实现
type inferenceRepository struct {
	db *gorm.DB
}

// NewInferenceRepository 创建推理仓库
func NewInferenceRepository(db *gorm.DB) InferenceRepository {
	return &inferenceRepository{db: db}
}

// Create 创建推理请求
func (r *inferenceRepository) Create(request *model.InferenceRequest) error {
	if err := r.db.Create(request).Error; err != nil {
		return fmt.Errorf("创建推理请求失败: %w", err)
	}
	return nil
}

// GetByRequestID 根据请求ID获取推理请求
func (r *inferenceRepository) GetByRequestID(requestID string) (*model.InferenceRequest, error) {
	var request model.InferenceRequest
	if err := r.db.Where("request_id = ?", requestID).First(&request).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, fmt.Errorf("获取推理请求失败: %w", err)
	}
	return &request, nil
}

// GetByID 根据ID获取推理请求
func (r *inferenceRepository) GetByID(id uint) (*model.InferenceRequest, error) {
	var request model.InferenceRequest
	if err := r.db.First(&request, id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, fmt.Errorf("获取推理请求失败: %w", err)
	}
	return &request, nil
}

// List 获取推理请求列表
func (r *inferenceRepository) List(limit, offset int) ([]*model.InferenceRequest, error) {
	var requests []*model.InferenceRequest
	if err := r.db.Limit(limit).Offset(offset).Order("created_at DESC").Find(&requests).Error; err != nil {
		return nil, fmt.Errorf("获取推理请求列表失败: %w", err)
	}
	return requests, nil
}

// ListByStatus 根据状态获取推理请求列表
func (r *inferenceRepository) ListByStatus(status model.InferenceStatus, limit, offset int) ([]*model.InferenceRequest, error) {
	var requests []*model.InferenceRequest
	if err := r.db.Where("status = ?", status).Limit(limit).Offset(offset).Order("created_at DESC").Find(&requests).Error; err != nil {
		return nil, fmt.Errorf("获取推理请求列表失败: %w", err)
	}
	return requests, nil
}

// ListByModelName 根据模型名称获取推理请求列表
func (r *inferenceRepository) ListByModelName(modelName string, limit, offset int) ([]*model.InferenceRequest, error) {
	var requests []*model.InferenceRequest
	if err := r.db.Where("model_name = ?", modelName).Limit(limit).Offset(offset).Order("created_at DESC").Find(&requests).Error; err != nil {
		return nil, fmt.Errorf("获取推理请求列表失败: %w", err)
	}
	return requests, nil
}

// Update 更新推理请求
func (r *inferenceRepository) Update(request *model.InferenceRequest) error {
	if err := r.db.Save(request).Error; err != nil {
		return fmt.Errorf("更新推理请求失败: %w", err)
	}
	return nil
}

// UpdateStatus 更新推理请求状态
func (r *inferenceRepository) UpdateStatus(requestID string, status model.InferenceStatus) error {
	if err := r.db.Model(&model.InferenceRequest{}).Where("request_id = ?", requestID).Update("status", status).Error; err != nil {
		return fmt.Errorf("更新推理请求状态失败: %w", err)
	}
	return nil
}

// UpdateResult 更新推理结果
func (r *inferenceRepository) UpdateResult(requestID string, result string, endTime time.Time, duration int64) error {
	updates := map[string]interface{}{
		"result":   result,
		"end_time": endTime,
		"duration": duration,
		"status":   model.InferenceStatusCompleted,
	}
	if err := r.db.Model(&model.InferenceRequest{}).Where("request_id = ?", requestID).Updates(updates).Error; err != nil {
		return fmt.Errorf("更新推理结果失败: %w", err)
	}
	return nil
}

// UpdateError 更新推理错误
func (r *inferenceRepository) UpdateError(requestID string, errorMsg string, endTime time.Time, duration int64) error {
	updates := map[string]interface{}{
		"error":    errorMsg,
		"end_time": endTime,
		"duration": duration,
		"status":   model.InferenceStatusFailed,
	}
	if err := r.db.Model(&model.InferenceRequest{}).Where("request_id = ?", requestID).Updates(updates).Error; err != nil {
		return fmt.Errorf("更新推理错误失败: %w", err)
	}
	return nil
}

// Delete 删除推理请求
func (r *inferenceRepository) Delete(id uint) error {
	if err := r.db.Delete(&model.InferenceRequest{}, id).Error; err != nil {
		return fmt.Errorf("删除推理请求失败: %w", err)
	}
	return nil
}

// DeleteOldRecords 删除旧记录
func (r *inferenceRepository) DeleteOldRecords(before time.Time) error {
	if err := r.db.Where("created_at < ?", before).Delete(&model.InferenceRequest{}).Error; err != nil {
		return fmt.Errorf("删除旧记录失败: %w", err)
	}
	return nil
}

// GetStatistics 获取推理统计信息
func (r *inferenceRepository) GetStatistics() (*model.InferenceStatistics, error) {
	var stats model.InferenceStatistics
	
	// 总请求数
	if err := r.db.Model(&model.InferenceRequest{}).Count(&stats.TotalRequests).Error; err != nil {
		return nil, fmt.Errorf("获取总请求数失败: %w", err)
	}
	
	// 完成请求数
	if err := r.db.Model(&model.InferenceRequest{}).Where("status = ?", model.InferenceStatusCompleted).Count(&stats.CompletedRequests).Error; err != nil {
		return nil, fmt.Errorf("获取完成请求数失败: %w", err)
	}
	
	// 失败请求数
	if err := r.db.Model(&model.InferenceRequest{}).Where("status = ?", model.InferenceStatusFailed).Count(&stats.FailedRequests).Error; err != nil {
		return nil, fmt.Errorf("获取失败请求数失败: %w", err)
	}
	
	// 平均延迟
	var avgLatency float64
	if err := r.db.Model(&model.InferenceRequest{}).Where("status = ? AND duration > 0", model.InferenceStatusCompleted).Select("AVG(duration)").Scan(&avgLatency).Error; err != nil {
		return nil, fmt.Errorf("获取平均延迟失败: %w", err)
	}
	stats.AverageLatency = avgLatency
	
	// 每秒请求数（最近1小时）
	oneHourAgo := time.Now().Add(-time.Hour)
	var recentRequests int64
	if err := r.db.Model(&model.InferenceRequest{}).Where("created_at > ?", oneHourAgo).Count(&recentRequests).Error; err != nil {
		return nil, fmt.Errorf("获取最近请求数失败: %w", err)
	}
	stats.RequestsPerSecond = float64(recentRequests) / 3600.0
	
	return &stats, nil
}

// Count 获取推理请求总数
func (r *inferenceRepository) Count() (int64, error) {
	var count int64
	if err := r.db.Model(&model.InferenceRequest{}).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取推理请求总数失败: %w", err)
	}
	return count, nil
}

// CountByStatus 根据状态获取推理请求数量
func (r *inferenceRepository) CountByStatus(status model.InferenceStatus) (int64, error) {
	var count int64
	if err := r.db.Model(&model.InferenceRequest{}).Where("status = ?", status).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取推理请求数量失败: %w", err)
	}
	return count, nil
}

// CountByModelName 根据模型名称获取推理请求数量
func (r *inferenceRepository) CountByModelName(modelName string) (int64, error) {
	var count int64
	if err := r.db.Model(&model.InferenceRequest{}).Where("model_name = ?", modelName).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取推理请求数量失败: %w", err)
	}
	return count, nil
}

// GetAverageLatency 获取平均延迟
func (r *inferenceRepository) GetAverageLatency() (float64, error) {
	var avgLatency float64
	if err := r.db.Model(&model.InferenceRequest{}).Where("status = ? AND duration > 0", model.InferenceStatusCompleted).Select("AVG(duration)").Scan(&avgLatency).Error; err != nil {
		return 0, fmt.Errorf("获取平均延迟失败: %w", err)
	}
	return avgLatency, nil
}

// GetRequestsPerSecond 获取每秒请求数
func (r *inferenceRepository) GetRequestsPerSecond(duration time.Duration) (float64, error) {
	since := time.Now().Add(-duration)
	var count int64
	if err := r.db.Model(&model.InferenceRequest{}).Where("created_at > ?", since).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取请求数失败: %w", err)
	}
	return float64(count) / duration.Seconds(), nil
}