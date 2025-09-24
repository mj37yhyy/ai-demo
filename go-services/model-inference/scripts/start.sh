#!/bin/bash

# 模型推理服务启动脚本
set -e

echo "=== 模型推理服务启动 ==="
echo "时间: $(date)"
echo "环境: ${ENVIRONMENT:-production}"
echo "配置文件: ${CONFIG_FILE:-config/production.yaml}"

# 设置环境变量
export ENVIRONMENT=${ENVIRONMENT:-production}
export CONFIG_FILE=${CONFIG_FILE:-config/production.yaml}
export LOG_LEVEL=${LOG_LEVEL:-info}
export GIN_MODE=${GIN_MODE:-release}

# 检查必要的目录
echo "检查目录结构..."
mkdir -p /app/logs /app/models /app/data /app/cache /tmp/model-cache

# 检查配置文件
if [ ! -f "/app/${CONFIG_FILE}" ]; then
    echo "错误: 配置文件 ${CONFIG_FILE} 不存在"
    exit 1
fi

echo "使用配置文件: /app/${CONFIG_FILE}"

# 检查Python环境
echo "检查Python环境..."
python3 --version
pip3 --version

# 检查必要的Python包
echo "检查Python依赖..."
python3 -c "import torch; print(f'PyTorch版本: {torch.__version__}')" || {
    echo "警告: PyTorch未正确安装"
}

python3 -c "import transformers; print(f'Transformers版本: {transformers.__version__}')" || {
    echo "警告: Transformers未正确安装"
}

# 设置模型缓存目录权限
echo "设置缓存目录权限..."
chmod -R 755 /tmp/model-cache 2>/dev/null || true

# 检查网络连接
echo "检查网络连接..."
if command -v curl >/dev/null 2>&1; then
    # 检查Hugging Face连接（用于下载模型）
    curl -s --connect-timeout 5 https://huggingface.co >/dev/null && echo "✓ Hugging Face连接正常" || echo "⚠ Hugging Face连接失败"
    
    # 检查依赖服务连接
    if [ -n "${MYSQL_HOST}" ]; then
        curl -s --connect-timeout 5 "${MYSQL_HOST}:${MYSQL_PORT:-3306}" >/dev/null && echo "✓ MySQL连接正常" || echo "⚠ MySQL连接失败"
    fi
    
    if [ -n "${REDIS_HOST}" ]; then
        curl -s --connect-timeout 5 "${REDIS_HOST}:${REDIS_PORT:-6379}" >/dev/null && echo "✓ Redis连接正常" || echo "⚠ Redis连接失败"
    fi
    
    if [ -n "${KAFKA_BROKERS}" ]; then
        echo "✓ Kafka配置: ${KAFKA_BROKERS}"
    fi
fi

# 预热模型（如果配置了预加载）
if [ "${PRELOAD_MODELS}" = "true" ]; then
    echo "预加载模型..."
    # 这里可以添加模型预加载逻辑
    echo "模型预加载完成"
fi

# 设置信号处理
trap 'echo "收到停止信号，正在优雅关闭..."; kill -TERM $PID; wait $PID' TERM INT

echo "=== 启动模型推理服务 ==="
echo "监听端口: 8083 (HTTP), 9083 (Metrics)"
echo "配置文件: ${CONFIG_FILE}"
echo "日志级别: ${LOG_LEVEL}"
echo "运行模式: ${GIN_MODE}"

# 启动应用
./main --config="${CONFIG_FILE}" &
PID=$!

echo "服务已启动，PID: $PID"
echo "健康检查: http://localhost:8083/health"
echo "指标监控: http://localhost:9083/metrics"

# 等待进程结束
wait $PID
echo "服务已停止"