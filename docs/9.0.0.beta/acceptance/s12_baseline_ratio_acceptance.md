---
doc_role: feature_acceptance
doc_purpose: Formal S12 baselineRatio acceptance record for Foggy Pivot 9.0.0.beta.
acceptance_scope: feature
version: 9.0.0.beta
target: S12 baselineRatio
status: signed-off
decision: accepted
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-01
reviewed_by: Codex
blocking_items: []
follow_up_required: no
evidence_count: 8
---

# Feature Acceptance

## Background

S12 adds the structured `pivot.metrics` derived metric `baselineRatio` to cover the high-frequency cross-column baseline comparison scenario without exposing public `CELL_AT` or `AXIS_MEMBER` coordinate traversal.

The accepted public DSL shape is:

```json
{"name": "salesIndex", "type": "baselineRatio", "of": "salesAmount", "axis": "columns", "baseline": "first"}
```

## Acceptance Basis

- `docs/9.0.0.beta/detailed_design/07_s12_baseline_ratio_execution_plan.md`
- `docs/9.0.0.beta/acceptance/s12_baseline_ratio_progress.md`
- `docs/9.0.0.beta/acceptance/version-signoff.md`
- `docs/9.0.0.beta/detailed_design/00_version_goal_and_scope.md`
- `docs/9.0.0.beta/mdx_vs_foggy_syntax_comparison.md`
- `foggy-dataset-mcp/src/main/resources/schemas/query_model_v3_schema.json`
- `foggy-dataset-mcp/src/main/resources/schemas/descriptions/query_model_v3.md`

## Checklist

- [x] `PivotMetricItem` supports `type=baselineRatio`.
- [x] `PivotRequest.getSqlMetricNames()` includes `baselineRatio.of`.
- [x] `PivotRequest.getAllOutputMetricNames()` includes `baselineRatio.name`.
- [x] Runtime rejects unsupported shapes: missing `of`, non-`columns` axis, missing/invalid `baseline`, `tree + baselineRatio`, and non-additive source measures.
- [x] MCP JSON Schema exposes the supported shape and remains fail-closed for `expr`, additional properties, and invalid enums.
- [x] Prompt documentation instructs LLMs to use `baselineRatio` for cross-column first/last baseline comparison and not to generate `CELL_AT` / `AXIS_MEMBER`.
- [x] SQL Parity compares real Pivot query output directly against independent SQL oracle results.
- [x] SQLite, MySQL8, and PostgreSQL parity all pass.
- [x] Version and MDX comparison documents now mark S12 as implemented and signed off.

## Evidence

- Full workspace regression: `mvn test -P!multi-db` passed across all modules.
- SQLite unit/integration/parity evidence recorded in `s12_baseline_ratio_progress.md`.
- MySQL8 parity rerun: `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=mysql8" "-P!multi-db"` passed, 19/19.
- PostgreSQL parity rerun: `mvn test -pl foggy-dataset-model "-Dtest=PivotSqlParityIntegrationTest" "-Dspring.profiles.active=postgres" "-P!multi-db"` passed, 19/19.
- `PivotSqlParityIntegrationTest` includes baselineRatio direct SQL parity, systemSlice parity, user slice parity, and deniedColumns guard coverage.
- `PivotSchemaValidationTest` covers legal and illegal metric object shapes.
- `query_model_v3.md` contains the LLM-facing `baselineRatio` example and fail-closed warning.
- `mdx_vs_foggy_syntax_comparison.md` updates the MDX coverage conclusion to 92%+ with `baselineRatio` signed off.

## Risks / Open Items

No S12 blocking risk remains.

Known non-S12 boundaries remain unchanged:

- Generic `CELL_AT` and `AXIS_MEMBER` are rejected for public DSL.
- Cascaded Generate remains deferred / known limitation.
- Python Mirror is outside the Java Engine signoff scope.

## Final Decision

Accepted. S12 `baselineRatio` satisfies the execution plan, guardrail, documentation, and SQLite/MySQL8/PostgreSQL SQL Parity requirements.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-01
- acceptance_record: docs/9.0.0.beta/acceptance/s12_baseline_ratio_acceptance.md
- blocking_items: none
- follow_up_required: no
