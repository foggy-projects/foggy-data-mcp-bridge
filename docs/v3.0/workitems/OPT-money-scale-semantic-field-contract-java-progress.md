---
doc_role: progress
doc_purpose: Track Java implementation progress for MONEY-30-J1 money semantic scale fields.
version: v3.0
ticket: MONEY-30-J1
status: signed-off
owner: foggy-dataset-model
created_at: 2026-05-10
---

# Progress: Money Scale Semantic Field Contract for Java Engine

## 文档作用

- doc_type: progress
- intended_for: execution-agent | reviewer | signoff-owner
- purpose: 记录 Java 第一阶段实现、测试、自检和后续质量/覆盖/验收状态。

## Basic Info

- upstream_root_design: `../../../../docs/v3.0/OPT-money-scale-semantic-field-contract.md`
- local_requirement: `OPT-money-scale-semantic-field-contract-java.md`
- implementation_plan: `../detailed_design/01_money_scale_semantic_field_java_plan.md`
- current_status: accepted-with-risks
- completed_at: 2026-05-10

## Preconditions

| Check | Status | Notes |
|---|---|---|
| Root design reviewed | done | Root v3.0 design defines Java-first candidate scope |
| Java worktree CLAUDE.md reviewed | done | `foggy-dataset-model` owns TM/QM engine behavior |
| Version docs created | done | v3.0 Java execution docs initialized |
| Implementation started | done | Java V1 implementation completed in `foggy-dataset-model` with demo fixtures in `foggy-dataset-demo` |
| No currency exchange scope confirmed | done | `semanticUnit` / `semanticUnitLabel` are semantic metadata only |

## Development Progress

| Step | Status | Evidence / Notes |
|---|---|---|
| S1 Add model definition fields | done | Added fields to `DbMeasureDef`, `DbPropertyDef`, `DbMeasure`, and `DbProperty` |
| S2 Add loader validation | done | `SemanticScaleSqlSupport` validates factor and simple-column requirement; issue #2 follow-up allows resolved formula SQL with scale |
| S3 Apply semantic field binding | done | Measure/property physical column declarations are wrapped as `(declare / factor)`; no public generated `formulaDef` is exposed |
| S4 Preserve governance mapping | done | Physical mapping remains based on original `column`; deniedColumns and fieldAccess tests pass |
| S5 Add integration fixtures and tests | done | Added ecommerce TM/QM fixtures plus `SemanticScaleFactorIntegrationTest` |
| S6 Update progress and run review chain | done | Progress, quality gate, coverage audit, and acceptance signoff completed |

## Planned Code Touchpoints

| Path | Status | Notes |
|---|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/measure/DbMeasureDef.java` | done | Added semantic metadata fields |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/def/property/DbPropertyDef.java` | done | Added semantic metadata fields |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbMeasure.java` | done | Added default semantic metadata getters |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/DbProperty.java` | done | Added default semantic metadata getters |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | done | New helper for validation and SQL declaration wrapping |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | done | Added property semantic scale validation hook |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | done | Added metadata storage and scaled declaration support |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | done | Added metadata storage, validation, and scaled declaration support |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/PhysicalColumnMappingBuilder.java` | verified | No change required; governance behavior covered by targeted tests |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticServiceV3Impl.java` | unchanged | Metadata surfacing beyond runtime model fields is not part of Java V1 |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/` | done | Added semantic-scale valid and invalid TM/QM fixtures |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java` | done | Added focused integration coverage |

## Implementation Self-Check

| Item | Status | Notes |
|---|---|---|
| Requirement scope implemented | done | Java V1 covers definitions, runtime declaration scaling, validation, permissions, and focused query semantics |
| Non-goals kept out | done | No FX conversion, currency normalization, UI work, Python parity, or query rewrite optimization added |
| No arbitrary SQL fragment field introduced | done | `column` must remain a simple physical column when `semanticScaleFactor` is configured |
| Existing governance preserved | done | deniedColumns and fieldAccess tests pass against semantic fields backed by physical columns |
| No result-stage blanket conversion added | done | Conversion occurs in SQL declaration compilation only |
| No unrelated refactor mixed in | done | Changes are scoped to semantic scale support; pre-existing dirty worktree changes were not reverted |
| Self-check conclusion recorded | done | `quality-gate-and-acceptance-complete` |

## Testing Progress

| Test Area | Required | Status | Command / Evidence |
|---|---:|---|---|
| Demo fixtures install | yes if fixtures changed | passed | `mvn install -pl foggy-dataset-demo -DskipTests` |
| Targeted money semantic tests | yes | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleFactorIntegrationTest" "-P!multi-db"`; 13 tests passed |
| Full model module regression | yes | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-P!multi-db"`; 2529 tests, 0 failures, 0 errors, 1 skipped |
| Full model module coverage gate | yes | passed | `mvn verify -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Pcoverage,!multi-db"`; 2532 tests, 0 failures, 0 errors, 1 skipped; JaCoCo line 77.60%, branch 62.08%; `SemanticScaleSqlSupport` line/branch 100% |
| deniedColumns / fieldAccess regression | yes | passed | Covered by `SemanticScaleFactorIntegrationTest` |
| Pivot metrics semantic scale | yes | passed | Dedicated `pivotMetric_usesSemanticScaledMeasure` compares `pivot.metrics` with native SQL `sum(sales_amount / 100.0)` |
| timeWindow targetMetrics semantic scale | yes | passed | Dedicated `timeWindowTargetMetric_usesSemanticScaledMeasure` compares rolling window output with native SQL baseline |

## Experience Progress

experience: N/A. This work is backend semantic engine behavior and has no UI interaction surface.

## Acceptance Criteria Tracking

| Criterion | Status | Evidence |
|---|---|---|
| `columns: ["totalCost"]` returns semantic unit values | done | Property projection test compares result with native SQL `unit_price / 100.0` |
| `slice: totalCost > 1000` filters in semantic units | done | Slice test compares query result with native SQL semantic predicate |
| `orderBy: ["-totalCost"]` sorts in semantic units | done | Order tests compare semantic ordering with native SQL |
| `sum(totalCost)` or `aggregation: "sum"` returns semantic units | done | Measure aggregation test compares with native SQL `sum(sales_amount / 100.0)` |
| `having` compares in semantic units | done | Having test uses inline aggregate alias and native SQL baseline |
| `calculatedFields` money arithmetic returns semantic units | done | Calculated field test uses scaled leaf values and native SQL baseline |
| Ratio expression does not double-convert | done | Ratio test confirms scaled property/measure leaves are not converted twice |
| `pivot.metrics` returns semantic units | done | Dedicated pivot integration test with native SQL baseline |
| `timeWindow.targetMetrics` returns semantic units | done | Dedicated rolling_7d timeWindow integration test with native SQL baseline |
| denied physical column denies semantic field | done | Targeted deniedColumns test rejects semantic field backed by denied physical column |
| fieldAccess still applies to semantic field name | done | Targeted fieldAccess allowlist test rejects inaccessible semantic field |
| invalid model shapes fail closed | done | Invalid formula stacking and SQL-expression-in-column fixtures fail model load |

## Blockers

- No implementation blocker currently recorded.
- No acceptance-item evidence coverage gap currently recorded for Java V1 semantic scale behavior.

## Review / Audit / Acceptance

| Stage | Status | Notes |
|---|---|---|
| Lightweight implementation self-check | done | Quality gate and acceptance signoff completed after self-check |
| `foggy-implementation-quality-gate` | done | `../quality/OPT-money-scale-semantic-field-contract-java-implementation-quality.md` |
| `foggy-test-coverage-audit` | done | `../coverage/OPT-money-scale-semantic-field-contract-java-coverage-audit.md` |
| `foggy-acceptance-signoff` | signed-off | `../acceptance/OPT-money-scale-semantic-field-contract-java-acceptance.md`; decision: `accepted-with-risks` |

## Execution Check-in

2026-05-10 implementation check-in:

- Completed Java V1 semantic scale support for TM measure/property definitions, runtime SQL declaration scaling, validation, and metadata retention.
- Added `SemanticScaleSqlSupport` and wired it through measure/property runtime support without introducing a public SQL fragment or result-stage blanket conversion.
- Added ecommerce semantic-scale fixtures and `SemanticScaleFactorIntegrationTest`.
- Verified demo fixture install and model tests:
  - `mvn install -pl foggy-dataset-demo -DskipTests`: passed
  - `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleFactorIntegrationTest" "-P!multi-db"`: passed, 13 tests
  - `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-P!multi-db"`: passed, 2529 tests, 0 failures, 0 errors, 1 skipped
- Self-check conclusion: implementation proceeded through formal `foggy-implementation-quality-gate`.
- Coverage audit conclusion: acceptance-item evidence is complete for Java V1; no dedicated pivot/timeWindow gap remains.
- Quality gate conclusion: no blocking implementation quality findings; acceptance signoff completed with residual release-policy risks acknowledged.
- Coverage gate follow-up: added explicit `coverage` profile with JaCoCo report/check. The current full SQLite gate passes at module line 77.60%, module branch 62.08%, and `SemanticScaleSqlSupport` line/branch 100%.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: codex
- signed_off_at: 2026-05-10
- acceptance_record: ../acceptance/OPT-money-scale-semantic-field-contract-java-acceptance.md
- blocking_items: none
- follow_up_required: yes

## Follow-up: GitHub Issue #2 Formula Semantic Scale

2026-05-26 follow-up scope:

- Support `formulaDef.value + semanticScaleFactor` in the Java query engine.
- Support `formulaDef.builder + semanticScaleFactor` by applying scale to the builder SQL return value.
- Support `dialectFormulaDef.value + semanticScaleFactor`.
- Keep `formulaDef.value` / `dialectFormulaDef.value` as raw SQL fragments, not parsed fsscript expressions.
- Keep `calculatedFields[].expression` as the expression path that Java parses and compiles.
- Document pre-aggregation behavior: formula-backed pre-aggregation columns must materialize the intended formula semantic result, because the pre-aggregation rewrite path reads materialized columns directly.

Follow-up code touchpoints:

| Path | Status | Notes |
|---|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/FormulaSqlSupport.java` | added | Centralizes formula SQL `alias` placeholder replacement |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | updated | Resolves builder or raw SQL formula, with dialect-specific priority and generic fallback |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | updated | Applies semantic scale to resolved formula SQL / builder output; supports formula-only measures only when formula is resolved |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | updated | Applies semantic scale to property formula SQL / builder output |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | updated | Allows formula + scale and parenthesizes the scaled base expression |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/FactSalesSemanticScaleFormulaModel.tm` | added | Demo fixture for formula-backed semantic fields |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/FactSalesSemanticScaleFormulaQueryModel.qm` | added | Query model exposing formula-backed semantic fields |
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/FactSalesSemanticScaleFormulaUnsupportedDialectInvalidModel.tm` | added | Negative fixture for formula-only unsupported dialect |
| `foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactSalesPreAggModel.tm` | updated | Adds `salesAmountFormulaYuan` as a formula-backed semantic measure and maps it to a materialized pre-aggregation column |
| `foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/query/FactSalesPreAggQueryModel.qm` | updated | Exposes the formula-backed semantic measure in the pre-aggregation query model |
| `foggy-dataset-model/src/test/resources/sqlite/04-preagg-schema.sql` / `05-preagg-data.sql` | updated | Adds and populates `sales_amount_formula_yuan_sum` from the final semantic formula result |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/SemanticScaleFactorIntegrationTest.java` | updated | Adds formula measure/property, enabled/disabled semantic scale, invalid dialect coverage, and `SemanticQueryServiceV3` DSL-to-native-SQL comparison |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/preagg/PreAggregationMatcherTest.java` | updated | Adds formula-backed measure materialized pre-aggregation matching coverage |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationIntegrationTest.java` | updated | Executes rewritten pre-aggregation SQL against SQLite and compares it with native fact-table SQL |

Follow-up testing evidence:

| Test Area | Status | Evidence |
|---|---|---|
| Demo fixtures install | passed | `mvn install -pl foggy-dataset-demo -DskipTests`; build success |
| Targeted semantic scale tests | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleSqlSupportTest,SemanticScaleFactorIntegrationTest" "-P!multi-db"`; 27 tests, 0 failures, 0 errors |
| Caption formula regression | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=CaptionDefTest" "-P!multi-db"`; 13 tests, 0 failures, 0 errors |
| Wider ecommerce regression | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=ModelLoadingTest,PhysicalColumnMappingIntegrationTest" "-P!multi-db"`; 46 tests, 0 failures, 0 errors |
| Full model module coverage gate | passed | `mvn verify -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Pcoverage,!multi-db"`; 2883 tests, 0 failures, 0 errors, 1 skipped; module line 79.14%, branch 63.09%; `SemanticScaleSqlSupport` and `FormulaSqlSupport` line/branch 100% |
| Main regression after pull | passed | `git pull --ff-only` to `fb5a060c`, then root `mvn test`; build success |
| Semantic DSL real-data comparison | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleFactorIntegrationTest" "-P!multi-db"`; 26 tests, 0 failures, 0 errors |
| Pre-aggregation materialized-column contract | passed | `mvn test -pl foggy-dataset-model "-Dtest=PreAggregationMatcherTest" "-P!multi-db"`; 12 tests, 0 failures, 0 errors |
| Pre-aggregation real SQL comparison | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=PreAggregationIntegrationTest#testFormulaSemanticMeasurePreAggResultMatchesNativeSql" "-P!multi-db"`; rewritten SQL reads `preagg_daily_product_sales.sales_amount_formula_yuan_sum` and matches native fact-table SQL |
| Pre-aggregation + semantic regression set | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=PreAggregationIntegrationTest,PreAggregationDataValidationTest,SemanticScaleFactorIntegrationTest,PreAggregationMatcherTest" "-P!multi-db"`; 71 tests, 0 failures, 0 errors |
| Full model module regression after pre-aggregation fixture update | passed | `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-P!multi-db"`; 2905 tests, 0 failures, 0 errors, 1 skipped |

Follow-up acceptance notes:

- Real-data evidence is covered by `SemanticScaleFactorIntegrationTest` using both DSL-style `DbQueryRequestDef` execution and the `SemanticQueryServiceV3` DSL entrypoint against ecommerce SQLite fixture data:
  - `semanticScaleWithFormula_queryDataMatchesNativeSql`: formula measure query with semantic scale enabled compared to native SQL using `/ 100.0`.
  - `semanticScaleWithFormulaProperty_queryDataMatchesNativeSql`: formula property query with semantic scale enabled compared to native SQL using `/ 100.0`.
  - `disabledNamespace_formulaQueryUsesPhysicalFormulaValues`: formula measure query with semantic scale disabled compared to native SQL without semantic division.
- `semanticDslFormulaSemanticScaleToggle_matchesNativeSql`: `SemanticQueryServiceV3` query with semantic scale enabled and disabled namespace compared to native SQL baselines.
- Pre-aggregation is documented and tested as a materialized-column contract and is not expanded in this follow-up implementation.
  - `formulaMeasureWithoutMaterializedPreAggColumnDoesNotMatch`: a formula-backed measure absent from pre-aggregation measures does not match.
  - `formulaMeasureMatchesWhenMaterializedPreAggColumnConfigured`: a formula-backed measure matches only when a compatible materialized pre-aggregation column is configured.
  - `testFormulaSemanticMeasurePreAggResultMatchesNativeSql`: the rewrite path reads a real materialized formula semantic result column and compares the result with native fact-table SQL.
- Hybrid pre-aggregation with formula-backed semantic measures is recorded as a follow-up risk: the direct materialized pre-aggregation path is verified here; hybrid raw-tail SQL still needs dedicated formula expansion coverage before being enabled for this field shape.
- Follow-up quality gate: `../quality/ISSUE-2-formula-semantic-scale-java-implementation-quality.md`.
- Follow-up coverage audit: `../coverage/OPT-money-scale-semantic-field-contract-java-coverage-audit.md`, section `Follow-up: GitHub Issue #2 Formula Semantic Scale`.
