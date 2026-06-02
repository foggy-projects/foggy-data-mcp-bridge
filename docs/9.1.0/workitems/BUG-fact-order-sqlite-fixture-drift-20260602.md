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

# FactOrder Fixture Drift

## Summary

The Java local reactor failed after the order sales-team semantic fixture was added because the `foggy-dataset-model` SQLite integration fixture lagged behind the demo model contract. The same contract was then checked against docker demo init scripts to keep MySQL, PostgreSQL, and SQL Server demo fixtures aligned.

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
| `foggy-dataset-demo/docker/mysql/init/01-schema.sql` | Added `dim_sales_team`, `fact_order.sales_team_key`, and the sales-team key index. |
| `foggy-dataset-demo/docker/mysql/init/03-test-data.sql` | Seeded five sales teams and mapped generated plus fixed CRM fixture orders to `sales_team_key`. |
| `foggy-dataset-demo/docker/postgres/init/01-schema.sql` | Added `dim_sales_team`, `fact_order.sales_team_key`, and the sales-team key index. |
| `foggy-dataset-demo/docker/postgres/init/03-test-data.sql` | Seeded five sales teams and mapped generated plus fixed CRM fixture orders to `sales_team_key`. |
| `foggy-dataset-demo/docker/sqlserver/init/01-schema.sql` | Added `dim_sales_team`, `fact_order.sales_team_key`, and the sales-team key index. |
| `foggy-dataset-demo/docker/sqlserver/init/03-test-data.sql` | Seeded five sales teams and mapped generated plus fixed CRM fixture orders to `sales_team_key`. |
| `foggy-dataset-demo/docker/smoke-demo-init.sh` | Added optional static/live sales-team init smoke for MySQL, PostgreSQL, and SQL Server docker demo fixtures. |

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

Docker demo parity follow-up:

```bash
rg -n "dim_sales_team|sales_team_key|INSERT INTO .*fact_order" foggy-dataset-demo/docker/{mysql,postgres,sqlserver}/init/{01-schema.sql,03-test-data.sql}
```

Result: passed statically; all three docker demo init families now contain the sales-team dimension, order FK, seed rows, generated order mapping, and fixed CRM order mapping.

Fixed CRM insert arity probe:

```bash
python3 <read-only fact_order insert arity probe>
```

Result: passed; MySQL, PostgreSQL, and SQL Server fixed CRM `fact_order` inserts each have `16` columns and four rows with `16` values.

Focused model regression after docker parity patch:

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -am -P'!multi-db' '-Dtest=ModelLoadingTest#testLoadFactOrderModel+testFactOrderModelDimensions,SemanticServiceV3Test,SemanticQueryValidationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: passed, `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.

Reusable smoke entry:

```bash
cd foggy-dataset-demo/docker
./smoke-demo-init.sh --static
./smoke-demo-init.sh all
./smoke-demo-init.sh all --start --init
```

Result in the current local environment: static smoke passed. Default live smoke skipped MySQL, PostgreSQL, and SQL Server because Docker is not installed or not on `PATH`; full live DB evidence still depends on Docker database containers being available.

## Follow-up

Live docker DB init smoke for MySQL, PostgreSQL, and SQL Server remains environment-dependent, but the reusable smoke entry is now present. The current patch closes the SQLite reactor gate, docker script parity gap, and missing smoke-command gap.
