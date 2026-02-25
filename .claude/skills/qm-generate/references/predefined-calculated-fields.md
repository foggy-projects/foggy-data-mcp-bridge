# QM 预定义计算字段

QM 的 `columnGroups` 中除了 `ref` 引用 TM 字段外，还可以直接定义计算字段。查询时直接引用名称即可，无需在 DSL 的 `calculatedFields` 中重复定义。

## 公式计算字段

```javascript
{
    caption: '高级分析',
    items: [
        {
            name: 'profitRate',
            caption: '利润率(%)',
            formula: 'profitAmount / salesAmount * 100',
            type: 'NUMBER'
        }
    ]
}
```

- `formula` 中引用模型中的度量名称
- `type` 指定返回类型：`NUMBER`, `INTEGER`, `TEXT` 等

## 窗口函数字段

### 排名

```javascript
{
    name: 'salesRank',
    caption: '品类销售排名',
    formula: 'RANK()',
    partitionBy: ['product$categoryName'],
    windowOrderBy: [{ field: 'salesAmount', dir: 'desc' }],
    type: 'INTEGER'
}
```

### 移动平均

```javascript
{
    name: 'ma7',
    caption: '7日移动平均',
    formula: 'AVG(salesAmount)',
    partitionBy: ['product$caption'],
    windowOrderBy: [{ field: 'salesDate$caption', dir: 'asc' }],
    windowFrame: 'ROWS BETWEEN 6 PRECEDING AND CURRENT ROW',
    type: 'NUMBER'
}
```

## 窗口字段属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `name` | string | 字段名（DSL 中引用） |
| `caption` | string | 显示名称 |
| `formula` | string | 表达式（支持窗口函数和聚合函数） |
| `type` | string | 返回类型 |
| `partitionBy` | string[] | 窗口分区字段 |
| `windowOrderBy` | array | 窗口排序 `[{ field, dir }]` |
| `windowFrame` | string | 窗口帧定义 |

## 支持的窗口函数

- `ROW_NUMBER()` / `RANK()` / `DENSE_RANK()` — 排名
- `LAG(field, offset)` / `LEAD(field, offset)` — 偏移
- `AVG(field)` / `SUM(field)` + `windowFrame` — 移动聚合

## 覆盖规则

DSL 请求中 `calculatedFields` 里的同名字段会覆盖 QM 预定义字段：

```json
{
  "calculatedFields": [
    {
      "name": "profitRate",
      "expression": "profitAmount / salesAmount * 200"
    }
  ]
}
```

以上会覆盖 QM 中预定义的 `profitRate`（原 *100 → 被覆盖为 *200）。

## 生成决策

- 如果 TM 有利润/成本相关度量 → 生成 `profitRate` 公式字段
- 如果用户需要排名分析 → 生成 `RANK()` 窗口字段
- 如果用户需要趋势分析 → 生成 `LAG()` 或移动平均窗口字段
- 如果 TM 有 `count_distinct` 聚合度量 → 直接作为 `ref` 引用即可
