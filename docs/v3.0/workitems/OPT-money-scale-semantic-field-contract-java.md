---
doc_role: workitem
doc_purpose: Track Java implementation of money semantic scale fields in TM/QM models.
version: v3.0
ticket: MONEY-30-J1
priority: P1
status: signed-off
source_type: optimization
owner: foggy-dataset-model
created_at: 2026-05-10
---

# OPT: Money Scale Semantic Field Contract for Java Engine

## 文档作用

- doc_type: optimization
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 将 root v3.0 金额语义单位设计下沉到 Java worktree，明确 Java 第一阶段的需求、边界、触点和验收标准。

## Background

Different upstream systems store money fields in different physical units. ERP-style models often store values in the major currency unit, while payment, settlement, or TMS-style tables may store the smallest unit such as cents.

The LLM-facing TM/QM contract must expose semantic business units only. For example, `totalCost` should be described and queried in yuan even when the physical table stores `total_cost_cent`.

## Upstream Source

- Root design: `../../../../docs/v3.0/OPT-money-scale-semantic-field-contract.md`
- Local implementation plan: `../detailed_design/01_money_scale_semantic_field_java_plan.md`
- Progress template: `OPT-money-scale-semantic-field-contract-java-progress.md`

## Target Outcome

Java TM/QM engine supports semantic money fields with these model-level names:

```javascript
{
  name: "totalCost",
  column: "total_cost_cent",
  aggregation: "sum",
  semanticScaleFactor: 100,
  semanticUnit: "currency",
  semanticUnitLabel: "元"
}
```

Expected LLM-facing behavior:

- LLM sees `totalCost: 总成本金额，单位元`.
- LLM never sees "stored in cents" or "divide by 100".
- Query results, filters, sorting, aggregates, and calculated fields use semantic business units.

## Contract Rules

1. `column` remains the physical single-column binding. It must not accept arbitrary SQL.
2. `aggregation` remains the existing aggregation field. Do not introduce `aggregate`.
3. `semanticScaleFactor` is the divisor from physical value to semantic value. It must be a positive number.
4. `semanticUnit` and `semanticUnitLabel` are metadata. They may be exposed through describe/metadata, but must not trigger result-stage conversion.
5. If `formulaDef` and `semanticScaleFactor` are both configured on the same field, Java V1 must fail closed or reject model loading with a clear error. Do not guess stacking semantics.
6. Conversion happens at field binding / expression compilation only. No post-query blanket conversion is allowed.
7. Existing governance must remain intact: fieldAccess, visibleColumns, deniedColumns, systemSlice, calculated field dependency extraction, and sanitized error behavior must not be weakened.

## Non-Goals

- No Python engine parity in this Java workitem.
- No `slice` reverse optimization in V1.
- No `SUM(field / semanticScaleFactor)` to `SUM(field) / semanticScaleFactor` optimization requirement in V1.
- No full unit algebra system.
- No currency exchange, FX conversion, or multi-currency normalization. `semanticUnit` and `semanticUnitLabel` only describe the semantic unit of the current field.
- No public SQL fragment field such as `physical_sql`.
- No UI or frontend work.

## Ownership

| Area | Owner | Responsibility |
|---|---|---|
| Workspace root | `foggy-data-mcp` | Owns cross-project contract and future Python parity planning |
| Java worktree | `foggy-data-mcp-bridge-wt-dev-compose` | Owns Java implementation, Java tests, and this execution progress |
| Java module | `foggy-dataset-model` | Primary implementation and tests |
| Demo resources | `foggy-dataset-demo` | Test TM/QM fixtures only when required by Java integration tests |
| MCP/docs-site | `foggy-dataset-mcp` / `docs-site` | Only update if Java metadata/schema descriptions are intentionally exposed in this phase |

## Acceptance Criteria

Java first phase is accepted only when all required cases pass with real query execution evidence:

- `columns: ["totalCost"]` returns semantic unit values.
- `slice: totalCost > 1000` filters using semantic unit values.
- `orderBy: ["-totalCost"]` sorts using semantic unit values.
- `sum(totalCost)` or predefined `aggregation: "sum"` returns semantic unit values.
- `having` references to money fields compare in semantic units.
- `calculatedFields` such as `incomeAmount - costAmount` return semantic unit values.
- Ratio expressions such as `costAmount / incomeAmount` do not apply a second conversion.
- `pivot.metrics` money fields return semantic unit values.
- `timeWindow.targetMetrics` money fields return semantic unit values.
- `deniedColumns` and `fieldAccess` still fail closed for semantic fields bound to denied or inaccessible physical columns.
- Model load rejects or clearly reports invalid `semanticScaleFactor` values and unsupported `formulaDef + semanticScaleFactor` stacking.

## Required Verification

Minimum commands:

```powershell
mvn install -pl foggy-dataset-demo -DskipTests
mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleFactorIntegrationTest" "-P!multi-db"
mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-P!multi-db"
mvn verify -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Pcoverage,!multi-db"
```

If external database profiles are not needed, SQLite/in-memory integration coverage is acceptable. If a case depends on multi-database behavior, record the exact profile and result in the progress document.

Current Java V1 evidence:

- Demo fixtures install: passed.
- Targeted semantic scale integration test: passed, 13 tests.
- Full `foggy-dataset-model` SQLite regression with `-P!multi-db`: passed, 2529 tests, 0 failures, 0 errors, 1 skipped.
- Dedicated semantic-scale pivot/timeWindow fixtures: added and passed with native SQL baselines.
- JaCoCo coverage gate with full SQLite regression: passed, 2532 tests, 0 failures, 0 errors, 1 skipped. `SemanticScaleSqlSupport` line coverage is 100% and branch coverage is 100%; module baseline gate is line >= 77% and branch >= 62%.

## Review Flow

After coding:

1. Confirm the execution agent has read `../../CLAUDE.md` from this Java worktree and follows its TM/QM engine, testing, and security constraints.
2. Update `OPT-money-scale-semantic-field-contract-java-progress.md`.
3. Run a lightweight implementation self-check and record the result.
4. Because this changes shared TM/QM field semantics, run `foggy-implementation-quality-gate`.
5. Run `foggy-test-coverage-audit` before formal acceptance.
6. Use `foggy-acceptance-signoff` only after implementation quality and coverage evidence are available.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-10
- acceptance_record: ../acceptance/OPT-money-scale-semantic-field-contract-java-acceptance.md
- blocking_items: none
- follow_up_required: yes
