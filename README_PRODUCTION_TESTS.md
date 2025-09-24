# AI-Demo 生产级测试套件

## 概述

本测试套件为 AI-Demo 项目提供了完整的生产级测试，涵盖了从知乎数据爬取到智能推理的完整数据处理流程。测试套件包含四个微服务的独立测试和一个端到端集成测试。

## 项目架构

```
AI-Demo/
├── python-services/
│   ├── data-crawler/          # 数据爬取服务 (Python + Flask)
│   └── data-processor/        # 数据处理服务 (Python + Flask)
├── java-services/
│   └── model-trainer/         # 模型训练服务 (Java + Spring Boot)
├── go-services/
│   └── model-inference/       # 模型推理服务 (Go + Gin)
└── 测试脚本/
    ├── end_to_end_production_test.sh    # 端到端集成测试
    ├── python-services/data-crawler/production_crawler_test.sh
    ├── python-services/data-processor/production_processor_test.sh
    ├── java-services/model-trainer/production_trainer_test.sh
    └── go-services/model-inference/production_inference_test.sh
```

## 数据流程

```
知乎数据 → 数据爬取 → 数据清洗 → 模型训练 → 智能推理
   ↓         ↓         ↓         ↓         ↓
 原始网页   结构化数据   特征向量   训练模型   预测结果
```

## 测试套件说明

### 1. 数据爬取服务测试 (`data-crawler`)

**文件**: `python-services/data-crawler/production_crawler_test.sh`

**测试内容**:
- 知乎问答数据爬取
- 反爬虫机制处理
- 数据去重和验证
- 异步任务管理
- 错误重试机制
- 数据存储和检索

**运行方式**:
```bash
cd python-services/data-crawler
./production_crawler_test.sh
```

### 2. 数据处理服务测试 (`data-processor`)

**文件**: `python-services/data-processor/production_processor_test.sh`

**测试内容**:
- HTML标签清理
- 文本标准化处理
- 关键词提取
- 情感分析
- 词向量生成
- 数据增强技术

**运行方式**:
```bash
cd python-services/data-processor
./production_processor_test.sh
```

### 3. 模型训练服务测试 (`model-trainer`)

**文件**: `java-services/model-trainer/production_trainer_test.sh`

**测试内容**:
- 传统机器学习算法 (随机森林、SVM、逻辑回归)
- 深度学习模型 (LSTM、CNN、Transformer)
- ChatGLM-6B 微调 (LoRA、P-Tuning)
- 模型评估和验证
- 性能压力测试

**运行方式**:
```bash
cd java-services/model-trainer
./production_trainer_test.sh
```

### 4. 模型推理服务测试 (`model-inference`)

**文件**: `go-services/model-inference/production_inference_test.sh`

**测试内容**:
- 模型加载和管理
- 单次文本分类
- 批量推理处理
- 情感分析
- 特征提取
- 并发性能测试

**运行方式**:
```bash
cd go-services/model-inference
./production_inference_test.sh
```

### 5. 端到端集成测试

**文件**: `end_to_end_production_test.sh`

**测试内容**:
- 完整数据流程验证
- 服务间协同测试
- 性能压力测试
- 系统稳定性验证
- 综合监控和报告

**运行方式**:
```bash
./end_to_end_production_test.sh
```

## 系统要求

### 软件依赖
- **Python 3.8+** (数据爬取和处理服务)
- **Java 21+** (模型训练服务)
- **Go 1.21+** (模型推理服务)
- **Docker & Docker Compose** (容器化部署)
- **MySQL 8.0+** (数据存储)
- **Redis 6.0+** (缓存和消息队列)

### 系统工具
- `curl` - HTTP请求测试
- `jq` - JSON数据处理
- `ab` (Apache Bench) - 性能压力测试
- `git` - 版本控制

### 硬件要求
- **CPU**: 4核心以上
- **内存**: 8GB以上
- **磁盘**: 20GB可用空间
- **网络**: 稳定的互联网连接

## 快速开始

### 1. 环境准备

```bash
# 克隆项目
git clone <repository-url>
cd AI-Demo

# 安装Python依赖
pip3 install -r python-services/data-crawler/requirements.txt
pip3 install -r python-services/data-processor/requirements.txt

# 启动基础服务
docker-compose up -d mysql redis
```

### 2. 运行完整测试

```bash
# 运行端到端测试 (推荐)
./end_to_end_production_test.sh

# 或者分别运行各服务测试
cd python-services/data-crawler && ./production_crawler_test.sh
cd ../data-processor && ./production_processor_test.sh
cd ../../java-services/model-trainer && ./production_trainer_test.sh
cd ../../go-services/model-inference && ./production_inference_test.sh
```

### 3. 查看测试结果

测试完成后，结果将保存在以下位置：
- **端到端测试**: `e2e_test_results/reports/comprehensive_test_report.html`
- **各服务测试**: `<service>/test_results/<service>_test_report.html`

## 测试配置

### 数据量配置
```bash
# 在测试脚本中修改以下变量
TOTAL_SAMPLES=1000      # 总测试样本数
BATCH_SIZE=50           # 批处理大小
CONCURRENT_USERS=10     # 并发用户数
TEST_DURATION="5m"      # 测试持续时间
```

### 服务端口配置
```bash
CRAWLER_PORT=8081       # 数据爬取服务
PROCESSOR_PORT=8082     # 数据处理服务
TRAINER_PORT=8083       # 模型训练服务
INFERENCE_PORT=8084     # 模型推理服务
```

## 测试报告

### 报告内容
- **服务健康状态**: 各微服务运行状态
- **功能测试结果**: API接口测试结果
- **性能指标**: 响应时间、吞吐量、资源使用
- **数据流验证**: 端到端数据处理结果
- **错误分析**: 异常情况和处理结果

### 报告格式
- **HTML报告**: 可视化测试结果，包含图表和详细分析
- **JSON数据**: 结构化测试数据，便于自动化分析
- **日志文件**: 详细的运行日志，便于问题排查

## 性能基准

### 响应时间目标
- **数据爬取**: < 2秒 (单页)
- **数据处理**: < 1秒 (单条记录)
- **模型推理**: < 100毫秒 (单次预测)
- **批量处理**: < 5秒 (50条记录)

### 吞吐量目标
- **数据爬取**: 10+ QPS
- **数据处理**: 20+ QPS
- **模型推理**: 100+ QPS
- **并发处理**: 支持20+并发用户

### 资源使用限制
- **内存使用**: 单服务 < 512MB
- **CPU使用**: 平均 < 50%
- **错误率**: < 1%

## 故障排除

### 常见问题

1. **服务启动失败**
   ```bash
   # 检查端口占用
   lsof -i :8081
   
   # 检查服务日志
   tail -f test_results/logs/services/<service>.log
   ```

2. **数据库连接失败**
   ```bash
   # 检查MySQL服务
   docker ps | grep mysql
   
   # 重启MySQL
   docker restart mysql-test
   ```

3. **Redis连接失败**
   ```bash
   # 检查Redis服务
   redis-cli ping
   
   # 重启Redis
   redis-server --daemonize yes
   ```

4. **测试数据生成失败**
   ```bash
   # 检查网络连接
   curl -I https://www.zhihu.com
   
   # 使用模拟数据
   export USE_MOCK_DATA=true
   ```

### 日志分析

测试过程中的所有日志都保存在 `test_results/logs/` 目录下：
- `services/` - 各服务运行日志
- `tests/` - 测试执行日志
- `system/` - 系统监控日志

## 持续集成

### CI/CD 集成

可以将测试脚本集成到CI/CD流水线中：

```yaml
# .github/workflows/production-test.yml
name: Production Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Production Tests
        run: ./end_to_end_production_test.sh
      - name: Upload Test Reports
        uses: actions/upload-artifact@v2
        with:
          name: test-reports
          path: e2e_test_results/reports/
```

### 定期测试

建议设置定期测试任务：
```bash
# 添加到crontab
0 2 * * * /path/to/AI-Demo/end_to_end_production_test.sh
```

## 扩展和定制

### 添加新的测试用例

1. 在相应服务目录下创建测试函数
2. 更新测试脚本调用新函数
3. 添加结果验证逻辑
4. 更新测试报告模板

### 自定义测试数据

1. 修改 `generate_zhihu_test_data()` 函数
2. 添加新的数据源
3. 调整数据格式和结构
4. 更新数据验证规则

### 性能调优

1. 调整并发参数
2. 优化数据库连接池
3. 配置缓存策略
4. 监控资源使用情况

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 添加测试用例
4. 提交Pull Request
5. 确保所有测试通过

## 许可证

本项目采用 MIT 许可证，详见 LICENSE 文件。

## 联系方式

如有问题或建议，请提交 Issue 或联系项目维护者。

---

**注意**: 本测试套件设计用于生产环境验证，请确保在安全的测试环境中运行，避免对生产数据造成影响。