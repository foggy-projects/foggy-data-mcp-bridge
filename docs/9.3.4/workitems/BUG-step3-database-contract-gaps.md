---
type: bug
bug_source: acceptance-found
version: 9.3.4
ticket: BUG-934-STEP3-DATABASE-CONTRACT-GAPS
severity: critical
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# Step 3 五数据库契约未闭合

## Background

9.3.4 Step 3 要求 SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server
2022 五库全部完成真实 identity、同构 sentinel、QueryFacade/native parity 与
required skip=0 验证。进入 Step 3 后的首个 MySQL 8 诊断执行确认：容器、端口、镜像
和 JDBC 连接均正确，但测试契约仍只支持 9.3.3 的三库范围。

## Reproduction

前置条件：`foggy-demo-mysql8` healthy，镜像 ID 为冻结的
`sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21`。

```bash
mvn -q -P\!multi-db,\!model-lifecycle,\!query-cache-real-query \
  -pl foggy-dataset-model -am \
  -Dit.test=com.foggyframework.dataset.db.model.lifecycle.gate.RequiredDatabasePreflightIT \
  -Dspring.profiles.active=mysql8 \
  -Dv933.expectedDatabase=mysql8 \
  -DskipUnitTests=true -DskipITs=false \
  -Dfailsafe.failIfNoTests=false \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

稳定结果：Failsafe 运行 `1` 个 testcase，`Failures=1, Errors=0, Skipped=0`，失败原因为
`Unsupported v933.expectedDatabase: mysql8`。

## Expected vs Actual

- expected：五库均由同一 fail-closed preflight 识别正确 product/version、driver-aware
  physical coordinate、catalog/schema 和 `v934_test_sentinel`；错误库或错误坐标必须失败。
- actual：preflight 与 QueryFacade parity 只识别 SQLite/MySQL 5.7/PostgreSQL 15；
  SQL Server JDBC URL 仍走通用 URI 解析；sentinel 只有冻结声明、没有真实 fixture；
  required Pivot/MultiDatabase suites 仍存在 assumption/early-return 伪绿色路径。

## Impact Scope

- `database-contract-matrix` 的 29 个 required execution 无法形成五库 S0 authority。
- Step 2 deferred 中 45 个 required execution 尚未清零，Step 3 不能 exit；另有
  1 个 optional LLM execution 需要 reviewed disposition。
- 若只检查容器 health 或 Maven 退出码，会把错误数据库、旧持久卷或 skipped capability
  误判为绿色。

## Test Strategy

使用 Failsafe integration test 和版本化 Step 3 report verifier：

1. 为五库执行同一 preflight/QueryFacade parity，并精确校验 raw XML、testcase 与 S0。
2. SQL Server 使用分号属性感知的 JDBC coordinate 解析和 `TOP`/`OFFSET FETCH` oracle。
3. 不支持能力以明确 refusal assertion 通过，不使用 assumption 或无断言 return。
4. runner 增加 unavailable、wrong kind/major/catalog/schema/sentinel、fixture mutation、
   missing/stale XML 负向探针。

## Code Inventory

- `foggy-dataset-model/src/test/java/**/RequiredDatabasePreflightIT.java`
- `foggy-dataset-model/src/test/java/**/RequiredDatabaseQueryFacadeParityIT.java`
- `foggy-dataset-model/src/test/java/**/MultiDatabaseQueryTest.java`
- `foggy-dataset-model/src/test/java/**/PivotSqlParityIT.java`
- `foggy-dataset-model/src/test/java/**/PivotCascadeGenerateSqlParityIT.java`
- `foggy-dataset-model/src/test/resources/application-{sqlite,v934-sqlite,docker,mysql8,postgres,sqlserver}.yml`
- `foggy-dataset-demo/docker/docker-compose-v934.yml`
- `foggy-dataset-demo/docker/v934/**`
- `scripts/v934/step3/manage-database-foundation-fixtures.sh`
- `scripts/v934/step3/verify-v933-real-query-compat.sh`
- `scripts/verify-v934-database-matrix.sh`
- `scripts/v934/step3/**`

## Fix Checklist

- [x] add a v934-only compose override pinned to the frozen OCI image IDs/digests
- [x] add idempotent, homogeneous five-database sentinel fixtures
- [x] isolate v934 fixtures/profile from the signed-off v933 init contract
- [x] extend preflight to MySQL 8 and SQL Server with exact JDBC metadata/coordinate checks
- [x] extend QueryFacade/native parity to MySQL 8 and SQL Server
- [x] replay v933-only preflight and full batch6 real-query without changing frozen counts
- [ ] make the final runner verify container image ID and SQLite JAR byte SHA
- [ ] replace required assumption/early-return paths with positive/refusal assertions
- [ ] repair Pivot pre-aggregation physical-column mapping and execute its SQL/oracle
- [ ] provide homogeneous five-database pre-aggregation schema/data fixtures
- [ ] implement exact 46-execution Step 3 runner/report verifier and negative probes
- [ ] execute all required lanes with `Failures=0, Errors=0, Skipped=0`

## Verification

The item can move to `ready-for-verification` only when the Step 3 authority summary proves:

- exact five DB kinds and 29 DB execution keys;
- exact required external execution set and total deferred gap `0`;
- product/version/coordinate/catalog/schema/image-or-artifact identity matches;
- sentinel and protected fixture before/after hashes match;
- raw Failsafe totals have `Failures=0, Errors=0, Skipped=0`;
- all versioned negative probes reject their injected failure.

The runner must use fresh/run-scoped database storage. The long-lived demo volumes were used only
for diagnostics and cannot prove fixture isolation or sequence identity.

## References

- `docs/9.3.4/implementation-plan.md`, Step 3
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`, Step 3
- `scripts/v934/database-contract.tsv`
- `scripts/v934/successor/step2/deferred-step3.tsv`
