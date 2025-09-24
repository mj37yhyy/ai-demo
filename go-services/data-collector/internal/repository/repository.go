package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/model"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// Repository 数据仓库接口
type Repository interface {
	// RawText 相关操作
	SaveRawText(ctx context.Context, text *model.RawText) error
	GetRawTextByID(ctx context.Context, id string) (*model.RawText, error)
	ListRawTexts(ctx context.Context, source string, limit, offset int) ([]*model.RawText, error)
	CountRawTexts(ctx context.Context, source string) (int64, error)

	// CollectionTask 相关操作
	CreateCollectionTask(ctx context.Context, task *model.CollectionTask) error
	UpdateCollectionTask(ctx context.Context, task *model.CollectionTask) error
	GetCollectionTaskByID(ctx context.Context, id string) (*model.CollectionTask, error)
	ListCollectionTasks(ctx context.Context, status string, limit, offset int) ([]*model.CollectionTask, error)
	CountCollectionTasks(ctx context.Context, status string) (int64, error)
	UpdateTaskProgress(ctx context.Context, taskID string, progress int, collectedCount int) error
	UpdateTaskStatus(ctx context.Context, taskID string, status string, errorMessage string) error

	// ProcessedText 相关操作
	SaveProcessedText(ctx context.Context, text *model.ProcessedText) error
	GetProcessedTextByID(ctx context.Context, id string) (*model.ProcessedText, error)
	ListProcessedTexts(ctx context.Context, source string, limit, offset int) ([]*model.ProcessedText, error)

	// Model 相关操作
	SaveModel(ctx context.Context, model *model.Model) error
	GetModelByName(ctx context.Context, name string) (*model.Model, error)
	ListModels(ctx context.Context, modelType string) ([]*model.Model, error)
	UpdateModelStatus(ctx context.Context, modelID string, status string) error

	// AuditRecord 相关操作
	SaveAuditRecord(ctx context.Context, record *model.AuditRecord) error
	GetAuditRecordByRequestID(ctx context.Context, requestID string) (*model.AuditRecord, error)
	ListAuditRecords(ctx context.Context, startTime, endTime time.Time, limit, offset int) ([]*model.AuditRecord, error)

	// TrainingTask 相关操作
	CreateTrainingTask(ctx context.Context, task *model.TrainingTask) error
	UpdateTrainingTask(ctx context.Context, task *model.TrainingTask) error
	GetTrainingTaskByID(ctx context.Context, id string) (*model.TrainingTask, error)
	ListTrainingTasks(ctx context.Context, status string, limit, offset int) ([]*model.TrainingTask, error)

	// StopWord 相关操作
	GetStopWords(ctx context.Context, language string) ([]*model.StopWord, error)
	AddStopWord(ctx context.Context, word *model.StopWord) error

	// Vocabulary 相关操作
	GetVocabulary(ctx context.Context, language string, limit, offset int) ([]*model.Vocabulary, error)
	UpdateWordFrequency(ctx context.Context, word string, language string) error

	// SystemConfig 相关操作
	GetConfig(ctx context.Context, key string) (*model.SystemConfig, error)
	SetConfig(ctx context.Context, key, value, description string) error

	// 健康检查
	HealthCheck(ctx context.Context) error
}

// MySQLRepository MySQL数据库仓库实现
type MySQLRepository struct {
	db *gorm.DB
}

// NewMySQLRepository 创建MySQL仓库实例
func NewMySQLRepository(dsn string) (*MySQLRepository, error) {
	db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to connect to database: %w", err)
	}

	// 自动迁移数据库表
	err = db.AutoMigrate(
		&model.RawText{},
		&model.CollectionTask{},
		&model.ProcessedText{},
		&model.Model{},
		&model.AuditRecord{},
		&model.TrainingTask{},
		&model.StopWord{},
		&model.Vocabulary{},
		&model.SystemConfig{},
	)
	if err != nil {
		return nil, fmt.Errorf("failed to migrate database: %w", err)
	}

	return &MySQLRepository{db: db}, nil
}

// RawText 相关操作实现
func (r *MySQLRepository) SaveRawText(ctx context.Context, text *model.RawText) error {
	return r.db.WithContext(ctx).Create(text).Error
}

func (r *MySQLRepository) GetRawTextByID(ctx context.Context, id string) (*model.RawText, error) {
	var text model.RawText
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&text).Error
	if err != nil {
		return nil, err
	}
	return &text, nil
}

func (r *MySQLRepository) ListRawTexts(ctx context.Context, source string, limit, offset int) ([]*model.RawText, error) {
	var texts []*model.RawText
	query := r.db.WithContext(ctx)
	if source != "" {
		query = query.Where("source = ?", source)
	}
	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&texts).Error
	return texts, err
}

func (r *MySQLRepository) CountRawTexts(ctx context.Context, source string) (int64, error) {
	var count int64
	query := r.db.WithContext(ctx).Model(&model.RawText{})
	if source != "" {
		query = query.Where("source = ?", source)
	}
	err := query.Count(&count).Error
	return count, err
}

// CollectionTask 相关操作实现
func (r *MySQLRepository) CreateCollectionTask(ctx context.Context, task *model.CollectionTask) error {
	return r.db.WithContext(ctx).Create(task).Error
}

func (r *MySQLRepository) UpdateCollectionTask(ctx context.Context, task *model.CollectionTask) error {
	return r.db.WithContext(ctx).Save(task).Error
}

func (r *MySQLRepository) GetCollectionTaskByID(ctx context.Context, id string) (*model.CollectionTask, error) {
	var task model.CollectionTask
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&task).Error
	if err != nil {
		return nil, err
	}
	return &task, nil
}

func (r *MySQLRepository) ListCollectionTasks(ctx context.Context, status string, limit, offset int) ([]*model.CollectionTask, error) {
	var tasks []*model.CollectionTask
	query := r.db.WithContext(ctx)
	if status != "" {
		query = query.Where("status = ?", status)
	}
	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&tasks).Error
	return tasks, err
}

func (r *MySQLRepository) CountCollectionTasks(ctx context.Context, status string) (int64, error) {
	var count int64
	query := r.db.WithContext(ctx).Model(&model.CollectionTask{})
	if status != "" {
		query = query.Where("status = ?", status)
	}
	err := query.Count(&count).Error
	return count, err
}

func (r *MySQLRepository) UpdateTaskProgress(ctx context.Context, taskID string, progress int, collectedCount int) error {
	return r.db.WithContext(ctx).Model(&model.CollectionTask{}).
		Where("id = ?", taskID).
		Updates(map[string]interface{}{
			"progress":        progress,
			"collected_count": collectedCount,
		}).Error
}

func (r *MySQLRepository) UpdateTaskStatus(ctx context.Context, taskID string, status string, errorMessage string) error {
	updates := map[string]interface{}{
		"status": status,
	}
	if errorMessage != "" {
		updates["error_message"] = errorMessage
	}
	if status == "completed" || status == "failed" {
		updates["end_time"] = time.Now()
	}
	if status == "running" {
		updates["start_time"] = time.Now()
	}
	return r.db.WithContext(ctx).Model(&model.CollectionTask{}).
		Where("id = ?", taskID).
		Updates(updates).Error
}

// ProcessedText 相关操作实现
func (r *MySQLRepository) SaveProcessedText(ctx context.Context, text *model.ProcessedText) error {
	return r.db.WithContext(ctx).Create(text).Error
}

func (r *MySQLRepository) GetProcessedTextByID(ctx context.Context, id string) (*model.ProcessedText, error) {
	var text model.ProcessedText
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&text).Error
	if err != nil {
		return nil, err
	}
	return &text, nil
}

func (r *MySQLRepository) ListProcessedTexts(ctx context.Context, source string, limit, offset int) ([]*model.ProcessedText, error) {
	var texts []*model.ProcessedText
	query := r.db.WithContext(ctx)
	if source != "" {
		query = query.Where("source = ?", source)
	}
	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&texts).Error
	return texts, err
}

// Model 相关操作实现
func (r *MySQLRepository) SaveModel(ctx context.Context, model *model.Model) error {
	return r.db.WithContext(ctx).Create(model).Error
}

func (r *MySQLRepository) GetModelByName(ctx context.Context, name string) (*model.Model, error) {
	var model model.Model
	err := r.db.WithContext(ctx).Where("name = ?", name).First(&model).Error
	if err != nil {
		return nil, err
	}
	return &model, nil
}

func (r *MySQLRepository) ListModels(ctx context.Context, modelType string) ([]*model.Model, error) {
	var models []*model.Model
	query := r.db.WithContext(ctx)
	if modelType != "" {
		query = query.Where("type = ?", modelType)
	}
	err := query.Order("created_at DESC").Find(&models).Error
	return models, err
}

func (r *MySQLRepository) UpdateModelStatus(ctx context.Context, modelID string, status string) error {
	return r.db.WithContext(ctx).Model(&model.Model{}).
		Where("id = ?", modelID).
		Update("status", status).Error
}

// AuditRecord 相关操作实现
func (r *MySQLRepository) SaveAuditRecord(ctx context.Context, record *model.AuditRecord) error {
	return r.db.WithContext(ctx).Create(record).Error
}

func (r *MySQLRepository) GetAuditRecordByRequestID(ctx context.Context, requestID string) (*model.AuditRecord, error) {
	var record model.AuditRecord
	err := r.db.WithContext(ctx).Where("request_id = ?", requestID).First(&record).Error
	if err != nil {
		return nil, err
	}
	return &record, nil
}

func (r *MySQLRepository) ListAuditRecords(ctx context.Context, startTime, endTime time.Time, limit, offset int) ([]*model.AuditRecord, error) {
	var records []*model.AuditRecord
	query := r.db.WithContext(ctx)
	if !startTime.IsZero() {
		query = query.Where("created_at >= ?", startTime)
	}
	if !endTime.IsZero() {
		query = query.Where("created_at <= ?", endTime)
	}
	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&records).Error
	return records, err
}

// TrainingTask 相关操作实现
func (r *MySQLRepository) CreateTrainingTask(ctx context.Context, task *model.TrainingTask) error {
	return r.db.WithContext(ctx).Create(task).Error
}

func (r *MySQLRepository) UpdateTrainingTask(ctx context.Context, task *model.TrainingTask) error {
	return r.db.WithContext(ctx).Save(task).Error
}

func (r *MySQLRepository) GetTrainingTaskByID(ctx context.Context, id string) (*model.TrainingTask, error) {
	var task model.TrainingTask
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&task).Error
	if err != nil {
		return nil, err
	}
	return &task, nil
}

func (r *MySQLRepository) ListTrainingTasks(ctx context.Context, status string, limit, offset int) ([]*model.TrainingTask, error) {
	var tasks []*model.TrainingTask
	query := r.db.WithContext(ctx)
	if status != "" {
		query = query.Where("status = ?", status)
	}
	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&tasks).Error
	return tasks, err
}

// StopWord 相关操作实现
func (r *MySQLRepository) GetStopWords(ctx context.Context, language string) ([]*model.StopWord, error) {
	var words []*model.StopWord
	query := r.db.WithContext(ctx)
	if language != "" {
		query = query.Where("language = ?", language)
	}
	err := query.Find(&words).Error
	return words, err
}

func (r *MySQLRepository) AddStopWord(ctx context.Context, word *model.StopWord) error {
	return r.db.WithContext(ctx).Create(word).Error
}

// Vocabulary 相关操作实现
func (r *MySQLRepository) GetVocabulary(ctx context.Context, language string, limit, offset int) ([]*model.Vocabulary, error) {
	var vocab []*model.Vocabulary
	query := r.db.WithContext(ctx)
	if language != "" {
		query = query.Where("language = ?", language)
	}
	err := query.Order("frequency DESC").Limit(limit).Offset(offset).Find(&vocab).Error
	return vocab, err
}

func (r *MySQLRepository) UpdateWordFrequency(ctx context.Context, word string, language string) error {
	var vocab model.Vocabulary
	err := r.db.WithContext(ctx).Where("word = ? AND language = ?", word, language).First(&vocab).Error
	if err == gorm.ErrRecordNotFound {
		// 创建新词汇
		vocab = model.Vocabulary{
			Word:      word,
			Frequency: 1,
			Language:  language,
		}
		return r.db.WithContext(ctx).Create(&vocab).Error
	} else if err != nil {
		return err
	}

	// 更新频率
	return r.db.WithContext(ctx).Model(&vocab).Update("frequency", vocab.Frequency+1).Error
}

// SystemConfig 相关操作实现
func (r *MySQLRepository) GetConfig(ctx context.Context, key string) (*model.SystemConfig, error) {
	var config model.SystemConfig
	err := r.db.WithContext(ctx).Where("config_key = ?", key).First(&config).Error
	if err != nil {
		return nil, err
	}
	return &config, nil
}

func (r *MySQLRepository) SetConfig(ctx context.Context, key, value, description string) error {
	config := model.SystemConfig{
		ConfigKey:   key,
		ConfigValue: value,
		Description: description,
	}
	return r.db.WithContext(ctx).Save(&config).Error
}

// HealthCheck 健康检查
func (r *MySQLRepository) HealthCheck(ctx context.Context) error {
	sqlDB, err := r.db.DB()
	if err != nil {
		return err
	}
	return sqlDB.PingContext(ctx)
}