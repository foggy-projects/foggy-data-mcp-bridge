# Foggy Dataset Model API 文档

本文档描述 Foggy Dataset Model 的 HTTP API 接口规范。

## 基础信息

- **Base URL**: `/jdbc-model/query-model`
- **Content-Type**: `application/json`
- **响应格式**: JSON

---

## 一、接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/queryModelData` | 查询模型数据（已废弃，建议使用 v2） |
| POST | `/v2/{model}` | 查询模型数据（推荐） |

---

## 二、查询模型数据

### 2.1 接口地址

```
POST /jdbc-model/query-model/v2/{model}
```

### 2.2 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 查询模型名称，如 `FactOrderQueryModel` |

### 2.3 请求体

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "orderStatus", "customer$caption", "totalAmount"],
        "slice": [
            { "field": "orderStatus", "op": "=", "value": "COMPLETED" }
        ],
        "groupBy": [
            { "field": "customer$customerType" }
        ],
        "orderBy": [
            { "field": "totalAmount", "order": "desc" }
        ],
        "totalColumn": true
    }
}
```

### 2.4 请求参数说明

#### 分页参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | integer | 否 | 1 | 页码，从 1 开始 |
| `pageSize` | integer | 否 | 10 | 每页条数 |
| `start` | integer | 否 | 0 | 起始记录数，与 page 二选一 |
| `limit` | integer | 否 | 10 | 返回条数，与 pageSize 二选一 |

#### param 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `columns` | string[] | 否 | 查询列，空则返回所有有权限的列 |
| `exColumns` | string[] | 否 | 排除列 |
| `slice` | SliceRequestDef[] | 否 | 过滤条件 |
| `groupBy` | GroupRequestDef[] | 否 | 分组字段 |
| `orderBy` | OrderRequestDef[] | 否 | 排序字段 |
| `totalColumn` | boolean | 否 | 是否返回总数及汇总数据 |

### 2.5 响应体

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

#### 响应字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | array | 明细数据列表（分页后的数据） |
| `total` | integer | 符合条件的总记录数 |
| `totalData` | object | 汇总数据（仅当 `totalColumn=true` 时返回） |

> **重要说明**：
> - `items` 只会按param指定groupBy进行聚合并返回明细数据（分页后的原始记录）
> - 若需要获取所有数据的聚合汇总值（如总金额），应设置 `totalColumn=true`，然后从 `totalData` 中获取
> - `totalData` 包含 `columns` 中指定的度量字段的聚合值

---

## 四、过滤条件 (slice)

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
// 查询 2024 年上半年的订单
{
    "field": "orderDate$caption",
    "op": "[)",
    "value": ["2024-01-01", "2024-07-01"]
}

// 查询金额在 100-500 之间（含边界）
{
    "field": "totalAmount",
    "op": "[]",
    "value": [100, 500]
}
```

### 4.4 逻辑连接 (link)

| 值 | 说明 |
|----|------|
| `1` 或不填 | AND 连接 |
| `2` | OR 连接 |

### 4.5 复合条件 (children)

支持嵌套条件：

```json
{
    "slice": [
        { "field": "orderStatus", "op": "=", "value": "COMPLETED" },
        {
            "link": 2,
            "children": [
                { "field": "totalAmount", "op": ">=", "value": 1000 },
                { "field": "customer$customerType", "op": "=", "value": "VIP", "link": 2 }
            ]
        }
    ]
}
```

生成的 SQL 条件：
```sql
WHERE order_status = 'COMPLETED'
  AND (total_amount >= 1000 OR customer_type = 'VIP')
```

---

## 五、分组 (groupBy)

### 5.1 基本格式

```json
{
    "groupBy": [
        { "field": "customer$customerType" },
        { "field": "orderDate$year" }
    ]
}
```

### 5.2 说明

- 设置 `groupBy` 后，度量字段会自动聚合
- 不设置 `groupBy` 返回明细数据
- 可以按维度属性分组：`维度名$属性名`

---

## 六、排序 (orderBy)

### 6.1 基本格式

```json
{
    "orderBy": [
        { "field": "totalAmount", "order": "desc" },
        { "field": "orderId", "order": "asc" }
    ]
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

## 七、字段引用格式

### 7.1 事实表字段

直接使用属性名或度量名：

```json
{
    "columns": ["orderId", "orderStatus", "totalAmount"]
}
```

### 7.2 维度字段

使用 `维度名$属性名` 格式：

| 格式 | 说明 | 示例 |
|------|------|------|
| `维度名$caption` | 维度显示值 | `customer$caption` |
| `维度名$id` | 维度ID值 | `customer$id` |
| `维度名$属性名` | 维度其他属性 | `customer$customerType` |

```json
{
    "columns": [
        "customer$caption",
        "customer$customerType",
        "customer$province",
        "orderDate$year",
        "orderDate$month"
    ]
}
```

---

## 八、完整请求示例

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
            "customer$caption",
            "customer$customerType",
            "totalAmount",
            "orderPayAmount"
        ],
        "slice": [
            { "field": "orderStatus", "op": "in", "value": ["COMPLETED", "SHIPPED"] },
            { "field": "totalAmount", "op": ">=", "value": 100 }
        ],
        "orderBy": [
            { "field": "orderTime", "order": "desc" }
        ],
        "totalColumn": true
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
            "totalAmount",
            "orderPayAmount"
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

### 8.3 KPI 汇总

```json
POST /jdbc-model/query-model/queryKpi

{
    "queryModel": "FactSalesQueryModel",
    "columns": ["quantity", "salesAmount", "profitAmount"],
    "slice": [
        { "field": "salesDate$caption", "op": "[)", "value": ["2024-01-01", "2024-07-01"] },
        { "field": "product$categoryName", "op": "=", "value": "数码电器" }
    ]
}
```

---

## 九、错误码

| 错误码 | 说明 |
|--------|------|
| `0` | 成功 |
| `400` | 请求参数错误 |
| `404` | 查询模型不存在 |
| `500` | 服务器内部错误 |

---

## 十、注意事项

1. **字段名大小写**：字段名区分大小写，使用驼峰命名
2. **维度属性**：维度属性使用 `$` 分隔，如 `customer$caption`
3. **分页限制**：建议单次查询不超过 1000 条
4. **聚合查询**：设置 `groupBy` 后，度量字段会自动按配置的聚合方式聚合
5. **权限控制**：返回的字段受查询模型中 `accesses` 配置的权限控制
