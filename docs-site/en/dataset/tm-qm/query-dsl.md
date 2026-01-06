# JSON Query DSL Syntax

This document describes the complete JSON Query DSL (Domain Specific Language) syntax for Foggy Dataset Model.

## 1. Overview

JSON Query DSL is a declarative query language that describes query conditions, field selection, grouping, sorting, and other operations in JSON format. The system parses the DSL and converts it to SQL for execution.

### 1.1 Request Structure

```json
{
    "page": 1,                          // Page number (starts from 1)
    "pageSize": 20,                     // Page size
    "param": {
        "columns": [...],               // Query columns
        "slice": [...],                 // Filter conditions
        "groupBy": [...],               // Grouping fields
        "orderBy": [...],               // Sorting fields
        "calculatedFields": [...],      // Dynamic calculated fields
        "returnTotal": true             // Whether to return totals
    }
}
```

---

## 2. Field Reference Format

### 2.1 Reference Types

| Format | Description | Example |
|--------|-------------|---------|
| `propertyName` | Fact table property | `orderId`, `orderStatus` |
| `measureName` | Measure field | `totalAmount`, `quantity` |
| `dimension$id` | Dimension ID | `customer$id` |
| `dimension$caption` | Dimension display value | `customer$caption` |
| `dimension$property` | Dimension property | `customer$customerType` |
| `dimension$hierarchy$id` | Parent-child hierarchy view | `team$hierarchy$id` |
| `nested.dimension$property` | Nested dimension property | `product.category$caption` |

### 2.2 Nested Dimension Reference

For multi-level nested dimensions, use `.` to separate paths:

```json
{
    "columns": [
        "product$caption",                    // Level 1 dimension
        "product.category$caption",           // Level 2 dimension
        "product.category.group$caption"      // Level 3 dimension
    ]
}
```

---

## 3. Filter Conditions (slice)

### 3.1 Basic Structure

```json
{
    "field": "fieldName",
    "op": "operator",
    "value": "value",
    "link": 1,              // Logical connection: 1=AND, 2=OR
    "maxDepth": 2,          // Hierarchy depth limit (hierarchy operators only)
    "children": [...]       // Nested conditions
}
```

### 3.2 Operator List

#### Comparison Operators

| Operator | Description | Value Type | Example |
|----------|-------------|------------|---------|
| `=` | Equal | any | `{ "op": "=", "value": "A" }` |
| `!=` | Not equal | any | `{ "op": "!=", "value": "B" }` |
| `>` | Greater than | number | `{ "op": ">", "value": 100 }` |
| `>=` | Greater or equal | number | `{ "op": ">=", "value": 100 }` |
| `<` | Less than | number | `{ "op": "<", "value": 1000 }` |
| `<=` | Less or equal | number | `{ "op": "<=", "value": 1000 }` |

#### Set Operators

| Operator | Description | Value Type | Example |
|----------|-------------|------------|---------|
| `in` | In list | array | `{ "op": "in", "value": ["A", "B", "C"] }` |
| `not in` | Not in list | array | `{ "op": "not in", "value": ["X", "Y"] }` |

#### Pattern Matching Operators

| Operator | Description | Wildcard Handling | Example |
|----------|-------------|-------------------|---------|
| `like` | Pattern match | Auto adds `%...%` | `{ "op": "like", "value": "keyword" }` |
| `left_like` | Left match | Auto adds `%...` | `{ "op": "left_like", "value": "suffix" }` |
| `right_like` | Right match | Auto adds `...%` | `{ "op": "right_like", "value": "prefix" }` |

#### Null Operators

| Operator | Description | Value | Example |
|----------|-------------|-------|---------|
| `is null` | Is null | not needed | `{ "op": "is null" }` |
| `is not null` | Is not null | not needed | `{ "op": "is not null" }` |

#### Range Operators

| Operator | Description | Boundaries | Example |
|----------|-------------|------------|---------|
| `[]` | Closed interval | Includes both | `{ "op": "[]", "value": [100, 500] }` |
| `[)` | Left-closed right-open | Includes left | `{ "op": "[)", "value": ["2024-01-01", "2024-07-01"] }` |
| `(]` | Left-open right-closed | Includes right | `{ "op": "(]", "value": [0, 100] }` |
| `()` | Open interval | Excludes both | `{ "op": "()", "value": [0, 100] }` |

#### Hierarchy Operators (Parent-Child Dimensions)

| Operator | Description | Includes Self | Example |
|----------|-------------|---------------|---------|
| `childrenOf` | Direct children | No | `{ "op": "childrenOf", "value": "T001" }` |
| `descendantsOf` | All descendants | No | `{ "op": "descendantsOf", "value": "T001" }` |
| `selfAndDescendantsOf` | Self and descendants | Yes | `{ "op": "selfAndDescendantsOf", "value": "T001" }` |

### 3.3 Logical Connection (link)

| Value | Description |
|-------|-------------|
| `1` or omit | AND connection (default) |
| `2` | OR connection |

### 3.4 Nested Conditions (children)

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

---

## 4. Grouping (groupBy)

### 4.1 Basic Format

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

### 4.2 Aggregation Types

| Type | Description |
|------|-------------|
| `SUM` | Sum |
| `AVG` | Average |
| `COUNT` | Count |
| `MAX` | Maximum |
| `MIN` | Minimum |

---

## 5. Sorting (orderBy)

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

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `field` | string | Yes | Sort field name |
| `order` | string | Yes | `asc` (ascending) / `desc` (descending) |
| `nullFirst` | boolean | No | NULL values first |
| `nullLast` | boolean | No | NULL values last |

---

## 6. Dynamic Calculated Fields (calculatedFields)

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "profitRate",
                "caption": "Profit Rate",
                "expression": "profitAmount / salesAmount * 100",
                "agg": "SUM"
            }
        ],
        "columns": ["product$caption", "profitRate"]
    }
}
```

### Supported Functions

- **Math**: `ABS`, `ROUND`, `CEIL`, `FLOOR`, `MOD`, `POWER`, `SQRT`
- **Date**: `YEAR`, `MONTH`, `DAY`, `DATE`, `NOW`, `DATE_ADD`, `DATE_SUB`, `DATEDIFF`
- **String**: `CONCAT`, `SUBSTRING`, `UPPER`, `LOWER`, `TRIM`, `LENGTH`
- **Other**: `COALESCE`, `NULLIF`, `IFNULL`

---

## 7. Pagination

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | 1 | Page number (starts from 1) |
| `pageSize` | integer | 10 | Page size |
| `start` | integer | 0 | Start record (alternative to page) |
| `limit` | integer | 10 | Record limit (alternative to pageSize) |

---

## 8. Response Structure

```json
{
    "code": 0,
    "data": {
        "items": [...],
        "total": 100,
        "totalData": {
            "total": 100,
            "totalAmount": 129900.00
        }
    },
    "msg": "success"
}
```

---

## 9. Complete Examples

### Detail Query

```json
{
    "page": 1,
    "pageSize": 20,
    "param": {
        "columns": ["orderId", "customer$caption", "product$caption", "totalAmount"],
        "slice": [
            { "field": "orderStatus", "op": "in", "value": ["COMPLETED", "SHIPPED"] },
            { "field": "orderTime", "op": "[)", "value": ["2024-01-01", "2024-07-01"] }
        ],
        "orderBy": [{ "field": "orderTime", "order": "desc" }]
    }
}
```

### Aggregation Query

```json
{
    "page": 1,
    "pageSize": 100,
    "param": {
        "columns": ["customer$customerType", "totalQuantity", "totalAmount"],
        "groupBy": [{ "field": "customer$customerType" }],
        "orderBy": [{ "field": "totalAmount", "order": "desc" }]
    }
}
```

---

## Next Steps

- [TM Syntax Manual](./tm-syntax.md) - Table model definition
- [QM Syntax Manual](./qm-syntax.md) - Query model definition
- [Parent-Child Dimensions](./parent-child.md) - Hierarchy dimension details
- [Calculated Fields](./calculated-fields.md) - Complex calculation logic
- [Query API](../api/query-api.md) - HTTP API reference
