---
type: bug
bug_source: acceptance-found
version: 9.3.4
ticket: BUG-934-STEP3-DATABASE-CONTRACT-GAPS
severity: critical
status: closed
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
- [x] make the run-local candidate verify container image ID and SQLite JAR byte SHA
- [x] replace required assumption/early-return paths with positive/refusal assertions
- [x] repair Pivot pre-aggregation physical-column mapping and execute its SQL/oracle
- [x] provide homogeneous five-database pre-aggregation schema/data fixtures
- [x] implement exact 29-execution database runner/report/cell verifier and 14 report negatives
- [x] extend the Step 3 authority to all 45 required executions and DB-state negatives
- [x] execute all required lanes with `Failures=0, Errors=0, Skipped=0`

## 2026-07-15 Pivot PreAgg Diagnostic Check-in

The Pivot pre-aggregation sub-gap is repaired but the parent bug remains `in-progress`:

- isolated V934 preagg schema/data now exists for all five databases;
- `PivotSqlParityIT#testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder` executes the
  rewritten relation, native grouped oracle and final TopN wrapper on SQLite, MySQL 5.7,
  MySQL 8, PostgreSQL 15 and SQL Server 2022; result is `5/F0/E0/S0`;
- matcher/rewriter/final-stage no longer infer materialized caption/id/time/property columns;
  hybrid semantic watermark also requires an explicit materialized column contract;
- final regression roots and source digests are frozen in
  `step3-pivot-preagg-method-diagnostic-20260715.md` and its SHA manifest.

This check-in is method-level diagnostic evidence on fixed demo containers. It does not satisfy
the final fresh-storage runner, exact 46-execution collector, remaining assumption/early-return
cleanup, required external matrix, or negative-probe checklist. The DDL/refresh Addon has a
separate confirmed lifecycle gap tracked by
`BUG-step3-preagg-addon-materialization-contract.md`.

## 2026-07-15 Required Database S0 Diagnostic Check-in

数据库侧 frozen selector 的 false-green 路径已清除：

- Pivot/cascade 不再使用 assumption；支持能力执行正向 request，不支持能力精确断言
  typed/dialect refusal；
- MultiDatabase 不再以空 fixture、日志或无断言 return 通过；确定性数据断言覆盖分页、
  aggregate、join、subquery、null、CTE/window/LAG/moving average；
- 五个标准 lane 加 MySQL 8/PostgreSQL 定向 variant 的最终 raw XML 精确为
  `29 reports / 370 tests / F0/E0/S0`；
- 外库 test-owned fixture 在诊断后全部清理为
  `sentinel_rows=0 fixture_tables=0`。

证据见 `step3-db-required-s0-diagnostic-20260715.md`。该结果仍来自 fixed demo
containers，`inventory_consumption=0`；fresh/run-scoped provision、exact runner/collector、
required external matrix 与全套 negative probes 仍未完成，所以本 BUG 保持
`in-progress`，`execute all required lanes` 也不得提前勾选。

## 2026-07-15 Run-local Database Runner Candidate Check-in

`scripts/verify-v934-database-matrix.sh` 现已串联 fresh/run-scoped storage、五库 exact
identity、canonical fixture、29-report collector、5-cell cleanup evidence、final bundle
重验和 sensitive scan。最终 contract 精确为
`5 cells / 7 variants / 29 reports / 370 testcase`，66 个 authority input 和 8 棵
protected source tree 均 fail closed。

最终冻结字节下，真实 physical-file SQLite run
`sqlite-collector-candidate-r3`=`5 reports / 50 tests / F0/E0/S0`；JDBC JAR 与 fixture
before/after hash 一致，SQLite 文件残留为 0。完整 runner 在本机因长期 MySQL57 容器
占用冻结端口而于 preflight 返回 `E_PORT_OWNED`，durable status=`failed/1`、summary
absent、新建资源为 0，未复用或停止外部容器。

14/14 negative 仅覆盖 report/manifest/final-bundle exact-tree 契约。unavailable、wrong
kind/major/catalog/schema/sentinel、fixture mutation、provision cleanup/signal 等数据库
状态负向仍未完成；16 个 required external execution 也未消费。因此只勾选 29 个
database execution 的 runner/collector 实现，不勾选 full 45 required 或 all-lane
execution。证据见
`docs/9.3.4/evidence/step-3/step3-database-matrix-runner-candidate-20260715.md`。

另有一项下游 high：当前 final bundle 依赖原 run 的 absolute SQLite coordinate 与
nanosecond mtime，只能 run-local 重验；Step 5 archive 前必须完成 portable evidence
contract，当前 candidate 不得直接作为可下载 immutable authority。

## 2026-07-16 Formal MySQL Initialization Readiness Regression

首次同提交正式父运行 `step3-required-20260716-final-r1` 在数据库动态负向
`fixture-mutation` 处被正确拒绝。run-owned MySQL 5.7 容器已被 healthcheck 标记为
healthy，但官方 entrypoint 尚处于临时初始化服务器阶段，`foggy` 业务用户查询返回
`ERROR 1045`。因此目标探针没有到达 `E_FIXTURE_MUTATION`，状态工具记录
`actual_error=E_MISSING_ERROR_CODE` 并使父候选缺席；本次容器、卷、网络残留均为零。

provisioner 现于 health gate 后增加有界业务 readiness：必须使用冻结的 `foggy`
credential 查询 `foggy_test` 成功，才允许进入 identity、fixture 与 callback 阶段。
该修复同时适用于 MySQL 5.7/MySQL 8；动态状态套件本身即为回归测试，因为旧实现会在
目标 probe 之前失败，而修复后必须继续到每个约定错误码。该项在新的 clean committed
HEAD 完成 `18/18` 状态负向与正式父矩阵前保持 `in-progress`。

第二次正式父运行 `step3-required-20260716-final-r2` 证明仅等待业务用户查询仍不充分：
该用户在最后一个 init script 之前已可登录，随后 V934 fixture 访问尚未创建的
`fact_sales`，虽然 cleanup probe 本身返回预期 `E_CLEANUP_FORCED`，但 fixture hashes
为空，manifest verifier 因此以 `E_MANIFEST` 拒绝候选。readiness 随即加强为双条件：
业务 identity 成功，且最后初始化脚本写入的四条 `preagg_watermark` marker 全部存在。
r2 同样只作为 excluded diagnostic；零残留与原 demo 容器恢复状态需在最终证据中单列。

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

## 2026-07-16 Formal Closure

同一 committed HEAD `ce3d70c391c7b8bd8046fe66dde0ad568d66601e` 的父运行
`step3-required-20260716-final-r4` 已用 fresh/run-scoped storage 完成五库：SQLite
`5/50`、MySQL 5.7 `5/50`、MySQL 8 `6/105`、PostgreSQL 15 `8/115`、SQL Server
2022 `5/50`，合计 `29 reports / 370 testcase / F0E0S0`。数据库状态负向=`18/18`、
report/final-bundle 负向=`14/14`，fixture/source before=after，run-owned resource residue
为零。

同一父 authority 随后消费 required external `16/76`，exact union=`45/446`，
gap/overlap/extra=`0/0/0`；optional LLM 以 reviewed exclusion 单列。正式证据见
`docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`。r1/r2/r3 与其他
diagnostic 均被排除，未参与绿色拼接。本 BUG 的 Step 3 database-contract scope 关闭。

## References

- `docs/9.3.4/implementation-plan.md`, Step 3
- `docs/9.3.4/test/test-ci-evidence-chain-test-plan.md`, Step 3
- `scripts/v934/database-contract.tsv`
- `scripts/v934/successor/step2/deferred-step3.tsv`
