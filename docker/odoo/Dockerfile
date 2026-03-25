# ============================================
# Foggy Odoo MCP - Dockerfile
# 用于构建 Odoo MCP 集成服务的 Docker 镜像
# ============================================
#
# 构建包含以下模块的镜像：
# - foggy-dataset-mcp: MCP 核心服务
# - foggy-odoo-bridge-java: 内置 Odoo 数据模型
#
# 启动时通过环境变量配置：
# - SPRING_PROFILES_ACTIVE=lite,odoo
# - SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/database
# - FOGGY_AUTH_TOKEN=your_token
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
COPY foggy-dataset-mcp/pom.xml foggy-dataset-mcp/
COPY foggy-mcp-launcher/pom.xml foggy-mcp-launcher/
COPY foggy-mcp-spi/pom.xml foggy-mcp-spi/
COPY foggy-fsscript/pom.xml foggy-fsscript/
COPY addons/foggy-fsscript-client/pom.xml addons/foggy-fsscript-client/
COPY addons/foggy-dataset-client/pom.xml addons/foggy-dataset-client/
COPY addons/foggy-dataset-mongo/pom.xml addons/foggy-dataset-mongo/
COPY addons/foggy-dataset-model-mongo/pom.xml addons/foggy-dataset-model-mongo/
COPY addons/foggy-data-viewer/pom.xml addons/foggy-data-viewer/
COPY addons/foggy-dataset-model-cache/pom.xml addons/foggy-dataset-model-cache/
COPY addons/foggy-dataset-model-vector/pom.xml addons/foggy-dataset-model-vector/
COPY addons/foggy-chart-storage-cloud/pom.xml addons/foggy-chart-storage-cloud/
COPY addons/foggy-dataset-vector/pom.xml addons/foggy-dataset-vector/
COPY addons/foggy-dataset-graphql/pom.xml addons/foggy-dataset-graphql/
COPY addons/foggy-dataset-model-preagg/pom.xml addons/foggy-dataset-model-preagg/
COPY addons/foggy-odoo-bridge-java/pom.xml addons/foggy-odoo-bridge-java/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B -pl foggy-mcp-launcher -am -DskipTests

# 复制源代码
COPY foggy-core/src foggy-core/src
COPY foggy-bean-copy/src foggy-bean-copy/src
COPY foggy-dataset/src foggy-dataset/src
COPY foggy-dataset-model/src foggy-dataset-model/src
COPY foggy-dataset-mcp/src foggy-dataset-mcp/src
COPY foggy-mcp-launcher/src foggy-mcp-launcher/src
COPY foggy-mcp-spi/src foggy-mcp-spi/src
COPY foggy-fsscript/src foggy-fsscript/src
COPY addons/foggy-fsscript-client/src addons/foggy-fsscript-client/src
COPY addons/foggy-dataset-client/src addons/foggy-dataset-client/src
COPY addons/foggy-dataset-mongo/src addons/foggy-dataset-mongo/src
COPY addons/foggy-dataset-model-mongo/src addons/foggy-dataset-model-mongo/src
COPY addons/foggy-data-viewer/src addons/foggy-data-viewer/src
COPY addons/foggy-odoo-bridge-java/src addons/foggy-odoo-bridge-java/src

# 构建（跳过测试）
RUN mvn clean package -B -pl foggy-mcp-launcher -am -DskipTests

# 阶段2: 运行
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Foggy Framework <contact@foggysource.com>"
LABEL description="Foggy Odoo MCP - AI-powered semantic data query with built-in Odoo models"
LABEL version="8.1.8.beta"

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
EXPOSE 8080

# JVM 优化参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 默认配置（可被环境变量覆盖）
ENV SPRING_PROFILES_ACTIVE="lite,odoo"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]