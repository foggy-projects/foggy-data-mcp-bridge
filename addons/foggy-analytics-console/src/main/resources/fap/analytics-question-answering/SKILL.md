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
   Treat a described measure name as the canonical semantic expression: select and order by that
   name directly. For a detail/ranking question, do not invent `sum(...)`, another aggregate, or
   `groupBy`; use aggregation only when the user explicitly asks for grouped or summarized data.
3. Prefer `foggy.analytics.query-model.run@v1` for one-model detail, filters, grouping,
   aggregation, calculated fields, time windows, pivot, subtotal or controlled DSL_CTE plans.
   Call it with `mode=validate` first and then `mode=execute` with the same payload when
   validation succeeds.
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
