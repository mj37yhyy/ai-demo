package service

import (
	"context"
	"time"

	"github.com/go-redis/redis/v8"
	"gorm.io/gorm"

	"github.com/mj37yhyy/ai-demo/go-services/model-inference/internal/model"
)

// HealthService 健康检查服务接口
type HealthService interface {
	Health(ctx context.Context) *model.HealthResponse
	Ready(ctx context.Context) *model.HealthResponse
}

// healthService 健康检查服务实现
type healthService struct {
	db          *gorm.DB
	redisClient *redis.Client
}

// NewHealthService 创建健康检查服务
func NewHealthService(db *gorm.DB, redisClient *redis.Client) HealthService {
	return &healthService{
		db:          db,
		redisClient: redisClient,
	}
}

// Health 健康检查
func (s *healthService) Health(ctx context.Context) *model.HealthResponse {
	response := &model.HealthResponse{
		Status:    "healthy",
		Timestamp: time.Now(),
		Services:  make(map[string]interface{}),
	}

	// 检查数据库连接
	dbStatus := s.checkDatabase(ctx)
	response.Services["database"] = dbStatus

	// 检查Redis连接
	redisStatus := s.checkRedis(ctx)
	response.Services["redis"] = redisStatus

	// 如果任何服务不健康，整体状态为不健康
	if !dbStatus["healthy"].(bool) || !redisStatus["healthy"].(bool) {
		response.Status = "unhealthy"
	}

	return response
}

// Ready 就绪检查
func (s *healthService) Ready(ctx context.Context) *model.HealthResponse {
	// 就绪检查与健康检查相同
	return s.Health(ctx)
}

// checkDatabase 检查数据库连接
func (s *healthService) checkDatabase(ctx context.Context) map[string]interface{} {
	status := map[string]interface{}{
		"healthy": false,
		"message": "",
	}

	// 获取底层的sql.DB对象
	sqlDB, err := s.db.DB()
	if err != nil {
		status["message"] = "获取数据库连接失败: " + err.Error()
		return status
	}

	// 检查连接
	if err := sqlDB.PingContext(ctx); err != nil {
		status["message"] = "数据库连接失败: " + err.Error()
		return status
	}

	// 获取连接统计信息
	stats := sqlDB.Stats()
	status["healthy"] = true
	status["message"] = "数据库连接正常"
	status["stats"] = map[string]interface{}{
		"open_connections": stats.OpenConnections,
		"in_use":          stats.InUse,
		"idle":            stats.Idle,
	}

	return status
}

// checkRedis 检查Redis连接
func (s *healthService) checkRedis(ctx context.Context) map[string]interface{} {
	status := map[string]interface{}{
		"healthy": false,
		"message": "",
	}

	// 检查Redis连接
	if err := s.redisClient.Ping(ctx).Err(); err != nil {
		status["message"] = "Redis连接失败: " + err.Error()
		return status
	}

	// 获取Redis信息
	info, err := s.redisClient.Info(ctx, "server").Result()
	if err != nil {
		status["message"] = "获取Redis信息失败: " + err.Error()
		return status
	}

	status["healthy"] = true
	status["message"] = "Redis连接正常"
	status["info"] = info

	return status
}