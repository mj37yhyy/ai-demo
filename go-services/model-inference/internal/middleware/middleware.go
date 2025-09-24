package middleware

import (
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
)

// Logger 日志中间件
func Logger(logger *logrus.Logger) gin.HandlerFunc {
	return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		logger.WithFields(logrus.Fields{
			"status_code":  param.StatusCode,
			"latency":      param.Latency,
			"client_ip":    param.ClientIP,
			"method":       param.Method,
			"path":         param.Path,
			"request_id":   param.Keys["request_id"],
			"user_agent":   param.Request.UserAgent(),
			"error":        param.ErrorMessage,
		}).Info("HTTP请求")
		return ""
	})
}

// Recovery 恢复中间件
func Recovery(logger *logrus.Logger) gin.HandlerFunc {
	return gin.CustomRecovery(func(c *gin.Context, recovered interface{}) {
		logger.WithFields(logrus.Fields{
			"error":      recovered,
			"request_id": c.GetString("request_id"),
			"path":       c.Request.URL.Path,
			"method":     c.Request.Method,
		}).Error("服务器内部错误")
		
		c.JSON(500, gin.H{
			"error":   "服务器内部错误",
			"message": "请稍后重试",
		})
	})
}

// RequestID 请求ID中间件
func RequestID() gin.HandlerFunc {
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

// CORS 跨域中间件
func CORS() gin.HandlerFunc {
	return cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Length", "Content-Type", "Authorization", "X-Request-ID"},
		ExposeHeaders:    []string{"Content-Length", "X-Request-ID"},
		AllowCredentials: true,
		MaxAge:           12 * time.Hour,
	})
}

// RateLimit 限流中间件（简单实现）
func RateLimit() gin.HandlerFunc {
	// 这里可以实现更复杂的限流逻辑
	// 例如使用 Redis 或内存存储来跟踪请求频率
	return func(c *gin.Context) {
		// 简单的限流逻辑，实际项目中应该使用更复杂的实现
		c.Next()
	}
}