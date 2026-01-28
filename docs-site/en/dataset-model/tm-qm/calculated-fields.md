# Calculated Fields

<DownloadButton filename="calculated-fields.md" title="Download this document" />

Calculated fields allow dynamically defining new columns in queries using expressions, without modifying TM/QM models.

## 1. Overview

There are two ways to define calculated fields:

1. **calculatedFields parameter**: Defined in DSL requests
2. **Inline expressions**: Directly write `expression as alias` in columns

---

## 2. Define via calculatedFields

### 2.1 Basic Format

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "Net Sales Amount",
                "expression": "salesAmount - discountAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "discountAmount", "netAmount"]
    }
}
```

### 2.2 Field Description

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Field name, referenced in columns/slice/orderBy |
| `caption` | string | No | Display name |
| `expression` | string | Yes | Calculation expression |
| `description` | string | No | Field description |
| `agg` | string | No | Aggregation type (for autoGroupBy scenarios) |

### 2.3 Column References in Expressions

Expressions can reference:
- Property names in the model
- Measure names in the model
- Dimension columns: `dimension$caption`, `dimension$id`, `dimension$property`
- Other calculated field names (must be defined before current field)

---

## 3. Define via Inline Expressions

Use `expression as alias` format directly in columns:

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

### 3.1 Aggregation Expressions

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

> **Note**: Inline expression aliases cannot conflict with existing field names in the model, otherwise an error will be thrown.

---

## 4. Supported Expressions

### 4.1 Arithmetic Operations

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition | `salesAmount + taxAmount` |
| `-` | Subtraction | `salesAmount - discountAmount` |
| `*` | Multiplication | `unitPrice * quantity` |
| `/` | Division | `profitAmount / salesAmount` |
| `%` | Modulo | `quantity % 10` |

**Compound Expressions**:

```
(salesAmount - discountAmount) * 1.13
```

### 4.2 Math Functions

| Function | Description | Example |
|----------|-------------|---------|
| `ABS(x)` | Absolute value | `ABS(discountAmount)` |
| `ROUND(x, n)` | Rounding | `ROUND(salesAmount, 2)` |
| `CEIL(x)` | Ceiling | `CEIL(quantity / 10)` |
| `FLOOR(x)` | Floor | `FLOOR(quantity / 10)` |
| `MOD(x, y)` | Modulo | `MOD(quantity, 10)` |
| `POWER(x, y)` | Power | `POWER(2, 3)` |
| `SQRT(x)` | Square root | `SQRT(variance)` |

### 4.3 Date Functions

| Function | Description | Example |
|----------|-------------|---------|
| `YEAR(date)` | Extract year | `YEAR(salesDate$caption)` |
| `MONTH(date)` | Extract month | `MONTH(salesDate$caption)` |
| `DAY(date)` | Extract day | `DAY(salesDate$caption)` |
| `DATE(datetime)` | Extract date part | `DATE(orderTime)` |
| `NOW()` | Current time | `NOW()` |
| `DATE_ADD(date, interval)` | Date addition | `DATE_ADD(orderDate, 7)` |
| `DATE_SUB(date, interval)` | Date subtraction | `DATE_SUB(orderDate, 7)` |
| `DATEDIFF(d1, d2)` | Date difference | `DATEDIFF(NOW(), orderDate)` |

### 4.4 String Functions

| Function | Description | Example |
|----------|-------------|---------|
| `CONCAT(a, b, ...)` | String concatenation | `CONCAT(orderId, '-', orderLineNo)` |
| `SUBSTRING(s, start, len)` | Substring | `SUBSTRING(orderId, 1, 4)` |
| `UPPER(s)` | Uppercase | `UPPER(status)` |
| `LOWER(s)` | Lowercase | `LOWER(email)` |
| `TRIM(s)` | Trim spaces | `TRIM(customerName)` |
| `LENGTH(s)` | String length | `LENGTH(productName)` |

### 4.5 Null Handling Functions

| Function | Description | Example |
|----------|-------------|---------|
| `COALESCE(a, b, ...)` | Return first non-null value | `COALESCE(discountAmount, 0)` |
| `NULLIF(a, b)` | Return NULL if equal | `NULLIF(status, 'UNKNOWN')` |
| `IFNULL(a, b)` | Return default if null | `IFNULL(discountAmount, 0)` |

### 4.6 Aggregation Functions

| Function | Description | Example |
|----------|-------------|---------|
| `SUM(x)` | Sum | `SUM(salesAmount)` |
| `AVG(x)` | Average | `AVG(unitPrice)` |
| `COUNT(*)` | Count | `COUNT(*)` |
| `MAX(x)` | Maximum | `MAX(salesAmount)` |
| `MIN(x)` | Minimum | `MIN(salesAmount)` |

---

## 5. Complete Examples

### 5.1 Simple Arithmetic Expression

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "Net Sales Amount",
                "expression": "salesAmount - discountAmount"
            }
        ],
        "columns": ["orderId", "salesAmount", "discountAmount", "netAmount"]
    }
}
```

**Generated SQL**:

```sql
SELECT
    order_id AS orderId,
    sales_amount AS salesAmount,
    discount_amount AS discountAmount,
    (sales_amount - discount_amount) AS netAmount
FROM fact_sales
```

### 5.2 Profit Rate Calculation

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "profitRate",
                "caption": "Profit Rate (%)",
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

### 5.3 Chained Calculated Fields

Calculated fields can reference other calculated fields:

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "netAmount",
                "caption": "Net Sales Amount",
                "expression": "salesAmount - discountAmount"
            },
            {
                "name": "taxIncluded",
                "caption": "Tax Included Amount",
                "expression": "netAmount * 1.13"
            }
        ],
        "columns": ["orderId", "salesAmount", "netAmount", "taxIncluded"]
    }
}
```

### 5.4 Reference Dimension Columns

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "yearMonth",
                "caption": "Year-Month",
                "expression": "CONCAT(YEAR(salesDate$caption), '-', MONTH(salesDate$caption))"
            }
        ],
        "columns": ["orderId", "salesDate$caption", "yearMonth", "salesAmount"]
    }
}
```

### 5.5 Calculated Field as Filter Condition

```json
{
    "param": {
        "calculatedFields": [
            {
                "name": "profitRate",
                "caption": "Profit Rate",
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

### 5.6 Calculated Fields in Grouped Aggregations

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

## 6. Security

### 6.1 Prohibited Functions

The following functions will be intercepted and throw `SecurityException`:

- `EXEC`, `EXECUTE`
- `DROP`, `DELETE`, `UPDATE`, `INSERT`
- `CREATE`, `ALTER`, `TRUNCATE`
- Other functions not in the whitelist

### 6.2 Error Handling

| Error Type | Description |
|------------|-------------|
| Reference non-existent column | Throws exception indicating column doesn't exist |
| Duplicate calculated field name | Throws exception indicating name already exists |
| Alias conflicts with existing field | Throws exception indicating field name conflict |
| Using prohibited function | Throws SecurityException |

---

## 7. Best Practices

1. **Avoid division by zero**: Add `salesAmount > 0` condition before division
2. **Use COALESCE**: Handle fields that may be NULL
3. **Naming conventions**: Use meaningful names for calculated fields, avoid conflicts with existing fields
4. **Chain dependencies**: Referenced calculated fields must be defined before the referencing field
5. **Performance considerations**: Complex calculated fields may impact query performance

---

## Next Steps

- [JSON Query DSL](./query-dsl.md) - Complete DSL query syntax (recommended)
- [Query API](../api/query-api.md) - HTTP API reference
- [TM Syntax Manual](./tm-syntax.md) - Table model definitions
- [QM Syntax Manual](./qm-syntax.md) - Query model definitions
