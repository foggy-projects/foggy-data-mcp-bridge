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

首次完整 fixture remediation r2 又暴露第二层根因：callback 通过全 reactor
`-Dspring.datasource.*` 注入 MySQL URL/credential，覆盖了 `foggy-dataset-model` 默认
SQLite URL，但没有改变其 `sqlite` profile 的 `org.sqlite.JDBC`。结果是 SQLite driver +
MySQL URL，不能用“给所有模块补 MySQL driver/profile”修正，否则会继续改变其余 Unit
测试的既有 profile 语义。

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
9. 仅在 `foggy-dataset` test resource 中以
   `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders 接收 callback 受控环境，移除所有
   global Spring datasource 参数；adapter path/hash/唯一 consumer 进入机器契约，inventory
   使用 scrubbed Git environment、`HEAD` tree 与 no-replace object。outer 与 callback 双层
   拒绝 underscore/dotted/hyphen Spring/custom key 及 `@argfile`、`VMOptionsFile`、
   `javaagent/agentlib/agentpath` 间接注入。
10. closed Unit Maven observation window 由 root 配置 `init_connect` 开始；Maven 返回后同一
    root batch 先 disable、再按 `connection_id` SELECT。receipt 保存有序 observed user，
    窗口内全部 non-super connection 必须 exclusively 使用 restricted `v934_unit`；callback
    后 provisioner `foggy` 控制面位于窗口外。

## Regression Evidence

- r7 RED：6 suites / 11 errors，outer=`child-unit / exit 1`；source-before=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；
- Unit remediation r2 RED：run=`step4-unit-fixture-quality-20260716-r2`，commit=
  `a603f839a98d99b2d7beb8379f76b4d85539328c`，source-before=`3,981` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`，lifecycle=`5/5`；
  `foggy-dataset-model=3,115/F0E631S0`，首因=`org.sqlite.JDBC` 拒绝 `jdbc:mysql`；final
  manifest/summary absent。child=`unit-mysql57-90da4977dc197f81` cleanup=`0/0/0`、port
  free，r2 excluded/non-reusable；
- fresh r3 GREEN：run=`step4-unit-fixture-quality-20260716-r3`，tested commit=
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`，source before=after=`3,982` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；唯一 Maven
  invocation=`681+55/4,941/F0E0S0`，closed receipt `18/18` 均为 `v934_unit`，fixture
  negatives=`36/36`、receipt schema=`4/4`、lifecycle=`5/5`、cleanup=`0/0/0`；
- focused GREEN：run-owned MySQL 5.7 下 Unit=
  `681+55 / 4,941 / F0E0S0`，schema before=after=
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`；
- cleanup：temporary container/volume/network=`0/0/0`，port free；
- occupied-port negative：preflight exit `1 / E_PORT_OWNED`，ambient demo container identity
  before/after exact；
- static negatives：Unit negative receipt=`36/36`，其中原 fixture/manifest schema/tamper=
  `20/20`、connection receipt typed=`7/7`、atomic publisher=`3/3`、profile isolation=
  `6/6`；negative receipt schema
  tamper 另为 `4/4`；report inventory=`30/30`、overlay=`12/12`；
- lifecycle：真实 process probes=`5/5`，覆盖 INT/TERM/HUP、callback failure、leader-kill
  fallback，逐项 residue=`0/0/0` 且 port free；
- exact contract：coverage=`23 exec / 48 sessions`；profile isolation hardening 后 top tooling
  manifest=`60/60` / `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`，
  successor=`14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  diagnostic/formal contract=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`，fixture
  contract/tool/runner=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`，overlay
  contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`。

fresh r3 与 formal remediation quality 已证明 Unit replacement subgate 通过，但不是 Step 4
exit evidence。当前 `fresh Unit r3=passed / formal remediation quality=passed /
commit-push=pending / fresh r8=pending`；不得宣称 Step 4、coverage audit 或验收通过，
Step 5 保持关闭。

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
- [x] r2 profile-isolation failure 已封存为 excluded/non-reusable；
- [x] profile-scoped adapter、ambient 双层拒绝、restricted exclusive receipt 与
      `36/36 + receipt schema 4/4` negatives、真实
      lifecycle `5/5` 纳入 fail-closed evidence。
- [x] 按最终工作树刷新并复验 top exact manifest=`60/60`、SHA-256=
      `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`。
- [x] fresh Unit remediation r3 通过。
- [x] 正式实现质量闸门通过，B/H/M/L=`0/0/0/0`。
- [ ] commit/push 后 fresh r8 all-lane 通过 `23 exec / 48 sessions`。
- [ ] fresh formal 复验通过后关闭本 BUG。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`
- `docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`
- `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`
- `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`
- `scripts/verify-v934-unit.sh`
- `scripts/v934/step4/unit_mysql_fixture_tool.py`
- `scripts/v934/step4/unit-mysql57-fixture-contract.json`
- `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`
- `scripts/verify-v934-step4-coverage.sh`
- `scripts/v934/step4/report_inventory_tool.py`
