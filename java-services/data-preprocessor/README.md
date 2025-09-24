# 文本审计系统 - 数据预处理服务

## 概述

数据预处理服务是文本审计系统的核心组件之一，负责对原始文本数据进行清洗、分词、特征提取和数据增强等预处理操作。

## 功能特性

### 核心功能
- **文本清洗**: 去除HTML标签、URL、邮箱、电话号码等噪声
- **中文分词**: 基于HanLP的高精度中文分词
- **特征提取**: 统计特征、TF-IDF、Word2Vec、N-gram等多维度特征
- **数据增强**: 同义词替换、随机插入、交换、删除等增强策略
- **缓存优化**: Redis缓存提升处理性能
- **批量处理**: 支持大规模文本批量处理

### 技术栈
- **框架**: Spring Boot 2.7.x
- **数据库**: MySQL 8.0 + JPA
- **缓存**: Redis 6.x
- **消息队列**: Apache Kafka
- **NLP库**: HanLP 1.8.x
- **构建工具**: Gradle 8.5
- **容器化**: Docker

## 项目结构

```
src/main/java/com/textaudit/preprocessor/
├── DataPreprocessorApplication.java    # 主应用类
├── config/                            # 配置类
│   ├── KafkaConfig.java              # Kafka配置
│   ├── RedisConfig.java              # Redis配置
│   └── SwaggerConfig.java            # API文档配置
├── controller/                        # 控制器
│   └── PreprocessorController.java   # REST API接口
├── dto/                              # 数据传输对象
│   ├── ProcessingResult.java         # 处理结果DTO
│   ├── TextFeatures.java            # 文本特征DTO
│   └── TokenizationResult.java       # 分词结果DTO
├── entity/                           # 实体类
│   └── ProcessedText.java           # 预处理文本实体
├── exception/                        # 异常处理
│   ├── GlobalExceptionHandler.java   # 全局异常处理器
│   ├── TextProcessingException.java  # 文本处理异常
│   ├── FeatureExtractionException.java # 特征提取异常
│   └── DataAugmentationException.java # 数据增强异常
├── kafka/                           # Kafka消费者
│   └── RawTextConsumer.java         # 原始文本消费者
├── repository/                      # 数据仓库
│   └── ProcessedTextRepository.java # 预处理文本仓库
└── service/                        # 服务层
    ├── TextProcessingService.java   # 文本处理服务
    ├── FeatureExtractionService.java # 特征提取服务
    ├── DataAugmentationService.java # 数据增强服务
    └── CacheService.java           # 缓存服务
```

## 快速开始

### 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Apache Kafka 2.8+

### 本地开发

1. **克隆项目**
```bash
git clone <repository-url>
cd java-services/data-preprocessor
```

2. **配置数据库**
```sql
CREATE DATABASE text_audit_preprocessor;
```

3. **配置application.yml**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/text_audit_preprocessor
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

4. **启动服务**
```bash
./gradlew bootRun
```

### Docker部署

1. **构建镜像**
```bash
docker build -t text-audit/data-preprocessor:latest .
```

2. **运行容器**
```bash
docker run -d \
  --name data-preprocessor \
  -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=docker \
  text-audit/data-preprocessor:latest
```

## API文档

服务启动后，访问以下地址查看API文档：
- Swagger UI: http://localhost:8081/api/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/api/v3/api-docs

### 主要接口

#### 1. 处理单个文本
```http
POST /api/v1/preprocess/text
Content-Type: application/json

{
  "textId": "text_001",
  "content": "这是需要处理的文本内容",
  "source": "web",
  "extractFeatures": true,
  "augmentData": false
}
```

#### 2. 批量处理文本
```http
POST /api/v1/preprocess/batch
Content-Type: application/json

{
  "texts": [
    {
      "textId": "text_001",
      "content": "文本内容1",
      "source": "api"
    },
    {
      "textId": "text_002", 
      "content": "文本内容2",
      "source": "file"
    }
  ],
  "extractFeatures": true,
  "augmentData": true
}
```

#### 3. 提取文本特征
```http
POST /api/v1/features/extract
Content-Type: application/json

{
  "text": "需要提取特征的文本",
  "includeStatistics": true,
  "includeTfIdf": true,
  "includeWord2Vec": true,
  "includeNgrams": true
}
```

#### 4. 数据增强
```http
POST /api/v1/augment
Content-Type: application/json

{
  "text": "原始文本内容",
  "strategies": ["synonym_replacement", "random_insertion"],
  "augmentationCount": 3
}
```

## 配置说明

### 文本处理配置
```yaml
text-processing:
  cleaning:
    remove-html: true
    remove-urls: true
    remove-emails: true
    remove-phones: true
    remove-special-chars: true
    normalize-whitespace: true
    convert-to-lowercase: false
  tokenization:
    enable-pos-tagging: true
    enable-ner: true
    filter-stopwords: true
    min-word-length: 1
  limits:
    max-text-length: 10000
    max-word-length: 50
```

### 特征提取配置
```yaml
feature-extraction:
  statistics:
    enable-basic-stats: true
    enable-advanced-stats: true
  tfidf:
    max-features: 5000
    min-df: 1
    max-df: 0.95
  word2vec:
    vector-size: 100
    window-size: 5
  ngrams:
    min-n: 1
    max-n: 3
```

### 数据增强配置
```yaml
data-augmentation:
  enabled: true
  synonym-replacement:
    enabled: true
    replacement-rate: 0.1
  random-insertion:
    enabled: true
    insertion-rate: 0.1
  random-swap:
    enabled: true
    swap-rate: 0.1
  random-deletion:
    enabled: true
    deletion-rate: 0.1
  max-augmented-samples: 5
```

## 监控和日志

### 健康检查
```http
GET /api/actuator/health
```

### 指标监控
```http
GET /api/actuator/metrics
GET /api/actuator/prometheus
```

### 日志配置
日志文件位置：`/app/logs/`
- `application.log`: 应用日志
- `error.log`: 错误日志
- `access.log`: 访问日志

## 性能优化

### 缓存策略
- 处理结果缓存：2小时
- 文本特征缓存：6小时
- 统计信息缓存：1天
- 词频统计缓存：1天

### JVM调优
```bash
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication"
```

### 数据库优化
- 索引优化：原始文本ID、数据源、创建时间
- 连接池配置：HikariCP
- 查询优化：分页查询、批量操作

## 故障排除

### 常见问题

1. **内存不足**
   - 调整JVM堆内存大小
   - 优化批处理大小
   - 启用G1垃圾收集器

2. **Redis连接失败**
   - 检查Redis服务状态
   - 验证连接配置
   - 检查网络连通性

3. **Kafka消费延迟**
   - 调整消费者配置
   - 增加分区数量
   - 优化批处理大小

4. **数据库连接超时**
   - 调整连接池配置
   - 优化SQL查询
   - 检查数据库性能

### 日志分析
```bash
# 查看应用日志
docker logs data-preprocessor

# 查看错误日志
docker exec data-preprocessor tail -f /app/logs/error.log

# 查看性能指标
curl http://localhost:8081/api/actuator/metrics
```

## 开发指南

### 代码规范
- 遵循阿里巴巴Java开发手册
- 使用Lombok减少样板代码
- 统一异常处理机制
- 完善的单元测试覆盖

### 测试
```bash
# 运行所有测试
./gradlew test

# 运行集成测试
./gradlew integrationTest

# 生成测试报告
./gradlew jacocoTestReport
```

### 构建部署
```bash
# 本地构建
./gradlew build

# 构建Docker镜像
./gradlew bootBuildImage

# 发布到仓库
./gradlew publish
```

## 许可证

MIT License

## 联系方式

- 项目维护：TextAudit Team
- 邮箱：support@textaudit.com
- 文档：https://docs.textaudit.com