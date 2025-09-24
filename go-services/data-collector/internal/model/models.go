package model

import (
	"time"
)

// RawText 原始文本数据模型
type RawText struct {
	ID        string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	Content   string    `gorm:"type:text;not null" json:"content"`
	Source    string    `gorm:"type:varchar(100);not null;index" json:"source"`
	Timestamp int64     `gorm:"not null;index" json:"timestamp"`
	Metadata  string    `gorm:"type:json" json:"metadata"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
}

func (RawText) TableName() string {
	return "raw_texts"
}

// CollectionTask 采集任务模型
type CollectionTask struct {
	ID             string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	SourceType     string    `gorm:"type:varchar(20);not null;index" json:"source_type"`
	SourceURL      string    `gorm:"type:varchar(1000)" json:"source_url"`
	SourceFilePath string    `gorm:"type:varchar(500)" json:"source_file_path"`
	Config         string    `gorm:"type:json;not null" json:"config"`
	Status         string    `gorm:"type:varchar(20);default:'pending';index" json:"status"`
	CollectedCount int       `gorm:"default:0" json:"collected_count"`
	TotalCount     int       `gorm:"default:0" json:"total_count"`
	Progress       int       `gorm:"default:0" json:"progress"`
	StartTime      time.Time `gorm:"type:timestamp null" json:"start_time"`
	EndTime        time.Time `gorm:"type:timestamp null" json:"end_time"`
	ErrorMessage   string    `gorm:"type:text" json:"error_message"`
	CreatedAt      time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt      time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (CollectionTask) TableName() string {
	return "collection_tasks"
}

// ProcessedText 预处理文本数据模型
type ProcessedText struct {
	ID                 string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	RawTextID          string    `gorm:"type:varchar(36);index" json:"raw_text_id"`
	Content            string    `gorm:"type:text;not null" json:"content"`
	Tokens             string    `gorm:"type:json" json:"tokens"`
	Features           string    `gorm:"type:json" json:"features"`
	Label              *int      `gorm:"type:tinyint;index" json:"label"`
	Source             string    `gorm:"type:varchar(100);not null;index" json:"source"`
	Timestamp          int64     `gorm:"not null;index" json:"timestamp"`
	ProcessingMetadata string    `gorm:"type:json" json:"processing_metadata"`
	CreatedAt          time.Time `gorm:"autoCreateTime" json:"created_at"`
}

func (ProcessedText) TableName() string {
	return "processed_texts"
}

// Model 模型信息
type Model struct {
	ID        string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	Name      string    `gorm:"type:varchar(100);not null;uniqueIndex" json:"name"`
	Type      string    `gorm:"type:varchar(50);not null;index" json:"type"`
	Version   string    `gorm:"type:varchar(20);not null" json:"version"`
	FilePath  string    `gorm:"type:varchar(500);not null" json:"file_path"`
	Config    string    `gorm:"type:json" json:"config"`
	Metrics   string    `gorm:"type:json" json:"metrics"`
	Status    string    `gorm:"type:varchar(20);default:'training';index" json:"status"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (Model) TableName() string {
	return "models"
}

// AuditRecord 审核记录
type AuditRecord struct {
	ID               string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	RequestID        string    `gorm:"type:varchar(36);not null;index" json:"request_id"`
	TextContent      string    `gorm:"type:text;not null" json:"text_content"`
	IsViolation      bool      `gorm:"not null;index" json:"is_violation"`
	Confidence       float64   `gorm:"type:decimal(5,4);not null" json:"confidence"`
	ViolationType    string    `gorm:"type:varchar(50);index" json:"violation_type"`
	ModelResults     string    `gorm:"type:json" json:"model_results"`
	Features         string    `gorm:"type:json" json:"features"`
	Explanation      string    `gorm:"type:text" json:"explanation"`
	ProcessingTimeMs int       `gorm:"not null" json:"processing_time_ms"`
	CreatedAt        time.Time `gorm:"autoCreateTime;index" json:"created_at"`
}

func (AuditRecord) TableName() string {
	return "audit_records"
}

// TrainingTask 训练任务
type TrainingTask struct {
	ID           string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	ModelID      string    `gorm:"type:varchar(36);not null;index" json:"model_id"`
	ModelType    string    `gorm:"type:varchar(50);not null" json:"model_type"`
	DatasetPath  string    `gorm:"type:varchar(500);not null" json:"dataset_path"`
	Config       string    `gorm:"type:json;not null" json:"config"`
	Status       string    `gorm:"type:varchar(20);default:'pending';index" json:"status"`
	Metrics      string    `gorm:"type:json" json:"metrics"`
	StartTime    time.Time `gorm:"type:timestamp null" json:"start_time"`
	EndTime      time.Time `gorm:"type:timestamp null" json:"end_time"`
	ErrorMessage string    `gorm:"type:text" json:"error_message"`
	CreatedAt    time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt    time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (TrainingTask) TableName() string {
	return "training_tasks"
}

// StopWord 停用词
type StopWord struct {
	ID        int       `gorm:"primaryKey;autoIncrement" json:"id"`
	Word      string    `gorm:"type:varchar(50);not null;uniqueIndex" json:"word"`
	Language  string    `gorm:"type:varchar(10);default:'zh';index" json:"language"`
	Category  string    `gorm:"type:varchar(50);default:'general'" json:"category"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
}

func (StopWord) TableName() string {
	return "stop_words"
}

// Vocabulary 词汇表
type Vocabulary struct {
	ID        int       `gorm:"primaryKey;autoIncrement" json:"id"`
	Word      string    `gorm:"type:varchar(100);not null" json:"word"`
	Frequency int       `gorm:"default:1;index" json:"frequency"`
	IDFScore  float64   `gorm:"type:decimal(10,6);index" json:"idf_score"`
	Language  string    `gorm:"type:varchar(10);default:'zh'" json:"language"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (Vocabulary) TableName() string {
	return "vocabulary"
}

// SystemConfig 系统配置
type SystemConfig struct {
	ID          int       `gorm:"primaryKey;autoIncrement" json:"id"`
	ConfigKey   string    `gorm:"type:varchar(100);not null;uniqueIndex" json:"config_key"`
	ConfigValue string    `gorm:"type:text;not null" json:"config_value"`
	Description string    `gorm:"type:varchar(500)" json:"description"`
	CreatedAt   time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt   time.Time `gorm:"autoUpdateTime" json:"updated_at"`
}

func (SystemConfig) TableName() string {
	return "system_configs"
}