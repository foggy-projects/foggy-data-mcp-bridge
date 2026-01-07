# 图形渲染服务 (Chart Render Service)

基于Node.js + ECharts + Puppeteer的高性能图表渲染微服务，支持统一语义和原生ECharts图表渲染。

## 功能特性

- 🎨 **双渲染模式**: 支持统一语义和原生ECharts两种渲染模式
- 🚀 **高性能**: 基于Puppeteer + Chrome headless，支持并发渲染队列
- 🔒 **安全可靠**: 内置认证、限流、参数验证和安全防护
- 📊 **多格式支持**: 支持PNG、SVG等多种图片格式输出
- 📁 **双响应模式**: 支持JSON和文件流两种响应格式，适应不同使用场景
- 🐳 **容器化部署**: 完整的Docker支持，包含中文字体
- 📈 **监控友好**: 完善的健康检查、日志和性能监控
- 🔧 **易于集成**: 简单的REST API，与MCP数据查询服务无缝对接

## 快速开始

### 环境要求

- Node.js 18.0.0+
- Chrome/Chromium (Docker环境自动安装)

### 本地开发

```bash
# 克隆代码
git clone <repository>
cd chart-render-service

# 安装依赖
npm ci

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件设置认证token等配置

# 启动开发服务
npm run dev

# 或使用启动脚本
./scripts/start.sh start
```

### Docker部署

```bash
# 构建镜像
./scripts/docker-deploy.sh build

# 运行容器
./scripts/docker-deploy.sh run --port 3000 --auth-token your-token

# 或使用docker-compose
docker-compose up -d
```

## API接口

### 认证

所有API请求需要在Header中包含认证token：

```bash
Authorization: your-auth-token
```

### 统一语义渲染

将统一的图表语义转换为ECharts图表。

**POST** `/render/unified` - JSON响应模式
**POST** `/render/unified/stream` - 文件流响应模式

```json
{
  "unified": {
    "type": "bar",
    "title": "销售数据",
    "xField": "month",
    "yField": "sales",
    "topN": 10
  },
  "data": [
    {"month": "1月", "sales": 1200},
    {"month": "2月", "sales": 1500}
  ],
  "image": {
    "format": "png",
    "width": 800,
    "height": 600
  }
}
```

**支持的图表类型**:
- `bar` - 柱状图
- `column` - 条形图
- `line` - 折线图
- `pie` - 饼图
- `doughnut` - 环形图
- `scatter` - 散点图
- `area` - 面积图

### 原生ECharts渲染

直接使用ECharts配置进行渲染。

**POST** `/render/native` - JSON响应模式
**POST** `/render/native/stream` - 文件流响应模式

```json
{
  "engine": "echarts",
  "engine_spec": {
    "title": {"text": "销售数据"},
    "xAxis": {
      "type": "category",
      "data": ["1月", "2月", "3月"]
    },
    "yAxis": {"type": "value"},
    "series": [{
      "data": [120, 200, 150],
      "type": "bar"
    }]
  },
  "image": {
    "format": "png",
    "width": 800,
    "height": 600
  }
}
```

### 健康检查

**GET** `/healthz` - 基础健康检查
**GET** `/healthz/detailed` - 详细系统信息
**GET** `/healthz/ready` - 就绪状态检查
**GET** `/healthz/live` - 存活状态检查

### 队列状态

**GET** `/render/queue/status` - 获取渲染队列状态

## 配置说明

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `NODE_ENV` | development | 运行环境 |
| `PORT` | 3000 | 服务端口 |
| `RENDER_AUTH_TOKEN` | default-render-token | 认证token |
| `MAX_WIDTH` | 4000 | 最大图片宽度 |
| `MAX_HEIGHT` | 4000 | 最大图片高度 |
| `RENDER_TIMEOUT` | 15000 | 渲染超时时间(ms) |
| `MAX_CONCURRENT_RENDERS` | 10 | 最大并发渲染数 |

### 图片规格限制

- **尺寸范围**: 100px - 4000px
- **支持格式**: PNG, SVG
- **并发限制**: 最多10个并发渲染任务
- **超时时间**: 15秒

## 性能优化

### 渲染队列

服务内置渲染队列管理器，自动控制并发数量：

- 队列长度监控
- 任务等待时间统计
- 成功率和平均渲染时间跟踪
- 自动超时处理

### 缓存策略

- 浏览器实例复用
- 字体预加载
- 静态资源禁用

### 内存管理

- 及时关闭页面实例
- 内存使用监控
- 容器资源限制

## 部署指南

### 生产环境推荐配置

```yaml
# docker-compose.yml
version: '3.8'
services:
  chart-render:
    image: foggysource/chart-render-service:latest
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - RENDER_AUTH_TOKEN=your-production-token
      - MAX_CONCURRENT_RENDERS=20
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '2.0'
    restart: unless-stopped
```

### Docker Hub 部署

```bash
# 直接从 Docker Hub 拉取
docker pull foggysource/chart-render-service:latest

# 使用预配置的 docker-compose
docker compose -f docker-compose.hub.yml up -d
```

## 监控与运维

### 日志管理

```bash
# 查看实时日志
./scripts/start.sh logs

# Docker环境
docker logs -f chart-render-service
```

### 健康监控

```bash
# 检查服务状态
./scripts/start.sh status

# 运行测试渲染
./scripts/start.sh test
```

### 性能指标

- 渲染成功率
- 平均渲染时间
- 队列等待时间
- 内存使用率
- 并发连接数

## 故障排查

### 常见问题

1. **Chrome启动失败**
   ```bash
   # 检查Chrome安装
   google-chrome --version

   # Docker环境添加参数
   --no-sandbox --disable-setuid-sandbox
   ```

2. **中文字体显示问题**
   ```bash
   # 安装中文字体
   apt-get install fonts-noto-cjk
   ```

3. **内存不足**
   ```bash
   # 增加容器内存限制
   docker run -m 1g chart-render-service
   ```

4. **渲染超时**
   ```bash
   # 增加超时时间
   export RENDER_TIMEOUT=30000
   ```

### 调试模式

```bash
# 启用调试日志
export LOG_LEVEL=debug
export DEBUG=true

# 启动服务
./scripts/start.sh start
```

## 集成示例

### 与MCP查询服务集成

```python
# Python客户端示例
import httpx

async def render_chart(chart_config, data):
    async with httpx.AsyncClient() as client:
        response = await client.post(
            "http://chart-render-service:3000/render/unified",
            headers={"Authorization": "your-auth-token"},
            json={
                "unified": chart_config,
                "data": data,
                "image": {"format": "png", "width": 800, "height": 600}
            }
        )
        return response.json()
```

### JavaScript客户端

```javascript
// JavaScript客户端示例
const axios = require('axios');

async function renderChart(chartConfig, data) {
  const response = await axios.post('http://chart-render-service:3000/render/native', {
    engine: 'echarts',
    engine_spec: chartConfig,
    data: data,
    image: { format: 'png', width: 800, height: 600 }
  }, {
    headers: { 'Authorization': 'your-auth-token' }
  });

  return response.data;
}
```

## 开发指南

### 项目结构

```
chart-render-service/
├── src/
│   ├── server.js              # 主服务器
│   ├── config/                # 配置管理
│   ├── middleware/            # 中间件
│   ├── routes/                # 路由处理
│   ├── services/              # 业务服务
│   └── utils/                 # 工具函数
├── scripts/                   # 部署脚本
├── logs/                      # 日志目录
├── temp/                      # 临时文件
├── Dockerfile                 # Docker配置
├── docker-compose.yml         # Docker Compose配置
└── package.json               # Node.js配置
```

### 添加新渲染器

1. 创建渲染器类继承`BaseRenderer`
2. 实现`render`方法
3. 在路由中注册新端点
4. 添加对应的验证schema

### 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交变更
4. 创建Pull Request

## 许可证

MIT License

## 支持

如有问题或建议，请提交Issue或联系开发团队。