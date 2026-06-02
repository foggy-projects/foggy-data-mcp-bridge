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

This document makes the existing `query_model`, `timeWindow`, Pivot, DSL_CTE bridge, and result-stage formula boundaries explicit so future prompt/schema edits do not imply unsupported capabilities.

## Contract Matrix

| Recipe id | Status | Trigger | Engine behavior | Evidence |
|---|---|---|---|---|
| `query_model.detail_filter_sort_paginate` | signed | Detail rows with model fields, `slice`, `orderBy`, and `limit` | Runs through model-backed query lifecycle and permission checks; `orderBy` must use exposed fields or selected aliases. | QueryModel schema descriptions, regression runner |
| `query_model.grouped_aggregate_table` | signed | `columns` with grouped dimensions and aggregate aliases such as `sum(amountTotal)` | Produces grouped tables with explicit aliases; post-aggregate threshold semantics must use the documented aggregate path, not guessed SQL. | promoted semantic gate, `BUG-post-aggregate-alias-calculate-20260531.md` |
| `query_model.grouped_topn_order_limit` | signed | Grouped aggregate TopN using selected aggregate aliases or measures in `orderBy` plus top-level `limit` | Applies top-level `orderBy` / `limit` after ordinary `query_model` grouped aggregation; this is not a Pivot axis TopN substitute. | `OdooModelLoadingTest#testSaleOrderGroupedTopNOrderLimitMatchesFixtureBaseline`, promoted semantic gate |
| `query_model.date_grain_self_dimension` | signed | Ordinary monthly/weekly/yearly grouping using described fields such as `salesDate$month` | Uses fields returned by `dataset.describe_model_internal`; does not synthesize SQL functions or hidden `$year` / `$month` fields. | QueryModel schema descriptions, semantic promoted samples, `OdooInvalidFieldRecoveryTest#testSynthesizedDateOrderQuarterIsRejectedBeforeSql` |
| `query_model.time_window_standard` | signed | YoY, MoM, WoW, YTD, MTD, rolling 7/30/90 day | Uses declarative `timeWindow`; `value` is optional but exactly two elements when provided; `rollingAggregator` supports `sum`, `avg`, `count`, `min`, and `max`. | `PivotSchemaValidationTest`, `QueryModelDescriptionConsistencyTest` |
| `query_model.pivot_base_grid` | signed | Rows, columns, metrics, subtotals, grand total, or cross-tab requests | Uses `payload.pivot`; mutually exclusive with top-level `columns` and `timeWindow`. | Pivot tests and query model descriptions |
| `query_model.pivot_axis_topn_having` | signed | Axis member TopN, axis sorting, or axis metric threshold | Uses `pivot.rows[*].orderBy`, `pivot.columns[*].orderBy`, `pivot.rows[*].limit`, `pivot.columns[*].limit`, and `pivot.rows[*].having`; top-level `orderBy` / `limit` are not Pivot axis controls and are rejected in Pivot mode. | `QueryModelDescriptionConsistencyTest`, `PivotIntegrationTest#testTopLevelOrderByAndLimitRejectedInPivotMode` |
| `query_model.pivot_c2_cascade_topn` | signed-constrained | Top N parents then Top N children under each surviving parent | Rows-axis exactly two levels, explicit `orderBy`, additive metrics, supported SQL dialect; unsupported shapes fail closed. | Pivot C2 implementation docs and tests |
| `query_model.pivot_parent_share` | signed-constrained | Contribution to parent within a Pivot rows hierarchy | Uses structured `pivot.metrics.parentShare`; public `ROLLUP_TO` strings remain hidden/unsupported. | QueryModel schema descriptions |
| `query_model.pivot_baseline_ratio` | signed-constrained | Compare column cells to first/last baseline column | Uses structured `pivot.metrics.baselineRatio`; public `CELL_AT`, `AXIS_MEMBER`, and `AXIS_REF` strings remain hidden/unsupported. | QueryModel schema descriptions |
| `query_model.result_stage_cumulative_rank` | signed-constrained | Cumulative or rank formulas over selected aggregate aliases | Uses explicit `postAggregateCalculations`, governed calculatedFields sugar, or signed DSL_CTE derive formulas such as `cumulative_sum(alias)`, `cumulative_ratio_to_total(alias)`, and `rank_by(alias)`. The signed public shape is grand-total scope, DESC order, implicit frame only. The runtime emits a `post_stage` CTE with `RANK()`, running sum, cumulative ratio, optional result-stage filter, and final alias order. | `QueryRequestValidationStepTest#testPostAggregateUnsupportedRankingKindRejected`, `QueryRequestValidationStepTest#testPostAggregateUnsupportedDenseRankFormulaRejected`, `QueryRequestValidationStepTest#testPostAggregateUnsupportedFormulaOptionsRejected`, `DslCteAcceptanceSampleTest#generateSqlOptInUsesDslBridgeForCumulativeRankDerivedStage`, `DslCteAcceptanceSampleTest#dslValidationRejectsUnsignedDenseRankDerivedFormula`, `DslCteAcceptanceSampleTest#dslValidationRejectsUnsignedResultStageFormulaOptions`, `OdooModelLoadingTest#testExplicitCumulativeAndRankPostAggregateUsesOuterStage`, `OdooModelLoadingTest#testExplicitCumulativeAndRankPostAggregateMatchesFixtureBaseline`, `OdooModelLoadingTest#testDslCteCumulativeAndRankBridgeMatchesFixtureBaseline`, `OdooModelLoadingTest#testDslCteCumulativeAndRankExecuteModeMatchesFixtureBaseline`, `BUG-post-aggregate-alias-calculate-20260531.md`, promoted semantic gate |
| `query_model.cross_model_or_relation_composition` | routed | Multi-step relation composition that cannot be expressed as one query model request | Route to governed Memory Grid / CTE / compose-script flows without bypassing query model lifecycle, permissions, or physical column checks. | v3.8/v3.9 Memory Grid and runner docs |

## Unsigned or Deferred Variants

| Variant | Required behavior |
|---|---|
| SQL function field names such as `DATE_TRUNC(...)`, `YEAR(...)`, or `MONTH(...)` in public field references such as `slice`, `having`, `postSlice`, `groupBy`, or `orderBy` | Reject or rewrite to described date-grain fields; do not treat SQL functions as semantic field names. Legacy inline expressions in `columns` are not the signed date-grain route, but selected aliases from `columns` remain valid references. |
| Synthesized date-grain fields not returned by describe, such as appending `$month` to a plain date field | Reject or clarify; the field namespace is closed. |
| `pivot + timeWindow` | Reject or split into separate requests. |
| `columns + pivot` | Reject; use projection mode or Pivot mode. |
| Top-level `orderBy` / `limit` as Pivot TopN control | Reject or rewrite before runtime to axis-level `pivot.rows[*]` / `pivot.columns[*]` controls; raw Pivot runtime rejects top-level `orderBy` / `limit` before execution. |
| Pivot cascade without explicit orderBy on a limited level | Fail closed with the cascade order requirement. |
| Pivot cascade across both axes, column-axis cascade, three-level cascade, tree cascade, having-only cascade, or non-additive cascade totals | Fail closed; do not emulate with prompt-generated SQL. |
| `parentShare` or `baselineRatio` participating in axis `having`, `orderBy`, `limit`, tree mode, or cascade | Fail closed or simplify to ordinary Pivot metrics. |
| Public use of `ROLLUP_TO`, `CELL_AT`, `AXIS_MEMBER`, `AXIS_REF`, arbitrary MDX set algebra, or coordinate functions | Reject; only structured metric objects are signed. |
| Free-form window, cumulative, ratio, or rank expressions outside governed result-stage formula shapes | Defer until a typed recipe and negative gates exist. In particular, dense-rank/row-number/percent-rank style calculatedFields, DSL_CTE derive formulas, and post-aggregate kinds are not signed by the public query_model postAggregate contract. |
| Result-stage formula options beyond grand-total DESC implicit-frame semantics | Reject before compilation. `ASC` ordering, partitioned contribution, explicit window frames, tie-breakers, and extra named arguments are unsigned until represented as typed recipe fields with fixture parity and negative gates. |
| Raw CTE or compose-script intended to bypass model permissions or lifecycle | Reject; model-backed access must enter through `query_model` first. |

## Runtime Evidence

| Evidence | Meaning |
|---|---|
| `OdooModelLoadingTest#testExplicitCumulativeAndRankPostAggregateUsesOuterStage` | Explicit `postAggregateCalculations` for `rankByMeasure`, `cumulativeSum`, and `cumulativeRatioToTotal` generate a `post_stage` CTE with result-stage filter and final alias ordering. |
| `OdooModelLoadingTest#testExplicitCumulativeAndRankPostAggregateMatchesFixtureBaseline` | Executed `postAggregateCalculations` rows match a hand-built `sale_order` + `crm_team` fixture baseline for team sales, SQL `RANK()`, cumulative sum, and cumulative ratio. |
| `OdooModelLoadingTest#testDslCteCumulativeAndRankBridgeMatchesFixtureBaseline` | Signed DSL_CTE ordinary derive formulas for rank and cumulative contribution assemble structured CTE SQL, execute the `post_stage` result filter, and match the Odoo `sale_order` + `crm_team` baseline. |
| `OdooModelLoadingTest#testDslCteCumulativeAndRankExecuteModeMatchesFixtureBaseline` | Production `queryModel(..., "execute", ...)` uses assembled structured CTE SQL and assembled params for the same signed DSL_CTE request, returning rows that match the Odoo fixture baseline. |
| `OdooModelLoadingTest#testSaleOrderGroupedTopNOrderLimitMatchesFixtureBaseline` | Production `queryModel(..., "execute", ...)` runs a grouped `sale_order` + `crm_team` aggregate table, orders by the selected aggregate alias, applies top-level `limit`, and matches the hand-built Odoo fixture TopN baseline. |
| `OdooInvalidFieldRecoveryTest#testSynthesizedDateOrderQuarterIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects a synthesized `dateOrder$quarter` field with `INVALID_QUERY_FIELD` before SQL generation, proving the date-grain namespace is closed to fields not returned by describe. |
| `OdooInvalidFieldRecoveryTest#testSqlFunctionGroupByFieldIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects direct `YEAR(dateOrder)` usage in `groupBy` with `INVALID_QUERY_FIELD` before SQL generation; callers must use described date-grain fields or selected aliases. |
| `OdooInvalidFieldRecoveryTest#testSqlFunctionOrderByFieldIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects direct `YEAR(dateOrder)` usage in `orderBy` with `INVALID_QUERY_FIELD` before SQL generation; selected aliases and governed calculated fields remain the supported ordering route. |
| `OdooInvalidFieldRecoveryTest#testSqlFunctionSliceFieldIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects direct `YEAR(dateOrder)` usage in `slice` with `INVALID_QUERY_FIELD` before SQL generation; filters must use described fields or selected aliases. |
| `OdooInvalidFieldRecoveryTest#testSqlFunctionHavingFieldIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects direct `YEAR(dateOrder)` usage in `having` with `INVALID_QUERY_FIELD` before SQL generation; aggregate filters must use described fields, aggregate aliases, or governed result-stage routes. |
| `OdooInvalidFieldRecoveryTest#testSqlFunctionPostSliceFieldIsRejectedBeforeSql` | Public `queryModel(..., "execute", ...)` rejects direct `YEAR(dateOrder)` usage in `postSlice` with `INVALID_QUERY_FIELD` before SQL generation; result-stage filters must use selected aliases or governed calculated fields. |
| `OdooModelLoadingTest#testSaleOrderInlineYearAliasGroupByOrderByExecutes` | Public `queryModel(..., "execute", ...)` preserves the legacy `columns` inline-expression alias route: `YEAR(dateOrder) as orderYear` may be selected first and then referenced by `groupBy` / `orderBy`. |
| `PivotIntegrationTest#testMutuallyExclusiveValidation` | Pivot runtime rejects `pivot + columns` and `pivot + timeWindow` before execution, preserving the one-route contract between ordinary query projection, `timeWindow`, and Pivot. |
| `PivotIntegrationTest#testTopLevelOrderByAndLimitRejectedInPivotMode` | Pivot runtime rejects top-level `orderBy` and `limit` before execution and points callers to axis-level `pivot.rows[*]` / `pivot.columns[*]` sorting and TopN controls. |
| `OdooModelLoadingTest#testCalculatedFieldsCumulativeAndRankNormalizeToPostAggregate` | Governed calculatedFields sugar normalizes to the same post-aggregate contract and removes the sugar fields from ordinary calculated field processing. |
| `DslCteAcceptanceSampleTest#generateSqlOptInUsesDslBridgeForCumulativeRankDerivedStage` | Signed DSL_CTE ordinary derive formulas for rank, cumulative sum, and cumulative ratio bridge to `postAggregateCalculations` instead of remaining prompt-only formula text. |
| `DslCteAcceptanceSampleTest#dslValidationRejectsUnsignedDenseRankDerivedFormula` | DSL_CTE validation rejects unsigned `dense_rank()` result-stage derived formulas before compilation. |
| `DslCteAcceptanceSampleTest#dslValidationRejectsUnsignedResultStageFormulaOptions` | DSL_CTE validation rejects signed-looking result-stage formulas that request unsigned `ASC`, partition, explicit-frame, or extra-argument semantics. |
| `QueryRequestValidationStepTest#testPostAggregateUnsupportedRankingKindRejected` | Unsigned ranking kinds such as dense-rank style post-aggregate calculations are rejected before SQL generation. |
| `QueryRequestValidationStepTest#testPostAggregateUnsupportedDenseRankFormulaRejected` | Unsigned `dense_rank()` calculatedFields formulas are rejected before SQL generation. |
| `QueryRequestValidationStepTest#testPostAggregateUnsupportedFormulaOptionsRejected` | Signed-looking calculatedFields formula options outside the governed shape are rejected before SQL generation. |
| `BUG-post-aggregate-alias-calculate-20260531.md` | v3.9 root record for alias ratio, cumulative contribution, and rank-by-measure fixes and regression evidence. |

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
