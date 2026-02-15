# 高级分析语法

## COUNT(DISTINCT) 去重计数

两种等价写法：

```json
{
  "name": "uv",
  "caption": "独立客户数",
  "expression": "COUNTD(customer$id)"
}
```

```json
{
  "name": "uv",
  "expression": "COUNT_DISTINCT(customer$id)"
}
```

均生成 `COUNT(DISTINCT t1.customer_key)`。

**在 groupBy 中使用**：

```json
{
  "groupBy": [
    {"field": "product$categoryName"},
    {"field": "uniqueCustomers", "agg": "COUNT_DISTINCT"}
  ]
}
```

---

## 窗口函数

通过 `partitionBy`、`windowOrderBy`、`windowFrame` 将计算字段转为窗口函数。

### 字段定义

| 字段 | 类型 | 说明 |
|------|------|------|
| `partitionBy` | string[] | 分区字段（类似 GROUP BY） |
| `windowOrderBy` | WindowOrderDef[] | 窗口内排序 |
| `windowFrame` | string | 窗口帧（滑动窗口） |

`WindowOrderDef`: `{ "field": "字段名", "dir": "asc" | "desc" }`

### 排名函数

```json
{
  "calculatedFields": [
    {
      "name": "salesRank",
      "caption": "品类排名",
      "expression": "RANK()",
      "partitionBy": ["product$categoryName"],
      "windowOrderBy": [{"field": "salesAmount", "dir": "desc"}]
    }
  ],
  "columns": ["product$categoryName", "product$caption", "salesAmount", "salesRank"]
}
```

支持的排名函数：`ROW_NUMBER()`、`RANK()`、`DENSE_RANK()`

### LAG/LEAD 环比

```json
{
  "name": "prevAmount",
  "caption": "上期销售额",
  "expression": "LAG(salesAmount, 1)",
  "partitionBy": ["product$caption"],
  "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}]
}
```

### 移动平均（窗口帧）

```json
{
  "name": "ma7",
  "caption": "7日移动平均",
  "expression": "AVG(salesAmount)",
  "partitionBy": ["product$caption"],
  "windowOrderBy": [{"field": "salesDate$caption", "dir": "asc"}],
  "windowFrame": "ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"
}
```

常用窗口帧：
- `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` — 前6行到当前行
- `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` — 累计
- `ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING` — 前后各1行

---

## 统计函数

```json
{
  "name": "salesStdDev",
  "caption": "销售额标准差",
  "expression": "STDDEV_POP(salesAmount)"
}
```

| 函数 | 说明 |
|------|------|
| `STDDEV_POP(field)` | 总体标准差 |
| `STDDEV_SAMP(field)` | 样本标准差 |
| `VAR_POP(field)` | 总体方差 |
| `VAR_SAMP(field)` | 样本方差 |

**数据库兼容性**：
- MySQL / PostgreSQL — 标准函数名
- SQL Server — 自动映射（STDDEV_POP -> STDEVP，VAR_POP -> VARP）
- SQLite — 不支持，抛出运行时错误

---

## 完整示例：销售分析看板

```json
{
  "queryModel": "FactSalesQueryModel",
  "columns": [
    "product$categoryName", "product$caption",
    "salesDate$caption", "salesAmount",
    "uv", "salesRank", "ma7"
  ],
  "calculatedFields": [
    {
      "name": "uv",
      "expression": "COUNTD(customer$id)"
    },
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
  ]
}
```

---

## QM 预定义字段

QM 文件中可预定义计算字段，查询时直接引用名称即可，无需重复定义：

```json
{
  "queryModel": "FactSalesQueryModel",
  "columns": ["product$categoryName", "salesAmount", "profitRate", "salesRank", "ma7"]
}
```

预定义字段可被 DSL 中同名 `calculatedFields` 覆盖。
