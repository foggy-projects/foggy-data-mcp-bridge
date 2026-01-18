# Pre-Aggregation

Pre-aggregation is a technique to accelerate queries by pre-calculating aggregation results. The system automatically selects appropriate pre-aggregation tables during query execution without modifying query code.

## Overview

### How It Works

```
User Query → Match Pre-Aggregation → Rewrite SQL → Query Pre-Aggregation Table → Return Result
              ↓
          No Match
              ↓
        Query Original Table
```

### Use Cases

- Large-scale aggregation queries (millions of records)
- Report queries with fixed dimension combinations
- Scenarios with strict query response time requirements

## Configuring Pre-Aggregation

Add `preAggregations` configuration in TM file:

```javascript
export const model = {
    name: 'FactSalesModel',
    tableName: 'fact_sales',
    dimensions: [...],
    measures: [...],

    // Pre-aggregation configuration
    preAggregations: [
        {
            name: 'daily_product_sales',
            caption: 'Daily Sales Summary by Product',
            tableName: 'preagg_daily_product_sales',
            priority: 80,
            dimensions: ['salesDate', 'product'],
            granularity: {
                salesDate: 'day'
            },
            dimensionProperties: {
                salesDate: ['year', 'quarter', 'month'],
                product: ['category_name', 'brand']
            },
            measures: [
                { name: 'quantity', aggregation: 'SUM' },
                { name: 'salesAmount', aggregation: 'SUM' },
                { name: 'orderCount', aggregation: 'COUNT' }
            ],
            refresh: {
                strategy: 'INCREMENTAL',
                schedule: '0 2 * * *',
                watermarkColumn: 'salesDate$id',
                lookbackDays: 3
            },
            enabled: true
        }
    ]
};
```

## Configuration Details

### Basic Configuration

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | Yes | Pre-aggregation name (unique identifier) |
| `caption` | string | No | Display name |
| `tableName` | string | Yes | Pre-aggregation table name |
| `schema` | string | No | Database schema |
| `priority` | int | No | Priority 1-100, default 50 |
| `enabled` | boolean | No | Whether enabled, default true |

### Dimension Configuration

| Parameter | Type | Description |
|-----------|------|-------------|
| `dimensions` | string[] | List of dimension names included |
| `granularity` | object | Time dimension granularity configuration |
| `dimensionProperties` | object | Properties included for each dimension |

**Time Granularity Options**:

| Granularity | Description | Example |
|-------------|-------------|---------|
| `minute` | Minute | 2024-01-15 10:30 |
| `hour` | Hour | 2024-01-15 10:00 |
| `day` | Day | 2024-01-15 |
| `week` | Week | 2024-W03 |
| `month` | Month | 2024-01 |
| `quarter` | Quarter | 2024-Q1 |
| `year` | Year | 2024 |

### Measure Configuration

```javascript
measures: [
    { name: 'quantity', aggregation: 'SUM' },
    { name: 'salesAmount', aggregation: 'SUM' },
    { name: 'orderCount', aggregation: 'COUNT' },
    { name: 'minPrice', aggregation: 'MIN' },
    { name: 'maxPrice', aggregation: 'MAX' }
]
```

**Supported Aggregation Types**:

| Aggregation | Description | Can Rollup |
|-------------|-------------|------------|
| `SUM` | Sum | Yes |
| `COUNT` | Count | Yes (converts to SUM) |
| `MIN` | Minimum | Yes |
| `MAX` | Maximum | Yes |
| `AVG` | Average | Requires SUM+COUNT |

### Refresh Configuration

```javascript
refresh: {
    strategy: 'INCREMENTAL',  // FULL | INCREMENTAL
    schedule: '0 2 * * *',    // Cron expression
    watermarkColumn: 'salesDate$id',
    lookbackDays: 3
}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `strategy` | string | Refresh strategy: FULL (full), INCREMENTAL (incremental) |
| `schedule` | string | Cron expression |
| `watermarkColumn` | string | Watermark column (for incremental refresh) |
| `lookbackDays` | int | Lookback days (for handling delayed data) |

### Filter Conditions

You can configure permanent filter conditions for pre-aggregation:

```javascript
filters: [
    { column: 'orderStatus', operator: 'eq', value: 'COMPLETED' },
    { column: 'salesDate', operator: 'gte', value: '2023-01-01' }
]
```

## Matching Rules

The system automatically matches pre-aggregation based on the following rules:

### 1. Dimension Matching

Query dimensions must be a subset of pre-aggregation dimensions.

```
Query: [salesDate, product]
Pre-aggregation: [salesDate, product, customer]
Result: Match ✓
```

### 2. Property Matching

Query dimension properties must exist in pre-aggregation.

```
Query: product$category_name
Pre-aggregation dimensionProperties: { product: ['category_name', 'brand'] }
Result: Match ✓
```

**Note**: `caption` and `id` are implicit properties and don't need explicit configuration.

### 3. Granularity Matching

Query granularity must be >= pre-aggregation granularity (supports rollup).

```
Pre-aggregation granularity: day
Query salesDate$month: Match ✓ (day → month can rollup)
Query salesDate$hour: No match ✗ (day → hour cannot drill down)
```

### 4. Measure Matching

Query measures must exist in pre-aggregation, and aggregation methods must be compatible.

### 5. Priority Selection

When multiple pre-aggregations satisfy conditions, selection follows this order:

1. Higher `priority` value
2. Dimension count closer to query
3. Finer granularity

## Creating Pre-Aggregation Tables

### Manual Creation

Manually create pre-aggregation table based on configuration:

```sql
CREATE TABLE preagg_daily_product_sales (
    -- Dimension columns
    sales_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    product_category_name VARCHAR(100),
    product_brand VARCHAR(100),

    -- Measure columns
    quantity_sum DECIMAL(20,4),
    sales_amount_sum DECIMAL(20,4),
    order_count BIGINT,

    -- Metadata columns
    _preagg_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    _preagg_updated_at TIMESTAMP,

    PRIMARY KEY (sales_date, product_id)
);

CREATE INDEX idx_preagg_daily_sales_date
    ON preagg_daily_product_sales(sales_date);
```

### Create via API

```
POST /api/pre-agg/{modelName}/{preAggName}/create-table
```

## Refreshing Pre-Aggregation

### Automatic Refresh

After configuring `refresh.schedule`, the system automatically refreshes according to Cron expression.

### Manual Refresh

```
POST /api/pre-agg/{modelName}/{preAggName}/refresh
```

### Refresh Strategy

**Full Refresh (FULL)**:
```sql
TRUNCATE TABLE preagg_daily_product_sales;

INSERT INTO preagg_daily_product_sales (...)
SELECT DATE(order_date), product_id, SUM(quantity), ...
FROM fact_sales
LEFT JOIN dim_product ON ...
GROUP BY DATE(order_date), product_id;
```

**Incremental Refresh (INCREMENTAL)**:
```sql
-- Delete affected data (including lookback)
DELETE FROM preagg_daily_product_sales
WHERE sales_date >= '2024-01-08';

-- Insert new data
INSERT INTO preagg_daily_product_sales (...)
SELECT ... FROM fact_sales
WHERE order_date >= '2024-01-08'
GROUP BY ...;
```

## Hybrid Query

When pre-aggregation data may be incomplete (e.g., incremental refresh scenarios), the system automatically enables hybrid query:

```
Pre-aggregation Data (before watermark)
    UNION ALL
Original Table Data (after watermark)
```

### Watermark Detection

The system determines data freshness through `watermarkColumn` and `dataWatermark`:

- Watermark is today or later: Data is not expired
- Watermark is before today: Enable hybrid query

## Viewing Pre-Aggregation Status

### Information in Query Results

Query result's `extData` includes pre-aggregation usage information:

```json
{
  "data": [...],
  "extData": {
    "preAggUsed": "daily_product_sales",
    "preAggMode": "direct",  // direct | rollup | hybrid
    "originalSql": "..."
  }
}
```

### Management API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/pre-agg/list` | GET | List all pre-aggregations |
| `/api/pre-agg/{model}/{name}/status` | GET | Get status |
| `/api/pre-agg/{model}/{name}/refresh` | POST | Manual refresh |

## Configuration Examples

### Multi-Granularity Configuration

Configure pre-aggregations with multiple granularities for the same data source:

```javascript
preAggregations: [
    {
        name: 'daily_sales',
        tableName: 'preagg_daily_sales',
        priority: 80,
        dimensions: ['salesDate', 'product'],
        granularity: { salesDate: 'day' },
        measures: [...]
    },
    {
        name: 'monthly_sales',
        tableName: 'preagg_monthly_sales',
        priority: 60,
        dimensions: ['salesDate', 'product'],
        granularity: { salesDate: 'month' },
        measures: [...]
    }
]
```

### Multi-Dimension Combinations

Configure pre-aggregations for different dimension combinations:

```javascript
preAggregations: [
    {
        name: 'product_sales',
        tableName: 'preagg_product_sales',
        priority: 70,
        dimensions: ['salesDate', 'product'],
        ...
    },
    {
        name: 'customer_sales',
        tableName: 'preagg_customer_sales',
        priority: 70,
        dimensions: ['salesDate', 'customer'],
        ...
    },
    {
        name: 'channel_sales',
        tableName: 'preagg_channel_sales',
        priority: 70,
        dimensions: ['salesDate', 'channel'],
        ...
    }
]
```

## Best Practices

### 1. Analyze Query Patterns

Before configuring pre-aggregation, analyze actual query patterns:

- Which dimension combinations are most commonly used?
- What time granularity is needed?
- Which measures need pre-calculation?

### 2. Set Reasonable Priorities

- Set higher priority for fine-grained pre-aggregations
- Set higher priority for pre-aggregations corresponding to frequently used queries

### 3. Balance Storage and Performance

- Pre-aggregation tables consume additional storage space
- Finer granularity means more storage usage
- Recommend configuring pre-aggregation only for high-frequency queries

### 4. Monitor Refresh Status

Regularly check pre-aggregation refresh status to ensure data freshness.

### 5. Use with Cache

Pre-aggregation + cache can achieve optimal performance:

- Pre-aggregation reduces SQL computation
- Cache avoids repeated queries

## Common Questions

### Q: Pre-aggregation is not being used?

Check the following:
1. Is `enabled` set to `true`
2. Are query dimensions/measures in pre-aggregation
3. Does time granularity meet requirements
4. Check matching information in logs

### Q: Pre-aggregation data is inconsistent with original data?

Possible reasons:
1. Pre-aggregation not refreshed in time
2. `lookbackDays` for incremental refresh set too small
3. Filter conditions configured incorrectly

### Q: How to force use or disable pre-aggregation?

Currently not supported to specify in query. You can:
1. Adjust `priority` to affect matching order
2. Set `enabled: false` to disable specific pre-aggregation
