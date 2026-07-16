---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-UNIT-HIDDEN-MYSQL-ENVIRONMENT-DEPENDENCY
severity: blocker
status: in-progress
reproduction_status: confirmed
product_regression: false
test_strategy: run-owned-mysql57-fixture-and-fresh-all-lane
automation_decision: required
owner: step2-unit-authority
---

# Step 4 Unit authority 隐式依赖环境 MySQL 导致伪绿色

## Background

Step 4 diagnostic r7 在四个 repo demo DB 容器均已停止、冻结端口均无 listener 的 clean
环境启动。Unit Surefire 在 `foggy-dataset` 出现 6 个测试类、11 个错误；直接根因均为测试
资源将数据源固定到 `127.0.0.1:13306`，但 Unit runner 没有声明、创建或验证该依赖。

此前 Unit authority 多次通过，是因为环境中长期存在 `foggy-demo-mysql` 及历史 schema。
这些资源既不属于 run，也没有被 report/summary/source seal 绑定，因此属于环境驱动的
伪绿色。r7 正确失败，但暴露 Unit authority 不具备 hermetic lifecycle。

## Expected vs Actual

- Expected：Unit 所需外部资源必须由当前 run 创建、固定 identity/fixture、使用后清理，并
  进入 summary/report inventory；无资源时不能借用 ambient listener。
- Expected：existing `13306` listener 必须 fail closed，runner 不得接管、停止或更改它。
- Expected：修复后仍只有一个 Unit Maven invocation，保持
  `681 positive + 55 structural / 4,941 testcase` 和全局 `23 exec / 48 sessions`。
- Expected：fresh Step 4 以 run-owned MySQL 5.7 替换完整 Unit lane；已知
  `6 reports / 11 testcase nodes` 只是隐藏依赖清单，不得据此宣称其余 Unit 测试无 DB 访问。
  Step 2 identity/cardinality 只保留结构意义，其 Unit 正确性绿色不得复用。
- Actual：旧 runner 直接执行 Maven，6 个测试类隐式连接 ambient `13306`；listener 停止后
  r7 在 Unit 阶段产生 11 errors。

## Root Cause

`foggy-dataset/src/test/resources/application.yml` 提供 MySQL 连接坐标，而 Unit selector 将
上述测试纳入 Surefire；runner 只治理 report/coverage lifecycle，没有治理数据源 lifecycle。
这形成了“测试声明外部依赖、authority 不声明资源”的断链。将这些测试临时迁移到 H2、
SQLite 或放宽 selector 都会改变 MySQL 方言语义或冻结 inventory，不是本轮最小可信修复。

## Remediation

1. Unit runner 为 outer run 派生唯一 child/project，前台同步复用 frozen MySQL 5.7
   provisioner；不修改 frozen Step 3 文件。
2. 固定镜像 digest、MySQL identity、端口、catalog 与精确 `M_ETL_TEST` DDL；禁止使用历史
   named volume 或外部 listener。
3. 原唯一 Maven Surefire reactor 作为 provisioner callback 执行；不增加 exec/session 或
   改变 report identity/cardinality。
4. callback 前后验证 schema/index/row snapshot 字节相同；Maven 非零时 summary/final 不得
   发布。
5. provisioner 和 outer finalizer 双层清理派生 Compose project；Unit 结束、Integration 前、
   Step 3 前均验证 residue=`0/0/0` 且 `13306=free`。
6. Unit fixture manifest/negative receipt 进入 Unit summary 与 Step 4 report inventory；top
   exact manifest 和 successor declared amendment 同步更新。
7. 以 `scripts/v934/step4/unit-mysql57-fixture-contract.json` 机器绑定已知 `6/11`、完整
   Unit lane replacement 与 Step 2 supersession 边界；后续迁移由
   `DEBT-unit-mysql57-fixture-classification-migration.md` 跟踪。
8. 使用 restricted test credential 并生成非空连接回执；真实执行 INT/TERM/HUP、callback
   failure 与 leader-kill fallback 共 5 个 lifecycle probe，所有路径均要求精确资源 cleanup。

## Regression Evidence

- r7 RED：6 suites / 11 errors，outer=`child-unit / exit 1`；source-before=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；
- focused GREEN：run-owned MySQL 5.7 下 Unit=
  `681+55 / 4,941 / F0E0S0`，schema before=after=
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`；
- cleanup：temporary container/volume/network=`0/0/0`，port free；
- occupied-port negative：preflight exit `1 / E_PORT_OWNED`，ambient demo container identity
  before/after exact；
- static negatives：Unit negative receipt=`27/27`，其中原 fixture/manifest schema/tamper=
  `20/20`、connection receipt typed=`4/4`、atomic publisher=`3/3`；negative receipt schema
  tamper 另为 `4/4`；report inventory=`30/30`、overlay=`12/12`；
- lifecycle：真实 process probes=`5/5`，覆盖 INT/TERM/HUP、callback failure、leader-kill
  fallback，逐项 residue=`0/0/0` 且 port free；
- exact contract：coverage=`23 exec / 48 sessions`；fixture hardening 后 top tooling
  manifest=`59/59` / `2a52dbf591238a9c163c0774014e1407dadd4d5037a62a4ce2d0c3af931d6aa7`，
  successor=`14/14` / `bd8d1f1ef97db15b1fb08548c52c6be3fa60d82e848d5741b6a36f1f828924db`。

focused/static 结果只证明修复可进入 fresh diagnostic，不是 Step 4 exit evidence。
当前 `formal remediation quality=pending / fresh r8=pending`；不得宣称 Step 4、coverage
audit 或验收通过。

## Verification Checklist

- [x] 以停止 ambient MySQL 的 r7 重现 6 suites / 11 errors。
- [x] 记录 r7 为 excluded/non-reusable，禁止复用不完整 exec/XML。
- [x] 实现 pinned、run-owned MySQL 5.7 fixture 和最小 schema。
- [x] 保持唯一 Maven invocation 与冻结 Unit cardinality。
- [x] 封存 schema before/after、resource identity、cleanup 与 port-free evidence。
- [x] existing port collision fail closed 且不改变外部资源。
- [x] fixture evidence 字段纳入 Unit summary、report inventory 与 amendment schema。
- [x] machine contract 与 DEBT workitem 明确 full Unit replacement、known `6/11` 和 Step 2
      structure-only/correctness-non-reuse 边界。
- [x] restricted test connection receipt、`27/27 + receipt schema 4/4` negatives 与真实
      lifecycle `5/5` 纳入 fail-closed evidence。
- [x] 按最终工作树刷新并复验 top exact manifest=`59/59`、SHA-256=
      `2a52dbf591238a9c163c0774014e1407dadd4d5037a62a4ce2d0c3af931d6aa7`。
- [ ] 正式实现质量闸门通过。
- [ ] commit/push 后 fresh r8 all-lane 通过 `23 exec / 48 sessions`。
- [ ] fresh formal 复验通过后关闭本 BUG。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`
- `scripts/verify-v934-unit.sh`
- `scripts/v934/step4/unit_mysql_fixture_tool.py`
- `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- `scripts/verify-v934-step4-coverage.sh`
- `scripts/v934/step4/report_inventory_tool.py`
