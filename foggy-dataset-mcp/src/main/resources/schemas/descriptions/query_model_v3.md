# query_model_v3 工具描述

## 基本信息
- **工具名称**: query_model_v2
- **功能**: 执行指定模型的数据查询，支持复杂查询条件、分页、排序、分组聚合、计算字段等

## 核心概念

### 🎯 字段使用规则（V3版本）

**直接使用 `description_model` 返回的字段名**，无需额外处理。

#### 维度字段 (Dimension)
维度字段会返回两个变体，按用途选择：
- **xxx$id**: 用于精确查询/过滤
- **xxx$caption**: 用于展示名称

#### 父子维度 (Parent-Child Dimension)
层级结构维度（如组织架构）额外支持 `$hierarchy$` 视角：
- **xxx$id**: 精确匹配该节点
- **xxx$hierarchy$id**: 匹配该节点及所有后代（用于层级汇总）

示例：`team$hierarchy$id = 'T001'` 查询总公司及所有子部门

#### 属性字段 (Attribute) / 度量字段 (Measure)
直接使用返回的字段名。

## 工具描述

Query a specific dataset model with advanced filtering, sorting, grouping and aggregation capabilities.

🔍 **API能力概览**:
- 支持复杂查询条件、分页、排序、分组聚合等功能
- **直接使用 `description_model` 返回的字段名**
- 丰富的过滤操作符：=、!=、>、<、like、in、not in、区间查询[)、[]等
- 聚合函数：SUM、AVG、MAX、MIN、COUNT等
- **内联聚合表达式**：在 columns 中直接写聚合，系统自动处理 groupBy

🛠️ **查询参数详解**:

**列选择(columns) - 支持内联聚合表达式**:
```json
["product$categoryName", "sum(salesAmount) as totalSales", "count(orderId) as orderCount"]
```
- **内联聚合表达式**：`聚合函数(字段) as 别名`，如 `sum(salesAmount) as totalSales`
- 支持的聚合函数：`sum`、`avg`、`count`、`max`、`min`
- **系统自动处理 groupBy**，无需手动指定

**计算字段(calculatedFields)** - 需要指定 agg 或复杂表达式时使用:
```json
"calculatedFields": [
  {"name": "netAmount", "expression": "salesAmount - discountAmount"},
  {"name": "taxAmount", "expression": "salesAmount * 0.13", "agg": "SUM"}
]
```
- **name**: 字段名称，在 columns 中引用
- **expression**: 计算表达式，支持算术运算(+,-,*,/)、函数(ABS,ROUND,COALESCE)、字段引用
- **agg**: 聚合类型(SUM/AVG/MAX/MIN/COUNT)，用于 groupBy 场景

**过滤条件(slice)**:
```json
[
  {"field": "customer$caption", "op": "like", "value": "张三"},
  {"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]},
  {"field": "customerType", "op": "in", "value": [10, 20, 30]},
  {"field": "customerLevel", "op": "is not null"}
]
```

**重要语法规则**:
- **空值检查**: `is null` 或 `is not null` 时，**不需要 value 参数**
- **区间查询**: `[]`, `[)`, `()`, `(]` 时，value 必须是 `[start, end]`
- **模糊查询**: 系统会自动添加通配符

**支持的操作符**:
- **等值**: `=`, `!=`, `<>`
- **比较**: `>`, `>=`, `<`, `<=`
- **模糊**: `like`, `left_like`, `right_like`
- **集合**: `in`, `not in`
- **空值**: `is null`, `is not null` (无需value)
- **区间**: `[]`, `[)`, `()`, `(]`
- **层级**: `childrenOf`, `descendantsOf`, `selfAndDescendantsOf` (父子维度专用，可选 maxDepth)

**排序(orderBy)**:
```json
[{"field": "totalSales", "dir": "DESC"}, {"field": "product$categoryName", "dir": "ASC"}]
```
- **注意**：存在聚合时，orderBy 字段必须在 columns 中出现

**分组聚合(groupBy)** - 通常无需手动指定:
- **推荐**：使用内联聚合表达式，系统自动处理 groupBy
- 如需手动控制，可显式指定 groupBy
- **聚合类型(agg)**: MAX, MIN, SUM, AVG, COUNT, PK

**分页参数**:
- `start`: 起始行号，从0开始
- `limit`: 每页记录数
- `returnTotal`: 是否返回总数，设为 false 可提升性能

💡 **查询示例**:

**基础查询**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["orderNo", "customer$caption", "salesDate$caption", "totalAmount"],
    "slice": [{"field": "salesDate$id", "op": "[)", "value": ["20250101", "20251231"]}],
    "orderBy": [{"field": "salesDate$id", "dir": "DESC"}],
    "start": 0,
    "limit": 30
  }
}
```

**聚合查询（推荐：内联聚合表达式）**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "columns": ["salesDate$caption", "sum(totalAmount) as totalSales", "count(orderId) as orderCount"],
    "orderBy": [{"field": "totalSales", "dir": "DESC"}],
    "start": 0,
    "limit": 50
  }
}
```

**计算字段查询（需要 agg 参数时）**:
```json
{
  "model": "TmsOrderModel",
  "payload": {
    "calculatedFields": [
      {"name": "netAmount", "expression": "salesAmount - discountAmount", "agg": "SUM"}
    ],
    "columns": ["customer$customerType", "netAmount"],
    "orderBy": [{"field": "netAmount", "dir": "DESC"}],
    "start": 0,
    "limit": 30
  }
}
```

🎯 **最佳实践**:
- 展示数据时优先选择 `$caption` 变体，精确查询时使用 `$id` 变体
- **简单聚合推荐内联表达式**：`sum(amount) as total`，系统自动处理 groupBy
- **复杂计算或需指定 agg 时用 calculatedFields**
- orderBy 字段需在 columns 中出现（聚合场景）
