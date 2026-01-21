# dataset.query_model

执行数据模型查询，支持过滤、排序、分组聚合、计算字段。

## 字段规则

**直接使用 `description_model` 返回的字段名**

| 字段类型   | 用法                                         |
|------------|----------------------------------------------|
| 维度       | `xxx$id`(查询/过滤), `xxx$caption`(展示)     |
| 父子维度   | `xxx$hierarchy$id`(含子节点汇总)             |
| 属性/度量  | 直接使用字段名                               |

### 父子维度 (Parent-Child Dimension)
层级结构维度（如组织架构）额外支持 `$hierarchy$` 视角：
- **xxx$id**: 精确匹配该节点
- **xxx$hierarchy$id**: 匹配该节点及所有后代（用于层级汇总）
示例：`team$hierarchy$id = 'T001'` 查询总公司及所有子部门

## 参数

### columns (必填)
支持内联聚合表达式，系统自动处理groupBy：
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
聚合函数：`sum`、`avg`、`count`、`max`、`min`、`group_concat`

**重要**：
- 当使用聚合表达式后，系统自动推断 groupBy，通常无需手动指定
- columns 仅支持简单的 `agg(field) as alias`，复杂计算用 calculatedFields

### calculatedFields (可选)
需要指定agg或复杂表达式时使用：
```json
[{"name": "netAmount", "expression": "salesAmount - discountAmount", "agg": "SUM"}]
```

**支持的函数**（函数名不区分大小写，使用函数调用语法如 `YEAR(date)` 而非 SQL 语法 `EXTRACT(YEAR FROM date)`）：
| 类型 | 函数 |
|------|------|
| 日期 | `YEAR(date)`, `MONTH(date)`, `DAY(date)`, `DATE_FORMAT`, `STR_TO_DATE`, `DATE_ADD`, `DATE_SUB`, `DATEDIFF`, `TIMESTAMPDIFF` |
| 字符串 | `CONCAT`, `CONCAT_WS`, `SUBSTRING`, `LEFT`, `RIGHT`, `LPAD`, `RPAD`, `REPLACE`, `LOCATE` |
| 空值 | `COALESCE`, `IFNULL`, `NVL`, `NULLIF` |
| 条件 | `IF`, `CASE` |
| 类型 | `CAST`, `CONVERT` |

*常用数学函数如 ABS、ROUND、FLOOR、CEIL 等均支持*

### slice (可选)
过滤条件（数组内条件默认 AND 连接）：
```json
[
  {"field": "customer$caption", "op": "like", "value": "张三"},
  {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
  {"field": "customerLevel", "op": "is not null"}
]
```

**逻辑组合**：使用 `$or` / `$and` 显式组合条件：
```json
[
  {"field": "orderStatus", "op": "=", "value": "COMPLETED"},
  {
    "$or": [
      {"field": "totalAmount", "op": ">=", "value": 1000},
      {"field": "customer$customerType", "op": "=", "value": "VIP"}
    ]
  }
]
```
生成 SQL：`WHERE order_status = 'COMPLETED' AND (total_amount >= 1000 OR customer_type = 'VIP')`

**操作符**：

| 类型 | 操作符                                              |
|------|-----------------------------------------------------|
| 等值 | `=`, `!=`, `<>`                                     |
| 比较 | `>`, `>=`, `<`, `<=`                                |
| 模糊 | `like`, `left_like`, `right_like`                   |
| 集合 | `in`, `not in`                                      |
| 空值 | `is null`, `is not null` (无需value)                |
| 区间 | `[]`, `[)`, `()`, `(]` (value为[start,end])         |
| 层级 | `childrenOf`, `descendantsOf`, `selfAndDescendantsOf` |

**字段间比较**：
- `$field` 引用：`{"field": "a", "op": ">", "value": {"$field": "b"}}` → `WHERE a > b`
- `$expr` 表达式：`{"$expr": "salesAmount > costAmount * 1.2"}` → 支持算术运算

### orderBy (可选)
```json
[{"field": "totalSales", "dir": "DESC"}]
```
**使用 columns 中定义的别名**，如 `year` 而非 `YEAR(createdAt)`

### 分页
- `start`: 起始行(从0开始)
- `limit`: 每页记录数
- `returnTotal`: false可提升性能

## 示例

**聚合查询**：
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["salesDate$caption", "sum(totalAmount) as totalSales"],
    "orderBy": [{"field": "totalSales", "dir": "DESC"}],
    "limit": 50
  }
}
```

**按年月统计**：
```json
{
  "model": "ProductModel",
  "payload": {
    "columns": ["YEAR(createdAt) as year", "MONTH(createdAt) as month", "count(productKey) as cnt"],
    "orderBy": [{"field": "year"}, {"field": "month"}],
    "limit": 100
  }
}
```

## 最佳实践
- 展示用`$caption`，查询用`$id`
- 简单聚合用内联表达式，复杂计算用calculatedFields
- orderBy/groupBy 使用 columns 中定义的别名（如 `year`）而非表达式
