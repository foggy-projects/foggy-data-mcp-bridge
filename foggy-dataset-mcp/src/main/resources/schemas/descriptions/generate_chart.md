# generate_chart 工具描述

## 工具描述

从提供的数据直接生成图表，无需查询数据模型。

**注意**：chart参数格式与export_with_chart完全相同！

### 适用场景
- 已有数据需要快速可视化
- 数据分析中间结果图表生成
- 自定义数据的图表渲染

### **PIE图表示例**：
```json
{
  "data": [
    {"category": "产品A", "amount": 12500},
    {"category": "产品B", "amount": 8900},
    {"category": "产品C", "amount": 6700}
  ],
  "chart": {
    "type": "PIE",
    "title": "产品销售占比图",
    "xField": "category",  // 饼图分类字段
    "yField": "amount",    // 饼图数值字段
    "showLabel": true,
    "showLegend": true,
    "width": 800,
    "height": 600,
    "format": "png"
  }
}
```

### **LINE图表示例**：
```json
{
  "data": [
    {"date": "2025-09-20", "sales": 1200},
    {"date": "2025-09-21", "sales": 1500},
    {"date": "2025-09-22", "sales": 1300}
  ],
  "chart": {
    "type": "LINE",
    "title": "销售趋势图",
    "xAxis": {"field": "date", "label": "日期"},
    "yAxis": {"field": "sales", "label": "销售额"},
    "smooth": true
  }
}
```

**注意**：chart参数与export_with_chart工具的chart参数完全相同，请参考export_with_chart的chart参数说明。

## 参数说明

### data (必填)
- 类型: array
- 说明: 数据数组，每个元素是一个对象
- 示例: `[{"x": 1, "y": 10}, {"x": 2, "y": 20}]`

### chart (必填)
- 类型: object
- 说明: 图表配置
- **参数格式与export_with_chart的chart参数完全相同**，详细说明请参考export_with_chart工具的chart参数文档

### returnFormat (可选)
- 类型: string
- 说明: 返回格式
- 可选值:
  - URL: 返回图片URL（默认）
  - BASE64: 返回Base64编码
  - BINARY: 返回二进制数据
- 默认: URL

## 返回值说明

### URL格式返回
```json
{
  "success": true,
  "imageUrl": "https://junda-ai.obs.cn-east-5.myhuaweicloud.com/exports/charts/chart_xxx.png",
  "width": 800,
  "height": 600
}
```

### Base64格式返回
```json
{
  "success": true,
  "imageBase64": "data:image/png;base64,iVBORw0KGgo...",
  "width": 800,
  "height": 600
}
```

## 图表类型和高级配置

**与export_with_chart工具支持的图表类型和配置完全相同**，包括：
- 折线图 (LINE)、柱状图 (BAR)、饼图 (PIE)、散点图 (SCATTER)
- 多系列图表支持
- 大数值自动格式化
- 颜色主题和图例配置

详细说明请参考export_with_chart工具文档。

## 性能优化

1. **数据点限制**: 单个图表建议不超过1000个数据点
2. **数据预处理**: 大数据集建议先聚合再生成图表
3. **缓存机制**: 相同数据会使用缓存，15分钟有效

## 错误处理

### 常见错误
1. **INVALID_DATA**: 数据格式错误
   - 解决: 确保data是数组，每个元素是对象

2. **FIELD_NOT_FOUND**: 字段不存在
   - 解决: 检查xField/yField是否在数据中存在

3. **CHART_TYPE_UNSUPPORTED**: 不支持的图表类型
   - 解决: 使用支持的图表类型

4. **RENDER_FAILED**: 渲染失败
   - 解决: 检查chart-render-service是否正常运行

## 与其他工具配合

1. 使用 `query_model_v2()` 查询数据
2. 使用 `generate_chart()` 生成图表
3. 使用 `generate_excel()` 同时导出Excel

## 注意事项

- 数据格式必须是标准JSON数组
- 字段名区分大小写
- 图表类型要与数据结构匹配
- URL格式的图片默认24小时有效