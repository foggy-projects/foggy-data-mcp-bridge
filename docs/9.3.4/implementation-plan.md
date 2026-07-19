---
doc_role: implementation-plan
doc_purpose: Define the strict Step 1-7 implementation and verification order for 9.3.4.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-20
---

# 9.3.4 Implementation Plan

## 文档作用

- doc_type: implementation-plan
- intended_for: project-root-session / build owner / CI owner / reviewer
- purpose: 固定每步输入、代码触点、验证、证据和下一步进入条件。

## Global Entry

- predecessor: 9.3.3 `signed-off / accepted-with-risks`
- predecessor authority: `20260714T084351Z-3271604`
- inherited minimum gate:
  `docs/9.3.3/preconditions/9.3.4-A-minimum-test-gate.md`=`passed`
- protected baseline: current dirty worktree must be inventoried before any rename/POM
  change；authority clean-commit requirement applies at Step 7, not by destructive cleanup。
- predecessor rule: v931–v933 raw evidence、exact FQCN/count 和 runners保持封存；
  9.3.4 用 reviewed migration manifest + successor lanes 证明 current-source 等价回归。
- rehearsal rule: Step 5 可在 protected dirty baseline 演练，只产 candidate；Step 6
  使用 review commit 接线 CI；只有 Step 7 exact clean commit run 是 final authority。
- execution rule: one root progress, strict Step 1→7；each exit must be recorded before
  next step starts。

## Step 1 — 契约与静态库存冻结

Inputs：9.3.3 signoff、root/module POM、all test sources、workflows/scripts、DB
compose、existing JaCoCo/release paths。

Work：

1. 生成 workspace `source-inventory.tsv`（source/reactor membership/kind）和 reactor
   `execution-inventory.tsv`（report FQCN/runner/lane/variant/db/infra/step/required）；
   用 discovery-only JUnit test plan、POM variants 和已有 fresh diagnostic XML 展开
   `@Nested`/dynamic report keys，不提前依赖 external fixture。
2. 人工分类 prefix `Test*`/`IT*`、33 个 candidate `*IntegrationTest` 与 7 个
   `*IT`；记录 helper、nested/dynamic reports 和 external preflight 规则。
3. 冻结 contract schema、skip=0 目标、五库 identity/sentinel、coverage critical
   set/XML verifier、evidence layout、release Docker same-JAR contract 和 stable
   required check name。
4. 建 expected-negative harness：orphan、overlap、duplicate、missing/zero、stale
   report，全部先证明会失败。
5. 生成 9.3.1–9.3.3 predecessor migration groups/edges：每个 historical criterion/
   owning node 映射 declared-cardinality successor execution keys；1:1 默认，split/
   merge 有 relation/rationale/reviewer；POM-only coverage module 对 reactor/main-JAR/
   Launcher count 的预期 delta 单独冻结。
6. 冻结 `rename-successor-plan.tsv`：把 33 个真实 `*IntegrationTest` 展开为 62 个
   report、74 个 old→new execution keys 和 50 个 predecessor edge joins；只规划
   `IntegrationTest→IT`，所有执行语义列保持不变。
7. 记录 worktree/source baseline；不运行 rename 或生产变更。

Verification：inventory generator + independent set-difference review；candidate count
只作 diagnostic，review 后写入 frozen manifest/hash。

Exit：contract=`confirmed`；workspace source 与 reactor execution manifests/hash
冻结；nested/variant execution key 唯一；orphan/overlap=0；optional/non-reactor 均有
owner/reason；external execution 有 exact Step 3 owner；migration group declared/
observed cardinality 一致、unmapped/edge-duplicate=0；negative probes 全生效。
pre-rename baseline immutable，rename plan/Step 2 successor parent-link 已冻结。
Progress 回写 Step 1。

Recorded result（2026-07-14）：`passed`。confirmed run=
`step1-candidate-r8-20260714`；证据见
`evidence/step-1/inventory-contract-freeze-20260714.md`。Step 2 entry=`ready`。

## Step 2 — Surefire/Failsafe 全量分层

Inputs：confirmed immutable Step 1 pre-rename inventory/contract + rename successor plan。

Work：

1. root pin Surefire/Failsafe config/version；Failsafe 绑定
   `integration-test/verify`，UT/IT skip property 分离，显式覆盖
   `IT*/*IT/*ITCase/*E2E/*E2ETest`，并从 Surefire 显式排除这些模式。
2. 严格按 rename plan 处理 `*IntegrationTest`：当前 33 个真 integration→`*IT`；
   更新 references/resources，最终 ambiguous count=0，不允许 plan 外 rename。
3. 移除/禁用 active-by-default multi-db Surefire repeated execution；DB 只由 Step 3
   lane owner执行。
4. 各 module 只保留必要 owning config；helper 0-test 不得掩盖 owning module。
5. 不覆盖 Step 1 baseline；在 `scripts/v934/successor/step2/` 生成 post-rename
   candidate，以 confirmed Step 1 manifest SHA + rename-plan SHA 做 parent link，校验
   source/report/key exact delta、执行语义列不变，并独立 review/confirm。把已复核的
   59 个 zero-test top-level ClassSource container 从 positive execution 拆成 typed
   structural inventory；Step 1 baseline 保持 829/785 不变。同步 519 predecessor
   nodes 到 480 个 positive execution refs + 39 个 structural refs，50 个受影响 edges
   不得丢失。
6. 实跑 all-reactor unit 和全部 hermetic Failsafe IT；将 fresh raw report execution
   keys 与 confirmed Step 2 successor positive inventory 的 `execution_step=2` subset
   双向核对；同 variant structural raw report 另做 exact fresh/zero-metric 核对，校验
   freshness、exact testcase count、
   overlap=0。DB/Redis/other external required IT
   只允许以 Step 2 successor exact manifest 标 `deferred-to-step3`，不得标 pass。

Exit：每个 Step 2 positive execution key 恰由一个 runner 执行，structural report 只
允许在 reviewed typed set 中为 0；ambiguous/orphan/overlap/duplicate=0；missing/
positive-zero/structural-nonzero/stale negative 全失败；unit/hermetic IT actual pass；
external deferred set exact 且 predecessor typed mapping 无丢项。Progress 回写 Step 2。

Recorded result（2026-07-15）：`passed`。confirmed successor=
`step2-candidate-r8e-20260715`；Surefire/Failsafe authority=
`step2-unit-r8e-20260715` / `step2-it-r8e-20260715`；结果为
`724 positive + 59 structural / 5,205 testcase / F0/E0/S0`，Step 3 deferred=`46`。
INT/TERM/HUP 的 process 与 durable exit 分别为 `130/143/129`，失败状态不保留
summary。证据见
`evidence/step-2/step2-runner-split-exit-r8e-20260715.md`。Step 3 entry=`ready`。

## Step 3 — 五数据库与外部集成 Required Matrix

Inputs：Failsafe-only DB suites + Step 2 exact external-deferred manifest。

Work：

1. 建与 v933 base init 隔离的同构 sentinel/业务/preagg fixture manifest；在
   fresh/run-scoped storage 上自动、幂等初始化五库。
2. pin approved image/version/digest；SQLite pin JDBC artifact/version/hash。
3. 扩展 unified preflight 到 MySQL 8 与 SQL Server；使用 driver-aware physical
   coordinate，核对 catalog/schema/sentinel。
4. 将 `MultiDatabaseQueryTest`/parity owner 迁为 Failsafe IT；QueryFacade 与
   independent native oracle 精确比 rows/columns/order/values；Pivot preagg 必须真实
   执行 rewritten relation/planned SQL，不接受字符串-only 伪绿。
5. DB-specific capability：支持走 positive；不支持走明确 refusal assertion。
6. 启动并核验 Redis/其他 required external fixture，执行 Step 2 deferred
   execution-key subset 中全部 suites；不允许仍有 deferred owner。
7. 每 lane 记录 raw XML、testcases、S0、infra identity、fixture before/after；
   wrong/unavailable/mutated negative 必须失败。本 Step 只证明 correctness，明确不
   承诺 JaCoCo exec；coverage agent/rerun 归 Step 4。

Exit：SQLite/MySQL57/MySQL8/PostgreSQL15/SQLServer2022 全部 required、fresh、
S0；Step 2 deferred 中 45 个 required execution 全部 actual pass，1 个 optional LLM
保留 reviewed disposition；identity/sentinel/fixture exact；
negative probes 全生效；required inventory execution gap=0。Progress 回写 Step 3。

Recorded result（2026-07-16）：`passed`。tested commit=
`ce3d70c391c7b8bd8046fe66dde0ad568d66601e`；formal run=
`step3-required-20260716-final-r4`。Database=`29 reports / 370 testcase / F0E0S0`，
required external=`16/76/F0E0S0`，exact union=`45/446`，gap/overlap/extra=
`0/0/0`；DB state=`18/18`、Redis state=`4/4`。PreAgg Addon required companion=
`2/6/F0E0S0`，按契约不计入 45/446；optional LLM=
`reviewed-optional-excluded`。quality→coverage→feature acceptance 已按序通过，证据见
`evidence/step-3/step3-required-matrix-exit-20260716.md`。该句是 Step 3 准出时的历史
entry 结论；当前 Step 4 已由下方 superseding record 更新为
`in-progress / diagnostic-r18 PASS / threshold candidate not authorized / Pivot NULL-axis
remediation verified / pre-Cdiag quality PASS / replacement Cdiag pending`。

## Step 4 — JaCoCo Unit+IT 聚合与关键类门

Inputs：stable runner split + five DB and all required external lanes。

Work：

1. root central JaCoCo 和独立 UT/IT argLine；每 lane 唯一 exec 文件且保留 root
   UTF-8/JVM args。
2. 先冻结 parent-linked Step 4 coverage successor/schema，再用正式 agent 重新执行
   all unit、6 个 hermetic/SQLite IT variants、five-DB 的 7 个 variants、7 个 required
   external variants 与 Addon companion 的 2 个 variants，共 23 个唯一 exec；optional
   LLM 继续 reviewed/excluded。Step 2/3 无 agent 的 correctness reports 不得充当
   coverage evidence。
3. create build-only aggregate reporter，只产 aggregate XML/HTML 和 per-module
   locator；另建 versioned verifier 解析 XML，fail-close expected module/package/
   class/counter/threshold。
4. 将 model owning-module 0.77/0.62 check 改为消费其 UT+IT merged exec；不对空
   aggregate reporter 使用 `jacoco:check`。
5. 首次跑 diagnostic coverage；审查 source/classes/session/exec provenance 后冻结
   aggregate baseline。
6. 对 contract critical set 计算 candidate 0.80/0.70 floor；不足补测试，不以
   exclusion/降阈值过门。
7. expected-negative：missing/empty exec/XML、missing expected class/package、wrong
   class/source SHA、zero counter、低门、阈值被私改、exclusion drift 必须失败。

Step 4 authority layout 固定为
`target/v934-step4-coverage/runs/<run-id>/`；run-owned exec 位于 `exec/`，manifest=
`exec-manifest.json`，aggregate XML/HTML 位于 `report/`，candidate/final threshold 只能写
`scripts/v934/step4/coverage-thresholds.json`。Step 1 已冻结的
`scripts/v934/coverage-thresholds.json` 只作为 parent policy，不得原地改写。

Exit：reviewed frozen baseline、critical gates 和 exec manifest 全部 pass；
aggregate XML verifier + model merged-exec check实际生效；aggregate/per-module reports
可定位；canonical lane runners 此后默认一次执行即产唯一 exec，coverage verifier
不重跑 suite；negative probes 生效。Progress 回写 Step 4。

Recorded progress（2026-07-16，superseding bootstrap wording）：

- state=`in-progress / diagnostic-ready`，不是 `passed`；
- static scope exact=`23 exec / 48 sessions`，required report overlay=
  `773 positive + 59 structural / 5,707 testcase / F0E0S0`，Addon companion 单列
  `2/6`；
- fail-closed readiness probes：raw contract=`8/8`、effective POM=`4/4`、toolchain
  receipt=`5/5`、report inventory=`27/27`、Step 2 derived view=`12/12`、successor
  overlay=`8/8`；
- toolchain receipt 已约束 Step 1 raw 工具版本、compiler/JaCoCo/test ASM=
  `9.6/9.7/9.7.1` 和 24 个 production module effective compiler；
- report amendment exact=`10 rows = 4 new + 6 changed`，SHA-256=
  `5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504`；
  successor declared amendments=`15`；local Step 4 `SHA256SUMS` 已生成并通过 exact
  49 项校验，manifest SHA-256=
  `c735e8c1f7b74d72afe2d1d1872128d11a16acbd7373c750e59709624560106e`；
- threshold 仍为 `diagnostic-pending`，尚无 all-lane aggregate baseline/review 或
  Step 4 exit evidence。

Diagnostic r1 result（2026-07-16，supersedes the unexecuted wording above）：

- clean/pushed tested HEAD=`bc100b0f63bd3ff62d1105611dae41741790aedd`，run=
  `step4-coverage-20260716-diagnostic-r1`；outer runner 在 `child-unit` 正确 fail closed，
  Unit=`3115 tests / 1 failure / 0 errors / 0 skipped`，未进入 aggregate/report/threshold；
- root cause：`PreAggregationDataValidationTest#testDetectCorruptedPreAggData` 腐化
  `preagg_daily_product_sales`，而默认 hybrid 在 watermark=null 时跳过 daily，查询实际命中
  `preagg_monthly_category_sales`；同类另有 raw-vs-raw 与 nullable/empty 伪绿；
- fix：腐化探针精确 update/restore monthly 一行并先断言 hit/name，三个 snapshot 对比显式
  关闭 hybrid，强制命中 `daily_product_sales`、`daily_product_sales`、
  `daily_customer_channel_sales`，并拒绝 null/empty/non-numeric fixture；生产 Matcher、
  threshold 与 exclusion 不变；
- verification：focused class=`9/F0E0S0`，DataValidation+EdgeCase+Matcher+
  RequirementBuilder=`57/F0E0S0`，corruption delta=`1000.00`，source SHA-256=
  `affb6e415c1770eae7c9ab6f3505ac67acfe03eac1461bd2a48addf3ee790623`；workitem=
  `docs/9.3.4/workitems/BUG-step4-preagg-validation-wrong-table.md`；
- next entry：先提交并推送修复，确认新 `HEAD == origin/main` 且 worktree clean，再运行 r2。
  r1 不得拼接或转绿；在 r2、人工 aggregate review 与 confirmed thresholds 完成前，
  Step 5 entry 继续关闭。

Diagnostic r2 result and remediation（2026-07-16，supersedes current next entry）：

- clean/pushed tested HEAD=`0101a44a07784bf6b484d490c7fb508727fbab70`，run=
  `step4-coverage-20260716-diagnostic-r2`；Unit authority=
  `681 execution + 55 structural / 4,941 testcase / F0E0S0`；Integration 已执行
  caffeine=`2/F0E0S0`、hermetic=`3/F0E0S0`、sqlite-broad=`307/F1E0S0`，合计
  `312/F1E0S0`；outer 在 `child-integration` fail closed，summary absent，未进入
  database/external/Addon/aggregate/threshold；
- root cause：`PreAggregationL2CacheIT` 验证 post-rewrite L2 identity，却隐式继承新的
  hybrid=true 默认；SQLite fixture 没有 watermark 时生产 Matcher 正确回退 raw，测试仍要求
  `preAggHit=true`。修复仅为 snapshot-only fixture 显式设置 hybrid=false，并断言 exact
  name/table、raw negative、lookup/write key、二次 hit 与单次 write；
- L2 verification：最终 source SHA-256=
  `bb5d6884401579447382587861e197feac2884ab701065d7826fe7a676e0c313`，focused=
  `1/F0E0S0`，`PreAggregationIT + PreAggregationL2CacheIT=30/F0E0S0`；
- proactive hybrid audit：`PivotSqlParityIT` legacy fallback 在同一方法精确执行两次均为
  `1/F1E0S0`；只在 legacy 分支关闭 hybrid 并收紧 exact branch name/table/raw negative 后，
  legacy 与 V934 SQLite 各 `1/F0E0S0`，最终 source SHA-256=
  `5c6dcd3b4afba4d93a93c1af47cc4484a1dfc9976da92669bfbcc4529ede6155`；五数据库 FULL
  分支继续使用 production 默认；
- identity refresh：report amendment exact=`11 rows = 4 new + 7 changed`，SHA-256=
  `937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2`；declared
  amendments=`17`，SHA-256=
  `be9a2d553499f799d5dc81cee353397799ad3f01d2923c6aeccb82fdb9bd7548`；top manifest
  exact=`51`，SHA-256=`348ade918a5020b9b65b9fb93e4bb7034e73f197c8545c7cbbfeb3d34d044ac1`；
  successor manifest exact=`12`，SHA-256=
  `6ac8a24dd983c1929f6d21430f57adca503893e69b368b37a08731f5a5355948`；required totals
  保持 `773/59/5707`、exec/session 保持 `23/48`，static positives/negatives 全绿；
- evidence：
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r2-fail-closed-20260716.md` 与两个
  fixture workitem。r1/r2/focused XML 均不是 Step 4 exit evidence；
- next entry：提交并推送最终修复与 identity，确认新 `HEAD == origin/main` 且 worktree
  clean，再运行唯一 r3 all-lane diagnostic。threshold=`diagnostic-pending`、
  `can_enter_coverage_audit=no`；Step 5 entry 继续关闭。

Diagnostic r3 result and Cdiag hardening（2026-07-16，supersedes current next entry）：

- clean/pushed tested HEAD=`e16693297239f2a861f3b93b3de60c1bb783bda0`，run=
  `step4-coverage-20260716-diagnostic-r3`；contract/successor/toolchain/Step 2 view 与
  fresh class universe 通过，Unit=`681 positive + 55 structural / 4,941 testcase /
  F0E0S0`；outer 随后在 `child-unit` 检测到 live process-group member 并 fail
  closed，Integration/database/external/Addon/aggregate/threshold 均未执行；
- immutable failed evidence=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`，
  bug record=
  `docs/9.3.4/workitems/BUG-step4-child-run-log-tee-residue-race.md`；r3 Unit 绿色
  不得与 r1/r2/focused 证据拼接；
- root cause：Unit/Integration 的 `exec > >(tee ...)` 是未捕获、未 wait 的异步
  logger；child leader 返回后 `tee` 仍可在同 PGID 排空，outer 的组残留检测
  因而正确拒绝该 run；
- managed logging implementation：Unit/Integration 改用共享 FIFO logger，显式保存
  logger PID，关闭写端并 bounded wait；只有 logger flush/reap 成功后才可发布
  child green。`run_log_lifecycle_negative_test.sh` 覆盖 slow/nonzero/timeout/exit/
  clean-group/persistent-residue，但该 focused 实现结果不替代 fresh r4；
- process identity implementation：child ready receipt 绑定 PID/PGID/SID/starttime/boot-id，
  ready/completion receipt 按 exact schema、mode/link/inode/hash 校验，清理前持久化 bytes-safe
  member snapshot；信号与回收依据身份而不对可重用的裸 PID 操作；
- typed XML/formal implementation：`coverage_xml_negative_tool.py` 与扩展的
  `coverage_xml_tool.py` 对 child lifecycle、formalization delta、canonical gate/candidate/
  final path 和成功 status 执行类型化复算；绿色 `run-status.env` 必须是全部
  证据验证后的最后一次不可逆原子发布，禁止先发绿再校验；
- formal freeze policy：formal 必须对 confirmed threshold 所指向的真实
  diagnostic run 执行 frozen replay，从 diagnostic commit 取回当时
  threshold/contract blob 并重算原 run，不接受伪造的合成 run id。阈值冻结
  `Cfreeze` 只允许一个直接单父提交，拒绝 merge、多提交、shallow、
  replace refs 或 grafts；fresh formal 只允许以该 direct child 作为权威输入；
- current entry：Cdiag 最终快测、pre-r4 正式实现质量闸门和 54/54
  identity/manifest 级联已通过；下一动作是 commit/push 并确认 clean
  `HEAD == origin/main`，然后启动 fresh r4 all-lane diagnostic。threshold
  仍为 `diagnostic-pending`，Step 5、coverage audit、acceptance 和 Step 4 pass 均继续关闭。

Diagnostic r4 result and source-policy remediation（2026-07-16，supersedes current next
entry）：

- reported launch head=`ceea084ca25a9d679ba128e3f6bd50a63322c112`，run=
  `step4-coverage-20260716-diagnostic-r4`；该值是调用方启动前报告，不是 run-owned Git
  seal。outer 在 `source-before` 以 exit code `2` fail closed，run-owned Git/source
  identity、全部 lane、aggregate、threshold 与 summary 均 absent；r4=
  `excluded-from-step4-exit`；
- root cause：`core.fileMode=false` checkout 中 `3,968` 个 tracked file 有 `3,452` 个
  Git `100644`→executable-worktree row；旧 validator 错把 worktree executable bit 等同于
  authoritative Git mode，误拒绝 clean source；
- remediation：Git HEAD/index path+mode+blob 保持 exact；worktree 独立验证 regular file、
  exact content、owner/private-primary-group、single-link、stable stat，拒绝 world-write、
  special-bit、hardlink 与内容/identity 漂移；security Git 调用关闭 fsmonitor/untracked-cache
  并绑定 ordinary index flags；tracked FIFO 在 worktree-aware Git 前 preflight fail-fast，
  before/after raw stat identity 拒绝 Git-clean-equivalent concurrent rewrite；
- clean-equivalence：source seal 清除 ambient/global Git clean 配置并显式复算 raw 与
  CRLF-input 两个 candidate，使真实 CRLF worktree 在 HEAD/index clean-equivalent 时通过；
  HEAD-fixed attributes 若声明 external clean filter，则在任何 worktree-aware Git
  hash/driver hook 执行前 fail closed，negative 证明 hook 未执行；
- static verification：contract=`20/20`、source identity=`22/22`、XML=`63/63`、overlay=
  `12/12`；declared amendments SHA 保持
  `1e4f15c9e403d454fe07404e45b1226eae94f70faa154433a8db39531a305b47`；coverage contract
  diagnostic/formal=
  `5f4b49fd161b4f381a4f8c2238583eb56f27b577973ff93ce0659d84cca75f1d` /
  `58c3479666d0b786ea0ad8327b72b05c9e006dfdb516eacce9098ea83ef4c405`；successor
  `12/12` SHA=`751018ac7c2357cface77dd125c5edc757ad488a500a3c8d9eece0354767381a`；
  top `54/54` SHA=`ebda814b1278f92cf1ba7dc202170e4a77cb7e1f4485e6cb1375d152592a76d0`；
  coverage tool / contract-negative / XML tool SHA=
  `07a36a2be8edc0afc0ab1031b052c2208a4e32769c4cdb475a397f81e6121ac9` /
  `732d799619461a4b49c8e9bfbb0a3487b107c36110b9e55cd91a405352d0ddb0` /
  `b837314ac4166eeeab94124b53e4f776dcdf8095a3b3915e14e45b81d910d439`；overlay contract /
  overlay tool / outer SHA=
  `2d4fe0024caac33199e2ccf87289dd9a262302d3faabad6b038adadb2b2974cb` /
  `a16aadf9c4d540cda8b95d1fc1ded94cf420aa0cfe5a1653b8f90d4cb72e0f51` /
  `254c7603554787ca38d880ac607f7dd4a21ae89064674490858245f0824951c9`；
- quality：最终字节 review=`ready-with-risks`，B/H/M/L=`0/0/0/2`；两项 Low 均 accepted：
  `/usr/bin/echo` 平台前提漂移会 fail closed；同 UID 视为 build authority，未来更强隔离改用
  readonly snapshot/独立 checkout。当前只放行 amend/push + fresh r5；
- current entry：amend/push 并证明 clean `HEAD == origin/main`，随后以唯一新 run id
  执行 fresh r5。threshold=
  `diagnostic-pending`，`can_enter_coverage_audit=no`；Step 5、acceptance 与 9.3.5 保持关闭。

Diagnostic r5 result and successor authority remediation（2026-07-16，supersedes current
entry）：

- tested commit=`a35b99cb08f42817d8e75c440f18910b6961841b`，run=
  `step4-coverage-20260716-diagnostic-r5`；run-owned source seal 已建立；
- r5 完成 Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0` 和
  Addon=`2/6/F0E0S0` 后，database-state companion 因 frozen Step 3 authority manifest
  的 model POM SHA stale 而以 `E_AUTHORITY_MANIFEST` fail closed。database cells、
  external、aggregate、threshold、source-after 与 summary absent；r5=
  `excluded-from-step4-exit`，partial lanes 不得复用；
- implementation：保持 frozen Step 3 字节不变，新增 successor
  `database_state_negative_tool.py` 与 `step3_required_report_tool.py`；database runner、
  required runner 与 report_inventory 均选择 successor adapter，非 state verifier
  argv 保持不变；
- current static identity：coverage contract diagnostic/formal=
  `16677d3ae64a7d24aa5796e7c1bbb8ca5af347d6843878471a7e48bdc52c82af` /
  `d8e7efa775d021d42485f1ffa6cb51a98a3f3f6662b1793e6b06f69852d12463`；
  successor=`14/14` / `9fa9ddb23aa36c48961e54393f1fe747bf5d0433645cb1a0529e607db4f211cb`；
  top=`56/56` / `be8c4c9c1698674917f1115388d3e7b6a6078d698daf52cb4fa55916166460f9`；
  overlay contract/tool=
  `cd691d3d91540dd6ddba0045648493d16feaf9ebf3175da3b9ad15b0e399aadd` /
  `4df218807847beb789dcf1ef748e13bf21f39da071e4bcf7337fe97b78f8c84a`；
  coverage tool=`bf317dd09bb2f909773dba602ab00037acf112b835a166bfd64ef9709045179a`；
  amendments=`17` / `187aac883460b259cd002f6c12bb72d8d9824d1e4dd8f12a12959f6866bfccfe`；
  database/required contracts=
  `553dabf2b4c266b531fb4ce36f4a498dce223b6449106274a3a2b103ccb775ea` /
  `893ac03231cb4f6fd8ae427c01aa3f9f04267c96e3945814b9b70a3445a58af5`；
- record=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r5-fail-closed-20260716.md`，BUG=
  `docs/9.3.4/workitems/BUG-step4-database-state-successor-authority-manifest.md`；
- current entry：full static/quality 已通过，B/H/M/L=`0/0/0/0`；commit/push 并证明 clean
  `HEAD == origin/main`，再执行 fresh r6。threshold=`diagnostic-pending`，
  `can_enter_coverage_audit=no`；Step 5、acceptance 与 9.3.5 保持关闭。

Diagnostic r6 environment result（2026-07-16，supersedes current entry）：

- clean/pushed tested HEAD=`eb10d9c10a73f379db9ce4fa3d05ff340b489fd4`，run=
  `step4-coverage-20260716-diagnostic-r6`；source-before=`3,974 files` / SHA-256=
  `3a4322e8442646c58ed522c0d4fb52071b3219cc1c2f204c209299bd8acc1cff`；
- r6 完成 Unit=`681+55/4,941/F0E0S0`、Integration=`47+4/320/F0E0S0` 和
  Addon=`2/6/F0E0S0` 后，database-state dynamic precondition 发现 frozen MySQL 5.7
  port `13306` 已被 repo demo container `foggy-demo-mysql` 占用，以
  `E_DYNAMIC_PRECONDITION` fail closed；outer=`failed / child-step3-required / exit 1`；
- failure classification=`environment-precondition`，不是产品回归。r6 已越过 r5 的
  `E_AUTHORITY_MANIFEST` 边界，successor selector remediation 没有复发；runner 未启动、
  复用或改变既有 listener；run-owned cleanup residue=`0/0/0`；
- database cells、external、aggregate、threshold、source-after 与 summary absent；r6=
  `excluded-from-step4-exit`，Unit/Integration/Addon partial lanes 禁止拼接或复用；
- records=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r6-environment-fail-closed-20260716.md`、
  `docs/9.3.4/workitems/BLOCKER-step4-r6-mysql57-port-occupation.md`；
- current entry：repo demo DB 容器已在 r7 evidence window 外以保留原 ID 的方式停止，四个
  frozen ports 已证明无 listener；随后以全新 `step4-coverage-20260716-diagnostic-r7`
  从 source seal 开始。r6 仍为历史 excluded evidence。

Diagnostic r7 Unit hermeticity result（2026-07-16，supersedes current entry）：

- clean/pushed tested HEAD=`528a0a541d90ef77d577e1816b392d33168cb558`，run=
  `step4-coverage-20260716-diagnostic-r7`；source-before=`3,976 files` / SHA-256=
  `b3fc04ee0d16a7a81f5e9697b10b5edeaafec0f59cd5dbec1e65625381c3fe43`；
- 四个 frozen ports 均无 listener 后，Unit 在 `foggy-dataset` 暴露 6 suites / 11 errors；
  共同根因是隐式连接 ambient `127.0.0.1:13306`，`FDialectTest` 的 2 个 NPE 为连接失败
  次生错误。outer=`failed / child-unit / exit 1`；Integration、Addon、database、external、
  aggregate、threshold、source-after 与 summary absent；r7=excluded/non-reusable；
- remediation：Unit runner 以前台同步 callback 复用 frozen MySQL 5.7 provisioner，派生唯一
  child/project，固定 image/database/port/minimal schema，Maven 前后封存 schema，完成后删除
  container/volume/network；Unit/outer/Step 3 三层复验 residue 与 port；
- replacement semantics：fresh Step 4 以 run-owned MySQL 5.7 替换完整 Unit lane，仍只有一个
  Unit Maven invocation、`681+55/4,941`、全局 `23 exec / 48 sessions`。已知隐藏依赖清单是
  `6 reports / 11 testcase nodes`，不是其他 Unit 测试无 DB 访问的证明；Step 2
  identity/cardinality 继续保留结构含义，但其 Unit 正确性绿色不复用。机器例外契约=
  `scripts/v934/step4/unit-mysql57-fixture-contract.json`，迁移债务=
  `docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md`；
- focused/static evidence：actual=`681+55/4,941/F0E0S0`，schema before=after，cleanup=
  `0/0/0`、port free；existing-port negative fail closed 且不改变 demo container。Unit
  negative receipt=`27/27`（原 fixture/manifest schema/tamper=`20/20`、connection receipt
  typed=`4/4`、atomic publisher=`3/3`），negative receipt schema tamper=`4/4`；真实
  lifecycle=`5/5`，report inventory negatives=`30/30`，successor overlay=`12/12`；
- publication closure：fixture hardening 后 top manifest=`59/59` /
  `2a52dbf591238a9c163c0774014e1407dadd4d5037a62a4ce2d0c3af931d6aa7`，successor=
  `14/14` / `bd8d1f1ef97db15b1fb08548c52c6be3fa60d82e848d5741b6a36f1f828924db`，
  coverage amendment=`12 rows / 4 new + 8 changed` /
  `998ae49927721576c26327b8477010b0238843565e6afdbc70987e97544a028c`；静态复验已通过，
  threshold 仍为 `diagnostic-pending`；
- records=
  `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r7-unit-hidden-mysql-fail-closed-20260716.md`、
  `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`；
- current entry：正式实现质量闸门和 fresh r8 均 pending；质量通过后再 commit/push，证明
  clean `HEAD == origin/main`，并以 fresh r8 重跑全部 lane。`can_enter_coverage_audit=no`；
  Step 5、acceptance 与 9.3.5 保持关闭。

Unit remediation r2 profile-isolation result（2026-07-16，supersedes current entry）：

- immutable failed run=`step4-unit-fixture-quality-20260716-r2`，tested commit=
  `a603f839a98d99b2d7beb8379f76b4d85539328c`，source-before=`3,981 files` /
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`；真实 lifecycle
  negatives=`5/5` 后，`foggy-dataset-model` Unit=
  `3,115 tests / 0 failures / 631 errors / 0 skipped`；
- root cause：callback 的全 reactor `-Dspring.datasource.*` 把 MySQL URL 覆盖进默认
  `sqlite` profile，但 profile 仍提供 `org.sqlite.JDBC`，首因是 SQLite driver 明确拒绝
  `jdbc:mysql`。Unit final manifest/summary、fixture after/receipt、aggregate 与 threshold
  全部 absent，r2=`excluded/non-reusable`；
- cleanup：child=`unit-mysql57-90da4977dc197f81` 的 container/volume/network=`0/0/0`、
  `13306=free`；原 demo MySQL exact container 已在 evidence window 外恢复为
  `running/healthy`；
- minimal repair：只有 `foggy-dataset` test resource 通过
  `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholders 消费受控 credential；callback
  移除全部全局 Spring datasource 参数，其他 SQLite/显式 profile 不变。outer/callback
  双层拒绝 underscore/dotted/hyphen Spring/custom key 及 `@argfile`、`VMOptionsFile`、
  `javaagent/agentlib/agentpath` 间接注入；adapter path/hash 与唯一 consumer 范围由 scrubbed
  Git environment、`HEAD` tree、no-replace object inventory 约束。closed Unit Maven
  observation window 从配置 `init_connect` 到 Maven 返回后同一 root batch 先 disable 再
  SELECT；receipt 保存有序 `connection_id + observed user`，窗口内全部 non-super 连接必须
  使用 `v934_unit`，callback 后 provisioner `foggy` 控制面位于窗口外；
- repaired static closure：Unit negatives=`36/36`（`20+7+3+profile 6`）、negative receipt
  schema=`4/4`、lifecycle=`5/5`、report inventory=`30/30`；top manifest=`60/60` /
  `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`，successor=
  `14/14` / `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
  diagnostic/formal contract=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
  fixture contract/tool/runner=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345` /
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66`；
  declared amendments=`18` /
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
  overlay contract/tool=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`；
- record=
  `docs/9.3.4/evidence/step-4/step4-unit-profile-isolation-r2-fail-closed-20260716.md`；
- current entry：fresh Unit remediation r3、正式 remediation quality、commit/push/clean HEAD
  与 fresh all-lane r8 均 pending。上述静态结果不是质量或 r8 通过；
  `can_enter_coverage_audit=no`，Step 5、acceptance 与 9.3.5 保持关闭。

Unit remediation r3 result（2026-07-17，supersedes current entry）：

- run=`step4-unit-fixture-quality-20260716-r3`，tested commit=
  `50161a0a869430e353f3933d9bb00dda59d9c4b1`，source before=after=`3,982 files` /
  `1db06cc18bb86c288ffa79e7094e3b9e509d866ddc23b6c93bcaaa0422b99eb2`；
- 唯一 Surefire invocation=`681 positive + 55 structural = 736 raw / 4,941 testcase /
  F0E0S0`；fixture negatives=`36/36`、receipt schema=`4/4`、closed receipt=`18/18`
  restricted `v934_unit`、lifecycle=`5/5`、run-owned cleanup=`0/0/0`；
- demo MySQL exact container 仅在 evidence window 外恢复为同一 ID `running/healthy`；
- independent evidence review 与 remediation formal quality 均为 PASS，最终
  B/H/M/L=`0/0/0/0`；record=
  `docs/9.3.4/evidence/step-4/step4-unit-fixture-quality-r3-pass-20260717.md`；
- r8 superseding result：`step4-coverage-20260716-diagnostic-r8` 在 clean/pushed
  `3a3dd21a9aa956f0bedfffcf648ebb1cac0b756a` 启动，contract/overlay/authority 与 coverage
  negatives 先通过，但 lifecycle static validator 错把 fixture-aware Unit EXIT wrapper
  当成缺少旧 direct finalizer token，于 `bootstrap-negative` fail closed。run-owned
  Git/source identity、lane、aggregate、threshold 与 summary absent；r8=
  `excluded/non-reusable`；immutable record/BUG 分别为
  `evidence/step-4/step4-coverage-diagnostic-r8-bootstrap-negative-contract-drift-fail-closed-20260717.md`、
  `workitems/BUG-step4-unit-lifecycle-static-contract-drift.md`；
- lifecycle remediation：Unit/Integration 分类型 executable contract、critical slice、
  canonical reference 与 raw-byte whole-runner seal 已落地；dynamic=`9 类 / 14 case`，Unit
  shape/source-seal=`13/13 + 3/3`，Integration=`11/11 + 5/5`，两路独立质量 B/H/M/L=
  `0/0/0/0`；tool/top=`8dcc679c2762ff8908b3bc26e8dfb0553a083eb75003dd80366fd82e78d8ed9b` /
  `0c4e6c18f4af0a2c35418604ce80d20828ceb4c275375b7d12461b4683553a81`；
- current entry：commit/push 本轮权威 closure 并证明 clean `HEAD == origin/main`，再以新
  run ID 运行 fresh all-lane r9。threshold=`diagnostic-pending`，
  `can_enter_coverage_audit=no`；Step 5、formal、coverage audit、acceptance 与 9.3.5
  仍关闭。

## Step 5 — 单一 Authority Runner Rehearsal 与 Immutable Candidate

Inputs：Steps 1–4 stable lanes。

Work：

1. 以 Batch 7 runner 的锁/fresh count/DB/source/JAR/two-layer hash 设计为基础，
   建 `verify-v934-release-gate.sh`；只复用设计，不要求/修改 v933 exact runner。
2. 严格串行/受控并行编排 inventory、unit、integration、five DB、migration-backed
   9.3.1–9.3.3 successor regression、package/Launcher，最后只做 coverage
   collect/check，不重复执行 suites。
3. 在 protected dirty baseline 下核对 source/worktree before/after 不漂移、DB
   fixture、report inventory；package JAR 与本 candidate source/classes绑定。不得
   为 clean 运行破坏性清理。
4. 生成 run root、inner/outer manifest、deterministic archive + digest；上传模型
   先在本地模拟 download-and-verify。
5. 用 runtime-only release Dockerfile 直接嵌入 candidate JAR，从 image 回读
   `/app/app.jar` SHA；source-building `-DskipTests` Dockerfile 不属于 release path。
6. expected-negative：external skip flag、tamper、stale/missing XML、skip drift、
   historical mapping gap、hash mismatch、failed candidate pointer、JAR/source/
   image mismatch。

Exit：单一 exact candidate 可独立复算；状态明确 diagnostic/candidate；failed run
不更新 candidate pointer，任何 candidate 都不更新 final authority pointer；evidence
archive/digest/download/image-JAR verify pass。Progress 回写 Step 5。

## Step 6 — PR/Main/Release CI 接线

Inputs：rehearsed authority commands/candidate artifacts。

Work：

1. 建 reusable workflow，jobs 分为 inventory+unit、SQLite broad integration、
   five-DB contract matrix、coverage collector/check、package/evidence 和 always-run
   aggregator；coverage job 只下载各 lane exec，不重复执行测试；
   两个 SQLite job 消费 confirmed Step 2 successor 中冻结的互斥 FQCN manifests，
   并保留其 Step 1 parent/rename-plan linkage。
2. 五库每个 matrix cell 上传带 db kind/SHA/run/attempt 的 lane artifact；collector
   断言 exact set/cardinality=`5`、XML/manifest freshness。aggregator 再读取 collector
   与其他 required job results，拒绝 failure/skipped/cancelled；check name 稳定。
3. PR/main 实际触发并保留 run evidence；配置/核对 branch protection required
   check。无权限时记录 external blocker，不宣称完成。
4. 收窄/替换 legacy pivot/model-lifecycle workflow authority，禁止重复执行或
   partial green 被误当 release gate。
5. release 在同一 workflow run 调用/依赖 full reusable gate，校验 tag SHA，下载已测
   JAR/evidence，复验 digest；GitHub release 直接发布该 JAR+archive+digests。
6. Docker job 下载同一 JAR，使用 runtime-only release Dockerfile COPY；push 前回读
   image `/app/app.jar` SHA；删除 release path 中 skip-external/skip-test/source rebuild。

Exit：PR/main stable required check 实际通过；缺任一 DB artifact、重复/wrong-kind
artifact 或任一 required job skipped/cancelled 时 aggregator expected-fail；release
dry-run 的 GitHub JAR 与 image embedded JAR 均等于 gate artifact，missing upload
error。Progress 回写 Step 6。

## Step 7 — Clean-Commit 权威回放与后置门

Inputs：Steps 1–6 completed, clean commit, required CI configured。

Work：

1. 在 exact clean commit 执行完整 v934 local/CI authority；不得调用旧 exact runner
   冒充 current pass，也不得拼接旧/candidate run。
2. 从 CI 下载 archive/JAR/image，独立复算 migration coverage、XML/count/skip/
   coverage/DB/source/JAR/image/hash。
3. 更新 requirement、contract、inventory、test evidence、progress 和 roadmap。
4. 严格执行 implementation self-check → formal quality gate → coverage audit →
   version acceptance。
5. 只有所有 critical gate 通过才标 9.3.4 signed-off，并把 9.3.5 标 ready；
   不实施 9.3.5/9.4.0。

Exit：五库/external、coverage、successor regression、package、JAR=image、immutable
evidence、required CI 全绿；无 blocker/high；9.3.4 version signoff完成。签收记录必须
保存 `tested_commit`；authority 后的纯文档回写不冒充新的测试 commit。

## Stop Conditions

- inventory 无法做到唯一 runner/lane；
- predecessor node 无法归入唯一 mapping group，或 successor edge set 不满足 declared
  cardinality；
- DB 无法提供可证明的 product/version/physical identity/sentinel；
- coverage 只能靠降阈值/扩大 exclusion 过门；
- CI 平台无法使 required aggregator 对 skipped/cancelled fail；
- release 无法复用已测 artifact；
- Docker image 无法证明嵌入同一 tested JAR；
- 修复需要 9.3.5 public API 或 9.4.0 production module boundary。

遇到 stop condition：先回写 progress/workitem；不越过当前 Step，不伪造绿色。

## Step 4 r9 remediation addendum（2026-07-17）

r9 在完成全部 required lane 后于 exec inventory fail closed。进入 fresh r10 前增加以下
顺序约束：

1. 封存 r9 immutable failure/absence/cleanup；不得复用 r9 raw exec、lane candidate 或
   report inventory 形成新 run；
2. 修复 class-ID scope：fresh production universe strict match，raw execution data 全保留，
   aggregate 按 JaCoCo ID exact union；同步 contract、manifest、provenance、XML consumer、
   successor dual workflow hash 与 negative probes；
3. 闭合 lifecycle semantic validator bypass：semantic guard 与 raw byte seal 分开取证，
   negative 必须断言稳定 code，outer/library comment/dead context 与 dynamic `trap` 拼接必须
   fail closed；
4. implementation self-check → 两路独立 formal quality；B/H/M 必须为 0；
5. commit/push 后确认 clean `HEAD == origin/main`，停止四个 exact demo DB 容器并确认 frozen
   ports free，使用全新 run ID 执行 r10；退出后恢复 exact containers；
6. 仅当 r10 完整发布 source-after、exec manifest、aggregate/provenance/XML、observation、
   summary 且 cleanup=0/0/0，才 review exact-observed thresholds；freeze commit 必须是 r10
   tested commit 的 direct single parent child，变更范围遵守 frozen allowlist；
7. freeze 后执行 fresh formal；再做最终 implementation quality、test coverage audit 与
   acceptance。任一步失败继续 fail closed，不开始 Step 5。

## Step 4 r10 remediation addendum（2026-07-17）

r10 已完成全部 lane、aggregate 与 coverage observation，但在最终 sensitive scan fail closed。
进入 fresh r11 前增加以下顺序约束：

1. 封存 r10 immutable failure、absence、cleanup 与 external restoration；r10 全部 partial
   lane/exec/XML/observation 均不得拼接或冻结 threshold；
2. 五条 sensitive pattern、全 run-root extension set 与 fail-closed policy 保持不变；仅修复
   `DemoSecurityIdentityResolver` 的 credential-shaped producer label；
3. outer bootstrap-negative 最前执行不落盘、不回显的内存 probe；旧 label、env、Bearer、
   password、API key、credential URI、CLI password 必须命中，修复后 label 与 null 字段必须
   安全，`rg rc>1` 必须 fail closed；bootstrap 与最终扫描复用唯一 pattern 数组；
4. launcher request smoke、bash syntax、exact manifest、coverage contract negatives、successor
   overlay negatives、diff check 全部通过后，执行独立 formal remediation quality；
5. B/H/M=0 后 commit/push 并证明 clean `HEAD == origin/main`；停止四个 exact demo DB
   containers、确认 frozen ports free，以全新 run ID 执行 r11，退出后恢复 exact IDs；
6. 只有 r11 完整发布 sensitive scan、summary、cleanup 与 exact observation 后才允许 threshold
   review/freeze；后续 direct-child freeze、fresh formal 与 post-gates 顺序不变。

## Step 4 r11 remediation addendum（2026-07-17）

r11 在任何测试 lane 启动前，于 `bootstrap-negative` 以 stable `E_SOURCE_SEAL` 拒绝 outer
actual raw=`57f5da9a23c4973beef54a6bfd303c3dfd38fccb03a7bc2cadadbfaa3206f649`
与 lifecycle frozen raw=
`02a920d91d1b8792cad47d65ce860352a8e9ecf39106f4489a714df01888dbaa` 的漂移。
r11=`failed / excluded / non-reusable`，零 lane，所有 bootstrap 前置绿色不得拼接。进入 fresh
r12 前增加以下顺序约束：

1. 封存 r11 run-status、cleanup、partial lifecycle log 与 absence boundary；r11 不得贡献
   exec、XML、coverage observation、threshold 或 Step 4 exit claim；
2. 在 outer 分配 run root、建立 source seal 或启动 lane 前，执行 Unit、Integration、outer、
   lifecycle library 的 early four-way binding；每个独立 frozen constant 必须与 canonical
   raw bytes、top manifest exact row 同值，失败统一为 stable `E_SOURCE_SEAL_BINDING`；
3. focused regression 必须包含 canonical positive=`1` 与六类 negative：outer+manifest
   refresh/nested stale、outer-only drift、valid-64 nested-only wrong、missing、duplicate、
   invalid-format constant；同时保留 outer raw CRLF mutation 与 executable no-op
   semantic mutation，分别触达 raw seal 和 executable-stream seal；
4. 当前 identity 固定为 outer raw=
   `90b4b979e55c17243644cce186767a4647ce79c85b431adcb415bddd18cc1cec`、semantic=
   `065211912aab5227125ef02f40e2965fce7ff5060df5c7b91a902c4ad4f34cae`、lifecycle tool=
   `61bf7b990bdef6e0d75c53010644bcc6d1525a67119cd36c5f82eeb911e005fc`、top manifest=
   `a9b105ce2f8f640dfa09863e797697bcf9892a7b0fa68b38f83b5bbd7435afb4`；future edit 必须
   同步四方 binding，否则 preflight fail closed；
5. safety review 提出的 preflight TOCTOU Medium 必须由全部输入的 descriptor-bound strict
   read（`O_NOFOLLOW`、`fstat`、fd read、post-`lstat` identity）关闭；错误仍保持
   `E_SOURCE_SEAL_BINDING`。focused lifecycle=`PASS`、top manifest=`60/60`、successor=
   `14/14`、coverage contract=`21/21`、overlay=`12/12`；两路 post-fix implementation review
   与独立 docs/status review 最终 B/H/M/L=`0/0/0/0`，已放行 commit/push；
6. commit/push 后证明 clean `HEAD == origin/main`，在四个 frozen ports free 的 evidence
   window 用唯一新 run ID `step4-coverage-20260717-diagnostic-r12` 从头执行 all-lane
   diagnostic，退出后恢复 exact demo container IDs；
7. 只有 r12 完整成功才允许 threshold review；direct-single-parent freeze、fresh formal、最终
   quality、coverage audit 与 acceptance 仍严格后置。任一失败继续 fail closed，不开始 Step 5。

## Step 4 r12 threshold/coverage remediation addendum（2026-07-17）

r12 已完整成功发布 diagnostic evidence，但它观察到 9/12 critical classes 低于 floor；首次
freeze 又证明真实 enriched observation 与旧 consumer schema 不兼容，并且
`NamespaceScope.branch` 的结构性零分母无法表达。r12 是有效 diagnostic，不是可冻结的
baseline。后续顺序更新为：

1. 封存 r12 exact observation、summary、sensitive scan、cleanup 和四个 external container
   restoration；r12 不直接生成 Cfreeze；
2. threshold consumer 必须 exact 消费真实 critical row 六字段与八字段 metric，默认要求
   positive denominator；唯一 N/A tuple 为 `NamespaceScope / foggy-dataset-model / branch`；
3. 所有 decoded JSON 比较必须区分 bool/int/float，拒绝 `false == 0`、`0.0 == 0` 与
   `gap:false == 0.0` aliases；frozen diagnostic receipt 必须包含并验证 raw-exec replay schema，
   full formal validator 对 replay call 保持 exact one-call binding；
4. 九个 gap class 只通过既有测试补边界，不改 production、critical set、floor 或 exclusion，
   且 report/testcase cardinality 保持不变；focused 九类测试=`136/F0E0S0`；
5. focused gates 必须至少为 XML=`118/118`、contract mutations=`27/27`、threshold/frozen
   replay policy=`12/12`、overlay=`12/12`、top=`60/60`、successor=`14/14`；
6. fresh implementation quality B/H/M=0 后，将 remediation 作为 Cdiag commit/push，证明 clean
   `HEAD == origin/main`；停止并记录四个 exact demo DB containers，确认 frozen ports free，
   使用唯一新 run ID `step4-coverage-20260717-diagnostic-r13` 从头执行；退出后恢复 exact IDs；
7. 只有 r13 完整成功、`below_floor_class_count=0`、structural N/A count=`1` 且 candidate
   public verification 通过，才创建 Cdiag 的 direct-single-parent Cfreeze；Cfreeze 后 fresh
   formal、最终 quality、coverage audit、acceptance 顺序不变。Step 5 在 Step 4 exit 前关闭。

## Historical Step 4 r13 diagnostic / Cfreeze snapshot（2026-07-17）

r13 已完整成功并将 r12 remediation 重新证明为 fresh authority：run=
`step4-coverage-20260717-diagnostic-r13`，tested Cdiag commit=
`b76552e21479c75111f648a4aa678abe018cc3f9`，outer=
`diagnostic-observed / completed / exit 0`；required=`773+59/5,707/F0E0S0`、Addon=`2/6`、
exec=`23/48`，critical below-floor=`0`、唯一 `NamespaceScope.branch` structural N/A=`1`，
sensitive scan=`passed`、cleanup=`0/0/0`。threshold freeze candidate public verification
已通过，SHA-256=`8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`；独立 review
SHA-256=`2ab3dc50ed15399c07c1281c70961bf56593eae925727e5cc357bb448e737d8e`。
reviewed threshold 已确认为
`0cfc6765eda1aa8a5209e46bf668136ee1786c4761d66a07262ac3557e7227cb`，contract 已切换
`formal-ready`（SHA-256=
`6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`）。

后续严格按以下顺序推进：

1. [completed] 以 r13 sealed diagnostic 和 verified candidate 完成 exact threshold 人工 review；
2. [completed] Cfreeze 只包含 threshold/contract/`SHA256SUMS` exact allowlist 与
   `docs/9.3.4/` writeback；已以 direct-single-parent `86d505e` commit/push 并通过 topology proof；
3. [completed] Cfreeze commit/push、direct-parent delta 与 clean `HEAD == origin/main` 通过后，使用唯一新 run ID 从头执行
   fresh formal；不得复用 r13 exec、XML 或 candidate 充当 formal evidence；
4. [failed-closed] fresh formal-r1 在 coverage gate 失败；后续最终 implementation quality、
   test coverage audit 与 acceptance 未启动。

该时点 Step 4=`in-progress`、threshold=`confirmed`、Cfreeze=`formal-ready`；随后 formal-r1
fail closed 并由下方 recovery addendum supersede。Step 5 与 9.3.5 始终关闭。

## Step 4 formal-r1 failure recovery addendum（2026-07-17）

formal-r1 在 Cfreeze `86d505e` 上通过全部执行与证据 lane，随后因
`WatchServiceFileTracer` aggregate/critical exact threshold 下降 `9 line / 3 branch` 被
`E_FORMAL_LOW` 拒绝。逐类 XML 与 23 exec probe 对比确认唯一原因是 tracer shutdown hook
和 JaCoCo dump hook 的退出顺序竞态；不得以重跑获取伪绿色。

恢复顺序固定为：

1. [completed] 登记 immutable formal-r1 failure 与 BUG，验证 cleanup=`0/0/0`、敏感扫描通过、
   success-only artifacts absent；
2. [completed] 在既有 testcase 内创建 isolated tracer 并显式 shutdown；5/5 fresh fork 的
   目标 probe bitmap 完全一致，11 testcase/F0E0S0 不变；
3. [completed] 恢复 b765 exact pending machine trio，完成 focused/static quality；正式
   pre-Cdiag quality PASS，B/H/M/L=`0/0/0/0`；
4. [completed] 一次提交并 push 为新 Cdiag，证明 clean `HEAD == origin/main`；
5. [completed] 停止 exact demo DB containers，使用唯一新 run ID 执行 fresh diagnostic；
6. [completed] 对新 observation 生成 candidate、独立复核，并以新 Cdiag 为唯一 parent 创建一次
   Cfreeze commit；Cdiag 与 Cfreeze 间不得插入其他提交；
7. [failed-closed] fresh formal-r2 因独立的 ListPreset branch-order 波动被门禁拒绝；后续顺序
   由 formal-r2 recovery addendum supersede。

当前 threshold/contract=`diagnostic-pending/diagnostic-ready`，Step 4=`in-progress`，
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Step 5 与 9.3.5 保持关闭。

## Step 4 r14 diagnostic / second Cfreeze transition（2026-07-17）

1. [completed] new Cdiag `322bb346cca19998a90d6d990505ef033f3a496a` commit/push/clean
   identity verified；four exact demo DB containers stopped only for the run window and restored
   running/healthy afterwards；
2. [completed] fresh r14 completed with required=`773+59/5,707/F0E0S0`、Addon=`2/6`、
   exec=`23/48`、class universe=`24/2098`、critical below-floor=`0`、N/A=`1`、cleanup=`0/0/0`；
3. [completed] candidate SHA-256=`9087774387f0bb4b177a1b5f2fe28a4102e0434afe7a5b316aa106511c9e6d55`
   passed public verification and two independent reviews；the only Low is non-critical PostgreSQL
   Pivot probe variance，which is frozen at r14's real lower observation and remains guarded by formal；
4. [completed] reviewed threshold=`confirmed` SHA-256=
   `04544480ef73df4bfcba4ddb1d0323b8314fbb4a6934eae5eae51bb2a958486e`，contract/publication=
   `formal-ready` SHA-256=`6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`，
   manifest=`60/60`、full contract/frozen diagnostic/overlay validators PASS；
5. [completed] committed as direct-single-parent Cfreeze
   `1901a10138bac06a09b875c907b7aea6e2789b04`，pushed，validated topology/formal delta，and
   proved clean `HEAD == origin/main`；
6. [failed-closed] exact demo DB containers were stopped for fresh formal-r2 and restored on exit；
   formal-r2 completed all lanes but failed aggregate branch exact threshold by `1`；formal-r1 and
   formal-r2 remain immutable；
7. [pending] only after a new diagnostic/Cfreeze/fresh formal PASS run final implementation quality，
   then test coverage audit，then acceptance signoff。Step 5 stays closed until all Step 4 exit gates pass。

At the r14/Cfreeze snapshot Step 4=`in-progress`，`can_enter_coverage_audit=no`，
`can_enter_acceptance=no`；the formal-r2 recovery addendum below is a historical snapshot，
superseded by diagnostic-r15 recovery。

## Step 4 formal-r2 failure recovery addendum（2026-07-17）

formal-r2 在 committed/pushed direct-child Cfreeze
`1901a10138bac06a09b875c907b7aea6e2789b04` 上完成全部测试、库存、exec/report provenance，
随后因 aggregate branch 比 r14 reviewed exact threshold 少 `1` 被 `E_FORMAL_LOW` 拒绝。
aggregate line、12 critical class、below-floor 与 N/A 均符合要求；2066 个 reportable class
只有 `FileSystemListPresetStore` line 74 的 filename-false outcome 变化。逐 exec/probe 和全部
authority XML 证明原因是 `Files.find(...).findFirst()` 的目录遍历短路顺序，不是漏跑、
artifact 缺失或生产行为回归。

恢复顺序固定为：

1. [completed] 封存 formal-r2 immutable failure、success-only artifact absence、cleanup=`0/0/0`、
   sensitive PASS、source before=after，以及 aggregate/class/source/probe exact diff；
2. [completed] 登记
   `BUG-934-STEP4-LIST-PRESET-FILES-FIND-COVERAGE-ORDER`，拒绝重跑碰运气或降低 threshold；
3. [completed] 在既有 FileStore testcase 内增加不存在 ID empty assertion，强制遍历已有
   regular files；无生产变更、无新 testcase；
4. [completed] 5/5 fresh Maven/JVM/JaCoCo fork 均命中缺失 probe 106，bitmap unique=`1`；
   Data Viewer module=`104/F0E0S0`，目标类恢复 r14 exact `74/113` bitmap；
5. [completed] canonical machine trio 恢复为 `diagnostic-ready/diagnostic-pending`，manifest=
   `60/60`，contract 与 successor overlay validator PASS；
6. [completed] 正式 pre-Cdiag implementation quality PASS；Cdiag `9270d2d4…` commit/push/clean；
7. [failed-closed] exact demo DB evidence window 中运行 fresh r15，Unit timing oracle 失败；
   编排会话退出后在 evidence window 外恢复 exact IDs；
8. [pending] fresh formal PASS 后依次执行最终 implementation quality、coverage audit 与
   acceptance。任一步失败继续 fail closed。

该 historical recovery 已由下方 r15 addendum supersede；Step 5 与 9.3.5 始终保持关闭。

## Step 4 diagnostic-r15 failure recovery addendum（2026-07-17）

formal-r2 recovery Cdiag `9270d2d4e58684226aeb15eff55b027e6aa4a7eb` 已
commit/push/clean；fresh r15 在完整 Unit replacement 的 Bean2Map 单次计时断言处正确
fail closed。该轮仅有 partial `26 reports / 124/F1E0S0` 和 `2/48 sessions`，不得作为
diagnostic candidate 或与后续结果拼接。

1. [completed] 封存 r15 run/status/source-before/partial-exec/cleanup 与 success-only artifact
   absence；明确最终 sensitive scan 未到达，前置 pattern probe 不等于最终扫描；
2. [completed] 建立 `BUG-934-STEP4-BEAN2MAP-CACHE-TIMING-ORACLE`；确认 static cache 在该方法前
   已被相同 class 预热，原 first/second/third 均为 cache hit，单次纳秒倍率不能证明缓存正确性；
3. [completed] 保持两个既有 testcase 与 1000-copy 行为，用三个同类、不同 source 实例的精确复制结果
   替代计时门；不改 production/API/POM/runner/floor，不增加或删除 testcase；
4. [completed] 执行 focused 10 个 fresh Maven/JVM/JaCoCo forks、完整 class 和完整 module：
   `10/10`、`23/F0E0S0`、`27/F0E0S0`；
5. [completed] 同步 requirement/contract/inventory/responsibility/test/progress/evidence；正式
   pre-Cdiag implementation quality PASS，B/H/M/L=`0/0/0/0`；
6. [completed] 只创建一个新的 Cdiag commit/push/clean identity；在 exact demo DB 停止窗口中运行
   唯一 fresh diagnostic，退出后按 exact container ID 恢复；
7. [in-progress] diagnostic PASS 后生成并独立审查 candidate；reviewed Cfreeze 工作树已形成，
   direct-child Cfreeze commit/push 与 fresh formal 尚未执行；失败则封存并重复 fail-closed recovery，
   不盲重跑；
8. [pending] fresh formal PASS 后执行 final implementation quality；仅其 PASS 后进入 coverage
   audit，再在 audit PASS 后执行 acceptance。Step 5 在 Step 4 exit 前关闭。

r15 closure 时点 machine=`diagnostic-ready/diagnostic-pending`；该状态已由下方 r16 addendum
supersede。

## Step 4 diagnostic-r16 / reviewed Cfreeze addendum（2026-07-17）

new Cdiag `f863c672029d5d1e5a4903df74cf6cba22a04a85` 已 commit/push/clean；fresh r16
完整通过并完成 exact candidate/review。该状态只满足 Cfreeze 开启条件，不等于 Step 4 exit。

1. [completed] sealed diagnostic=`step4-coverage-20260717-diagnostic-r16`：required lanes=
   `773 positive + 59 structural / 5,707 testcase / F0E0S0`，coverage inventory=
   `23 exec / 48 sessions / 16,948 unique execution class identities`；
2. [completed] aggregate observation=`54,624/76,830 line`、`26,111/44,870 branch`；12 个
   critical class 全部通过，唯一 `NamespaceScope` branch 按 contract 保持 `N/A`；
3. [completed] immutable candidate SHA-256=
   `2160ef2e16fad161b91c8e3d2571a91a6a8142ae84e06d4539ec69e976563919`，独立 review
   SHA-256=`88b99e76e5584d3cd17bcdcffd138f1fe6655ce0b7795d3430d2f15a018c8fb3`，
   B/H/M/L=`0/0/0/1`；Low 要求 fresh formal 复现 aggregate；
4. [completed] canonical threshold=`confirmed`，SHA-256=
   `ca6a25c66fbbe9a595adde74f1b7589bd3829b93edebfd5b11dc394ab8d088c8`；contract=
   `formal-ready`，SHA-256=
   `6b5e03002ab10bb921d6cb06a4ff3472f2b0605524da6f0f9dc65452a8a21160`；
5. [in-progress] Cfreeze 只允许 machine contract trio 与 `docs/9.3.4/**` 文档证据，且必须是
   Cdiag 的 direct single parent；runner、production、public API 与测试清单无新增改动，
   formal-delta allowlist 持续 fail closed；
6. [pending] commit/push Cfreeze，验证 clean `HEAD == origin/main` 与 direct-parent delta 后，
   使用唯一新 run ID 从头执行 fresh formal，并验证其复现 reviewed aggregate；
7. [pending] fresh formal PASS 后执行 final implementation quality；仅其 PASS 后进入 coverage
   audit，再在 audit PASS 后执行 acceptance。

当前只授权 Cfreeze commit/push 与 fresh formal；两者均尚未完成。`can_enter_coverage_audit=no`、
`can_enter_acceptance=no`，不得写 Step 4 完成，Step 5 与 9.3.5 保持关闭。

## Step 4 formal-r3 fail-closed recovery addendum（2026-07-17）

formal-r3 已将 r16 reviewed Cfreeze 的唯一 Low 转为确定 failure：不降阈，
重新建立 deterministic test evidence 后必须从 Cdiag 起完整重跑。

1. [completed] commit/push direct-child Cfreeze=
   `a63c82c53ebaad1a1c22d78647fbda70b4bd6594`，验证 parent=
   `f863c672029d5d1e5a4903df74cf6cba22a04a85`、formal delta 与 clean identity；
2. [completed] 运行 `step4-coverage-20260717-formal-r3`；全 lane 通过后在 final gate
   以 line exact `54624/76830`、branch `26110/44870 < 26111/44870` fail closed，
   并保持 success-only artifacts absent；
3. [completed] 递归对比 r16/formal-r3，将唯一差异定位到
   `QueryModelSupport#getMergedJoinGraph` line 316 inner DCL，排除 production regression、
   report/exec/class-universe drift；
4. [completed] 在既有
   `RuntimeNamedDataSourceResolverBindingTest#publicationGuardSerializesTheCallbackWithAConcurrentRebind`
   中加受控 QueryModel contention、exact-monitor second-caller `BLOCKED` 确认、
   single-build 和 same-graph 断言；无新/改名 `@Test`，targeted/overlay PASS；
5. [completed] 5/5 fresh Maven/JVM 已 PASS；QueryModelSupport class id=
   `d242dafe9de31249`、probes=`34/629`、packed bitmap unique=`1`、Surefire=
   `1/F0E0S0`，protected overlay 前后均 PASS；`foggy-runtime-api` full module=
   `128/F0E0S0`，`RuntimeNamedDataSourceResolverBindingTest=5/F0E0S0`。独立 pre-Cdiag
   implementation quality 已 PASS，B/H/M/L=`0/0/0/0`，machine/contract/overlay/negative
   suites 通过；record=
   `docs/9.3.4/quality/step4-formal-r3-recovery-implementation-quality.md`；
6. [completed] 将 machine 恢复 `diagnostic-ready/diagnostic-pending`，contract/threshold/
   manifest SHA-256=`15dae282…` / `0df17a87…` / `cc356897…`，manifest=`60/60`；
7. [completed] QueryModel remediation Cdiag
   `316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean，并被 fresh diagnostic-r17
   消耗；r17 因下方 final-mysqld handoff race 在 Unit child immutable fail closed，不能复用；
8. [completed] 按下方 diagnostic-r17 addendum 完成 handoff focused remediation、authority docs
   与 pre-Cdiag formal implementation-quality gate，结论=`PASS / 0/0/0/0`；
9. [in-progress] 只创建一个 replacement Cdiag 并 commit/push/clean，随后运行唯一 fresh
   diagnostic；diagnostic PASS 后才生成并独立审查
   candidate，再创建 direct-child Cfreeze 并运行 fresh formal；
10. [pending] fresh formal PASS 后依次执行 final implementation quality、coverage audit、
    acceptance/signoff。

当前 Step 4=`in-progress / diagnostic-r17 immutable fail-closed / final-mysqld handoff
remediation verified / pre-Cdiag formal implementation-quality PASS`；replacement Cdiag 与 fresh
diagnostic 尚未建立。`can_enter_coverage_audit=no`、`can_enter_acceptance=no`，Step 5 与
9.3.5 保持关闭。

## Step 4 diagnostic-r17 final-mysqld handoff recovery addendum（2026-07-17）

QueryModel determinism recovery Cdiag
`316a71f753827f8f34063b0eb0669271f696c5ee` 已 commit/push/clean；fresh r17 在
`child-unit` 第三个 MySQL 5.7 lifecycle probe 的 callback ready 前以 `E_LIFECYCLE`
fail closed。r17 是 immutable/excluded/non-reusable failure；cleanup receipt 只证明资源回收，
不代表 fixture PASS。

恢复顺序固定为：

1. [completed] 封存 r17 outer/Unit/failed probe status、source-before、toolchain/class-universe、
   cleanup 与 exact container restore；确认 Unit XML/exec、source-after、aggregate、observation、
   candidate/summary 等 success-only artifacts absent；
2. [completed] 建立 final-mysqld handoff BUG，并以 runtime RED 证明原 stock
   `mysqladmin ping` 可在 entrypoint 临时 server 阶段提前 healthy；拒绝 blind rerun、固定 sleep
   或放宽 fixture identity/watermark；
3. [completed] 只在 Step 4 successor authority Compose amendment 中令 MySQL 5.7/MySQL 8
   healthcheck 同时要求 PID 1=`mysqld` 与原 `mysqladmin ping`；frozen Step 3 provisioner、
   production/public API、coverage floor/critical/threshold/exclusion 与 testcase/report identity
   均不变；
4. [completed] lifecycle tool 增加 failure-only diagnostics：run-root no-clobber `0600` 日志，
   受控 failure 保留原 typed `FixtureError` code（例如 lifecycle=`E_LIFECYCLE`、cleanup=
   `E_CLEANUP`）并携带 diagnostics path；成功日志只在 expected terminal state 与 final cleanup
   完成后删除，failure log 正式引用前必须 sensitive scan；
5. [completed] MySQL 5.7/MySQL 8 runtime GREEN 均证明首次 healthy 时 PID 1=`mysqld`；修复
   旧字节 lifecycle=`15/15`；penultimate current-byte `5/5` receipt SHA-256=
   `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`。最终 current-byte
   `step4-unit-lifecycle-handoff-current-20260717-r2` 在 fixture tool SHA-256=
   `9be62daaf7a3d2d873c7647078c0bf798ab25c491a163e90960d4143965be5be` 上完成 `5/5`，receipt
   SHA-256=`e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；successful
   `provisioner.log` absent，demo restore=`0/0` 且四库 healthy/listening；
6. [completed] 重新执行 successor overlay negatives=`12/12`、Unit fixture negatives=`36/36`、
   coverage negatives=`27`、source/Git negatives=`22`、replay negatives=`12`，machine 保持
   `diagnostic-ready/diagnostic-pending`；
7. [completed] authority docs 与 pre-Cdiag formal implementation-quality gate 完成，结论=
   `PASS / B/H/M/L=0/0/0/0`，记录=
   `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`；dirty worktree
   上的 full Unit 仅在 source seal 预检 fail closed、未执行 Maven，因此不得作为全量行为证据；
8. [in-progress] 只创建一个 replacement Cdiag commit，push 并验证 clean
   `HEAD == origin/main`；再使用唯一新 run ID 执行 fresh diagnostic，由该 clean source run
   完整证明 lifecycle、full Unit、all required lanes 与 aggregate；
9. [pending] diagnostic PASS 后才可生成/独立审查 candidate，并以 replacement Cdiag 为唯一
   parent 创建 direct-child Cfreeze，再运行 fresh formal；失败即封存并回到 fail-closed recovery，
   禁止降低 threshold、扩大 exclusion 或跨 run 拼接；
10. [pending] fresh formal PASS 后依次执行 final implementation quality、coverage audit、
    acceptance/signoff。只有全部 PASS 才满足 Step 4 exit 和 Step 5 开启条件。

当前 Step 4=`in-progress / diagnostic-r17 immutable fail-closed / final-mysqld handoff
remediation verified / pre-Cdiag formal implementation-quality PASS`；当前只授权 one
replacement Cdiag commit/push/clean + fresh diagnostic，二者尚未建立。full Unit authority、
`can_enter_coverage_audit`、`can_enter_acceptance`、Step 4、Step 5 与 9.3.5 均保持关闭。

## Step 4 diagnostic-r18 governed-high-water remediation addendum（2026-07-17）

Cdiag `5be1edaa16c5883cde2f66396ac26a1ae113430b` 已 commit/push/clean；fresh r18 是完整、
public-valid 的 diagnostic PASS，但 aggregate 未达 r16 reviewed high-water。本 addendum
supersede r17 handoff 恢复的 next gate，不改写 r17 immutable failure history。

1. [completed] 封存 `step4-coverage-20260717-diagnostic-r18`：required=
   `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session/identity=`23/48/16940`、
   class universe=`24/2098`、cleanup=`0/0/0`，public validation PASS；
2. [completed] 以 r16 reviewed high-water 作为 governed predecessor 复核 aggregate；r18=
   `54622/76830 line, 26107/44870 branch`，r16=`54624/76830 line, 26111/44870 branch`，
   delta=`-2/-4`；设定 decision=`threshold-candidate-not-authorized`并保持 candidate absent；
3. [completed] 将 delta 定位到 `BaselineRatioCalculator=-2 line/-3 branch` 与
   `ResultShaper=-1 branch`的 PostgreSQL exec bitmap incidentality；确认 production、分母、
   class universe 与 report inventory 无 drift；
4. [completed] 建立
   `docs/9.3.4/workitems/BUG-step4-pivot-null-axis-coverage-oracle.md`，并在既有
   `PivotSqlParityIT` S12 中增加 deterministic NULL-column baseline exclusion / NULL-row tree
   fallback semantic oracle；无新增/改名 `@Test`、无 production 修改，单方法 PASS；
5. [completed] 同步 successor amendment、protected-tree、database/required contract、overlay 与
   双层 hash manifests；successor/top=`14/14 + 60/60`，database=`7/29/370`、required=
   `45/446/F0E0S0`、overlay=`22/9`、coverage contract=`23/48/773+59/5707`。三次 fresh
   JVM/JaCoCo focused 均 `1/F0E0S0`，两目标 class probe bitmap=`3/3 identical`，完整
   `PivotSqlParityIT=23/F0E0S0`；implementation-quality=`PASS / B/H/M/L=0/0/0/0`，record=
   `docs/9.3.4/quality/step4-diagnostic-r18-pivot-null-axis-implementation-quality.md`；
6. [in-progress] 只创建一个 replacement Cdiag，commit/push 并证明 clean
   `HEAD == origin/main`；使用唯一新 run ID 运行 fresh diagnostic-r19；
7. [pending] r19 必须 all-lane/public validation PASS，且 aggregate line/branch 均不低于
   r16 reviewed `54624/76830` / `26111/44870`。仅此情况允许生成 candidate 并进行
   public verification/independent review；任一不足继续 fail closed，禁止降阈；
8. [pending] candidate review PASS 后以 replacement Cdiag 为唯一 direct parent 建立 Cfreeze，
   commit/push/clean 后运行 fresh formal；
9. [pending] fresh formal PASS 后依次执行 final implementation quality、coverage audit 与
   acceptance/signoff；仅全部 PASS 后才满足 Step 4 exit 与 Step 5 开启条件。

当前 machine=`diagnostic-ready/diagnostic-pending`，Step 4=`in-progress / r18 diagnostic PASS /
threshold candidate not authorized / Pivot NULL-axis remediation verified / pre-Cdiag quality PASS /
replacement Cdiag pending`。candidate、Cfreeze、formal、
final quality、coverage audit、acceptance、Step 5 与 9.3.5 全部关闭；
`can_enter_coverage_audit=no`、`can_enter_acceptance=no`。governed record=
`docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r18-governed-high-water-gap-20260717.md`。

## Step 4 diagnostic-r19 reviewed-Cfreeze addendum（2026-07-17）

1. [completed] 创建并 push replacement Cdiag `613b11a0ae6732f865f918551cd9116079771b5e`，证明
   `HEAD == origin/main`、worktree clean；
2. [completed] fresh r19 all-lane/public validation PASS：required=`773+59/5707/F0E0S0`、
   Addon=`2/6`、exec/session/class identity=`23/48/16931`、class universe=`24/2098`、
   cleanup=`0/0/0`；
3. [completed] aggregate exact 达到 r16 high-water：line=`54624/76830`、branch=
   `26111/44870`；critical `12`、positive metrics `23`、below-floor `0`、唯一 N/A=
   `NamespaceScope.branch`；
4. [completed] 生成 immutable candidate `6588e30b…f545b8`，public verification 与两路
   independent exact-projection/evidence-binding review PASS；
5. [completed] canonical threshold/contract/SHA256SUMS working-tree delta exact 投影为
   `confirmed/formal-ready`，pre-Cfreeze implementation quality=`PASS / 0/0/0/0`；
6. [completed] 将 allowlisted delta 作为 Cdiag 的唯一 direct-child Cfreeze `f97483a0…` commit，
   formalization delta PASS，push 并证明 clean `HEAD == origin/main`；
7. [completed] 从 Cfreeze 启动唯一 fresh formal-r4，完整重放 `23/48` 与全部 required lane；
   required=`773+59/5707/F0E0S0`、aggregate exact confirmed threshold、public final=`VALID`；
8. [completed] formal PASS 后执行 final implementation quality；decision=
   `ready-for-coverage-audit / B/H/M/L 0/0/0/1`；
9. [completed] test evidence coverage audit=`ready-for-acceptance`；25/25 workitem covered，
   critical/major gap=`0/0`；Pivot legacy companion=`1/F0E0S0`；
10. [completed] feature acceptance=`signed-off / accepted / blocking none`；Step 4=`passed`，
    Step 5=`ready / not-started`。

Historical formal-r4 closure：当时 Step 4=`passed / feature accepted`、`can_enter_step5=yes`、
Step 5=`ready / not-started`。该授权已由下方 formal-r6 replacement recovery plan 重新关闭；
current Step 4=`in-progress`、Step 5–7=`hold/closed`、9.3.5=`queued`。

## Step 4 feature-acceptance closure（2026-07-18）

- quality→coverage audit→feature acceptance 已按固定顺序完成，records 位于
  `quality/step4-coverage-gate-final-implementation-quality.md`、
  `coverage/step4-coverage-gate-coverage-audit.md` 与
  `acceptance/step4-coverage-gate-acceptance.md`；
- 25 个 Step 4 workitem 已在 acceptance decision 后关闭；唯一 classification DEBT 继续 open；
- 下一动作严格进入 Step 5 rehearsal，不启动 Step 6/7，不创建 version signoff，不建立
  `docs/9.3.5` 实施目录。

## Superseding formal-r6 recovery plan（historical；superseded below；2026-07-19）

1. [completed] 封存 formal-r6 bootstrap-negative failure 与 success-only absence boundary；
2. [completed] 两路独立根因审计，确认 malformed v2 newline token 和 real index 无漂移；
3. [completed] NUL-token 最小修复、focused/independent/full-negative stress；
4. [completed] machine 恢复 `diagnostic-ready / diagnostic-pending`，Step 4/6 hash closure PASS；
5. [completed] Cdiag `9b0bd281…` commit/push/clean，fresh diagnostic-r23 full PASS/public-valid；
6. [completed] 拒绝 r23 scheduling-dependent `+1 branch/+1 complexity` high-water freeze；candidate/
   capsule absent；existing-node MapBeanInfo controlled regression、5 fresh JVM probes、owning module 与
   pre-Cdiag review PASS；
7. [completed] replacement Cdiag commit/push/clean identity + fresh diagnostic-r24；
8. [completed] candidate、portable capsule、双审、machine formalization、direct-child Cfreeze commit/push/clean；
9. [failed / excluded] fresh-clone formal-r7 在 Integration 暴露仓外 CALCULATE catalog 依赖；
10. [in-progress] 封存 r7、纳入 exact repo-local catalog、增加 pre-test tracked blob/SHA gate、同步
    lifecycle seals 与 diagnostic Step4→Step6 hash closure；
11. [pending] Cdiag commit/push，isolated focused/negative proof，fresh all-lane diagnostic-r25；
12. [pending] r25 candidate/capsule/双审→direct-child Cfreeze→fresh formal-r8；
13. [pending] final quality→coverage audit→acceptance，随后恢复 Step 5 entry。

任一步失败继续 fail closed；formal-r6/r22 均不得成为 replacement authority。

r24 checkpoint：Cdiag=`414c8b12…`，r24 public-valid，aggregate=`54624/76830 line +
26112/44870 branch`，candidate=`f13f3c35…2ee`，capsule=`6638/0 symlink`，dual review 与
pre-Cfreeze quality 均=`PASS / 0/0/0/0`，machine=`confirmed/formal-ready`。第 8 步已完成；
第 9 步 formal-r7 已按 fail-closed 规则终止并封存。当前只进入第 10–11 步；不得续跑 r7 或复用
r24 evidence。

## Superseding diagnostic-r25 Unit MySQL 7/12 recovery plan（2026-07-19）

本节只 supersede 上方 formal-r6 recovery plan 的当前 next gate；r7 的 `6 reports / 11
errors` 继续作为不可改写的历史观测。r25 的 all-lane 运行结果同样保持原样，但不得提升为
candidate authority。

1. [completed] portability remediation Cdiag=
   `5aaffbb4cd217d3d891c22eca4d3ae31d4e9d6e7` 已 commit/push/clean，isolated catalog
   focused/negative proof 通过；
2. [completed / diagnostic-only] fresh
   `step4-coverage-20260719-diagnostic-r25` 完整退出并获 public `DIAGNOSTIC VALID`；observation=
   `01487f7efd930406ffa05af9408012aa1fb215d94ba9c36c261f72c1aec7e42a`，required=
   `773+59/5707/F0E0S0`、Addon=`2/6`、exec/session=`23/48`、production universe=
   `24/2098`、cleanup=`0/0/0`；
3. [completed] follow-up read-only consumer audit 证明
   `DatasetJdbcUtilsTest#getOrCreateDataSource` 是第 7 个真实 MySQL consumer / 第 12 个 node；
   旧测试捕获 `SQLException` 并仅 `printStackTrace`，所以 r7 无 listener 时的 Maven error
   集合只能观察到 `6/11`。r25-tested schema 1 contract 错误地把历史错误集合当成完整
   known-consumer 集合；
4. [completed] 将 r25 定性为
   `pre-remediation / superseded / non-candidate`；禁止生成 threshold candidate、portable
   capsule 或 Cfreeze，禁止复用其 XML/exec 与后续 run 拼接为 authority；
5. [completed / local observation] 最小测试 oracle 修复：方法声明 `throws SQLException`，保留 datasource
   identity 断言，并用 try-with-resources 管理 `Connection`、`PreparedStatement`、`ResultSet`；
   对 `SELECT 1` 的列数、唯一行和值作精确断言，删除 catch/printStackTrace；同一 disposable
   MySQL 随机端口下正向 Maven rc=`0`、XML=`1/F0E0S0`，错误密码负向 Maven rc=`1`、
   XML=`1 test / 1 error`，一次性容器自动删除且四个 demo DB exact ID 保持 healthy。正向 XML
   随 deliberate negative 被覆盖，故本项不作为 portable authority，isolated durable proof 仍 pending；
6. [completed] Unit fixture machine contract 升级到 schema 2：分离 immutable
   `historical_observed_failure=6/11` 与 reviewed
   `known_database_consumers.reports_minimum/testcase_nodes_minimum=7/12`，纳入
   `v934|8:surefire|4:unit|4:unit|51:com.foggyframework.dataset.fun.DatasetJdbcUtilsTest`；
   同步 validator、Step 4 manifest、Step4→Step6 hash closure 与必要 successor binding；
   fixture negative=`42/42`、lifecycle=`5/5`；
7. [completed] contract/negative/manifest/overlay/coverage/CI 全部机器校验与 pre-Cdiag
   implementation-quality review PASS；Step4=`61/61 / 4805dd3e…`、Step6=
   `16/16 / 84407570…`、review=`APPROVE / 0/0/0/0`；
8. [completed] 只创建一个 new Cdiag `4fe86929de6206aa3e514c974635e90395c28b2e` 并
   commit/push/clean；fresh-clone isolated r4 保存 positive/wrong-password 双 XML、Maven rc 与 cleanup
   receipt，随后唯一 fresh all-lane diagnostic-r26 public validation PASS；未复用 r25 运行产物；
9. [completed] r26 exact 7/12 contract、candidate public verification、deterministic capsule 与两路
   independent review PASS；candidate=`b8bd2411…2797a`，review=`APPROVE / 0/0/0/0`，只授权
   direct-child Cfreeze；
10. [in-progress] reviewed exact projection 已写入六个 machine formalization path，Step 4→Step 6
    hash cascade 与 pre-Cfreeze validators 均 PASS；当前只将这些路径连同 `docs/9.3.4/**` 形成
    Cdiag 的唯一 direct-single-parent Cfreeze，完成 commit/push/topology/clean proof 后运行 fresh
    formal-r8；
11. [pending] formal-r8 PASS 后按顺序运行 final implementation quality→Step 4 coverage audit→
    Step 4 feature acceptance；仅三门全部 PASS 才把 Step 4 标为 `passed` 并恢复 Step 5 entry；
12. [pending] 继续完成 Steps 5–7 和 9.3.4 version signoff；Step 4 feature acceptance 不等于版本签收。

Current Step 4=`in-progress / diagnostic-r26 reviewed / ready-for-direct-child-Cfreeze`。
`can_enter_cfreeze=yes`、`can_enter_coverage_audit=no`、`can_enter_acceptance=no`；Step 5–7、
9.3.5 与 9.4.0 保持关闭。9.3.5 只能在 9.3.4 version signoff 后先进入 Gate 0 classification-debt
migration。记录：
`docs/9.3.4/evidence/step-4/step4-unit-mysql57-known-consumer-7of12-remediation-20260719.md`、
`docs/9.3.4/workitems/BUG-step4-unit-mysql57-known-consumer-understatement.md`。

## Superseding formal-r8 interpreter-portability recovery plan（2026-07-19）

1. [completed] 将 formal-r8 固定为 `failed / excluded / non-reusable / non-candidate`，封存
   `16 entries / 282473 bytes` failure capsule、raw log hash、success-only absence、cleanup、敏感扫描与
   四个 demo DB exact restore；
2. [completed] 两路只读根因/同类风险审计确认：formal-r8 失败涉及两个 Git `100644` 工具和三个
   direct command positions；runner 总计使用四个 Git `100644` Python tools / 七个 dispatch，均纳入
   successor gate；主工作树 `core.fileMode=false` 和偶然 executable bit 掩盖历史 direct calls；
3. [completed] 三处统一改为显式 `python3`，新增 runner raw/292-command-stream 双封印、四 target/七
   top-level logical binding、raw/stream/semantic mutation=`44/44 / 43/43 / 33/33`、Git-mode mutation=
   `4/4` 与 non-executable smoke=`4/4`；production/test/POM/
   cardinality/floor/critical/exclusion delta=`0`；
4. [completed] machine 恢复 `diagnostic-ready / diagnostic-pending`，Step 4=`6a48ab01…0782 / 61/61`、
   Step 6=`d1efe031…43bd / 16/16`，hash closure 与 contract/XML/overlay/lifecycle/authority/CI focused
   regressions PASS；
5. [completed] 首轮独立 review 的 static false-green blocker 与三项状态文档 finding 已修复；
   current-state、recovery evidence 与 pre-Cdiag implementation-quality 已同步，code/docs 复审=
   `PASS / B/H/M/L 0/0/0/0 / mandatory 0 / broken links 0 / status drift 0`；
6. [pending] 只创建一个 new Cdiag commit/push/clean；在 clean fresh clone 证明 Python 工具权威
   mode=`100644`、direct denied、interpreter passed，再运行唯一 all-lane diagnostic-r27；
7. [pending] r27 public validation 后生成全新 candidate/capsule，完成两路独立 review；旧 r26
   candidate、Cfreeze 与 r8 raw artifacts 不得复用；
8. [pending] 唯一 direct-child Cfreeze commit/push/topology/clean 后运行 fresh formal-r9；
9. [pending] formal-r9 PASS 后依次完成 final quality→replacement coverage audit `31/31`→feature
   acceptance；三门全部 PASS 后才恢复 Step 5 entry；
10. [pending] 继续 Steps 5–7 与 9.3.4 version signoff，再进入 9.3.5 Gate 0；9.4.0 仍按主线顺序 queued。

At the diagnostic-r27 checkpoint, Step 4 was=`in-progress / formal-r8 failed + r27 high-water remediation /
Cdiag→fresh-r28`；`can_enter_cfreeze=no / can_enter_step5=no / can_enter_coverage_audit=no /
can_enter_acceptance=no`。This checkpoint is superseded by the formal-r9 boundary below.

## diagnostic-r27 ExportWithChart order remediation addendum（2026-07-19）

1. [completed] reject r27 as threshold input despite public validation: its aggregate branch and complexity
   each lost one covered outcome relative to r26.
2. [completed] localize the sole delta to `ExportWithChartTool.java:248`; identify unspecified `Map.of`
   iteration plus short-circuit field selection as the cause.
3. [completed] stabilize only existing test data with `LinkedHashMap(category -> amount)`; no production,
   POM, test cardinality, report inventory, floor or critical-target change.
4. [completed] independently review and replay five fresh JVM/JaCoCo runs with exact `mb0/cb2` and target
   bitmap identity.
5. [pending] commit/push/clean this Cdiag, then execute one fresh diagnostic-r28. Do not create candidate,
   Cfreeze or formal authority until r28 reaches r26 high-water.

## Superseding formal-r9 strict-umask recovery addendum（2026-07-19）

1. [completed / historical] r28 produced reviewed material and its exact direct-child Cfreeze
   `34cd2452c1bbe793c0567ebe23179b290227ae3d`; these are preserved only as the predecessor of formal-r9.
2. [failed / excluded] fresh `step4-coverage-20260719-formal-r9` ended at `coverage-report / exit 2` after a
   strict umask caused the contractually public effective-POM receipt to be published mode=`0600`; the reporter
   emitted `E_OUTPUT: unexpected output mode: 0600`. Do not rerun, amend or reuse r9 partial output as formal
   authority.
3. [completed / recovery baseline] `390322295e1efce34399468f98076edf7fcc6f73` explicitly publishes the
   receipt mode and adds a strict-umask negative probe, clears reviewed r28 observations and restores
   `coverage-contract=diagnostic-ready` / `coverage-thresholds=diagnostic-pending`. It is expressly not the
   final Cdiag and does not authorize a candidate, Cfreeze, formal result or post-formal gate. The corresponding
   [implementation-quality record](quality/step4-formal-r9-effective-pom-output-mode-recovery-implementation-quality.md)
   authorizes only the next Cdiag.
4. [pending] create one new clean/pushed Cdiag successor containing the recovered authority documentation,
   then run one fresh all-lane diagnostic-r29. The successor must not import r28 candidate/capsule/Cfreeze or
   r9 child/exec/report output.
5. [pending] only after diagnostic-r29 is independently validated may a new candidate/capsule and dual review
   be created; only that Cdiag's direct-single-parent Cfreeze may start a fresh formal successor.
6. [pending] only after that fresh formal successor may final implementation quality → replacement coverage
   audit `31/31` → Step 4 feature acceptance be evaluated. Steps 5–7, version signoff, 9.3.5 and 9.4.0 stay
   closed throughout this chain.

Current Step 4=`in-progress / formal-r9 strict-umask excluded / diagnostic-ready→pending Cdiag successor`。
`can_enter_cfreeze=no / can_enter_step5=no / can_enter_coverage_audit=no / can_enter_acceptance=no`。

## Superseding diagnostic-r29 Git-safety remediation addendum（2026-07-19）

1. [completed] clean/pushed Cdiag `f420a4eaa3cf9bed0d7027b656ea71af6d0b03ca` contains the strict-umask
   recovery, permanent r9 exclusion, documentation, and diagnostic-ready machine reset.
2. [completed] one fresh all-lane diagnostic-r29 ran under outer `umask 077`; durable replay preserved its
   diagnostic facts and candidate calculation.
3. [completed] Git-safety review found that the local capsule recursively captured Git-excluded raw runtime
   content. r29 is permanently `non-freezable`; its candidate, local capsule and reviews grant no Cfreeze
   authority and may not be repaired or reused in place.
4. [completed] Cdiag `7757aa36c0efd0970422669e0f88f74daa8f15b0` replaced recursive capsule closure and raw-exec-only frozen replay with an
   explicit two-file Git-safe allowlist, source-validated hash-only diagnostic attestation, and XML-only semantic
   recomputation boundary. The capsule rejects raw execution/log/process/container content, unsafe XML/tar framing
   and TOCTOU; static evidence is `21` capsule probes, `28` contract probes, `128` XML probes and `86` CI probes.
5. [failed / excluded] fresh `step4-coverage-20260719-diagnostic-r30` started from that Cdiag under outer
   `umask 077` but stopped in `contract-validate` when the successor overlay retained stale dual
   coverage-contract and coverage-tool bindings. It executed zero lanes and has no candidate/Cfreeze authority.
6. [completed] Cdiag `f80fadd62ca00d3ba56f1be04e92113ba1145019` synchronized both successor binding sources, successor/Step 4/Step 6
   integrity manifests, and add a canonical overlay positive control to the contract-negative suite. The original
   `21 / 28 / 128 / 86` static counts remain intact; the new overlay binding control is separate.
7. [failed / excluded] fresh `step4-coverage-20260719-diagnostic-r31` completed preflight/source-seal/class-universe
   but stopped before lifecycle probes, fixture provision or Maven Unit execution because the fixed-port
   environment precondition was occupied outside its derived project. It has zero lane/candidate/Cfreeze authority;
   the `0/0/0` cleanup result does not permit taking over or reusing the listener.
8. [pending] seal r31, form/push one new Cdiag, independently verify the fixed port is free, then run one fresh
   strict-umask diagnostic-r32 in a fresh clone. Only its new candidate and independently reviewed Git-safe closure
   may be considered for Cfreeze.
9. [pending] after a valid successor Cfreeze, run one fresh formal successor, then final implementation
   quality → replacement coverage audit `31/31` → Step 4 feature acceptance.

Current Step 4=`in-progress / r29 non-freezable + r30 binding exclusion + r31 environment exclusion / replacement Cdiag pending`。
`can_enter_cfreeze=no / can_enter_formal=no / can_enter_step5=no / can_enter_coverage_audit=no /
can_enter_acceptance=no`；Steps 5–7、9.3.5 与 9.4.0 保持关闭。

## Superseding diagnostic-r32 WatchService delete recovery addendum（2026-07-20）

1. [completed] Fresh strict-umask r32 from clean `a9ec2a2f…` completed all lanes: required=`773+59/5707/F0E0S0`,
   Addon=`2/6/F0E0S0`, exec/session=`23/48`, source before=after, critical below-floor=`0`, cleanup=`0/0/0`.
2. [completed / fail-closed] r32 aggregate line=`54624/76830` reached the floor, while branch=`26111/44870`
   and complexity=`17658/35571` are each one below reviewed high-water. r32 is permanently non-freezable;
   its isolated non-canonical material must not enter candidate/review/Cfreeze authority.
3. [completed] Semantic comparison localized the sole loss to `WatchServiceFileTracer.java:442`. One existing
   mock-key test now drives unfiltered, filtered-reject, and filtered-match deletion. It preserves production,
   POM, runner, test-node/report identity, floor, critical set, exclusion, and public API; five focused JVMs
   restored line=`4/4` and method branch/complexity=`11/12` / `6/7`, and full `foggy-core`=`97/F0E0S0`.
4. [completed] Independent implementation review=`PASS / B/H/M/L=0/0/0/1`, mandatory=`0`; the Low is
   pre-existing singleton fake-watcher cleanup debt and is not enlarged by this change.
5. [pending] Commit/push exactly this test-only Cdiag and verify clean identity. Then run one fresh strict-umask
   diagnostic-r33. Only if r33 completes all lanes and reaches line >= `54624/76830`, branch >= `26112/44870`,
   complexity >= `17659/35571` may new candidate/Git-safe closure/dual review begin.
6. [pending] Only r33's Cdiag may receive a direct-single-parent Cfreeze, followed by fresh formal, final
   implementation quality, replacement coverage audit, and Step 4 feature acceptance.

Current Step 4=`in-progress / r32 non-freezable / test-only Cdiag ready / fresh r33 pending`.
`can_enter_cfreeze=no / can_enter_formal=no / can_enter_step5=no / can_enter_coverage_audit=no /
can_enter_acceptance=no`；Steps 5–7、9.3.5 与 9.4.0 保持关闭。
