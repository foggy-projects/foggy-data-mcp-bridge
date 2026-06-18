---
type: bug
bug_source: regression-found
version: 9.2.0
ticket: BUG-postgres-full-gate-regressions
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
created_at: 2026-06-18
updated_at: 2026-06-18
owner_repo: foggy-data-mcp-bridge-wt-dev-compose
owner_module: foggy-dataset-model
---

# BUG: PostgreSQL Full Gate Regressions

## Background

The 9.2.0 readiness documents previously recorded only targeted PostgreSQL aggregate-join evidence. A full prepared PostgreSQL dataset-model gate was then run to close the release evidence gap.

The initial full gate reproduced three regressions:

- aggregate relation self-alias projection pruning omitted the requested aggregate output;
- the CTE running-sum post-slice test used a threshold that is below the PostgreSQL fixture's first cumulative row, so the generated query and handwritten oracle compared an empty boundary;
- the YoY integration oracle compared an unlimited handwritten SQL result with an engine request capped at `limit=50`.

## Reproduction

Command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model surefire:test@test-postgres -Dsurefire.failIfNoSpecifiedTests=false
```

Initial result:

```text
Tests run: 3162, Failures: 2, Errors: 1, Skipped: 3
```

Observed failures:

- `AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases`
  - PostgreSQL reported `column aggregatesalesbyline.salesamount does not exist`.
  - The outer query selected `aggregateSalesByLine.salesAmount`, but the pruned RHS aggregate relation did not include the unqualified output alias `salesAmount`.
- `JdbcModelQueryEngineCteWrapTest#testRunningSumPostSliceExecutesAndMatchesHandWrittenSql`
  - PostgreSQL fixture rows begin with a cumulative sales amount above `15000`, so the test threshold produced no rows.
- `ComparativeExecutionIntegrationTest#testYoYExecutionMatchesSql`
  - The engine request was limited to 50 rows while the handwritten YoY SQL oracle returned 51 rows.

## Expected vs Actual

Expected:

- aggregate relation derived-table projection pruning keeps every aggregate output referenced by the current query, including alias-qualified references;
- PostgreSQL CTE/window tests compare a non-empty deterministic post-slice range;
- YoY parity compares the same result boundary on both engine and handwritten SQL paths.

Actual:

- alias-qualified aggregate relation output marking did not also mark the derived relation's simple output alias;
- the running-sum post-slice threshold was fixture-invalid for PostgreSQL;
- the YoY request and handwritten oracle used different row limits.

## Impact Scope

- Affects PostgreSQL full prepared-service evidence for 9.2.0.
- Runtime impact is concentrated in aggregate relation projection pruning when the selected field is qualified by relation alias, for example `aggregateSalesByLine.salesAmount`.
- The CTE/window and YoY failures are test oracle/data-boundary issues; they did not require production runtime semantic changes.

## Fix

- Added `AggregateRelationQueryObject#resetAggregateRelationProjection()` and reset required projection aliases before each query planning pass.
- When marking aggregate relation output aliases, record both the original alias and the simple suffix after the final dot. This keeps `aggregateSalesByLine.salesAmount` and the derived output alias `salesAmount` aligned.
- Adjusted the PostgreSQL running-sum post-slice fixture threshold to `150000`, which keeps a deterministic non-empty boundary.
- Removed the YoY request-side `limit=50` from monthly and quarterly parity tests so both engine and handwritten SQL compare the same full-result boundary.

## Test Strategy

Integration tests are required because the failure requires generated SQL, PostgreSQL execution, and fixture data.

Targeted recheck:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model test-compile surefire:test@test-postgres -Dtest='AdvancedAnalyticsTest#testQmV2SameTableModelMultipleAliases,JdbcModelQueryEngineCteWrapTest#testRunningSumPostSliceExecutesAndMatchesHandWrittenSql,ComparativeExecutionIntegrationTest#testYoYExecutionMatchesSql+testQuarterlyYoYExecutionMatchesSql' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Full PostgreSQL gate:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model test-compile surefire:test@test-postgres -Dsurefire.failIfNoSpecifiedTests=false
```

Result:

```text
Tests run: 3162, Failures: 0, Errors: 0, Skipped: 3
```

## Code Inventory

| Path | Change |
|---|---|
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateRelationQueryObject.java` | Adds projection-reset hook for aggregate relation query objects. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/model/AggregateJoinTableModel.java` | Clears projection requirements per query and maps qualified output aliases to simple derived aliases. |
| `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/JdbcModelQueryEngine.java` | Resets aggregate relation projection state before computing pruning requirements. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/JdbcModelQueryEngineCteWrapTest.java` | Uses a PostgreSQL-valid running-sum threshold for the fixture. |
| `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/ComparativeExecutionIntegrationTest.java` | Removes request-side YoY limits so oracle and engine boundaries match. |

## Fix Checklist

- [x] Reproduce full PostgreSQL gate failures.
- [x] Fix aggregate relation alias-qualified projection pruning.
- [x] Align running-sum post-slice fixture boundary.
- [x] Align YoY request/oracle row boundary.
- [x] Run targeted PostgreSQL recheck.
- [x] Run full PostgreSQL prepared-service gate.
- [x] Update 9.2.0 readiness and quality evidence.

## Verification

Closed on 2026-06-18 after the targeted recheck and the full PostgreSQL dataset-model gate both passed.

## References

- `docs/9.2.0/acceptance/version-readiness-snapshot.md`
- `docs/9.2.0/quality/query-model-hardening-followups-quality-and-coverage.md`
- `docs/9.2.0/workitems/query-model-aggregate-join.md`
