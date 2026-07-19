---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-UNIT-MYSQL57-KNOWN-CONSUMER-UNDERSTATEMENT
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: fail-closed-jdbc-oracle-and-schema2-consumer-contract
automation_decision: required
owner: step4-coverage-authority
recorded_at: 2026-07-19
---

# Step 4 Unit MySQL 已知消费者被低记为 6/11

## Background

diagnostic r7 在无 ambient MySQL listener 时观察到 `6 reports / 11 testcase errors`。后续
Unit fixture contract schema 1 将这组历史失败观测写成
`known_hidden_dependency_reports=6 / known_hidden_dependency_testcase_nodes=11`。契约同时明确
6/11 不是“其他 Unit 测试无 DB 访问”的证明，但机器集合仍只列出了这 6 个 execution key。

2026-07-19 在 r25 完整通过后的 follow-up consumer audit 中确认：
`DatasetJdbcUtilsTest#getOrCreateDataSource` 也会从 Spring datasource 建立真实 JDBC connection
并执行 `SELECT 1`。旧测试捕获 `SQLException` 后仅调用 `printStackTrace()`，所以无 listener 时
可以继续以绿色 testcase 结束，未进入 r7 的 Maven error 集合。

## Expected vs Actual

- Expected：连接建立或 `SELECT 1` 失败必须让 testcase 失败，禁止 catch-and-log 伪绿。
- Expected：机器契约区分不可改写的 r7 historical observation `6/11` 与当前 reviewed
  known-consumer lower bound `7/12`。
- Expected：发现新 consumer 后必须更新 contract/validator/hash closure，并从 new Cdiag
  启动 fresh diagnostic/formal authority chain。
- Actual：旧测试吞掉 `SQLException`；schema 1 把 r7 error set 当作 6 个 known consumers 的
  完整机器集合，使 public-valid r25 仍只验证低估后的契约。

第 7 个 execution identity 与 node：

```text
v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest	1
```

因此当前 reviewed lower bound 为 `7 reports / 12 testcase nodes`。这仍是 lower bound，不是
其余 Unit suite 绝无数据库访问的证明。

## Root Cause

根因由两个独立 fail-open 组合而成：

1. `DatasetJdbcUtilsTest` 的连接探针以 catch/printStackTrace 吞掉 `SQLException`，数据库不可用
   不会改变 JUnit 结果；
2. schema 1 的 known-consumer inventory 从 r7 Maven errors 反推，未把“历史实际报错集合”与
   “静态/动态审计确认的消费者集合”建模为不同字段。

r25 的 all-lane 执行、source seal、cleanup 和 public validation 均真实有效；它不属于运行器
伪造。但其前置机器契约不完整，所以 r25 只能是 pre-remediation diagnostic，不能成为 threshold
candidate 或 Cfreeze parent evidence。

## Remediation

1. 将 `getOrCreateDataSource` 改为声明 `throws SQLException`；使用 `assertSame` 保留 datasource
   cache identity 语义。
2. 以 try-with-resources 同时管理 `Connection`、`PreparedStatement` 和 `ResultSet`，执行
   `SELECT 1`，精确断言列数、唯一结果行和值；删除 catch/printStackTrace，不使用
   `assertDoesNotThrow`。
3. 将 fixture contract 升为 schema 2，分别记录：
   - immutable `historical_observed_failure=6 reports / 11 nodes`；
   - reviewed `known_database_consumers.reports_minimum=7` / `testcase_nodes_minimum=12`；
   - 7 个 exact execution keys 与逐 key node count。
4. 更新 fixture validator 与 expected-negative probes，目标为 `42/42`；同步 Step 4 exact
   manifest、Step4→Step6 hash closure 与必要 successor/runtime binding。
5. 完成 focused RED/GREEN、机器 contract/negative/overlay 验证和 pre-Cdiag quality 后，只创建
   一个 new Cdiag；从该 clean/pushed commit 运行 fresh r26，不复用 r25 XML/exec。
6. 仅 r26 public-valid 且 7/12 contract exact 后进入 candidate/capsule/双审→direct-child
   Cfreeze→fresh formal-r8→final quality/audit/acceptance。

## Evidence and Decision

- r25 run=`step4-coverage-20260719-diagnostic-r25`；tested commit=
  `5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7`；public observation=
  `01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a`；
- r25 required=`773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、cleanup=
  `0/0/0`、wrapper runner/restore/outcome=`0/0/0`；
- local pre-Cdiag positive observation：同一 disposable MySQL 随机端口，Maven rc=`0`，XML=
  `1/F0E0S0`，XML SHA-256=
  `7eea8fbe876c27e7600bf6b71851e95704db9209e5da7a1d6b11a458d332ad01`；
- local wrong-password observation：Maven rc=`1`，XML=
  `tests=1 / errors=1 / failures=0 / skipped=0`，XML SHA-256=
  `71aeab932af1cc0beb2228eaab93199c7b990d1fd33ff2a47b4680e78a2d6454`；一次性
  container 已自动删除，四个 demo DB original ID 均保持 exact healthy；
- r25 decision=`pre-remediation / superseded / non-candidate`；
- r7 historical `6/11` 不修改、不重新解释成 7/12；7/12 来自后续 consumer audit；
- 上述 positive XML 随 deliberate negative 被覆盖，未形成 portable standalone receipt；两项只作
  local observation，必须在 new Cdiag 后以 isolated durable receipt 重跑；
- machine validation 与 pre-Cdiag quality 已 PASS；本 BUG 保持 `in-progress`，直到 new Cdiag、
  isolated proof 与 r26 建立；
- Step 5、Cfreeze、formal-r8、9.3.5 和 9.4.0 当前均未授权。

## Verification Checklist

- [x] 确认第 7 个 execution key / 第 12 个 node 的真实 JDBC 行为。
- [x] 确认 catch/printStackTrace 可在 `SQLException` 时产生假绿。
- [x] 保留 r7 `6/11` 为 immutable historical observation。
- [x] 将 r25 标记为 superseded/non-candidate，不生成 candidate/capsule。
- [x] 完成 fail-closed `SELECT 1` test oracle 并取得 local positive/wrong-password observations。
- [x] schema 2 精确表达 historical `6/11` 与 known lower bound `7/12`。
- [x] fixture negatives=`42/42`，全部 manifest/hash/overlay/CI validators PASS。
- [x] pre-Cdiag implementation-quality review PASS，mandatory fixes=0。
- [ ] new Cdiag 后保存 isolated 双 XML、Maven rc、fixture identity 与 cleanup 的 durable receipt。
- [ ] new Cdiag commit/push/clean，fresh r26 all-lane/public validation PASS。
- [ ] candidate/capsule/双审、Cfreeze、fresh formal-r8 与后置 gates 全部 PASS 后关闭。

## References

- `docs/9.3.4/evidence/step-4/step4-unit-mysql57-known-consumer-7of12-remediation-20260719.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`
- `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- `docs/9.3.4/quality/step4-unit-mysql712-remediation-implementation-quality.md`
- `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- `scripts/v934/step4/unit_mysql_fixture_tool.py`
- `foggy-dataset/src/test/java/com/foggyframework/dataset/fun/DatasetJdbcUtilsTest.java`
