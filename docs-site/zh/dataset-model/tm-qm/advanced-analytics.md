# 高级分析能力

## 概述

foggy-dataset-model 支持三个层级的分析能力：

| Level | 能力 | 示例 |
|-------|------|------|
| 1 | 扩展聚合 | COUNT(DISTINCT)、STDDEV、VARIANCE |
| 2 | 窗口函数 | RANK、LAG/LEAD、移动平均 |
| 3 | 复合分析模式 | Z-Score、同比环比、RFM、帕累托 |

---

## Level 1: 扩展聚合

### COUNT(DISTINCT) 去重计数

```json
{
  "calculatedFields": [
    {"name": "uv", "expression": "COUNTD(customerId)"}
  ],
  "columns": ["product$caption", "uv"],
  "groupBy": ["product$caption"]
}
```

别名：`COUNTD()` 和 `COUNT_DISTINCT()` 等价。

### 统计函数

| 函数 | 说明 | SQLite |
|------|------|--------|
| `STDDEV_POP(x)` | 总体标准差 | 不支持 |
| `STDDEV_SAMP(x)` | 样本标准差 | 不支持 |
| `VAR_POP(x)` | 总体方差 | 不支持 |
| `VAR_SAMP(x)` | 样本方差 | 不支持 |

SQL Server 会自动翻译为 `STDEVP`/`STDEV`/`VARP`/`VAR`。

```json
{
  "calculatedFields": [
    {"name": "salesStd", "expression": "STDDEV_POP(salesAmount)"}
  ],
  "columns": ["product$caption", "salesStd"],
  "groupBy": ["product$caption"]
}
```

### TM 中定义

Phase 1 完成后，TM 的 `aggregation` 字段直接支持新类型：

```javascript
measures: [
  { column: 'customer_id', name: 'uniqueCustomers', caption: '独立客户数',
    type: 'INTEGER', aggregation: 'COUNT_DISTINCT' },
  { column: 'sales_amount', name: 'salesStddev', caption: '销售标准差',
    type: 'NUMBER', aggregation: 'STDDEV_POP' }
]
```

---

## Level 2: 窗口函数

### 支持的窗口函数

`ROW_NUMBER`、`RANK`、`DENSE_RANK`、`NTILE`、`LAG`、`LEAD`、`FIRST_VALUE`、`LAST_VALUE`

聚合函数（`SUM`/`AVG`/`COUNT`/`MAX`/`MIN`）加上 `partitionBy` 后也变为窗口函数。

### 方式1: 结构化定义（推荐）

```json
{
  "calculatedFields": [
    {
      "name": "salesRank",
      "expression": "RANK()",
      "partitionBy": ["product$categoryName"],
      "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]
    },
    {
      "name": "ma7",
      "expression": "AVG(salesAmount)",
      "partitionBy": ["product$caption"],
      "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
      "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"
    }
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "salesRank", "ma7"]
}
```

### 窗口函数字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `partitionBy` | `string[]` | PARTITION BY 字段列表 |
| `windowOrderBy` | `{field, dir}[]` | ORDER BY 排序定义 |
| `windowFrame` | `string` | 窗口帧，如 `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` |

### QM 预定义窗口字段

在 QM 中使用 `formula` + `partitionBy` 预定义窗口计算字段：

```javascript
columnGroups: [{
  caption: '高级分析',
  items: [
    { name: 'profitRate', caption: '利润率(%)',
      formula: 'profitAmount / salesAmount * 100', type: 'NUMBER' },
    { name: 'salesRank', caption: '销售排名',
      formula: 'RANK()',
      partitionBy: ['product$categoryName'],
      windowOrderBy: [{ field: 'salesAmount', dir: 'desc' }],
      type: 'INTEGER' },
    { name: 'ma7', caption: '7日移动平均',
      formula: 'AVG(salesAmount)',
      partitionBy: ['product$caption'],
      windowOrderBy: [{ field: 'salesDate$caption', dir: 'asc' }],
      windowFrame: 'ROWS BETWEEN 6 PRECEDING AND CURRENT ROW',
      type: 'NUMBER' }
  ]
}]
```

查询时直接引用字段名：

```json
{"columns": ["product$caption", "salesDate$caption", "salesAmount", "salesRank", "ma7"]}
```

### 定义位置选择

| 场景 | 推荐位置 | 原因 |
|------|---------|------|
| COUNT_DISTINCT / STDDEV 等新聚合 | TM `aggregation` | 与现有度量定义一致 |
| 窗口函数（RANK/LAG/移动平均） | QM `formula` + `partitionBy` | 使用语义字段名，可跨维度引用 |
| 复杂计算字段（利润率等） | QM `formula` | 引用其他模型字段名 |

---

## Level 3: 复合分析模式

以下模式通过组合 Level 1-2 能力实现。

### Z-Score 离群值检测

```json
{
  "calculatedFields": [
    {"name": "avgSales", "expression": "AVG(salesAmount)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "stdSales", "expression": "STDDEV_POP(salesAmount)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "zScore", "expression": "(salesAmount - avgSales) / stdSales"}
  ],
  "columns": ["product$caption", "salesAmount", "zScore"]
}
```

Z-Score > 2 或 < -2 的数据点视为离群值。

### 同比 YoY（Year over Year）

```json
{
  "calculatedFields": [
    {"name": "lastYearSales", "expression": "LAG(salesAmount, 12)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}]},
    {"name": "yoyRate", "expression": "(salesAmount - lastYearSales) / lastYearSales * 100"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "yoyRate"]
}
```

### 环比 MoM（Month over Month）

```json
{
  "calculatedFields": [
    {"name": "lastMonthSales", "expression": "LAG(salesAmount, 1)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}]},
    {"name": "momRate", "expression": "(salesAmount - lastMonthSales) / lastMonthSales * 100"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "momRate"]
}
```

### 排行榜 Top N

```json
{
  "calculatedFields": [
    {"name": "salesRank", "expression": "DENSE_RANK()",
     "partitionBy": ["salesDate$caption"],
     "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]}
  ],
  "columns": ["salesDate$caption", "product$caption", "salesAmount", "salesRank"],
  "filters": [{"field": "salesRank", "op": "<=", "value": 10}]
}
```

### 移动平均（7日/30日）

```json
{
  "calculatedFields": [
    {"name": "ma7", "expression": "AVG(salesAmount)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
     "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"},
    {"name": "ma30", "expression": "AVG(salesAmount)",
     "partitionBy": ["product$caption"],
     "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
     "windowFrame": "ROWS BETWEEN 29 PRECEDING AND CURRENT ROW"}
  ],
  "columns": ["product$caption", "salesDate$caption", "salesAmount", "ma7", "ma30"]
}
```

### 帕累托 ABC 分析

```json
{
  "calculatedFields": [
    {"name": "totalSales", "expression": "SUM(salesAmount)"},
    {"name": "cumSales", "expression": "SUM(totalSales)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "totalSales", "dir": "desc"}],
     "windowFrame": "ROWS UNBOUNDED PRECEDING"},
    {"name": "grandTotal", "expression": "SUM(totalSales)",
     "partitionBy": [], "windowOrderBy": []},
    {"name": "cumPct", "expression": "cumSales / grandTotal * 100"}
  ],
  "columns": ["product$caption", "totalSales", "cumPct"],
  "groupBy": ["product$caption"]
}
```

cumPct <= 80 → A类，80-95 → B类，>95 → C类。

### RFM 客户分群

```json
{
  "calculatedFields": [
    {"name": "rScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "lastOrderDate", "dir": "desc"}]},
    {"name": "fScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "orderCount", "dir": "asc"}]},
    {"name": "mScore", "expression": "NTILE(5)",
     "partitionBy": [],
     "windowOrderBy": [{"field": "totalSpend", "dir": "asc"}]}
  ],
  "columns": ["customer$caption", "rScore", "fScore", "mScore"]
}
```

---

## 数据库兼容性

| 能力 | MySQL | PostgreSQL | SQL Server | SQLite |
|------|-------|-----------|------------|--------|
| COUNT(DISTINCT) | 5.7+ | 12+ | 2012+ | 3.30+ |
| STDDEV/VARIANCE | 5.7+ | 12+ | 2012+ | 不支持 |
| 窗口函数 | 8.0+ | 8.4+ | 2012+ | 3.25+ |
