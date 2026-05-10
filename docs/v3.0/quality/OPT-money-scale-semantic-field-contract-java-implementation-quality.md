---
quality_scope: feature
quality_mode: pre-coverage-audit
version: v3.0
target: MONEY-30-J1 money semantic scale fields for Java TM/QM engine
status: reviewed
decision: ready-for-coverage-audit
reviewed_by: codex
reviewed_at: 2026-05-10
follow_up_required: yes
---

# Implementation Quality Gate

## Background

Java V1 implements semantic money-unit scaling for TM/QM measure and property fields. The implementation must keep model naming aligned with existing TM/QM contracts, avoid exposing arbitrary SQL fragments, preserve physical-column governance, and keep conversion inside query compilation rather than result post-processing.

This quality gate is run after implementation and after the coverage audit was already completed. The decision remains `ready-for-coverage-audit` to match the fixed quality-gate contract; practically, the next stage is acceptance signoff.

## Check Basis

- Requirement: `../workitems/OPT-money-scale-semantic-field-contract-java.md`
- Implementation plan: `../detailed_design/01_money_scale_semantic_field_java_plan.md`
- Progress: `../workitems/OPT-money-scale-semantic-field-contract-java-progress.md`
- Coverage audit: `../coverage/OPT-money-scale-semantic-field-contract-java-coverage-audit.md`
- Targeted test result: `SemanticScaleFactorIntegrationTest`, 13 tests passed
- Module regression result: `foggy-dataset-model` SQLite regression, 2529 tests, 0 failures, 0 errors, 1 skipped

## Changed Surface

| Area | Path | Quality Review |
|---|---|---|
| Definition fields | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/measure/DbMeasureDef.java` | Scoped metadata additions |
| Definition fields | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/property/DbPropertyDef.java` | Scoped metadata additions |
| SPI compatibility | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbMeasure.java` | Default getters preserve implementor compatibility |
| SPI compatibility | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbProperty.java` | Default getters preserve implementor compatibility |
| SQL scaling helper | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | Centralized validation and declaration wrapping |
| Loader validation | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | Property validation hook added |
| Measure runtime | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | Non-formula physical-column declaration scaling |
| Property runtime | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | Non-formula physical-column declaration scaling |
| Fixtures | `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/` | Dedicated valid and invalid semantic-scale models |
| Tests | `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java` | Real SQL baseline coverage for all acceptance items |
| Docs | `docs/v3.0/` | Requirement, plan, progress, coverage audit updated |

## Quality Checklist

| Check | Result | Notes |
|---|---|---|
| Scope conformance | pass | Implementation is confined to TM/QM model definitions, runtime binding, loader validation, demo fixtures, tests, and version docs |
| Missing required implementation | pass | Measure and property paths both support metadata, validation, and declaration scaling |
| Code hygiene | pass | No TODO/FIXME/debug print residue found in new semantic-scale paths |
| Duplication and consolidation | pass | Shared validation and SQL literal handling are centralized in `SemanticScaleSqlSupport` |
| Complexity and abstraction | pass | A small helper is sufficient; no strategy/factory layer is justified for this V1 rule |
| Error handling and edge cases | pass | Non-positive scale, formula stacking, and SQL-expression `column` are fail-closed |
| Readability and maintainability | pass | The scale contract is visible at definition fields and concentrated at column declaration boundaries |
| Critical logic documentation | pass | Version docs explain why conversion is declaration-level and not result-stage |
| Contract and compatibility | pass | Existing `column`, `aggregation`, fieldAccess, and deniedColumns contracts remain intact |
| Documentation and writeback | pass | Workitem, plan, progress, and coverage audit are updated |
| Test alignment | pass | Tests map directly to projection, slice, order, aggregation, having, calculated fields, ratio, pivot, timeWindow, permissions, and invalid models |
| Release readiness | pass | No implementation blocker found for moving to acceptance signoff |

## Findings

No blocking implementation quality findings.

Non-blocking observations:

- The worktree contains many pre-existing dirty files outside this feature. They were not reverted or normalized in this pass.
- `DbPropertyImpl.java` already had unrelated local modifications in the same file; review focused on the semantic-scale additions and verified full module regression remains green.
- The Maven module now has an explicit `coverage` profile with JaCoCo report/check. The semantic-scale helper is gated at 100% line/branch coverage; the whole-module legacy baseline is gated at line >= 77% and branch >= 62%.

## Risks / Follow-ups

- If the team wants to raise the whole legacy module to 80/80 or higher, treat that as a separate broad test-hardening effort rather than mixing unrelated module-wide test debt into MONEY-30-J1.
- Multi-database behavior was not rerun in this pass because Java V1 evidence uses SQLite with `-P!multi-db`; if release policy requires MySQL/PostgreSQL/SQL Server parity, run the existing multi-db profile set before release packaging.
- Acceptance signoff explicitly acknowledges that `semanticUnit` and `semanticUnitLabel` are metadata only and do not imply FX conversion or multi-currency normalization.

## Follow-up State

- Formal `foggy-acceptance-signoff` is complete in `../acceptance/OPT-money-scale-semantic-field-contract-java-acceptance.md`.

## Decision

Decision: `ready-for-coverage-audit`.

Coverage audit and formal acceptance signoff are now complete. The feature signoff decision is `accepted-with-risks`.
