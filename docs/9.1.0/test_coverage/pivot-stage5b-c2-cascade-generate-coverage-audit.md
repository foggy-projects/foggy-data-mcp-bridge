---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.1.0
target: PIVOT-91-C2 Cascade Generate implementation
status: reviewed
conclusion: ready-with-gaps
reviewed_by: Codex coverage reviewer
reviewed_at: 2026-05-02
follow_up_required: yes
---

# PIVOT-91-C2 Cascade Generate Coverage Audit

## Background

This audit maps the C1.1/C2 oracle and refusal matrix to executable tests after the Phase 4 GREEN implementation. The audit focuses on semantic safety: no ambiguous cascade result should be produced without deterministic SQL semantics and accepted dialect coverage.

## Coverage Matrix

| Oracle / Refusal Case | Evidence | Result |
|---|---|---|
| Two-level rows cascade subset | `PivotCascadeGenerateSqlParityIntegrationTest.testRowsTwoLevelCascadeSubset` | covered |
| Parent ranking ignores child limit | `PivotCascadeGenerateSqlParityIntegrationTest.testParentRankingIgnoresChildLimit` | covered |
| Parent having filters before child rank | `PivotCascadeGenerateSqlParityIntegrationTest.testParentHavingFiltersBeforeChildRank` | covered |
| Child having does not affect parent rank | `PivotCascadeGenerateSqlParityIntegrationTest.testChildHavingDoesNotAffectParent` | covered |
| Missing cascade `orderBy` rejected | `PivotCascadeGenerateValidationTest.testMissingOrderByRejected` | covered |
| Deterministic tie and NULL buckets | `PivotCascadeGenerateSqlParityIntegrationTest.testDeterministicTieWithNullBuckets`, `PivotAxisDomainSqlPlannerCascadeTest`, `PivotAxisDomainSqlPlannerTest.testBasicRowLimit` | covered after gate fix |
| Additive subtotal over surviving domain | `PivotCascadeGenerateSqlParityIntegrationTest.testAdditiveRowSubtotalSurvivingDomain` | covered |
| Unsupported SQL dialect / current memory fallback refusal | `PivotAxisDomainSqlPlannerTest.testC2DialectWhitelist`, `PivotCascadeGenerateValidationTest.testMysql57CascadeFailsClosedWithoutMemoryFallback` | covered without live MySQL 5.7 profile |
| Non-additive cascade rejected | `PivotCascadeGenerateValidationTest.testNonAdditiveCascadeRejected` | covered |
| Tree mode cascade rejected | `PivotCascadeGenerateValidationTest.testTreeModeCascadeRejected` | covered |
| Three-level cascade rejected | `PivotCascadeGenerateValidationTest.testThreeLevelCascadeRejected` | covered |
| Rows cascade plus column domain operation rejected | `PivotCascadeGenerateValidationTest.testRowsCascadePlusColumnLimitRejected` | covered |
| Having-only cascade rejected | `PivotCascadeGenerateValidationTest.testHavingOnlyCascadeRejected` | covered |
| Leaf-only partitioned TopN remains non-cascade | `PivotCascadeGenerateValidationTest.testLeafOnlyLimitIsNotCascadeRequest` | covered |

## Verification Evidence

- `mvn -pl foggy-dataset-model "-Dtest=PivotAxisDomainSqlPlannerTest,PivotAxisDomainSqlPlannerCascadeTest" test` passed on 2026-05-02.
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateValidationTest test` passed on 2026-05-02 with 9 tests across the default, mysql, and postgres surefire executions.
- `mvn -pl foggy-dataset-model -Dtest=PivotCascadeGenerateSqlParityIntegrationTest test` passed on 2026-05-02.
- `mvn -pl foggy-dataset-model "-Dtest=PivotIntegrationTest#testParentChildTopN" test` passed on 2026-05-02.
- `git diff --check` passed on 2026-05-02 with only expected Windows LF-to-CRLF warnings.

## Coverage Gaps

- No dedicated live SQL Server cascade profile exists. This is acceptable for C2 v1 because SQL Server is explicitly refused.
- No dedicated live MySQL 5.7 database profile is recorded. Planner-level dialect refusal and pipeline-boundary no-memory-fallback behavior are covered without requiring a live MySQL 5.7 service.
- Non-additive leaf-output metrics beyond the accepted additive subtotal/grandTotal path remain outside C2 v1 and must continue to fail closed.
- Three-level and cross-axis cascade coverage is refusal-only, matching C2 v1 scope.

## Conclusion

`ready-with-gaps`

The implemented oracle/refusal matrix is sufficient for acceptance with risks. The open gaps are fail-closed, deferred dialect coverage, or intentionally excluded C2 v1 semantics.
