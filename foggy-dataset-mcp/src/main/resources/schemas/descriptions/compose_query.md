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
- `.isEmpty()` — check if result is empty
- `.value('field')` — scalar value from first row
- `.withJoin(rightDs, joinType, joinKey)` — CTE/subquery composition, same dataSource only (lazy execution)
- `.joinInMemory(rightDs, joinType, joinKey)` — in-memory Hash JOIN, works across different dataSources
- `.joinInMemory(rightDs, joinType, leftKey, rightKey)` — in-memory Hash JOIN with different key names
- `.filter(expr)` — in-memory row filter using boolean expression
- `.sort(field)` — in-memory sort; prefix `-` for descending (e.g. `'-amount'`)
- `.compute(name, expr)` — add computed column via expression

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

## Pattern: CTE Composition (withJoin)

Join results from two different QM models at the database level using CTE (or subquery fallback for MySQL 5.7):

```javascript
const sales = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id', 'sum(amountTotal) as totalSales']
});
const leads = dsl({
    model: 'CrmLeadQM',
    columns: ['partner$id', 'count(id) as leadCount']
});
// LEFT JOIN at DB level — generates WITH cte_0 AS (...), cte_1 AS (...) SELECT ...
return sales.withJoin(leads, 'LEFT', 'partner$id');
```

Supported join types: `LEFT`, `INNER`.

## Pattern: Cross-Database JOIN (joinInMemory)

When two models are on different dataSources (cross-database), use `joinInMemory` instead of `withJoin`.
Each query executes on its own database, results are merged via Hash JOIN in Java memory.

```javascript
const orders = dsl({
    model: 'SaleOrderQM',    // on MySQL
    columns: ['partner$id', 'sum(amountTotal) as totalSales']
});
const employees = dsl({
    model: 'HrEmployeeQM',   // on PostgreSQL
    columns: ['partner$id', 'name', 'department$caption']
});
// In-memory Hash JOIN (O(n+m)), no CTE — works across databases
return orders.joinInMemory(employees, 'LEFT', 'partner$id');
```

Different key names:

```javascript
return orders.joinInMemory(shipments, 'INNER', 'orderId', 'orderRef');
```

Combine with filter/sort/compute:

```javascript
return orders.joinInMemory(employees, 'INNER', 'partner$id')
    .filter("totalSales > 10000")
    .sort('-totalSales');
```

**Choosing withJoin vs joinInMemory:**
- Same `dataSourceGroup` → `withJoin` (SQL-level, no row limit)
- Different `dataSourceGroup` → `joinInMemory` (in-memory, subject to JVM memory)

## Pattern: In-Memory Transformations (filter / sort / compute)

Post-process query results without additional SQL queries. Each method returns a new `DataSetResult` (immutable).

```javascript
const ds = dsl({
    model: 'SaleOrderQM',
    columns: ['partner$caption', 'amountTotal', 'status']
});

// Filter: keep rows where expression is truthy
const big = ds.filter('amountTotal > 1000');

// Sort: prefix '-' for descending
const sorted = big.sort('-amountTotal');

// Compute: add a derived column
const withMargin = sorted.compute('margin', 'amountTotal * 0.1');

return withMargin;
```

Chain calls fluently:

```javascript
return dsl({ model: 'SaleOrderQM', columns: ['partner$caption', 'amountTotal', 'status'] })
    .filter("status == 'confirmed'")
    .sort('-amountTotal')
    .compute('rank', 'amountTotal');
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
