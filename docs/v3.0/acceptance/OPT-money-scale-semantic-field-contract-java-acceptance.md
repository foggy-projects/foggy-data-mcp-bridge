---
doc_role: acceptance
doc_purpose: Formal acceptance record for MONEY-30-J1 Java money semantic scale fields.
acceptance_scope: feature
version: v3.0
target: MONEY-30-J1 money semantic scale fields for Java TM/QM engine
status: signed-off
decision: accepted-with-risks
signed_off_by: codex
signed_off_at: 2026-05-10
reviewed_by: codex
blocking_items: []
follow_up_required: yes
evidence_count: 9
---

# Feature Acceptance

## Background

MONEY-30-J1 implements Java V1 support for TM/QM money fields whose physical database values are stored in minor units, while the LLM-facing model exposes semantic business units. The accepted Java scope covers model definition fields, runtime SQL declaration scaling, fail-closed validation, permission/governance preservation, dedicated fixtures, focused integration tests, and module regression evidence.

This signoff does not accept currency exchange, FX conversion, multi-currency normalization, UI behavior, Python parity, or query rewrite optimization. `semanticUnit` and `semanticUnitLabel` are metadata only.

## Acceptance Basis

- Root contract: `../../../../docs/v3.0/OPT-money-scale-semantic-field-contract.md`
- Local workitem: `../workitems/OPT-money-scale-semantic-field-contract-java.md`
- Implementation plan: `../detailed_design/01_money_scale_semantic_field_java_plan.md`
- Progress record: `../workitems/OPT-money-scale-semantic-field-contract-java-progress.md`
- Implementation quality gate: `../quality/OPT-money-scale-semantic-field-contract-java-implementation-quality.md`
- Test coverage audit: `../coverage/OPT-money-scale-semantic-field-contract-java-coverage-audit.md`
- Test code: `../../../foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java`
- Demo fixtures: `../../../foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/`

## Checklist

| Item | Status | Evidence |
|---|---|---|
| Model fields use TM/QM naming: `column`, `aggregation`, `semanticScaleFactor`, `semanticUnit`, `semanticUnitLabel` | pass | Root contract and local workitem updated |
| Plain property projection returns semantic unit values | pass | Native SQL baseline in targeted integration test |
| Slice filters compare in semantic units | pass | Native SQL baseline in targeted integration test |
| OrderBy sorts in semantic units | pass | Property and grouped measure ordering tests |
| Aggregation returns semantic unit values | pass | Measure aggregation test with `sum(sales_amount / 100.0)` baseline |
| Having compares semantic aggregate values | pass | Having integration test with native SQL baseline |
| Calculated fields use scaled leaf values | pass | Calculated field integration test |
| Ratio expressions do not double-convert | pass | Ratio integration test |
| `pivot.metrics` returns semantic unit values | pass | Dedicated pivot integration test |
| `timeWindow.targetMetrics` returns semantic unit values | pass | Dedicated rolling window integration test |
| deniedColumns and fieldAccess remain fail-closed | pass | Dedicated governance regression tests |
| Invalid model shapes fail closed | pass | Formula stacking and SQL-expression column fixtures reject load |
| Implementation quality gate completed | pass | Quality gate reports no blocking findings |
| Coverage audit completed | pass | Audit reports no acceptance-item evidence gap |
| Numeric coverage gate completed | pass | `coverage` profile passes JaCoCo check; semantic scale helper line/branch coverage is 100% |
| Required Maven verification completed | pass | Demo install, targeted test suite, and full SQLite model regression passed |

## Evidence

1. Requirement and naming contract are updated in `docs/v3.0/OPT-money-scale-semantic-field-contract.md` and the local workitem.
2. Implementation code paths cover definition fields, SPI getters, `SemanticScaleSqlSupport`, loader validation, measure runtime binding, and property runtime binding.
3. Valid and invalid ecommerce TM/QM fixtures are present under `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/`.
4. Targeted semantic-scale integration suite passed: `SemanticScaleFactorIntegrationTest`, 13 tests, 0 failures, 0 errors, 0 skipped.
5. Full `foggy-dataset-model` SQLite regression passed: 2529 tests, 0 failures, 0 errors, 1 skipped.
6. Implementation quality gate reports no blocking implementation quality findings.
7. Test coverage audit concludes `ready-for-acceptance` with every Java V1 acceptance item mapped to evidence.
8. JaCoCo coverage gate passes with full SQLite regression: 2532 tests, 0 failures, 0 errors, 1 skipped; module line 77.60%, module branch 62.08%, `SemanticScaleSqlSupport` line/branch 100%.
9. Progress record captures execution check-in, commands, test results, coverage gate result, and residual risks.

## Failed Items

No failed acceptance item.

## Risks / Open Items

- No blocking item remains for Java V1 acceptance.
- The explicit `coverage` profile now enforces JaCoCo checks for this module. The whole-module threshold is a current baseline gate, while the new semantic-scale helper is gated at 100% line/branch coverage.
- Multi-database profiles were not rerun in this pass. If release policy requires MySQL, PostgreSQL, or SQL Server parity, run the existing multi-db profile set before release packaging.
- `semanticUnit` and `semanticUnitLabel` are accepted as metadata only; they do not imply FX conversion or multi-currency normalization.
- The worktree contains pre-existing unrelated dirty files, and `DbPropertyImpl.java` had unrelated local modifications in the same file before this signoff.

## Final Decision

Decision: `accepted-with-risks`.

MONEY-30-J1 meets the Java V1 requirement and acceptance criteria with complete requirement-level evidence, no blocking implementation quality findings, passing targeted plus full-module SQLite regression, and passing JaCoCo coverage gate. The remaining items are release-governance follow-ups rather than blockers for this feature signoff.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-10
- acceptance_record: docs/v3.0/acceptance/OPT-money-scale-semantic-field-contract-java-acceptance.md
- blocking_items: none
- follow_up_required: yes
