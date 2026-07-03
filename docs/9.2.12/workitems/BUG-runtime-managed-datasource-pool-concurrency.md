---
type: bug
bug_source: acceptance-found
version: 9.2.12
ticket: BUG-runtime-managed-datasource-pool-concurrency
severity: major
status: ready-for-verification
reproduction_status: confirmed
test_strategy: unit-test
automation_decision: required
owner: runtime-api
---

# BUG Work Item

## Background

Review of the runtime-managed datasource pool found that `ManagedDataSourcePoolManager`
could close a pool while a connection borrow was already in progress but not yet counted
as active.

## Reproduction

1. Resolve a runtime-managed datasource.
2. Let the pool become idle beyond `idlePoolCloseMinutes`.
3. Start `DataSource.getConnection()`.
4. Trigger idle cleanup before the borrow path increments the manager active counter.

## Expected vs Actual

Expected: cleanup observes the in-flight borrow as active and leaves the pool open.

Actual: cleanup can observe zero active connections and close the pool during borrow.

## Impact Scope

- Runtime API-managed datasources.
- Namespace default datasource resolution that uses runtime-managed datasources.
- Semantic model loads and query execution that borrow connections through the runtime pool.

## Test Strategy

Add unit regression coverage for:

- cleanup during in-flight connection borrow;
- concurrent `DataSource.getConnection()` after an idle close;
- diagnostics after idle close.

## Code Inventory

- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManager.java`
- `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/ManagedDataSourcePoolManagerTest.java`

## Fix Checklist

- [x] Count in-flight borrow before delegating to the underlying pool.
- [x] Keep slot state transitions synchronized consistently.
- [x] Keep idle cleanup from closing pools that have in-flight borrows.
- [x] Make `poolClosed` diagnostics represent idle-closed pools correctly.
- [x] Include all datasource pool settings in the config fingerprint or document exclusions.

## Verification

- `mvn -pl foggy-runtime-api -Dtest=ManagedDataSourcePoolManagerTest test` - passed, 12 tests.
- `mvn -pl foggy-runtime-api test` - passed, 60 tests.
- `mvn -pl foggy-dataset-model -Dtest=TableModelLoaderManagerImplDataSourceResolutionTest test` - passed, 4 tests in each configured surefire execution.
- `PYTHONPATH=src python -m pytest` in `foggy-runtime-cli` - passed, 87 tests.
- `git diff --check` in `foggy-data-mcp-bridge` - passed with CRLF conversion warnings only.

## References

- Review finding: runtime-managed datasource pool borrow/cleanup race.
