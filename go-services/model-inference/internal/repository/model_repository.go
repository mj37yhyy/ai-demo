package repository

import (
	"fmt"
	"time"

	"gorm.io/gorm"

	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/model"
)

// ModelRepository 模型仓库接口
type ModelRepository interface {
	Create(model *model.Model) error
	GetByName(name string) (*model.Model, error)
	GetByID(id uint) (*model.Model, error)
	List(limit, offset int) ([]*model.Model, error)
	ListByType(modelType model.ModelType, limit, offset int) ([]*model.Model, error)
	Update(model *model.Model) error
	Delete(id uint) error
	UpdateStatus(name string, status model.ModelStatus) error
	UpdateLoadedAt(name string, loadedAt *time.Time) error
	GetStatistics() (*model.ModelStatistics, error)
	Count() (int64, error)
	CountByType(modelType model.ModelType) (int64, error)
	CountByStatus(status model.ModelStatus) (int64, error)
}

// modelRepository 模型仓库实现
type modelRepository struct {
	db *gorm.DB
}

// NewModelRepository 创建模型仓库
func NewModelRepository(db *gorm.DB) ModelRepository {
	return &modelRepository{db: db}
}

// Create 创建模型
func (r *modelRepository) Create(m *model.Model) error {
	if err := r.db.Create(m).Error; err != nil {
		return fmt.Errorf("创建模型失败: %w", err)
	}
	return nil
}

// GetByName 根据名称获取模型
func (r *modelRepository) GetByName(name string) (*model.Model, error) {
	var m model.Model
	if err := r.db.Where("name = ?", name).First(&m).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, fmt.Errorf("获取模型失败: %w", err)
	}
	return &m, nil
}

// GetByID 根据ID获取模型
func (r *modelRepository) GetByID(id uint) (*model.Model, error) {
	var m model.Model
	if err := r.db.First(&m, id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, fmt.Errorf("获取模型失败: %w", err)
	}
	return &m, nil
}

// List 获取模型列表
func (r *modelRepository) List(limit, offset int) ([]*model.Model, error) {
	var models []*model.Model
	if err := r.db.Limit(limit).Offset(offset).Order("created_at DESC").Find(&models).Error; err != nil {
		return nil, fmt.Errorf("获取模型列表失败: %w", err)
	}
	return models, nil
}

// ListByType 根据类型获取模型列表
func (r *modelRepository) ListByType(modelType model.ModelType, limit, offset int) ([]*model.Model, error) {
	var models []*model.Model
	if err := r.db.Where("type = ?", modelType).Limit(limit).Offset(offset).Order("created_at DESC").Find(&models).Error; err != nil {
		return nil, fmt.Errorf("获取模型列表失败: %w", err)
	}
	return models, nil
}

// Update 更新模型
func (r *modelRepository) Update(m *model.Model) error {
	if err := r.db.Save(m).Error; err != nil {
		return fmt.Errorf("更新模型失败: %w", err)
	}
	return nil
}

// Delete 删除模型
func (r *modelRepository) Delete(id uint) error {
	if err := r.db.Delete(&model.Model{}, id).Error; err != nil {
		return fmt.Errorf("删除模型失败: %w", err)
	}
	return nil
}

// UpdateStatus 更新模型状态
func (r *modelRepository) UpdateStatus(name string, status model.ModelStatus) error {
	if err := r.db.Model(&model.Model{}).Where("name = ?", name).Update("status", status).Error; err != nil {
		return fmt.Errorf("更新模型状态失败: %w", err)
	}
	return nil
}

// UpdateLoadedAt 更新模型加载时间
func (r *modelRepository) UpdateLoadedAt(name string, loadedAt *time.Time) error {
	if err := r.db.Model(&model.Model{}).Where("name = ?", name).Update("loaded_at", loadedAt).Error; err != nil {
		return fmt.Errorf("更新模型加载时间失败: %w", err)
	}
	return nil
}

// GetStatistics 获取模型统计信息
func (r *modelRepository) GetStatistics() (*model.ModelStatistics, error) {
	var stats model.ModelStatistics
	
	// 总模型数
	if err := r.db.Model(&model.Model{}).Count(&stats.TotalModels).Error; err != nil {
		return nil, fmt.Errorf("获取总模型数失败: %w", err)
	}
	
	// 已加载模型数
	if err := r.db.Model(&model.Model{}).Where("status = ?", model.ModelStatusLoaded).Count(&stats.LoadedModels).Error; err != nil {
		return nil, fmt.Errorf("获取已加载模型数失败: %w", err)
	}
	
	// 未加载模型数
	if err := r.db.Model(&model.Model{}).Where("status = ?", model.ModelStatusUnloaded).Count(&stats.UnloadedModels).Error; err != nil {
		return nil, fmt.Errorf("获取未加载模型数失败: %w", err)
	}
	
	// 错误模型数
	if err := r.db.Model(&model.Model{}).Where("status = ?", model.ModelStatusError).Count(&stats.ErrorModels).Error; err != nil {
		return nil, fmt.Errorf("获取错误模型数失败: %w", err)
	}
	
	return &stats, nil
}

// Count 获取模型总数
func (r *modelRepository) Count() (int64, error) {
	var count int64
	if err := r.db.Model(&model.Model{}).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取模型总数失败: %w", err)
	}
	return count, nil
}

// CountByType 根据类型获取模型数量
func (r *modelRepository) CountByType(modelType model.ModelType) (int64, error) {
	var count int64
	if err := r.db.Model(&model.Model{}).Where("type = ?", modelType).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取模型数量失败: %w", err)
	}
	return count, nil
}

// CountByStatus 根据状态获取模型数量
func (r *modelRepository) CountByStatus(status model.ModelStatus) (int64, error) {
	var count int64
	if err := r.db.Model(&model.Model{}).Where("status = ?", status).Count(&count).Error; err != nil {
		return 0, fmt.Errorf("获取模型数量失败: %w", err)
	}
	return count, nil
}