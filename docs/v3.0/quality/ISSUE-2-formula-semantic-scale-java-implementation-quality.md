---
quality_scope: feature-follow-up
quality_mode: pre-coverage-audit
version: v3.0
target: GitHub issue #2 formulaDef/dialectFormulaDef semanticScaleFactor support
status: reviewed
decision: ready-for-coverage-audit
reviewed_by: codex
reviewed_at: 2026-05-26
follow_up_required: yes
---

# Implementation Quality Gate: Issue #2 Formula Semantic Scale

## Background

GitHub issue #2 extends the Java TM/QM semantic scale contract from plain physical columns to formula-backed fields:

- `formulaDef.value + semanticScaleFactor`
- `formulaDef.builder + semanticScaleFactor`
- `dialectFormulaDef.value + semanticScaleFactor`

The formula SQL is evaluated as the physical numeric expression first, then the semantic scale is applied to the whole formula result.

## Check Basis

- Requirement/progress: `../workitems/OPT-money-scale-semantic-field-contract-java.md`
- Progress evidence: `../workitems/OPT-money-scale-semantic-field-contract-java-progress.md`
- Related prior quality gate: `OPT-money-scale-semantic-field-contract-java-implementation-quality.md`
- Targeted tests:
  - `SemanticScaleSqlSupportTest`
  - `SemanticScaleFactorIntegrationTest`
  - `CaptionDefTest`
  - `ModelLoadingTest`
  - `PhysicalColumnMappingIntegrationTest`

## Changed Surface

| Area | Path | Review |
|---|---|---|
| Formula SQL helper | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/FormulaSqlSupport.java` | Small centralized helper for alias placeholder replacement |
| Scale SQL helper | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/SemanticScaleSqlSupport.java` | Allows formula + scale and parenthesizes scaled formula output |
| Loader formula resolution | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java` | Resolves builder or raw SQL with dialect priority and generic fallback |
| Measure runtime | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/measure/DbMeasureSupport.java` | Applies scale to formula SQL / builder output and supports formula-only measure when resolved |
| Property runtime | `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/property/DbPropertyImpl.java` | Applies scale to property formula SQL / builder output |
| API docs | `DbMeasureDef.java`, `DbPropertyDef.java` | Removed obsolete formula conflict warning |
| Fixtures/tests | `foggy-dataset-demo`, `SemanticScaleFactorIntegrationTest` | Added positive formula fixtures, unsupported dialect negative fixture, and real-data SQL comparisons |

## Quality Checklist

| Check | Result | Notes |
|---|---|---|
| Scope conformance | pass | Changes are scoped to formula resolution, SQL declaration generation, fixtures, tests, and docs |
| Existing behavior compatibility | pass | Caption formula regression and model loading regressions pass |
| SQL generation safety | pass | Formula base expressions are wrapped before division, avoiding precedence bugs |
| Validation behavior | pass | Formula-only measure without resolved dialect formula still fails closed |
| Duplication control | pass | Alias handling is consolidated in `FormulaSqlSupport`; scale wrapping remains in `SemanticScaleSqlSupport` |
| Real-data verification | pass | Enabled and disabled semantic scale modes are compared against native SQL on ecommerce fixture data |
| Pre-aggregation clarity | pass | Pre-aggregation behavior is explicitly documented as a materialized-column contract |

## Findings

No blocking implementation quality findings.

Non-blocking observations:

- `formulaDef.value` and `dialectFormulaDef.value` remain raw SQL fragments. This is intentional and documented; the Java engine does not parse them as fsscript expressions.
- Formula-only measures have no simple physical `column` dependency. The implementation supports them only when a formula is resolved for the current dialect, but physical dependency inference for arbitrary raw SQL is still outside this follow-up.
- Pre-aggregation was not changed. Formula-backed semantic measures require a materialized pre-aggregation column containing the intended semantic result, or the query must fall back to the base model path.

## Verification

| Command | Result |
|---|---|
| `mvn install -pl foggy-dataset-demo -DskipTests` | passed |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleSqlSupportTest,SemanticScaleFactorIntegrationTest" "-P!multi-db"` | passed, 27 tests |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=CaptionDefTest" "-P!multi-db"` | passed, 13 tests |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=ModelLoadingTest,PhysicalColumnMappingIntegrationTest" "-P!multi-db"` | passed, 46 tests |
| `mvn verify -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Pcoverage,!multi-db"` | passed, 2883 tests, module line 79.14%, branch 63.09%, new helpers line/branch 100% |

## Decision

Decision: `ready-for-coverage-audit`.

The implementation is ready for coverage audit with the documented residual risks around raw SQL dependency inference and pre-aggregation materialization.
