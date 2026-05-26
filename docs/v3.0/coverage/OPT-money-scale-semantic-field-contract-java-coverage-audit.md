---
audit_scope: feature
audit_mode: pre-acceptance-check
version: v3.0
target: MONEY-30-J1 Java money semantic scale, including GitHub issue #2 formula semantic scale follow-up
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex
reviewed_at: 2026-05-26
follow_up_required: yes
---

# Test Coverage Audit: Money Semantic Scale Java

## Background

This audit maps the Java semantic money scale contract to executable evidence. It includes the original V1 column-based semantic scale behavior and the GitHub issue #2 follow-up for `formulaDef` / `dialectFormulaDef` with `semanticScaleFactor`.

## Audit Basis

- Workitem: `../workitems/OPT-money-scale-semantic-field-contract-java.md`
- Progress: `../workitems/OPT-money-scale-semantic-field-contract-java-progress.md`
- Quality gate: `../quality/OPT-money-scale-semantic-field-contract-java-implementation-quality.md`
- Issue #2 quality gate: `../quality/ISSUE-2-formula-semantic-scale-java-implementation-quality.md`
- Main test suites:
  - `SemanticScaleFactorIntegrationTest`
  - `SemanticScaleSqlSupportTest`
  - `PreAggregationMatcherTest`
  - `CaptionDefTest`
  - `ModelLoadingTest`
  - `PhysicalColumnMappingIntegrationTest`

## Follow-up: GitHub Issue #2 Formula Semantic Scale

The follow-up coverage items below confirm that formula SQL is treated as a database SQL fragment, semantic scaling is applied to the whole formula result, semantic DSL execution matches native SQL baselines, and pre-aggregation remains an explicit materialized-column contract.

## Coverage Matrix

| Requirement / Risk | Priority | Evidence Type | Evidence | Status |
|---|---:|---|---|---|
| Plain column semantic scale applies to select, filter, order, aggregation, having, calculated fields, pivot, timeWindow, and access control paths | P1 | integration-test | `SemanticScaleFactorIntegrationTest` plus full module coverage gate | covered |
| `formulaDef.value + semanticScaleFactor` produces native-SQL-equivalent values | P1 | integration-test | `semanticScaleWithFormula_queryDataMatchesNativeSql`; `semanticDslFormulaSemanticScaleToggle_matchesNativeSql` | covered |
| `formulaDef.builder + semanticScaleFactor` applies scale to the builder SQL return value | P1 | integration-test | `semanticScaleWithFormula_queryDataMatchesNativeSql`; `semanticDslFormulaSemanticScaleToggle_matchesNativeSql` | covered |
| `dialectFormulaDef.value + semanticScaleFactor` applies scale to the resolved dialect formula | P1 | integration-test | `semanticScaleWithFormula_queryDataMatchesNativeSql`; `semanticDslFormulaSemanticScaleToggle_matchesNativeSql` | covered |
| Property `formulaDef.value + semanticScaleFactor` produces native-SQL-equivalent values | P1 | integration-test | `semanticScaleWithFormulaProperty_queryDataMatchesNativeSql` | covered |
| Disabled namespace clears semantic scale and uses physical formula values | P1 | integration-test | `disabledNamespace_formulaQueryUsesPhysicalFormulaValues`; `semanticDslFormulaSemanticScaleToggle_matchesNativeSql` | covered |
| Formula-only measure without a resolved formula fails closed | P1 | integration-test | `formulaOnlyMeasureWithoutResolvedDialect_rejectedOnModelLoad` | covered |
| Formula-backed pre-aggregation does not match without an explicit materialized measure column | P1 | unit-test | `PreAggregationMatcherTest.formulaMeasureWithoutMaterializedPreAggColumnDoesNotMatch` | covered |
| Formula-backed pre-aggregation can match when the materialized semantic result column is explicitly configured | P1 | unit-test | `PreAggregationMatcherTest.formulaMeasureMatchesWhenMaterializedPreAggColumnConfigured` | covered |
| Existing caption formula and physical column mapping behavior remain compatible | P2 | regression-test | `CaptionDefTest`; `ModelLoadingTest`; `PhysicalColumnMappingIntegrationTest` | covered |

## Evidence Summary

| Command | Result |
|---|---|
| `git pull --ff-only` in main worktree | fast-forwarded to `fb5a060c` |
| `mvn test` | passed on current `main` root reactor |
| `mvn install -pl foggy-dataset-demo -DskipTests` | passed |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleSqlSupportTest,SemanticScaleFactorIntegrationTest" "-P!multi-db"` | passed, 27 tests |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=SemanticScaleFactorIntegrationTest" "-P!multi-db"` | passed, 26 tests |
| `mvn test -pl foggy-dataset-model "-Dtest=PreAggregationMatcherTest" "-P!multi-db"` | passed, 12 tests |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=CaptionDefTest" "-P!multi-db"` | passed, 13 tests |
| `mvn test -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=ModelLoadingTest,PhysicalColumnMappingIntegrationTest" "-P!multi-db"` | passed, 46 tests |
| `mvn verify -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Pcoverage,!multi-db"` | passed, 2883 tests, 0 failures, 0 errors, 1 skipped; module line 79.14%, branch 63.09%; `SemanticScaleSqlSupport` and `FormulaSqlSupport` line/branch 100% |

## Gaps

No blocking coverage gap is recorded for the Java implementation.

Residual risks:

- Raw SQL dependency inference for arbitrary `formulaDef.value` / `dialectFormulaDef.value` remains outside this follow-up.
- Pre-aggregation still relies on model authors or build jobs materializing formula-backed semantic measure results into explicit pre-aggregation columns.
- External database profile reruns are optional for this follow-up because the covered behavior is SQL generation and SQLite fixture execution; multi-database coverage should be rerun only when dialect-specific behavior is changed.

## Conclusion

Conclusion: `ready-for-acceptance`.

The required Java semantic scale behavior has direct test evidence, the issue #2 formula follow-up has native SQL comparisons through the semantic DSL path, and pre-aggregation behavior is covered as a materialized-column contract.
