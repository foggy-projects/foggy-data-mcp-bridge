---
type: bug
bug_source: user-report
version: 9.2.12
ticket: BUG-query-access-dimension-filter-join
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# BUG Work Item

## Document Purpose

- doc_type: bug
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track Issue 12 regression coverage and fix for Query Engine access filters on dimension fields.

## Background

GitLab issue 12 reports that a QM `access` script using dimension-path field references can produce invalid SQL or self-comparison style predicates when the query model does not explicitly join the dimension table.

The original TMS workaround replaced the field-reference DSL with raw SQL / `EXISTS` in `TerminalTransitHandoffCandidateQuery.qm`. The engine still needs direct regression coverage so the workaround is not the only protection.

## Reproduction

1. Build a QM over `FactOrderO615StockItemProbeModel`.
2. Do not declare an explicit join to `stockHouse`.
3. Add access filters through field-reference DSL:
   - `context.query.and(si.stockHouse$useType, '直营')`
   - `context.query.andIn(si.stockHouse$status, ['ACTIVE'])`
   - `context.query.getWhere().and(si.stockHouse$status, 'ACTIVE')`
4. Build and execute the query.

Before the fix, dimension columns could be resolved into WHERE predicates without registering the required dimension join on the current `JdbcFrom`.

## Expected vs Actual

Expected:

- Dimension field filters in access should trigger the same join dependency resolution as normal slice filters.
- Generated SQL should include `left join dim_store`.
- WHERE predicates should use the physical dimension columns, for example `d1.store_type = ?` and `d1.status in (?)`.
- The query should execute against real fixture data.

Actual before fix:

- The access field-reference path resolved the dimension column but did not add the dimension table to the query `from` joins.
- SQL could reference an alias that was not present in the FROM clause, or degrade into invalid/self-comparison style SQL in the original TMS scenario.

## Impact Scope

- `foggy-dataset-model` query engine.
- QM `access` scripts that call `context.query.and(...)`, `andIn(...)`, `andNe(...)`, `andNull(...)`, `andNotNull(...)`, or nested `context.query.getWhere().and(fieldRef, value)` with dimension-path field references.
- TMS X3 query models that use access filters over dimension fields, especially terminal transit handoff candidate queries.

## Test Strategy

Automation is required because the issue is stable, engine-level, and regression-prone.

Primary regression:

- `AggregateJoinQueryModelTest#issue12AccessDimensionFilterShouldNotRenderSelfComparison`

Adjacent regression guard:

- O615 aggregate relation join-path tests that already protect related dimension path and join graph behavior.

## Code Inventory

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query/JdbcQuery.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/AggregateJoinQueryModelTest.java`
- `foggy-dataset-model/src/test/resources/foggy/templates/ecommerce/query/OrderStationStockProjectionIssue12AccessDimensionProbeQueryModel.qm`

## Root Cause

`JdbcQuery` field-reference helper methods resolved a `ColumnRef` to `DbColumn`, then rendered `alias.column = ?` into WHERE. For dimension fields, that `DbColumn` belongs to a non-root `QueryObject`, but the access DSL path did not ask `JdbcFrom` to join that object.

Normal request slice handling already registered joins for non-root fields in `JdbcModelQueryEngine`, so the bug was specific to access scripts that directly used `JdbcQuery` field-reference APIs.

## Fix Checklist

- [x] Add an Issue 12 QM probe that filters a dimension path from access without explicit joins.
- [x] Add an integration regression that verifies generated SQL and executes real fixture data.
- [x] Register the resolved condition column's `QueryObject` through `from.join(...)`.
- [x] Apply the same join registration to `and`, `andIn`, `andNe`, `andNull`, `andNotNull`, and nested `getWhere().and(fieldRef, value)`.
- [x] Reuse existing `JoinGraph` / `JdbcFrom` behavior for path resolution and join de-duplication.
- [x] Keep SQL visitor, QM loading, and aggregate relation behavior unchanged.

## Progress Tracking

- development: complete.
- testing: complete for targeted Issue 12 and adjacent O615 regressions.
- experience: N/A, backend query engine SQL generation and fixture execution only.
- quality: post-fix implementation quality review recorded under `docs/9.2.12/quality/BUG-query-access-dimension-filter-join-fix-quality-review.md`.
- acceptance readiness: ready for reviewer verification.

## Implementation Check-In

- completed work: regression probe, failing reproduction, engine fix, adjacent regression verification, documentation writeback.
- changed code paths: `JdbcQuery.java`, `AggregateJoinQueryModelTest.java`, Issue 12 probe QM.
- self-check summary: fix is limited to condition field-reference join registration and delegates join path details to existing `from.join(...)`.
- non-goals: no TMS QM workaround removal, no SQL visitor refactor, no QueryModel loader contract change.
- self-check decision: formal post-fix quality review recorded because this is a core query engine path.

## Verification

- `mvn -pl foggy-dataset-model "-Dtest=AggregateJoinQueryModelTest#issue12AccessDimensionFilterShouldNotRenderSelfComparison" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test` - passed, 1 test.
- `mvn -pl foggy-dataset-model "-Dtest=AggregateJoinQueryModelTest#issue12AccessDimensionFilterShouldNotRenderSelfComparison+aggregateRelationO615ProbeRhsDimensionFilterShouldResolveJoinPath+aggregateRelationO615ProbeRhsJoinDimensionFilterShouldResolveJoinPath+aggregateRelationO615ProbeNoColumnsWithAccessShouldResolveJoinPath+aggregateRelationO615ProbeExpressJoinNoColumnsShouldResolveJoinPath" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test` - passed, 5 tests.
- `git diff --check` - passed with CRLF conversion warnings only.

## TMS Workaround Evaluation

Do not remove the current TMS raw SQL / `EXISTS` workaround in the same change. Let the engine regression land first, then evaluate a separate TMS QM simplification after the fixed engine version is available to that workspace.

## References

- GitLab issue: `http://gitlib.foggysource.com/domain/tms-x6/tms-x3/-/issues/12`
- TMS workaround file observed earlier: `query-cloud-service/src/main/resources/foggy/templates/TerminalTransitHandoffCandidateQuery.qm`
