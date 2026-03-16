Use fsscript to orchestrate multi-model queries. The script runs in a sandbox with only the `dsl()` function available.

## dsl() function

```javascript
const result = dsl({
    model: 'ModelName',     // Required: query model name
    columns: [...],         // Fields to select (same syntax as dataset.query_model)
    slice: [...],           // Filter conditions
    orderBy: [...],         // Sort: '-field' = desc, 'field' = asc
    limit: 100,             // Row limit
    start: 0,               // Offset
    returnTotal: false,     // Include total count
    distinct: false         // SELECT DISTINCT
});
```

Returns a `DataSetResult` with methods:
- `.column('field')` — extract unique values of one column (for ID pushdown)
- `.toList()` — all rows as array of objects
- `.first()` — first row
- `.size()` — row count
- `.value('field')` — scalar value from first row

## Pattern: ID Pushdown

Use the result of one query to filter another:

```javascript
const topCustomers = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id'],
    orderBy: ['-amountTotal'],
    limit: 10
});
const leads = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$caption', 'stage$caption', 'expectedRevenue'],
    slice: [{ field: 'partner$id', op: 'in', value: topCustomers.column('partner$id') }]
});
return leads;
```

## Pattern: Multi-step Analysis

```javascript
// Step 1: Get total sales per partner
const sales = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id', 'partner$caption', 'sum(amountTotal) as totalSales'],
    orderBy: ['-totalSales'],
    limit: 20
});

// Step 2: Get lead pipeline for those partners
const pipeline = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$id', 'count(id) as leadCount', 'sum(expectedRevenue) as pipeline'],
    slice: [{ field: 'partner$id', op: 'in', value: sales.column('partner$id') }]
});

return { sales: sales.toList(), pipeline: pipeline.toList() };
```

## Rules
- Always use `return` to output results
- Max 20 dsl() calls per script
- Only `dsl()`, `JSON`, and `console.log` are available (no file I/O, no network, no Java imports)
