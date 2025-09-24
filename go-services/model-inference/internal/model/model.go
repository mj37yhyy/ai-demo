package model

import (
	"time"

	"gorm.io/gorm"
)

// ModelStatus 模型状态枚举
type ModelStatus string

const (
	ModelStatusUnloaded ModelStatus = "unloaded"
	ModelStatusLoading  ModelStatus = "loading"
	ModelStatusLoaded   ModelStatus = "loaded"
	ModelStatusError    ModelStatus = "error"
)

// ModelType 模型类型枚举
type ModelType string

const (
	ModelTypeClassification ModelType = "classification"
	ModelTypeRegression     ModelType = "regression"
	ModelTypeClustering     ModelType = "clustering"
	ModelTypeTextAnalysis   ModelType = "text_analysis"
)

// InferenceStatus 推理状态枚举
type InferenceStatus string

const (
	InferenceStatusPending   InferenceStatus = "pending"
	InferenceStatusRunning   InferenceStatus = "running"
	InferenceStatusCompleted InferenceStatus = "completed"
	InferenceStatusFailed    InferenceStatus = "failed"
)

// Model 模型信息
type Model struct {
	ID          uint           `json:"id" gorm:"primaryKey"`
	Name        string         `json:"name" gorm:"uniqueIndex;not null"`
	Type        ModelType      `json:"type" gorm:"not null"`
	Version     string         `json:"version" gorm:"not null"`
	Description string         `json:"description"`
	FilePath    string         `json:"file_path" gorm:"not null"`
	FileSize    int64          `json:"file_size"`
	Status      ModelStatus    `json:"status" gorm:"default:unloaded"`
	Metadata    string         `json:"metadata" gorm:"type:jsonb"`
	LoadedAt    *time.Time     `json:"loaded_at"`
	CreatedAt   time.Time      `json:"created_at"`
	UpdatedAt   time.Time      `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// InferenceRequest 推理请求
type InferenceRequest struct {
	ID          uint            `json:"id" gorm:"primaryKey"`
	RequestID   string          `json:"request_id" gorm:"uniqueIndex;not null"`
	ModelName   string          `json:"model_name" gorm:"not null"`
	InputData   string          `json:"input_data" gorm:"type:jsonb;not null"`
	Status      InferenceStatus `json:"status" gorm:"default:pending"`
	Result      string          `json:"result" gorm:"type:jsonb"`
	Error       string          `json:"error"`
	StartTime   time.Time       `json:"start_time"`
	EndTime     *time.Time      `json:"end_time"`
	Duration    int64           `json:"duration"` // 毫秒
	CreatedAt   time.Time       `json:"created_at"`
	UpdatedAt   time.Time       `json:"updated_at"`
	DeletedAt   gorm.DeletedAt  `json:"-" gorm:"index"`
}

// ModelStatistics 模型统计信息
type ModelStatistics struct {
	TotalModels   int64 `json:"total_models"`
	LoadedModels  int64 `json:"loaded_models"`
	UnloadedModels int64 `json:"unloaded_models"`
	ErrorModels   int64 `json:"error_models"`
}

// InferenceStatistics 推理统计信息
type InferenceStatistics struct {
	TotalRequests     int64   `json:"total_requests"`
	CompletedRequests int64   `json:"completed_requests"`
	FailedRequests    int64   `json:"failed_requests"`
	AverageLatency    float64 `json:"average_latency"` // 毫秒
	RequestsPerSecond float64 `json:"requests_per_second"`
}

// PredictRequest 预测请求
type PredictRequest struct {
	ModelName string                 `json:"model_name" binding:"required"`
	Data      map[string]interface{} `json:"data" binding:"required"`
	Options   map[string]interface{} `json:"options,omitempty"`
}

// BatchPredictRequest 批量预测请求
type BatchPredictRequest struct {
	ModelName string                   `json:"model_name" binding:"required"`
	Data      []map[string]interface{} `json:"data" binding:"required"`
	Options   map[string]interface{}   `json:"options,omitempty"`
}

// PredictResponse 预测响应
type PredictResponse struct {
	RequestID   string                 `json:"request_id"`
	ModelName   string                 `json:"model_name"`
	Prediction  interface{}            `json:"prediction"`
	Confidence  float64                `json:"confidence,omitempty"`
	Probability map[string]float64     `json:"probability,omitempty"`
	Metadata    map[string]interface{} `json:"metadata,omitempty"`
	Duration    int64                  `json:"duration"` // 毫秒
}

// BatchPredictResponse 批量预测响应
type BatchPredictResponse struct {
	RequestID   string            `json:"request_id"`
	ModelName   string            `json:"model_name"`
	Predictions []PredictResponse `json:"predictions"`
	Duration    int64             `json:"duration"` // 毫秒
}

// TextClassifyRequest 文本分类请求
type TextClassifyRequest struct {
	ModelName string `json:"model_name" binding:"required"`
	Text      string `json:"text" binding:"required"`
}

// SentimentAnalysisRequest 情感分析请求
type SentimentAnalysisRequest struct {
	ModelName string `json:"model_name" binding:"required"`
	Text      string `json:"text" binding:"required"`
}

// FeatureExtractionRequest 特征提取请求
type FeatureExtractionRequest struct {
	ModelName string `json:"model_name" binding:"required"`
	Text      string `json:"text" binding:"required"`
}

// AnomalyDetectionRequest 异常检测请求
type AnomalyDetectionRequest struct {
	ModelName string                 `json:"model_name" binding:"required"`
	Data      map[string]interface{} `json:"data" binding:"required"`
}

// TextAnalysisResponse 文本分析响应
type TextAnalysisResponse struct {
	RequestID  string                 `json:"request_id"`
	ModelName  string                 `json:"model_name"`
	Text       string                 `json:"text,omitempty"`
	Result     interface{}            `json:"result"`
	Confidence float64                `json:"confidence,omitempty"`
	Features   map[string]interface{} `json:"features,omitempty"`
	Duration   int64                  `json:"duration"` // 毫秒
}

// ModelLoadRequest 模型加载请求
type ModelLoadRequest struct {
	Force bool `json:"force,omitempty"`
}

// ModelStatusResponse 模型状态响应
type ModelStatusResponse struct {
	Name      string      `json:"name"`
	Status    ModelStatus `json:"status"`
	LoadedAt  *time.Time  `json:"loaded_at"`
	Error     string      `json:"error,omitempty"`
	Metadata  interface{} `json:"metadata,omitempty"`
}

// HealthResponse 健康检查响应
type HealthResponse struct {
	Status    string                 `json:"status"`
	Timestamp time.Time              `json:"timestamp"`
	Services  map[string]interface{} `json:"services"`
}

// ErrorResponse 错误响应
type ErrorResponse struct {
	Error     string                 `json:"error"`
	Message   string                 `json:"message"`
	Code      int                    `json:"code"`
	Details   map[string]interface{} `json:"details,omitempty"`
	Timestamp time.Time              `json:"timestamp"`
}

// TableName 指定表名
func (Model) TableName() string {
	return "models"
}

// TableName 指定表名
func (InferenceRequest) TableName() string {
	return "inference_requests"
}