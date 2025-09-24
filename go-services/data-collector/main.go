package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gorilla/mux"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/sirupsen/logrus"
	"google.golang.org/grpc"

	"github.com/text-audit/data-collector/internal/config"
	"github.com/text-audit/data-collector/internal/handler"
	"github.com/text-audit/data-collector/internal/service"
	pb "github.com/text-audit/data-collector/proto"
)

func main() {
	// 初始化日志
	logrus.SetFormatter(&logrus.JSONFormatter{})
	logrus.SetLevel(logrus.InfoLevel)

	// 加载配置
	cfg, err := config.Load()
	if err != nil {
		logrus.Fatalf("Failed to load config: %v", err)
	}

	// 初始化服务
	collectorService, err := service.NewCollectorService(cfg)
	if err != nil {
		logrus.Fatalf("Failed to create collector service: %v", err)
	}

	// 启动 gRPC 服务器
	go startGRPCServer(cfg, collectorService)

	// 启动 HTTP 服务器
	go startHTTPServer(cfg, collectorService)

	// 等待信号
	waitForShutdown()

	logrus.Info("Data collector service stopped")
}

func startGRPCServer(cfg *config.Config, service *service.CollectorService) {
	lis, err := net.Listen("tcp", cfg.GRPC.Address)
	if err != nil {
		logrus.Fatalf("Failed to listen on %s: %v", cfg.GRPC.Address, err)
	}

	s := grpc.NewServer()
	pb.RegisterDataCollectionServiceServer(s, service)

	logrus.Infof("gRPC server listening on %s", cfg.GRPC.Address)
	if err := s.Serve(lis); err != nil {
		logrus.Fatalf("Failed to serve gRPC: %v", err)
	}
}

func startHTTPServer(cfg *config.Config, service *service.CollectorService) {
	router := mux.NewRouter()

	// 创建 HTTP 处理器
	httpHandler := handler.NewHTTPHandler(service)

	// 注册路由
	api := router.PathPrefix("/api/v1").Subrouter()
	api.HandleFunc("/collect", httpHandler.CollectText).Methods("POST")
	api.HandleFunc("/status/{taskId}", httpHandler.GetStatus).Methods("GET")
	api.HandleFunc("/tasks", httpHandler.ListTasks).Methods("GET")

	// 健康检查
	router.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	}).Methods("GET")

	// Prometheus 指标
	router.Handle("/metrics", promhttp.Handler())

	// 启动服务器
	srv := &http.Server{
		Addr:         cfg.HTTP.Address,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	logrus.Infof("HTTP server listening on %s", cfg.HTTP.Address)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		logrus.Fatalf("Failed to serve HTTP: %v", err)
	}
}

func waitForShutdown() {
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan
	logrus.Info("Received shutdown signal")
}