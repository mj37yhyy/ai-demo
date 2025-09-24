package handler

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/textaudit/model-inference/internal/model"
	"github.com/textaudit/model-inference/internal/service"
)

// InferenceHandler 推理处理器
type InferenceHandler struct {
	inferenceService service.InferenceService
	logger           *logrus.Logger
}

// NewInferenceHandler 创建推理处理器
func NewInferenceHandler(inferenceService service.InferenceService, logger *logrus.Logger) *InferenceHandler {
	return &InferenceHandler{
		inferenceService: inferenceService,
		logger:           logger,
	}
}

// Predict 单次预测
// @Summary 单次预测
// @Description 对单个输入进行预测
// @Tags 推理服务
// @Accept json
// @Produce json
// @Param request body model.PredictRequest true "预测请求"
// @Success 200 {object} model.PredictResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/inference/predict [post]
func (h *InferenceHandler) Predict(c *gin.Context) {
	var req model.PredictRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行预测
	response, err := h.inferenceService.Predict(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("预测失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "预测失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// BatchPredict 批量预测
// @Summary 批量预测
// @Description 对多个输入进行批量预测
// @Tags 推理服务
// @Accept json
// @Produce json
// @Param request body model.BatchPredictRequest true "批量预测请求"
// @Success 200 {object} model.BatchPredictResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/inference/batch-predict [post]
func (h *InferenceHandler) BatchPredict(c *gin.Context) {
	var req model.BatchPredictRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行批量预测
	response, err := h.inferenceService.BatchPredict(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("批量预测失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "批量预测失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// TextClassify 文本分类
// @Summary 文本分类
// @Description 对文本进行分类
// @Tags 文本分析
// @Accept json
// @Produce json
// @Param request body model.TextClassifyRequest true "文本分类请求"
// @Success 200 {object} model.TextAnalysisResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/text/classify [post]
func (h *InferenceHandler) TextClassify(c *gin.Context) {
	var req model.TextClassifyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行文本分类
	response, err := h.inferenceService.TextClassify(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("文本分类失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "文本分类失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// SentimentAnalysis 情感分析
// @Summary 情感分析
// @Description 对文本进行情感分析
// @Tags 文本分析
// @Accept json
// @Produce json
// @Param request body model.SentimentAnalysisRequest true "情感分析请求"
// @Success 200 {object} model.TextAnalysisResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/text/sentiment [post]
func (h *InferenceHandler) SentimentAnalysis(c *gin.Context) {
	var req model.SentimentAnalysisRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行情感分析
	response, err := h.inferenceService.SentimentAnalysis(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("情感分析失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "情感分析失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// FeatureExtraction 特征提取
// @Summary 特征提取
// @Description 从文本中提取特征
// @Tags 文本分析
// @Accept json
// @Produce json
// @Param request body model.FeatureExtractionRequest true "特征提取请求"
// @Success 200 {object} model.TextAnalysisResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/text/features [post]
func (h *InferenceHandler) FeatureExtraction(c *gin.Context) {
	var req model.FeatureExtractionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行特征提取
	response, err := h.inferenceService.FeatureExtraction(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("特征提取失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "特征提取失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// AnomalyDetection 异常检测
// @Summary 异常检测
// @Description 对文本进行异常检测
// @Tags 文本分析
// @Accept json
// @Produce json
// @Param request body model.AnomalyDetectionRequest true "异常检测请求"
// @Success 200 {object} model.TextAnalysisResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/text/anomaly [post]
func (h *InferenceHandler) AnomalyDetection(c *gin.Context) {
	var req model.AnomalyDetectionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("绑定请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: err.Error(),
		})
		return
	}

	// 执行异常检测
	response, err := h.inferenceService.AnomalyDetection(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", req.ModelName).Error("异常检测失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "异常检测失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// GetInferenceHistory 获取推理历史
// @Summary 获取推理历史
// @Description 获取推理请求的历史记录
// @Tags 推理服务
// @Accept json
// @Produce json
// @Param page query int false "页码" default(1)
// @Param limit query int false "每页数量" default(10)
// @Param model_name query string false "模型名称"
// @Param status query string false "状态"
// @Success 200 {array} model.InferenceRequest
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/inference/history [get]
func (h *InferenceHandler) GetInferenceHistory(c *gin.Context) {
	// 解析查询参数
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))
	modelName := c.Query("model_name")
	status := c.Query("status")

	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 10
	}

	// 获取推理历史
	history, err := h.inferenceService.GetInferenceHistory(c.Request.Context(), page, limit, modelName, status)
	if err != nil {
		h.logger.WithError(err).Error("获取推理历史失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "获取推理历史失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, history)
}

// GetInferenceResult 获取推理结果
// @Summary 获取推理结果
// @Description 根据请求ID获取推理结果
// @Tags 推理服务
// @Accept json
// @Produce json
// @Param request_id path string true "请求ID"
// @Success 200 {object} model.InferenceRequest
// @Failure 400 {object} model.ErrorResponse
// @Failure 404 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/inference/result/{request_id} [get]
func (h *InferenceHandler) GetInferenceResult(c *gin.Context) {
	requestID := c.Param("request_id")
	if requestID == "" {
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: "请求ID不能为空",
		})
		return
	}

	// 获取推理结果
	result, err := h.inferenceService.GetInferenceResult(c.Request.Context(), requestID)
	if err != nil {
		h.logger.WithError(err).WithField("request_id", requestID).Error("获取推理结果失败")
		c.JSON(http.StatusNotFound, model.ErrorResponse{
			Error:   "推理结果不存在",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// GetInferenceStatistics 获取推理统计信息
// @Summary 获取推理统计信息
// @Description 获取推理服务的统计信息
// @Tags 推理服务
// @Accept json
// @Produce json
// @Success 200 {object} model.InferenceStatistics
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/inference/statistics [get]
func (h *InferenceHandler) GetInferenceStatistics(c *gin.Context) {
	// 获取推理统计信息
	stats, err := h.inferenceService.GetInferenceStatistics(c.Request.Context())
	if err != nil {
		h.logger.WithError(err).Error("获取推理统计信息失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "获取推理统计信息失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, stats)
}