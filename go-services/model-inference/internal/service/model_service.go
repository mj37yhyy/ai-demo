package service

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/sirupsen/logrus"

	"github.com/textaudit/model-inference/internal/config"
	"github.com/textaudit/model-inference/internal/model"
	"github.com/textaudit/model-inference/internal/repository"
)

// ModelService 模型服务接口
type ModelService interface {
	LoadModel(ctx context.Context, name string, force bool) error
	UnloadModel(ctx context.Context, name string) error
	GetModel(ctx context.Context, name string) (*model.Model, error)
	ListModels(ctx context.Context, limit, offset int) ([]*model.Model, error)
	ListModelsByType(ctx context.Context, modelType model.ModelType, limit, offset int) ([]*model.Model, error)
	GetModelStatus(ctx context.Context, name string) (*model.ModelStatusResponse, error)
	GetStatistics(ctx context.Context) (*model.ModelStatistics, error)
	IsModelLoaded(name string) bool
	GetLoadedModels() []string
}

// modelService 模型服务实现
type modelService struct {
	modelRepo   repository.ModelRepository
	cacheRepo   repository.CacheRepository
	config      config.ModelConfig
	loadedModels sync.Map // 存储已加载的模型
	mu          sync.RWMutex
}

// NewModelService 创建模型服务
func NewModelService(modelRepo repository.ModelRepository, cacheRepo repository.CacheRepository, cfg config.ModelConfig) ModelService {
	return &modelService{
		modelRepo: modelRepo,
		cacheRepo: cacheRepo,
		config:    cfg,
	}
}

// LoadModel 加载模型
func (s *modelService) LoadModel(ctx context.Context, name string, force bool) error {
	// 检查模型是否已加载
	if !force && s.IsModelLoaded(name) {
		return fmt.Errorf("模型 %s 已经加载", name)
	}

	// 获取模型信息
	modelInfo, err := s.modelRepo.GetByName(name)
	if err != nil {
		return fmt.Errorf("获取模型信息失败: %w", err)
	}
	if modelInfo == nil {
		return fmt.Errorf("模型 %s 不存在", name)
	}

	// 检查模型文件是否存在
	modelPath := filepath.Join(s.config.StoragePath, modelInfo.FilePath)
	if _, err := os.Stat(modelPath); os.IsNotExist(err) {
		return fmt.Errorf("模型文件不存在: %s", modelPath)
	}

	// 检查已加载模型数量限制
	if err := s.checkLoadedModelsLimit(); err != nil {
		return err
	}

	// 更新模型状态为加载中
	if err := s.modelRepo.UpdateStatus(name, model.ModelStatusLoading); err != nil {
		return fmt.Errorf("更新模型状态失败: %w", err)
	}

	// 模拟模型加载过程（实际项目中这里会加载真实的模型）
	go func() {
		defer func() {
			if r := recover(); r != nil {
				logrus.Errorf("加载模型 %s 时发生panic: %v", name, r)
				s.modelRepo.UpdateStatus(name, model.ModelStatusError)
			}
		}()

		// 模拟加载时间
		time.Sleep(2 * time.Second)

		// 将模型标记为已加载
		now := time.Now()
		s.loadedModels.Store(name, &LoadedModel{
			Name:     name,
			Type:     modelInfo.Type,
			LoadedAt: now,
			FilePath: modelPath,
		})

		// 更新数据库状态
		s.modelRepo.UpdateStatus(name, model.ModelStatusLoaded)
		s.modelRepo.UpdateLoadedAt(name, &now)

		// 缓存模型信息
		cacheKey := fmt.Sprintf("model:%s", name)
		s.cacheRepo.Set(context.Background(), cacheKey, modelInfo, time.Duration(s.config.CacheTTL)*time.Second)

		logrus.Infof("模型 %s 加载成功", name)
	}()

	return nil
}

// UnloadModel 卸载模型
func (s *modelService) UnloadModel(ctx context.Context, name string) error {
	// 检查模型是否已加载
	if !s.IsModelLoaded(name) {
		return fmt.Errorf("模型 %s 未加载", name)
	}

	// 从内存中移除模型
	s.loadedModels.Delete(name)

	// 更新模型状态
	if err := s.modelRepo.UpdateStatus(name, model.ModelStatusUnloaded); err != nil {
		return fmt.Errorf("更新模型状态失败: %w", err)
	}

	// 更新加载时间为空
	if err := s.modelRepo.UpdateLoadedAt(name, nil); err != nil {
		return fmt.Errorf("更新模型加载时间失败: %w", err)
	}

	// 清除缓存
	cacheKey := fmt.Sprintf("model:%s", name)
	s.cacheRepo.Delete(ctx, cacheKey)

	logrus.Infof("模型 %s 卸载成功", name)
	return nil
}

// GetModel 获取模型信息
func (s *modelService) GetModel(ctx context.Context, name string) (*model.Model, error) {
	// 先从缓存获取
	cacheKey := fmt.Sprintf("model:%s", name)
	var cachedModel model.Model
	if err := s.cacheRepo.Get(ctx, cacheKey, &cachedModel); err == nil {
		return &cachedModel, nil
	}

	// 从数据库获取
	modelInfo, err := s.modelRepo.GetByName(name)
	if err != nil {
		return nil, fmt.Errorf("获取模型信息失败: %w", err)
	}

	if modelInfo != nil {
		// 缓存模型信息
		s.cacheRepo.Set(ctx, cacheKey, modelInfo, time.Duration(s.config.CacheTTL)*time.Second)
	}

	return modelInfo, nil
}

// ListModels 获取模型列表
func (s *modelService) ListModels(ctx context.Context, limit, offset int) ([]*model.Model, error) {
	return s.modelRepo.List(limit, offset)
}

// ListModelsByType 根据类型获取模型列表
func (s *modelService) ListModelsByType(ctx context.Context, modelType model.ModelType, limit, offset int) ([]*model.Model, error) {
	return s.modelRepo.ListByType(modelType, limit, offset)
}

// GetModelStatus 获取模型状态
func (s *modelService) GetModelStatus(ctx context.Context, name string) (*model.ModelStatusResponse, error) {
	modelInfo, err := s.GetModel(ctx, name)
	if err != nil {
		return nil, err
	}
	if modelInfo == nil {
		return nil, fmt.Errorf("模型 %s 不存在", name)
	}

	response := &model.ModelStatusResponse{
		Name:     modelInfo.Name,
		Status:   modelInfo.Status,
		LoadedAt: modelInfo.LoadedAt,
	}

	// 如果模型已加载，获取加载信息
	if loadedModel, ok := s.loadedModels.Load(name); ok {
		if lm, ok := loadedModel.(*LoadedModel); ok {
			response.LoadedAt = &lm.LoadedAt
			response.Metadata = map[string]interface{}{
				"file_path": lm.FilePath,
				"type":      lm.Type,
			}
		}
	}

	return response, nil
}

// GetStatistics 获取模型统计信息
func (s *modelService) GetStatistics(ctx context.Context) (*model.ModelStatistics, error) {
	return s.modelRepo.GetStatistics()
}

// IsModelLoaded 检查模型是否已加载
func (s *modelService) IsModelLoaded(name string) bool {
	_, loaded := s.loadedModels.Load(name)
	return loaded
}

// GetLoadedModels 获取已加载的模型列表
func (s *modelService) GetLoadedModels() []string {
	var models []string
	s.loadedModels.Range(func(key, value interface{}) bool {
		if name, ok := key.(string); ok {
			models = append(models, name)
		}
		return true
	})
	return models
}

// checkLoadedModelsLimit 检查已加载模型数量限制
func (s *modelService) checkLoadedModelsLimit() error {
	loadedCount := 0
	s.loadedModels.Range(func(key, value interface{}) bool {
		loadedCount++
		return true
	})

	if loadedCount >= s.config.MaxLoadedModels {
		return fmt.Errorf("已加载模型数量达到上限 %d", s.config.MaxLoadedModels)
	}

	return nil
}

// LoadedModel 已加载的模型信息
type LoadedModel struct {
	Name     string
	Type     model.ModelType
	LoadedAt time.Time
	FilePath string
}