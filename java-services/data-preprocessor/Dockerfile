# 使用多阶段构建
FROM gradle:8.5-jdk21 AS builder

# 设置工作目录
WORKDIR /app

# 复制构建文件
COPY build.gradle settings.gradle ./

# 下载依赖（利用Docker缓存）
RUN gradle dependencies --no-daemon || true

# 复制源代码
COPY src src

# 构建应用
RUN gradle build --no-daemon -x test

# 运行时镜像
FROM eclipse-temurin:21-jre

# 安装必要的工具和中文字体
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    && rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r textaudit && useradd -r -g textaudit textaudit

# 设置工作目录
WORKDIR /app

# 从构建阶段复制JAR文件
COPY --from=builder /app/build/libs/*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R textaudit:textaudit /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:+UseStringDeduplication"

# 切换到非root用户
USER textaudit

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]