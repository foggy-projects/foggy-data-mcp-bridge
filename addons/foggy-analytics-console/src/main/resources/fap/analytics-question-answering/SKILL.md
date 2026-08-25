---
name: analytics-question-answering
description: Answer Analytics Console questions with the complete governed Foggy query-model DSL and restricted Compose/CTE tools.
---

# Analytics question answering

Work only in the Namespace frozen by the current Analytics Console Task. The available
Functions are read-only semantic operations; their presence is not permission to access another
Namespace, inject identity or policy fields, or bypass a model.

## Workflow

1. If no exact model is known, call `foggy.analytics.model-dependencies.list@v1` for the bound
   Namespace. Never claim that models cannot be listed before trying this Function.
2. Call `foggy.analytics.semantic-models.describe@v1` with the selected model and its exact
   revision. Use only fields, measures, captions, dates and relationships in that description.
   For a detail/ranking question, select and order by the described measure name directly and do
   not add aggregation or `groupBy`. When the user explicitly asks for a total, count, grouped
   contribution or other summary, use the aggregation advertised by describe, for example
   `sum(amount) as totalAmount`, and count a described identifier such as
   `count(orderId) as orderCount`. Never invent a field or aggregate function absent from describe.
3. Prefer `foggy.analytics.query-model.run@v1` for one-model detail, filters, grouping,
   aggregation, calculated fields, time windows, pivot, subtotal or controlled DSL_CTE plans.
   Its standard DSL input schema can exceed the Function INLINE prompt budget. When its delivered
   contract says `effectiveDelivery=ON_DEMAND` (or `delivery=ON_DEMAND`), call
   `describe_business_function` with the `INPUT` or `FULL` view before invoking it. Do not guess
   nested `slice`, `having`, `groupBy`, `orderBy`, `timeWindow` or `pivot` shapes from a summary.
   The `executable_plan` object is intentionally governed by the controlled recipes in
   `references/query-model-dsl.md` plus Runtime validation; read that reference before using CTE.
   A total/count or ordinary grouped summary is standard DSL: use `columns` and, only when needed,
   `groupBy`; omit `route` and `executable_plan`. Choose `DSL_CTE` only for a documented staged
   recipe that cannot be expressed by the standard fields. In a DSL_CTE plan, the first stage uses
   `input: {"model": "<exact described model>"}`; later `inputs` values may reference only prior
   stage names. Never invent an implicit `source` stage.
   Call it with `mode=validate` first and then `mode=execute` with the same payload when
   validation succeeds. If validation returns repairable violations, use each stable
   `messageKey`, correct the payload, and validate again. Never execute a payload that failed
   validation or end the turn after the first repairable parameter error.
4. Use `foggy.analytics.compose.run@v1` only when one query-model request is insufficient:
   cross-model joins, union, a derived query over prior output, or multiple named result plans.
   Validate first; use preview when SQL/plan evidence is sufficient; execute only when rows are
   needed.
5. Answer only from Function evidence. Preserve totals, truncation, `hasMore`, warnings and
   validation failures. Ask a concise clarification when model metadata cannot disambiguate the
   request.

The older `foggy.analytics.semantic-queries.execute@v1` remains available for its small typed
subset, but do not fall back to it merely because the full DSL requires validation.

Read `references/query-model-dsl.md` before constructing a full DSL payload and
`references/compose-script.md` before constructing Compose.

## Closed boundaries

Never use raw SQL, standalone FSScript, filesystem, network, Java/Python host access, private
Runtime endpoints, credentials, datasource selectors, tenant/employee/organization identifiers,
authority objects, security filters, or mutation Functions. Do not create or modify Report,
Dashboard, Bundle, model, or business data. If the semantic surface cannot express a request,
state that limitation instead of weakening these boundaries.
