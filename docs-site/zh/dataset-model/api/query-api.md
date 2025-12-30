# DSL 查询 API

本文档详细介绍 Foggy Dataset Model 的 DSL（Domain Specific Language）查询接口。

## 1. 概述

DSL 查询使用 JSON 格式，通过 HTTP API 向 QM（查询模型）发起查询请求。

### 1.1 基础信息

- **Base URL**: `/jdbc-model/query-model`
- **Content-Type**: `application/json`
- **响应格式**: JSON

### 1.2 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v2/{model}` | 查询模型数据（推荐） |
| POST | `/queryKpi` | KPI 汇总查询 |

---

## 2. 查询模型数据

### 2.1 接口地址

```
POST /jdbc-model/query-model/v2/{model}
```

### 2.2 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 查询模型名称，如 `FactOrderQueryModel` |

### 2.3 请求体结构

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "customer$caption", "totalAmount"],
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" }
        ],
        "groupBy": [
            { "field": "customer$customerType" }
        ],
        "orderBy": [
            { "field": "totalAmount", "order": "desc" }
        ],
        "returnTotal": true
    }
}
```

### 2.4 分页参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | integer | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | integer | 否 | 10 | 每页条数 |
| `start` | integer | 否 | 0 | 起始记录数（与 page 二选一） |
| `limit` | integer | 否 | 10 | 返回条数（与 pageSize 二选一） |

### 2.5 param 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `columns` | string[] | 否 | 查询列，空则返回所有有权限的列 |
| `exColumns` | string[] | 否 | 排除列 |
| `slice` | SliceRequestDef[] | 否 | 过滤条件 |
| `groupBy` | GroupRequestDef[] | 否 | 分组字段 |
| `orderBy` | OrderRequestDef[] | 否 | 排序字段 |
| `returnTotal` | boolean | 否 | 是否返回总数及汇总数据 |

---

## 3. 查询列 (columns)

### 3.1 基本用法

指定要查询的字段列表：

```json
{
    "param": {
        "columns": [
            "orderId",              // 事实表属性
            "orderStatus",          // 事实表属性
            "totalAmount",          // 度量
            "customer$caption",     // 维度显示值
            "customer$customerType" // 维度属性
        ]
    }
}
```

### 3.2 字段引用格式

| 格式 | 说明 | 示例 |
|------|------|------|
| `属性名` | 事实表属性 | `orderId`, `orderStatus` |
| `度量名` | 度量字段 | `totalAmount`, `quantity` |
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$id` | 维度 ID | `customer$id` |
| `维度名$属性名` | 维度属性 | `customer$customerType`, `orderDate$year` |

### 3.3 排除列 (exColumns)

排除特定列：

```json
{
    "param": {
        "columns": ["*"],           // 查询所有列
        "exColumns": ["sensitiveField"]  // 但排除敏感字段
    }
}
```

---

## 4. 过滤条件 (slice)

### 4.1 基本格式

```json
{
    "field": "字段名",
    "op": "操作符",
    "value": "值",
    "link": 1
}
```

### 4.2 操作符类型

| 操作符 | 说明 | value 类型 | 示例 |
|--------|------|-----------|------|
| `=` | 等于 | any | `{ "op": "=", "value": "COMPLETED" }` |
| `!=` | 不等于 | any | `{ "op": "!=", "value": "CANCELLED" }` |
| `>` | 大于 | number | `{ "op": ">", "value": 100 }` |
| `>=` | 大于等于 | number | `{ "op": ">=", "value": 100 }` |
| `<` | 小于 | number | `{ "op": "<", "value": 1000 }` |
| `<=` | 小于等于 | number | `{ "op": "<=", "value": 1000 }` |
| `in` | 包含 | array | `{ "op": "in", "value": ["A", "B", "C"] }` |
| `not in` | 不包含 | array | `{ "op": "not in", "value": ["X", "Y"] }` |
| `like` | 模糊匹配 | string | `{ "op": "like", "value": "%关键字%" }` |
| `not like` | 不匹配 | string | `{ "op": "not like", "value": "%排除%" }` |
| `is null` | 为空 | null | `{ "op": "is null" }` |
| `is not null` | 不为空 | null | `{ "op": "is not null" }` |
| `[]` | 闭区间 | array[2] | `{ "op": "[]", "value": [100, 500] }` |
| `[)` | 左闭右开 | array[2] | `{ "op": "[)", "value": ["2024-01-01", "2024-07-01"] }` |
| `(]` | 左开右闭 | array[2] | `{ "op": "(]", "value": [0, 100] }` |
| `()` | 开区间 | array[2] | `{ "op": "()", "value": [0, 100] }` |

### 4.3 范围条件示例

```json
{
    "param": {
        "slice": [
            {
                "field": "orderTime",
                "op": "[)",
                "value": ["2024-01-01", "2024-07-01"]
            },
            {
                "field": "totalAmount",
                "op": "[]",
                "value": [100, 500]
            }
        ]
    }
}
```

**生成的 SQL**：

```sql
WHERE order_time >= '2024-01-01' AND order_time < '2024-07-01'
  AND total_amount >= 100 AND total_amount <= 500
```

### 4.4 逻辑连接 (link)

| 值 | 说明 |
|----|------|
| `1` 或不填 | AND 连接 |
| `2` | OR 连接 |

**示例**：

```json
{
    "param": {
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" },
            { "field": "orderStatus", "op": "=", "value": "SHIPPED", "link": 2 }
        ]
    }
}
```

**生成的 SQL**：

```sql
WHERE (order_status = 'COMPLETED' OR order_status = 'SHIPPED')
```

### 4.5 嵌套条件 (children)

支持复杂的嵌套条件：

```json
{
    "param": {
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" },
            {
                "link": 1,
                "children": [
                    { "field": "totalAmount", "op": ">=", "value": 1000 },
                    { "field": "customer$customerType", "op": "=", "value": "VIP", "link": 2 }
                ]
            }
        ]
    }
}
```

**生成的 SQL**：

```sql
WHERE order_status = 'COMPLETED'
  AND (total_amount >= 1000 OR customer_type = 'VIP')
```

---

## 5. 分组 (groupBy)

### 5.1 基本格式

```json
{
    "param": {
        "groupBy": [
            { "field": "customer$customerType" },
            { "field": "orderDate$year" },
            { "field": "orderDate$month" }
        ]
    }
}
```

### 5.2 说明

- 设置 `groupBy` 后，度量字段会自动按 TM 中定义的聚合方式聚合
- 不设置 `groupBy` 时返回明细数据
- 可以按维度属性分组

### 5.3 示例

```json
{
    "param": {
        "columns": [
            "orderDate$year",
            "orderDate$month",
            "customer$customerType",
            "totalQuantity",
            "totalAmount"
        ],
        "groupBy": [
            { "field": "orderDate$year" },
            { "field": "orderDate$month" },
            { "field": "customer$customerType" }
        ]
    }
}
```

**返回结果**：

```json
{
    "items": [
        {
            "orderDate$year": 2024,
            "orderDate$month": 6,
            "customer$customerType": "VIP",
            "totalQuantity": 150,
            "totalAmount": 89900.00
        },
        {
            "orderDate$year": 2024,
            "orderDate$month": 6,
            "customer$customerType": "普通",
            "totalQuantity": 80,
            "totalAmount": 45600.00
        }
    ]
}
```

---

## 6. 排序 (orderBy)

### 6.1 基本格式

```json
{
    "param": {
        "orderBy": [
            { "field": "totalAmount", "order": "desc" },
            { "field": "orderId", "order": "asc" }
        ]
    }
}
```

### 6.2 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field` | string | 是 | 排序字段名 |
| `order` | string | 是 | `asc`（升序）/ `desc`（降序） |
| `nullFirst` | boolean | 否 | NULL 值排在最前 |
| `nullLast` | boolean | 否 | NULL 值排在最后 |

---

## 7. 响应结构

### 7.1 响应体

```json
{
    "code": 0,
    "data": {
        "items": [
            {
                "orderId": "ORD202401010001",
                "orderStatus": "COMPLETED",
                "customer$caption": "客户A",
                "totalAmount": 1299.00
            }
        ],
        "total": 100,
        "totalData": {
            "total": 100,
            "totalAmount": 129900.00
        }
    },
    "msg": "success"
}
```

### 7.2 响应字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 状态码，0 表示成功 |
| `data.items` | array | 明细数据列表（分页后的数据） |
| `data.total` | integer | 符合条件的总记录数 |
| `data.totalData` | object | 汇总数据（仅当 `returnTotal=true` 时返回） |
| `msg` | string | 消息 |

### 7.3 totalData 说明

- `totalData` 包含 `columns` 中指定的度量字段的聚合值
- 这是对**所有符合条件的数据**进行聚合，不受分页影响
- 只有设置 `returnTotal=true` 时才返回

---

## 8. 完整请求示例

### 8.1 明细查询

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": [
            "orderId",
            "orderStatus",
            "orderTime",
            "customer$caption",
            "customer$customerType",
            "product$caption",
            "totalAmount"
        ],
        "slice": [
            { "field": "orderStatus", "op": "in", "value": ["COMPLETED", "SHIPPED"] },
            { "field": "totalAmount", "op": ">=", "value": 100 }
        ],
        "orderBy": [
            { "field": "orderTime", "order": "desc" }
        ],
        "returnTotal": true
    }
}
```

### 8.2 分组汇总

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
    "page": 1,
    "pageSize": 100,
    "param": {
        "columns": [
            "orderDate$year",
            "orderDate$month",
            "customer$customerType",
            "totalQuantity",
            "totalAmount"
        ],
        "slice": [
            { "field": "orderDate$caption", "op": "[)", "value": ["2024-01-01", "2024-07-01"] }
        ],
        "groupBy": [
            { "field": "orderDate$year" },
            { "field": "orderDate$month" },
            { "field": "customer$customerType" }
        ],
        "orderBy": [
            { "field": "orderDate$year", "order": "desc" },
            { "field": "orderDate$month", "order": "asc" }
        ]
    }
}
```

### 8.3 复杂条件查询

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": [
            "orderId",
            "orderStatus",
            "customer$caption",
            "product$category",
            "totalAmount"
        ],
        "slice": [
            { "field": "orderTime", "op": "[)", "value": ["2024-01-01", "2024-07-01"] },
            {
                "children": [
                    { "field": "customer$customerType", "op": "=", "value": "VIP" },
                    { "field": "totalAmount", "op": ">=", "value": 1000, "link": 2 }
                ]
            },
            { "field": "product$category", "op": "in", "value": ["数码电器", "家居用品"] }
        ],
        "orderBy": [
            { "field": "totalAmount", "order": "desc" }
        ]
    }
}
```

**生成的 SQL 条件**：

```sql
WHERE order_time >= '2024-01-01' AND order_time < '2024-07-01'
  AND (customer_type = 'VIP' OR total_amount >= 1000)
  AND category IN ('数码电器', '家居用品')
ORDER BY total_amount DESC
```

### 8.4 KPI 汇总查询

```json
POST /jdbc-model/query-model/queryKpi

{
    "queryModel": "FactSalesQueryModel",
    "columns": ["salesQuantity", "salesAmount", "profitAmount"],
    "slice": [
        { "field": "salesDate$caption", "op": "[)", "value": ["2024-01-01", "2024-07-01"] },
        { "field": "product$category", "op": "=", "value": "数码电器" }
    ]
}
```

**返回结果**：

```json
{
    "code": 0,
    "data": {
        "salesQuantity": 15000,
        "salesAmount": 8990000.00,
        "profitAmount": 1798000.00
    }
}
```

---

## 9. 错误处理

### 9.1 错误码

| 错误码 | 说明 |
|--------|------|
| `0` | 成功 |
| `400` | 请求参数错误 |
| `403` | 无权限访问 |
| `404` | 查询模型不存在 |
| `500` | 服务器内部错误 |

### 9.2 错误响应示例

```json
{
    "code": 404,
    "msg": "Query model 'InvalidModel' not found",
    "data": null
}
```

---

## 10. 最佳实践

### 10.1 分页建议

- 单次查询建议不超过 1000 条
- 大数据量查询建议使用分页
- 导出数据时可以分批次请求

### 10.2 性能优化

- 只查询需要的字段，避免使用 `columns: ["*"]`
- 合理使用索引字段作为过滤条件
- 对于大数据量的分组汇总，考虑预聚合

### 10.3 安全性

- 字段访问受 QM 中 accesses 配置控制
- 敏感字段可通过 accesses 限制访问
- 所有条件参数都经过参数化处理，防止 SQL 注入

---

## 下一步

- [JSON 查询 DSL](../tm-qm/query-dsl.md) - 查询 DSL 完整语法（推荐阅读）
- [TM 语法手册](../tm-qm/tm-syntax.md) - 表格模型定义
- [QM 语法手册](../tm-qm/qm-syntax.md) - 查询模型定义
- [权限控制](./authorization.md) - 详细的权限配置
