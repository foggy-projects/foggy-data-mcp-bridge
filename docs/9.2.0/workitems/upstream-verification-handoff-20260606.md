---
doc_role: upstream_verification_handoff
doc_purpose: Provide a compact TMS/upstream verification packet for 9.2.0 workitems that are local-verified but still awaiting consumer confirmation.
version: 9.2.0
target: TMS Upstream Verification For 9.2 QueryModel Hardening
status: ready-for-upstream-verification
created_at: 2026-06-06
updated_at: 2026-06-18
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# TMS Upstream Verification Handoff

## Purpose

This handoff is for the remaining 9.2.0 items that already have local Java engine evidence but still need TMS-side confirmation:

- `BUG-formula-property-missing-column-error`
- `BUG-qm-predefined-formula-slice-injection`

The upstream verifier does not need to re-run the full Foggy Java test suite. The expected output is a short pass/fail confirmation against the real TMS model and query shapes.

## Current Upstream Audit

2026-06-18 audit result:

- `gh issue view 85 -R foggy-projects/foggy-data-mcp-bridge` still reports
  issue #85 as `OPEN`, updated at `2026-06-13T04:41:07Z`.
- `gh issue list --search 'availablePieceCount OR OrderStationStockProjectionQuery OR predefined formula slice'`
  returned #85 and #84 only; no standalone `availablePieceCount` issue was
  found.
- Local workspace audit still finds no TMS/query-cloud-service checkout under
  `/Users/fengjianguang/foggy-projects`.
- The target TMS EXPLAIN and registry promotion boundary is now recorded in
  `TMS-target-explain-and-registry-handoff-20260618.md`.
- Both items remain `local-verified-awaiting-upstream`; do not mark either as
  `upstream-verified` until a real TMS pass/fail reply is returned.

2026-06-13 audit result:

- `foggy-projects/foggy-data-mcp-bridge#85` remains open. It has the Java engine fix comment for commit `1a39d711`, but no TMS pass/fail confirmation yet.
- No standalone upstream issue was found for `OrderStationStockProjectionQuery.availablePieceCount`; keep this handoff as the tracking packet until a real TMS confirmation is returned.
- Both items therefore remain `local-verified-awaiting-upstream`; do not mark them `upstream-verified` from local Java evidence alone.
- Local workspace audit found no TMS/query-cloud-service checkout, so this repo can only provide Java engine evidence. Real consumer verification still needs the upstream TMS repo and model bundle.
- #84 aggregate relation left-side dimension ON is no longer part of this pending handoff: upstream feedback confirmed the TMS real query path, while this handoff remains scoped to #85 and `availablePieceCount`.

2026-06-13 local Java recheck:

```bash
mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P'!multi-db' '-Dtest=SemanticScaleFactorIntegrationTest#formulaPropertyMissingColumn_reportsFieldPathAndCarrierColumnRule,InlineExpressionPreprocessStepTest#injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns+injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,AggregateJoinQueryModelTest#aggregateRelationOnLeftKeyShouldSupportJoinedDimensionField+aggregateRelationOnLeftKeyShouldSupportNestedDimensionPath+aggregateRelationRhsFixedFilterShouldSupportRightDimensionField'
```

Result: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.

## Engine Baseline

Use `foggy-projects/foggy-data-mcp-bridge` branch `main` at, or after, the 2026-06-06 hardening verification baseline.

Relevant Java engine evidence already recorded locally:

```text
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
```

Command used for the local combination gate:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite '-Dtest=AggregateJoinQueryModelTest,SemanticScaleFactorIntegrationTest,InlineExpressionPreprocessStepTest,AdvancedAnalyticsTest#testQmPredefinedFormulaFieldReferencedOnlyBySlice,PivotIntegrationTest#testQmPredefinedFormulaMetricInPivotFlat+testQmPredefinedFormulaInPivotTopLevelSlice+testQmPredefinedFormulaInPivotAxisHavingAndOrderBy+testQmPredefinedFormulaMetricInPivotGrandTotal' test
```

## Verification Item 1: Formula Property Missing Column Error

Source workitem:

- `docs/9.2.0/workitems/BUG-formula-property-missing-column-error.md`
- upstream issue: `foggy-projects/foggy-data-mcp-bridge#85`

### What To Verify

When a formula-backed TM property is missing its physical carrier `column`, the model loading or validation error should point to the concrete model field instead of dumping the whole model object.

Expected diagnostic shape:

```text
<ModelName>.<fieldName> column不能为空；formulaDef/dialectFormulaDef 字段必须声明 carrier column，用于字段元数据、权限和物理列绑定
```

### Suggested TMS Scenario

Use the original TMS #85 model family:

- `OrderSettlementCandidateQuery`
- `FactOrderSettlementModel`
- formula-backed JSON/property fields around settlement cost data

If TMS has already added carrier columns to all affected fields, this can be verified by temporarily removing one carrier `column` in a local verification branch and loading the model.

### Pass Criteria

- The error names the concrete model and field path.
- The error mentions that `formulaDef` / `dialectFormulaDef` property fields still require a carrier column.
- The error no longer prints a full `DbObjectSupport(...)` model dump as the only actionable context.

### Fail Criteria

- The model load still fails with only a full model dump.
- The message does not identify which field lacks `column`.
- A formula-backed property without carrier column is silently accepted.

## Verification Item 2: QM Predefined Formula Slice Injection

Source workitem:

- `docs/9.2.0/workitems/BUG-qm-predefined-formula-slice-injection.md`

### What To Verify

When a QM predefined formula field is referenced by request clauses outside `columns`, the Java engine should inject the trusted predefined calculated field before SQL condition building.

Primary upstream field:

```javascript
{
  name: 'availablePieceCount',
  caption: '可配件数',
  type: 'DOUBLE',
  formula: 'GREATEST(COALESCE(number, 0) - COALESCE(plannedPieceCount, 0), 0)'
}
```

Primary query model:

- `OrderStationStockProjectionQuery`

### Suggested TMS Scenario

Run the real consumer query with `availablePieceCount` referenced only from a filter-like clause and not explicitly selected in `columns`.

Minimal request shape:

```json
{
  "slice": [
    {
      "field": "availablePieceCount",
      "op": ">",
      "value": 0
    }
  ]
}
```

If the TMS query also requires runtime context such as `extData`, include the same `extData` payload used by the production query path.

### Pass Criteria

- The request no longer fails with `未能找到列[availablePieceCount]`.
- The formula field can be referenced from `slice` even when omitted from `columns`.
- Paging, totals, and any existing backend filters keep the same business result shape as the equivalent workaround query that explicitly selected the formula field.

### Fail Criteria

- The request still fails with `未能找到列[availablePieceCount]`.
- The request succeeds only when `availablePieceCount` is also listed in `columns`.
- The generated result shape changes unexpectedly when the formula is used only in `slice`.

## Upstream Reply Template

Please return the following fields in the upstream confirmation:

```text
engine branch / commit:
TMS branch / commit:
model bundle or registry version:

BUG-formula-property-missing-column-error:
  result: pass | fail | not-applicable
  scenario used:
  observed message or error:

BUG-qm-predefined-formula-slice-injection:
  result: pass | fail | not-applicable
  scenario used:
  request shape:
  observed result or error:

extra notes:
```

## Local Follow-Up Rule

After upstream confirmation:

- mark the corresponding workitem as `upstream-verified` when the real scenario passes;
- keep status as `local-verified-awaiting-upstream` if no consumer confirmation is available;
- reopen the workitem as a regression if either fail criterion is observed.
