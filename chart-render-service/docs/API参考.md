# 图形渲染服务 API 参考

## 基础信息

- **Base URL**: `http://localhost:3001`
- **认证方式**: HTTP Header
- **Content-Type**: `application/json`

## 认证

所有API请求需要在Header中包含认证token：

```
Authorization: default-render-token
```

## 端点列表

| 方法 | 端点 | 描述 |
|------|------|------|
| POST | `/render/native` | 原生ECharts渲染 (JSON响应) |
| POST | `/render/native/stream` | 原生ECharts渲染 (文件流响应) |
| POST | `/render/unified` | 统一语义渲染 (JSON响应) |
| POST | `/render/unified/stream` | 统一语义渲染 (文件流响应) |
| GET | `/render/queue/status` | 获取队列状态 |
| GET | `/healthz` | 基础健康检查 |
| GET | `/healthz/detailed` | 详细健康信息 |
| GET | `/healthz/ready` | 就绪状态检查 |
| GET | `/healthz/live` | 存活状态检查 |

## API详细说明

### POST /render/native

原生ECharts配置渲染

**请求参数**:

```typescript
interface NativeRenderRequest {
  engine: 'echarts';                    // 渲染引擎
  engine_spec: EChartsOption;           // ECharts配置对象
  data?: any[];                         // 可选数据数组
  image: ImageSpec;                     // 图片规格
}

interface ImageSpec {
  format?: 'png' | 'svg';              // 图片格式，默认png
  width?: number;                       // 宽度，默认800，范围100-4000
  height?: number;                      // 高度，默认600，范围100-4000
  quality?: number;                     // 质量，默认1.0，仅JPEG有效
  backgroundColor?: string;             // 背景色，默认#ffffff
}
```

**响应**:

```typescript
interface RenderResponse {
  success: boolean;                     // 是否成功
  renderTime: number;                   // 渲染耗时(ms)
  format: string;                       // 图片格式
  size: {
    width: number;
    height: number;
  };
  image: string;                        // Base64编码的图片
  mimeType: string;                     // MIME类型
}
```

**示例**:

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "示例图表"},
      "xAxis": {"type": "category", "data": ["A", "B", "C"]},
      "yAxis": {"type": "value"},
      "series": [{"data": [100, 200, 300], "type": "bar"}]
    },
    "image": {"format": "png", "width": 800, "height": 600}
  }'
```

### POST /render/native/stream

原生ECharts配置渲染 - 文件流模式

**描述**: 与 `/render/native` 接口相同的输入参数，但直接返回图片文件流而不是JSON响应。适用于需要直接获取图片文件的场景，避免Base64编码/解码的开销。

**请求参数**: 与 `/render/native` 完全相同

**响应**: 直接返回图片文件流，包含以下响应头：

```
Content-Type: image/png (或 image/svg+xml)
Content-Length: [文件大小]
Content-Disposition: inline; filename="chart_[类型]_[时间戳].png"
Cache-Control: public, max-age=3600
X-Render-Time: [渲染时间ms]
X-Chart-Type: [图表类型]
X-Image-Format: [图片格式]
X-Image-Size: [宽度]x[高度]
```

**示例**:

```bash
curl -X POST http://localhost:3001/render/native/stream \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "示例图表"},
      "xAxis": {"type": "category", "data": ["A", "B", "C"]},
      "yAxis": {"type": "value"},
      "series": [{"data": [100, 200, 300], "type": "bar"}]
    },
    "image": {"format": "png", "width": 800, "height": 600}
  }' \
  --output chart.png
```

### POST /render/unified

统一语义渲染

**请求参数**:

```typescript
interface UnifiedRenderRequest {
  unified: UnifiedChartSpec;            // 统一图表语义
  data: any[];                          // 数据数组
  image: ImageSpec;                     // 图片规格
}

interface UnifiedChartSpec {
  type: 'bar' | 'column' | 'line' | 'pie' | 'doughnut' | 'scatter' | 'area';
  title?: string;                       // 图表标题
  xField?: string;                      // X轴字段名
  yField?: string;                      // Y轴字段名
  seriesField?: string;                 // 系列分组字段名(多系列图表)
  nameField?: string;                   // 名称字段(饼图)
  valueField?: string;                  // 数值字段(饼图)
  topN?: number;                        // TopN限制，默认100，最大1000
  color?: string;                       // 主色调
  smooth?: boolean;                     // 平滑曲线(折线图)
  showLegend?: boolean;                 // 显示图例
  showLabel?: boolean;                  // 显示标签
}
```

**示例**:

### 单系列图表
```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "bar",
      "title": "销售数据",
      "xField": "product",
      "yField": "sales",
      "topN": 5
    },
    "data": [
      {"product": "产品A", "sales": 1000},
      {"product": "产品B", "sales": 800},
      {"product": "产品C", "sales": 600}
    ],
    "image": {"format": "png", "width": 600, "height": 400}
  }'
```

### 多系列图表 (使用seriesField)
```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "line",
      "title": "商品销售趋势对比",
      "xField": "month",
      "yField": "amount",
      "seriesField": "product",
      "smooth": true,
      "showLegend": true
    },
    "data": [
      {"month": "2024-01", "product": "iPhone", "amount": 85000},
      {"month": "2024-02", "product": "iPhone", "amount": 92000},
      {"month": "2024-01", "product": "MacBook", "amount": 65000},
      {"month": "2024-02", "product": "MacBook", "amount": 71000},
      {"month": "2024-01", "product": "iPad", "amount": 42000},
      {"month": "2024-02", "product": "iPad", "amount": 48000}
    ],
    "image": {"format": "png", "width": 1200, "height": 600}
  }'
```

### POST /render/unified/stream

统一语义渲染 - 文件流模式

**描述**: 与 `/render/unified` 接口相同的输入参数，但直接返回图片文件流而不是JSON响应。

**请求参数**: 与 `/render/unified` 完全相同

**响应**: 直接返回图片文件流，包含以下响应头：

```
Content-Type: image/png (或 image/svg+xml)
Content-Length: [文件大小]
Content-Disposition: inline; filename="chart_[类型]_[时间戳].png"
Cache-Control: public, max-age=3600
X-Render-Time: [渲染时间ms]
X-Chart-Type: [图表类型]
X-Image-Format: [图片格式]
X-Image-Size: [宽度]x[高度]
```

**示例**:

```bash
curl -X POST http://localhost:3001/render/unified/stream \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "bar",
      "title": "销售数据",
      "xField": "product",
      "yField": "sales"
    },
    "data": [
      {"product": "产品A", "sales": 1000},
      {"product": "产品B", "sales": 800}
    ],
    "image": {"format": "png", "width": 600, "height": 400}
  }' \
  --output chart.png
```

### GET /render/queue/status

获取渲染队列状态

**响应**:

```typescript
interface QueueStatus {
  success: boolean;
  timestamp: string;
  queue: {
    maxConcurrent: number;              // 最大并发数
    running: number;                    // 正在运行的任务
    queued: number;                     // 排队中的任务
    completed: number;                  // 已完成任务数
    failed: number;                     // 失败任务数
    total: number;                      // 总任务数
    successRate: number;                // 成功率(%)
    avgWaitTime: string;                // 平均等待时间
    avgRenderTime: string;              // 平均渲染时间
  };
}
```

### GET /healthz

基础健康检查

**响应**:

```json
{
  "status": "healthy",
  "timestamp": "2024-09-19T16:30:00.000Z",
  "uptime": "3600s",
  "version": "1.0.0",
  "environment": "development"
}
```

### GET /healthz/detailed

详细健康信息

**响应包含**:
- 系统信息(平台、架构、CPU、内存)
- 进程信息(PID、内存使用、CPU使用)
- 服务配置
- 依赖状态(Puppeteer、ECharts)

### GET /healthz/ready

就绪状态检查，包含依赖检查和资源状态

### GET /healthz/live

简单的存活检查

## 错误响应

```typescript
interface ErrorResponse {
  error: string;                        // 错误类型
  message: string;                      // 错误描述
  timestamp: string;                    // 时间戳
  requestId: string;                    // 请求ID
  stack?: string;                       // 错误堆栈(开发环境)
}
```

**常见错误码**:

| 状态码 | 错误类型 | 说明 |
|--------|----------|------|
| 400 | Bad Request | 请求参数错误 |
| 401 | Unauthorized | 认证失败 |
| 403 | Forbidden | 权限不足 |
| 408 | Request Timeout | 请求超时 |
| 413 | Payload Too Large | 请求体过大 |
| 422 | Unprocessable Entity | 渲染错误 |
| 429 | Too Many Requests | 请求频率过高 |
| 500 | Internal Server Error | 服务器内部错误 |

## 限制和约束

### 图片规格限制
- **尺寸范围**: 100px - 4000px
- **支持格式**: PNG, SVG
- **并发限制**: 最多10个并发渲染任务
- **超时时间**: 15秒

### 数据限制
- **TopN范围**: 10 - 1000
- **数据点建议**: 单个系列不超过10000个点
- **系列数量**: 建议不超过50个系列

### 安全限制
- **配置复杂度**: 防止过度嵌套的配置对象
- **内存限制**: 单个请求不超过10MB
- **请求频率**: 每分钟最多100个请求

## ECharts配置参考

### 基础配置结构

```javascript
{
  title: {
    text: '图表标题',
    textStyle: { fontSize: 16, color: '#333' }
  },
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255,255,255,0.9)'
  },
  legend: {
    data: ['系列1', '系列2']
  },
  grid: {
    left: '10%',
    right: '10%',
    top: '15%',
    bottom: '15%'
  },
  xAxis: {
    type: 'category',
    data: ['分类1', '分类2', '分类3']
  },
  yAxis: {
    type: 'value'
  },
  series: [{
    name: '系列1',
    type: 'bar',
    data: [100, 200, 300]
  }]
}
```

### 常用图表类型配置

#### 柱状图
```javascript
{
  xAxis: { type: 'category', data: ['A', 'B', 'C'] },
  yAxis: { type: 'value' },
  series: [{
    type: 'bar',
    data: [120, 200, 150],
    itemStyle: { color: '#5470c6' }
  }]
}
```

#### 折线图
```javascript
{
  xAxis: { type: 'category', data: ['1月', '2月', '3月'] },
  yAxis: { type: 'value' },
  series: [{
    type: 'line',
    data: [820, 932, 901],
    smooth: true
  }]
}
```

#### 饼图
```javascript
{
  series: [{
    type: 'pie',
    radius: '60%',
    data: [
      { name: '类别A', value: 335 },
      { name: '类别B', value: 310 },
      { name: '类别C', value: 234 }
    ]
  }]
}
```

#### 散点图
```javascript
{
  xAxis: { type: 'value' },
  yAxis: { type: 'value' },
  series: [{
    type: 'scatter',
    data: [[10, 20], [15, 25], [20, 30]]
  }]
}
```

## 性能优化建议

1. **数据预处理**: 在客户端完成数据聚合和格式化
2. **合理尺寸**: 根据使用场景选择合适的图片尺寸
3. **批量处理**: 避免短时间内大量单个请求
4. **缓存结果**: 相同配置的图表可以缓存结果
5. **监控队列**: 使用队列状态接口监控服务负载

## 集成示例

### 在Web应用中使用

```javascript
class ChartRenderer {
  constructor(baseUrl = 'http://localhost:3001', token = 'default-render-token') {
    this.baseUrl = baseUrl;
    this.token = token;
  }

  async render(config) {
    const response = await fetch(`${this.baseUrl}/render/native`, {
      method: 'POST',
      headers: {
        'Authorization': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(config)
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(`渲染失败: ${error.message}`);
    }

    return await response.json();
  }

  async renderToBlob(config) {
    const result = await this.render(config);
    const binaryString = atob(result.image);
    const bytes = new Uint8Array(binaryString.length);

    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    return new Blob([bytes], { type: result.mimeType });
  }
}

// 使用示例
const renderer = new ChartRenderer();
const chartBlob = await renderer.renderToBlob({
  engine: 'echarts',
  engine_spec: { /* ECharts配置 */ },
  image: { format: 'png', width: 800, height: 600 }
});
```

---

更多详细信息请参考[完整使用指南](./用户使用指南.md)。