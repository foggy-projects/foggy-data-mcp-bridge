# ============================================
# Foggy MCP Data Model Service - Dockerfile
# 用于构建 MCP 语义查询服务的 Docker 镜像
# ============================================

# 阶段1: 构建
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 复制 pom 文件（用于依赖解析）
COPY pom.xml ./
COPY foggy-core/pom.xml foggy-core/
COPY foggy-bean-copy/pom.xml foggy-bean-copy/
COPY foggy-dataset/pom.xml foggy-dataset/
COPY foggy-dataset-model/pom.xml foggy-dataset-model/
COPY foggy-dataset-demo/pom.xml foggy-dataset-demo/
COPY foggy-dataset-mcp/pom.xml foggy-dataset-mcp/
COPY foggy-fsscript/pom.xml foggy-fsscript/
COPY foggy-fsscript-client/pom.xml foggy-fsscript-client/
COPY foggy-benchmark-spider2/pom.xml foggy-benchmark-spider2/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B -pl foggy-dataset-mcp -am -DskipTests

# 复制源代码
COPY foggy-core/src foggy-core/src
COPY foggy-bean-copy/src foggy-bean-copy/src
COPY foggy-dataset/src foggy-dataset/src
COPY foggy-dataset-model/src foggy-dataset-model/src
COPY foggy-dataset-demo/src foggy-dataset-demo/src
COPY foggy-dataset-mcp/src foggy-dataset-mcp/src
COPY foggy-fsscript/src foggy-fsscript/src
COPY foggy-fsscript-client/src foggy-fsscript-client/src

# 构建（跳过测试）
RUN mvn clean package -B -pl foggy-dataset-mcp -am -DskipTests

# 阶段2: 运行
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Foggy Framework <contact@foggysource.com>"
LABEL description="Foggy MCP Data Model Service - AI-powered semantic data query"
LABEL version="8.0.0-beta"

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S foggy && adduser -S foggy -G foggy

# 从构建阶段复制 JAR
COPY --from=builder /build/foggy-dataset-mcp/target/*.jar app.jar

# 设置权限
RUN chown -R foggy:foggy /app

# 切换到非 root 用户
USER foggy

# 暴露端口
EXPOSE 7108 7109

# JVM 优化参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:7108/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
