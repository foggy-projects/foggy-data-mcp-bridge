---
type: bug
bug_source: user-report
version: 9.2.0
ticket: BUG-dsl-cte-cross-model-cte-stages
severity: major
status: local-verified
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-20
updated_at: 2026-06-20
owner_module: foggy-dataset-model
---

# BUG: DSL_CTE Cross-model Bridge Rejects Structured CTE Base SQL

## Background

Architecture review found that `DslCteDslRequestMapper` has multiple cross-model bridge wrappers that reject a base `SqlGenerationResult` when it contains structured CTE stages. The engine already exposes structured CTE output through `SqlGenerationResult.CteStage`, and result-stage DSL_CTE wrappers can consume it, but cross-model join/funnel wrappers still fail closed.

## Reproduction

Use a signed DSL_CTE cross-model bridge, then make one or more generated base SQL results include `SqlGenerationResult.CteStage`.

Initial target:

- `DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult#wrap`
- left or right `SqlGenerationResult` has `hasCteStages() == true`

## Expected vs Actual

Expected:

- Cross-model DSL_CTE wrappers should hoist structured base CTE stages into the final top-level `WITH` block.
- Final SQL should preserve stage parameter ordering before base body parameters and wrapper parameters.
- Existing single-pass base SQL should continue to work.

Actual before fix:

- Cross-model wrappers reject base SQL with structured CTE stages or leading `WITH`.
- The runtime error is triggered before SQL execution, even though the engine already has structured CTE metadata.

## Impact Scope

- Affects DSL_CTE cross-model join/funnel execution bridge paths.
- Most visible when a base QM uses window calculated fields or another engine feature that emits CTE wrapping.
- Does not affect ordinary DSL queries.
- Does not affect result-stage DSL_CTE wrappers that already handle `base.hasCteStages()`.

## Test Strategy

Automation is required because this is a core query compiler path and can regress silently.

Planned unit coverage:

- Add a failing unit test in `DslCteAcceptanceSampleTest`.
- Use existing signed cross-model join-align fixture.
- Mock `QueryFacade.buildSqlOnly` to return structured CTE `SqlGenerationResult` for left and right base requests.
- Assert final SQL hoists the base stages and emits wrapper CTEs instead of throwing.
- Assert params preserve stage params, base params, then wrapper params.
- Add supplementary coverage for raw leading `WITH` rejection.
- Add supplementary coverage for a three-input time-attribution wrapper, because it has the same base CTE rejection and parameter ordering risk.

## Repair Plan

- Add a shared structured-base CTE helper in `DslCteDslRequestMapper`.
- Let the helper validate base SQL, reject raw leading `WITH`, hoist `SqlGenerationResult.CteStage` entries, and wrap the base body under the existing wrapper alias.
- Rename hoisted stage aliases with the wrapper alias as prefix, so multiple base inputs that each expose `stage1` do not collide in the final `WITH`.
- Rewrite base-stage references inside stage SQL and base SQL using the alias map.
- Replace duplicated base CTE assembly in cross-model join-align, source-rate, money-attribution, and time-attribution wrappers.
- Keep existing wrapper CTE names and result SQL shape stable except for safe hoisting of structured base stages.

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/support/DslCteDslRequestMapper.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/SqlGenerationResult.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/DslCteAcceptanceSampleTest.java`

## Fix Checklist

- [x] Establish failing unit test.
- [x] Confirm whether additional cross-model wrappers need equivalent tests.
- [x] Implement minimal CTE-stage hoisting for affected cross-model wrappers.
- [x] Keep existing rejection for raw leading `WITH` SQL unless a safe parser/structured representation is available.
- [x] Run targeted test class.
- [x] Update this work item with final verification.

## Verification

- 2026-06-20: Added failing unit test
  `DslCteAcceptanceSampleTest#generateSqlCrossModelJoinAlignHoistsStructuredBaseCteStages`.
- 2026-06-20: Confirmed current failure with
  `mvn -pl foggy-dataset-model -Dtest=DslCteAcceptanceSampleTest#generateSqlCrossModelJoinAlignHoistsStructuredBaseCteStages test`.
  Failure: `DSL_CTE_JOIN_ALIGN_LEFT_BASE_WITH_UNSUPPORTED` from
  `DslCteDslRequestMapper$CrossModelJoinAlignPlan.validateBaseSql`.
- 2026-06-20: Added supplementary tests:
  `generateSqlCrossModelJoinAlignRejectsRawWithBaseSql` and
  `generateSqlCrossModelTimeAttributionHoistsStructuredBaseCteStages`.
  Running the three focused methods produced two expected failures for structured CTE bases and kept raw `WITH` rejection passing.
- 2026-06-20: Implemented shared structured-base CTE hoisting in
  `DslCteDslRequestMapper`. The helper rejects raw leading `WITH`, renames hoisted
  stage aliases with the wrapper alias prefix, rewrites structured stage
  references, and preserves stage params before base-body params.
- 2026-06-20: Applied the helper to cross-model join-align, source-rate,
  money-attribution, and time-attribution wrappers.
- 2026-06-20: Extracted the helper from `DslCteDslRequestMapper` into
  `DslCteAssemblySupport` and added focused coverage in
  `DslCteAssemblySupportTest`.
- 2026-06-20: Focused regression passed:
  `mvn -pl foggy-dataset-model "-Dtest=DslCteAcceptanceSampleTest#generateSqlCrossModelJoinAlignHoistsStructuredBaseCteStages+generateSqlCrossModelJoinAlignRejectsRawWithBaseSql+generateSqlCrossModelTimeAttributionHoistsStructuredBaseCteStages" test`.
- 2026-06-20: Full target class passed:
  `mvn -pl foggy-dataset-model -Dtest=DslCteAcceptanceSampleTest test`.
  Surefire ran the class under default, mysql, and postgres executions; each
  reported `Tests run: 185, Failures: 0, Errors: 0`.
- 2026-06-20: Related target regression set passed:
  `mvn -pl foggy-dataset-model "-Dtest=SemanticQueryServiceV3ValidatePipelineTest,SemanticRequestNormalizerTest,DslCteAssemblySupportTest,DslCtePlanningServiceTest,DslCteAcceptanceSampleTest" test`.
  Surefire ran under default, mysql, and postgres executions; each reported
  `Tests run: 194, Failures: 0, Errors: 0`.
- 2026-06-20: Expanded target regression set passed after updating the legacy
  validate expectations:
  `mvn -pl foggy-dataset-model "-Dtest=SemanticQueryServiceV3ValidatePipelineTest,SemanticRequestNormalizerTest,DslCteAssemblySupportTest,DslCtePlanningServiceTest,DslCteAcceptanceSampleTest,SemanticServiceV3Test" test`.
  Reported `Tests run: 217, Failures: 0, Errors: 0`.
- 2026-06-20: Full module verification with
  `mvn -pl foggy-dataset-model test` is not green because of existing failures
  outside this fix scope:
  `AdvancedAnalyticsTest.testQmV2SameTableModelMultipleAliases`,
  `JavaSemanticScaleSnapshotTest.shouldProduceSemanticScaleSnapshot`,
  pre-aggregation consistency tests, and relation metric fixture baseline
  assertions. The latest full-module failure list no longer includes
  `SemanticServiceV3Test`; the new DSL_CTE acceptance coverage passed in that
  run.

## References

- `docs/model-engine-dsl-cte-tm-qm-review-20260620.md`
