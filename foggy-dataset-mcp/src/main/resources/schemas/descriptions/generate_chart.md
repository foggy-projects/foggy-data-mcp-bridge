# generate_chart

从提供的数据直接生成图表，无需查询数据模型。chart参数格式与export_with_chart完全相同。

## 参数

### data (必填)
数据数组：`[{"x": 1, "y": 10}, {"x": 2, "y": 20}]`

### chart (必填)
图表配置，格式同export_with_chart。

### returnFormat (可选)
- `URL`: 返回图片URL（默认，24小时有效）
- `BASE64`: 返回Base64编码
- `BINARY`: 返回二进制数据

## 示例

**饼图**：
```json
{
  "data": [{"category": "产品A", "amount": 12500}, {"category": "产品B", "amount": 8900}],
  "chart": {"type": "PIE", "title": "销售占比", "xField": "category", "yField": "amount"}
}
```

**折线图**：
```json
{
  "data": [{"date": "2025-09-20", "sales": 1200}, {"date": "2025-09-21", "sales": 1500}],
  "chart": {"type": "LINE", "title": "销售趋势", "xAxis": {"field": "date"}, "yAxis": {"field": "sales"}}
}
```

## 返回值
```json
{"success": true, "imageUrl": "https://...", "width": 800, "height": 600}
```

## 常见错误
- INVALID_DATA → 确保data是对象数组
- FIELD_NOT_FOUND → 检查xField/yField是否存在
- RENDER_FAILED → 检查chart-render-service状态

## 注意
- 数据点建议<1000个
- 相同数据缓存15分钟
- 图表类型详见export_with_chart
