---
doc_role: test_evidence
doc_purpose: Record reproducible verification evidence for the 9.3.1 production isolation and fail-closed work item.
version: 9.3.1
target: P0-production-isolation-fail-closed
status: reviewed
recorded_by: Codex
recorded_at: 2026-07-13
---

# 9.3.1 测试证据

## 执行约束

- 所有最终 Maven 证据均串行执行，避免多个 reactor 进程竞争同一模块 `target`。
- SQLite 定向测试使用 `-P!multi-db`；外部数据库矩阵显式指定 Spring profile。
- package 成功只证明编译与装配，不替代测试通过证据。

## 定向回归

### Cache identity、provider 与真实双数据源隔离

```bash
mvn -pl addons/foggy-dataset-model-cache -am '-P!multi-db' \
  -Dtest=QueryFingerprintBuilderTest,QueryFingerprintTest,StableCanonicalEncoderTest,CaffeineQueryCacheProviderTest,CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest,RedisQueryCacheProviderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- result: 86 tests，0 failure，0 error，0 skipped；reactor 8/8 success。
- 关键证据：两个真实 SQLite 文件库分别写入 `sentinel-a`/`sentinel-b`；同一 query model、context、SQL、params 和 `DelegatingDataSource` 切换 target 后分别 miss/write/hit，切回仍只命中各自结果，最终 L2 entry 数为 2。
- 另含：namespace、requested/expected/resolved model、model instance、datasource、security policy、authorization、typed params、locale、布尔树、routing datasource 和 snapshot 隔离。

### 数据源解析、Step 顺序与 PreAgg→L2

```bash
mvn -pl foggy-dataset-model '-P!multi-db' \
  -Dtest=TableModelLoaderManagerImplDataSourceResolutionTest,DbModelAutoConfigurationTest,DataSetResultStepExecutorOrderingTest,PreAggregationL2CacheIntegrationTest test
```

- result: 27 tests，0 failure，0 error，0 skipped。
- 关键证据：named source/namespace 缺失默认拒绝、legacy 回退仅显式启用、`definition.dataSource` 优先级、重复 Step order 拒绝。

```bash
mvn -pl foggy-dataset-model -am '-P!multi-db' \
  -Dtest=PreAggregationL2CacheIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- result: 1 test，0 failure，0 error，0 skipped；reactor 7/7 success。
- 关键证据：使用生产 `QueryFacade`、`PreAggRewriteStep`、`L2CacheStep` 与真实 SQLite；首轮 PreAgg hit → L2 miss → SQL execute → L2 write，第二个新上下文 PreAgg hit → L2 hit 且不再执行 SQL；lookup/write 使用同一改写后 key。

### 测试/开发 Controller 隔离

```bash
mvn -pl foggy-dataset-mcp -am '-P!multi-db' \
  -Dtest=DevToolsControllerIsolationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- result: 5 tests，0 failure，0 error，0 skipped；reactor 9/9 success。
- 关键证据：默认 Bean 不存在且 route 404；显式开关后注册；缺失或错误类型的 named datasource 返回 400，primary datasource 未被调用。

```bash
mvn -pl foggy-dataset-model '-P!multi-db' \
  -Dtest=SemanticServiceV3TestControllerIsolationTest test
```

- result: 2 tests，0 failure，0 error，0 skipped。
- 关键证据：默认 Bean 不存在且 route 404；`foggy.test.enabled=true` 后 Bean 注册且 route 200。

## 全量与真实数据库矩阵

| Scope | Environment | Result |
|---|---|---|
| `foggy-dataset-model` full-suite baseline before the new PreAgg integration test | SQLite | 3289 tests，0 failure，0 error，3 skipped；新增 PreAgg 用例另行 1/1 定向通过 |
| `MultiDatabaseQueryTest` | MySQL 5.7, `foggy-demo-mysql` / `mysql:5.7` | 18/18 passed |
| `MultiDatabaseQueryTest` | PostgreSQL, `foggy-demo-postgres` / `postgres:15-alpine` | 18/18 passed |
| `MultiDatabaseQueryTest` | SQL Server, `foggy-demo-sqlserver` / `mcr.microsoft.com/mssql/server:2022-latest` | 18/18 passed |
| root reactor package | project reactor | 25/25 modules success |

矩阵执行形式：

```bash
mvn test -pl foggy-dataset-model '-P!multi-db' \
  -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=docker
mvn test -pl foggy-dataset-model '-P!multi-db' \
  -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=postgres
mvn test -pl foggy-dataset-model '-P!multi-db' \
  -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=sqlserver
```

## 证据边界

- 外部数据库矩阵证明真实方言和查询结果兼容；跨数据源缓存隔离由双 SQLite sentinel 集成测试直接证明，二者不混为同一类证据。
- Redis 跨 JVM generation、Surefire/Failsafe 分层、不可变 CI artifact 和发布门分别归 9.3.3/9.3.4。
- 本记录保留修复后结果和修复前代码基线描述，但没有归档不可变的“先红后绿” CI artifact；该项归 9.3.4 证据链收口。
- 当前证据足以进入正式验收，但上述后续项不得被描述为已完成。
