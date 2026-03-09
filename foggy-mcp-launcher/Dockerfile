# ============================================
# Foggy MCP Launcher - Dockerfile
# 用于构建 MCP 语义查询服务的 Docker 镜像
# ============================================
#
# 构建 foggy-mcp-launcher 模块，包含：
# - foggy-dataset-mcp: MCP 核心服务
# - foggy-dataset-demo: 电商演示数据模型
#
# 可通过 foggy.demo.enabled=false 禁用演示模型
#
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
COPY foggy-mcp-launcher/pom.xml foggy-mcp-launcher/
COPY foggy-mcp-spi/pom.xml foggy-mcp-spi/
COPY foggy-fsscript/pom.xml foggy-fsscript/
COPY addons/foggy-fsscript-client/pom.xml addons/foggy-fsscript-client/
COPY addons/foggy-dataset-client/pom.xml addons/foggy-dataset-client/
COPY addons/foggy-dataset-mongo/pom.xml addons/foggy-dataset-mongo/
COPY addons/foggy-dataset-model-mongo/pom.xml addons/foggy-dataset-model-mongo/
COPY addons/foggy-data-viewer/pom.xml addons/foggy-data-viewer/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B -pl foggy-mcp-launcher -am -DskipTests

# 复制源代码
COPY foggy-core/src foggy-core/src
COPY foggy-bean-copy/src foggy-bean-copy/src
COPY foggy-dataset/src foggy-dataset/src
COPY foggy-dataset-model/src foggy-dataset-model/src
COPY foggy-dataset-demo/src foggy-dataset-demo/src
COPY foggy-dataset-mcp/src foggy-dataset-mcp/src
COPY foggy-mcp-launcher/src foggy-mcp-launcher/src
COPY foggy-mcp-spi/src foggy-mcp-spi/src
COPY foggy-fsscript/src foggy-fsscript/src
COPY addons/foggy-fsscript-client/src addons/foggy-fsscript-client/src
COPY addons/foggy-dataset-client/src addons/foggy-dataset-client/src
COPY addons/foggy-dataset-mongo/src addons/foggy-dataset-mongo/src
COPY addons/foggy-dataset-model-mongo/src addons/foggy-dataset-model-mongo/src
COPY addons/foggy-data-viewer/src addons/foggy-data-viewer/src

# 构建（跳过测试）
RUN mvn clean package -B -pl foggy-mcp-launcher -am -DskipTests

# 阶段2: 运行
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Foggy Framework <contact@foggysource.com>"
LABEL description="Foggy MCP Launcher - AI-powered semantic data query with demo models"
LABEL version="8.1.7.beta"

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S foggy && adduser -S foggy -G foggy

# 从构建阶段复制 JAR
COPY --from=builder /build/foggy-mcp-launcher/target/*.jar app.jar

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
