# Pivot 多维透视

> **状态**：9.0 / 9.1 同步版
> **入口**：MCP `dataset.query_model` 的 `payload.pivot`
> **适用场景**：行列维度转置、交叉分析、小计 / 总计、父级占比、基准比值

Pivot 是面向 AI 查询和前端透视表的多维分析 DSL。开启 Pivot 时，查询不再通过 `payload.columns` 描述返回列，而是通过 `payload.pivot` 描述行轴、列轴、指标和输出格式。

## 1. 最小示例

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "pivot": {
      "rows": ["region$caption"],
      "columns": ["salesDate$month"],
      "metrics": ["salesAmount"],
      "outputFormat": "grid"
    },
    "slice": [
      { "field": "salesDate$id", "op": "[)", "value": ["2025-01-01", "2026-01-01"] }
    ]
  }
}
```

## 2. 顶层互斥规则

`payload.pivot` 与普通列查询是两种不同模式：

| 组合 | 结果 |
|------|------|
| `pivot` + `columns` | 不允许，schema 报错：不能同时出现 pivot 和 columns |
| `pivot` + `timeWindow` | 不允许，schema 报错：不能同时出现 pivot 和 timeWindow |
| `pivot` + `slice` / `calculatedFields` | 允许，先过滤/计算，再进入透视 |
| `pivot` + 顶层 `orderBy` / `limit` | 不作为透视轴裁剪控制；Pivot 请使用 `pivot.rows[*].orderBy` / `limit` 或 `pivot.columns[*].orderBy` / `limit` |

需要同比、环比、滚动窗口时，使用 [DSL timeWindow](./dsl-manual.md#9-时间窗口语义层高层快捷方式)；需要行列透视时，使用本页 Pivot。两者不要在同一个请求中混用。

## 3. `pivot` 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `rows` | array | ✅ | 行轴维度，元素可以是字段名或轴对象 |
| `columns` | array | ❌ | 列轴维度，元素可以是字段名或轴对象 |
| `metrics` | array | ✅ | 度量列表，支持原生度量和受限派生指标 |
| `properties` | array | ❌ | 随维度查出的附加属性，不改变聚合粒度 |
| `options` | object | ❌ | 交叉补全、小计、总计等选项 |
| `layout` | object | ❌ | 客户端布局参数 |
| `outputFormat` | enum | ❌ | `flat` / `tree` / `grid`，默认 `flat` |

## 4. 轴定义

行轴和列轴都支持字符串短写：

```json
{
  "rows": ["region$caption", "productCategory$caption"],
  "columns": ["salesDate$month"]
}
```

需要 TopN、排序、Having 或树形展开时，使用对象形态：

```json
{
  "rows": [
    {
      "field": "region$caption",
      "limit": 10,
      "orderBy": ["salesAmount desc"],
      "having": [
        { "metric": "salesAmount", "op": ">", "value": 100000 }
      ]
    }
  ],
  "columns": [
    {
      "field": "salesDate$month",
      "limit": 12
    }
  ]
}
```

轴对象字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `field` | string | 维度字段名，必填 |
| `hierarchyMode` | `flat` / `tree` | 层级输出模式 |
| `expandDepth` | integer | 树形展开深度 |
| `limit` | integer | 该轴 TopN 限制 |
| `orderBy` | string[] | 该轴排序规则 |
| `having` | object[] | 该轴聚合后过滤，元素为 `{ metric, op, value }` |

## 5. 指标与派生指标

原生度量可直接写字符串：

```json
{
  "metrics": ["salesAmount", "orderCount"]
}
```

9.0 / 9.1 当前公开的派生指标只支持 `parentShare` 和 `baselineRatio`，不支持任意 `expr`，也不支持 `ROLLUP_TO` / `CELL_AT` / `AXIS_MEMBER` 等高级单元格函数。

### 5.1 父级占比 `parentShare`

`parentShare` 用于计算当前行值占父级聚合值的比例，当前公开轴限定为 `rows`。

```json
{
  "rows": [
    "region$caption",
    "city$caption"
  ],
  "metrics": [
    "salesAmount",
    {
      "name": "salesParentShare",
      "type": "parentShare",
      "of": "salesAmount",
      "axis": "rows",
      "level": "city$caption",
      "parentLevel": "region$caption"
    }
  ],
  "outputFormat": "flat"
}
```

边界：

- `parentShare` 只支持 `rows` 轴相邻层级，且 `of` 必须引用同一 `metrics` 中的原生可加度量。
- `parentShare` 不能与 `hierarchyMode=tree`、cascade TopN、轴级 `having` / `orderBy` / `limit` 混用。
- 超出边界时，移除 `parentShare` 返回普通透视结果；不要改用 `ROLLUP_TO`、`REMOVE(childDim)` 或自造 `expr`。

### 5.2 基准比值 `baselineRatio`

`baselineRatio` 用于计算当前列值相对列轴首项或末项的比值，当前公开轴限定为 `columns`，`baseline` 支持 `first` / `last`。

```json
{
  "rows": ["region$caption"],
  "columns": ["salesDate$month"],
  "metrics": [
    "salesAmount",
    {
      "name": "salesVsFirstMonth",
      "type": "baselineRatio",
      "of": "salesAmount",
      "axis": "columns",
      "baseline": "first"
    }
  ],
  "outputFormat": "grid"
}
```

边界：

- `baselineRatio` 只支持 `columns` 轴，`baseline` 只能是 `first` / `last`，且 `columns` 不能为空。
- `of` 必须引用同一 `metrics` 中的原生可加度量。
- `baselineRatio` 不能与 `hierarchyMode=tree`、cascade TopN、轴级 `having` / `orderBy` / `limit` 混用。
- 超出边界时，移除 `baselineRatio` 返回普通透视结果；不要改用 `CELL_AT`、`AXIS_MEMBER` 或坐标索引。

## 6. 小计、总计与骨架补全

```json
{
  "pivot": {
    "rows": ["region$caption"],
    "columns": ["salesDate$quarter"],
    "metrics": ["salesAmount"],
    "options": {
      "crossjoin": true,
      "rowSubtotals": true,
      "columnSubtotals": true,
      "grandTotal": true
    },
    "outputFormat": "grid"
  }
}
```

选项含义：

| 字段 | 说明 |
|------|------|
| `crossjoin` | 补齐行轴与列轴的组合骨架 |
| `rowSubtotals` | 输出行小计 |
| `columnSubtotals` | 输出列小计 |
| `grandTotal` | 输出总计 |

9.1 的大域传输与级联能力已有生产实现，但对超大维度域会 fail-closed；遇到域值规模超过阈值时，应收窄 `slice`、增加轴 `limit`，或改为分页明细查询。

Pivot 输出规模由轴定义和引擎基数保护控制。需要 TopN 或排序时，应放在 `rows` / `columns` 轴对象里；不要依赖顶层 `orderBy` / `limit` 对透视结果做裁剪。

## 7. 输出格式

| `outputFormat` | 说明 |
|----------------|------|
| `flat` | 扁平记录，便于 LLM 直接解释 |
| `tree` | 行轴层级树，当前主要用于 rows 层级 |
| `grid` | 透视表网格，适合前端表格渲染 |

布局参数：

```json
{
  "layout": {
    "metricPlacement": "columns"
  }
}
```

`metricPlacement` 支持 `columns` / `rows`，用于提示客户端把多指标放在列方向或行方向展示。

## 8. 治理与限制

- `systemSlice`、`fieldAccess`、`deniedColumns` 等治理能力仍然生效
- Pivot 模式下不能同时传 `columns`
- Pivot 模式下不能同时传 `timeWindow`
- 当前树形输出主要覆盖 rows 轴；列轴树形和复杂级联仍按后续版本推进
- 大域值、过深层级、不可下推的 TopN / Having 会按 fail-closed 策略处理，避免生成不可控 SQL

## 9. 能力选择建议

| 用户意图 | 推荐能力 |
|----------|----------|
| “按月份看销售额同比” | `timeWindow` |
| “按地区和月份做透视表” | `pivot` |
| “看各城市占所属大区销售额比例” | `pivot.metrics.parentShare` |
| “看每个月相对首月的变化” | `pivot.metrics.baselineRatio` |
| “先聚合再过滤 TopN，再和另一个结果合并” | Compose Query 派生 / Join / Union |

## 维护记录

| 日期 | 操作 | 备注 |
|------|------|------|
| 2026-05-03 | 新增页面 | 同步 9.0 Pivot DSL、9.1 大域和级联 guardrail，接入中文站点侧边栏 |
