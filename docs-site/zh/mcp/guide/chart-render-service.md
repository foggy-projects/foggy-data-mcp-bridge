# 图表渲染服务

图表渲染服务（chart-render-service）是一个独立的 Node.js 微服务，用于将查询数据渲染为可视化图表。

## 功能特性

- 支持多种图表类型：折线图、柱状图、饼图、散点图、面积图
- 支持多系列图表（年度同比、多维度对比）
- 响应式布局，自动适配不同尺寸
- 输出 PNG/SVG 格式
- 自动上传至云存储并返回 URL

## 安装方式

### 方式一：Docker Hub（推荐）

使用官方预构建镜像，无需本地构建：

```bash
# 创建 docker-compose.yml
cat > docker-compose.yml << 'EOF'
services:
  chart-render:
    image: foggysource/chart-render-service:latest
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - RENDER_TOKEN=your-token-here
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/healthz"]
      interval: 30s
      timeout: 10s
      retries: 3
EOF

# 启动服务
docker compose up -d
```

### 方式二：本地构建

如需自定义或开发调试：

```bash
# 进入服务目录
cd addons/chart-render-service

# 构建并启动
docker compose up -d --build

# 查看日志
docker compose logs -f
```

## 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `PORT` | 服务端口 | 3000 |
| `NODE_ENV` | 运行环境 | development |
| `RENDER_TOKEN` | 访问令牌 | default-render-token |
| `OSS_*` | 云存储配置 | - |

### 云存储配置（可选）

如需将图表上传至云存储：

```yaml
environment:
  - OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
  - OSS_ACCESS_KEY_ID=your-access-key
  - OSS_ACCESS_KEY_SECRET=your-secret
  - OSS_BUCKET=your-bucket
  - OSS_REGION=oss-cn-hangzhou
```

## 与 MCP 服务集成

在 MCP 服务的配置中指定图表渲染服务地址：

```yaml
# application.yml
foggy:
  mcp:
    chart:
      render-url: http://localhost:3000
      render-token: your-token-here
```

## 验证服务

```bash
# 健康检查
curl http://localhost:3000/healthz

# 测试渲染（需要 POST 图表配置）
curl -X POST http://localhost:3000/render/unified \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-token-here" \
  -d '{
    "unified": {
      "type": "line",
      "title": "测试图表",
      "xField": "month",
      "yField": "value"
    },
    "data": [
      {"month": "1月", "value": 100},
      {"month": "2月", "value": 150}
    ]
  }'
```

## 支持的图表类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `line` | 折线图 | 趋势分析、时间序列 |
| `bar` | 横向柱状图 | 分类对比 |
| `column` | 纵向柱状图 | 分类对比 |
| `pie` | 饼图 | 占比分析 |
| `doughnut` | 环形图 | 占比分析 |
| `scatter` | 散点图 | 相关性分析 |
| `area` | 面积图 | 趋势+累计 |

## 多系列图表

通过 `seriesField` 参数支持多系列图表：

```json
{
  "unified": {
    "type": "line",
    "title": "2024-2025年月度销售对比",
    "xField": "month",
    "yField": "amount",
    "seriesField": "year",
    "showLegend": true
  },
  "data": [
    {"year": "2024", "month": "1", "amount": 1000},
    {"year": "2024", "month": "2", "amount": 1200},
    {"year": "2025", "month": "1", "amount": 1100},
    {"year": "2025", "month": "2", "amount": 1400}
  ]
}
```

## 故障排查

### 服务无法启动

```bash
# 检查端口占用
netstat -tlnp | grep 3000

# 查看详细日志
docker compose logs chart-render
```

### 图表渲染失败

1. 检查数据格式是否正确
2. 确认字段名与配置匹配
3. 查看服务日志获取详细错误信息

### 中文显示乱码

确保容器内安装了中文字体，官方镜像已包含 Noto Sans CJK 字体。

## 更新镜像

```bash
# 拉取最新镜像
docker pull foggysource/chart-render-service:latest

# 重启服务
docker compose down
docker compose up -d
```
