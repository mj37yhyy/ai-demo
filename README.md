# AI Demo - 智能数据处理与模型推理系统

基于 Java 与 Go 技术栈构建的全链路 AI 智能数据处理与模型推理系统，集成了数据采集、预处理、模型训练和推理等核心功能，支持 ChatGLM-6B 等大语言模型的训练和推理。

## 🚀 快速开始

### 环境要求
- Docker 20.10+
- Docker Compose 2.0+
- 至少 16GB RAM
- 100GB+ 可用磁盘空间
- NVIDIA GPU (推荐，用于模型训练和推理)

### 一键部署
```bash
# 1. 克隆项目
git clone <repository-url>
cd AI-Demo

# 2. 设置权限
chmod +x scripts/*.sh

# 3. 生产环境部署
./scripts/deploy-production.sh

# 4. 访问系统
# Grafana 监控面板: http://localhost:3000 (admin/admin)
# 数据采集 API: http://localhost:8081
# 模型推理 API: http://localhost:9083
```

## 🏗️ 系统架构

### 核心服务
- **数据采集服务** (Go): 多源数据采集和实时处理
- **数据预处理服务** (Java): 数据清洗、转换和特征工程
- **模型训练服务** (Java): 支持 ChatGLM-6B 模型的微调训练
- **模型推理服务** (Go): 高性能模型推理 API

### 基础设施
- **MySQL 8.0**: 主数据库，存储业务数据
- **Redis 7.0**: 缓存和会话存储
- **Kafka**: 消息队列，服务间异步通信
- **MinIO**: 对象存储，存储模型文件和数据集

### 监控管理
- **Prometheus**: 指标收集和监控
- **Grafana**: 可视化监控面板
- **Nginx**: 反向代理和负载均衡

## 📁 项目结构

```
AI-Demo/
├── go-services/              # Go 微服务
│   ├── data-collector/       # 数据采集服务
│   └── model-inference/      # 模型推理服务
├── java-services/            # Java 微服务
│   ├── data-preprocessor/    # 数据预处理服务
│   └── model-trainer/        # 模型训练服务
├── scripts/                  # 部署和管理脚本
│   ├── deploy-production.sh  # 生产环境部署
│   ├── monitor-services.sh   # 服务监控
│   ├── view-logs.sh         # 日志查看
│   └── debug-services.sh    # 服务调试
├── docs/                     # 项目文档
│   ├── deployment-guide.md   # 部署指南
│   ├── operations-manual.md  # 运维手册
│   └── chatglm-6b-training-plan.md  # 模型训练计划
├── docker-compose.yml        # 开发环境配置
├── docker-compose.production.yml  # 生产环境配置
└── README.md                # 项目说明
```

## 🛠️ 管理脚本

### 部署管理
```bash
# 生产环境部署
./scripts/deploy-production.sh

# 服务监控
./scripts/monitor-services.sh

# 查看日志
./scripts/view-logs.sh

# 调试服务
./scripts/debug-services.sh
```

### 服务操作
```bash
# 启动所有服务
docker-compose -f docker-compose.production.yml up -d

# 停止所有服务
docker-compose -f docker-compose.production.yml down

# 重启特定服务
docker-compose -f docker-compose.production.yml restart model-inference

# 查看服务状态
docker-compose -f docker-compose.production.yml ps
```

## 📊 监控和日志

### 访问地址
- **Grafana 监控面板**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **数据采集 API**: http://localhost:8081
- **数据预处理 API**: http://localhost:8082
- **模型训练 API**: http://localhost:9082
- **模型推理 API**: http://localhost:9083

### 日志管理
```bash
# 查看所有服务日志
./scripts/view-logs.sh -a

# 查看特定服务日志
./scripts/view-logs.sh -s model-inference

# 实时监控日志
./scripts/view-logs.sh -f

# 搜索错误日志
./scripts/view-logs.sh -e
```

## 🔧 技术栈

### 后端服务
- **Go 1.21+**: 高性能的模型训练和推理服务
- **Java 21**: 数据处理和业务逻辑服务
- **Python 3.9+**: 机器学习和模型相关组件

### 数据存储
- **MySQL 8.0**: 关系型数据库
- **Redis 7.0**: 内存缓存数据库
- **MinIO**: S3 兼容的对象存储

### 消息队列
- **Apache Kafka**: 分布式流处理平台
- **Zookeeper**: Kafka 集群协调

### 监控运维
- **Prometheus**: 监控指标收集
- **Grafana**: 数据可视化
- **Docker**: 容器化部署
- **Nginx**: 反向代理和负载均衡

### 机器学习
- **PyTorch**: 深度学习框架
- **Transformers**: 预训练模型库
- **ChatGLM-6B**: 对话语言模型

## 📚 文档

- [部署指南](docs/deployment-guide.md): 详细的部署流程和配置说明
- [运维手册](docs/operations-manual.md): 日常运维和故障处理指南
- [ChatGLM-6B 训练计划](docs/chatglm-6b-training-plan.md): 模型训练的详细计划

## 🚨 故障排查

### 常见问题
1. **服务启动失败**: 检查端口占用和依赖服务状态
2. **内存不足**: 调整 Docker 内存限制或系统配置
3. **GPU 不可用**: 检查 NVIDIA Docker 运行时配置
4. **网络连接问题**: 检查防火墙和网络配置

### 调试工具
```bash
# 服务健康检查
./scripts/monitor-services.sh -s

# 查看系统资源
./scripts/monitor-services.sh -r

# 网络连接测试
./scripts/debug-services.sh
```

## 🔒 安全配置

### 网络安全
- 防火墙配置
- VPN 访问控制
- SSL/TLS 加密

### 访问控制
- API 密钥认证
- JWT 令牌验证
- 角色权限管理

### 数据保护
- 数据库加密
- 敏感信息脱敏
- 审计日志记录

## 📈 性能优化

### 系统级优化
- 调整 Docker 资源限制
- 优化内核参数
- 配置 SSD 存储

### 应用级优化
- 连接池配置
- 缓存策略
- 批处理优化

### GPU 优化
- CUDA 内存管理
- 模型量化
- 批处理推理

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

## 📞 联系方式

- 项目维护者: AI Demo Team
- 技术支持: support@ai-demo.com
- 问题反馈: https://github.com/ai-demo/issues

---

**注意**: 本系统包含 GPU 加速的机器学习组件，建议在配备 NVIDIA GPU 的环境中部署以获得最佳性能。

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