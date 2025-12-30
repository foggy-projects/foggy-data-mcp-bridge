# DSL 查询 API

本文档介绍 Foggy Dataset Model 的 HTTP 查询接口。完整的 DSL 语法请参阅 [JSON 查询 DSL](../tm-qm/query-dsl.md)。

## 1. 概述

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
        "calculatedFields": [...],
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
| `columns` | string[] | 否 | 查询列，空则返回所有有权限的列。详见 [字段引用格式](../tm-qm/query-dsl.md#_2-字段引用格式) |
| `exColumns` | string[] | 否 | 排除列 |
| `slice` | SliceRequestDef[] | 否 | 过滤条件。详见 [过滤条件](../tm-qm/query-dsl.md#_3-过滤条件-slice) |
| `groupBy` | GroupRequestDef[] | 否 | 分组字段。详见 [分组](../tm-qm/query-dsl.md#_4-分组-groupby) |
| `orderBy` | OrderRequestDef[] | 否 | 排序字段。详见 [排序](../tm-qm/query-dsl.md#_5-排序-orderby) |
| `calculatedFields` | CalculatedFieldDef[] | 否 | 动态计算字段。详见 [计算字段](../tm-qm/query-dsl.md#_6-动态计算字段-calculatedfields) |
| `returnTotal` | boolean | 否 | 是否返回总数及汇总数据 |

---

## 3. KPI 汇总查询

快捷获取度量汇总值，无需分页。

### 3.1 接口地址

```
POST /jdbc-model/query-model/queryKpi
```

### 3.2 请求体结构

```json
{
    "queryModel": "FactSalesQueryModel",
    "columns": ["salesQuantity", "salesAmount", "profitAmount"],
    "slice": [
        { "field": "salesDate$caption", "op": "[)", "value": ["2024-01-01", "2024-07-01"] },
        { "field": "product$category", "op": "=", "value": "数码电器" }
    ]
}
```

### 3.3 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `queryModel` | string | 是 | 查询模型名称 |
| `columns` | string[] | 是 | 要汇总的度量字段 |
| `slice` | SliceRequestDef[] | 否 | 过滤条件 |

### 3.4 响应示例

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

## 4. 响应结构

### 4.1 响应体

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

### 4.2 响应字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 状态码，0 表示成功 |
| `data.items` | array | 明细数据列表（分页后的数据） |
| `data.total` | integer | 符合条件的总记录数 |
| `data.totalData` | object | 汇总数据（仅当 `returnTotal=true` 时返回） |
| `msg` | string | 消息 |

### 4.3 totalData 说明

- 包含 `columns` 中指定的度量字段的聚合值
- 是对**所有符合条件的数据**进行聚合，不受分页影响
- 只有设置 `returnTotal=true` 时才返回

---

## 5. 错误处理

### 5.1 错误码

| 错误码 | 说明 |
|--------|------|
| `0` | 成功 |
| `400` | 请求参数错误 |
| `403` | 无权限访问 |
| `404` | 查询模型不存在 |
| `500` | 服务器内部错误 |

### 5.2 错误响应示例

```json
{
    "code": 404,
    "msg": "Query model 'InvalidModel' not found",
    "data": null
}
```

---

## 6. 快速示例

### 6.1 明细查询

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "orderStatus", "customer$caption", "totalAmount"],
        "slice": [
            { "field": "orderStatus", "op": "in", "value": ["COMPLETED", "SHIPPED"] }
        ],
        "orderBy": [
            { "field": "totalAmount", "order": "desc" }
        ],
        "returnTotal": true
    }
}
```

### 6.2 分组汇总

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
    "page": 1,
    "pageSize": 100,
    "param": {
        "columns": ["customer$customerType", "totalQuantity", "totalAmount"],
        "groupBy": [
            { "field": "customer$customerType" }
        ],
        "orderBy": [
            { "field": "totalAmount", "order": "desc" }
        ]
    }
}
```

更多示例请参阅 [JSON 查询 DSL - 完整示例](../tm-qm/query-dsl.md#_10-完整示例)。

---

## 下一步

- [JSON 查询 DSL](../tm-qm/query-dsl.md) - **DSL 完整语法（推荐阅读）**
- [TM 语法手册](../tm-qm/tm-syntax.md) - 表格模型定义
- [QM 语法手册](../tm-qm/qm-syntax.md) - 查询模型定义
- [权限控制](./authorization.md) - 详细的权限配置
