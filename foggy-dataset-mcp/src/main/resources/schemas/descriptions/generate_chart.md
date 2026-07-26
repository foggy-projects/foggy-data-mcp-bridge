# chart.generate

直接使用调用方提供的数据生成图片。`data` 必填且至少包含一行；默认使用 `xchart`，只有明确需要 ECharts 原生能力时才设置 `engine: "echarts"`。

本工具使用引擎原生表达，不做配置转换：

- XChart：`config` 使用受控 XChart Adapter JSON。CategoryChart/XYChart series 使用 `xField`、`yField` 和可选 `seriesField` 绑定顶层 `data`；PieChart 使用 `nameField`、`valueField`。
- ECharts：`config` 使用原生 ECharts Option；顶层 `data` 会被注入单个 `dataset.source`，Option 使用 `dataset.dimensions` 和 `series.encode` 绑定。

任何配置都不能嵌入第二份数据：

- XChart 不接受 `xData`、`yData` 或 `series.value`。
- ECharts 不接受已有 `dataset.source`、`series[*].data`、`xAxis.data`、dataset 数组或 `transform`。

XChart 示例：

```json
{
  "data": [
    {"month": "1月", "amount": 12000},
    {"month": "2月", "amount": 15000}
  ],
  "config": {
    "chartType": "CategoryChart",
    "title": "月度销售额",
    "series": [
      {
        "name": "销售额",
        "xField": "month",
        "yField": "amount",
        "renderStyle": "Bar"
      }
    ]
  },
  "image": {
    "width": 1000,
    "height": 600,
    "format": "png"
  }
}
```

ECharts 示例：

```json
{
  "engine": "echarts",
  "data": [
    {"month": "1月", "amount": 12000},
    {"month": "2月", "amount": 15000}
  ],
  "config": {
    "title": {"text": "月度销售额"},
    "dataset": {
      "dimensions": ["month", "amount"]
    },
    "xAxis": {"type": "category"},
    "yAxis": {"type": "value"},
    "series": [
      {
        "type": "bar",
        "encode": {"x": "month", "y": "amount"}
      }
    ]
  },
  "image": {
    "width": 1000,
    "height": 600,
    "format": "png"
  }
}
```
