---
name: dsl-syntax-guide
description: DSL 查询语法参考手册。当用户需要了解 DSL 查询语法、操作符、字段引用格式时使用。
---

# DSL 查询语法参考

Foggy Dataset Model 统一的 DSL 查询语法参考手册。

## 使用场景

当用户需要以下操作时使用：
- 了解 DSL 查询语法
- 查询操作符使用方法
- 字段引用格式说明
- 构建复杂查询条件

## 核心概念

DSL（Domain Specific Language）查询语法用于统一前后端的数据查询接口，无论是 JDBC 还是 MongoDB 模型，都使用相同的查询语法。

## 完整查询结构

```json
{
  "queryModel": "ModelName",
  "columns": ["field1", "field2"],
  "slice": [条件数组],
  "groupBy": [分组数组],
  "orderBy": [排序数组],
  "calculatedFields": [计算字段数组],
  "returnTotal": true
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| queryModel | string | 是 | 查询模型名称 |
| columns | string[] | 否 | 查询列（空则返回所有有权限列） |
| slice | array | 否 | 过滤条件 |
| groupBy | array | 否 | 分组字段 |
| orderBy | array | 否 | 排序字段 |
| calculatedFields | array | 否 | 动态计算字段 |
| returnTotal | boolean | 否 | 是否返回总数（默认 false） |

---

## 字段引用格式

### 直接属性
```
"userId"
"userName"
"orderStatus"
```

### 维度字段
```
"customer$id"           # 维度ID
"customer$caption"      # 维度显示值
"customer$customerType" # 维度属性
"customer$province"     # 维度属性
```

### 嵌套维度（雪花模型）
```
"product$categoryName"           # 一级维度属性
"product.category$caption"       # 二级维度显示值
"product.category.group$caption" # 三级维度显示值
```

### 日期维度字段
```
"salesDate$caption"      # 日期显示值
"salesDate$year"         # 年
"salesDate$quarter"      # 季度
"salesDate$month"        # 月
"salesDate$day"          # 日
"salesDate$dayOfWeek"    # 周几
"salesDate$isWeekend"    # 是否周末
```

---

## 过滤条件 (slice)

### 基本结构
```json
{
  "field": "fieldName",
  "op": "operator",
  "value": value
}
```

### 比较操作符

| 操作符 | 说明 | 示例 | 生成 SQL |
|--------|------|------|----------|
| `=` | 等于 | `{"field":"status","op":"=","value":"ACTIVE"}` | `WHERE status = 'ACTIVE'` |
| `!=` | 不等于 | `{"field":"status","op":"!=","value":"DELETED"}` | `WHERE status != 'DELETED'` |
| `>` | 大于 | `{"field":"amount","op":">","value":100}` | `WHERE amount > 100` |
| `>=` | 大于等于 | `{"field":"amount","op":">=","value":100}` | `WHERE amount >= 100` |
| `<` | 小于 | `{"field":"amount","op":"<","value":1000}` | `WHERE amount < 1000` |
| `<=` | 小于等于 | `{"field":"amount","op":"<=","value":1000}` | `WHERE amount <= 1000` |

### 集合操作符

| 操作符 | 说明 | 示例 | 生成 SQL |
|--------|------|------|----------|
| `in` | 包含于 | `{"field":"status","op":"in","value":["A","B","C"]}` | `WHERE status IN ('A','B','C')` |
| `not in` | 不包含于 | `{"field":"status","op":"not in","value":["X","Y"]}` | `WHERE status NOT IN ('X','Y')` |

### 模糊匹配

| 操作符 | 说明 | 示例 | 生成 SQL |
|--------|------|------|----------|
| `like` | 模糊匹配 | `{"field":"name","op":"like","value":"张"}` | `WHERE name LIKE '%张%'` |
| `left_like` | 左匹配 | `{"field":"name","op":"left_like","value":"张"}` | `WHERE name LIKE '张%'` |
| `right_like` | 右匹配 | `{"field":"name","op":"right_like","value":"三"}` | `WHERE name LIKE '%三'` |

### 空值判断

| 操作符 | 说明 | 示例 | 生成 SQL |
|--------|------|------|----------|
| `is null` | 为空 | `{"field":"email","op":"is null"}` | `WHERE email IS NULL` |
| `is not null` | 不为空 | `{"field":"email","op":"is not null"}` | `WHERE email IS NOT NULL` |

### 范围操作符

| 操作符 | 说明 | 示例 | 生成 SQL |
|--------|------|------|----------|
| `[]` | 闭区间 | `{"field":"amount","op":"[]","value":[100,1000]}` | `WHERE amount >= 100 AND amount <= 1000` |
| `[)` | 左闭右开 | `{"field":"date","op":"[)","value":["2024-01-01","2024-12-31"]}` | `WHERE date >= '2024-01-01' AND date < '2024-12-31'` |
| `(]` | 左开右闭 | `{"field":"amount","op":"(]","value":[100,1000]}` | `WHERE amount > 100 AND amount <= 1000` |
| `()` | 开区间 | `{"field":"amount","op":"()","value":[100,1000]}` | `WHERE amount > 100 AND amount < 1000` |

### 层级操作符（父子维度）

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `childrenOf` | 直接子节点 | `{"field":"team","op":"childrenOf","value":"TEAM001"}` |
| `descendantsOf` | 所有后代 | `{"field":"team","op":"descendantsOf","value":"TEAM001"}` |

### 表达式条件

```json
{
  "$expr": "actualAmount > budgetAmount"
}
```

生成：`WHERE actual_amount > budget_amount`

---

## 逻辑组合

### OR 条件组

```json
{
  "$or": [
    {"field": "status", "op": "=", "value": "ACTIVE"},
    {"field": "status", "op": "=", "value": "PENDING"}
  ]
}
```

生成：`WHERE (status = 'ACTIVE' OR status = 'PENDING')`

### AND 条件组

```json
{
  "$and": [
    {"field": "amount", "op": ">=", "value": 100},
    {"field": "amount", "op": "<=", "value": 1000}
  ]
}
```

生成：`WHERE (amount >= 100 AND amount <= 1000)`

### 嵌套逻辑

```json
{
  "$or": [
    {
      "$and": [
        {"field": "status", "op": "=", "value": "ACTIVE"},
        {"field": "amount", "op": ">=", "value": 100}
      ]
    },
    {"field": "priority", "op": "=", "value": "HIGH"}
  ]
}
```

生成：`WHERE ((status = 'ACTIVE' AND amount >= 100) OR priority = 'HIGH')`

---

## 排序 (orderBy)

### 简写格式（字符串）

```json
"orderBy": ["fieldName"]           // 升序
"orderBy": ["-fieldName"]          // 降序（前缀 -）
```

### 完整格式（对象）

```json
{
  "field": "amount",
  "dir": "desc",           // asc | desc
  "nullLast": true,        // NULL值排在最后
  "nullFirst": false       // NULL值排在最前
}
```

### 多字段排序

```json
"orderBy": [
  {"field": "year", "dir": "desc"},
  {"field": "month", "dir": "asc"},
  "-amount"
]
```

---

## 分组 (groupBy)

### 简单分组

```json
"groupBy": [
  {"field": "customer$customerType"},
  {"field": "salesDate$year"}
]
```

### 带聚合的分组

```json
"groupBy": [
  {"field": "salesDate$year", "agg": "SUM"}
]
```

---

## 计算字段 (calculatedFields)

### 基本结构

```json
{
  "name": "fieldName",
  "caption": "显示名称",
  "expression": "计算表达式",
  "agg": "SUM"  // 可选：聚合类型
}
```

### 算术运算

```json
{
  "name": "netAmount",
  "caption": "净销售额",
  "expression": "salesAmount - discountAmount"
}
```

### 函数调用

```json
{
  "name": "avgPrice",
  "caption": "平均单价",
  "expression": "salesAmount / quantity"
}
```

### 聚合计算

```json
{
  "name": "totalAmount",
  "caption": "总金额",
  "expression": "salesAmount",
  "agg": "SUM"
}
```

**支持的聚合类型**：`SUM`, `AVG`, `COUNT`, `MAX`, `MIN`

---

## 分页参数

### 前端风格（page/pageSize）

```json
{
  "page": 1,
  "pageSize": 20,
  "param": {查询参数}
}
```

### 后端风格（start/limit）

```json
{
  "start": 0,
  "limit": 20,
  "param": {查询参数}
}
```

---

## 完整查询示例

### 示例 1：简单查询

```json
{
  "queryModel": "UserQueryModel",
  "columns": ["userId", "userName", "email"],
  "slice": [
    {"field": "status", "op": "=", "value": "ACTIVE"}
  ],
  "orderBy": ["-createTime"],
  "returnTotal": true
}
```

### 示例 2：复杂条件查询

```json
{
  "queryModel": "OrderQueryModel",
  "columns": ["orderId", "orderNo", "customer$caption", "amount"],
  "slice": [
    {
      "$and": [
        {"field": "orderDate", "op": "[)", "value": ["2024-01-01", "2024-12-31"]},
        {"field": "amount", "op": ">=", "value": 100}
      ]
    },
    {
      "$or": [
        {"field": "status", "op": "=", "value": "COMPLETED"},
        {"field": "status", "op": "=", "value": "PAID"}
      ]
    }
  ],
  "orderBy": ["-amount"],
  "returnTotal": true
}
```

### 示例 3：分组聚合查询

```json
{
  "queryModel": "SalesQueryModel",
  "columns": [
    "salesDate$year",
    "salesDate$month",
    "customer$customerType",
    "quantity",
    "salesAmount"
  ],
  "groupBy": [
    {"field": "salesDate$year"},
    {"field": "salesDate$month"},
    {"field": "customer$customerType"}
  ],
  "orderBy": [
    {"field": "salesDate$year", "dir": "desc"},
    {"field": "salesDate$month", "dir": "asc"}
  ],
  "returnTotal": true
}
```

### 示例 4：计算字段查询

```json
{
  "queryModel": "SalesQueryModel",
  "columns": ["orderId", "salesAmount", "discountAmount", "netAmount"],
  "calculatedFields": [
    {
      "name": "netAmount",
      "caption": "净销售额",
      "expression": "salesAmount - discountAmount"
    }
  ],
  "orderBy": ["-netAmount"]
}
```

---

## 响应格式

### 成功响应

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "items": [
      {"userId": 1, "userName": "张三"},
      {"userId": 2, "userName": "李四"}
    ],
    "total": 100,
    "totalData": {
      "amount": 50000
    }
  }
}
```

### 错误响应

```json
{
  "code": 400,
  "msg": "查询模型不存在: InvalidModel"
}
```

---

## 注意事项

1. **字段引用**：
   - 直接属性使用字段名
   - 维度字段使用 `维度名$属性` 格式
   - 嵌套维度使用 `.` 连接

2. **数据类型**：
   - 字符串：`"value": "text"`
   - 数字：`"value": 100`
   - 布尔：`"value": true`
   - 数组：`"value": [1, 2, 3]`
   - 日期：`"value": "2024-01-01"`

3. **操作符大小写**：
   - 操作符大小写不敏感
   - 推荐使用小写：`=`, `in`, `like`

4. **空值处理**：
   - 使用 `is null` 和 `is not null`
   - 不要使用 `= null`

5. **性能优化**：
   - 尽量指定 `columns`，避免查询所有列
   - 使用索引字段作为过滤条件
   - 分组查询时确保分组字段有索引
