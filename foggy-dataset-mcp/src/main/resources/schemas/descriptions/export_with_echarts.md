# dataset.export_with_echarts

执行一个受治理的 `dataset.query_model` 查询，并把原生 ECharts Option 发送给可选图表渲染服务生成图片。仅在用户明确要求 ECharts，或需要 ECharts 私有能力时使用；常规图表优先使用 `dataset.export_with_xchart`。

`chart.config` 就是原生 ECharts Option。Foggy 不把它转换成 XChart，也不提供通用 chart config。查询最终返回的 `items` 会被确定性注入单个 `dataset.source`。

数据规则：

- 请求中不要传 `data` 或 `engine`。
- Option 使用原生 `dataset.dimensions`、`series.encode` 等能力绑定字段。
- 不接受已有 `dataset.source`、`series[*].data` 或 `xAxis.data`。
- 首期不支持 dataset 数组、`transform` 或 dataset 链。
- 显式 ECharts 渲染失败时不会自动回退到 XChart。

查询模式：

- 普通 DSL 和 `route=DSL_CTE`：只使用实际执行后最终 `items`。
- timeWindow 派生字段和合法 `null` 原样进入 `dataset.source`。
- Pivot：未指定 `outputFormat` 时自动使用 `flat`；显式 `tree`、`grid` 或 `hierarchyMode=tree` 会被拒绝。小计和总计元数据行会被过滤。
- 不支持 ComposeScript 多 plan。

请求示例：

```json
{
  "model": "SalesQueryModel",
  "payload": {
    "columns": ["month", "amount"]
  },
  "chart": {
    "config": {
      "title": {"text": "月度销售额"},
      "tooltip": {"trigger": "axis"},
      "dataset": {
        "dimensions": ["month", "amount"]
      },
      "xAxis": {"type": "category"},
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "销售额",
          "type": "bar",
          "encode": {"x": "month", "y": "amount"}
        }
      ]
    },
    "image": {
      "width": 1200,
      "height": 700,
      "format": "png"
    }
  }
}
```
