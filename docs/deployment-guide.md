# AI Demo 部署指南

## 概述

本文档提供了 AI Demo 系统的完整部署指南，包括环境准备、服务部署、配置管理、监控设置和故障排查等内容。

## 系统架构

AI Demo 是一个基于微服务架构的智能问答系统，主要包含以下组件：

### 核心服务
- **数据采集服务** (data-collector): 负责从知乎等平台采集训练数据
- **数据预处理服务** (data-preprocessor): 对采集的数据进行清洗和预处理
- **模型训练服务** (model-trainer): 基于 ChatGLM-6B 进行模型微调训练
- **模型推理服务** (model-inference): 提供训练后模型的推理能力

### 基础设施
- **MySQL**: 主数据库，存储业务数据
- **Redis**: 缓存服务，提供高性能数据访问
- **Kafka**: 消息队列，处理异步任务
- **MinIO**: 对象存储，存储模型文件和训练数据

### 监控和管理
- **Prometheus**: 指标收集和监控
- **Grafana**: 可视化监控面板
- **Nginx**: 反向代理和负载均衡

## 环境要求

### 硬件要求
- **CPU**: 8核心以上 (推荐16核心)
- **内存**: 32GB以上 (推荐64GB)
- **存储**: 500GB以上可用空间
- **GPU**: NVIDIA GPU (推荐RTX 3080或以上，用于模型训练)

### 软件要求
- **操作系统**: Ubuntu 20.04+ / CentOS 8+ / macOS 12+
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **Git**: 2.30+

### 网络要求
- 互联网连接 (用于下载依赖和模型)
- 开放端口: 80, 3000, 8081-8083, 9082-9083, 9090

## 快速部署

### 1. 克隆项目

```bash
git clone <repository-url>
cd AI-Demo
```

### 2. 环境检查

```bash
# 检查Docker环境
docker --version
docker-compose --version

# 检查系统资源
df -h
free -h
```

### 3. 配置权限

```bash
# 设置脚本执行权限
chmod +x scripts/*.sh

# 创建必要目录
mkdir -p logs data/mysql data/redis data/kafka data/minio
```

### 4. 一键部署

```bash
# 执行部署脚本
./scripts/deploy-production.sh
```

部署脚本会自动完成以下操作：
- 检查系统依赖
- 创建必要的目录结构
- 拉取和构建Docker镜像
- 启动所有服务
- 验证服务状态

### 5. 验证部署

```bash
# 检查服务状态
./scripts/monitor-services.sh -s

# 查看服务日志
./scripts/view-logs.sh -l
```

## 详细部署步骤

### 1. 环境准备

#### 安装Docker和Docker Compose

**Ubuntu/Debian:**
```bash
# 更新包索引
sudo apt update

# 安装Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 安装Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 将用户添加到docker组
sudo usermod -aG docker $USER
```

**CentOS/RHEL:**
```bash
# 安装Docker
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker

# 安装Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

**macOS:**
```bash
# 使用Homebrew安装
brew install docker docker-compose

# 或下载Docker Desktop
# https://www.docker.com/products/docker-desktop
```

#### 配置系统参数

```bash
# 增加文件描述符限制
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf

# 配置内核参数
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

### 2. 配置管理

#### 环境配置文件

主要配置文件位于以下位置：
- `docker-compose.production.yml`: Docker Compose配置
- `go-services/*/config/production.yaml`: 各服务的生产环境配置

#### 关键配置项

**数据库配置:**
```yaml
database:
  host: mysql
  port: 3306
  username: ai_demo
  password: your_secure_password
  database: ai_demo_db
```

**Redis配置:**
```yaml
redis:
  host: redis
  port: 6379
  password: your_redis_password
  database: 0
```

**Kafka配置:**
```yaml
kafka:
  brokers:
    - kafka:9092
  topics:
    data_collection: data-collection
    data_processing: data-processing
```

### 3. 服务启动顺序

建议按以下顺序启动服务：

1. **基础设施服务**
   ```bash
   docker-compose -f docker-compose.production.yml up -d mysql redis zookeeper kafka minio
   ```

2. **等待基础服务就绪**
   ```bash
   # 等待MySQL启动
   while ! docker-compose -f docker-compose.production.yml exec mysql mysqladmin ping -h localhost --silent; do
     echo "等待MySQL启动..."
     sleep 5
   done
   ```

3. **应用服务**
   ```bash
   docker-compose -f docker-compose.production.yml up -d data-collector data-preprocessor model-trainer model-inference
   ```

4. **监控服务**
   ```bash
   docker-compose -f docker-compose.production.yml up -d prometheus grafana nginx
   ```

### 4. 数据库初始化

```bash
# 连接到MySQL容器
docker-compose -f docker-compose.production.yml exec mysql mysql -u root -p

# 创建数据库和用户
CREATE DATABASE IF NOT EXISTS ai_demo_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'ai_demo'@'%' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON ai_demo_db.* TO 'ai_demo'@'%';
FLUSH PRIVILEGES;
```

## 配置管理

### 环境变量

主要环境变量配置：

```bash
# 数据库配置
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=ai_demo_db
MYSQL_USER=ai_demo
MYSQL_PASSWORD=your_secure_password

# Redis配置
REDIS_PASSWORD=your_redis_password

# Kafka配置
KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181

# MinIO配置
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=your_minio_password

# 应用配置
LOG_LEVEL=info
ENVIRONMENT=production
```

### 配置文件管理

1. **开发环境配置**: `config/development.yaml`
2. **测试环境配置**: `config/testing.yaml`
3. **生产环境配置**: `config/production.yaml`

### 敏感信息管理

使用Docker Secrets或环境变量管理敏感信息：

```yaml
# docker-compose.yml
secrets:
  mysql_password:
    file: ./secrets/mysql_password.txt
  redis_password:
    file: ./secrets/redis_password.txt

services:
  mysql:
    secrets:
      - mysql_password
    environment:
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/mysql_password
```

## 监控和日志

### Prometheus监控

访问地址: `http://localhost:9090`

主要监控指标：
- 系统资源使用率
- 服务响应时间
- 错误率统计
- 数据库连接数
- 消息队列积压

### Grafana仪表板

访问地址: `http://localhost:3000`
默认账号: `admin/admin`

预配置的仪表板：
- 系统概览
- 服务性能
- 数据库监控
- 消息队列监控

### 日志管理

#### 查看日志
```bash
# 查看所有服务日志
./scripts/view-logs.sh -a

# 查看特定服务日志
./scripts/view-logs.sh -v data-collector

# 实时跟踪日志
./scripts/view-logs.sh -f model-trainer

# 搜索日志
./scripts/view-logs.sh -s all "error"
```

#### 日志轮转配置

```yaml
# docker-compose.yml
services:
  data-collector:
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "5"
```

## 故障排查

### 常见问题

#### 1. 容器启动失败

**问题**: 容器无法启动或频繁重启

**排查步骤**:
```bash
# 检查容器状态
docker-compose -f docker-compose.production.yml ps

# 查看容器日志
docker-compose -f docker-compose.production.yml logs <service-name>

# 检查资源使用
docker stats
```

**常见原因**:
- 端口冲突
- 内存不足
- 配置文件错误
- 依赖服务未就绪

#### 2. 服务连接失败

**问题**: 服务间无法正常通信

**排查步骤**:
```bash
# 检查网络连接
./scripts/debug-services.sh -n

# 测试端口连通性
./scripts/debug-services.sh -p

# 检查健康检查端点
./scripts/debug-services.sh -e
```

#### 3. 性能问题

**问题**: 服务响应缓慢或超时

**排查步骤**:
```bash
# 检查资源使用情况
./scripts/debug-services.sh -r

# 运行性能基准测试
./scripts/debug-services.sh -b

# 分析错误日志
./scripts/debug-services.sh -l
```

### 调试工具

#### 1. 服务调试脚本
```bash
# 全面诊断检查
./scripts/debug-services.sh -a

# 生成诊断报告
./scripts/debug-services.sh --report

# 快速修复常见问题
./scripts/debug-services.sh --fix

# 交互式调试器
./scripts/debug-services.sh -i
```

#### 2. 监控脚本
```bash
# 查看服务状态
./scripts/monitor-services.sh -s

# 实时监控
./scripts/monitor-services.sh -w

# 查看所有信息
./scripts/monitor-services.sh -a
```

### 紧急恢复

#### 1. 服务重启
```bash
# 重启所有服务
docker-compose -f docker-compose.production.yml restart

# 重启特定服务
docker-compose -f docker-compose.production.yml restart <service-name>
```

#### 2. 数据备份恢复
```bash
# 备份数据库
docker-compose -f docker-compose.production.yml exec mysql mysqldump -u root -p ai_demo_db > backup.sql

# 恢复数据库
docker-compose -f docker-compose.production.yml exec -T mysql mysql -u root -p ai_demo_db < backup.sql
```

#### 3. 完全重新部署
```bash
# 停止所有服务
docker-compose -f docker-compose.production.yml down

# 清理数据 (谨慎操作)
docker-compose -f docker-compose.production.yml down -v

# 重新部署
./scripts/deploy-production.sh
```

## 性能优化

### 1. 资源配置优化

#### CPU和内存限制
```yaml
# docker-compose.yml
services:
  model-trainer:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 8G
        reservations:
          cpus: '2.0'
          memory: 4G
```

#### 数据库优化
```sql
-- MySQL配置优化
SET GLOBAL innodb_buffer_pool_size = 2147483648;  -- 2GB
SET GLOBAL max_connections = 200;
SET GLOBAL query_cache_size = 268435456;  -- 256MB
```

#### Redis优化
```bash
# Redis配置
maxmemory 2gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
```

### 2. 网络优化

#### Docker网络配置
```yaml
networks:
  ai-demo:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

#### 负载均衡配置
```nginx
# nginx.conf
upstream api_backend {
    least_conn;
    server data-collector:8081 weight=3;
    server data-preprocessor:8082 weight=2;
    server model-inference:9083 weight=1;
}
```

### 3. 存储优化

#### 数据卷配置
```yaml
volumes:
  mysql_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /opt/ai-demo/mysql
```

#### 缓存策略
```yaml
# Redis缓存配置
cache:
  ttl: 3600  # 1小时
  max_size: 1000
  eviction_policy: "lru"
```

## 安全配置

### 1. 网络安全

#### 防火墙配置
```bash
# Ubuntu UFW
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3000/tcp
sudo ufw enable
```

#### SSL/TLS配置
```nginx
# nginx SSL配置
server {
    listen 443 ssl http2;
    ssl_certificate /etc/ssl/certs/ai-demo.crt;
    ssl_certificate_key /etc/ssl/private/ai-demo.key;
    ssl_protocols TLSv1.2 TLSv1.3;
}
```

### 2. 访问控制

#### API认证
```yaml
# 服务配置
security:
  api_key_required: true
  jwt_secret: "your-jwt-secret"
  cors_origins:
    - "https://yourdomain.com"
```

#### 数据库安全
```sql
-- 创建只读用户
CREATE USER 'readonly'@'%' IDENTIFIED BY 'readonly_password';
GRANT SELECT ON ai_demo_db.* TO 'readonly'@'%';

-- 限制连接数
ALTER USER 'ai_demo'@'%' WITH MAX_USER_CONNECTIONS 50;
```

### 3. 数据保护

#### 数据加密
```yaml
# 数据库加密
encryption:
  enabled: true
  algorithm: "AES-256-GCM"
  key_rotation_days: 90
```

#### 备份策略
```bash
# 自动备份脚本
#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=$(date +%Y%m%d_%H%M%S)

# 数据库备份
docker-compose exec mysql mysqldump -u root -p ai_demo_db > "$BACKUP_DIR/mysql_$DATE.sql"

# 文件备份
tar -czf "$BACKUP_DIR/files_$DATE.tar.gz" data/

# 清理旧备份 (保留7天)
find "$BACKUP_DIR" -name "*.sql" -mtime +7 -delete
find "$BACKUP_DIR" -name "*.tar.gz" -mtime +7 -delete
```

## 扩展和升级

### 1. 水平扩展

#### 服务副本扩展
```bash
# 扩展数据采集服务
docker-compose -f docker-compose.production.yml up -d --scale data-collector=3

# 扩展推理服务
docker-compose -f docker-compose.production.yml up -d --scale model-inference=2
```

#### 负载均衡配置
```yaml
# docker-compose.yml
services:
  nginx:
    depends_on:
      - data-collector
    ports:
      - "80:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
```

### 2. 垂直扩展

#### 资源升级
```yaml
# 增加服务资源限制
services:
  model-trainer:
    deploy:
      resources:
        limits:
          cpus: '8.0'
          memory: 16G
```

### 3. 版本升级

#### 滚动更新
```bash
# 更新镜像
docker-compose -f docker-compose.production.yml pull

# 逐个重启服务
for service in data-collector data-preprocessor model-trainer model-inference; do
    docker-compose -f docker-compose.production.yml up -d --no-deps $service
    sleep 30
done
```

#### 回滚策略
```bash
# 标记当前版本
docker tag ai-demo/data-collector:latest ai-demo/data-collector:backup

# 回滚到上一版本
docker-compose -f docker-compose.production.yml down
docker tag ai-demo/data-collector:backup ai-demo/data-collector:latest
docker-compose -f docker-compose.production.yml up -d
```

## 维护和运维

### 1. 定期维护

#### 系统清理
```bash
# 清理Docker资源
docker system prune -f
docker volume prune -f
docker network prune -f

# 清理日志文件
find /var/log -name "*.log" -mtime +30 -delete
```

#### 健康检查
```bash
# 定期健康检查脚本
#!/bin/bash
./scripts/debug-services.sh -a > /tmp/health_check.log

if grep -q "ERROR" /tmp/health_check.log; then
    # 发送告警邮件
    mail -s "AI Demo Health Check Failed" admin@example.com < /tmp/health_check.log
fi
```

### 2. 监控告警

#### Prometheus告警规则
```yaml
# alerts.yml
groups:
  - name: ai-demo-alerts
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.instance }} is down"
      
      - alert: HighMemoryUsage
        expr: (node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / node_memory_MemTotal_bytes > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage on {{ $labels.instance }}"
```

### 3. 容灾备份

#### 数据备份策略
```bash
# 每日备份脚本
#!/bin/bash
BACKUP_DIR="/opt/backups/$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"

# 数据库备份
docker-compose exec mysql mysqldump --all-databases > "$BACKUP_DIR/mysql_full.sql"

# 配置文件备份
tar -czf "$BACKUP_DIR/configs.tar.gz" go-services/*/config/

# 上传到远程存储
aws s3 sync "$BACKUP_DIR" s3://ai-demo-backups/$(date +%Y%m%d)/
```

## 附录

### A. 端口映射表

| 服务 | 内部端口 | 外部端口 | 协议 | 说明 |
|------|----------|----------|------|------|
| MySQL | 3306 | 3306 | TCP | 数据库服务 |
| Redis | 6379 | 6379 | TCP | 缓存服务 |
| Kafka | 9092 | 9092 | TCP | 消息队列 |
| MinIO | 9000 | 9000 | HTTP | 对象存储 |
| Data Collector | 8081 | 8081 | HTTP | 数据采集API |
| Data Preprocessor | 8082 | 8082 | HTTP | 数据预处理API |
| Model Trainer | 9082 | 9082 | HTTP | 模型训练API |
| Model Inference | 9083 | 9083 | HTTP | 模型推理API |
| Prometheus | 9090 | 9090 | HTTP | 监控服务 |
| Grafana | 3000 | 3000 | HTTP | 监控面板 |
| Nginx | 80 | 80 | HTTP | 反向代理 |

### B. 环境变量参考

```bash
# 数据库配置
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=ai_demo_db
MYSQL_USER=ai_demo
MYSQL_PASSWORD=your_secure_password

# Redis配置
REDIS_PASSWORD=your_redis_password

# Kafka配置
KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092

# MinIO配置
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=your_minio_password

# 应用配置
LOG_LEVEL=info
ENVIRONMENT=production
DEBUG=false

# 模型配置
MODEL_PATH=/app/models
HUGGINGFACE_HUB_CACHE=/app/cache
CUDA_VISIBLE_DEVICES=0
```

### C. 故障排查清单

#### 服务启动问题
- [ ] 检查Docker服务状态
- [ ] 验证配置文件语法
- [ ] 检查端口占用情况
- [ ] 确认依赖服务状态
- [ ] 查看容器日志

#### 网络连接问题
- [ ] 测试端口连通性
- [ ] 检查防火墙设置
- [ ] 验证DNS解析
- [ ] 确认网络配置
- [ ] 检查代理设置

#### 性能问题
- [ ] 监控资源使用率
- [ ] 分析慢查询日志
- [ ] 检查缓存命中率
- [ ] 优化数据库索引
- [ ] 调整连接池配置

#### 数据问题
- [ ] 验证数据完整性
- [ ] 检查备份状态
- [ ] 确认权限设置
- [ ] 分析错误日志
- [ ] 测试数据恢复

### D. 联系信息

如需技术支持，请联系：
- 邮箱: support@ai-demo.com
- 文档: https://docs.ai-demo.com
- 问题反馈: https://github.com/ai-demo/issues