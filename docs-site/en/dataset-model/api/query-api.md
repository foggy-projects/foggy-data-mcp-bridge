# DSL Query API

This document introduces the HTTP query interface of Foggy Dataset Model. For complete DSL syntax, please refer to [JSON Query DSL](../tm-qm/query-dsl.md).

## 1. Overview

### 1.1 Basic Information

- **Base URL**: `/jdbc-model/query-model`
- **Content-Type**: `application/json`
- **Response Format**: JSON

### 1.2 Interface List

| Method | Path | Description |
|------|------|------|
| POST | `/v2/{model}` | Query model data (recommended) |
| POST | `/queryKpi` | KPI summary query |

---

## 2. Query Model Data

### 2.1 Interface Address

```
POST /jdbc-model/query-model/v2/{model}
```

### 2.2 Path Parameters

| Parameter | Type | Required | Description |
|------|------|------|------|
| `model` | string | Yes | Query model name, e.g., `FactOrderQueryModel` |

### 2.3 Request Body Structure

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

### 2.4 Pagination Parameters

| Parameter | Type | Required | Default | Description |
|------|------|------|--------|------|
| `page` | integer | No | 1 | Page number, starting from 1 |
| `pageSize` | integer | No | 10 | Number of items per page |
| `start` | integer | No | 0 | Starting record number (choose one with page) |
| `limit` | integer | No | 10 | Number of items to return (choose one with pageSize) |

### 2.5 param Parameters

| Parameter | Type | Required | Description |
|------|------|------|------|
| `columns` | string[] | No | Query columns, empty returns all columns with permissions. See [Field Reference Format](../tm-qm/query-dsl.md#_2-field-reference-format) |
| `exColumns` | string[] | No | Exclude columns |
| `slice` | SliceRequestDef[] | No | Filter conditions. See [Filter Conditions](../tm-qm/query-dsl.md#_3-filter-conditions-slice) |
| `groupBy` | GroupRequestDef[] | No | Group by fields. See [Grouping](../tm-qm/query-dsl.md#_4-grouping-groupby) |
| `orderBy` | OrderRequestDef[] | No | Order by fields. See [Sorting](../tm-qm/query-dsl.md#_5-sorting-orderby) |
| `calculatedFields` | CalculatedFieldDef[] | No | Dynamic calculated fields. See [Calculated Fields](../tm-qm/query-dsl.md#_6-dynamic-calculated-fields-calculatedfields) |
| `returnTotal` | boolean | No | Whether to return total count and summary data |

---

## 3. KPI Summary Query

Quickly get measure summary values without pagination.

### 3.1 Interface Address

```
POST /jdbc-model/query-model/queryKpi
```

### 3.2 Request Body Structure

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

### 3.3 Request Parameters

| Parameter | Type | Required | Description |
|------|------|------|------|
| `queryModel` | string | Yes | Query model name |
| `columns` | string[] | Yes | Measure fields to summarize |
| `slice` | SliceRequestDef[] | No | Filter conditions |

### 3.4 Response Example

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

## 4. Response Structure

### 4.1 Response Body

```json
{
    "code": 0,
    "data": {
        "items": [
            {
                "orderId": "ORD202401010001",
                "orderStatus": "COMPLETED",
                "customer$caption": "Customer A",
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

### 4.2 Response Fields

| Field | Type | Description |
|------|------|------|
| `code` | integer | Status code, 0 indicates success |
| `data.items` | array | Detail data list (paginated data) |
| `data.total` | integer | Total number of records matching conditions |
| `data.totalData` | object | Summary data (returned only when `returnTotal=true`) |
| `msg` | string | Message |

### 4.3 totalData Description

- Contains aggregated values of measure fields specified in `columns`
- Aggregates **all data matching conditions**, not affected by pagination
- Returned only when `returnTotal=true` is set

---

## 5. Error Handling

### 5.1 Error Codes

| Error Code | Description |
|--------|------|
| `0` | Success |
| `400` | Request parameter error |
| `403` | No permission to access |
| `404` | Query model not found |
| `500` | Server internal error |

### 5.2 Error Response Example

```json
{
    "code": 404,
    "msg": "Query model 'InvalidModel' not found",
    "data": null
}
```

---

## 6. Quick Examples

### 6.1 Detail Query

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

### 6.2 Group Summary

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

For more examples, please refer to [JSON Query DSL - Complete Examples](../tm-qm/query-dsl.md#_10-complete-examples).

---

## Next Steps

- [JSON Query DSL](../tm-qm/query-dsl.md) - **Complete DSL Syntax (Recommended Reading)**
- [TM Syntax Manual](../tm-qm/tm-syntax.md) - Table model definition
- [QM Syntax Manual](../tm-qm/qm-syntax.md) - Query model definition
- [Row-Level Permission Control](./authorization.md) - Row-level data isolation
