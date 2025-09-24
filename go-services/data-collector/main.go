package main

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/sirupsen/logrus"
	"google.golang.org/grpc"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/handler"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/service"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

// Prometheus metrics
var (
	requestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "data_collector_requests_total",
			Help: "Total number of requests",
		},
		[]string{"method", "endpoint", "status"},
	)
	
	requestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name: "data_collector_request_duration_seconds",
			Help: "Request duration in seconds",
		},
		[]string{"method", "endpoint"},
	)
	
	activeCollectionTasks = prometheus.NewGauge(
		prometheus.GaugeOpts{
			Name: "data_collector_active_tasks",
			Help: "Number of active collection tasks",
		},
	)
)

func init() {
	// 注册 Prometheus metrics
	prometheus.MustRegister(requestsTotal)
	prometheus.MustRegister(requestDuration)
	prometheus.MustRegister(activeCollectionTasks)
}

func main() {
	// 初始化日志
	logrus.SetLevel(logrus.InfoLevel)
	logrus.SetFormatter(&logrus.JSONFormatter{
		TimestampFormat: time.RFC3339,
	})
	
	logger := logrus.WithField("service", "data-collector")
	
	// 加载配置
	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("Failed to load config: %v", err)
	}
	
	// 初始化服务
	collectorService, err := service.NewCollectorService(cfg)
	if err != nil {
		logger.Fatalf("Failed to initialize collector service: %v", err)
	}
	
	// 初始化处理器
	httpHandler := handler.NewHTTPHandler(collectorService)
	
	// 创建上下文用于优雅关闭
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	
	// 启动 gRPC 服务器
	go func() {
		if err := startGRPCServer(ctx, cfg, collectorService, logger); err != nil {
			logger.Errorf("gRPC server error: %v", err)
		}
	}()
	
	// 启动 HTTP 服务器
	go func() {
		if err := startHTTPServer(ctx, cfg, httpHandler, logger); err != nil {
			logger.Errorf("HTTP server error: %v", err)
		}
	}()
	
	// 等待中断信号
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	
	logger.Info("Data collector service started successfully")
	<-sigChan
	
	logger.Info("Shutting down data collector service...")
	cancel()
	
	// 给服务一些时间来优雅关闭
	time.Sleep(5 * time.Second)
	logger.Info("Data collector service stopped")
}

func startGRPCServer(ctx context.Context, cfg *config.Config, service *service.CollectorService, logger *logrus.Entry) error {
	// 从配置中解析端口
	grpcPort := 9090
	if cfg.GRPC.Address != "" {
		// 简单解析，假设格式为 ":port"
		if cfg.GRPC.Address[0] == ':' {
			fmt.Sscanf(cfg.GRPC.Address, ":%d", &grpcPort)
		}
	}
	
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", grpcPort))
	if err != nil {
		return fmt.Errorf("failed to listen on gRPC port %d: %w", grpcPort, err)
	}
	
	grpcServer := grpc.NewServer(
		grpc.UnaryInterceptor(grpcLoggingInterceptor(logger)),
	)
	
	pb.RegisterDataCollectionServiceServer(grpcServer, service)
	
	logger.Infof("gRPC server starting on port %d", grpcPort)
	
	go func() {
		<-ctx.Done()
		logger.Info("Shutting down gRPC server...")
		grpcServer.GracefulStop()
	}()
	
	return grpcServer.Serve(lis)
}

func startHTTPServer(ctx context.Context, cfg *config.Config, handler *handler.HTTPHandler, logger *logrus.Entry) error {
	// 从配置中解析端口
	httpPort := 8080
	if cfg.HTTP.Address != "" {
		// 简单解析，假设格式为 ":port"
		if cfg.HTTP.Address[0] == ':' {
			fmt.Sscanf(cfg.HTTP.Address, ":%d", &httpPort)
		}
	}
	
	// 创建 Gin 引擎
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	
	// 设置路由
	handler.SetupRoutes(router)
	
	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", httpPort),
		Handler:      router,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}
	
	logger.Infof("HTTP server starting on port %d", httpPort)
	
	go func() {
		<-ctx.Done()
		logger.Info("Shutting down HTTP server...")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		server.Shutdown(shutdownCtx)
	}()
	
	return server.ListenAndServe()
}

// gRPC 日志拦截器
func grpcLoggingInterceptor(logger *logrus.Entry) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		start := time.Now()
		
		resp, err := handler(ctx, req)
		
		duration := time.Since(start)
		
		fields := logrus.Fields{
			"method":   info.FullMethod,
			"duration": duration,
		}
		
		if err != nil {
			fields["error"] = err.Error()
			logger.WithFields(fields).Error("gRPC request failed")
		} else {
			logger.WithFields(fields).Info("gRPC request completed")
		}
		
		return resp, err
	}
}