package handler

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/service"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

// HTTPHandler HTTP处理器
type HTTPHandler struct {
	collectorService *service.CollectorService
	logger           *logrus.Logger
}

// NewHTTPHandler 创建HTTP处理器
func NewHTTPHandler(collectorService *service.CollectorService) *HTTPHandler {
	logger := logrus.New()
	logger.SetLevel(logrus.InfoLevel)

	return &HTTPHandler{
		collectorService: collectorService,
		logger:           logger,
	}
}

// CollectRequest 采集请求结构
type CollectRequest struct {
	Source *CollectionSource `json:"source" binding:"required"`
	Config *CollectionConfig `json:"config"`
}

// CollectionSource 采集源配置
type CollectionSource struct {
	Type     string `json:"type" binding:"required,oneof=web api file"`
	URL      string `json:"url"`
	FilePath string `json:"file_path"`
}

// CollectionConfig 采集配置
type CollectionConfig struct {
	MaxTexts    int32             `json:"max_texts"`
	Timeout     int32             `json:"timeout"`
	Concurrent  int32             `json:"concurrent"`
	Filters     map[string]string `json:"filters"`
	Selectors   map[string]string `json:"selectors"`
	Headers     map[string]string `json:"headers"`
	Pagination  *PaginationConfig `json:"pagination"`
	RateLimit   *RateLimitConfig  `json:"rate_limit"`
	FileOptions *FileOptions      `json:"file_options"`
}

// PaginationConfig 分页配置
type PaginationConfig struct {
	Enabled   bool   `json:"enabled"`
	PageParam string `json:"page_param"`
	SizeParam string `json:"size_param"`
	MaxPages  int32  `json:"max_pages"`
}

// RateLimitConfig 速率限制配置
type RateLimitConfig struct {
	RequestsPerSecond float64 `json:"requests_per_second"`
	BurstSize         int     `json:"burst_size"`
}

// FileOptions 文件选项
type FileOptions struct {
	Encoding    string `json:"encoding"`
	Delimiter   string `json:"delimiter"`
	TextColumn  string `json:"text_column"`
	LabelColumn string `json:"label_column"`
}

// CollectResponse 采集响应结构
type CollectResponse struct {
	TaskID  string `json:"task_id"`
	Message string `json:"message"`
}

// TaskStatusResponse 任务状态响应结构
type TaskStatusResponse struct {
	TaskID         string `json:"task_id"`
	Status         string `json:"status"`
	Progress       int    `json:"progress"`
	CollectedCount int    `json:"collected_count"`
	TotalCount     int    `json:"total_count"`
	StartTime      string `json:"start_time,omitempty"`
	EndTime        string `json:"end_time,omitempty"`
	ErrorMessage   string `json:"error_message,omitempty"`
}

// TaskListResponse 任务列表响应结构
type TaskListResponse struct {
	Tasks      []*TaskStatusResponse `json:"tasks"`
	Total      int64                 `json:"total"`
	Page       int                   `json:"page"`
	PageSize   int                   `json:"page_size"`
	TotalPages int                   `json:"total_pages"`
}

// ErrorResponse 错误响应结构
type ErrorResponse struct {
	Error   string `json:"error"`
	Code    int    `json:"code"`
	Message string `json:"message"`
}

// CollectText 文本采集接口
func (h *HTTPHandler) CollectText(c *gin.Context) {
	var req CollectRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("Invalid request body")
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Error:   "invalid_request",
			Code:    400,
			Message: err.Error(),
		})
		return
	}

	// 转换源类型
	var sourceType pb.SourceType
	switch req.Source.Type {
	case "api":
		sourceType = pb.SourceType_API
	case "web":
		sourceType = pb.SourceType_WEB_CRAWLER
	case "file":
		sourceType = pb.SourceType_LOCAL_FILE
	default:
		sourceType = pb.SourceType_API
	}

	// 转换为protobuf格式
	pbSource := &pb.CollectionSource{
		Type:     sourceType,
		Url:      req.Source.URL,
		FilePath: req.Source.FilePath,
	}

	pbConfig := &pb.CollectionConfig{}
	if req.Config != nil {
		pbConfig.MaxCount = req.Config.MaxTexts
		pbConfig.ConcurrentLimit = req.Config.Concurrent
		pbConfig.RateLimit = req.Config.Timeout
		if req.Config.Filters != nil {
			for _, filter := range req.Config.Filters {
				pbConfig.Filters = append(pbConfig.Filters, filter)
			}
		}
	}

	// 调用服务
	pbReq := &pb.CollectRequest{
		Source: pbSource,
		Config: pbConfig,
	}

	resp, err := h.collectorService.CollectText(c.Request.Context(), pbReq)
	if err != nil {
		h.logger.WithError(err).Error("Failed to collect text")
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Error:   "collection_failed",
			Code:    500,
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, CollectResponse{
		TaskID:  resp.TaskId,
		Message: resp.Message,
	})
}

// GetTaskStatus 获取任务状态
func (h *HTTPHandler) GetTaskStatus(c *gin.Context) {
	taskID := c.Param("id")
	if taskID == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Error:   "invalid_task_id",
			Code:    400,
			Message: "Task ID is required",
		})
		return
	}

	req := &pb.StatusRequest{
		TaskId: taskID,
	}

	resp, err := h.collectorService.GetCollectionStatus(c.Request.Context(), req)
	if err != nil {
		h.logger.WithError(err).Error("Failed to get task status")
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Error:   "status_query_failed",
			Code:    500,
			Message: err.Error(),
		})
		return
	}

	response := &TaskStatusResponse{
		TaskID:   resp.TaskId,
		Status:   resp.Status.String(),
		Progress: int(resp.Progress),
	}

	if resp.StartTime != 0 {
		response.StartTime = time.Unix(resp.StartTime, 0).Format(time.RFC3339)
	}
	if resp.EndTime != 0 {
		response.EndTime = time.Unix(resp.EndTime, 0).Format(time.RFC3339)
	}

	c.JSON(http.StatusOK, response)
}

// ListTasks 获取任务列表
func (h *HTTPHandler) ListTasks(c *gin.Context) {
	// 获取查询参数
	pageStr := c.DefaultQuery("page", "1")
	pageSizeStr := c.DefaultQuery("page_size", "10")
	status := c.Query("status")

	page, err := strconv.Atoi(pageStr)
	if err != nil || page < 1 {
		page = 1
	}

	pageSize, err := strconv.Atoi(pageSizeStr)
	if err != nil || pageSize < 1 || pageSize > 100 {
		pageSize = 10
	}

	offset := (page - 1) * pageSize

	// 从数据库获取任务列表
	ctx := c.Request.Context()
	tasks, err := h.collectorService.GetRepository().ListCollectionTasks(ctx, status, pageSize, offset)
	if err != nil {
		h.logger.WithError(err).Error("Failed to list collection tasks")
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Error:   "internal_error",
			Code:    http.StatusInternalServerError,
			Message: "Failed to retrieve tasks",
		})
		return
	}

	// 获取总数
	total, err := h.collectorService.GetRepository().CountCollectionTasks(ctx, status)
	if err != nil {
		h.logger.WithError(err).Error("Failed to count collection tasks")
		total = 0
	}

	// 转换为响应格式
	taskResponses := make([]*TaskStatusResponse, len(tasks))
	for i, task := range tasks {
		taskResponses[i] = &TaskStatusResponse{
			TaskID:         task.ID,
			Status:         task.Status,
			Progress:       task.Progress,
			CollectedCount: task.CollectedCount,
			TotalCount:     task.TotalCount,
			StartTime:      task.StartTime.Format(time.RFC3339),
			EndTime:        task.EndTime.Format(time.RFC3339),
			ErrorMessage:   task.ErrorMessage,
		}
	}

	totalPages := int((total + int64(pageSize) - 1) / int64(pageSize))

	c.JSON(http.StatusOK, TaskListResponse{
		Tasks:      taskResponses,
		Total:      total,
		Page:       page,
		PageSize:   pageSize,
		TotalPages: totalPages,
	})
}

// HealthCheck 健康检查
func (h *HTTPHandler) HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":    "healthy",
		"timestamp": time.Now().Unix(),
		"service":   "data-collector",
		"version":   "1.0.0",
	})
}

// GetMetrics 获取指标
func (h *HTTPHandler) GetMetrics(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"total_tasks":     0,
		"running_tasks":   0,
		"completed_tasks": 0,
		"failed_tasks":    0,
		"total_texts":     0,
		"uptime":          time.Now().Unix(),
	})
}

// SetupRoutes 设置路由
func (h *HTTPHandler) SetupRoutes(r *gin.Engine) {
	// API路由组
	api := r.Group("/api/v1")
	{
		api.POST("/collect", h.CollectText)
		api.GET("/status/:taskId", h.GetTaskStatus)
		api.GET("/tasks", h.ListTasks)
	}

	// 健康检查和指标
	r.GET("/health", h.HealthCheck)
	r.GET("/metrics", h.GetMetrics)

	// 中间件
	r.Use(h.requestIDMiddleware())
	r.Use(h.loggingMiddleware())
	r.Use(h.corsMiddleware())
}

// requestIDMiddleware 请求ID中间件
func (h *HTTPHandler) requestIDMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		requestID := c.GetHeader("X-Request-ID")
		if requestID == "" {
			requestID = uuid.New().String()
		}
		c.Set("request_id", requestID)
		c.Header("X-Request-ID", requestID)
		c.Next()
	}
}

// loggingMiddleware 日志中间件
func (h *HTTPHandler) loggingMiddleware() gin.HandlerFunc {
	return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		return fmt.Sprintf("%s - [%s] \"%s %s %s %d %s \"%s\" %s\"\n",
			param.ClientIP,
			param.TimeStamp.Format(time.RFC1123),
			param.Method,
			param.Path,
			param.Request.Proto,
			param.StatusCode,
			param.Latency,
			param.Request.UserAgent(),
			param.ErrorMessage,
		)
	})
}

// corsMiddleware CORS中间件
func (h *HTTPHandler) corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, X-Request-ID")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}