# 计算字段

计算字段允许在查询时动态定义新的列，通过表达式计算得到值，无需修改 TM/QM 模型。

## 1. 概述

计算字段有两种定义方式：

1. **calculatedFields 参数**：在 DSL 请求中定义
2. **内联表达式**：在 columns 中直接写 `expression as alias`

---

## 2. 通过 calculatedFields 定义

### 2.1 基本格式

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "净销售额",
                "expression": "salesAmount - discountAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "discountAmount", "netAmount"]
    }
}
```

### 2.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 字段名，在 columns/slice/orderBy 中引用 |
| `caption` | string | 否 | 显示名称 |
| `expression` | string | 是 | 计算表达式 |
| `description` | string | 否 | 字段描述 |
| `agg` | string | 否 | 聚合类型（用于 autoGroupBy 场景） |

### 2.3 表达式中的列引用

表达式可以引用：
- 模型中的属性名
- 模型中的度量名
- 维度列：`dimension$caption`, `dimension$id`, `dimension$property`
- 其他计算字段名（需在当前字段之前定义）

---

## 3. 通过内联表达式定义

在 columns 中直接使用 `expression as alias` 格式：

```json
{
    "param": {
        "columns": [
            "orderId",
            "salesAmount",
            "salesAmount * 1.1 as adjustedAmount",
            "ROUND(salesAmount, 2) as roundedAmount"
        ]
    }
}
```

### 3.1 聚合表达式

```json
{
    "param": {
        "columns": [
            "product$categoryName",
            "sum(salesAmount) as totalSales",
            "count(*) as orderCount"
        ],
        "groupBy": [
            { "field": "product$categoryName" }
        ]
    }
}
```

> **注意**：内联表达式的别名不能与模型中已有的字段名相同，否则会报错。

---

## 4. 支持的表达式

### 4.1 算术运算

| 运算符 | 说明 | 示例 |
|--------|------|------|
| `+` | 加法 | `salesAmount + taxAmount` |
| `-` | 减法 | `salesAmount - discountAmount` |
| `*` | 乘法 | `unitPrice * quantity` |
| `/` | 除法 | `profitAmount / salesAmount` |
| `%` | 取模 | `quantity % 10` |

**复合表达式**：

```
(salesAmount - discountAmount) * 1.13
```

### 4.2 数学函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `ABS(x)` | 绝对值 | `ABS(discountAmount)` |
| `ROUND(x, n)` | 四舍五入 | `ROUND(salesAmount, 2)` |
| `CEIL(x)` | 向上取整 | `CEIL(quantity / 10)` |
| `FLOOR(x)` | 向下取整 | `FLOOR(quantity / 10)` |
| `MOD(x, y)` | 取模 | `MOD(quantity, 10)` |
| `POWER(x, y)` | 幂运算 | `POWER(2, 3)` |
| `SQRT(x)` | 平方根 | `SQRT(variance)` |

### 4.3 日期函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `YEAR(date)` | 提取年份 | `YEAR(salesDate$caption)` |
| `MONTH(date)` | 提取月份 | `MONTH(salesDate$caption)` |
| `DAY(date)` | 提取日期 | `DAY(salesDate$caption)` |
| `DATE(datetime)` | 提取日期部分 | `DATE(orderTime)` |
| `NOW()` | 当前时间 | `NOW()` |
| `DATE_ADD(date, interval)` | 日期加法 | `DATE_ADD(orderDate, 7)` |
| `DATE_SUB(date, interval)` | 日期减法 | `DATE_SUB(orderDate, 7)` |
| `DATEDIFF(d1, d2)` | 日期差 | `DATEDIFF(NOW(), orderDate)` |

### 4.4 字符串函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `CONCAT(a, b, ...)` | 字符串连接 | `CONCAT(orderId, '-', orderLineNo)` |
| `SUBSTRING(s, start, len)` | 子字符串 | `SUBSTRING(orderId, 1, 4)` |
| `UPPER(s)` | 转大写 | `UPPER(status)` |
| `LOWER(s)` | 转小写 | `LOWER(email)` |
| `TRIM(s)` | 去除空格 | `TRIM(customerName)` |
| `LENGTH(s)` | 字符串长度 | `LENGTH(productName)` |

### 4.5 空值处理函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `COALESCE(a, b, ...)` | 返回第一个非空值 | `COALESCE(discountAmount, 0)` |
| `NULLIF(a, b)` | 相等则返回 NULL | `NULLIF(status, 'UNKNOWN')` |
| `IFNULL(a, b)` | 为空则返回默认值 | `IFNULL(discountAmount, 0)` |

### 4.6 聚合函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `SUM(x)` | 求和 | `SUM(salesAmount)` |
| `AVG(x)` | 平均值 | `AVG(unitPrice)` |
| `COUNT(*)` | 计数 | `COUNT(*)` |
| `MAX(x)` | 最大值 | `MAX(salesAmount)` |
| `MIN(x)` | 最小值 | `MIN(salesAmount)` |

---

## 5. 完整示例

### 5.1 简单算术表达式

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "净销售额",
                "expression": "salesAmount - discountAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "discountAmount", "netAmount"]
    }
}
```

**生成的 SQL**：

```sql
SELECT
    order_id AS orderId,
    sales_amount AS salesAmount,
    discount_amount AS discountAmount,
    (sales_amount - discount_amount) AS netAmount
FROM fact_sales
```

### 5.2 利润率计算

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "profitRate",
                "caption": "利润率(%)",
                "expression": "profitAmount * 100.0 / salesAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "profitAmount", "profitRate"],
        "slice": [
            { "field": "salesAmount", "op": ">", "value": 0 }
        ]
    }
}
```

### 5.3 链式计算字段

计算字段可以引用其他计算字段：

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "净销售额",
                "expression": "salesAmount - discountAmount"
            },
            {
                "name": "taxIncluded",
                "caption": "含税金额",
                "expression": "netAmount * 1.13"
            }
        ],
        "columns": ["orderId", "salesAmount", "netAmount", "taxIncluded"]
    }
}
```

### 5.4 引用维度列

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "yearMonth",
                "caption": "年月",
                "expression": "CONCAT(YEAR(salesDate$caption), '-', MONTH(salesDate$caption))"
            }
        ],
        "columns": ["orderId", "salesDate$caption", "yearMonth", "salesAmount"]
    }
}
```

### 5.5 计算字段作为过滤条件

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "profitRate",
                "caption": "利润率",
                "expression": "profitAmount * 100.0 / salesAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "profitAmount", "profitRate"],
        "slice": [
            { "field": "salesAmount", "op": ">", "value": 0 },
            { "field": "profitRate", "op": ">", "value": 10 }
        ]
    }
}
```

### 5.6 分组汇总中的计算字段

```json
{
    "param": {
        "columns": [
            "product$categoryName",
            "sum(salesAmount) as totalSales",
            "sum(profitAmount) as totalProfit",
            "sum(profitAmount) * 100.0 / sum(salesAmount) as profitRate"
        ],
        "groupBy": [
            { "field": "product$categoryName" }
        ],
        "orderBy": [
            { "field": "totalSales", "order": "desc" }
        ]
    }
}
```

---

## 6. 安全性

### 6.1 禁止的函数

以下函数会被拦截并抛出 `SecurityException`：

- `EXEC`、`EXECUTE`
- `DROP`、`DELETE`、`UPDATE`、`INSERT`
- `CREATE`、`ALTER`、`TRUNCATE`
- 其他未在白名单中的函数

### 6.2 错误处理

| 错误类型 | 说明 |
|----------|------|
| 引用不存在的列 | 抛出异常，提示列名不存在 |
| 重复的计算字段名 | 抛出异常，提示名称已存在 |
| 别名与已有字段冲突 | 抛出异常，提示字段名冲突 |
| 使用禁止的函数 | 抛出 SecurityException |

---

## 7. 最佳实践

1. **避免除零错误**：除法运算前添加 `salesAmount > 0` 条件
2. **使用 COALESCE**：处理可能为 NULL 的字段
3. **命名规范**：计算字段使用有意义的名称，避免与已有字段冲突
4. **链式依赖**：被引用的计算字段需在引用者之前定义
5. **性能考虑**：复杂的计算字段可能影响查询性能

---

## 下一步

- [DSL 查询 API](../api/query-api.md) - 完整的查询参考
- [TM 语法手册](./tm-syntax.md) - 表格模型定义
- [QM 语法手册](./qm-syntax.md) - 查询模型定义
