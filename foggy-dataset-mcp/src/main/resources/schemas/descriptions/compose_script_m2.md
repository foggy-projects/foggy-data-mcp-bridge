# dataset.compose_script (M2 DSL)

Use this tool to orchestrate multi-model queries, complex aggregations, and JOINs. The script runs in a secure sandbox.
**Language**: FSScript (syntax identical to JavaScript ES5). Only `Query`, `dsl`, `JSON`, `console.log` are available — no file I/O, no network, no `eval`.
The recommended standard is the **Object-Oriented Chain API** (`Query.from(...)`). For very simple extractions, the legacy `dsl({...})` is still supported.

## Core API: `Query.from()`

`Query.from("ModelName")` returns a `QueryPlan` representing the base physical model.
**All fields should be referenced via the plan object**: `plan.fieldName`.

You can chain `.where()`, `.groupBy()`, `.orderBy()`, `.limit()`, and `.offset()` to build logic.

**Semantic Invariants**:
1. **`.select()` cuts a new schema stage**: After `.select()`, only the projected columns exist. Field references are **bound to their stage**; after `.select()` creates a new stage, the new plan exposes new field references (e.g. `orderSummary.totalAmount`).
2. **`.where()` syntax**: ALWAYS uses an array of JSON slice objects (`[{ field: "str", op: "=", value: 1 }]`). This is the ONLY method that uses string field names. **The `field` string refers to the current stage's output column name (i.e. alias)**, not the underlying physical field name.
3. **`.where()` on Derived Plans**: Calling `.where()` AFTER `.select()` applies an outer `WHERE` on the derived table (CTE/subquery). In aggregation scenarios, this is **effect-equivalent** to SQL `HAVING`, but the engine does not generate `HAVING` — it always wraps as an outer filter.
4. **`.orderBy()` uses strings (not FieldRef)**: Because sorting never involves cross-table disambiguation — it always operates on the current single stage's output columns. You can only order by a field that exists in the current schema. If ordering by an alias, you MUST `.orderBy()` AFTER `.select()`.
5. **Special field names**: Fields containing `$` (like `category_id$caption`) work as normal JS property access. For other special chars (like `-`), use `plan["field-name"]`.
6. **UNION field inheritance**: After `.union()`, the resulting plan's field references **inherit from the left-side schema**. Right-side aligns by position but does not expose separate references.

### 1. Simple Extraction & Aggregation
```javascript
const sales = Query.from("OdooSaleOrderModel");

const orderSummary = sales
    .where([{ field: "status", op: "=", value: "done" }])
    .groupBy(sales.partnerId)
    .select(
        sales.partnerId, 
        sales.amountTotal.sum().as("totalAmount", "Total Amount") // Object-oriented aggregation!
    )
    .orderBy("-totalAmount")
    .limit(10);

return orderSummary.execute();
```

**Supported aggregations on columns**: `.sum()`, `.count()`, `.avg()`, `.max()`, `.min()`.

### 2. Derived Query (Two-stage aggregation)
```javascript
// Step 1: Sales per customer
const sales = Query.from("OdooSaleOrderModel");
const customerSales = sales
    .groupBy(sales.partnerId)
    .select(sales.partnerId, sales.amountTotal.sum().as("totalSpent"));

// Step 2: Distribution of top spenders
// NOTE: .where() here applies outer WHERE on derived table; effect-equivalent to SQL HAVING
return customerSales
    .where([{ field: "totalSpent", op: ">", value: 100000 }])
    .select(customerSales.partnerId.count().as("premiumCount"))
    .execute();
```

### 3. JOINing Multiple Models
**Golden Rule (Aggregate First, Join Later)**: To prevent cartesian explosion, ALWAYS aggregate data in separate `QueryPlan`s BEFORE joining them. Direct 1:N JOINs without aggregation will inflate metrics (e.g. `customer.creditLimit` will sum up N times!).

```javascript
const customers = Query.from("OdooResPartnerModel");
const orders = Query.from("OdooSaleOrderModel");

// 1. Pre-aggregate facts
const groupedOrders = orders
    .groupBy(orders.partnerId, orders.companyId)
    .select(
        orders.partnerId, 
        orders.companyId, 
        orders.amountTotal.sum().as("totalSales")
    );

// 2. Perform Join
const joined = customers.leftJoin(groupedOrders)
    .on(customers.id, groupedOrders.partnerId)
    .and(customers.companyId, groupedOrders.companyId); // Multiple conditions supported

// 3. Disambiguate and Select
const result = joined
    .select(
        customers.id,
        customers.name.as("customerName"),
        groupedOrders.totalSales
    )
    .orderBy("-totalSales") // .orderBy MUST come after .select if using alias
    .execute();

return result;
```
Supported Joins: `.leftJoin(other)`, `.innerJoin(other)`, `.rightJoin(other)`, `.fullJoin(other)`.

### 4. UNION
**WARNING**: When performing UNION, ALWAYS ensure the metrics have the exact same business meaning (e.g., do not union `amountTaxed` with `amountUntaxed` into the same column).
```javascript
const a = Query.from("ModelA");
const b = Query.from("ModelB");
const q1 = a.select(a.id, a.amount);
const q2 = b.select(b.id, b.amount);
return q1.union(q2, { all: true }).execute();
```

## Legacy API: `dsl({...})` / `base.query({...})`
For simple single-table fetch:
```javascript
return dsl({
    model: 'SaleOrderQM',
    columns: ['partner$id', 'sum(amountTotal) as total'],
    slice: [{ field: 'status', op: '=', value: 'done' }],
    orderBy: ['-total'],
    limit: 10
}).execute();
```

## Future Capabilities (Do not use yet)
- **Window Functions**: `sales.amount.sum().over({ partitionBy: sales.partnerId })` is planned for 8.3.0.
- **WHERE EXISTS**: Not supported currently.

## Execution Rules
- Always end with `.execute()` to trigger DB compilation and return a `DataSetResult`.
- The `DataSetResult` has methods: `.toList()`, `.column('field')`, `.first()`, `.size()`, `.isEmpty()`.
- Use `.joinInMemory(other, 'LEFT', 'key')` on `DataSetResult` ONLY if joining across different DB types (MySQL vs PG). Otherwise, always use CTE `A.leftJoin(B)`.
