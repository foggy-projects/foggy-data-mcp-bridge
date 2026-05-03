# Compose Query Chain API Manual

> **Status**: 8.x synchronized edition.
> **Positioning**: Chain-style API reference for `Query.from(...).where(...).select(...)` users.
> **Primary AI path**: For MCP and AI generation, prefer the JSON-first [DSL Manual](./dsl-manual.md). The chain API is a mirror for developers who prefer method chaining.

## 1. Relationship with the DSL Manual

The DSL manual and this chain API manual must be functionally equivalent, but the syntax is intentionally different:

| Capability | DSL form | Chain form |
|------------|----------|------------|
| Base query | `dsl({ model, columns, slice })` | `Query.from(model).where(...).select(...)` |
| Derived query | `dsl({ model: prevPlan, ... })` | continue from `prevPlan` or wrap it as a source |
| Join | `left.join(right, type, on)` | same plan method |
| Union | `left.union(right, options)` | same plan method |
| timeWindow | top-level `timeWindow` object | helper may exist, but DSL form is the stable public AI contract |
| CTE reuse | compiler-managed | compiler-managed |

## 2. Minimal Chain Query

```javascript
const sales = Query.from("FactSalesQueryModel")
  .where([{ field: "salesDate$id", op: ">=", value: "2025-01-01" }])
  .select("product$id", "sum(salesAmount) as totalSales")
  .groupBy("product$id")
  .orderBy("-totalSales")
  .limit(100);

return {
  plans: { top_sales: sales }
};
```

## 3. Public Boundaries

- Chain API is not the recommended shape for AI prompt generation.
- Do not hand-write SQL or CTE text.
- CTE reuse is compiler-managed when the same plan is reused by downstream plans.
- Do not use chain helpers to bypass `dataset.query_model` boundaries. Single-model pivot and single-model time-window analysis remain `dataset.query_model` payload features.

## 4. Derived Query

```javascript
const base = Query.from("FactSalesQueryModel")
  .select("product$id", "sum(salesAmount) as totalSales")
  .groupBy("product$id");

const filtered = base
  .where([{ field: "totalSales", op: ">", value: 100000 }])
  .select("product$id", "totalSales")
  .orderBy("-totalSales");
```

The derived layer can only reference columns projected by the previous plan.

## 5. Join

```javascript
const joined = customers.join(orders, "left", [
  { left: "id", op: "=", right: "customerId" }
]);
```

Boundaries:

- Supported types: `inner`, `left`, `right`, `full` where the dialect supports them.
- Conditions are equality-based and combined with AND.
- Cross-datasource Join is not part of the public contract.
- Rename same-name columns before joining or before final projection.

## 6. Union

```javascript
const allOrders = online.union(offline, { all: true });
```

Union uses the left-side schema. Branches must expose compatible columns with the same business meaning.

## 7. CTE Reuse

If a plan is reused by multiple downstream plans, the compiler may generate a shared CTE on supported dialects:

```javascript
const productSales = Query.from("FactSalesQueryModel")
  .select("product$id", "sum(salesAmount) as totalSales")
  .groupBy("product$id");

const highValue = productSales.where([
  { field: "totalSales", op: ">", value: 100000 }
]);

const ranked = productSales.orderBy("-totalSales").limit(20);
```

Public boundaries:

- Users do not name SQL CTEs.
- Named top-level plans are only result-map keys.
- Recursive CTE and arbitrary SQL CTE injection are not supported.

## 8. timeWindow

For stable AI and MCP usage, prefer the DSL shape:

```javascript
dsl({
  model: "FactSalesQueryModel",
  columns: ["salesDate$id", "salesAmount"],
  groupBy: ["salesDate$id"],
  timeWindow: {
    field: "salesDate$id",
    grain: "month",
    comparison: "yoy",
    targetMetrics: ["salesAmount"]
  }
});
```

Execution order is the same as in the DSL manual: expand time-window metrics first, then apply post-window scalar calculated fields.

## Maintenance

| Date | Change | Notes |
|------|--------|-------|
| 2026-05-03 | Added English synchronized page | Mirrors the chain API contract and points AI generation to the DSL manual |
