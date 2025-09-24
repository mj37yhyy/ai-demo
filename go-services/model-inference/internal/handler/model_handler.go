package handler

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/model"
	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/service"
)

// ModelHandler 模型处理器
type ModelHandler struct {
	modelService service.ModelService
	logger       *logrus.Logger
}

// NewModelHandler 创建模型处理器
func NewModelHandler(modelService service.ModelService, logger *logrus.Logger) *ModelHandler {
	return &ModelHandler{
		modelService: modelService,
		logger:       logger,
	}
}

// LoadModel 加载模型
// @Summary 加载模型
// @Description 加载指定的模型到内存中
// @Tags 模型管理
// @Accept json
// @Produce json
// @Param name path string true "模型名称"
// @Param request body model.ModelLoadRequest true "加载请求"
// @Success 200 {object} model.ModelStatusResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /models/{name}/load [post]
func (h *ModelHandler) LoadModel(c *gin.Context) {
	modelName := c.Param("name")
	if modelName == "" {
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "模型名称不能为空",
			Message: "model name is required",
		})
		return
	}

	var req model.ModelLoadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.WithError(err).Error("解析请求参数失败")
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "请求参数错误",
			Message: err.Error(),
		})
		return
	}

	// 加载模型
	err := h.modelService.LoadModel(c.Request.Context(), modelName, req.Force)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", modelName).Error("加载模型失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "加载模型失败",
			Message: err.Error(),
		})
		return
	}

	// 获取模型状态
	status, err := h.modelService.GetModelStatus(c.Request.Context(), modelName)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", modelName).Error("获取模型状态失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "获取模型状态失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, status)
}

// UnloadModel 卸载模型
// @Summary 卸载模型
// @Description 从内存中卸载指定的模型
// @Tags 模型管理
// @Accept json
// @Produce json
// @Param model_name path string true "模型名称"
// @Success 200 {object} model.ModelStatusResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/models/{model_name}/unload [post]
func (h *ModelHandler) UnloadModel(c *gin.Context) {
	modelName := c.Param("model_name")
	if modelName == "" {
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: "模型名称不能为空",
		})
		return
	}

	// 卸载模型
	err := h.modelService.UnloadModel(c.Request.Context(), modelName)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", modelName).Error("卸载模型失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "卸载模型失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, model.ModelStatusResponse{
		Name:   modelName,
		Status: model.ModelStatusUnloaded,
	})
}

// GetModel 获取模型信息
// @Summary 获取模型信息
// @Description 获取指定模型的详细信息
// @Tags 模型管理
// @Accept json
// @Produce json
// @Param model_name path string true "模型名称"
// @Success 200 {object} model.Model
// @Failure 400 {object} model.ErrorResponse
// @Failure 404 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/models/{model_name} [get]
func (h *ModelHandler) GetModel(c *gin.Context) {
	modelName := c.Param("model_name")
	if modelName == "" {
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: "模型名称不能为空",
		})
		return
	}

	// 获取模型信息
	modelInfo, err := h.modelService.GetModel(c.Request.Context(), modelName)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", modelName).Error("获取模型信息失败")
		c.JSON(http.StatusNotFound, model.ErrorResponse{
			Error:   "模型不存在",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, modelInfo)
}

// ListModels 获取模型列表
// @Summary 获取模型列表
// @Description 获取所有模型的列表
// @Tags 模型管理
// @Accept json
// @Produce json
// @Param page query int false "页码" default(1)
// @Param limit query int false "每页数量" default(10)
// @Param type query string false "模型类型"
// @Param status query string false "模型状态"
// @Success 200 {array} model.Model
// @Failure 400 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/models [get]
func (h *ModelHandler) ListModels(c *gin.Context) {
	// 解析查询参数
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))
	modelType := c.Query("type")
	status := c.Query("status")

	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 10
	}

	// 获取模型列表
	models, err := h.modelService.ListModels(c.Request.Context(), page, limit)
	if err != nil {
		h.logger.WithError(err).Error("获取模型列表失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "获取模型列表失败",
			Message: err.Error(),
		})
		return
	}

	// 过滤结果
	var filteredModels []*model.Model
	for _, m := range models {
		if modelType != "" && string(m.Type) != modelType {
			continue
		}
		if status != "" && string(m.Status) != status {
			continue
		}
		filteredModels = append(filteredModels, m)
	}

	c.JSON(http.StatusOK, filteredModels)
}

// GetModelStatus 获取模型状态
// @Summary 获取模型状态
// @Description 获取指定模型的当前状态
// @Tags 模型管理
// @Accept json
// @Produce json
// @Param model_name path string true "模型名称"
// @Success 200 {object} model.ModelStatusResponse
// @Failure 400 {object} model.ErrorResponse
// @Failure 404 {object} model.ErrorResponse
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/models/{model_name}/status [get]
func (h *ModelHandler) GetModelStatus(c *gin.Context) {
	modelName := c.Param("model_name")
	if modelName == "" {
		c.JSON(http.StatusBadRequest, model.ErrorResponse{
			Error:   "无效的请求参数",
			Message: "模型名称不能为空",
		})
		return
	}

	// 获取模型状态
	status, err := h.modelService.GetModelStatus(c.Request.Context(), modelName)
	if err != nil {
		h.logger.WithError(err).WithField("model_name", modelName).Error("获取模型状态失败")
		c.JSON(http.StatusNotFound, model.ErrorResponse{
			Error:   "模型不存在",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, status)
}

// GetModelStatistics 获取模型统计信息
// @Summary 获取模型统计信息
// @Description 获取模型的统计信息
// @Tags 模型管理
// @Accept json
// @Produce json
// @Success 200 {object} model.ModelStatistics
// @Failure 500 {object} model.ErrorResponse
// @Router /api/v1/models/statistics [get]
func (h *ModelHandler) GetModelStatistics(c *gin.Context) {
	// 获取模型统计信息
	stats, err := h.modelService.GetStatistics(c.Request.Context())
	if err != nil {
		h.logger.WithError(err).Error("获取模型统计信息失败")
		c.JSON(http.StatusInternalServerError, model.ErrorResponse{
			Error:   "获取模型统计信息失败",
			Message: err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, stats)
}