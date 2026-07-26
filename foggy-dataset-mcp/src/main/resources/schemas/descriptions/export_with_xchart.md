# dataset.export_with_xchart

执行一个受治理的 `dataset.query_model` 查询，并在当前 Java 进程内使用 XChart 生成图片。常规图表导出优先使用本工具；它不依赖浏览器、Node.js 或额外渲染服务。

`chart.config` 使用 XChart Adapter JSON，直接采用 XChart 的 `CategoryChart`、`XYChart`、`PieChart`、builder、styler 和 series 词汇。该 Adapter 是 Foggy 对 XChart Java API 的受控 JSON 映射，不是跨引擎通用配置。

数据规则：

- 图表数据只能来自查询最终返回的 `items`，请求中不要传 `data` 或 `engine`。
- CategoryChart/XYChart 的 series 使用 `xField`、`yField`，可选 `seriesField` 动态拆分系列。
- PieChart 使用顶层 `nameField`、`valueField`。
- 不接受 `xData`、`yData`、`series.value` 或其他内嵌数据。
- timeWindow 派生字段可以直接绑定；合法 `null` 会保留为数据缺口，不补零。

查询模式：

- 普通 DSL 和 `route=DSL_CTE`：只绑定实际执行后最终 `items` 中的字段或 CTE output 别名。
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
      "chartType": "CategoryChart",
      "title": "月度销售额",
      "styler": {
        "legendVisible": true,
        "defaultSeriesRenderStyle": "Bar"
      },
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
      "width": 1200,
      "height": 700,
      "format": "png"
    }
  }
}
```
