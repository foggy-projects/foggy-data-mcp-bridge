# Quick Start

This guide helps you create your first TM/QM and use DSL queries in 10 minutes.

## Prerequisites

- JDK 17+
- Spring Boot 3.x
- MySQL 8.0+ (or other supported database)

## 1. Add Dependencies

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-model</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

## 2. Enable Foggy Framework

```java
@Configuration
@EnableFoggyFramework(bundleName = "my-app")
public class FoggyConfiguration {
}
```

## 3. Create Database Tables

```sql
-- Dimension table: Customers
CREATE TABLE dim_customer (
    customer_id INT PRIMARY KEY,
    customer_name VARCHAR(100),
    customer_type VARCHAR(20),
    province VARCHAR(50),
    city VARCHAR(50)
);

-- Dimension table: Products
CREATE TABLE dim_product (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    unit_price DECIMAL(10, 2)
);

-- Fact table: Orders
CREATE TABLE fact_order (
    order_id VARCHAR(50) PRIMARY KEY,
    customer_id INT,
    product_id INT,
    order_status VARCHAR(20),
    order_time DATETIME,
    quantity INT,
    amount DECIMAL(10, 2)
);

-- Insert test data
INSERT INTO dim_customer VALUES 
(1, 'Customer A', 'Enterprise', 'Beijing', 'Chaoyang'),
(2, 'Customer B', 'Individual', 'Shanghai', 'Pudong');

INSERT INTO dim_product VALUES
(1, 'Product X', 'Electronics', 99.00),
(2, 'Product Y', 'Clothing', 199.00);

INSERT INTO fact_order VALUES
('ORD001', 1, 1, 'COMPLETED', '2024-01-15 10:30:00', 2, 198.00),
('ORD002', 2, 2, 'COMPLETED', '2024-01-16 14:20:00', 1, 199.00);
```

## 4. Create TM Model

Create file `src/main/resources/foggy/templates/FactOrderModel.tm`:

```javascript
export const model = {
    name: 'FactOrderModel',
    caption: 'Order Fact Table',
    tableName: 'fact_order',
    idColumn: 'order_id',

    dimensions: [
        {
            name: 'customer',
            caption: 'Customer',
            tableName: 'dim_customer',
            foreignKey: 'customer_id',
            primaryKey: 'customer_id',
            captionColumn: 'customer_name',
            properties: [
                { column: 'customer_id', caption: 'Customer ID' },
                { column: 'customer_name', caption: 'Customer Name' },
                { column: 'customer_type', caption: 'Customer Type' },
                { column: 'province', caption: 'Province' },
                { column: 'city', caption: 'City' }
            ]
        },
        {
            name: 'product',
            caption: 'Product',
            tableName: 'dim_product',
            foreignKey: 'product_id',
            primaryKey: 'product_id',
            captionColumn: 'product_name',
            properties: [
                { column: 'product_id', caption: 'Product ID' },
                { column: 'product_name', caption: 'Product Name' },
                { column: 'category', caption: 'Category' },
                { column: 'unit_price', caption: 'Unit Price', type: 'MONEY' }
            ]
        }
    ],

    properties: [
        { column: 'order_id', caption: 'Order ID', type: 'STRING' },
        { column: 'order_status', caption: 'Order Status', type: 'STRING' },
        { column: 'order_time', caption: 'Order Time', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'quantity',
            caption: 'Order Quantity',
            type: 'INTEGER',
            aggregation: 'sum'
        },
        {
            column: 'amount',
            caption: 'Order Amount',
            type: 'MONEY',
            aggregation: 'sum'
        }
    ]
};
```

## 5. Create QM Model

Create file `src/main/resources/foggy/templates/FactOrderQueryModel.qm`:

```javascript
export const queryModel = {
    name: 'FactOrderQueryModel',
    caption: 'Order Analysis',
    model: 'FactOrderModel',

    columnGroups: [
        {
            caption: 'Order Info',
            items: [
                { name: 'orderId', caption: 'Order ID' },
                { name: 'orderStatus', caption: 'Order Status' },
                { name: 'orderTime', caption: 'Order Time' }
            ]
        },
        {
            caption: 'Customer Info',
            items: [
                { name: 'customer$caption', caption: 'Customer Name' },
                { name: 'customer$customerType', caption: 'Customer Type' },
                { name: 'customer$province', caption: 'Province' }
            ]
        },
        {
            caption: 'Product Info',
            items: [
                { name: 'product$caption', caption: 'Product Name' },
                { name: 'product$category', caption: 'Category' }
            ]
        },
        {
            caption: 'Metrics',
            items: [
                { name: 'quantity', caption: 'Quantity' },
                { name: 'amount', caption: 'Amount' }
            ]
        }
    ]
};
```

## 6. DSL Query Examples

### Detail Query

```json
{
    "columns": ["orderId", "customer$caption", "product$caption", "amount"],
    "slice": [
        {"field": "orderStatus", "op": "=", "value": "COMPLETED"}
    ],
    "orderBy": [{"field": "orderTime", "dir": "DESC"}],
    "limit": 10
}
```

### Aggregate Query

```json
{
    "columns": [
        "customer$caption",
        "sum(amount) as totalAmount",
        "count(*) as orderCount"
    ],
    "groupBy": [{"field": "customer$id"}],
    "orderBy": [{"field": "totalAmount", "dir": "DESC"}]
}
```

### Multi-condition Query

```json
{
    "columns": ["product$category", "sum(amount) as total"],
    "slice": [
        {"field": "orderTime", "op": "[)", "value": ["2024-01-01", "2024-02-01"]},
        {"field": "customer$customerType", "op": "=", "value": "Enterprise"}
    ],
    "groupBy": [{"field": "product$category"}]
}
```

## Common Operators Reference

| Operator | Description | Example Value |
|----------|-------------|---------------|
| `=` | Equal | `"COMPLETED"` |
| `!=` | Not equal | `"CANCELLED"` |
| `>` | Greater than | `100` |
| `>=` | Greater or equal | `100` |
| `<` | Less than | `1000` |
| `<=` | Less or equal | `1000` |
| `in` | In list | `["A", "B", "C"]` |
| `not in` | Not in list | `["X", "Y"]` |
| `like` | Fuzzy match | Auto adds `%` on both sides |
| `is null` | Is null | No value needed |
| `[]` | Closed interval | `[100, 500]` |
| `[)` | Left-closed right-open | `["2024-01-01", "2024-07-01"]` |

## Next Steps

- [TM Syntax Manual](../tm-qm/tm-syntax.md) - Complete TM definition syntax
- [QM Syntax Manual](../tm-qm/qm-syntax.md) - Complete QM definition syntax
- [Query API](../api/query-api.md) - Complete query API reference
- [Parent-Child Dimension](../tm-qm/parent-child.md) - Hierarchical dimension configuration
