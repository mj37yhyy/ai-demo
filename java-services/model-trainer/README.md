# TextAudit 模型训练服务

TextAudit 模型训练服务是一个基于 Spring Boot 的微服务，提供机器学习和深度学习模型的训练、管理和存储功能。

## 功能特性

### 🚀 核心功能
- **多算法支持**: 支持深度学习（LSTM、GRU、CNN、Transformer、BERT、Autoencoder）和传统机器学习（随机森林、SVM、逻辑回归等）算法
- **数据集管理**: 支持多种格式（CSV、JSON、TXT、ARFF）的数据集上传、预处理和分割
- **模型存储**: 提供模型的存储、加载、备份和版本管理功能
- **训练任务管理**: 完整的训练任务生命周期管理，支持暂停、恢复、取消操作
- **模型评估**: 提供全面的模型评估指标和报告

### 🛠 技术特性
- **异步训练**: 支持异步模型训练，不阻塞API调用
- **进度跟踪**: 实时跟踪训练进度和性能指标
- **缓存优化**: 智能数据集缓存，提升训练效率
- **错误处理**: 完善的异常处理和错误恢复机制
- **API文档**: 集成 Swagger，提供完整的API文档

## 技术栈

- **Java 21**: 使用最新的Java版本
- **Spring Boot 3.2**: 现代化的Spring框架
- **Spring Data JPA**: 数据持久化
- **MySQL**: 主数据库
- **Redis**: 缓存和会话存储
- **Deeplearning4j**: 深度学习框架
- **Smile**: 机器学习库
- **Weka**: 数据挖掘工具
- **Docker**: 容器化部署
- **Swagger**: API文档

## 快速开始

### 环境要求

- Java 21+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 7+

### 本地开发

1. **克隆项目**
```bash
git clone <repository-url>
cd java-services/model-trainer
```

2. **配置环境变量**
```bash
cp .env.example .env
# 编辑 .env 文件，配置数据库和Redis连接信息
```

3. **启动依赖服务**
```bash
docker-compose up -d mysql redis
```

4. **运行应用**
```bash
./gradlew bootRun
```

5. **访问API文档**
```
http://localhost:8081/swagger-ui.html
```

### Docker部署

1. **构建镜像**
```bash
docker build -t textaudit/model-trainer:latest .
```

2. **运行容器**
```bash
docker-compose up -d
```

## API接口

### 训练任务管理
- `POST /api/v1/training/jobs` - 创建训练任务
- `GET /api/v1/training/jobs` - 获取训练任务列表
- `GET /api/v1/training/jobs/{jobId}` - 获取训练任务详情
- `POST /api/v1/training/jobs/{jobId}/start` - 开始训练
- `POST /api/v1/training/jobs/{jobId}/pause` - 暂停训练
- `POST /api/v1/training/jobs/{jobId}/resume` - 恢复训练
- `POST /api/v1/training/jobs/{jobId}/stop` - 停止训练

### 模型管理
- `POST /api/v1/models/upload` - 上传模型文件
- `GET /api/v1/models` - 获取模型列表
- `GET /api/v1/models/{modelName}` - 获取模型信息
- `GET /api/v1/models/{modelName}/download` - 下载模型文件
- `DELETE /api/v1/models/{modelName}` - 删除模型
- `POST /api/v1/models/{modelName}/backup` - 备份模型

### 数据集管理
- `POST /api/v1/datasets/upload` - 上传数据集
- `GET /api/v1/datasets` - 获取数据集列表
- `GET /api/v1/datasets/{datasetPath}` - 获取数据集信息
- `POST /api/v1/datasets/{datasetPath}/split` - 分割数据集
- `POST /api/v1/datasets/{datasetPath}/preprocess` - 预处理数据集

## 配置说明

### 应用配置 (application.yml)

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/textaudit_trainer
    username: ${DB_USERNAME:textaudit}
    password: ${DB_PASSWORD:password}
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}

textaudit:
  trainer:
    model-storage-path: ${MODEL_STORAGE_PATH:./models}
    dataset-storage-path: ${DATASET_STORAGE_PATH:./datasets}
    max-concurrent-jobs: ${MAX_CONCURRENT_JOBS:3}
```

### 环境变量

| 变量名 | 描述 | 默认值 |
|--------|------|--------|
| `DB_HOST` | 数据库主机 | localhost |
| `DB_PORT` | 数据库端口 | 3306 |
| `DB_NAME` | 数据库名称 | textaudit_trainer |
| `DB_USERNAME` | 数据库用户名 | textaudit |
| `DB_PASSWORD` | 数据库密码 | password |
| `REDIS_HOST` | Redis主机 | localhost |
| `REDIS_PORT` | Redis端口 | 6379 |
| `REDIS_PASSWORD` | Redis密码 | (空) |
| `MODEL_STORAGE_PATH` | 模型存储路径 | ./models |
| `DATASET_STORAGE_PATH` | 数据集存储路径 | ./datasets |

## 支持的算法

### 深度学习算法
- **LSTM**: 长短期记忆网络，适用于序列数据
- **GRU**: 门控循环单元，LSTM的简化版本
- **CNN**: 卷积神经网络，适用于图像和文本数据
- **Transformer**: 注意力机制模型，适用于NLP任务
- **BERT**: 预训练语言模型，适用于文本理解
- **Autoencoder**: 自编码器，适用于降维和异常检测

### 传统机器学习算法
- **随机森林**: 集成学习算法，适用于分类和回归
- **支持向量机**: SVM，适用于分类和回归
- **逻辑回归**: 线性分类算法
- **朴素贝叶斯**: 概率分类算法
- **决策树**: 树形分类算法
- **梯度提升**: 集成学习算法
- **K-Means**: 聚类算法
- **DBSCAN**: 密度聚类算法
- **层次聚类**: 分层聚类算法

## 数据格式支持

### 输入格式
- **CSV**: 逗号分隔值文件
- **JSON**: JSON格式数据
- **TXT**: 纯文本文件
- **ARFF**: Weka格式文件

### 数据类型
- **分类数据**: 用于分类任务
- **回归数据**: 用于回归任务
- **聚类数据**: 用于聚类任务
- **文本数据**: 用于NLP任务

## 监控和日志

### 健康检查
```bash
curl http://localhost:8081/actuator/health
```

### 指标监控
```bash
curl http://localhost:8081/actuator/metrics
```

### 日志配置
日志文件位置: `./logs/model-trainer.log`

## 开发指南

### 项目结构
```
src/main/java/com/textaudit/trainer/
├── controller/          # REST控制器
├── service/            # 业务服务层
├── entity/             # 实体类
├── repository/         # 数据访问层
├── config/             # 配置类
├── exception/          # 异常处理
└── ModelTrainerApplication.java
```

### 添加新算法

1. 在相应的服务类中添加算法实现
2. 更新模型类型枚举
3. 添加相应的测试用例
4. 更新API文档

### 代码规范
- 使用 Lombok 减少样板代码
- 遵循 Spring Boot 最佳实践
- 添加适当的日志记录
- 编写单元测试

## 故障排除

### 常见问题

1. **内存不足**
   - 增加JVM堆内存: `-Xmx4g`
   - 优化批处理大小

2. **数据库连接失败**
   - 检查数据库服务状态
   - 验证连接配置

3. **Redis连接失败**
   - 检查Redis服务状态
   - 验证网络连接

4. **模型训练失败**
   - 检查数据集格式
   - 验证超参数配置
   - 查看详细错误日志

### 日志分析
```bash
# 查看应用日志
tail -f logs/model-trainer.log

# 查看错误日志
grep ERROR logs/model-trainer.log

# 查看训练相关日志
grep "Training" logs/model-trainer.log
```

## 性能优化

### 训练性能
- 使用GPU加速（如果可用）
- 调整批处理大小
- 启用数据集缓存
- 使用多线程处理

### 内存优化
- 配置合适的JVM参数
- 定期清理缓存
- 使用流式处理大数据集

## 安全考虑

- 文件上传大小限制
- 输入数据验证
- 访问权限控制
- 敏感信息加密

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

MIT License

## 联系方式

- 邮箱: support@textaudit.com
- 文档: https://docs.textaudit.com
- 问题反馈: https://github.com/textaudit/issues