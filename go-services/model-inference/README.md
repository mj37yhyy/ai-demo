# Model Inference Service

模型推理服务，提供机器学习模型的推理功能，支持文本分析、分类、情感分析等多种AI服务。

## 功能特性

- **模型管理**: 支持模型的加载、卸载、状态查询
- **推理服务**: 提供单次预测和批量预测功能
- **文本分析**: 支持文本分类、情感分析、特征提取、异常检测
- **缓存机制**: 使用Redis缓存推理结果，提高性能
- **监控指标**: 集成Prometheus监控
- **健康检查**: 提供健康检查和就绪检查接口
- **API文档**: 集成Swagger文档

## 技术栈

- **语言**: Go 1.21
- **框架**: Gin
- **数据库**: MySQL + GORM
- **缓存**: Redis
- **监控**: Prometheus
- **文档**: Swagger
- **日志**: Logrus

## 项目结构

```
model-inference/
├── cmd/
│   └── main.go              # 应用入口
├── internal/
│   ├── config/
│   │   ├── config.go        # 配置管理
│   │   ├── database.go      # 数据库连接
│   │   └── redis.go         # Redis连接
│   ├── model/
│   │   └── model.go         # 数据模型定义
│   ├── repository/
│   │   ├── model_repository.go      # 模型数据访问层
│   │   ├── inference_repository.go  # 推理数据访问层
│   │   └── cache_repository.go      # 缓存数据访问层
│   ├── service/
│   │   ├── model_service.go         # 模型业务逻辑
│   │   ├── inference_service.go     # 推理业务逻辑
│   │   └── health_service.go        # 健康检查服务
│   ├── handler/
│   │   ├── model_handler.go         # 模型HTTP处理器
│   │   ├── inference_handler.go     # 推理HTTP处理器
│   │   └── health_handler.go        # 健康检查处理器
│   └── middleware/
│       └── middleware.go            # HTTP中间件
├── config/
│   └── config.yaml          # 配置文件
├── Dockerfile               # Docker构建文件
├── go.mod                   # Go模块文件
├── go.sum                   # Go依赖校验文件
└── README.md               # 项目文档
```

## 快速开始

### 环境要求

- Go 1.21+
- MySQL 8.0+
- Redis 6.0+

### 安装依赖

```bash
go mod download
```

### 配置文件

复制并修改配置文件：

```bash
cp config/config.yaml.example config/config.yaml
```

### 运行服务

```bash
go run cmd/main.go
```

### Docker运行

```bash
# 构建镜像
docker build -t model-inference:latest .

# 运行容器
docker run -d \
  --name model-inference \
  -p 8083:8083 \
  -v $(pwd)/config:/root/config \
  -v $(pwd)/models:/app/models \
  model-inference:latest
```

## API文档

服务启动后，可以通过以下地址访问API文档：

- Swagger UI: http://localhost:8083/swagger/index.html

### 主要接口

#### 模型管理

- `POST /api/v1/models/load` - 加载模型
- `POST /api/v1/models/{model_name}/unload` - 卸载模型
- `GET /api/v1/models/{model_name}` - 获取模型信息
- `GET /api/v1/models` - 获取模型列表
- `GET /api/v1/models/{model_name}/status` - 获取模型状态
- `GET /api/v1/models/statistics` - 获取模型统计信息

#### 推理服务

- `POST /api/v1/inference/predict` - 单次预测
- `POST /api/v1/inference/batch-predict` - 批量预测
- `GET /api/v1/inference/history` - 获取推理历史
- `GET /api/v1/inference/result/{request_id}` - 获取推理结果
- `GET /api/v1/inference/statistics` - 获取推理统计信息

#### 文本分析

- `POST /api/v1/text/classify` - 文本分类
- `POST /api/v1/text/sentiment` - 情感分析
- `POST /api/v1/text/features` - 特征提取
- `POST /api/v1/text/anomaly` - 异常检测

#### 健康检查

- `GET /health` - 健康检查
- `GET /ready` - 就绪检查

#### 监控指标

- `GET /metrics` - Prometheus指标

## 配置说明

### 服务器配置

```yaml
server:
  host: "0.0.0.0"          # 监听地址
  port: 8083               # 监听端口
  mode: "debug"            # 运行模式: debug, release, test
  read_timeout: 30s        # 读取超时
  write_timeout: 30s       # 写入超时
  idle_timeout: 60s        # 空闲超时
```

### 数据库配置

```yaml
database:
  host: "localhost"        # 数据库主机
  port: 3306              # 数据库端口
  user: "root"            # 数据库用户
  password: "password"     # 数据库密码
  dbname: "text_audit"    # 数据库名称
  charset: "utf8mb4"      # 字符集
  parse_time: true        # 解析时间
  loc: "Local"            # 时区
  max_idle_conns: 10      # 最大空闲连接数
  max_open_conns: 100     # 最大打开连接数
  conn_max_lifetime: 3600s # 连接最大生命周期
```

### Redis配置

```yaml
redis:
  host: "localhost"       # Redis主机
  port: 6379             # Redis端口
  password: ""           # Redis密码
  db: 0                  # Redis数据库
  pool_size: 10          # 连接池大小
  min_idle_conns: 5      # 最小空闲连接数
  dial_timeout: 5s       # 连接超时
  read_timeout: 3s       # 读取超时
  write_timeout: 3s      # 写入超时
  pool_timeout: 4s       # 连接池超时
  idle_timeout: 300s     # 空闲超时
```

## 开发指南

### 添加新的推理类型

1. 在 `internal/model/model.go` 中定义请求和响应结构体
2. 在 `internal/service/inference_service.go` 中实现业务逻辑
3. 在 `internal/handler/inference_handler.go` 中添加HTTP处理器
4. 在 `cmd/main.go` 中注册路由

### 添加新的模型类型

1. 在 `internal/model/model.go` 中扩展 `ModelType` 枚举
2. 在 `internal/service/model_service.go` 中添加相应的处理逻辑
3. 更新数据库迁移脚本

## 监控和日志

### 监控指标

服务集成了Prometheus监控，提供以下指标：

- HTTP请求数量和延迟
- 数据库连接状态
- Redis连接状态
- 模型加载状态
- 推理请求统计

### 日志格式

服务使用结构化日志，支持JSON和文本格式：

```json
{
  "level": "info",
  "time": "2024-01-01T12:00:00Z",
  "msg": "HTTP请求",
  "request_id": "uuid",
  "method": "POST",
  "path": "/api/v1/inference/predict",
  "status_code": 200,
  "latency": "100ms"
}
```

## 部署

### Docker Compose

```yaml
version: '3.8'
services:
  model-inference:
    build: .
    ports:
      - "8083:8083"
    environment:
      - DATABASE_HOST=mysql
      - REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    volumes:
      - ./models:/app/models
      - ./config:/root/config
```

### Kubernetes

参考 `k8s/` 目录下的部署文件。

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查数据库配置
   - 确认数据库服务是否启动
   - 检查网络连接

2. **Redis连接失败**
   - 检查Redis配置
   - 确认Redis服务是否启动
   - 检查网络连接

3. **模型加载失败**
   - 检查模型文件路径
   - 确认模型文件格式
   - 检查磁盘空间

### 日志查看

```bash
# 查看容器日志
docker logs model-inference

# 查看文件日志
tail -f /var/log/model-inference.log
```

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

MIT License