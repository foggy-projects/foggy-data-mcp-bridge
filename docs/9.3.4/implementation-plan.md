---
doc_role: implementation-plan
doc_purpose: Define the strict Step 1-7 implementation and verification order for 9.3.4.
version: 9.3.4
status: in-progress
created_at: 2026-07-14
updated_at: 2026-07-16
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
`in-progress / r6 historical environment fail-closed / fresh r7 pending`。

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
  frozen ports 已证明无 listener；下一步以全新 `step4-coverage-20260716-diagnostic-r7`
  从 source seal 开始。threshold=
  `diagnostic-pending`，`can_enter_coverage_audit=no`；Step 5、acceptance 与 9.3.5 保持关闭。

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
