# 使用多阶段构建
FROM gradle:8.5-jdk21 AS builder

# 设置工作目录
WORKDIR /app

# 复制构建文件
COPY build.gradle settings.gradle ./
COPY gradle gradle

# 下载依赖（利用Docker缓存）
RUN gradle dependencies --no-daemon

# 复制源代码
COPY src src

# 构建应用
RUN gradle build --no-daemon -x test

# 运行时镜像
FROM openjdk:21-jre-slim

# 安装必要的工具和中文字体
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r textaudit && useradd -r -g textaudit textaudit

# 设置工作目录
WORKDIR /app

# 从构建阶段复制JAR文件
COPY --from=builder /app/build/libs/*.jar app.jar

# 创建模型和日志目录
RUN mkdir -p /app/models /app/models/backup /app/models/temp /app/logs && \
    chown -R textaudit:textaudit /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置JVM参数（针对机器学习优化）
ENV JAVA_OPTS="-Xms1g -Xmx8g -XX:+UseG1GC -XX:G1HeapRegionSize=32m -XX:+UseStringDeduplication -XX:MaxDirectMemorySize=2g"

# 切换到非root用户
USER textaudit

# 暴露端口
EXPOSE 8082 9092

# 健康检查
HEALTHCHECK --interval=30s --timeout=15s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8082/api/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]