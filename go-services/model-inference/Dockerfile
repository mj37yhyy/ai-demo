# 构建阶段
FROM golang:1.24-alpine AS builder

# 设置工作目录
WORKDIR /app

# 安装必要的包和构建工具
RUN apk add --no-cache \
    git \
    ca-certificates \
    tzdata \
    gcc \
    musl-dev \
    pkgconfig \
    curl \
    wget

# 复制go mod文件
COPY go.mod go.sum ./

# 下载依赖
RUN go mod download && go mod verify

# 复制源代码
COPY . .

# 构建应用（启用CGO以支持某些机器学习库）
RUN CGO_ENABLED=1 GOOS=linux go build -a -ldflags '-linkmode external -extldflags "-static"' -o main .

# 运行阶段 - 使用Ubuntu以支持Python和机器学习库
FROM ubuntu:22.04

# 设置非交互模式
ENV DEBIAN_FRONTEND=noninteractive

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    ca-certificates \
    tzdata \
    curl \
    wget \
    python3 \
    python3-pip \
    python3-dev \
    build-essential \
    libssl-dev \
    libffi-dev \
    libblas-dev \
    liblapack-dev \
    gfortran \
    && rm -rf /var/lib/apt/lists/*

# 安装Python依赖（ChatGLM相关）
RUN pip3 install --no-cache-dir \
    torch \
    transformers \
    accelerate \
    bitsandbytes \
    peft \
    datasets \
    tokenizers \
    sentencepiece \
    protobuf \
    numpy \
    scipy \
    scikit-learn \
    pandas \
    requests \
    flask \
    gunicorn

# 设置工作目录
WORKDIR /app

# 从构建阶段复制二进制文件
COPY --from=builder /app/main .

# 复制配置文件
COPY --from=builder /app/config ./config

# 创建必要的目录
RUN mkdir -p /app/models \
    /app/logs \
    /app/data \
    /app/cache \
    /tmp/model-cache

# 设置环境变量
ENV TZ=Asia/Shanghai
ENV PYTHONPATH=/app
ENV MODEL_CACHE_DIR=/tmp/model-cache
ENV TRANSFORMERS_CACHE=/tmp/model-cache
ENV HF_HOME=/tmp/model-cache
ENV TORCH_HOME=/tmp/model-cache

# 设置Python环境
ENV PYTHON_PATH=/usr/bin/python3
ENV PIP_PATH=/usr/bin/pip3

# 创建非root用户
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app /tmp/model-cache
USER appuser

# 暴露端口
EXPOSE 8083 9083

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/health || exit 1

# 启动脚本
COPY --chown=appuser:appuser scripts/start.sh /app/start.sh
RUN chmod +x /app/start.sh

# 运行应用
CMD ["/app/start.sh"]