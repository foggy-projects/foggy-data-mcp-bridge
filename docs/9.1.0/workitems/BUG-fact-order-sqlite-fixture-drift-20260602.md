---
type: bug
bug_source: regression-found
version: 9.1.0
ticket: BUG-fact-order-sqlite-fixture-drift-20260602
severity: minor
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: covered-by-existing-integration-tests
owner: java-engine
created_at: 2026-06-02
updated_at: 2026-06-02
---

# FactOrder SQLite Fixture Drift

## Summary

The Java local reactor failed after the order sales-team semantic fixture was added because the `foggy-dataset-model` SQLite integration fixture lagged behind the demo model contract.

`foggy-dataset-demo` exposes `FactOrderModel.salesTeam` and `FactOrderModel.shipDate`, but the SQLite test schema/data used by `foggy-dataset-model` did not include the backing sales-team dimension table, order foreign key, or ship-date column.

## Failure

Canonical reactor command:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dsurefire.failIfNoSpecifiedTests=false test
```

Failure before fix:

```text
QM [FactOrderQueryModel] loadTableModel('FactOrderModel'): 表模型 'FactOrderModel' 加载失败 (namespace=default): 表 'dim_sales_team' 在数据源中不存在或无列信息（schema=default）
```

Observed result before fix: `Tests run: 3019, Failures: 15, Errors: 105, Skipped: 1`.

## Fix

| Path | Change |
|---|---|
| `foggy-dataset-model/src/test/resources/sqlite/01-schema.sql` | Added `dim_sales_team`, `sales_team_key`, `ship_date`, and sales-team indexes. |
| `foggy-dataset-model/src/test/resources/sqlite/03-test-data.sql` | Added five sales-team rows, assigned orders to teams, and filled `ship_date` where the order lifecycle has shipment evidence. |

This keeps the SQLite integration fixture aligned with `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/FactOrderModel.tm` and `FactOrderQueryModel.qm`.

## Verification

Focused regression:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' '-Dtest=ModelLoadingTest#testLoadFactOrderModel+testFactOrderModelDimensions,AutoGroupByIntegrationTest,HavingClauseIntegrationTest,SemanticQueryValidationTest,SemanticServiceV3Test,DslCteCrmFunnelFixtureIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: passed, `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`.

Full local reactor:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: passed, `Tests run: 3019, Failures: 0, Errors: 0, Skipped: 1`.

## Follow-up

Check and align `foggy-dataset-demo/docker/{mysql,postgres,sqlserver}/init` for the same `salesTeam` model contract. The current patch closes the SQLite reactor gate; docker demo parity remains the next narrow hardening slice.
