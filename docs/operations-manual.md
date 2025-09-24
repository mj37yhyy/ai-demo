# AI Demo 操作手册

## 概述

本操作手册为 AI Demo 系统的日常运维提供详细指导，包括系统管理、监控维护、故障处理、性能优化等操作流程。

## 目录

1. [日常运维操作](#日常运维操作)
2. [系统监控](#系统监控)
3. [服务管理](#服务管理)
4. [数据管理](#数据管理)
5. [性能调优](#性能调优)
6. [故障处理](#故障处理)
7. [安全管理](#安全管理)
8. [备份恢复](#备份恢复)
9. [升级维护](#升级维护)
10. [应急响应](#应急响应)

## 日常运维操作

### 系统状态检查

#### 每日检查清单

```bash
# 1. 检查系统资源使用情况
./scripts/monitor-services.sh -s

# 2. 查看服务健康状态
./scripts/debug-services.sh -c

# 3. 检查错误日志
./scripts/view-logs.sh -e all

# 4. 验证关键功能
curl -f http://localhost:8081/health
curl -f http://localhost:8082/health
curl -f http://localhost:9082/health
curl -f http://localhost:9083/health
```

#### 每周检查清单

```bash
# 1. 生成系统诊断报告
./scripts/debug-services.sh --report

# 2. 检查磁盘空间使用
df -h
docker system df

# 3. 清理系统资源
docker system prune -f

# 4. 更新系统包 (谨慎操作)
# sudo apt update && sudo apt upgrade -y
```

#### 每月检查清单

```bash
# 1. 备份重要数据
./scripts/backup-data.sh

# 2. 检查安全更新
# sudo apt list --upgradable

# 3. 性能基准测试
./scripts/debug-services.sh -b

# 4. 日志轮转和清理
./scripts/cleanup-logs.sh
```

### 服务启停操作

#### 启动服务

```bash
# 启动所有服务
./scripts/deploy-production.sh

# 启动特定服务
docker-compose -f docker-compose.production.yml up -d <service-name>

# 分阶段启动
# 1. 基础设施
docker-compose -f docker-compose.production.yml up -d mysql redis zookeeper kafka minio

# 2. 应用服务
docker-compose -f docker-compose.production.yml up -d data-collector data-preprocessor model-trainer model-inference

# 3. 监控服务
docker-compose -f docker-compose.production.yml up -d prometheus grafana nginx
```

#### 停止服务

```bash
# 优雅停止所有服务
docker-compose -f docker-compose.production.yml down

# 停止特定服务
docker-compose -f docker-compose.production.yml stop <service-name>

# 强制停止 (紧急情况)
docker-compose -f docker-compose.production.yml kill
```

#### 重启服务

```bash
# 重启所有服务
docker-compose -f docker-compose.production.yml restart

# 重启特定服务
docker-compose -f docker-compose.production.yml restart <service-name>

# 滚动重启 (零停机)
for service in data-collector data-preprocessor model-trainer model-inference; do
    echo "重启服务: $service"
    docker-compose -f docker-compose.production.yml up -d --no-deps $service
    sleep 30
    echo "等待服务稳定..."
    sleep 30
done
```

## 系统监控

### Prometheus监控

#### 访问监控面板
- URL: http://localhost:9090
- 主要监控指标查询:

```promql
# CPU使用率
100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# 内存使用率
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100

# 磁盘使用率
100 - ((node_filesystem_avail_bytes * 100) / node_filesystem_size_bytes)

# 服务响应时间
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# 错误率
rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) * 100
```

#### 告警规则配置

```yaml
# /etc/prometheus/alert_rules.yml
groups:
  - name: system-alerts
    rules:
      - alert: HighCPUUsage
        expr: cpu_usage_percent > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "CPU使用率过高: {{ $value }}%"
      
      - alert: HighMemoryUsage
        expr: memory_usage_percent > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "内存使用率过高: {{ $value }}%"
      
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.instance }} 不可用"
      
      - alert: HighErrorRate
        expr: error_rate_percent > 5
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "错误率过高: {{ $value }}%"
```

### Grafana仪表板

#### 访问仪表板
- URL: http://localhost:3000
- 默认账号: admin/admin

#### 主要仪表板

1. **系统概览仪表板**
   - 系统资源使用情况
   - 服务状态概览
   - 关键指标趋势

2. **服务性能仪表板**
   - API响应时间
   - 请求量统计
   - 错误率分析

3. **基础设施仪表板**
   - 数据库性能
   - 消息队列状态
   - 缓存命中率

#### 自定义仪表板

```json
{
  "dashboard": {
    "title": "AI Demo 自定义监控",
    "panels": [
      {
        "title": "API响应时间",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

### 日志监控

#### 实时日志监控

```bash
# 监控所有服务日志
./scripts/view-logs.sh -f all

# 监控特定服务
./scripts/view-logs.sh -f data-collector

# 监控错误日志
./scripts/view-logs.sh -s all "ERROR|FATAL|Exception"
```

#### 日志分析

```bash
# 错误统计
./scripts/view-logs.sh -t all

# 搜索特定错误
./scripts/view-logs.sh -s all "connection timeout"

# 导出日志进行分析
./scripts/view-logs.sh -x all analysis_$(date +%Y%m%d).log
```

## 服务管理

### 数据采集服务管理

#### 服务配置

```yaml
# go-services/data-collector/config/production.yaml
server:
  host: "0.0.0.0"
  port: 8081
  timeout: 30s

collector:
  zhihu:
    enabled: true
    rate_limit: 100  # 每分钟请求数
    concurrent_workers: 5
    retry_attempts: 3
```

#### 常用操作

```bash
# 查看采集状态
curl http://localhost:8081/api/v1/collector/status

# 启动数据采集
curl -X POST http://localhost:8081/api/v1/collector/start

# 停止数据采集
curl -X POST http://localhost:8081/api/v1/collector/stop

# 查看采集统计
curl http://localhost:8081/api/v1/collector/stats
```

### 数据预处理服务管理

#### 服务配置

```yaml
# go-services/data-preprocessor/config/production.yaml
processor:
  batch_size: 100
  max_workers: 10
  timeout: 60s
  
quality_filter:
  min_length: 50
  max_length: 2000
  min_score: 0.7
```

#### 常用操作

```bash
# 查看处理队列状态
curl http://localhost:8082/api/v1/processor/queue

# 手动触发处理
curl -X POST http://localhost:8082/api/v1/processor/process

# 查看处理统计
curl http://localhost:8082/api/v1/processor/stats
```

### 模型训练服务管理

#### 训练任务管理

```bash
# 查看训练任务列表
curl http://localhost:9082/api/v1/training/jobs

# 创建新的训练任务
curl -X POST http://localhost:9082/api/v1/training/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "model_name": "chatglm-6b-zhihu",
    "dataset_id": "zhihu_qa_v1",
    "training_config": {
      "epochs": 3,
      "batch_size": 4,
      "learning_rate": 2e-5
    }
  }'

# 停止训练任务
curl -X POST http://localhost:9082/api/v1/training/jobs/{job_id}/stop

# 查看训练进度
curl http://localhost:9082/api/v1/training/jobs/{job_id}/progress
```

#### GPU资源监控

```bash
# 检查GPU使用情况
nvidia-smi

# 监控GPU使用率
watch -n 1 nvidia-smi

# 查看CUDA版本
nvcc --version
```

### 模型推理服务管理

#### 模型管理

```bash
# 查看已加载的模型
curl http://localhost:9083/api/v1/models

# 加载新模型
curl -X POST http://localhost:9083/api/v1/models/load \
  -H "Content-Type: application/json" \
  -d '{
    "model_name": "chatglm-6b-zhihu",
    "model_path": "/app/models/chatglm-6b-zhihu"
  }'

# 卸载模型
curl -X POST http://localhost:9083/api/v1/models/{model_name}/unload

# 测试推理
curl -X POST http://localhost:9083/api/v1/inference \
  -H "Content-Type: application/json" \
  -d '{
    "model_name": "chatglm-6b-zhihu",
    "prompt": "什么是人工智能？",
    "max_length": 200
  }'
```

## 数据管理

### 数据库管理

#### MySQL操作

```bash
# 连接数据库
docker-compose -f docker-compose.production.yml exec mysql mysql -u ai_demo -p ai_demo_db

# 备份数据库
docker-compose -f docker-compose.production.yml exec mysql mysqldump -u ai_demo -p ai_demo_db > backup_$(date +%Y%m%d).sql

# 恢复数据库
docker-compose -f docker-compose.production.yml exec -T mysql mysql -u ai_demo -p ai_demo_db < backup.sql

# 查看数据库状态
docker-compose -f docker-compose.production.yml exec mysql mysql -u root -p -e "SHOW PROCESSLIST;"
docker-compose -f docker-compose.production.yml exec mysql mysql -u root -p -e "SHOW ENGINE INNODB STATUS\G"
```

#### 数据库优化

```sql
-- 查看慢查询
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;

-- 分析表
ANALYZE TABLE zhihu_questions;
ANALYZE TABLE zhihu_answers;

-- 优化表
OPTIMIZE TABLE zhihu_questions;
OPTIMIZE TABLE zhihu_answers;

-- 查看索引使用情况
SHOW INDEX FROM zhihu_questions;
EXPLAIN SELECT * FROM zhihu_questions WHERE title LIKE '%AI%';
```

### Redis管理

#### Redis操作

```bash
# 连接Redis
docker-compose -f docker-compose.production.yml exec redis redis-cli

# 查看Redis信息
docker-compose -f docker-compose.production.yml exec redis redis-cli INFO

# 监控Redis命令
docker-compose -f docker-compose.production.yml exec redis redis-cli MONITOR

# 清理过期键
docker-compose -f docker-compose.production.yml exec redis redis-cli --scan --pattern "*" | xargs docker-compose -f docker-compose.production.yml exec redis redis-cli DEL
```

#### 缓存管理

```bash
# 查看缓存使用情况
redis-cli INFO memory

# 清理特定缓存
redis-cli DEL "cache:zhihu:*"

# 设置缓存过期时间
redis-cli EXPIRE "cache:model:predictions" 3600
```

### Kafka管理

#### 主题管理

```bash
# 列出所有主题
docker-compose -f docker-compose.production.yml exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# 创建主题
docker-compose -f docker-compose.production.yml exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic data-processing --partitions 3 --replication-factor 1

# 查看主题详情
docker-compose -f docker-compose.production.yml exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic data-collection

# 删除主题
docker-compose -f docker-compose.production.yml exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic old-topic
```

#### 消息监控

```bash
# 查看消费者组
docker-compose -f docker-compose.production.yml exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# 查看消费者组详情
docker-compose -f docker-compose.production.yml exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group data-processor-group

# 重置消费者偏移量
docker-compose -f docker-compose.production.yml exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group data-processor-group --reset-offsets --to-earliest --topic data-collection --execute
```

### MinIO管理

#### 对象存储操作

```bash
# 使用MinIO客户端
docker run --rm -it --network ai-demo_default minio/mc config host add myminio http://minio:9000 admin your_minio_password

# 列出存储桶
docker run --rm -it --network ai-demo_default minio/mc ls myminio

# 创建存储桶
docker run --rm -it --network ai-demo_default minio/mc mb myminio/models

# 上传文件
docker run --rm -it --network ai-demo_default -v /path/to/file:/tmp/file minio/mc cp /tmp/file myminio/models/

# 下载文件
docker run --rm -it --network ai-demo_default -v /path/to/download:/tmp minio/mc cp myminio/models/file /tmp/
```

## 性能调优

### 系统级优化

#### 内核参数调优

```bash
# 编辑 /etc/sysctl.conf
vm.swappiness=10
vm.max_map_count=262144
net.core.somaxconn=65535
net.ipv4.tcp_max_syn_backlog=65535
fs.file-max=2097152

# 应用配置
sudo sysctl -p
```

#### 文件描述符限制

```bash
# 编辑 /etc/security/limits.conf
* soft nofile 65536
* hard nofile 65536
* soft nproc 65536
* hard nproc 65536

# 编辑 /etc/systemd/system.conf
DefaultLimitNOFILE=65536
DefaultLimitNPROC=65536
```

### 应用级优化

#### Go服务优化

```yaml
# 服务配置优化
server:
  read_timeout: 30s
  write_timeout: 30s
  idle_timeout: 120s
  max_header_bytes: 1048576

database:
  max_open_conns: 100
  max_idle_conns: 10
  conn_max_lifetime: 3600s

redis:
  pool_size: 100
  min_idle_conns: 10
  dial_timeout: 5s
  read_timeout: 3s
  write_timeout: 3s
```

#### 数据库优化

```sql
-- MySQL配置优化
SET GLOBAL innodb_buffer_pool_size = 4294967296;  -- 4GB
SET GLOBAL innodb_log_file_size = 268435456;      -- 256MB
SET GLOBAL max_connections = 200;
SET GLOBAL query_cache_size = 268435456;          -- 256MB
SET GLOBAL tmp_table_size = 134217728;            -- 128MB
SET GLOBAL max_heap_table_size = 134217728;       -- 128MB
```

#### Redis优化

```bash
# Redis配置优化
maxmemory 4gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
tcp-keepalive 60
timeout 300
```

### 容器优化

#### Docker配置优化

```yaml
# docker-compose.yml 资源限制
services:
  data-collector:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
```

#### 镜像优化

```dockerfile
# 多阶段构建优化
FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

FROM alpine:latest
RUN apk --no-cache add ca-certificates tzdata
WORKDIR /root/
COPY --from=builder /app/main .
CMD ["./main"]
```

## 故障处理

### 常见故障处理流程

#### 服务无法启动

1. **检查容器状态**
   ```bash
   docker-compose -f docker-compose.production.yml ps
   ```

2. **查看容器日志**
   ```bash
   docker-compose -f docker-compose.production.yml logs <service-name>
   ```

3. **检查配置文件**
   ```bash
   # 验证YAML语法
   docker-compose -f docker-compose.production.yml config
   ```

4. **检查端口占用**
   ```bash
   netstat -tulpn | grep <port>
   lsof -i :<port>
   ```

5. **检查资源使用**
   ```bash
   df -h
   free -h
   docker system df
   ```

#### 服务响应缓慢

1. **检查系统资源**
   ```bash
   top
   htop
   iotop
   ```

2. **分析应用性能**
   ```bash
   # 查看API响应时间
   curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8081/health
   
   # curl-format.txt 内容:
   #     time_namelookup:  %{time_namelookup}\n
   #        time_connect:  %{time_connect}\n
   #     time_appconnect:  %{time_appconnect}\n
   #    time_pretransfer:  %{time_pretransfer}\n
   #       time_redirect:  %{time_redirect}\n
   #  time_starttransfer:  %{time_starttransfer}\n
   #                     ----------\n
   #          time_total:  %{time_total}\n
   ```

3. **数据库性能分析**
   ```sql
   -- 查看慢查询
   SHOW VARIABLES LIKE 'slow_query_log';
   SHOW VARIABLES LIKE 'long_query_time';
   
   -- 查看正在执行的查询
   SHOW PROCESSLIST;
   
   -- 查看表锁情况
   SHOW OPEN TABLES WHERE In_use > 0;
   ```

#### 内存泄漏处理

1. **监控内存使用**
   ```bash
   # 持续监控容器内存
   docker stats --no-stream
   
   # 查看进程内存使用
   ps aux --sort=-%mem | head
   ```

2. **分析内存泄漏**
   ```bash
   # Go应用内存分析
   curl http://localhost:8081/debug/pprof/heap > heap.prof
   go tool pprof heap.prof
   ```

3. **重启服务**
   ```bash
   # 优雅重启
   docker-compose -f docker-compose.production.yml restart <service-name>
   ```

### 数据恢复

#### 数据库恢复

```bash
# 1. 停止相关服务
docker-compose -f docker-compose.production.yml stop data-collector data-preprocessor

# 2. 备份当前数据
docker-compose -f docker-compose.production.yml exec mysql mysqldump -u ai_demo -p ai_demo_db > current_backup.sql

# 3. 恢复数据
docker-compose -f docker-compose.production.yml exec -T mysql mysql -u ai_demo -p ai_demo_db < backup.sql

# 4. 验证数据完整性
docker-compose -f docker-compose.production.yml exec mysql mysql -u ai_demo -p -e "SELECT COUNT(*) FROM zhihu_questions;"

# 5. 重启服务
docker-compose -f docker-compose.production.yml start data-collector data-preprocessor
```

#### 文件恢复

```bash
# 1. 停止服务
docker-compose -f docker-compose.production.yml down

# 2. 恢复数据卷
docker run --rm -v ai-demo_mysql_data:/data -v /backup:/backup alpine sh -c "cd /data && tar xzf /backup/mysql_data.tar.gz"

# 3. 重启服务
docker-compose -f docker-compose.production.yml up -d
```

## 安全管理

### 访问控制

#### API安全

```yaml
# 服务配置
security:
  api_key_required: true
  jwt_secret: "your-secure-jwt-secret"
  rate_limit:
    requests_per_minute: 100
    burst: 10
  cors:
    allowed_origins:
      - "https://yourdomain.com"
    allowed_methods:
      - "GET"
      - "POST"
    allowed_headers:
      - "Authorization"
      - "Content-Type"
```

#### 数据库安全

```sql
-- 创建只读用户
CREATE USER 'readonly'@'%' IDENTIFIED BY 'secure_readonly_password';
GRANT SELECT ON ai_demo_db.* TO 'readonly'@'%';

-- 创建备份用户
CREATE USER 'backup'@'localhost' IDENTIFIED BY 'secure_backup_password';
GRANT SELECT, LOCK TABLES, SHOW VIEW ON ai_demo_db.* TO 'backup'@'localhost';

-- 限制连接数
ALTER USER 'ai_demo'@'%' WITH MAX_USER_CONNECTIONS 50;
```

### 网络安全

#### 防火墙配置

```bash
# Ubuntu UFW
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow from 10.0.0.0/8 to any port 3306  # MySQL
sudo ufw allow from 10.0.0.0/8 to any port 6379  # Redis
sudo ufw enable
```

#### SSL/TLS配置

```nginx
# nginx SSL配置
server {
    listen 443 ssl http2;
    server_name yourdomain.com;
    
    ssl_certificate /etc/ssl/certs/yourdomain.com.crt;
    ssl_certificate_key /etc/ssl/private/yourdomain.com.key;
    
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    
    add_header Strict-Transport-Security "max-age=63072000" always;
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
}
```

### 日志审计

#### 访问日志

```bash
# 查看访问日志
tail -f /var/log/nginx/access.log

# 分析访问模式
awk '{print $1}' /var/log/nginx/access.log | sort | uniq -c | sort -nr | head -10

# 查找异常访问
grep "404\|500\|403" /var/log/nginx/access.log
```

#### 安全事件监控

```bash
# 监控失败的登录尝试
grep "Failed password" /var/log/auth.log

# 监控sudo使用
grep "sudo" /var/log/auth.log

# 监控文件系统变化
inotifywait -m -r -e modify,create,delete /etc/
```

## 备份恢复

### 自动备份策略

#### 数据库备份

```bash
#!/bin/bash
# backup-database.sh

BACKUP_DIR="/opt/backups/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

# 全量备份
docker-compose -f docker-compose.production.yml exec mysql mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  -u ai_demo -p ai_demo_db > "$BACKUP_DIR/full_backup_$DATE.sql"

# 压缩备份
gzip "$BACKUP_DIR/full_backup_$DATE.sql"

# 清理旧备份
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +$RETENTION_DAYS -delete

# 验证备份
if [ -f "$BACKUP_DIR/full_backup_$DATE.sql.gz" ]; then
    echo "备份成功: $BACKUP_DIR/full_backup_$DATE.sql.gz"
else
    echo "备份失败" >&2
    exit 1
fi
```

#### 文件备份

```bash
#!/bin/bash
# backup-files.sh

BACKUP_DIR="/opt/backups/files"
DATE=$(date +%Y%m%d_%H%M%S)
SOURCE_DIRS=("data" "go-services/*/config" "scripts")

mkdir -p "$BACKUP_DIR"

for dir in "${SOURCE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        tar -czf "$BACKUP_DIR/$(basename $dir)_$DATE.tar.gz" "$dir"
    fi
done

# 同步到远程存储
# rsync -av "$BACKUP_DIR/" user@backup-server:/backups/ai-demo/
# aws s3 sync "$BACKUP_DIR/" s3://ai-demo-backups/
```

### 恢复流程

#### 完整系统恢复

```bash
#!/bin/bash
# restore-system.sh

BACKUP_DATE=$1
if [ -z "$BACKUP_DATE" ]; then
    echo "用法: $0 <backup_date>"
    echo "示例: $0 20231201_120000"
    exit 1
fi

# 1. 停止所有服务
docker-compose -f docker-compose.production.yml down

# 2. 恢复数据库
gunzip -c "/opt/backups/mysql/full_backup_$BACKUP_DATE.sql.gz" | \
docker-compose -f docker-compose.production.yml exec -T mysql mysql -u ai_demo -p ai_demo_db

# 3. 恢复文件
for backup in /opt/backups/files/*_$BACKUP_DATE.tar.gz; do
    if [ -f "$backup" ]; then
        tar -xzf "$backup" -C /
    fi
done

# 4. 重启服务
docker-compose -f docker-compose.production.yml up -d

# 5. 验证恢复
sleep 30
./scripts/debug-services.sh -c
```

## 升级维护

### 版本升级流程

#### 应用升级

```bash
#!/bin/bash
# upgrade-application.sh

NEW_VERSION=$1
if [ -z "$NEW_VERSION" ]; then
    echo "用法: $0 <new_version>"
    exit 1
fi

# 1. 备份当前版本
./scripts/backup-system.sh

# 2. 拉取新版本代码
git fetch origin
git checkout "v$NEW_VERSION"

# 3. 构建新镜像
docker-compose -f docker-compose.production.yml build

# 4. 滚动更新
services=("data-collector" "data-preprocessor" "model-trainer" "model-inference")
for service in "${services[@]}"; do
    echo "升级服务: $service"
    docker-compose -f docker-compose.production.yml up -d --no-deps "$service"
    
    # 等待服务稳定
    sleep 30
    
    # 健康检查
    if ! curl -f "http://localhost:$(get_service_port $service)/health"; then
        echo "服务 $service 升级失败，开始回滚"
        rollback_service "$service"
        exit 1
    fi
done

echo "升级完成"
```

#### 数据库升级

```bash
#!/bin/bash
# upgrade-database.sh

# 1. 备份数据库
./scripts/backup-database.sh

# 2. 执行迁移脚本
for migration in migrations/*.sql; do
    echo "执行迁移: $migration"
    docker-compose -f docker-compose.production.yml exec -T mysql mysql -u ai_demo -p ai_demo_db < "$migration"
done

# 3. 验证数据完整性
docker-compose -f docker-compose.production.yml exec mysql mysql -u ai_demo -p -e "
    SELECT 
        table_name,
        table_rows,
        data_length,
        index_length
    FROM information_schema.tables 
    WHERE table_schema = 'ai_demo_db'
    ORDER BY table_name;
"
```

### 回滚策略

#### 应用回滚

```bash
#!/bin/bash
# rollback-application.sh

ROLLBACK_VERSION=$1
if [ -z "$ROLLBACK_VERSION" ]; then
    echo "用法: $0 <rollback_version>"
    exit 1
fi

# 1. 切换到回滚版本
git checkout "v$ROLLBACK_VERSION"

# 2. 重新构建镜像
docker-compose -f docker-compose.production.yml build

# 3. 重启服务
docker-compose -f docker-compose.production.yml up -d

# 4. 验证回滚
sleep 30
./scripts/debug-services.sh -c
```

## 应急响应

### 应急响应流程

#### 服务中断响应

1. **立即响应 (0-5分钟)**
   ```bash
   # 快速诊断
   ./scripts/debug-services.sh -c
   
   # 检查关键服务
   curl -f http://localhost:8081/health
   curl -f http://localhost:9083/health
   
   # 查看错误日志
   ./scripts/view-logs.sh -e all
   ```

2. **问题定位 (5-15分钟)**
   ```bash
   # 生成诊断报告
   ./scripts/debug-services.sh --report
   
   # 检查系统资源
   ./scripts/debug-services.sh -r
   
   # 分析网络连接
   ./scripts/debug-services.sh -n
   ```

3. **快速修复 (15-30分钟)**
   ```bash
   # 尝试快速修复
   ./scripts/debug-services.sh --fix
   
   # 重启失败服务
   docker-compose -f docker-compose.production.yml restart
   
   # 如果无法修复，启动备用系统
   ./scripts/activate-backup-system.sh
   ```

#### 数据丢失响应

1. **立即停止写入操作**
   ```bash
   # 停止数据采集和处理
   docker-compose -f docker-compose.production.yml stop data-collector data-preprocessor
   ```

2. **评估数据损失**
   ```bash
   # 检查数据库完整性
   docker-compose -f docker-compose.production.yml exec mysql mysqlcheck -u ai_demo -p --all-databases
   
   # 检查最后备份时间
   ls -la /opt/backups/mysql/ | tail -5
   ```

3. **数据恢复**
   ```bash
   # 从最近备份恢复
   ./scripts/restore-system.sh $(ls /opt/backups/mysql/ | grep full_backup | tail -1 | sed 's/full_backup_\|\.sql\.gz//g')
   ```

#### 安全事件响应

1. **隔离受影响系统**
   ```bash
   # 断开网络连接
   docker network disconnect ai-demo_default <container_name>
   
   # 停止可疑服务
   docker-compose -f docker-compose.production.yml stop <service-name>
   ```

2. **收集证据**
   ```bash
   # 导出日志
   ./scripts/view-logs.sh -x all security_incident_$(date +%Y%m%d_%H%M%S).log
   
   # 保存系统状态
   docker ps -a > system_state_$(date +%Y%m%d_%H%M%S).txt
   netstat -tulpn > network_state_$(date +%Y%m%d_%H%M%S).txt
   ```

3. **修复和加固**
   ```bash
   # 更新密码
   ./scripts/update-passwords.sh
   
   # 重新部署系统
   ./scripts/deploy-production.sh --security-hardened
   ```

### 联系信息

#### 紧急联系人

- **系统管理员**: admin@ai-demo.com
- **开发团队**: dev-team@ai-demo.com
- **安全团队**: security@ai-demo.com

#### 外部支持

- **云服务商支持**: [云服务商支持热线]
- **硬件供应商**: [硬件供应商支持]
- **网络服务商**: [网络服务商支持]

#### 升级路径

- **P0 (系统完全不可用)**: 立即通知所有相关人员
- **P1 (核心功能受影响)**: 30分钟内通知管理层
- **P2 (部分功能受影响)**: 2小时内通知相关团队
- **P3 (性能问题)**: 工作时间内处理

---

## 附录

### 常用命令速查

```bash
# 服务管理
docker-compose -f docker-compose.production.yml ps
docker-compose -f docker-compose.production.yml logs <service>
docker-compose -f docker-compose.production.yml restart <service>

# 监控检查
./scripts/monitor-services.sh -s
./scripts/debug-services.sh -c
./scripts/view-logs.sh -l

# 性能分析
docker stats
curl -w "@curl-format.txt" -o /dev/null -s <url>
./scripts/debug-services.sh -b

# 备份恢复
./scripts/backup-system.sh
./scripts/restore-system.sh <backup_date>

# 应急处理
./scripts/debug-services.sh --fix
docker-compose -f docker-compose.production.yml down && docker-compose -f docker-compose.production.yml up -d
```

### 配置文件位置

- Docker Compose: `docker-compose.production.yml`
- 服务配置: `go-services/*/config/production.yaml`
- Nginx配置: `nginx/nginx.conf`
- Prometheus配置: `monitoring/prometheus/prometheus.yml`
- Grafana配置: `monitoring/grafana/provisioning/`

### 日志文件位置

- 应用日志: `logs/`
- Nginx日志: `/var/log/nginx/`
- 系统日志: `/var/log/`
- Docker日志: `/var/lib/docker/containers/`