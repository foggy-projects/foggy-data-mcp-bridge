---
doc_role: detailed_design
doc_purpose: Provide a signed and unsigned recipe index for stable non-SLA query and analysis engine capabilities.
version: 9.1.0
target: Query analysis recipe contract index
status: active-contract
created_at: 2026-06-02
updated_at: 2026-06-02
---

# Query Analysis Recipe Contract Index

## Purpose

This index records the stable non-SLA query and analysis recipes that planner, prompt, evaluator, and runtime owners can treat as signed engine contracts.

This document does not add runtime behavior. It makes the existing `query_model`, `timeWindow`, Pivot, and result-stage formula boundaries explicit so future prompt/schema edits do not imply unsupported capabilities.

## Contract Matrix

| Recipe id | Status | Trigger | Engine behavior | Evidence |
|---|---|---|---|---|
| `query_model.detail_filter_sort_paginate` | signed | Detail rows with model fields, `slice`, `orderBy`, and `limit` | Runs through model-backed query lifecycle and permission checks; `orderBy` must use exposed fields or selected aliases. | QueryModel schema descriptions, regression runner |
| `query_model.grouped_aggregate_table` | signed | `columns` with grouped dimensions and aggregate aliases such as `sum(amountTotal)` | Produces grouped tables with explicit aliases; post-aggregate threshold semantics must use the documented aggregate path, not guessed SQL. | promoted semantic gate, `BUG-post-aggregate-alias-calculate-20260531.md` |
| `query_model.date_grain_self_dimension` | signed | Ordinary monthly/weekly/yearly grouping using described fields such as `salesDate$month` | Uses fields returned by `dataset.describe_model_internal`; does not synthesize SQL functions or hidden `$year` / `$month` fields. | QueryModel schema descriptions, semantic promoted samples |
| `query_model.time_window_standard` | signed | YoY, MoM, WoW, YTD, MTD, rolling 7/30/90 day | Uses declarative `timeWindow`; `value` is optional but exactly two elements when provided; `rollingAggregator` supports `sum`, `avg`, `count`, `min`, and `max`. | `PivotSchemaValidationTest`, `QueryModelDescriptionConsistencyTest` |
| `query_model.pivot_base_grid` | signed | Rows, columns, metrics, subtotals, grand total, or cross-tab requests | Uses `payload.pivot`; mutually exclusive with top-level `columns` and `timeWindow`. | Pivot tests and query model descriptions |
| `query_model.pivot_axis_topn_having` | signed | Axis member TopN, axis sorting, or axis metric threshold | Uses `pivot.rows[*].orderBy`, `pivot.columns[*].orderBy`, `pivot.rows[*].limit`, `pivot.columns[*].limit`, and `pivot.rows[*].having`; top-level `orderBy` / `limit` are not Pivot axis controls. | `QueryModelDescriptionConsistencyTest` |
| `query_model.pivot_c2_cascade_topn` | signed-constrained | Top N parents then Top N children under each surviving parent | Rows-axis exactly two levels, explicit `orderBy`, additive metrics, supported SQL dialect; unsupported shapes fail closed. | Pivot C2 implementation docs and tests |
| `query_model.pivot_parent_share` | signed-constrained | Contribution to parent within a Pivot rows hierarchy | Uses structured `pivot.metrics.parentShare`; public `ROLLUP_TO` strings remain hidden/unsupported. | QueryModel schema descriptions |
| `query_model.pivot_baseline_ratio` | signed-constrained | Compare column cells to first/last baseline column | Uses structured `pivot.metrics.baselineRatio`; public `CELL_AT`, `AXIS_MEMBER`, and `AXIS_REF` strings remain hidden/unsupported. | QueryModel schema descriptions |
| `query_model.result_stage_cumulative_rank` | signed-constrained | Cumulative or rank formulas over query output aliases | Uses governed result-stage formulas such as `cumulative_sum(alias)`, `cumulative_ratio_to_total(alias)`, and `rank_by(alias)` when the shape is accepted. | `BUG-post-aggregate-alias-calculate-20260531.md`, promoted semantic gate |
| `query_model.cross_model_or_relation_composition` | routed | Multi-step relation composition that cannot be expressed as one query model request | Route to governed Memory Grid / CTE / compose-script flows without bypassing query model lifecycle, permissions, or physical column checks. | v3.8/v3.9 Memory Grid and runner docs |

## Unsigned or Deferred Variants

| Variant | Required behavior |
|---|---|
| SQL function fields such as `DATE_TRUNC(...)`, `YEAR(...)`, or `MONTH(...)` in `columns`, `groupBy`, or `orderBy` | Reject or rewrite to described date-grain fields; do not treat SQL functions as semantic field names. |
| Synthesized date-grain fields not returned by describe, such as appending `$month` to a plain date field | Reject or clarify; the field namespace is closed. |
| `pivot + timeWindow` | Reject or split into separate requests. |
| `columns + pivot` | Reject; use projection mode or Pivot mode. |
| Top-level `orderBy` / `limit` as Pivot TopN control | Rewrite to axis-level `pivot.rows[*]` / `pivot.columns[*]` controls. |
| Pivot cascade without explicit orderBy on a limited level | Fail closed with the cascade order requirement. |
| Pivot cascade across both axes, column-axis cascade, three-level cascade, tree cascade, having-only cascade, or non-additive cascade totals | Fail closed; do not emulate with prompt-generated SQL. |
| `parentShare` or `baselineRatio` participating in axis `having`, `orderBy`, `limit`, tree mode, or cascade | Fail closed or simplify to ordinary Pivot metrics. |
| Public use of `ROLLUP_TO`, `CELL_AT`, `AXIS_MEMBER`, `AXIS_REF`, arbitrary MDX set algebra, or coordinate functions | Reject; only structured metric objects are signed. |
| Free-form window, cumulative, ratio, or rank expressions outside governed result-stage formula shapes | Defer until a typed recipe and negative gates exist. |
| Raw CTE or compose-script intended to bypass model permissions or lifecycle | Reject; model-backed access must enter through `query_model` first. |

## Required Touchpoints For New Query Recipes

| Touchpoint | Required update |
|---|---|
| Query model schema descriptions | Update `query_model_v3.md`, `query_model_v3_basic.md`, and `query_model_v3_no_vector.md` together. |
| Description consistency test | Add planner-visible snippets to `QueryModelDescriptionConsistencyTest`. |
| Fixture or replay sample | Add positive evidence for the signed recipe. |
| Negative gate | Add close-neighbor failures for underspecified or unsupported variants. |
| Runtime validator or mapper | Add a signed branch or explicit fail-closed branch. |
| Version docs | Update this index and the owning v3.9 workitem. |

## Extension Rule

Do not promote a new query or analysis capability through prompt text alone. A capability is signed only when its fields, lifecycle route, accepted shape, negative variants, and evidence are recorded together.
