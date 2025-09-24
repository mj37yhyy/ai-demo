# AI Agent Instructions for AI-Demo Text Audit System

## System Architecture Overview

This is a **multilingual microservices text audit system** implementing the complete AI text audit pipeline following the design from https://www.doubao.com/thread/aa438dc7cdb0c. The system has distinct service boundaries:
- **Go services**: High-performance data collection (`data-collector`) and ML inference (`model-inference`)
- **Java services**: Data preprocessing (`data-preprocessor`) and model training (`model-trainer`)
- **Communication**: Protobuf via gRPC for service-to-service, HTTP/REST for external APIs
- **Data flow**: Multi-source Collection → Text Preprocessing → Model Training → Real-time Inference → Results

### Complete AI Pipeline Coverage
- **Data Collection**: Zhihu crawler, API collection, local file import with anti-crawling mechanisms
- **Preprocessing**: Text cleaning, Chinese word segmentation (HanLP), TF-IDF vectorization, data augmentation
- **Model Training**: Traditional ML (Logistic Regression, Random Forest) + Deep Learning (CNN, LSTM, Transformer) + LLM Fine-tuning (ChatGLM-6B with LoRA)
- **Inference Optimization**: Model quantization, concurrent processing, Redis caching, fallback strategies

## Service Architecture Patterns

### Go Services Structure (Standard Pattern)
```
internal/
├── config/     # YAML-based config with env overrides
├── handler/    # HTTP/gRPC handlers (thin layer)
├── service/    # Business logic
├── repository/ # Data access (MySQL/Redis/Cache)
├── model/      # Domain models and DTOs
└── middleware/ # Auth, logging, metrics
```

### Java Services Structure
- **Spring Boot 3.2** with **Java 21**
- **Gradle** build system with `bootJar` task
- Standard Spring layered architecture with REST controllers

### Key Communication Patterns
- **Inter-service**: gRPC with Protobuf definitions in `proto/text_audit.proto`
- **External APIs**: RESTful HTTP with JSON
- **Async processing**: Kafka topics (`raw-text-topic`, `processed-text-topic`, `text-features`)
- **Caching**: Redis for inference results, hot text, stopwords, and duplicate detection
- **Storage**: MySQL for structured data, MinIO for model files

## Critical Development Workflows

### Local Development Setup
```bash
# Start infrastructure first
docker-compose up -d mysql redis kafka zookeeper minio

# Run Go services (from service directories)
cd go-services/data-collector && go run main.go
cd go-services/model-inference && go run main.go

# Run Java services
cd java-services/data-preprocessor && ./gradlew bootRun
cd java-services/model-trainer && ./gradlew bootRun
```

### Production Deployment
- **Full deployment**: `./scripts/deploy-production.sh` (orchestrates all services)
- **Service debugging**: `./scripts/debug-services.sh` (health checks, logs, metrics)
- **E2E testing**: `./end_to_end_production_test.sh` (complete data pipeline validation)

### Service Port Conventions
- **data-collector**: 8081 (HTTP), gRPC varies by config
- **data-preprocessor**: 8082 (HTTP)
- **model-trainer**: 9082 (HTTP)
- **model-inference**: 9083 (HTTP)
- **Infrastructure**: MySQL:3306, Redis:6379, Kafka:9092

## Project-Specific Conventions

### Configuration Management
- **Go services**: YAML files in `config/` with environment variable overrides
- **Java services**: Spring Boot `application.yaml` + profile-specific configs
- **Docker**: Separate `docker-compose.yml` (dev) and `docker-compose.production.yml`

### Protobuf Integration
- **Shared definitions**: `proto/text_audit.proto` generates code for both Go and Java
- **Go**: Uses `proto/text_audit_grpc.pb.go` and `proto/text_audit.pb.go`
- **Java**: Maven/Gradle protobuf plugins generate classes in target packages

### Monitoring & Observability
- **Metrics**: Prometheus metrics embedded in Go services (`requestsTotal`, `requestDuration`, etc.)
- **Health checks**: `/health` endpoints on all HTTP services
- **Logging**: Structured logging with logrus (Go) and Logback (Java)

### Data Pipeline Flow
1. **Collection**: `data-collector` with multi-source support:
   - **Zhihu Crawler**: Anti-crawling with User-Agent rotation, rate limiting (5 req/sec), proxy pools
   - **API Integration**: Douban reviews, news platforms with official API keys
   - **Local Import**: Batch TXT/CSV file processing via `io` package
2. **Preprocessing**: `data-preprocessor` with comprehensive text processing:
   - **Cleaning**: HTML tag removal (Jsoup), deduplication (MD5 hashing), noise filtering
   - **Segmentation**: HanLP Chinese word segmentation, stopword filtering from Redis
   - **Feature Engineering**: Hand-implemented TF-IDF (Apache Commons Math), 1000-dim vectors
   - **Data Augmentation**: Synonym replacement, random insertion/swap/deletion
3. **Training**: `model-trainer` with multi-algorithm support:
   - **Traditional ML**: Logistic Regression, Random Forest (Weka/Smile frameworks)
   - **Deep Learning**: CNN, LSTM, Transformer (DeepLearning4J)
   - **LLM Fine-tuning**: ChatGLM-6B with LoRA adaptation (rank=8, lr=2e-4)
4. **Inference**: `model-inference` with optimization:
   - **Model Compression**: INT8 quantization, model pruning
   - **Concurrent Processing**: Go goroutines + Java ThreadPoolExecutor
   - **Fallback Strategy**: Keyword matching when model inference fails

## Testing Patterns

### Service-Level Testing
- **Go**: Use `go test` with testify for unit tests
- **Java**: JUnit 5 with Spring Boot Test slices
- **Integration**: Docker Compose test environments

### Production Testing Scripts
- **Component tests**: Individual service test scripts in `*_test.sh` files
- **E2E validation**: `end_to_end_production_test.sh` runs complete data flow
- **Load testing**: Built-in concurrent user simulation

### Zhihu-Specific Testing
- **Crawler Testing**: `tests/data-collector/zhihu_crawler_test.go` validates anti-crawling mechanisms
- **Data Quality**: Production test runners verify text cleaning, deduplication
- **Performance**: Concurrent crawling tests with rate limiting validation

## Key Files for Understanding Context

- **Architecture**: `README.md` (system overview), `docker-compose.yml` (service dependencies)
- **Service APIs**: `proto/text_audit.proto` (data contracts)
- **Deployment**: `scripts/deploy-production.sh` (complete orchestration)
- **Debugging**: `scripts/debug-services.sh` (operational procedures)
- **Service configs**: `go-services/*/config/config.go`, `java-services/*/build.gradle`

When modifying services, always consider the impact on the complete data pipeline and ensure both HTTP and gRPC interfaces remain compatible with the Protobuf schema.