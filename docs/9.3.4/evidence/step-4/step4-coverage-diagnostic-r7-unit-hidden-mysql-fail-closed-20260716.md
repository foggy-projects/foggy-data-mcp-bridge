---
evidence_type: failed-diagnostic
version: 9.3.4
step: 4
run_id: step4-coverage-20260716-diagnostic-r7
tested_commit: 528a0a541d90ef77d577e1816b392d33168cb558
status: failed
failure_class: test-authority-hermeticity
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 coverage diagnostic r7 Unit hidden MySQL dependency fail-closed evidence

## Decision

`step4-coverage-20260716-diagnostic-r7` 在 clean、pushed commit
`528a0a541d90ef77d577e1816b392d33168cb558` 上启动。四个 repo demo DB 容器已在
evidence window 外停止，`13306/13308/15432/11433` 均无 listener。outer 建立
run-owned Git/source seal 后，Unit Surefire 在 `foggy-dataset` 暴露 6 个测试类、11 个
错误；共同根因是测试配置隐式依赖 `127.0.0.1:13306` 的常驻 MySQL：

| Suite | Errors |
|---|---:|
| `BugFixInsertUpdateMapTest` | 1 |
| `FDialectTest` | 2 |
| `JdbcUpdaterTest` | 2 |
| `SqlTableRowEditorTest` | 1 |
| `SyncSqlTableTest` | 1 |
| `JdbcTableUtilsTest` | 4 |
| Total | 11 |

日志中的 9 个直接错误为 `Communications link failure / Connection refused`；
`FDialectTest` 的 2 个 NPE 是连接失败后方言查询返回空值的次生错误。Maven 片段最终为
`Tests run: 105, Failures: 0, Errors: 11, Skipped: 0`，outer 为
`failed / exit_code=1 / last_phase=child-unit`。

这证明此前 Unit 绿色依赖环境中未纳管的 MySQL listener/历史 schema，属于测试权威的
伪绿色缺陷。r7 未形成 canonical Unit report authority，也未启动 Integration、Addon、
database、external、aggregate、threshold、source-after 或 summary，因此明确为
`excluded-from-step4-exit`，其单个不完整 `jacoco-ut.exec` 不得复用或拼接。

## Immutable outer result

- started/finished：`2026-07-16T13:17:44Z`—`2026-07-16T13:25:18Z`；
- tested commit：`528a0a541d90ef77d577e1816b392d33168cb558`；
- source-before：`3,976` files，SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；
- source-after、Unit summary/final manifest、outer summary、aggregate、coverage gate、threshold
  observation 与 candidate：全部 absent；
- JaCoCo exec：仅不完整 Unit `1/23`；expected sessions 未形成 authority；
- outer cleanup：run-owned container/volume/network residue=`0/0/0`。

关键 outer artifact SHA-256：

- `run-context.json`：
  `2ca1a268499dfbc2b2dfac0b44c0974ddaead1c076e866d9599441caffcb79fa`；
- `run-status.env`：
  `fa252227b55a3673ad6ed782dd10addc3420325ee28d79c3b2a9cce261362ddd`；
- `run.log`：
  `caafab55f2c20a1b273efd617792fbe5e40a151351bb742eec783779c806c39c`；
- `cleanup.env`：
  `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1`；
- `class-universe.json`：
  `486c73981dd182407e7cbc57db2e054fa1955a4d738c9c3a57fb6df90e2a38d9`；
- `toolchain-receipt.json`：
  `baae2846bf5a31caeb62c226e89cf10d78cb0ae16c99bf7bfd0dc4db17056dee`。

## Remediation design and focused validation

修复不接管 ambient listener，也不改变 6 个已知测试类或 Surefire/Failsafe selector。Unit
runner 同步复用 frozen `provision-database-cell.sh`，为 outer run 派生唯一 MySQL 5.7
child/project，固定镜像 digest、`foggy_test|5.7.44-log`、端口 `13306` 与最小
`M_ETL_TEST` schema；唯一 Maven Unit 调用作为 provisioner callback 执行。callback 前后
封存 schema/row snapshot，并以 restricted test credential 生成连接回执；provisioner 随后
删除 container/volume/network，Unit、outer 与后续 Step 3 boundary 均复验 residue=
`0/0/0` 且端口已释放。

该设计替换完整 Unit lane并保持 `681 positive + 55 structural / 4,941 testcase`，不是只重跑
6 个已知 suite。`6 reports / 11 testcase nodes` 仅是 r7 已知隐藏依赖清单，不构成其余 Unit
测试无数据库访问的证明。Step 2 identity/cardinality 继续保留结构含义，其 Unit 正确性绿色
不再复用。机器例外契约为 `scripts/v934/step4/unit-mysql57-fixture-contract.json`；临时分类
与 9.3.5 迁移关闭条件记录于
`docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`。

focused implementation run（不是 canonical diagnostic/exit evidence）：

- outer id=`step4-unit-fixture-focused-20260716-r1`；
- child id=`unit-mysql57-7c2b09e5798332eb`；project=
  `v934db-mysql57-992edd2cfc7b`；
- frozen Unit reports=`681 positive + 55 structural / 4,941 testcase / F0E0S0`；唯一 Maven
  invocation，coverage session 数设计保持不变；
- provisioner fixture before/after=
  `70b1a5d755bd781004cd35abd8d11525a997b857335165e0b0e2754ae38950cf`；
- Unit schema before/after=
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`；
- status/cleanup receipt SHA-256=
  `287b206c67e42a5c27e9936122242860e77b11a0d49726e995e75eb984329a52` /
  `954eeb76e89e47c58457425f3536a7421765ace92dcc06703aa80dc6d8df266c`；
- callback status SHA-256=
  `a57766bbf27247d218d9bd1b3da3837c8efdefb8d9d60698c17ba6e51842f339`；
- cleanup 后 temporary container/volume/network=`0/0/0`，`13306=free`。

port-occupied negative 使用原 demo MySQL 验证：child=
`unit-mysql57-9da8bd129571a072`、project=`v934db-mysql57-df59f45e7ca4`，provisioner 在
`preflight` 以 exit `1 / E_PORT_OWNED` fail closed；既有 demo container exact ID、健康状态
与 listener 均未改变，派生 project cleanup 通过。该 negative status/cleanup SHA-256=
`6ef91181f31461977c94db46d8059edc19ee0d21598c83b6d24625097e103318` /
`ea14720829c1ac1f6318e62e97e75017005c7e3af9829b377acf5429fba9de27`。

## Follow-up Unit remediation r2 boundary

第一次完整 fixture authority retry=`step4-unit-fixture-quality-20260716-r2` 在 commit
`a603f839a98d99b2d7beb8379f76b4d85539328c` 上建立 source-before=`3,981 files` /
`087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`，并先完成真实
lifecycle=`5/5`。主 fixture 随后成功启动，但 callback 以全 reactor
`-Dspring.datasource.*` 覆盖 MySQL URL，`foggy-dataset-model` 默认 `sqlite` profile 仍保留
`org.sqlite.JDBC`，最终得到 `3,115/F0E631S0`，首因是 SQLite driver 拒绝 `jdbc:mysql`。

r2 没有 final manifest/summary、fixture after/receipt 或 source-after；child=
`unit-mysql57-90da4977dc197f81` 已 cleanup `0/0/0` 且 port free，demo exact container 在
evidence window 外恢复 healthy。r2=`excluded/non-reusable`，不得与 focused/r7/r8 证据拼接。
完整记录见
`docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`。

最小修复只让 `foggy-dataset` test resource 通过
`V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders 消费 callback 受控环境，移除所有
global Spring datasource args并保持其余模块 profile 不变。outer/callback 双层拒绝
underscore/dotted/hyphen Spring/custom key 及 `@argfile`、`VMOptionsFile`、
`javaagent/agentlib/agentpath`；adapter path/hash/唯一 consumer 进入机器契约，其 inventory
使用 scrubbed Git environment 枚举 `HEAD` tree 并禁用 replace object。connection receipt
只覆盖 closed Unit Maven observation window：root 配置 `init_connect` 后运行 Maven，随后在
同一 root batch 先 disable 再按 `connection_id` SELECT，保存有序 observed user；该窗口内
所有 non-super connections 必须为 restricted `v934_unit`，callback 后 provisioner 的
`foggy` 控制面连接位于窗口外。

## Contract closure before r8

- successor overlay positive/negative=`12/12`；
- Unit negative receipt=`36/36`：原 fixture/manifest schema/tamper=`20/20`、connection receipt typed=
  `7/7`、atomic publisher=`3/3`、profile isolation=`6/6`；negative receipt schema tamper
  另为 `4/4`；
- 真实 fixture lifecycle=`5/5`：INT/TERM/HUP、callback failure 与 leader-kill fallback
  均返回约定 exit，逐项 container/volume/network=`0/0/0` 且 `13306=free`；
- report inventory negatives=`30/30`；fixture manifest、negative receipt、lifecycle receipt 与
  classification contract/debt 引用进入 typed inventory；
- coverage execution contract 仍为 `23 exec / 48 sessions`；required inventory 仍为
  `773 positive + 59 structural / 5,707 testcase`，Addon=`2/6`；
- profile isolation hardening 后 top tooling manifest=`60/60` /
  `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  diagnostic/formal contract=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
  threshold 仍为 `diagnostic-pending`，未修改 threshold 或 exclusion。

以上 static 结果当时只允许发起 fresh Unit r3。后续 r3 已通过，见
`step4-unit-fixture-quality-r3-pass-20260717.md`；该结果仍不能替代 all-lane r8。

## Next gate

- [x] r7 immutable failure、11 errors 与 cleanup evidence 已封存；
- [x] 建立 `BUG-step4-unit-hidden-mysql-environment-dependency.md`；
- [x] 实现 run-owned pinned MySQL 5.7 fixture、schema seal、cleanup seal 与 negatives；
- [x] focused Unit 保持 `681+55 / 4,941 / F0E0S0`；port collision fail closed；
- [x] machine contract/DEBT 固定 full Unit replacement、known `6/11` 与 Step 2
      structure-only/correctness-non-reuse 边界；
- [x] r2 profile-isolation failure 已封存为 excluded/non-reusable；
- [x] Unit negative receipt=`36/36`、negative receipt schema tamper=`4/4`、真实 lifecycle=
      `5/5`、report inventory negatives=`30/30`；
- [x] 按最终工作树刷新并复验 top exact manifest=`60/60`、SHA-256=
      `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`；
- [x] fresh Unit remediation r3 通过；
- [x] 正式实现质量闸门通过；
- [ ] commit/push 并证明 clean `HEAD == origin/main`；
- [ ] 从 clean/pushed HEAD 执行 fresh r8 all-lane diagnostic；
- [ ] reviewed threshold freeze、fresh formal 与最终质量闸门完成。

fresh Unit r3 与正式实现质量已通过；commit/push 与 fresh r8 当前仍 pending。在 fresh r8、threshold freeze、formal 与最终质量完成前，
`can_enter_coverage_audit=no`，Step 5 保持关闭。
