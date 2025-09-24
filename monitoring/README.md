# 文本审核系统监控配置

本文档描述了文本审核系统的监控配置，包括 Prometheus 指标收集和 Grafana 可视化仪表板。

## 监控架构

### 组件概览
- **Prometheus**: 指标收集和存储
- **Grafana**: 数据可视化和告警
- **应用指标**: 各微服务暴露的业务指标
- **基础设施指标**: MySQL、Redis、Kafka 等中间件指标

### 监控指标分类

#### 1. 应用层指标
- **请求指标**: QPS、响应时间、错误率
- **业务指标**: 文本处理量、模型推理次数、训练任务状态
- **JVM指标**: 内存使用、GC情况、线程数

#### 2. 基础设施指标
- **数据库指标**: 连接数、查询性能、锁等待
- **缓存指标**: Redis连接数、命中率、内存使用
- **消息队列指标**: Kafka消息生产/消费速率、延迟

#### 3. 系统指标
- **资源使用**: CPU、内存、磁盘、网络
- **容器指标**: Pod状态、资源限制、重启次数

## 部署指南

### Kubernetes 部署

1. **部署监控组件**
```bash
# 部署 Prometheus 和 Grafana
kubectl apply -f k8s/monitoring.yaml

# 检查部署状态
kubectl get pods -n text-audit -l app=prometheus
kubectl get pods -n text-audit -l app=grafana
```

2. **访问监控界面**
```bash
# Prometheus
kubectl port-forward -n text-audit svc/prometheus-service 9090:9090

# Grafana
kubectl port-forward -n text-audit svc/grafana-service 3000:3000
```

### Docker Compose 部署

```bash
# 启动监控服务
docker-compose up -d prometheus grafana

# 访问地址
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin123)
```

## 配置说明

### Prometheus 配置

#### 采集目标配置
```yaml
scrape_configs:
  - job_name: 'data-collector'
    static_configs:
      - targets: ['data-collector-service:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'model-inference'
    static_configs:
      - targets: ['model-inference-service:8083']
    metrics_path: '/metrics'
    scrape_interval: 5s
```

#### 告警规则配置
- **服务可用性**: 检测服务是否正常运行
- **错误率**: 监控HTTP 5xx错误率
- **响应时间**: 监控95分位响应时间
- **资源使用**: 监控CPU和内存使用率

### Grafana 配置

#### 数据源配置
- **Prometheus**: http://prometheus-service:9090
- **自动发现**: 支持服务发现和动态配置

#### 仪表板配置
- **系统概览**: 整体系统状态和关键指标
- **服务详情**: 各微服务的详细监控
- **基础设施**: 数据库、缓存、消息队列监控
- **业务指标**: 文本处理、模型训练等业务相关指标

## 关键指标说明

### 1. 数据采集服务指标
```
# 采集速率
text_audit_collection_rate_total
text_audit_collection_success_total
text_audit_collection_error_total

# 数据源指标
text_audit_source_status{source="api|crawler|file"}
text_audit_source_latency_seconds{source}

# 队列指标
text_audit_queue_size{queue="raw_text"}
text_audit_queue_processing_time_seconds
```

### 2. 数据预处理服务指标
```
# 处理速率
text_audit_processing_rate_total
text_audit_processing_success_total
text_audit_processing_error_total

# 处理时间
text_audit_processing_duration_seconds
text_audit_feature_extraction_duration_seconds

# 缓存指标
text_audit_cache_hit_rate
text_audit_cache_size
```

### 3. 模型训练服务指标
```
# 训练任务指标
text_audit_training_jobs_total{status="running|completed|failed"}
text_audit_training_duration_seconds
text_audit_training_accuracy

# 模型指标
text_audit_model_count{type="lr|rf|cnn|lstm"}
text_audit_model_size_bytes
text_audit_model_training_samples
```

### 4. 推理服务指标
```
# 推理请求指标
text_audit_inference_requests_total
text_audit_inference_duration_seconds
text_audit_inference_accuracy

# 模型加载指标
text_audit_model_load_time_seconds
text_audit_model_memory_usage_bytes
text_audit_loaded_models_count
```

## 告警配置

### 告警规则示例

#### 服务可用性告警
```yaml
- alert: ServiceDown
  expr: up == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "服务 {{ $labels.instance }} 不可用"
    description: "服务已停止超过1分钟"
```

#### 高错误率告警
```yaml
- alert: HighErrorRate
  expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "{{ $labels.instance }} 错误率过高"
    description: "错误率: {{ $value }} 请求/秒"
```

#### 高延迟告警
```yaml
- alert: HighLatency
  expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 0.5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "{{ $labels.instance }} 响应时间过长"
    description: "95分位延迟: {{ $value }} 秒"
```

### 告警通知配置

#### 邮件通知
```yaml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@example.com'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'

receivers:
- name: 'web.hook'
  email_configs:
  - to: 'admin@example.com'
    subject: 'Text Audit Alert: {{ .GroupLabels.alertname }}'
    body: |
      {{ range .Alerts }}
      Alert: {{ .Annotations.summary }}
      Description: {{ .Annotations.description }}
      {{ end }}
```

## 性能调优

### Prometheus 优化
- **存储配置**: 根据数据量调整存储保留时间
- **采集频率**: 根据业务需求调整采集间隔
- **标签优化**: 避免高基数标签

### Grafana 优化
- **查询优化**: 使用合适的时间范围和聚合函数
- **缓存配置**: 启用查询结果缓存
- **面板优化**: 避免过多的实时查询

## 故障排除

### 常见问题

#### 1. Prometheus 无法采集指标
```bash
# 检查服务状态
kubectl get pods -n text-audit -l app=prometheus

# 检查配置
kubectl logs -n text-audit deployment/prometheus

# 检查目标状态
curl http://prometheus-service:9090/api/v1/targets
```

#### 2. Grafana 无法连接数据源
```bash
# 检查 Grafana 日志
kubectl logs -n text-audit deployment/grafana

# 测试数据源连接
curl http://prometheus-service:9090/api/v1/query?query=up
```

#### 3. 指标数据缺失
- 检查应用是否正确暴露指标端点
- 验证 Prometheus 配置中的采集目标
- 确认网络连通性和端口配置

### 监控最佳实践

1. **指标命名**: 使用一致的命名规范
2. **标签使用**: 合理使用标签，避免高基数
3. **告警设置**: 设置合理的告警阈值和时间窗口
4. **数据保留**: 根据存储容量设置合适的数据保留策略
5. **性能监控**: 定期检查监控系统本身的性能

## 扩展配置

### 自定义指标
各服务可以根据业务需求添加自定义指标：

```java
// Java 服务示例
@Component
public class CustomMetrics {
    private final Counter textProcessedCounter = Counter.build()
        .name("text_audit_texts_processed_total")
        .help("Total processed texts")
        .labelNames("source", "status")
        .register();
    
    public void incrementProcessedTexts(String source, String status) {
        textProcessedCounter.labels(source, status).inc();
    }
}
```

```go
// Go 服务示例
var (
    inferenceRequestsTotal = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "text_audit_inference_requests_total",
            Help: "Total inference requests",
        },
        []string{"model", "status"},
    )
)

func init() {
    prometheus.MustRegister(inferenceRequestsTotal)
}
```

### 集成第三方监控
- **APM工具**: 集成 Jaeger、Zipkin 等链路追踪
- **日志聚合**: 集成 ELK Stack 或 Loki
- **云监控**: 集成云厂商的监控服务