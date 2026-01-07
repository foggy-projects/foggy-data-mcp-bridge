# export_with_chart

查询数据模型并生成图表。query参数格式与dataset.query_model完全相同。

## 图表类型
- LINE: 折线图（趋势），支持多系列
- BAR: 柱状图（对比），支持分组
- PIE: 饼图（占比）
- SCATTER: 散点图（相关性）

## 字段选择规则（重要）

**核心原则：图表轴/分组必须使用用户可读的字段，禁止使用ID字段**

| 场景 | 正确 | 错误 |
|------|------|------|
| 维度本身作为轴/分组 | `startTeam$caption`(网点名) | `startTeam$id` |
| 维度属性（本身可读） | `takingTimeDim$timeDayId`(日期) | - |
| 普通列存在ID/Name对 | `customerName`(道宇) | `tmsCustomerId`(TC111...) |

**判断规则**：
- `维度$id`/`维度$caption` → 用 `$caption`
- `维度$属性`（如日期、性别）→ 直接用
- 同时有ID列和Name列 → 用Name列

## 示例

**多系列折线图（年度同比）**：
```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["salesDate$year", "salesDate$month", "salesAmount"],
    "slice": [{"field": "salesDate$id", "op": "[)", "value": ["20240101", "20260101"]}],
    "groupBy": [
      {"field": "salesDate$year"},
      {"field": "salesDate$month"},
      {"field": "salesAmount", "agg": "SUM"}
    ],
    "orderBy": [{"field": "salesDate$year"}, {"field": "salesDate$month"}]
  },
  "chart": {
    "type": "LINE",
    "title": "2024-2025年月度销售对比",
    "xAxis": {"field": "salesDate$month", "label": "月份"},
    "yAxis": {"field": "salesAmount", "label": "销售额(元)"},
    "seriesField": "salesDate$year",
    "showLegend": true
  }
}
```

**饼图**：
```json
{
  "model": "TeamNoAuthEsOrderDataModel",
  "query": {
    "columns": ["startTeam$caption", "totalYfValue"],
    "groupBy": [{"field": "startTeam$caption"}, {"field": "totalYfValue", "agg": "SUM"}]
  },
  "chart": {
    "type": "PIE",
    "title": "各网点运费占比",
    "xField": "startTeam$caption",
    "yField": "totalYfValue",
    "showLabel": true,
    "showLegend": true
  }
}
```

## 参数说明

### model (必填)
数据模型名称

### query/payload (必填)
语义查询参数，包含：columns、slice/filters、groupBy、orderBy、limit
- columns容错：groupBy字段会自动补充到columns

### chart (必填，扁平结构)
| 字段 | 说明 |
|------|------|
| type | LINE/BAR/PIE/SCATTER |
| title | 图表标题 |
| xAxis/yAxis | LINE/BAR用，格式：`{"field":"字段","label":"标签"}` |
| xField/yField | PIE用，分类字段/数值字段 |
| seriesField | 多系列字段（年度同比、多网点对比） |
| showLabel | 显示数据标签 |
| showLegend | 显示图例 |
| smooth | 平滑曲线(LINE) |
| width/height | 图片尺寸，默认800x600 |
| format | png/svg，默认png |

## 返回值
```json
{
  "type": "result",
  "items": [...],
  "total": 100,
  "exports": {
    "charts": [{"url": "https://...", "type": "LINE", "title": "..."}]
  }
}
```

## 常见错误
- MODEL_NOT_FOUND → 用get_metadata()查看可用模型
- INVALID_COLUMN → 用description_model_internal()查看字段
- CHART_GENERATION_FAILED → 检查数据格式与图表类型匹配

## 注意
- 数据量建议<10万行，图表数据点<1000个
- Y轴标签建议包含单位
- 图表自动上传云存储返回URL
