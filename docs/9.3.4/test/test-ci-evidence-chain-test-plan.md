---
doc_role: test-plan
doc_purpose: Define positive, negative and authority verification for the 9.3.4 test and CI evidence chain.
version: 9.3.4
status: in-progress
result: in-progress
step1_result: passed
step2_result: passed
step3_result: passed
step4_result: in-progress
created_at: 2026-07-14
updated_at: 2026-07-17
---

# 9.3.4 Test and Evidence Plan

## 文档作用

- doc_type: test-plan
- intended_for: test/build/CI implementers and evidence reviewers
- purpose: 把 requirement criteria 映射到可执行 positive、expected-negative 和
  authority evidence；本文件不预填任何通过结果。

## Evidence Rules

- 每个 positive suite 必须有 owning module、FQCN、runner、lane、fresh XML、exact
  testcase count 和 commit/source marker。
- expected-negative 必须验证 wrapper/aggregator 非零退出和稳定错误原因；失败输入
  不得污染后续 positive reports。
- 只有同一 clean commit 的 Step 7 exact authority 可用于签收；diagnostic、failed、
  superseded 或多个 run 拼接结果全部排除。
- v931–v933 exact runner/raw evidence 只作 historical baseline；current-source 通过
  reviewed migration map + v934 successor reports 验证，旧 runner 不要求重放。
- compile/package success 不是测试通过；workflow YAML 存在不是 branch protection
  或 remote required check 通过。

## Step 1 — Inventory / Contract

Positive：

- 扫描 workspace candidates 与 active root reactor modules，输出
  `source-inventory.tsv`；用 discovery-only JUnit test plan、POM variants 和 available
  diagnostic XML 展开 `execution-inventory.tsv` 的 report FQCN/runner/lane/variant/
  db/infra/step，不执行 Step 3 external fixtures。
- 人工 review prefix-only `Test*`/`IT*`、`*IntegrationTest`、`*IT`、nested/dynamic/
  report-producing suites；helper/non-executable source 也保留 owner/reason。
- workspace source↔source inventory 双向差集为 0；non-reactor source 有显式
  disposition。每个 executable reactor source 至少一个 execution row；helper/
  generator 为 0 rows 且有 owner/reason。
- 65 个 diagnostic `@Nested` source 的 `Outer$Nested` expected reports 逐行 review；
  DB/provider/profile variants 以 execution key 区分，全表 key 唯一。
- predecessor migration group 声明 relation 与 old/successor cardinality；observed
  distinct nodes exact，所有 predecessor 都被覆盖、successor key 均存在于 execution
  inventory、edge tuple duplicate=0；mapping edges 不参与测试总数求和。
- historical package inventory 与新 POM-only reporter delta 有 reviewed mapping；
  reporter 不产 main JAR、不进入 Launcher，successor reactor/JAR set 单独冻结。
- `rename-successor-plan.tsv` exact 覆盖 33 sources、62 reports、74 old/new execution
  keys 和 50 predecessor edges；target 只做 `IntegrationTest→IT`，执行语义列不变、
  无碰撞，plan SHA 纳入 confirmed summary。
- contract freeze 记录 manifest SHA、tool/source SHA、reviewer、decision。

Expected-negative：orphan source、未声明 non-reactor source、nested report missing/
unexpected、execution key/edge duplicate、同 key 双 runner、missing owner、optional
无 reason、stale manifest、zero candidate owning module、migration cardinality mismatch/
unmapped、tampered rename successor/policy 均被拒绝。

## Step 2 — Runner Split

Positive：

- root/reactor unit command只由 Surefire执行 unit；all integration/E2E 只由
  Failsafe `integration-test/verify` 执行。
- Step 1 pre-rename baseline immutable；post-rename inventory 写入独立 successor
  目录，parent manifest/rename-plan SHA exact，approved delta 双向差集精确且再次独立
  confirm；Step 1 baseline validator 变 stale 不能被当作 post-rename 失败或被覆盖。
- final `*IntegrationTest` ambiguous source count=`0`；Step 2 raw report execution
  keys 与 confirmed Step 2 successor positive `execution_step=2` subset 完全一致，
  不要求尚未执行的 Step 3 subset。59 个 reviewed outer zero-test containers 进入独立
  structural inventory，不进入 positive execution/test totals。
- fresh XML exact set 等于 positive + structural expected reports；positive suite tests
  大于 0，structural suite tests/testcase/F/E/S 全为 0，且 suite tests 总和等于 testcase
  nodes；同一 FQCN 不同时出现在 Surefire/Failsafe 或 positive/structural 两类。
- SQLite broad integration 与 five-DB parity SQLite 子 lane 的 execution-key subsets
  双向求交为空；相同 `(report_fqcn, sqlite, lane)` execution count 不大于 1。
- all unit + hermetic IT actual pass；DB/Redis/other external required suites 只以
  confirmed Step 2 successor exact manifest 标 `deferred-to-step3`，且 owner/preflight
  唯一。
- DB/外部依赖测试移交 Step 3 仍是默认通则。r7 暴露的 Unit 隐式 MySQL 依赖只触发
  9.3.4 临时例外：Step 2 的 Unit identity/cardinality 仅保留结构证明，旧绿色不得复用为
  correctness；correctness 必须由 fresh Step 4 的完整 Unit lane replacement 重建。
- 例外的机器权威为
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`。其中 6 个 execution key / 11 个
  testcase nodes 是 r7 已知清单，不构成穷尽证明，也不得据此只重跑这 6 个 suite。
- renamed FQCN 与 predecessor typed migration map 双向一致；50 个受影响 edges 经
  plan 确定性改写，480 execution refs + 39 structural refs 覆盖全部 519 historical
  nodes，无 criterion 丢失。

Expected-negative：指定 owning test 不存在、0 report、旧 XML、duplicate FQCN、
Surefire 执行 IT、Failsafe 执行 unit、helper module 放宽掩盖 owner 0 tests、缺 parent
link、plan 外 rename、语义列漂移、structural missing/nonzero/无 positive sibling、
typed predecessor ref 漂移或覆盖 Step 1 baseline 均失败。这里的 0 report 指 positive
execution 为 0；reviewed structural report 只能按独立 strict-zero contract 通过。

## Step 3 — Required Database / External Matrix

| Lane | Required positive evidence | Required negative evidence |
|---|---|---|
| SQLite | JDBC artifact/version/hash、physical file/memory coordinate、sentinel、Failsafe parity/capability XML、S0 | wrong artifact/coordinate/sentinel、mutated fixture、missing XML |
| MySQL 5.7 | pinned image/digest、product/version/host/port/catalog、sentinel、QueryFacade/native parity、S0 | unavailable、wrong MySQL major/catalog/sentinel、fixture drift |
| MySQL 8 | pinned image/digest、product/version/host/port/catalog、sentinel、QueryFacade/native parity、S0 | unavailable、accidentally hitting 5.7、wrong catalog/sentinel |
| PostgreSQL 15 | pinned image/digest、product/version/host/port/catalog/schema、sentinel、parity、S0 | wrong major/schema/sentinel、fixture drift |
| SQL Server 2022 | pinned image/digest、product/version、driver-aware host/port/catalog/schema、sentinel、dialect-native oracle、S0 | generic URL misparse、wrong edition/version/catalog/sentinel |

所有库使用同构 sentinel manifest 和幂等 fixture init。支持能力走 positive；不支持
能力必须返回被精确断言的 refusal，不产生 `<skipped>`。五库 required skip 总数为 0。
v934 fixture/profile 必须与 v933 共用 demo 初始化隔离；authority 使用 fresh/run-scoped
storage，禁止把长期 named volume 当作初始状态证明。Pivot pre-aggregation 用例必须真实
执行 rewritten relation/planned SQL 并与 native oracle 对比，单纯断言 SQL 字符串包含
`preagg_` 不计为证据。
此外，Step 2 deferred 中 45 个 required DB/Redis/other external execution 必须全部
actual pass 并清零 required gap；1 个 optional LLM execution 保留 reviewed
disposition。raw report keys 与 confirmed Step 2 successor `execution_step=3` required
subset exact。Step 2/3 required execution key 并集等于该 successor generation 全部 required execution
inventory、交集为空。此 Step 只验证 correctness/identity/
report，不要求或接受 coverage exec。

Required external contract 精确冻结为：Redis `2 reports / 3 testcase`、Mongo/DataViewer
`4/30`、MCP/MySQL57 `8/23`、Vector `2/20`，合计 `7 variants / 16 reports / 76 testcase`；
optional LLM 独立为 `1/1`。collector 除 missing/extra/duplicate/F/E/S/stale 外，还必须拒绝
flaky/rerun outcome、raw-report run-context splice、wrong selector/marker 与 cross-run manifest。

Historical Redis、Mongo/DataViewer、MySQL57 与 Vector subset candidates 分别为
`2/3`、`4/30`、`8/23` 与 `2/20`，且仍不得彼此拼接。当前 Step 3 authority 只接受同一
committed HEAD 的 parent run `step3-required-20260716-final-r4`：database `29/370` +
external `16/76` = exact `45/446/F0E0S0`，gap/overlap/extra=`0/0/0`。DB state=
`18/18`、Redis resource state=`4/4`；Addon companion=`2/6/F0E0S0` 且不计入
45/446；optional LLM=`reviewed-optional-excluded`。Step 3 correctness 没有 coverage
exec，Step 4 必须重新带 agent 执行全部 required lanes。

## Step 4 — Coverage

Positive：

- 接好 agent 后重新执行 all unit、6 个 hermetic/SQLite variants、five-DB 7 variants、
  7 个 required external variants 与 Addon companion 2 variants；共 23 个独立 exec，
  optional LLM 继续 reviewed/excluded。manifest 绑定
  JaCoCo version、commit、source/classes hash、lane/session。
- r7 触发的 9.3.4-only Unit replacement 以
  `scripts/v934/step4/unit-mysql57-fixture-contract.json` 为机器权威：在同一次 Maven
  invocation 中完整执行 `681 positive + 55 structural / 4,941 testcase`，只连接
  pinned/run-owned MySQL 5.7，并生成 restricted non-super exclusive connection receipt、
  typed schema receipt、before/after seal、publisher/lifecycle 与 cleanup evidence。
  datasource override 只通过 `foggy-dataset` test resource 的
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` 三个 placeholder 生效，不得覆盖其他模块的
  SQLite/其他 profile。Step 2 旧绿色、ambient MySQL 或仅重跑已知
  6 keys / 11 nodes 均不能替代该完整 lane。
- 6 keys / 11 nodes 只是 r7 已知消费者清单；发现新增消费者时必须同步更新 fixture
  contract 与
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`，并重新执行
  fresh formal、实现质量闸门和测试证据覆盖审计。
- build-only aggregator 只产生 aggregate XML/HTML；versioned verifier 精确校验
  expected module/package/class、counter totals 和 reviewed thresholds。
- model 既有 LINE 0.77 / BRANCH 0.62 对 UT+IT merged exec 的 owning-module check
  不下降；reactor baseline 经人工 review 后冻结；critical set 每类达到最终门。
- 同一 source/class 对 coverage report、tested JAR 和 authority manifest 一致。
- Step 4 后 canonical lanes 一次执行即产 exec；coverage collect/check 在 Step 5–7
  只消费 upstream exec，测试 FQCN execution ledger 不出现 coverage-induced duplicate。
- Unit/Integration 的 run logger 必须为 owned FIFO process；在发布 child green 前
  关闭写端并 wait logger，并由 ready/completion receipt 绑定 PID/PGID/SID/
  starttime/boot-id。真实组残留在 kill 前生成 bytes-safe member snapshot。
- XML verifier 对 child lifecycle、formalization delta、canonical gate/candidate/final/status
  path 执行 typed 重算；成功 status 是全部其他证据通过后的最后一次
  原子发布，发布后不再有可使 run 转红的 post-verification。
- confirmed formal 必须重放 threshold 指向的真实 frozen diagnostic run，从该
  diagnostic commit 读取历史 threshold/contract blob 并重算；threshold freeze
  仅允许 diagnostic commit 的一个 direct-single-parent child。

Expected-negative：missing/empty/truncated exec/XML、missing expected class/package、
错误 commit/class/source hash、zero counter、低于门槛、阈值被私降、exclusion
漂移、同名 exec 并发覆盖、空 reporter `jacoco:check`、只有 unit 或只有 IT exec、
slow/nonzero/timeout logger、持管道 descendant、ready receipt 缺字段/换 inode/错
starttime、非 canonical final path、伪造 diagnostic run、merge/multi-commit/shallow/
replace-ref/graft formal delta 均失败。Unit replacement 还必须拒绝 fixture contract 与
parent execution/discovery metadata 不一致、ambient/既有容器复用、connection receipt
缺失/为空/类型错误、schema seal 漂移、publisher 非原子或 lifecycle/cleanup 不完整、
全局 Spring datasource/profile 覆盖、placeholder 错绑/跨模块消费、non-super connection
未独占 restricted credential、仅重跑已知 6 keys，以及绕过 DEBT acceptance gate。

Historical diagnostic-ready result（2026-07-16，不是 Step 4 pass）：静态执行结构为
`23 exec / 48 sessions`，required report overlay 为
`773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion 独立为
`2/6`。raw contract/effective POM/toolchain/report inventory expected-negative 已分别得到
`8/8`、`4/4`、`5/5`、`27/27`；Step 2 derived view/successor overlay negatives=
`12/12`、`8/8`。toolchain receipt 还必须在执行边界持续
复验 Step 1 raw 工具版本、ASM `9.6/9.7/9.7.1` 三层 realm 和 24 个
production module effective compiler。report amendment exact=
`10 rows = 4 new + 6 changed`，SHA-256=
`5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`；successor
declared amendments=`15`。本地 `scripts/v934/step4/SHA256SUMS` 已生成并通过 exact
49 项校验，manifest SHA-256=
`c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`。

Diagnostic r1 result（2026-07-16，failed evidence，不是 Step 4 pass）：clean/pushed
HEAD=`bc100b0f63bd3ff62d1105611dae41741790aedd`，run=
`step4-coverage-20260716-diagnostic-r1`；`child-unit` 结果=
`3115 tests / 1 failure / 0 errors / 0 skipped`，outer 正确 fail closed，未进入
aggregate/report/threshold。失败证明 `PreAggregationDataValidationTest` 腐化 daily
而默认 hybrid 实际命中 monthly；同类还存在 raw-vs-raw 和 nullable/empty 伪绿。

Diagnostic r2 result（2026-07-16，failed evidence，不是 Step 4 pass）：clean/pushed
HEAD=`0101a44a07784bf6b484d490c7fb508727fbab70`，run=
`step4-coverage-20260716-diagnostic-r2`。Unit=
`681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration 已执行
caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、sqlite-broad=`307/F1E0S0`，合计
`312/F1E0S0`。唯一失败为 `PreAggregationL2CacheIT` 的 `preAggHit` 前提；outer 在
`child-integration` fail closed，summary absent，database/external/Addon/aggregate/
threshold 均未运行。failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md`。

Diagnostic r3 result（2026-07-16，failed evidence，不是 Step 4 pass）：clean/pushed
HEAD=`e16693297239f2a861f3b93b3de60c1bb783bda0`，run=
`step4-coverage-20260716-diagnostic-r3`。contract/successor/toolchain/Step 2 view/fresh
class universe 通过，Unit=`681 positive + 55 structural / 4,941 testcase /
F0E0S0`。outer 在 Unit leader 返回后检测到同 PGID live member，因而在
`child-unit` fail closed；Integration/database/external/Addon/aggregate/threshold 均未执行，
summary absent。failed evidence=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`。
r3 Unit 不得与 r1/r2/focused 结果拼接为绿色。

Build regression：

- `docs/9.3.4/workitems/BUG-step4-legacy-coverage-argline-fail-open.md` 已关闭：
  `coverage_tool.py` + manifest + `validate-contract` + `8/8` negatives 精确保护
  canonical late-evaluation 形式；三态 focused 动态证据为 legacy coverage 产
  exec/低门 `rc=1`、普通 profile `rc=0/no exec`、v934 profile=
  `rc=0/non-empty exec/exact session`；
- historical `scripts/verify-v934-step2-successor.sh` 在当前 25-module root 因冻结
  24-production-reactor generation 而 fail closed 是预期 supersession boundary。正式
  Step 4 路径必须使用 immutable Step 2 parent + `step2_report_view_tool.py`
  derived view + overlay，不得为重跑 historical authority 将其放宽到 25。

Test regression：

- `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md` 已关闭测试缺陷：腐化
  探针改为实际命中的 `preagg_monthly_category_sales` 并精确 update/restore 一行，在金额
  差断言前验证 hit/name；三个 snapshot 比较显式关闭 hybrid，路由依次为
  `daily_product_sales`、`daily_product_sales`、`daily_customer_channel_sales`，所有
  raw/PreAgg 列表、字段和值均拒绝 null/empty/missing/non-numeric；
- focused class=`9/F0E0S0`；DataValidation+EdgeCase+Matcher+RequirementBuilder=
  `57/F0E0S0`；monthly corruption diff=`1000.00`；source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`。这些 focused
  结果证明修复面，不替代新 clean/pushed HEAD 的 Unit authority 和 r2 all-lane evidence。
- `BUG-step4-preagg-l2-hybrid-fixture-drift.md`：L2 identity 测试显式选择 snapshot-only，
  exact 校验 preAgg name/table、raw negative、lookup/write key、二次 hit 与单次 write；最终
  source SHA-256=`bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`，
  focused=`1/F0E0S0`，与 `PreAggregationIT` 组合=`30/F0E0S0`；
- `BUG-step4-pivot-legacy-hybrid-fixture-drift.md`：legacy fallback 两次稳定 RED；只在 legacy
  分支关闭 hybrid，V934 FULL 分支保持 production 默认，两个分支 exact 校验 name/table/raw
  negative。最终 source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`，legacy 与
  V934 SQLite focused 各 `1/F0E0S0`；
- identity/static 验证：coverage amendment=`11 rows / 4 new + 7 changed`、declared
  amendments=`17`、top manifest=`51/51`、successor manifest=`12/12`；对应 SHA-256=
  `937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2` /
  `be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548` /
  `348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1` /
  `6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`；
  coverage/view/successor/DB negatives=`8/12/8/14` 全绿。focused 结果仍不替代 r3。

Runner/evidence regression（Cdiag，pre-r4 main quality gate 已通过）：

- `BUG-step4-child-run-log-tee-residue-race.md` 确认 Unit/Integration 的
  `exec > >(tee ...)` logger 未被 close/wait；实现改为共享 managed FIFO
  lifecycle，绿色 status/summary 必须在 logger flush/reap 之后；
- `run_log_lifecycle_negative_test.sh` 以 `9 类 / 14 case` 定义 slow/nonzero/timeout/
  PID-reuse/early-signal/capture-failure/exit/clean-group/persistent-residue 负例；outer 的
  ready/completion receipt 和 cleanup snapshot 定义
  process identity 与 residue 证据边界；
- `coverage_xml_negative_tool.py` 与 typed XML validator=`63/63`，覆盖 child
  lifecycle、source/context/manifest 四元绑定、exact retained raw exec replay、
  formalization delta 与 canonical evidence path；成功 status 最后发布，formal
  frozen replay 不允许 synthetic diagnostic；
- contract mutations=`20/20`、source Git identity=`7/7`；`Cfreeze` 只允许一个
  direct single-parent commit，merge、multi-commit、shallow、replace/graft 及 allowlist
  外 delta 均 RED。这些已由主线程正式复核，但不替代 fresh r4 all-lane evidence；
- identity：top=`54/54` / SHA=
  `589a7d67f35a0f09c7f1a026dbbf07e56dc89f099ca51291418cd1c6cc5fd077`，
  successor=`12/12` / SHA=
  `961e50350cef1c7984c6ff6b4fd0b5716ac5bb87d42271a3478233258b30784f`。

Post-r4 source-policy regression（superseding current entry）：

- r4 reported launch head=`ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
  `step4-coverage-20260716-diagnostic-r4`；outer 在 `source-before` 以 `rc=2` fail
  closed，run-owned Git/source seal 与全部 lane/aggregate/summary absent，明确排除 Step 4
  exit；reported head 不是 run-owned `tested_commit`；
- BUG=`BUG-step4-source-inventory-filemode-false.md`：`core.fileMode=false` clean checkout
  的 worktree executable bit 不得被当作 authoritative Git mode；
- positives 覆盖安全 `100644 -> 0775` 与 `100755 -> 0644` permission mapping；negatives
  覆盖 world-write、special-bit、hardlink、owner/group、content/blob/index flags、fsmonitor/
  untracked-cache 等边界；另以 tracked FIFO 证明 canonical preflight 在 worktree-aware Git
  读取前 fail-fast，并以受控并发重写证明 before/after raw stat identity 会拒绝即使 Git clean
  过滤后内容等价的变化；
- source seal 清除 ambient/global Git clean 配置并显式复算 raw 与 CRLF-input 两个
  candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；HEAD-fixed
  attributes 若声明 external clean filter，则在任何 worktree-aware Git hash/driver hook
  执行前 fail closed，negative 证明 hook 未执行；
- focused static result：contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=
  `12/12`；top=`54/54` / SHA=
  `ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`，successor=
  `12/12` / SHA=`751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；
  coverage contract diagnostic/formal SHA=
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；coverage tool /
  contract-negative / XML tool SHA=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
  overlay tool / outer SHA=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`；
- these tests prove remediation only；final-byte review=`ready-with-risks`，B/H/M/L=
  `0/0/0/2`；两项 Low 均 accepted：`/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为
  build authority，未来更强隔离改用 readonly snapshot/独立 checkout。当前只放行
  amend/push 与 fresh r5 all-lane diagnostic；
  `can_enter_coverage_audit=no`，Step 5 关闭。

## Step 5 — Authority Rehearsal / Immutable Candidate

Positive：

- single runner 串联 inventory/migration、unit、integration、五库、coverage、
  9.3.1–9.3.3 successor regressions、package 和 Launcher audit。
- run root 包含 environment/source/inventory/lane/skip/DB/coverage/regression/JAR
  manifests、raw XML、inner/outer `SHA256SUMS`、deterministic archive/digest。
- protected dirty source/worktree before/after 不漂移；DB fixtures、reports、JAR/
  nested JAR 保持契约；runtime-only image 内 `/app/app.jar` SHA 与 candidate JAR
  exact；archive 上传模型经过 download-and-verify。
- run 明确标 candidate/diagnostic，只写 candidate pointer，不写 final authority pointer。

Expected-negative：`--skip-external-db`/skipTests/skipITs、missing/stale XML、skip
drift、migration gap、report/JAR/image/source hash mismatch、tampered archive、missing
artifact、failed run更新 candidate pointer、candidate 更新 final pointer、credential
scan hit 均失败。

## Step 6 — CI / Release

Positive：

- PR/main reusable workflow 实际运行所有 required jobs；always-run aggregator 名称
  固定为 reviewed stable check，并确认 branch rule 实际引用。
- artifact 名含 commit/run/attempt；package/evidence job 下载并复验所有 upstream
  evidence。
- five-DB cells 各自产带 db kind/SHA/run/attempt 的 artifact；collector 只接受
  exact set/cardinality=5 和 fresh manifests/XML。
- release dry-run 校验 tag SHA，下载同一已测 Launcher JAR/archive/digests；GitHub
  asset直接使用该 JAR，runtime-only Dockerfile COPY 后回读 image JAR SHA，均不重建。

Expected-negative：任一 required job `failure`、`skipped`、`cancelled`；artifact
missing/tampered/duplicate/wrong-kind 或 cardinality!=5；tag SHA mismatch；release
使用 `-DskipTests`、source-building Dockerfile 或生成/嵌入不同 JAR；
legacy partial workflow 冒充 required authority，全部使 aggregator/release 失败。

## Step 7 — Final Authority and Gate Order

在 exact clean commit 完整回放 v934 successor authority，并由独立 reviewer 从
migration map、原始 XML、DB manifests、JaCoCo XML、JAR/image/archive digests 和
CI job states 复算，不只读取 summary。

Final acceptance 至少验证：

- inventory orphan/overlap/duplicate/ambiguous=`0`；
- Surefire/Failsafe 所有 owning lane fresh、exact、required S0；
- 五库 identity/sentinel/parity/capability refusal 全通过；
- aggregate XML verifier、model merged-exec 与 critical coverage checks 全通过；
- 9.3.1–9.3.3 successor mapping/regression、package、Launcher、JAR=image
  same-artifact release 全通过；
- five-DB artifacts exact set/cardinality=5，且每 cell XML/manifest fresh；
- CI required aggregator 的 success 与 failure/skipped/cancelled negative 均有实际证据；
- inner/outer/archive digest、下载复验、sensitive scan 全通过；
- self-check → quality gate → coverage audit → version acceptance 顺序无跳门。

## Requirement Mapping

| Requirement | Primary steps | Minimum evidence |
|---|---:|---|
| INVENTORY | 1/2 | frozen source/execution manifests + migration/hash + subset set proofs + negatives |
| RUNNER-SPLIT / NO-ZERO | 2/3 | unit/hermetic IT + external-deferred/actual union + exact XML + negatives |
| DB-IDENTITY / DB-PARITY / SKIP-ZERO | 3 | five DB manifests/XML/native oracle + unavailable/wrong/skip negatives |
| COVERAGE-AGG / COVERAGE-CRITICAL | 4 | all-lane exec + aggregate XML verifier + model merged check + negatives |
| AUTHORITY / EVIDENCE-IMMUTABLE | 5/7 | candidate rehearsal + clean exact run + two-layer/archive digest + recomputation |
| CI-REQUIRED | 6/7 | exact five-cell artifacts + actual PR/main state negatives + branch-rule evidence |
| RELEASE-SAME-ARTIFACT | 6/7 | tag/source/JAR/image linkage + download/reverify + rebuild-negative |
| REGRESSION-931-933 | 1/5/7 | historical→successor mapping + current-source exact lane summaries/raw XML |
| POST-GATES | 7 | timestamped ordered gate records and version signoff |

## Current Result

- result: `in-progress`
- step1_result: `passed`
- step2_result: `passed`
- step3_result: `passed`
- step4_result: `in-progress / r9 coverage-report excluded / remediations implemented / quality passed / commit pending / fresh r10 pending`
- confirmed run: `step1-candidate-r8-20260714`
- Step 1：532 workspace sources、820 discovery rows、829 execution keys、519
  predecessor nodes/edges；28/28 expected-negative probes 精确通过。
- Step 2 confirmed successor=`step2-candidate-r8e-20260715`；current required split=
  `724 Step 2 positive + 46 Step 3 deferred + 59 structural`。
- Surefire `677/55/732` reports、`4,890` testcase；Failsafe `47/4/51` reports、
  `315` testcase；combined `5,205`，F/E/S=`0/0/0`，report negatives 各 `20/20`。
- INT/TERM/HUP 实际信号探针为 `130/143/129` 且失败 summary absent；r8d 及其 runner
  evidence 已 superseded，不参与当前结果。
- Step 3 formal parent=`step3-required-20260716-final-r4`，tested commit=
  `ce3d70c391c7b8bd8046fe66dde0ad568d66601e`；database=`29/370/F0E0S0`，
  external=`16/76/F0E0S0`，exact union=`45/446`，gap/overlap/extra=`0/0/0`。
- 五库分项：SQLite `5/50`、MySQL 5.7 `5/50`、MySQL 8 `6/105`、PostgreSQL 15
  `8/115`、SQL Server 2022 `5/50`；DB state=`18/18`、report negatives=`14/14`。
- external 分项：Redis `2/3`、Mongo `4/30`、MySQL57 `8/23`、Vector `2/20`；
  Redis state=`4/4`、external report/sensitive negatives=`12/12`、`24/24`。
- Addon companion=`2/6/F0E0S0`，不计入 45/446；optional LLM reviewed、未执行、
  excluded。Parent/child source/fixture seals与 cleanup verifier通过，residue=`0/0/0`。
- evidence：
  `docs/9.3.4/evidence/step-1/inventory-contract-freeze-20260714.md`；
  `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`；
  `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-database-matrix-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-redis-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-mysql-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-external-vector-runner-candidate-20260715.md`；
  `docs/9.3.4/evidence/step-3/step3-shared-external-matrix-candidate-20260716.md`；
  `docs/9.3.4/evidence/step-3/step3-required-matrix-exit-20260716.md`。
- Step 4 static readiness：exact `23 exec / 48 sessions`、
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon=`2/6`；
  contract/source/XML/logger=`20/22/63/14`，effective POM/toolchain/report inventory=
  `4/5/30`，derived view/overlay/DB/external=`12/12/14/12`；Unit fixture negatives=
  `36/36`（原 fixture/manifest probes `20/20`、connection typed `7/7`、atomic
  publisher `3/3`、profile boundary `6/6`），negative receipt 文件 schema/tamper 另为
  `4/4`，真实 lifecycle=`5/5`，report inventory=`30/30`。
  该 r8 时点 threshold 仍为
  `diagnostic-pending`；fresh r9 diagnostic 与 subsequent formal 尚未执行，没有 aggregate
  baseline/review 或 Step 4 exit evidence；lifecycle remediation quality 通过不替代这些
  runtime gate。
- Step 4 r1：clean/pushed `bc100b0f` 的 `child-unit`=`3115/1/0/0` fail closed；修复
  focused=`9/0`、组合=`57/0`，但未产生 aggregate evidence。
- Step 4 r2：clean/pushed `0101a44a` 的 Unit=`4941/F0E0S0`；Integration=
  `312/F1E0S0` 后 fail closed。L2 修复=`1/0`、组合=`30/0`；Pivot legacy/V934 SQLite
  各=`1/0`；top identity=`51`，但未产生 aggregate evidence。
- Step 4 r3：clean/pushed `e1669329` 的 Unit=`4941/F0E0S0`；outer 因未管理
  async `tee` 仍在 child PGID 而在 `child-unit` fail closed，后续 lane/aggregate
  未执行。Cdiag 的 managed logger、Git/source provenance、typed XML/formal
  validation、绿色 status 最后发布、真实 frozen diagnostic replay 与 direct-
  single-parent freeze policy 已通过 pre-r4 main quality gate 与 54/54 identity；
- Step 4 r4：reported launch head=`ceea084c`；outer 在 `source-before` 以 `rc=2` fail
  closed，run-owned source/Git seal 与全部 lane absent。source-policy remediation focused
  static=`20/22/63/12`，top/successor identity=`54/54 + 12/12`；r4 仍为
  `excluded-from-step4-exit`。
- Step 4 r5：clean/pushed tested commit=`a35b99cb08f42817d8e75c440f18910b6961841b`；
  Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、Addon=`2/6/F0E0S0`
  后，database-state companion 因误选 frozen Step 3 authority manifest 以
  `E_AUTHORITY_MANIFEST` fail closed。database cells、external、aggregate、threshold、
  source-after 与 summary absent，r5=`excluded-from-step4-exit`，partial lanes 不可复用；
- Step 4 r6：clean/pushed tested commit=`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，
  source-before=`3,974` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；Unit=
  `681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0`、Addon=`2/6/F0E0S0`；
  database-state 以 `E_DYNAMIC_PRECONDITION` 拒绝被 repo demo container
  `foggy-demo-mysql` 占用的 frozen port `13306`。database cells、external、aggregate、
  threshold、source-after 与 summary absent；r6=`excluded-from-step4-exit`，partial lanes
  不可复用。failure class=`environment-precondition`，不是产品回归；
- Step 4 r7：clean/pushed tested commit=`528a0a541d90ef77d577e1816b392d33168cb558`，
  source-before=`3,976` / SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；四个 frozen
  ports 均无 listener 时，Unit 出现 6 suites / 11 errors，证明旧绿色隐式依赖 ambient
  `127.0.0.1:13306` MySQL/schema。outer=`child-unit / exit 1`，后续 lane、aggregate、
  threshold、source-after、summary absent；r7 与不完整 Unit exec excluded/non-reusable；
- Unit fixture quality r2=`step4-unit-fixture-quality-20260716-r2`：commit=
  `a603f839a98d99b2d7beb8379f76b4d85539328c`，source-before=`3,981 files` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`；
  lifecycle `5/5` 后，完整 Maven invocation 因全局 `spring.datasource.*` 覆盖
  `foggy-dataset-model` SQLite profile 而以 `3,115 tests / 631 errors` fail closed。
  根因=`org.sqlite.JDBC` + `jdbc:mysql://127.0.0.1:13306/...` mismatch；r2
  excluded/non-reusable；
- Unit fixture quality r3=`step4-unit-fixture-quality-20260716-r3`：tested commit=
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`，source before=after=`3,982` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；唯一
  Surefire invocation=`681 positive + 55 structural = 736 raw / 4,941 testcase / F0E0S0`；
  fixture negatives=`36/36`、receipt schema=`4/4`、closed receipt `18/18` 均为
  `v934_unit`、lifecycle=`5/5`、cleanup=`0/0/0`；r3=`passed-unit-remediation-subgate`；
- Unit fixture remediation focused：run-owned pinned MySQL 5.7 下保持
  schema before/after、temporary residue=`0/0/0`、port free；当前 adapter 只在
  `foggy-dataset` test resource 消费三个 `V934_UNIT_MYSQL57_*` placeholder，其他 profile
  保持原配置。outer/callback negatives 覆盖 underscore/dotted/hyphen Spring/custom key、
  `@argfile`、`VMOptionsFile`、`javaagent/agentlib/agentpath`；adapter inventory 在 scrubbed
  Git environment 下读取 `HEAD` tree 并禁用 replace object；
  existing-port negative preflight fail closed 且不改变外部容器。机器权威为
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`；其中 6 keys / 11 nodes 只是已知
  清单，replacement 范围是完整 Unit lane。Step 2 identity/cardinality 仅保留结构证明，
  correctness 由 fresh Step 4 replacement 取代；
- connection receipt test：root 配置 `init_connect` 后运行 Maven；Maven 返回后同一 root
  batch 必须先 disable、再按 `connection_id` SELECT。receipt 保存有序 observed user，closed
  window 内全部 non-super connection 必须为 `v934_unit`；callback 后 provisioner `foggy`
  控制面连接在窗口外；
- 当前 Unit replacement 静态/负向/lifecycle readiness 为 Unit contract `20/20`、fixture
  negatives `36/36`（原 fixture/manifest probes `20/20`、connection typed `7/7`、atomic
  publisher `3/3`、profile boundary `6/6`）、negative receipt 文件 schema/tamper 另为
  `4/4`、真实 lifecycle `5/5`、report inventory `30/30`；这些是在 r3 运行前的
  focused/static readiness，本身不是 fresh r3、fresh r8 diagnostic 或 subsequent formal；
- successor remediation regression：database-state/required-report/report-inventory 均经 Step 4
  adapters 选择 successor authority；original frozen state control 仍 fail closed。current
  r9-remediation identity：
  coverage contract diagnostic/formal=
  `58f7dfc0716539dd741595aefcd3f5b37d6456703e8e5430854c721393a923f0` /
  `cabedad99522bb1c76e8cd35eb25922a1117d445256f1e346b47687dbadbb66e`，
  successor=`14/14` / `e63b315e9607c1f7efbf3f0bffe99e0800a4c1062e9fcfa5c2c569ecf67cc5db`，
  top=`60/60` / `6be72b655b322d89763fc4871c953cd0d4bd5516206964d4cb1f8117b3133376`，
  overlay contract/tool=
  `001963c511b036d54c08abab2fcf0a0ab204b920614b35e10da809ed3f42c4d8` /
  `780e9a3d61626b8a37f85a185942de7c1862a119cd10b553962a98d5e2acd301`，
  coverage tool=`ef5b78f25ffebf48e45e363a15fb1c4bc53341488a8e703133f01bb7b2c40bef`，
  amendments=`18` / `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`，
  fixture contract/tool/Unit runner/datasource adapter=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66` /
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`，
  database/required contracts=
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- evidence/BUG：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`；
- database-state successor remediation 的历史 quality 结论保持在对应 evidence 中；当前
  Unit fixture remediation 正式实现质量闸门已在 r3 与文档 closure 后通过，
  B/H/M/L=`0/0/0/0`；该历史闸门当时仅放行 commit-push/clean HEAD→fresh r8，r8 的
  bootstrap-negative 结果由下方 superseding record 接管；
- r6 evidence/blocker：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`；
- r7 evidence/BUG：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`；
  `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`；
- r8 result：clean/pushed launch HEAD=`3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a`；在
  run-owned identity 与 lane 前因 stale Unit direct-trap lifecycle shape 于
  `bootstrap-negative` fail closed，lane/aggregate/threshold/summary absent，r8 excluded。
  remediation focused 唯一 PASS：dynamic=`9 类 / 14 case`、Unit shape/seal=`13+3`、
  Integration=`11+5`；comment/quoted heredoc、EXIT/0、early return、shadow、
  false/subshell、heredoc source/eval 与 CRLF raw-byte drift 均 expected-negative；两路
  independent quality B/H/M/L=`0/0/0/0`；
- r8 时点 next executable action: 完成本轮 authoritative closure commit/push 并证明 clean
  `HEAD == origin/main`，再在四个 frozen ports 无 listener 的 evidence window 执行
  fresh r9 all-lane diagnostic。
  threshold 仍为
  `diagnostic-pending`，`can_enter_coverage_audit=no`，Step 5、formal、coverage audit 与
  acceptance 仍关闭。只有 fresh formal、实现质量闸门与测试证据覆盖审计全部通过，9.3.4 才可按
  例外携带已登记债务签收；
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md` 必须在
  9.3.5 验收前关闭。

## Superseding r9 / r10 test delta（2026-07-17）

- r9 result：required=`773+59/5,707/F0E0S0`、Addon=`2/6`、exec=`23/48` 已到达；
  `E_CLASS_ID_MISMATCH` 发生在 exec-manifest 前，aggregate/source-after/summary absent；
  r9 excluded/non-reusable；
- exec scope positive：non-production 同名多 ID 通过；production correct ID 通过；名称看似
  generated 但属于 production universe 时仍严格校验；
- exec scope negative：production correct+forged、only-forged 均
  `E_CLASS_ID_MISMATCH`；contract scope drift 必须被 exact validator 拒绝；
- aggregate positive：同名不同 class ID 分别保留，同 ID bitmap exact OR；
- aggregate negative：missing ID=`E_AGGREGATE_CLASS_SET`、同 ID name/probe shape drift=
  `E_AGGREGATE_CLASS_SHAPE`、bitmap union drift=`E_AGGREGATE_PROBE_UNION`、manifest unique
  class-ID count drift=`E_AGGREGATE_CLASS_SET`；focused 总计=
  `17/17`；contract mutation=`21/21`；
- downstream XML identity contract：valid scope/count 正例，以及 manifest scope、aggregate
  scope、merge semantics、aggregate/manifest class-ID count 四个 stable-code drift 均触达；
  generic XML fast negative=`68/68`；
- lifecycle remediation negative 必须使用 stable expected code，semantic probes 禁用 raw seal
  并真正触达 comment/dead-context/dynamic-command guard；CRLF 单独只验证 source seal；
- fresh r10 必须从新的 clean/pushed commit 和空 run root 启动，禁止复用 r9 partials；只有
  r10 diagnostic 完整成功后才可执行 threshold freeze，随后必须 fresh formal。

当前 `can_enter_coverage_audit=no`；focused/static 绿色不得当作 r10、formal 或 Step 4 exit。
