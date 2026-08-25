# Restricted Compose and CTE

Use `foggy.analytics.compose.run@v1` only when one query-model DSL request cannot express the
analysis. Compose is appropriate for cross-model joins, compatible unions, derived filters or
projections over an earlier aggregate plan, and multiple named outputs.

Describe every participating model first. A base query starts with `dsl({...})`; a derived query
uses `plan.query({...})`; plans can use `.join(...)` and `.union(...)`. The final script must return
an envelope:

```fsscript
return { plans: result };
```

For multiple outputs:

```fsscript
return { plans: { current, prior, delta } };
```

## Base and derived plan

```fsscript
const sales = dsl({
  model: "SalesQM",
  columns: ["customer$id", "sum(amount) as total_amount"],
  slice: [{ field: "state", op: "=", value: "done" }],
  groupBy: ["customer$id"]
});

const top = sales.query({
  slice: [{ field: "total_amount", op: ">", value: 50000 }],
  columns: ["customer$id", "total_amount"],
  orderBy: ["-total_amount"],
  limit: 20
});

return { plans: top };
```

## Join

Aggregate the fact side before joining to avoid row multiplication, and alias duplicate columns.

```fsscript
const customers = dsl({
  model: "CustomerQM",
  columns: ["id as customer_id", "name as customer_name"]
});

const orders = dsl({
  model: "OrderQM",
  columns: ["customerId as order_customer_id", "sum(amount) as total_amount"],
  groupBy: ["customerId"]
});

const joined = customers.join(orders, "left", [
  { left: "customer_id", op: "=", right: "order_customer_id" }
]);

return { plans: joined.query({
  columns: ["customer_id", "customer_name", "total_amount"],
  orderBy: ["-total_amount"],
  limit: 20
}) };
```

Supported join types are `inner`, `left`, `right`, and `full`, subject to the configured dialect.
Join conditions are AND-only and may reference only columns exposed by the two input plans.

Use union only when both sides expose compatible columns with the same business meaning:

```fsscript
const all_orders = online.union(offline, { all: true });
return { plans: all_orders };
```

Always call `mode=validate` first. Use `preview` for generated SQL and plan evidence. Call
`execute` only after validation succeeds and row evidence is required. A valid empty result is not
an error.

Do not write raw SQL or unrestricted CTEs, call direct execution helpers, use ES modules, or access
host, filesystem, network, Java, Python, datasource, tenant, routing, identity, credentials, or
security values. Do not work around `COMPOSE_SANDBOX_VIOLATION` through another surface.
