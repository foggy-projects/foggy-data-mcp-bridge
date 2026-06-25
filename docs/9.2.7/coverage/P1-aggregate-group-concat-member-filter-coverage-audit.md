---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.2.7
target: P1-aggregate-group-concat-member-filter
status: reviewed
conclusion: ready
reviewed_by: Codex
reviewed_at: 2026-06-25
follow_up_required: no
---

# Test Coverage Audit

## Background

This audit covers the `GROUP_CONCAT` aggregate relation alias member filter feature. The expected behavior is that callers can filter an aggregate alias such as `linkmanTelList = '18911897366'` or `paymentMethodList in [...]` without knowing the right-side source field, while the engine rewrites the condition to a source-member predicate and keeps the selected aggregate list complete.

## Audit Basis

- Requirement and implementation plan: `docs/9.2.7/workitems/P1-aggregate-group-concat-member-filter.md`
- Implementation scope: `foggy-dataset-model` aggregate member planner, `JdbcModelQueryEngine`, aggregate relation SQL rendering, diagnostics.
- Test files:
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationDiagnosticContractTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/plan/ColumnObjectNormalizerF5Test.java`
- Test fixtures:
  - `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/model/FactSalesStringAggModel.tm`
  - `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/query/OrderSalesAggregateRelationStringAggQueryModel.qm`
- Execution evidence:
  - `mvn -pl foggy-dataset-model -DskipTests compile`
  - `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureShouldRenderAndFilterByLike+aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists+aggregateRelationGroupConcatEqualsInsideOrShouldStayOuterOnly+aggregateRelationGroupConcatEqualsInsideNestedAndUnderOrShouldStayOuterOnly" test`
  - `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists+aggregateRelationGroupConcatNegativeOpsShouldNotRewriteToMemberExists+aggregateMemberFilterRewriteStepShouldPlanBeforeEngineFallback+aggregateRelationGroupConcatMemberFilterShouldWorkThroughQueryFacade" test`
  - `mvn -pl foggy-dataset-model "-Dspring.profiles.active=sqlite" "-Dtest=AggregateJoinQueryModelTest,ColumnObjectNormalizerF5Test,AggregateRelationDiagnosticContractTest" test`
  - `mvn -pl foggy-dataset-model "-P!multi-db" "-Dspring.profiles.active=docker" "-Dtest=AggregateJoinQueryModelTest#aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists+aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists" test`
  - `mvn test`

## Coverage Matrix

| Requirement / Acceptance Item | Risk | Existing Validation Layer | Evidence | Coverage |
|---|---|---|---|---|
| QM authors can use `groupConcat(sourceField, alias)` and expose the alias as a string aggregate output. | major | integration-test, unit-test | `aggregateRelationGroupConcatMeasureShouldRenderAndFilterByLike`; `ColumnObjectNormalizerF5Test$HappyPath` group_concat cases | covered |
| Alias `=` is rewritten to a source-member correlated `EXISTS`, not aggregate-string equality. | critical | integration-test | `aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists` validates SQL shape, parameters, real execution, and absence of outer alias equality | covered |
| Alias `in` is rewritten to a parameterized source-member `EXISTS`. | critical | integration-test | `aggregateRelationGroupConcatMeasureInShouldRewriteToMemberExists` validates SQL shape, parameters, real execution | covered |
| Selected `GROUP_CONCAT` output remains the full list, not only the matched member. | critical | integration-test | `aggregateRelationGroupConcatMeasureEqualsShouldRewriteToMemberExists` asserts the multi-line order still returns the full aggregate list | covered |
| Existing `like` aggregate-string behavior remains unchanged. | major | integration-test | `aggregateRelationGroupConcatMeasureShouldRenderAndFilterByLike` validates RHS HAVING plus outer WHERE behavior and real execution | covered |
| OR contexts do not receive member rewrite or RHS pushdown that changes boolean semantics. | critical | integration-test | `aggregateRelationGroupConcatEqualsInsideOrShouldStayOuterOnly`; `aggregateRelationGroupConcatEqualsInsideNestedAndUnderOrShouldStayOuterOnly` | covered |
| Diagnostics expose rewritten / retained decisions. | major | integration-test, unit-test | rewritten assertions in `=` / `in` tests; retained assertions in OR tests; `AggregateRelationDiagnosticContractTest` | covered |
| Unsupported negative operators do not silently become member predicates. | major | integration-test | `aggregateRelationGroupConcatNegativeOpsShouldNotRewriteToMemberExists` validates `!=` and `not in` stay on aggregate-string semantics and do not render member `EXISTS` | covered |
| before-query pipeline classifier records the semantic plan in context before SQL generation. | major | integration-test | `aggregateMemberFilterRewriteStepShouldPlanBeforeEngineFallback` asserts `AggregateMemberFilterRewriteStep` writes the context plan; `aggregateRelationGroupConcatMemberFilterShouldWorkThroughQueryFacade` validates the public query facade lifecycle | covered |
| Dialect-sensitive string aggregation rendering remains valid across supported dialects. | major | integration-test | Root `mvn test` executed `foggy-dataset-model` default/sqlite, `test-mysql`/docker, and `test-postgres`; `AggregateJoinQueryModelTest` passed 61 tests in each execution | covered |

## Evidence Summary

- Focused feature tests: 5 tests passed, 0 failures, 0 errors.
- Gap-closure focused tests: 5 selected tests passed, 0 failures, 0 errors, including negative operators, pipeline context planning, and QueryFacade lifecycle.
- Focused MySQL member-filter regression passed: 2 tests, 0 failures, 0 errors.
- Broader aggregate relation regression command completed with Maven build success.
- Root full Maven regression passed: 25-module reactor build success.
- `foggy-dataset-model` multi-db executions passed for the aggregate join suite:
  - default/sqlite: `AggregateJoinQueryModelTest` 61 tests, 0 failures, 0 errors.
  - `test-mysql`/docker: `AggregateJoinQueryModelTest` 61 tests, 0 failures, 0 errors.
  - `test-postgres`: `AggregateJoinQueryModelTest` 61 tests, 0 failures, 0 errors.
- The broader regression run covered:
  - `AggregateJoinQueryModelTest`: 58 tests, 0 failures, 0 errors.
  - `AggregateRelationDiagnosticContractTest`: 3 tests, 0 failures, 0 errors.
  - `ColumnObjectNormalizerF5Test` nested classes: 16 tests, 0 failures, 0 errors.
- `git diff --check` passed; only line-ending warnings were reported by Git.

## Residual Non-Blocking Notes

No remaining test coverage gap is known for the scoped `GROUP_CONCAT` aggregate relation member-filter behavior after focused regressions and root full Maven verification.

## Recommended Next Skills

- `foggy-acceptance-signoff`: appropriate if this feature needs a formal acceptance record after full Maven verification.

## Conclusion

Coverage is sufficient for the core requirement and main regression risks. The previously identified negative-operator, QueryFacade lifecycle, and cross-dialect execution gaps are covered by focused integration tests and root full Maven verification.
