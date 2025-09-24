package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
	"github.com/spf13/viper"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"

	"github.com/textaudit/model-inference/internal/config"
	"github.com/textaudit/model-inference/internal/handler"
	"github.com/textaudit/model-inference/internal/middleware"
	"github.com/textaudit/model-inference/internal/repository"
	"github.com/textaudit/model-inference/internal/service"
	_ "github.com/textaudit/model-inference/docs"
)

// @title TextAudit 模型推理服务 API
// @version 1.0
// @description TextAudit 模型推理服务的 RESTful API 文档
// @termsOfService https://textaudit.com/terms

// @contact.name TextAudit Team
// @contact.url https://textaudit.com/support
// @contact.email support@textaudit.com

// @license.name MIT
// @license.url https://opensource.org/licenses/MIT

// @host localhost:8082
// @BasePath /api/v1

// @securityDefinitions.apikey ApiKeyAuth
// @in header
// @name Authorization

func main() {
	// 初始化日志
	logrus.SetFormatter(&logrus.JSONFormatter{})
	logrus.SetLevel(logrus.InfoLevel)

	// 加载配置
	cfg, err := config.Load()
	if err != nil {
		logrus.Fatalf("加载配置失败: %v", err)
	}

	// 设置日志级别
	if level, err := logrus.ParseLevel(cfg.Log.Level); err == nil {
		logrus.SetLevel(level)
	}

	logrus.Info("启动 TextAudit 模型推理服务...")

	// 初始化数据库
	db, err := repository.NewDatabase(cfg.Database)
	if err != nil {
		logrus.Fatalf("初始化数据库失败: %v", err)
	}

	// 初始化Redis
	redisClient, err := repository.NewRedisClient(cfg.Redis)
	if err != nil {
		logrus.Fatalf("初始化Redis失败: %v", err)
	}

	// 初始化仓库层
	modelRepo := repository.NewModelRepository(db)
	inferenceRepo := repository.NewInferenceRepository(db)
	cacheRepo := repository.NewCacheRepository(redisClient)

	// 初始化服务层
	modelService := service.NewModelService(modelRepo, cacheRepo, cfg.Model)
	inferenceService := service.NewInferenceService(inferenceRepo, modelService, cacheRepo, cfg.Inference)
	healthService := service.NewHealthService(db, redisClient)

	// 初始化处理器
	modelHandler := handler.NewModelHandler(modelService)
	inferenceHandler := handler.NewInferenceHandler(inferenceService)
	healthHandler := handler.NewHealthHandler(healthService)

	// 设置Gin模式
	if cfg.Server.Mode == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	// 创建路由器
	router := gin.New()

	// 添加中间件
	router.Use(middleware.Logger())
	router.Use(middleware.Recovery())
	router.Use(middleware.CORS())
	router.Use(middleware.RequestID())

	// 健康检查
	router.GET("/health", healthHandler.Health)
	router.GET("/ready", healthHandler.Ready)

	// API路由组
	v1 := router.Group("/api/v1")
	{
		// 模型管理
		models := v1.Group("/models")
		{
			models.GET("", modelHandler.ListModels)
			models.GET("/:name", modelHandler.GetModel)
			models.POST("/:name/load", modelHandler.LoadModel)
			models.POST("/:name/unload", modelHandler.UnloadModel)
			models.GET("/:name/status", modelHandler.GetModelStatus)
			models.GET("/statistics", modelHandler.GetStatistics)
		}

		// 推理服务
		inference := v1.Group("/inference")
		{
			inference.POST("/predict", inferenceHandler.Predict)
			inference.POST("/batch-predict", inferenceHandler.BatchPredict)
			inference.GET("/history", inferenceHandler.GetHistory)
			inference.GET("/history/:id", inferenceHandler.GetInferenceResult)
			inference.GET("/statistics", inferenceHandler.GetStatistics)
		}

		// 文本分析
		textAnalysis := v1.Group("/text-analysis")
		{
			textAnalysis.POST("/classify", inferenceHandler.ClassifyText)
			textAnalysis.POST("/sentiment", inferenceHandler.AnalyzeSentiment)
			textAnalysis.POST("/extract-features", inferenceHandler.ExtractFeatures)
			textAnalysis.POST("/detect-anomaly", inferenceHandler.DetectAnomaly)
		}
	}

	// Swagger文档
	if cfg.Server.Mode != "release" {
		router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
	}

	// 指标端点
	router.GET("/metrics", middleware.PrometheusHandler())

	// 创建HTTP服务器
	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.Server.Port),
		Handler:      router,
		ReadTimeout:  time.Duration(cfg.Server.ReadTimeout) * time.Second,
		WriteTimeout: time.Duration(cfg.Server.WriteTimeout) * time.Second,
		IdleTimeout:  time.Duration(cfg.Server.IdleTimeout) * time.Second,
	}

	// 启动服务器
	go func() {
		logrus.Infof("服务器启动在端口 %d", cfg.Server.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logrus.Fatalf("启动服务器失败: %v", err)
		}
	}()

	// 等待中断信号
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logrus.Info("正在关闭服务器...")

	// 优雅关闭
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		logrus.Errorf("服务器关闭失败: %v", err)
	}

	// 关闭数据库连接
	if sqlDB, err := db.DB(); err == nil {
		sqlDB.Close()
	}

	// 关闭Redis连接
	redisClient.Close()

	logrus.Info("服务器已关闭")
}