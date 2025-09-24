# 多源文本审核系统

基于 Java 与 Go 技术栈构建的全链路 AI 文本内容审核系统，聚焦"违规文本检测"核心需求（识别色情、暴力、广告等违规内容）。

## 系统架构

系统采用分层解耦架构，自上而下分为：
- 交互层：Web UI 和 API 接口
- 应用层：业务逻辑处理
- 推理层：模型推理服务
- 模型层：机器学习和深度学习模型
- 数据层：数据采集和预处理
- 基础设施层：存储、消息队列、监控

## 技术栈

### Go 服务
- 数据采集服务：多源文本采集（API、爬虫、本地文件）
- 推理服务：模型推理和 API 接口
- 依赖管理：Go Modules

### Java 服务
- 数据预处理服务：文本清洗和特征工程
- 模型训练服务：机器学习和深度学习模型训练
- 版本：Java 21
- 构建工具：Gradle

### 基础设施
- 存储：MySQL + Redis + MinIO
- 消息队列：Kafka + Zookeeper
- 监控：Prometheus + Grafana
- 部署：Docker + Kubernetes

## 项目结构

```
AI-Demo/
├── go-services/           # Go 服务
│   ├── data-collector/    # 数据采集服务
│   └── inference-service/ # 推理服务
├── java-services/         # Java 服务
│   ├── data-processor/    # 数据预处理服务
│   └── model-trainer/     # 模型训练服务
├── proto/                 # Protobuf 定义
├── docker/               # Docker 配置
├── k8s/                  # Kubernetes 配置
├── monitoring/           # 监控配置
└── docs/                 # 文档
```

## 快速开始

### 环境要求
- Go 1.21+
- Java 21
- Docker & Docker Compose
- Kubernetes (可选)

### 启动服务
```bash
# 启动基础设施
docker-compose up -d

# 启动 Go 服务
cd go-services/data-collector && go run main.go
cd go-services/inference-service && go run main.go

# 启动 Java 服务
cd java-services/data-processor && ./gradlew bootRun
cd java-services/model-trainer && ./gradlew bootRun
```

## 功能特性

### 数据采集
- 多源采集：API 接口、网页爬虫、本地文件
- 并发处理：Go 协程实现高并发采集
- 数据格式：Protobuf 标准化数据格式

### 数据预处理
- 文本清洗：去重、去噪、长度过滤
- 特征工程：中文分词、停用词过滤、TF-IDF 向量化
- 数据存储：MySQL 持久化存储

### 模型训练
- 传统机器学习：逻辑回归、随机森林
- 深度学习：CNN、RNN 模型
- 模型评估：准确率、F1 值等指标

### 推理服务
- 实时推理：毫秒级响应
- 批量处理：支持批量文本审核
- API 接口：RESTful API 和 gRPC 接口

## 许可证

MIT License