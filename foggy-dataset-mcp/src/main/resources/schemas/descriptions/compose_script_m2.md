# dataset.compose_script (SemanticDSL)

Use this tool to orchestrate complex queries through SemanticDSL scripts. The script runs in a secure FSScript sandbox.

## When to Use This Tool
**Use `compose_script` ONLY IF your task requires:**
- **Cross-model Join / Union**: Combining data from multiple query models.
- **Derived Queries**: Processing the results of a previous aggregation (e.g., filtering on an aggregated alias, or double aggregation).
- **Time-Window Analysis**: Calculating YoY, MoM, WoW, YTD, MTD, or rolling averages using the `timeWindow` configuration.
- **Multiple Result Plans**: Returning multiple separate query plans at once.

**OTHERWISE**, if you are just querying a single model with basic filters, grouping, and aggregations, **use `dataset.query_model` instead.**

> **Note**: For comprehensive SemanticDSL syntax (including all supported `slice` operators, `calculatedFields` expressions, and column definitions), please refer to the `dataset.query_model` tool documentation or the `query_model_v3` schema.

## AI-Facing Entry Points

Expose only these 3 entry points in generated scripts:

| Entry | Trigger Scenario | Example |
|---|---|---|
| `dsl({...})` | Start a new query from a model, OR filter/process an existing plan (derived query) | `dsl({ model: "SalesQM", columns: [...] })` |
| `.join(other, type, on)` | Combine two plans side-by-side | `a.join(b, "inner", [{ left, op, right }])` |
| `.union(other, options)` | Append one plan's rows to another | `a.union(b, { all: true })` |

## Base Query

Use `dsl({...})` with `model` as a query model name:

```fsscript
const sales = dsl({
    model: "OdooSaleOrderModel",
    columns: ["partnerId", "SUM(amountTotal) AS totalAmount"],
    slice: [{ field: "status", op: "=", value: "done" }],
    groupBy: ["partnerId"],
    orderBy: ["-totalAmount"],
    limit: 10
});

return { plans: sales };
```

Common fields:

| Field | Type | Notes |
|---|---|---|
| `model` | `string | QueryPlan` | Query model name for base queries, or a previous plan for derived queries |
| `columns` | `string[] | object[]` | Projected columns and aliases |
| `slice` | `object[]` | Filters: `{ field, op, value }` |
| `groupBy` | `string[]` | Grouping columns |
| `orderBy` | `string[]` | Prefix `-` for descending |
| `limit` | `number` | Row limit |
| `start` | `number` | Offset |
| `distinct` | `boolean` | SELECT DISTINCT |
| `calculatedFields` | `object[]` | Post/base calculated fields when supported by the query model |
| `timeWindow` | `object` | Time-window expansion |

## Derived Query

Use `dsl({...})` with `model` set to a previous plan. Derived queries may only reference columns already projected by the previous stage.

```fsscript
const grouped = dsl({
    model: "FactSalesQueryModel",
    columns: ["product$id", "SUM(amount) AS totalSales"],
    groupBy: ["product$id"]
});

const topProducts = dsl({
    model: grouped,
    slice: [{ field: "totalSales", op: ">", value: 50000 }],
    columns: ["product$id", "totalSales"],
    orderBy: ["-totalSales"]
});

return { plans: topProducts };
```

## Join

Build each side first, aggregate before joining 1:N facts, then use `.join(other, type, on)`.

```fsscript
const customers = dsl({
    model: "OdooResPartnerModel",
    columns: ["id", "name AS customerName"]
});

const orders = dsl({
    model: "OdooSaleOrderModel",
    columns: ["partnerId", "companyId", "SUM(amountTotal) AS totalSales"],
    groupBy: ["partnerId", "companyId"]
});

const joined = customers.join(orders, "left", [
    { left: "id", op: "=", right: "partnerId" }
]);

const result = dsl({
    model: joined,
    columns: ["id", "customerName", "totalSales"],
    orderBy: ["-totalSales"],
    limit: 20
});

return { plans: result };
```

Supported join types: `"inner"`, `"left"`, `"right"`, `"full"` where the SQL dialect supports them. Join conditions are AND-only arrays using field names visible on each side.

When two joined plans have same-name columns, rename them in the source plans or with supported column object forms before the final projection.

## Union

Use `.union(...)` only when both sides expose compatible columns with the same business meaning.

```fsscript
const online = dsl({
    model: "OnlineSalesQueryModel",
    columns: ["orderId", "customerName", "amount"]
});

const offline = dsl({
    model: "OfflineSalesQueryModel",
    columns: ["orderId", "customerName", "amount"]
});

return { plans: online.union(offline, { all: true }) };
```

After union, the result exposes the left-side schema.

## Time Window

Use `timeWindow` as a top-level field on `dsl({...})` for YoY, MoM, WoW, YTD, MTD, and rolling 7/30/90 day analysis. Prefer `timeWindow`; do not hand-write window SQL.

> **WARNING**: Before writing a timeWindow script, **always** inspect the model using `dataset.describe_model_internal`. Look for the field marked with `timeRole=business_date`. You MUST use this field's `$id` as `timeWindow.field` (e.g., `salesDate$id`). Do NOT guess time fields, and DO NOT use system fields like `created_at` or `write_date` unless explicitly requested.

### Choosing the Time Field

| Time definition | How to use it | Notes |
|---|---|---|
| Standard time dimension | Use the dimension id field as `timeWindow.field`, for example `salesDate$id` | Preferred. It is usually marked `timeRole=business_date` and may expose attributes such as `salesDate$year`, `salesDate$month`, `salesDate$quarter`, and `salesDate$caption` for grouping or display. |
| Plain Date/DateTime field | Use the raw field name, for example `orderDate` | Use only when it is the actual business or event time. The engine can bucket by `grain`, but the model may not expose rich calendar attributes. |
| System time field | Usually avoid | Do not use `created_at`, `updated_at`, or `write_date` for business trend analysis. |

```fsscript
const monthlyYoy = dsl({
    model: "FactSalesQueryModel",
    columns: [
        "salesDate$year",
        "salesDate$month",
        "salesAmount",
        "salesAmount__prior",
        "salesAmount__diff",
        "salesAmount__ratio"
    ],
    groupBy: ["salesDate$year", "salesDate$month"],
    timeWindow: {
        field: "salesDate$id",
        grain: "month",
        comparison: "yoy",
        targetMetrics: ["salesAmount"]
    }
});

return { plans: monthlyYoy };
```

Supported grains: `day`, `week`, `month`, `quarter`, `year`.

Supported comparisons:

| comparison | Output columns |
|---|---|
| `yoy`, `mom`, `wow` | `{metric}__prior`, `{metric}__diff`, `{metric}__ratio` |
| `ytd`, `mtd` | `{metric}__ytd`, `{metric}__mtd` |
| `rolling_7d`, `rolling_30d`, `rolling_90d` | `{metric}__rolling_7d`, `{metric}__rolling_30d`, `{metric}__rolling_90d` |

`targetMetrics` must name metric output columns from the current query stage. Do not point `targetMetrics` at `calculatedFields`.

Post time-window scalar fields are allowed when they only reference final output columns:

```fsscript
const yoy = dsl({
    model: "FactSalesQueryModel",
    columns: ["salesDate$year", "salesDate$month", "salesAmount", "growthPercent"],
    groupBy: ["salesDate$year", "salesDate$month"],
    timeWindow: {
        field: "salesDate$id",
        grain: "month",
        comparison: "yoy",
        targetMetrics: ["salesAmount"]
    },
    calculatedFields: [
        { name: "growthPercent", expression: "salesAmount__ratio * 100" }
    ]
});

return { plans: yoy };
```

## Execution Rules & Constraints

- **Return Shape**: End query plans by returning an envelope object: `return { plans: yourPlan };`
- **Output Statements**: Always use `return` to output the final value. ES module `export` is NOT supported.
- **Execution Control**: Do not use `.execute()` directly unless specifically instructed. Let the host handle execution via the returned plan envelope.
- **SQL / CTE**: Do not hand-write raw SQL or CTE syntax (`WITH ...`). Use `dsl()` and plan composition instead.
- **Host Context**: Do not pass host-controlled security parameters such as user identity, system slice, denied columns, or datasource routing fields inside the script.
- **Outer Aggregation / Windowing Restrictions**: Do not use aggregate functions or window functions (`partitionBy`, `windowOrderBy`, `windowFrame`) in the `calculatedFields` of a **derived query**. Keep derived queries simple and prefer scalar calculated fields.
- **Plan Clarity**: Keep intermediate plans explicit and assigned to descriptive variables. This preserves schema metadata and helps repair errors.

## Schema Discovery

If you are uncertain about model fields or the primary time axis, first use `dataset.list_models` to discover models, then use `dataset.describe_model_internal` to inspect the exact schema and `timeRole` attributes before writing the script.
