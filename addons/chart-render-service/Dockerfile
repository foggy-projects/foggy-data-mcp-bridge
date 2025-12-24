# 图形渲染服务 Dockerfile
# 支持 ECharts 图表服务端渲染
FROM node:18-slim

# 设置环境变量
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/google-chrome-stable \
    NODE_ENV=production

# 安装系统依赖、Chrome 和中文字体
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        curl \
        ca-certificates \
        fonts-liberation \
        fonts-noto-cjk \
        fonts-noto-color-emoji \
        libasound2 \
        libatk-bridge2.0-0 \
        libdrm2 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        libgbm1 \
        libgtk-3-0 \
        libxss1 \
        libnss3 \
    # 安装 Chrome
    && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && dpkg -i /tmp/chrome.deb || apt-get install -f -y \
    && rm /tmp/chrome.deb \
    # 清理
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 创建应用目录
WORKDIR /app

# 创建非root用户
RUN groupadd -r chartuser && useradd -r -g chartuser -s /bin/false chartuser

# 复制 package 文件并安装依赖
COPY package*.json ./
RUN npm ci --only=production && npm cache clean --force

# 复制应用代码
COPY src/ ./src/
COPY fonts/ ./fonts/

# 设置权限
RUN chown -R chartuser:chartuser /app

# 切换到非root用户
USER chartuser

# 暴露端口
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:3000/healthz || exit 1

# 启动应用
CMD ["node", "src/server.js"]