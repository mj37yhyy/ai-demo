package service

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"gorm.io/gorm"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/collector"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/model"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/repository"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

type CollectorService struct {
	pb.UnimplementedDataCollectionServiceServer
	
	config     *config.Config
	repo       repository.Repository
	collectors map[pb.SourceType]collector.Collector
	tasks      map[string]*CollectionTask
	tasksMutex sync.RWMutex
}

// GetRepository 获取repository实例
func (s *CollectorService) GetRepository() repository.Repository {
	return s.repo
}

type CollectionTask struct {
	ID              string
	SourceType      pb.SourceType
	Config          *pb.CollectionConfig
	Status          pb.CollectionStatus
	CollectedCount  int32
	TotalCount      int32
	Progress        int32
	StartTime       *time.Time
	EndTime         *time.Time
	ErrorMessage    string
	cancelFunc      context.CancelFunc
}

func NewCollectorService(cfg *config.Config) (*CollectorService, error) {
	// 构建数据库DSN
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		cfg.Database.Username,
		cfg.Database.Password,
		cfg.Database.Host,
		cfg.Database.Port,
		cfg.Database.Database)
	
	// 初始化数据库连接
	repo, err := repository.NewMySQLRepository(dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to create repository: %w", err)
	}

	// 初始化采集器
	collectors := make(map[pb.SourceType]collector.Collector)
	
	// API 采集器
	apiCollector, err := collector.NewAPICollector(cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create API collector: %w", err)
	}
	collectors[pb.SourceType_API] = apiCollector

	// 网页爬虫采集器
	webCollector, err := collector.NewWebCollector(cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create web collector: %w", err)
	}
	collectors[pb.SourceType_WEB_CRAWLER] = webCollector

	// 本地文件采集器
	fileCollector, err := collector.NewFileCollector(cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create file collector: %w", err)
	}
	collectors[pb.SourceType_LOCAL_FILE] = fileCollector

	return &CollectorService{
		config:     cfg,
		repo:       repo,
		collectors: collectors,
		tasks:      make(map[string]*CollectionTask),
	}, nil
}

func (s *CollectorService) CollectText(ctx context.Context, req *pb.CollectRequest) (*pb.CollectResponse, error) {
	taskID := uuid.New().String()
	
	logrus.Info("CollectText method called - DEBUG TEST")
	
	logrus.WithFields(logrus.Fields{
		"task_id":     taskID,
		"source_type": req.Source.Type,
		"url":         req.Source.Url,
		"file_path":   req.Source.FilePath,
	}).Info("Starting text collection task")

	// 创建任务
	task := &CollectionTask{
		ID:         taskID,
		SourceType: req.Source.Type,
		Config:     req.Config,
		Status:     pb.CollectionStatus_COLLECTION_PENDING,
	}

	s.tasksMutex.Lock()
	s.tasks[taskID] = task
	s.tasksMutex.Unlock()

	// 保存任务到数据库
	dbTask := &model.CollectionTask{
		ID:         taskID,
		SourceType: req.Source.Type.String(),
		SourceURL:  req.Source.Url,
		SourceFilePath: req.Source.FilePath,
		Status:     pb.CollectionStatus_COLLECTION_PENDING.String(),
		StartTime:  nil, // 明确设置为nil，任务开始时会被设置
		EndTime:    nil, // 明确设置为nil，任务结束时会被设置
	}
	
	// 序列化配置，添加调试日志
	configBytes, err := json.Marshal(req.Config)
	if err != nil {
		logrus.WithError(err).Error("Failed to marshal config")
	}
	dbTask.Config = string(configBytes)
	
	logrus.WithFields(logrus.Fields{
		"task_id": taskID,
		"config_bytes": string(configBytes),
		"config_object": req.Config,
	}).Info("Config serialization debug")
	
	if err := s.repo.CreateCollectionTask(ctx, dbTask); err != nil {
		logrus.WithError(err).Error("Failed to save collection task")
		return nil, fmt.Errorf("failed to save collection task: %w", err)
	}

	// 异步执行采集任务 - 使用background context避免HTTP请求结束时任务被取消
	go s.executeCollectionTask(context.Background(), task, req)

	return &pb.CollectResponse{
		TaskId:         taskID,
		Status:         pb.CollectionStatus_COLLECTION_PENDING,
		CollectedCount: 0,
		Message:        "Collection task started",
	}, nil
}

func (s *CollectorService) GetCollectionStatus(ctx context.Context, req *pb.StatusRequest) (*pb.StatusResponse, error) {
	s.tasksMutex.RLock()
	task, exists := s.tasks[req.TaskId]
	s.tasksMutex.RUnlock()

	if !exists {
		// 从数据库查询
		dbTask, err := s.repo.GetCollectionTaskByID(ctx, req.TaskId)
		if err != nil {
			if err == gorm.ErrRecordNotFound {
				return nil, fmt.Errorf("task not found: %s", req.TaskId)
			}
			return nil, fmt.Errorf("failed to get task: %w", err)
		}

		return &pb.StatusResponse{
			TaskId:    dbTask.ID,
			Status:    parseCollectionStatus(dbTask.Status),
			Progress:  int32(dbTask.Progress),
			Message:   dbTask.ErrorMessage,
			StartTime: func() int64 { if dbTask.StartTime != nil { return dbTask.StartTime.Unix() } else { return 0 } }(),
			EndTime:   func() int64 { if dbTask.EndTime != nil { return dbTask.EndTime.Unix() } else { return 0 } }(),
		}, nil
	}

	resp := &pb.StatusResponse{
		TaskId:   task.ID,
		Status:   task.Status,
		Progress: task.Progress,
		Message:  task.ErrorMessage,
	}

	if task.StartTime != nil {
		resp.StartTime = task.StartTime.Unix()
	}
	if task.EndTime != nil {
		resp.EndTime = task.EndTime.Unix()
	}

	return resp, nil
}

func (s *CollectorService) executeCollectionTask(ctx context.Context, task *CollectionTask, req *pb.CollectRequest) {
	logrus.WithField("task_id", task.ID).Info("executeCollectionTask started")
	
	// 创建可取消的上下文
	taskCtx, cancel := context.WithCancel(ctx)
	task.cancelFunc = cancel
	defer cancel()

	logrus.WithField("task_id", task.ID).Info("Context created")

	// 更新任务状态为运行中
	now := time.Now()
	task.StartTime = &now
	task.Status = pb.CollectionStatus_COLLECTION_RUNNING
	
	logrus.WithFields(logrus.Fields{
		"task_id": task.ID,
		"config": task.Config,
	}).Info("About to call updateTaskInDB")
	
	s.updateTaskInDB(task)

	logrus.WithField("task_id", task.ID).Info("Collection task started")

	// 获取对应的采集器
	collector, exists := s.collectors[req.Source.Type]
	if !exists {
		s.handleTaskError(task, fmt.Errorf("unsupported source type: %v", req.Source.Type))
		return
	}

	// 执行采集
	textChan := make(chan *pb.RawText, 100)
	errorChan := make(chan error, 1)

	go func() {
		defer close(textChan)
		defer close(errorChan)
		
		err := collector.Collect(taskCtx, req.Source, req.Config, textChan)
		if err != nil {
			errorChan <- err
		}
	}()

	// 处理采集结果
	collectedCount := int32(0)
	for {
		select {
		case text, ok := <-textChan:
			if !ok {
				// 采集完成
				s.completeTask(task, collectedCount)
				return
			}
			
			// 保存文本到数据库
			if err := s.saveRawText(ctx, text); err != nil {
				logrus.WithError(err).Error("Failed to save raw text")
				continue
			}
			
			collectedCount++
			task.CollectedCount = collectedCount
			
			// 更新进度
			if req.Config.MaxCount > 0 {
				task.Progress = (collectedCount * 100) / req.Config.MaxCount
			}
			
			// 定期更新数据库
			if collectedCount%10 == 0 {
				s.updateTaskInDB(task)
			}

		case err := <-errorChan:
			if err != nil {
				s.handleTaskError(task, err)
				return
			}

		case <-taskCtx.Done():
			s.handleTaskError(task, fmt.Errorf("task cancelled"))
			return
		}
	}
}

func (s *CollectorService) saveRawText(ctx context.Context, text *pb.RawText) error {
	// 保存到数据库
	dbText := &model.RawText{
		ID:        text.Id,
		Content:   text.Content,
		Source:    text.Source,
		Timestamp: text.Timestamp,
	}
	
	if len(text.Metadata) > 0 {
		metadataBytes, _ := json.Marshal(text.Metadata)
		dbText.Metadata = string(metadataBytes)
	}
	
	if err := s.repo.SaveRawText(ctx, dbText); err != nil {
		return fmt.Errorf("failed to save to database: %w", err)
	}

	// 发送到消息队列 (暂时注释掉，因为repository接口中没有PublishRawText方法)
	// TODO: 实现消息队列发布功能
	// if err := s.repo.PublishRawText(ctx, text); err != nil {
	//     logrus.WithError(err).Error("Failed to publish to message queue")
	// }

	return nil
}

func (s *CollectorService) completeTask(task *CollectionTask, collectedCount int32) {
	now := time.Now()
	task.EndTime = &now
	task.Status = pb.CollectionStatus_COLLECTION_COMPLETED
	task.CollectedCount = collectedCount
	task.Progress = 100

	s.updateTaskInDB(task)
	
	logrus.WithFields(logrus.Fields{
		"task_id":         task.ID,
		"collected_count": collectedCount,
		"duration":        now.Sub(*task.StartTime),
	}).Info("Collection task completed")
}

func (s *CollectorService) handleTaskError(task *CollectionTask, err error) {
	now := time.Now()
	task.EndTime = &now
	task.Status = pb.CollectionStatus_COLLECTION_FAILED
	task.ErrorMessage = err.Error()

	// 确保Config字段不为空，如果为空则从数据库获取原始配置
	if task.Config == nil {
		if dbTask, dbErr := s.repo.GetCollectionTaskByID(context.Background(), task.ID); dbErr == nil && dbTask.Config != "" {
			var config pb.CollectionConfig
			if json.Unmarshal([]byte(dbTask.Config), &config) == nil {
				task.Config = &config
			}
		}
	}

	s.updateTaskInDB(task)
	
	logrus.WithFields(logrus.Fields{
		"task_id": task.ID,
		"error":   err.Error(),
	}).Error("Collection task failed")
}

func (s *CollectorService) updateTaskInDB(task *CollectionTask) {
	logrus.WithField("task_id", task.ID).Info("updateTaskInDB called")
	
	// 先从数据库获取原始任务信息，避免覆盖其他字段
	dbTask, err := s.repo.GetCollectionTaskByID(context.Background(), task.ID)
	if err != nil {
		logrus.WithError(err).Error("Failed to get task from database for update")
		return
	}
	
	// 添加调试日志
	logrus.WithFields(logrus.Fields{
		"task_id": task.ID,
		"task_config": task.Config,
		"db_config": dbTask.Config,
	}).Info("updateTaskInDB debug info")
	
	// 只更新需要更新的字段
	dbTask.Status = task.Status.String()
	dbTask.CollectedCount = int(task.CollectedCount)
	dbTask.Progress = int(task.Progress)
	dbTask.ErrorMessage = task.ErrorMessage
	
	// 序列化配置 - 只有当task.Config不为nil时才更新config字段
	if task.Config != nil {
		configBytes, err := json.Marshal(task.Config)
		if err != nil {
			logrus.WithError(err).Error("Failed to marshal config")
		} else {
			dbTask.Config = string(configBytes)
			logrus.WithFields(logrus.Fields{
				"task_id": task.ID,
				"config_bytes": string(configBytes),
			}).Info("Config serialized successfully")
		}
	} else {
		logrus.WithFields(logrus.Fields{
			"task_id": task.ID,
			"original_config": dbTask.Config,
		}).Info("task.Config is nil, keeping original config")
	}
	
	if task.StartTime != nil {
		dbTask.StartTime = task.StartTime
	}
	if task.EndTime != nil {
		dbTask.EndTime = task.EndTime
	}

	logrus.WithFields(logrus.Fields{
		"task_id": dbTask.ID,
		"config": dbTask.Config,
	}).Info("About to update task in DB")
	
	if err := s.repo.UpdateCollectionTask(context.Background(), dbTask); err != nil {
		logrus.WithError(err).Error("Failed to update task in database")
	}
}

func parseCollectionStatus(status string) pb.CollectionStatus {
	switch status {
	case "pending":
		return pb.CollectionStatus_COLLECTION_PENDING
	case "running":
		return pb.CollectionStatus_COLLECTION_RUNNING
	case "completed":
		return pb.CollectionStatus_COLLECTION_COMPLETED
	case "failed":
		return pb.CollectionStatus_COLLECTION_FAILED
	default:
		return pb.CollectionStatus_COLLECTION_PENDING
	}
}