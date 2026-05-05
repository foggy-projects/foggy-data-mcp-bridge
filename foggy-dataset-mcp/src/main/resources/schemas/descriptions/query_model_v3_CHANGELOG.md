# query_model_v3 Prompt Changelog

This file records why the `dataset.query_model` prompt contract changed. Java is the source of truth; Python and Odoo bridge copies synchronize from this directory unless an explicit exception is documented.

## 2026-05-05 - Closed Field Names For Date Grain Suffixes

Status: implemented

Problem:
An AR follow-up attempted `invoiceDate$year` / `invoiceDate$month` when the model had only exposed `invoiceDate` as a plain property. The engine correctly rejected the field, but the prompt needed a sharper recovery rule: described field names are a closed set.

Contract change:
- If `dataset.describe_model_internal` did not return a field name, do not synthesize it.
- Do not append `$year`, `$month`, `$caption`, or other suffixes to a plain property.
- `invoiceDate$year` / `invoiceDate$month` are valid only when describe explicitly returns those field names.
- If the model exposes only `invoiceDate`, use that plain field, a returned date-grain field, `timeWindow`, or explain that the model does not expose the requested grain.

Files changed:
- `query_model_v3.md`
- `query_model_v3_basic.md`
- `query_model_v3_no_vector.md`

Validation:
- Odoo embedded backend contract test checks the vendored runtime description includes the closed-field rule and the `invoiceDate$year` misuse example.
- Odoo chat system prompt test checks the compressed chat prompt warns not to synthesize date-grain suffixes from plain properties.

## 2026-05-04 - Date Bucket SQL Function Boundary

Status: implemented

Problem:
An AR-001 retry path produced a follow-up `dataset.query_model` call with `columns: ["DATE_TRUNC"]` after a successful `arOverdueAmount = 0` query. `DATE_TRUNC` is a SQL/database function concept, not a semantic field name in the `query_model` payload. The engine correctly rejected it as an unknown field, but the prompt did not clearly tell the LLM how to express ordinary date bucketing.

Contract change:
- Do not generate SQL function fields such as `DATE_TRUNC(...)`, `YEAR(...)`, or `MONTH(...)` in `columns`, `groupBy`, or `orderBy`.
- Do not use `DATE_TRUNC` as a field name.
- For ordinary period grouping, use date grain fields returned by `dataset.describe_model_internal`, such as `salesDate$year`, `salesDate$month`, or `salesDate$week`.
- For yoy, mom, YTD, MTD, and rolling analysis, use `timeWindow`.
- If the model does not expose the required grain field, do not invent SQL; use available fields, `timeWindow`, or explain that the model does not expose the requested grain.

Files changed:
- `query_model_v3.md`
- `query_model_v3_basic.md`
- `query_model_v3_no_vector.md`

Validation:
- Python prompt golden test checks the date bucket boundary appears in `dataset.query_model` tool description.
- Odoo embedded backend contract test checks the vendored runtime description exposes the same boundary.
- Odoo chat system prompt test checks the compressed chat prompt also warns against `DATE_TRUNC` as a field.

Expected effect:
Reduce the probability that LLMs use raw SQL date functions as semantic fields during self-check or retry queries, especially after a successful primary query.

Follow-up:
Run AR-001 after synchronizing and restarting the active Odoo / gateway profiles. Track whether the LLM still emits `DATE_TRUNC` and whether query count drops without removing tools.

## 2026-05-05 - Explicit GroupBy For Dimension Plus Aggregate Queries

Status: implemented

Problem:
AR-002 still produced a first-attempt `GROUP BY` SQL error after the AR Ready 10 prompt update. The model selected `OdooAccountMoveLineQueryModel` and the correct predefined AR measure, but sent dimension columns plus `arOverdueAmount` without an explicit `groupBy`, then had to repair itself in a later tool call.

Contract change:
- Treat engine groupBy inference as a capability, not as the preferred LLM payload shape.
- When `columns` combines dimensions with aggregate expressions or predefined aggregate measures, explicitly send `groupBy` in the first query.
- Include every displayed grouping dimension in `groupBy`; for partner displays, prefer the pair `partner$id` and `partner$caption`.
- Do not add unrequested detail/count fields such as `lineCount`, `moveName`, or `move$caption` to explain an aggregate result unless the user asked for that detail.

Files changed:
- `query_model_v3.md`
- `query_model_v3_basic.md`
- `query_model_v3_no_vector.md`

Validation:
- Prompt sync and Odoo runtime refresh are required before the next AR-002 / AR-003 / AR-010 smoke.
- Expected to reduce first-attempt GROUP BY errors and avoid extra repair query calls.

Follow-up evidence:
- AR-002 improved to a single `dataset.query_model` call with explicit `groupBy`.
- AR-010 still tried an extra probe after a successful result by putting `arOutstandingAmount > 0` in `slice`, which the engine rejects because aggregate measures cannot be translated into WHERE.
- AR-003 tried to invent `move$invoiceUserId$caption`, which is not a described field.

Additional contract change:
- Do not use aggregate expressions or predefined aggregate measures in `slice`; answer from the returned grouped result, or use `dataset.compose_script` for explicit post-aggregate filtering.
- Only use complete field names returned by `dataset.describe_model_internal`; do not invent multi-hop `$caption` fields or a standalone `caption` field.
