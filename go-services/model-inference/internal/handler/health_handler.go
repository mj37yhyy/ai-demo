package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/service"
)

// HealthHandler 健康检查处理器
type HealthHandler struct {
	healthService service.HealthService
	logger        *logrus.Logger
}

// NewHealthHandler 创建健康检查处理器
func NewHealthHandler(healthService service.HealthService, logger *logrus.Logger) *HealthHandler {
	return &HealthHandler{
		healthService: healthService,
		logger:        logger,
	}
}

// Health 健康检查
// @Summary 健康检查
// @Description 检查服务的健康状态
// @Tags 健康检查
// @Accept json
// @Produce json
// @Success 200 {object} model.HealthResponse
// @Failure 503 {object} model.HealthResponse
// @Router /health [get]
func (h *HealthHandler) Health(c *gin.Context) {
	response := h.healthService.Health(c.Request.Context())
	
	if response.Status == "healthy" {
		c.JSON(http.StatusOK, response)
	} else {
		c.JSON(http.StatusServiceUnavailable, response)
	}
}

// Ready 就绪检查
// @Summary 就绪检查
// @Description 检查服务是否准备好接收请求
// @Tags 健康检查
// @Accept json
// @Produce json
// @Success 200 {object} model.HealthResponse
// @Failure 503 {object} model.HealthResponse
// @Router /ready [get]
func (h *HealthHandler) Ready(c *gin.Context) {
	response := h.healthService.Ready(c.Request.Context())
	
	if response.Status == "healthy" {
		c.JSON(http.StatusOK, response)
	} else {
		c.JSON(http.StatusServiceUnavailable, response)
	}
}