---
doc_role: execution_progress
doc_purpose: Track planned implementation, test evidence and downstream readiness for 9.3.3.
version: 9.3.3
status: completed
acceptance_status: signed-off
acceptance_decision: accepted-with-risks
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 模型生命周期与并发进度

## 文档作用

- doc_type: progress-template
- intended_for: execution agent / reviewer / signoff owner
- purpose: 每个 Batch 完成时记录实际实现、测试、偏差、风险和证据，不允许只报“已完成”。

## 基本信息

- upstream requirement: `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- contract: `docs/9.3.3/contract/model-lifecycle-concurrency-contract.md`
- implementation plan: `docs/9.3.3/implementation-plan.md`
- implementation owner: current 9.3.3 execution lane
- started_at: 2026-07-13
- completed_at: 2026-07-14
- baseline reference/patch/checksum: `docs/9.3.3/evidence/gate-0/baseline-20260713.md`
- experience: N/A（纯后端生命周期与并发治理）

## 前置条件

| 条件 | 状态 | 证据/备注 |
|---|---|---|
| 9.3.2 feature signoff accepted-with-risks，无 blocker/high | verified | `docs/9.3.2/acceptance/auto-configuration-addon-assembly-acceptance.md` |
| roadmap 已同步 9.3.3 signed-off / 9.3.4 ready | verified | `docs/9.3.1/roadmap-9.3.1-to-9.4.0.md` |
| 9.3.1/9.3.2 dirty worktree baseline 已冻结 | verified | `docs/9.3.3/evidence/gate-0/baseline-20260713.md` |
| 9.3.4-A minimum test gate | verified | Batch 6 aggregate child `20260714T052029Z-2908765`；5 positive + 4/4 expected-negative（older Gate records retained） |
| lifecycle contract 已冻结，无未决 blocker | verified | `contract/model-lifecycle-concurrency-contract.md` status=`confirmed`；2026-07-13 Batch 1 freeze |

## Contract Decisions

| 决策 | 预期 | 实际/偏差 |
|---|---|---|
| default namespace canonical form | empty string，API default 先解析 | confirmed：scope 保留 unset/default/named；catalog default=`""`；`open/openInherited` 区分显式与继承 |
| catalog generation | observable candidate publish 才推进；plain read/failure 不推进 | confirmed：boot UUID + monotonic sequence，external opaque |
| binding generation | persisted/opaque/non-reused；handle pinned | confirmed：Runtime registry epoch+sequence/v1 migration；MCP boot UUID+sequence/restart cold identity |
| model refresh scope | target+dependencies，preserve sibling，namespace generation 恰好变化一次 | confirmed |
| observable lazy materialization | detached candidate + atomic publish，after identity != before；plain read 不切代 | confirmed |
| unknown routing identity | query 可兼容，cache no-read/no-write | confirmed |
| bundle remove event | source mutation commit 后 refresh | confirmed |
| file event/source revision | committed revision；未知 scope 使可能受影响 catalog stale/admission-blocked | confirmed |
| binding remove/disable/rebind | commit 后拒绝新旧-binding lease；in-flight bounded drain；hard revoke 可立即关闭 | confirmed：`DRAIN` default，60s（1s..300s），`HARD` explicit |
| cross-JVM cache | boot epoch cold miss；无 instance hash/旧 Redis key 碰撞 | confirmed |
| Runtime DTO | additive opaque strings + stable sanitized error contract | confirmed：exact success fields + typed failure context/nullability、9 lifecycle codes、legacy HTTP/error.code frozen |

## Development Progress

| Batch | 内容 | 状态 | 实际结果/主要路径 |
|---|---|---|---|
| Gate 0 | baseline + 9.3.4-A | completed | baseline frozen；runner/DB/harness 本地正反向 Gate passed；CI entry configured（remote run not claimed） |
| Batch 1 | contract + failing baseline | completed | Steps 1–7 closed；run `20260713T115746Z-1058249` = 10 suites/12 expected-red assertions；all product criteria remain pending |
| Batch 2 | NamespaceScope | completed | historical exit retained；aggregate child `20260714T045604Z-2854237-10-batch2` = 25 product + 7 compatibility criteria + 1 harness probe green |
| Batch 3 | binding identity + CatalogSnapshot | completed | historical exit retained；Batch 6 aggregate current-source replay `20260714T045604Z-2854237-08-batch3` = 168 tests / 20 reports green |
| Batch 4 | single-flight | completed | historical exit retained；Batch 6 aggregate current-source replay `20260714T045604Z-2854237-07-batch4` = 142 tests / 22 reports green |
| Batch 5 | detached validate + atomic refresh/events | completed | aggregate child `20260714T045604Z-2854237-06-batch5` = 90 tests / 19 reports green；six owning criteria passed |
| Batch 6 | catalog/cache/pivot + real query | completed | Step 7 aggregate `20260714T045604Z-2854237`：11 children、676 criteria / 677 XML testcases / 99 reports、4/4 expected-negative、0 red、F/E/S=0；two independent reviews no blocker |
| Batch 7 | regressions + post gates | completed | replacement authority `20260714T084351Z-3271604`=`3824 tests / 519 reports / F0/E0/S3`，独立 audit no blocker；self-check→quality→coverage→version acceptance 顺序闭环，signed-off / accepted-with-risks |

## Gate 0 Execution Check-in

- completed: Surefire/Failsafe 最小分层、fresh owning-report 断言、统一 fail-closed 入口、SQLite/MySQL 5.7/PostgreSQL 15 preflight、确定性并发 harness、专用 CI workflow。
- touched code/build paths: root/model POM、`scripts/verify-v933-entry-gate.sh`、`scripts/assert-v933-test-report.sh`、`foggy-dataset-model/.../lifecycle/{gate,support}`、`.github/workflows/model-lifecycle-concurrency.yml`。
- protected baseline: 未 reset/checkout/clean/stash；既有 9.3.1/9.3.2 dirty worktree 继续按 baseline manifest 保护。
- self-check: Gate 0 未修改生产 lifecycle；新增并发断言无 `Thread.sleep`，所有 wait/future/executor 有界且异常路径清理 in-flight；报告绑定 run marker；evidence 敏感模式扫描为 0。
- actual verification: `scripts/verify-v933-entry-gate.sh` 从 `/tmp` 调用通过，run `20260713T104955Z-959834`，5 个正向测试全绿、4 个 expected-negative case 全部生效。
- documentation: README、precondition、implementation plan、test plan、progress 与 Gate 0 evidence 已同步。
- obvious risks/follow-ups: workflow 仅完成本地 YAML 解析，尚无 remote GitHub Actions run；完整 9.3.4 matrix/coverage/release evidence 明确延期；probe reactor 使用 `install` 让 clean CI 的 isolated negative lanes 解析当前 sibling artifacts，其证据仍由 verify 阶段报告绑定。
- self-check decision: `self-check-only`（Gate 0 scope，无明显实现质量 blocker）。
- needs-formal-quality-gate: yes；9.3.3 正式 implementation quality gate 仍按 Batch 7 执行，未提前标完成或进入 coverage audit。

## Batch 1 Execution Check-in

- completed: authority/package/DTO/error/lease/MCP generation contract freeze；10 个 explicit-only red suites；缺失 port 的 source proof/mandatory-green 映射；strict marker-bound replay；post-change Gate 0 replay。
- touched production code: none（Batch 1 只改测试、脚本、文档）。
- touched test paths: model lifecycle red、Runtime DTO/model/binding red、fsscript source-order red、MCP catalog-authority red。
- touched scripts: `scripts/assert-v933-red-report.sh`、`scripts/verify-v933-batch1-red-baselines.sh`。
- protected baseline: 未 reset/checkout/clean/stash；9.3.1/9.3.2 dirty worktree 继续按 baseline manifest 保护。
- actual red verification: run `20260713T115746Z-1058249`，12/12 assertions expected-red，errors=0，skipped=0；exact owning XML、fresh marker、scenario pattern 全部核验。
- actual green verification: Gate run `20260713T120110Z-1066109`，5 positive 全绿，4 expected-negative 全部 fail closed；显式 RedBaseline 未污染正常 discovery。
- self-check: 无 sleep-driven interleaving/unbounded wait；并发/连接/ThreadLocal/临时目录有清理；evidence 敏感模式扫描为 0；expected-red/source-proof 未冒充产品 pass。
- documentation: contract、README、implementation plan、progress 与 Steps 1–7 evidence 已同步。
- obvious risks/follow-ups: source-only gaps必须在 owning batch 首次 port 落地时立刻转 normal green；本地 target 不是 remote CI/release immutable evidence；9.3.4 full 仍延期。
- self-check decision: `self-check-only`（no-production-code contract/test baseline）。
- needs-formal-quality-gate: yes；生产实现完成后按 Batch 7 执行，不在 Batch 1 提前声称。

## Batch 2 Execution Check-in

- started_at: 2026-07-13
- completed_at: 2026-07-13
- mode: single-root-delivery / progress-update
- scope: `NamespaceScope`、兼容 `NamespaceContext` API、QueryFacade/Semantic
  metadata/QM loader 的 7 个生产 scope，以及 NS-01～NS-05 正常绿测。
- source inventory: 生产 writer 仅位于 `QueryFacadeImpl`、
  `SemanticServiceV3Impl`、`QueryModelLoaderImpl`；`JdbcQueryModelBuilder` 的
  `getNamespace()` 是合法只读 consumer。
- semantic decision: `ModelResultContext`、`SemanticRequestContext` 和
  `QueryModelLoader` 的 null/blank 均是显式 default，必须 `open(...)` 遮蔽
  outer named；只有上下文本身不存在时使用 `openInherited()`。
- non-goals: 不进入 CatalogSnapshot、binding generation、single-flight、
  refresh 或 cache consumer。
- completed: stack/token-backed `NamespaceScope`；exact previous-state restore；
  owner/LIFO fail-fast；active-scope legacy mutation fail-closed；QueryFacade、
  Semantic metadata、QueryModelLoader/managed relation 生产入口全部 TWR 迁移。
- touched production paths: `NamespaceContext`/`NamespaceScope`、
  `QueryFacade`/`QueryFacadeImpl`、`QueryModelLoaderImpl`、
  `SemanticServiceV3Impl`。
- protected baseline: 未 reset/checkout/clean/stash；9.3.1/9.3.2 dirty
  worktree 继续按 Gate 0 manifest 保护。
- development: completed
- testing: passed；authoritative run `20260713T130626Z-1313396` = 25 product
  + 7 legacy compatibility，failures/errors/skipped=0；post-change Gate run
  `20260713T130717Z-1316013` = 5 positive green + 4 expected-negative
  fail-closed。
- source audit: production legacy `setNamespace/clear`=0；sleep-driven Batch 2
  tests=0；两个 run 的 `SHA256SUMS` 校验全通过。
- obvious risk/follow-up: QueryModelLoader cache-hit 测试通过 private
  reflection/raw Map seam 观察内部 cache，证据有效但内部重构时维护成本较高。
- experience: N/A（纯后端 ThreadLocal 生命周期治理，无 UI）
- self-check decision: `self-check-only`；无 blocker；正式质量闸门仍由
  Batch 7 在全部生产批次结束后执行。
- evidence: `evidence/batch-2/namespace-scope-exit-20260713.md`

## Batch 3 Execution Check-in

- started_at: 2026-07-13
- completed_at: 2026-07-13
- mode: single-root-delivery / progress-update
- entry evidence: `evidence/batch-3/entry-checkin-20260713.md`
- scope: `CatalogSnapshot`/identity/source revision、TM/QM candidate authority、
  deterministic alias、binding generation/admission/lease、Runtime/MCP adapter、
  query/metadata pinned identity。
- criteria: SNAPSHOT、GENERATION、DS-GENERATION、BINDING-REVOKE、QM-COMPLETE。
- non-goals: Batch 4 single-flight、Batch 5 refresh/events/Runtime DTO、Batch 6
  catalog/cache/Pivot consumer、9.3.4 full、9.3.5、9.4.0。
- completed: immutable per-namespace catalog/candidate authority；typed identity
  and provenance；deterministic static/dynamic aliases；TM single binding pin；
  partial QM/candidate fail-closed；atomic QueryFacade/metadata identity capture；
  Runtime persisted generation and Runtime/MCP generation-pinned DRAIN/HARD
  admission/lease adapters。
- touched production paths: model `lifecycle/{identity,catalog,port}`、
  TM/QM loaders/builder/auto-configuration、QueryFacade/ModelResultContext、
  Semantic metadata/catalog、Runtime registry/resolver/pool、MCP datasource
  manager/resolver/controller/persistence、cache strong-key support。
- protected baseline: 未 reset/checkout/clean/stash；9.3.1/9.3.2 dirty
  worktree 继续按 Gate 0 manifest 保护。
- development: completed
- testing: passed；authoritative run `20260713T150948Z-1719636` = catalog 46
  + SQLite consumers 33 + Runtime 28 + MCP 12 + Caffeine support 30 = 149，
  failures/errors/skipped=`0/0/0`；final entry gate
  `20260713T151323Z-1726207` = positive 5 + expected-negative 4/4。
- source audit: mutable legacy catalog authority=0；sleep-driven tests=0；
  promoted red references=0；physical/credential generation inputs=0；两个
  final run 的 `SHA256SUMS` 校验全通过。
- deviations/fail-closed review: 首次 runner 因 nested report 数量不匹配
  主动失败；静态复核发现 pure-fail no-op、candidate 跨线程 mutation、external
  loader fresh TM 三个证据盲点，均先补确定性回归再修复；最终复核无 blocker。
- experience: N/A（纯后端 lifecycle/binding 状态治理）
- self-check decision: `self-check-only`；无 blocker；正式质量闸门仍由
  Batch 7 在全部生产批次完成后执行。
- evidence: `evidence/batch-3/catalog-binding-exit-20260713.md`

## Batch 4 Execution Check-in

- started_at: 2026-07-13
- completed_at: 2026-07-13
- mode: single-root-delivery / progress-update
- entry evidence: `evidence/batch-4/entry-checkin-20260713.md`
- scope: exact keyed single-flight、different-key detached overlap、shared
  winner failure、cleanup/retry、catalog-generation stale retry、dependency
  cycle/self-wait guard。
- criteria: SF-SAME、SF-ISOLATION、SF-FAIL。
- non-goals: Batch 5 source/refresh/events/Runtime validate；Batch 6
  catalog/cache/Pivot/real-query consumers；9.3.4 full、9.3.5、9.4.0。
- completed: model-kind + canonical namespace/model + catalog generation +
  committed source revision + canonical backend/binding identity set exact key；
  caller-inline winner/shared future；same exact success/Throwable；precise cleanup；
  untracked nonce isolation；same-thread/self-wait dependency-cycle fail-fast；
  detached candidate and short publication critical section。
- publication safety: TM/QM publish 前执行 catalog/source view stale check，且
  binding resolver 在与 Runtime/MCP mutation 同一 monitor 内复核 identity 并
  commit catalog swap；该 supporting guard 不等于 Batch 5 refresh convergence。
- compatibility: legacy resolver 不接收 empty namespace；只有显式
  `ProcessLocalDefaultDataSourceResolver` opt-in default binding。Runtime composite
  resolver 过滤 AOP self proxy、按稳定 domain 顺序嵌套 external guards，并对
  decorator recursion fail closed。
- touched production paths: model `lifecycle/{concurrent,catalog,port}`、TM/QM
  loaders、catalog discovery、default resolver SPI；Runtime registry/resolver；
  MCP datasource manager/resolver。
- development: completed
- testing: passed；authoritative run `20260713T164144Z-1910217` = single-flight
  core 50 + catalog 34 + SQLite 33 + namespace 3 + Runtime guard 5 + MCP guard
  14 = 139，failures/errors/skipped=`0/0/0`；100 callers、99 waiters、build=1、
  residual flight=0。
- cross-batch regression: Batch 3 replay `20260713T164538Z-1920394` = 164
  green；post-Batch4 Gate `20260713T165330Z-1941345` = positive 5 +
  expected-negative 4/4；remaining-red replay 见 Batch 4 exit record。
- source audit: long build lock=0；owned executor/thread=0；sleep-driven
  Batch 4 tests=0；promoted distinct-key red reference=0；`rg` scan errors fail
  closed。
- deviations/review closure: binding publication TOCTOU、waiter ThreadLocal、
  six key dimensions、cancel/checked-timeout shared failure、observer `Error`、
  process-local default compatibility、Runtime provider ownership/AOP/decorator/
  multi-external composition 均先补确定性回归再收口。
- experience: N/A（纯后端 concurrency/lifecycle）
- self-check decision: `self-check-only`；独立只读复核 no blocker/major；
  正式质量闸门仍由 Batch 7 执行。
- evidence: `evidence/batch-4/single-flight-exit-20260713.md`

## Batch 5 Execution Check-in

- started_at: 2026-07-13
- completed_at: 2026-07-14
- mode: single-root-delivery / progress-update
- entry evidence: `evidence/batch-5/entry-checkin-20260713.md`
- exit evidence: `evidence/batch-5/atomic-refresh-exit-20260714.md`
- scope: detached namespace/model candidate、single atomic publication、committed
  source events、unknown-scope admission、Runtime validate/refresh DTO、
  Runtime/MCP datasource convergence。
- criteria: REFRESH-ATOMIC、REFRESH-FAIL、REFRESH-SCOPE、EVENT-CONVERGENCE、
  SOURCE-COMMIT、VALIDATE-ISOLATION；API-COMPAT 仅 supporting evidence，
  criterion 仍归 Batch 7。
- non-goals: Batch 6 model/MCP catalog single authority、L1/L2/Redis/Pivot
  generation consumer、cross-JVM cold key、完整 REAL-QUERY；Batch 7 formal
  gates；9.3.4 full、9.3.5、9.4.0。
- completed: per-namespace coordinator；exact source/catalog/binding view；
  detached complete/model-scope candidate；source/binding final currentness；
  conditional failure marker；one publish；named refresh NamespaceScope；
  bundle/file/Runtime/MCP convergence；unknown-scope admission block；detached
  Runtime validate；additive typed/sanitized Runtime lifecycle response。
- Runtime hardening: datasource/namespace canonicalization covers controller、
  service and v1/v2 persisted registry migration；canonical collision fails
  closed；binding summary sanitization removes physical datasource/path/
  credential material；refresh failure reports latest admission state。
- development: completed
- authoritative testing: final run `20260713T200646Z-2120785` = model unit 30 +
  SQLite Failsafe IT 2 + fsscript 7 + Runtime 34 + MCP 17 = 90 tests in 19
  owning reports，failures/errors/skipped=`0/0/0`。
- real SQLite evidence: REF-01 blocked readers and post-publish readers observe
  complete old/new QueryFacade identity/model/native result only；REF-02 failed
  candidate publishes zero and old real query remains usable while binding is
  current。REF-03/04 are deterministic model/adapter evidence, not mislabeled
  as SQLite IT；REF-01/02 也不单独提升完整 `REAL-QUERY`，后者与
  catalog/cache/Pivot consumer parity 仍归 Batch 6。
- final ordered regression: Batch 4 replay `20260713T201031Z-2124453`=140；
  Batch 3 replay `20260713T201525Z-2129344`=166；post-Batch5 entry gate
  `20260713T201941Z-2133081`=5 positive + 4/4 expected-negative；Batch 2 replay
  `20260713T202421Z-2138021`=25+7；remaining red
  `20260713T202645Z-2141955`=only Batch 6 `CATALOG-AUTHORITY` 1/1 expected-red。
- source audit: sleep-driven frozen tests=0；production Runtime/event clear-first
  paths=0；promoted Batch 5 red references=0；refresh-owned thread/executor=0。
- failure-driven closure: real IT first exposed missing named refresh scope；
  review then closed mixed tracked/untracked guard bypass、late failure overriding
  a concurrent winner、registry whitespace migration、binding-summary leak and
  stale Runtime failure state；tests were not weakened。
- final code audit closure: mixed provenance 仍对每个 known binding 执行
  currentness guard；datasource mutation 在 pool callback 前先阻断 catalog
  admission；hostile diagnostic key/value 均执行脱敏；datasource ID
  canonicalization 保留原 credentials，不因规范化丢失密钥材料。
- residual review: Batch 7 formal quality gate must inspect mutation callback
  latency/multi-binding retire failure atomicity、typed internal admission reason、
  source seqlock retry exhaustion and committed-mutation retry semantics；no
  frozen Batch 5 test currently demonstrates a remaining contract failure。
- experience: N/A（纯后端 lifecycle/source/binding convergence）
- self-check decision: `self-check-only / pass`；正式质量闸门、coverage audit、
  version acceptance 均未提前执行。

## Batch 6 Catalog Authority Execution Check-in

- started_at: 2026-07-14
- checkpoint_at: 2026-07-14
- mode: single-root-delivery / progress-update
- entry evidence: `evidence/batch-6/entry-checkin-20260714.md`
- criterion evidence:
  `evidence/batch-6/catalog-authority-green-20260714.md`
- checkpoint scope: model/MCP namespace catalog single authority、完整 immutable
  view、exact binding provenance、native/MCP metadata bounded seqlock、Spring
  shared authority wiring，以及 historical expected-red promotion。
- completed: `SemanticModelCatalogService` 只读取 active snapshot；complete
  view 直接投影，absent/incomplete 只经 `CatalogRefreshCoordinator` 恢复并重读；
  blocked/partial/provenance absent 均 fail closed；exact empty/deletion 不做旧名称
  union。view 在同一 identity 下绑定 names、aliases、models 和 per-model exact
  `CatalogResolution`。
- MCP consumers: resolver 与 model catalog 共用 model namespace view；无独立
  names cache、目录 watcher 或重复 invalidation authority；动态 metadata 使用
  pinned resolutions。native 与 MCP metadata callback 均执行最多三次的完整
  operation seqlock，generation churn/admission block 不返回 hybrid。
- Spring wiring: resolver 与 MCP catalog 的 tracked constructors 注入同一
  `SemanticModelCatalogService` authority bean，且不触发 legacy loader/bundle
  interaction；model service 的 store/coordinator 注入由 model test/source
  audit 证明。legacy/custom loader 保留 identity-null、无缓存兼容路径。
- touched production paths: model `SemanticModelCatalogService`；MCP
  `SemanticServiceResolverImpl`、`ModelCatalogService`。
- touched owning tests: model `SemanticModelCatalogServiceTest`；MCP
  `CatalogNamespaceAuthorityTest`、`CatalogAuthoritySpringWiringTest`；resolver、
  ListModels 与 Controller consumer regressions。
- authoritative testing: `scripts/verify-v933-batch6-catalog-authority.sh`；run
  `20260714T021057Z-2445113` = 53 tests / 7 fresh owning reports /
  failures/errors/skipped=`0/0/0`；direct authority 17（model
  11 + MCP authority 5 + Spring wiring 1），supporting consumers 36（resolver
  11 + ListModels 21 + controller 4）。
- integrity: `summary.env` SHA-256=
  `62604e1053c328c3c219da2f8792cdcb1d9ab13878f5d8ad021055ea7ea21563`；
  `SHA256SUMS` SHA-256=
  `5b017cb750b0d28f4df8cd1998b63f27f46edcad25e27b9edda37aaa13fda3a1`。
- remaining-red replay: run `20260714T021251Z-2449309` = 0 cases / 0 tests /
  0 sources；`summary.tsv` SHA-256=
  `690b22d6cad493e293c6d9bbd5d8c7f37d7b027f06c46d82ba2c9306ef7a5406`。
- superseded evidence: run `20260714T014919Z-2373851` 仅为 diagnostic，标记
  `superseded-by-review`；即使内部 summary=`passed` 也不计入 criterion 或测试
  总数。
- criterion decision at this Catalog checkpoint: 只提升 `CATALOG-AUTHORITY`
  为 passed；当时 `CACHE-GEN`、`CACHE-CROSS-JVM`、完整 `REAL-QUERY` 仍
  pending。Steps 2–4 的后续状态见下一 check-in。
- next order: Step 2 L1/L2/Caffeine/Redis strong key；Step 3 双
  ApplicationContext；Step 4 shared Redis 双 JVM；Step 5 Pivot strong identity
  且 manual token 仅 additive；Step 6完整 `REAL-QUERY`；Step 7
  runner/replays/exit。
- experience: N/A（纯后端 catalog authority/metadata 一致性治理）。
- self-check decision: `self-check-only / criterion pass`；Batch 6 尚未满足
  exit，正式 quality/coverage/acceptance 不提前执行。

## Batch 6 Steps 2–4 Cache Identity / Cross-JVM Review Check-in

- checkpoint_at: 2026-07-14
- mode: single-root-delivery / progress-update
- evidence:
  `evidence/batch-6/cache-identity-cross-jvm-green-20260714.md`
- scope: 保留 Step 2 QueryFacade/L1/L2 strong-key 与 Step 3 two-context direct
  evidence；以通过独立二审的 replacement run 收口 Step 4 production-path
  two-JVM authority。
- Step 2 completed: model `QueryFacadeCatalogIdentityTest` 4/0/0/0；addon
  focused 68/0/0/0（StrongIdentity 12 + Caffeine 30 + Redis 25 + SQLite
  datasource isolation 1）。缺失、冲突、不完整或 malformed
  catalog/source/binding identity 均 no-key；catalog/binding/source/backend
  变化同时轮换 L1/L2。
- Step 3 completed: `QueryCacheKeyCrossApplicationContextTest` 1/0/0/0；两个
  真实 Spring contexts 中 provider/builders 实例不同，同一完整身份生成相同
  L1/L2 key，catalog 或 binding generation 变化均使两层 key 改变。
- Step 4 passed: authoritative run `20260714T025444Z-2628329` 使用 Redis
  7.4.6、production auto-configured template/provider、真实 non-empty published
  snapshot 派生的 exact resolution 与两个 child JVM（PIDs `2629762` /
  `2630070`）；independent second review found no blocker。
- independently checked state: 27 probes；两进程相同 binding；old physical-key
  read controls=2（仅物理 key 可读控制，不代表 lifecycle resolution/provider 命中）；
  restart current-identity misses=2；post-write hits=2；Redis `SCAN`=4 且
  `DBSIZE`=4。
- identity audit retained for Steps 2/3: `QueryCacheKeyBuilder` 不含 instance
  UUID/object address/`identityHashCode` fallback；boot UUID 只由
  `CatalogSnapshotStore` 作为预期 process-cold catalog/source epoch 生成。该
  source boundary 不足以替代有效的 Step 4 production-path test。
- touched production paths: model `QueryFacadeImpl`、`ModelResultContext`；cache
  addon `QueryCacheKeyBuilder`、`CaffeineQueryCacheProvider`、
  `RedisQueryCacheProvider`。
- focused evidence integrity: model log SHA-256=
  `c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048`；
  addon log SHA-256=
  `370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a`；
  Step 3 XML/log SHA-256=
  `cc252b5de28832631b19257245c831eaabaa0002769e6078379da770676c37cb` /
  `fa7e0f962eda61cb8f97801ccc2a458fed8e91c861e43d6660219b30eb119563`。
- Step 4 authoritative integrity: owning XML 1/0/0/0 SHA-256=
  `93e92cf913098bbd110da8b0a98fd2a16414b2fe615e7fc98edb9277dffd51f9`；
  `summary.env` SHA-256=
  `ff76b8a81360606e97a316797f40f7d145951c56e7e803911be030d4a3e5358e`；
  `SHA256SUMS` SHA-256=
  `a54b7a4b18b750a5afb99fc3ee47b91af4759d0420f60cf9fb4c28edb1950ed3`；
  source manifest SHA-256=
  `f7c53054262502c7a348ea93e0ed9e12d461e2c18c4dd2105563b03ab300eea6`。
  两层 hash check 通过；container cleaned；evidence 无 credentials。
- diagnostic exclusion: `20260714T023304Z-2522929` 是 failed diagnostic；
  `20260714T023442Z-2527116` 因 test-only serializer、empty snapshot/manual
  resolution 与 runner self-report window 为 diagnostic/superseded；
  `20260714T025247Z-2623994` 虽 product test green，但 runner model-segment
  parser failed，亦为 diagnostic/superseded。三者均不计入 pass。
- Step 4 checkpoint decision（historical）：`CACHE-CROSS-JVM` passed；当时
  Steps 2/3 仅为 `CACHE-GEN` direct partial。此后 Steps 5–6 已通过，详见后续
  check-in；最终 Step 7 aggregate 通过，Batch 6 completed。
- experience: N/A（纯后端 cache identity/process isolation）。
- self-check decision: `Step 2/3 direct partial retained / Step 4 criterion
  pass`；已进入 Step 5，但未提前提升 `CACHE-GEN`，也未进入正式
  quality/coverage/acceptance。

## Batch 6 Step 5 Pivot / CACHE-GEN Execution Check-in

- checkpoint_at: 2026-07-14
- mode: single-root-delivery / execution-checkin
- evidence:
  `evidence/batch-6/pivot-cache-generation-green-20260714.md`
- authoritative run:
  `target/v933-batch6-pivot/runs/20260714T032135Z-2678670/`
- scope: Pivot outer-cache strong lifecycle identity、pipeline read/write pin、
  diagnostics/telemetry、Semantic service 与 managed-relation pre-pin、
  QueryFacade mid-request generation fail-closed，以及 public compatibility。
- completed identity: catalog generation + source revision + canonical
  namespace/model + exact sorted dependency bindings 使用 length-framed full
  SHA-256；provider/manual tokens 仅 additive，不能替代或挽救 lifecycle
  identity。
- completed fail-closed: missing、incomplete、JDBC-empty、conflicting identity
  与 supplementary provider exception 均 no lookup/no store；provider failure
  诊断只暴露规范化异常类型，不含 message/token/binding/stack。
- blocker closure: `PivotPipelineCatalogIdentityTest` 补齐第 8 个 direct case
  `completeLifecycleStillRefusesCacheWhenSupplementaryProviderThrows`，证明
  lifecycle identity 完整时 provider exception 仍拒绝 cache I/O。
- entry pinning: `SemanticQueryServiceV3Impl` 与 managed relation 有 direct
  pre-pin cases；QueryFacade generation conflict 在 filters/query 前拒绝，旧
  key 不会在切代后写入。
- compatibility: Pivot public record components、provider SAM、legacy
  `OuterCacheOptions` constructor 与 component wiring 保持。
- direct testing: 38/0/0/0 in 4 fresh reports：strong identity 4 + pipeline 8 +
  QueryFacade 6 + SemanticRequestContext 20。
- supporting testing: 61/0/0/0 in 3 fresh reports：Pivot integration 55 +
  operational SPI 3 + telemetry 3。supporting lane 保留既有 bounded 20 ms TTL
  wait；direct identity lane sleep/skip=0，不削弱 criterion。
- integrity: `summary.txt` SHA-256=
  `45f9cc2ab69f6e63434606be645b607a430869ca1a6ff073ce16a709163c40d7`；
  source manifest SHA-256=
  `50f3dc8fd40a9bde4d88f5487318730f54a8be22eeeb011ff0cbfb2fe207849b`；
  outer `SHA256SUMS` SHA-256=
  `e7014adaaa1932d62993d2a051830e9bb3f3b2fb4c7a982f356b32fc67d04ad7`。
  source/outer manifests 均独立 `sha256sum -c` 通过；两 Maven lanes 各且仅有
  一次 `BUILD SUCCESS`；7 XML 均 fresh；sensitive output=0。
- touched production paths: Pivot `Pipeline`/strong identity/telemetry/
  diagnostic/model identity/provider，`SemanticRequestContext`、
  `SemanticQueryServiceV3Impl`、`QueryFacadeImpl`、`ModelResultContext`。
- criterion decision: independent second review no blocker；Step 5 signed off，
  `CACHE-GEN` passed。其后的 Steps 6–7 已通过；Batch 6 completed，Batch 7
  ready/not-started。
- experience: N/A（纯后端 Pivot/cache lifecycle identity）。
- self-check decision: `Step 5 criterion pass`；正式 quality/coverage/acceptance
  仍待 Batch 7，未提前执行。

## Batch 6 Step 6 REAL-QUERY Execution Check-in

- checkpoint_at: 2026-07-14
- mode: single-root-delivery / execution-checkin
- evidence: `evidence/batch-6/real-query-green-20260714.md`
- authoritative run:
  `target/v933-batch6-real-query/runs/20260714T041047Z-2755326/`
- result: 11/0/0/0 in 6 fresh Failsafe reports；model lifecycle 4、required
  SQLite/MySQL 5.7/PostgreSQL 15 parity 各 1、Caffeine 2、Redis 2；六个 serial
  lanes 各且仅有一次 `BUILD SUCCESS`。
- lifecycle boundary: atomic old/new refresh + sibling preservation、same-name
  namespace isolation、datasource rebind admission/lease drain/stale-publication
  rejection、Pivot native `SUM` miss→hit→generation miss→hit 全部通过
  production QueryFacade/Semantic/Pivot path。
- native parity: rows、columns、column order 与 values 直接逐项比较，未排序实际
  输出制造一致；SQLite 3.42 sentinel 8/2、MySQL 5.7 25/25、PostgreSQL
  15.17 25/25。
- cache boundary: Caffeine/Redis 均由 production Spring auto-configuration
  选择；L1-on 与显式 L1-off 的 L2 流程均证明同代命中、catalog generation
  切换后 miss；real Redis 7.4.6 初始/最终 keys=0 且 run-owned container removed。
- integrity: summary/source/inner/outer SHA-256=
  `5602a3a75b6fb99d938ace19f7ca6b6528d0a9876fc2f799db14d24df5604f9e` /
  `95aa4d9d5fbe31465724ad87eb704b531e04a4a2be0393f1a0b8afc2baf61bb0` /
  `071987f7df30a3d08a6a194bf77f39a0861853ce36470db19090a36fd351f1aa` /
  `6464baeda9e899736f3031dea27f1ceafc4a0b72e3863dbcafcbce499ce17cfe`；
  source/inner/outer checks passed。
- checkpoint-time review/decision: independent post-run review no blocker；Step 6
  signed off，`REAL-QUERY` passed。其后的 Step 7 aggregate 已通过，Batch 6
  completed；当时 Batch 7 为 ready/not-started（现已 completed 并 version signoff）。
- self-check decision: `Step 6 criterion pass`；未提前执行 Batch 7 formal
  quality/coverage/acceptance。

## Batch 6 Step 7 Cache-Identity Fresh Replay Check-in

- evidence: `evidence/batch-6/cache-identity-replay-green-20260714.md`
- authoritative run:
  `target/v933-batch6-cache-identity/runs/20260714T044313Z-2824177/`
- result: Step 2 current source 74 tests（QueryFacade 6 + addon 68）+ Step 3 one
  two-context test = 75/0/0/0 in 6 fresh reports；three lanes each have exactly
  one `BUILD SUCCESS`；independent post-run review no blocker。
- accounting note: historical Step 2 focused evidence remains 72 because
  QueryFacade then had 4 methods；Pivot work added two generation-switch/
  idempotence assertions, so the Step 7 fresh replay authority is 74 + 1。
- integrity: summary/source/inner/outer SHA-256=
  `b8cff91a339df48b8ba79549af40948bae1a0bad1618a8f809266bd0898e2e9c` /
  `0563a797fcdb4a9c33e8e176851b50b466012734340f2eb077b5a28763dfe053` /
  `143bbccea012ff2a8ed4d183bb468dfbde7770953bdac021d959b1fdc16855ea` /
  `ffc7a11de0fcd2e9b3ec843ccdc2471c5636bd6df6ddfd72b63887991825e296`。
- diagnostic exclusion: run `20260714T044020Z-2817047` 虽测试绿色，但因旧
  runner 在 latest publication 后仍有输出窗口而 superseded，不计 authority。
- current decision: 该 authority 已作为 aggregate child 02 fresh replay；Step 7
  aggregate 随后通过，Batch 6 completed。

## Batch 6 Step 7 Aggregate Exit Check-in

- evidence: `evidence/batch-6/batch-6-exit-20260714.md`
- authoritative run:
  `target/v933-batch6-exit/runs/20260714T045604Z-2854237/`
- result: 11 ordered children、676 criterion tests、677 asserted XML
  testcases、99 reports、failures/errors/skipped=`0/0/0`；4/4 entry-gate
  expected-negative fail closed；remaining-red sources/tests=`0/0`。
- exact child accounting: Catalog 53/7、Cache Identity 75/6、cross-JVM 1/1、
  Pivot 99/7、REAL-QUERY 11/6、Batch 5 90/19、Batch 4 current 142/22、
  Batch 3 current 168/20、entry positive 5/5、Batch 2 criteria 32 / XML 33/6、
  remaining-red 0/0。
- one-test delta: Batch 2 的 25 product + 7 compatibility = 32 criteria；额外
  deterministic harness probe 被执行并断言，因此 XML testcase 总数为 677，
  不重复计入 `NS-SCOPE` criterion。
- integrity/environment: all child manifests initial/final verified and
  unchanged；source/dirty worktree/fixed containers before-after unchanged；
  two run-owned Redis containers removed；aggregate latest pointer correct；
  no Maven process or sensitive evidence leak。
- aggregate summary/children/source/inner/sealed/outer SHA-256=
  `10d72c2daa76361861a3796d0bc5699e1f09fecd53f82fa4b185b05ec8c803b1` /
  `b2ddfc24c44c762464fb4df54a5532b58a51f433fedbbf030239b871dc4e052a` /
  `e22630c9b1ccbc102861e1396bc9c55a68bab6d5557ada6a5c1fa86f0fdfdea1` /
  `3318f2e69e3d41de57886a56cd70f23e0dbf76d53186f8a32f7eb5820366223f` /
  `81a6456852fdb41f14eea316a60f4eea2f27b632dfc710c637a38f7435edab6d` /
  `fc73ce9e1e19ce14c2733706daf720bf3af6a7fa35688fad54c43285ea2a43a9`。
- checkpoint-time review/decision: two independent read-only post-run reviews
  returned `NO BLOCKER`；Batch 6 Step 7 passed，Batch 6 completed；当时 Batch 7
  ready/not-started、9.3.3 in-progress（现已 completed / signed-off）。

## Version Implementation Self-Check（Batch 7 closure）

以下是全版本清单；2026-07-14 在 Batch 7 权威回归和独立审计完成后统一复核：

- [x] requirement/contract scope 已全部收口；9.3.4 full matrix/coverage、9.3.5 typed execution/API、9.4.0 SPI v2 仍明确属于 downstream。
- [x] 没有新增全局锁、生产 clear-first、mutable snapshot 暴露或双 catalog authority；Catalog prepare 已移出 binding monitor，guard 内仅保留 currentness 与 atomic swap。
- [x] Runtime validate/refresh、bundle add/remove、已有/新增 file change、Runtime/MCP datasource save/remove/disable/rebind 入口均进入同一 lifecycle authority。
- [x] binding mutation admission、committed SourceRevision 与 unknown-scope stale 状态均按 contract 收口；新查询不回退旧 binding/source，在途连接只按冻结 DRAIN/HARD 策略退出。
- [x] source audit 与 `git diff --check` 未发现 debug 分支、生产 `Thread.sleep`、吞异常、永久兼容双写或明文凭据；所有并发等待有界。
- [x] NamespaceScope、CatalogIdentity/generation、binding generation 与 Runtime DTO 均有兼容说明；legacy validate/refresh envelope 以 additive、sanitized 基础类型结构保留。
- [x] 全部回归实际执行；首次 Batch 7 run 保持 superseded，replacement run
  `20260714T084351Z-3271604` 从头执行并独立复算通过。
- [x] test evidence、计划外变更、8 个 BUG、风险与 downstream 已按 replacement
  authority 重新核对；无 implementation blocker/high/medium。
- version self-check decision: `passed-to-formal-quality-gate`
- version self-check reviewer/date: Codex implementation owner / 2026-07-14
- version self-check evidence:
  `evidence/batch-7/batch-7-regression-exit-20260714-r2.md`

## Planned Deviations / Unplanned Changes

| 项目 | 原计划 | 实际变化 | 原因 | 是否更新 requirement/contract |
|---|---|---|---|---|
| expected-red report isolation | 直接指定 module report directory | standard Surefire modules 删除单个旧 owning report后复制 fresh XML 到 unique run directory；model profile 继续直接指定目录 | `surefire.reportsDirectory` 不是所有模块可用的 CLI property；需关闭 stale/multi-suite 伪绿窗口 | evidence runner updated；contract unchanged |
| Batch 3 Semantic nested reports | runner 预期一个 Semantic owning report | 精确接纳 owning class + 两个 nested-class XML，并分别断言 14/5/4 | JUnit Platform 为 nested classes 生成独立 owning report；首次 run 按 fail-closed 契约停止 | runner/evidence updated；contract unchanged |
| external loader compatibility | 旧 loader 可无 lifecycle identity 继续工作 | 明确标记 untracked；同名 TM fresh instance 拒绝发布 | 保留 additive SPI 的同时保证 snapshot 对象图一致 | implementation/test updated；contract unchanged |
| final binding publication guard | Batch 5 统一处理 mutation→refresh | Batch 4 loader publication 在 adapter mutation monitor 内完成 final currentness check + catalog swap | 关闭 build 后 check 与 publish 间旧 binding TOCTOU；不实现 refresh/event convergence | supporting safety pulled forward；Batch 5 criteria unchanged |
| process-local default binding | 旧 resolver 兼容 default namespace | default 只调用显式 marker SPI，旧 resolver 永不收到 empty namespace | additive SPI 同时避免把 default 误路由到 named resolver | implementation/test updated；contract unchanged |
| remaining-red replay drift | Batch 6 event/list red 持续失败 | 旧断言被 model-side cache removal 旁带转绿；替换为双 namespace global-cache split red，并使用 reactor-current `-am` + 三态 `rg` | 旧断言未覆盖 shared snapshot identity，且 module-only run 会漂移到本地 Maven jar | historical baseline retained；Batch 6 `CATALOG-AUTHORITY` unchanged |
| Catalog Authority review supersession | 初次绿色 run 可作为 criterion evidence | `20260714T014919Z-2373851` 降为 diagnostic；补 coordinator-only recovery、完整 binding provenance、native/MCP bounded seqlock 与 Spring shared wiring 后，以 53-test run 重新冻结 | 初次测试未覆盖 review 识别的 intermediate/union-delete/hybrid metadata/binding guard 与装配边界 | criterion contract unchanged；authoritative evidence replaced |
| cross-JVM authority review | Step 4 real Redis/two-JVM run 可直接冻结 | `20260714T023304Z-2522929` failed；`20260714T023442Z-2527116` 因测试专用 serializer、空 snapshot 手工 resolution、runner self-report window 被否决；`20260714T025247Z-2623994` product test green 但 runner model-segment parser failed；最终 `20260714T025444Z-2628329` 以 production auto-config、snapshot-derived resolution、27 probes 与独立 Redis/hash checks 通过二审 | serializer 可读性、真实 lifecycle pin 与 runner 独立断言都必须由同一 production-path evidence 证明 | 前三次 run diagnostic/excluded；replacement authority accepted；`CACHE-CROSS-JVM` passed |
| Pivot provider exception blocker | complete lifecycle identity 足以进入 cache I/O | supplementary provider failure 必须显式 refusal；新增 pipeline 第 8 个 direct case，完整 lifecycle identity 下仍 no lookup/no store，且 diagnostic class-only | provider/manual identity 是 additive 且可能失败，不能以 core identity 完整为由绕过 provider refusal | criterion contract clarified by regression；`CACHE-GEN` passed |
| Step 7 cache replay count drift | 沿用 Step 2 历史 72-test focused count | fresh runner 按当前源执行 QueryFacade 6 + addon 68 + Step 3 one = 75；旧 72 只保留为历史 checkpoint | Pivot work 为 QueryFacade 增加两项 generation-switch/idempotence assertion，出口不能使用陈旧数量契约 | new fresh authority `20260714T044313Z-2824177`；Batch 3/4 runner counts 同步到 current source |
| Step 7 aggregate accounting | criterion tests 与 XML testcases 使用同一总数 | 676 criterion tests / 677 asserted XML testcases；额外 1 是 Batch 2 deterministic harness probe，执行且断言但不重复计入 Namespace criterion | 同时保留准出 criterion accounting 和全量 report anti-false-green assertion | aggregate runner/documentation explicitly records both totals；contract unchanged |
| Batch 7 new-file watcher ownership | 已有 file watcher 覆盖 runtime mutation | 增加 fsscript/source-owned recursive directory watcher，先 commit source revision 再发布 exact-scope event；移除 MCP 残留 watcher | 运行期新 `.qm` 与新子目录原路径无 authority | `BUG-933-NEW-MODEL-WATCHER` RED→GREEN→authority closed；contract unchanged |
| Batch 7 multi-binding retire scheduling | 每个 binding 独立安排 bounded drain | 两阶段先统一关闭全部 affected admission；单个 schedule 拒绝时 hard revoke 当前 binding 并继续其他 binding | 原循环在首次 scheduler failure 后提前退出并留下旧 binding open | `BUG-933-MULTI-BINDING-RETIRE` RED→GREEN→authority closed；fail-closed contract restored |
| Batch 7 catalog prepare monitor boundary | candidate commit 在 binding guard 内完成 | validate/freeze/seal 在 guard 外 prepare；guard 内只做 binding/source/base/store 终检与 swap | O(catalog) prepare 扩大 datasource mutation monitor 临界区 | `BUG-933-CATALOG-PREPARE-MONITOR` RED→GREEN→authority closed；contract unchanged |
| Batch 7 Pivot diagnostic priority | lifecycle identity refusal 先于 request-shape refusal | 稳定为 request shape reason first，同时 identity 仍独立 fail-closed 且 no cache I/O | SQLite cascade regression 的可观测 reason 漂移 | `BUG-933-PIVOT-DIAGNOSTIC-ORDER` RED→GREEN→SQLite full authority closed |
| Batch 7 Runtime full Controller compatibility | focused API inventory 足以冻结 DTO | 将完整 48-test Controller suite 纳入 API lane；恢复 additive legacy diagnostics，测试改用 coordinator；package 使用真实 skip property | 意外 package test 暴露 3 个旧 consumer/fixture 回归 | `BUG-933-RUNTIME-MODEL-CONTROLLER-FULL-SUITE` RED→GREEN→authority closed；replacement API inventory frozen at 62/6（含 sanitizer 4） |
| Batch 7 watcher register/scan fixed-point | register-first + 一次 reconciliation 可关闭窗口 | 改为最多 8 轮 fixed-point；发现 scan 前未 watched child 时丢弃该轮 source snapshot，必须再取得 stable scan；超限 unknown fail-closed | 二审发现 reconciliation 新 child 仍存在第二窗口 | `BUG-933-WATCHER-REGISTER-SCAN-RACE` RED→GREEN→replacement authority closed |
| Batch 7 watcher authority loss | WatchService 日志/cleanup 足以表达失效 | 新增 additive loss callback/reason；OVERFLOW、invalid key、watched child delete、file-watch failure、reconciliation exhaustion 统一 unknown-scope commit，按 epoch 幂等 | 日志/移除 WatchKey 不会推进 committed revision，旧 catalog 可能继续 ACTIVE | `BUG-933-WATCHER-AUTHORITY-LOSS` RED→GREEN→replacement authority closed |
| Batch 7 Runtime diagnostic composite bound | Map depth cutoff 可覆盖 diagnostics | Map/Collection 共用 depth=5、width=100；20,000 层纯 List 稳定截断 | 纯 Collection 可绕过 Map depth guard 并 StackOverflow | `BUG-933-RUNTIME-DIAGNOSTIC-COLLECTION-DEPTH` RED→GREEN→replacement authority closed |

## Testing Progress

| 验证层 | 状态 | Tests/Failures/Errors/Skipped | 命令/报告路径 |
|---|---|---|---|
| 9.3.4-A negative/positive gate | passed | positive 5/0/0/0；expected-negative 4/4 | aggregate child `target/v933-entry-gate/runs/20260714T052029Z-2908765/` |
| Batch 1 expected-red baseline | captured（not product pass） | 12/12/0/0 | `scripts/verify-v933-batch1-red-baselines.sh`；run `target/v933-batch1-red/runs/20260713T115746Z-1058249/` |
| Batch 6 entry remaining-red baseline | captured（historical, not product pass） | `CATALOG-AUTHORITY` 1 suite / 1 expected-red test；0 errors/skips | entry run `target/v933-batch1-red/runs/20260713T202645Z-2141955/` |
| Batch 6 Catalog Authority | passed | 53/0/0/0 in 7 owning reports；direct 17 + supporting 36 | `target/v933-batch6-catalog/runs/20260714T021057Z-2445113/`；criterion evidence `evidence/batch-6/catalog-authority-green-20260714.md` |
| post-Catalog remaining-red replay | passed（none remaining） | 0 cases / 0 tests / 0 sources | `target/v933-batch1-red/runs/20260714T021251Z-2449309/`；`summary.tsv` SHA-256=`690b22d6cad493e293c6d9bbd5d8c7f37d7b027f06c46d82ba2c9306ef7a5406` |
| Batch 6 Step 2 model identity pin | passed；historical direct contribution to now-passed `CACHE-GEN` | 4/0/0/0 | `QueryFacadeCatalogIdentityTest`；`target/v933-batch6-cache-model-pin.log` SHA-256=`c139540a97d1e7fe55ee4bbe5aa200d0b3e5ee250aa3ebf97ced41b8dfdcc048` |
| Batch 6 Step 2 addon strong key | passed；historical direct contribution to now-passed `CACHE-GEN` | 68/0/0/0 | StrongIdentity 12 + Caffeine 30 + Redis 25 + SQLite isolation 1；focused log SHA-256=`370481a259c84376fc9e83ea84586534c94c0c4cae47d93d3d2f6e5ce97b555a` |
| Batch 6 Step 3 two ApplicationContexts | passed；historical direct contribution to now-passed `CACHE-GEN` | 1/0/0/0 | `QueryCacheKeyCrossApplicationContextTest`；XML/log SHA-256=`cc252b5de28832631b19257245c831eaabaa0002769e6078379da770676c37cb` / `fa7e0f962eda61cb8f97801ccc2a458fed8e91c861e43d6660219b30eb119563` |
| Batch 6 Step 4 independent-JVM Redis epoch | passed | 1/0/0/0 in 1 owning report；2 child JVMs；27 probes | authoritative `target/v933-batch6-cache-cross-jvm/runs/20260714T025444Z-2628329/`；production auto-config template/provider；snapshot-derived exact resolution；same binding；old physical-key read controls=2；current identity misses=2；post-write hits=2；Redis `SCAN`=`DBSIZE`=4；two-layer hashes；independent second review no blocker |
| Batch 6 Step 5 Pivot/CACHE-GEN | passed | direct 38/0/0/0 in 4 reports；supporting 61/0/0/0 in 3 reports；total 99/0/0/0 | authoritative `target/v933-batch6-pivot/runs/20260714T032135Z-2678670/`；full SHA-256 lifecycle identity、provider/manual additive only、all refusal gates no lookup/store、provider exception direct case；independent second review no blocker |
| Batch 6 Step 6 REAL-QUERY | passed | 11/0/0/0 in 6 Failsafe reports | authoritative `target/v933-batch6-real-query/runs/20260714T041047Z-2755326/`；model lifecycle 4 + SQLite/MySQL 5.7/PostgreSQL 15 parity 1 each + Caffeine 2 + real Redis 2；native rows/columns/order/values parity；independent second review no blocker |
| Batch 6 Step 7 cache-identity fresh replay | passed child authority | 75/0/0/0 in 6 reports；Step 2=74，Step 3=1 | authoritative `target/v933-batch6-cache-identity/runs/20260714T044313Z-2824177/`；current QueryFacade 6 + addon 68 + two-context 1；independent second review no blocker |
| Batch 6 Step 7 aggregate exit | passed | 676 criteria / 677 XML testcases / 99 reports；F/E/S=0；expected-negative 4/4；remaining red 0/0 | authoritative `target/v933-batch6-exit/runs/20260714T045604Z-2854237/`；11 strict children；source/child/inner/outer/dirty/container checks passed；two independent reviews no blocker |
| Batch 7 first compatibility/regression run | superseded sealed history | 3813/0/0/3 in 517 reports；不计入 final authority | `target/v933-batch7-regression/runs/20260714T074009Z-3153871/`；formal quality finding 后失效 |
| Batch 7 replacement compatibility/regression authority | passed；independent review `NO BLOCKER` | 3824/0/0/3 in 519 reports；three skips exact SQLite allowlist | `target/v933-batch7-regression/runs/20260714T084351Z-3271604/`；513 parent Surefire + 6 REAL-QUERY child Failsafe reports；top-level manifest SHA-256=`e8593ba0a3cb5acbce4308875159aa653b59e363ac33e96d7e75378fc258bc4d` |
| NamespaceScope product/compatibility | passed | product 25/0/0/0；legacy 7/0/0/0；additional harness probe 1/0/0/0 | aggregate child `target/v933-batch2-namespace/runs/20260714T045604Z-2854237-10-batch2/`；criteria 32 / asserted XML 33 |
| QueryFacade/Semantic/alias SQLite regressions | passed | 33/0/0/0 | Batch 3 `sqlite-consumer-regression` lane |
| snapshot/generation state transitions | passed | 46/0/0/0 catalog authority lane | Batch 3 final run；exact owning reports |
| single-flight deterministic concurrency | passed | aggregate Batch 4 current-source replay 142/0/0/0 in 22 reports；100 callers、99 waiters、build=1、residual=0 | child run `20260714T045604Z-2854237-07-batch4` |
| atomic refresh SQLite IT | passed | 2/0/0/0 | Batch 5 Failsafe `CatalogRefreshQueryIT`；aggregate child `20260714T045604Z-2854237-06-batch5`；owns REF-01/02 only, not full `REAL-QUERY` |
| datasource binding Runtime/MCP IT | passed | aggregate Batch 3 current-source replay 168/0/0/0 in 20 reports | child run `20260714T045604Z-2854237-08-batch3`；persisted/cold generations + pinned handles |
| binding revoke/admission/drain IT | passed | included in Runtime 10 + manager 12 + MCP 14 | manual clock/scheduler/latch + real H2 old/new sentinel；no sleep |
| source revision/unknown-scope IT | passed | included in Batch 5 model/fsscript/Runtime/MCP lanes | committed revision、stale candidate rejection、persistent admission block、exact namespace isolation |
| Batch 3 Caffeine identity compatibility | historical supporting-pass | 30/0/0/0 | superseded for current Step 2 by focused addon evidence；Pivot/CACHE-GEN now owned by Step 5 authoritative pass |
| Runtime validate/DTO compatibility | passed | replacement Batch 7 API 62/0/0/0 in 6 reports | real Controller 48 + refresh/validate/legacy supporting 10 + sanitizer 4；additive legacy envelope + typed/sanitized lifecycle errors |
| MySQL 5.7 required lifecycle gate | preflight-pass | 1/0/0/0 | aggregate entry child `20260714T052029Z-2908765/mysql57-preflight` |
| PostgreSQL 15 required lifecycle gate | preflight-pass | 1/0/0/0 | aggregate entry child `20260714T052029Z-2908765/postgres15-preflight` |
| SQL Server existing regression | passed | 18/0/0/0 in 1 report | Batch 7 lane 09；SQL Server `16.0.4236.2` / `foggy_test.dbo`；fixture before/after equal |
| 9.3.1 isolation regression | passed | 132/0/0/0 in 13 reports | Batch 7 lane 10；Step order/cache identity/datasource/Controller isolation |
| 9.3.2 auto-config/Launcher regression | passed | 64/0/0/0 in 15 reports | Batch 7 lane 11；Boot 3 registration/Addon assembly/default route isolation |
| root package (compile/assembly only) | passed | N/A；zero tests executed | Batch 7 lane 12；25 modules、24 fresh main JARs、21 unique imports、0 duplicate/legacy、Launcher nested checksums 12/12 |

## Deterministic Concurrency Evidence

| Scenario | Concurrency | Expected | Actual build/publish count | Observed generations/results | Timeout/deadlock | Status |
|---|---:|---|---|---|---|---|
| Gate 0 controlled same key | 8 unit / 2 IT | build=1；all waiters share result | 1 / 1 | unit same object；SQLite `[1,1]`；in-flight=0 | bounded/no deadlock | passed |
| Gate 0 interrupted winner cleanup | 1 | in-flight removed | 1 | cancelled winner；in-flight=0 | bounded/no leak | passed |
| same key | 100 | build=1 | build=1；99 waiters；same result | exact key includes catalog/source/backend/binding set；residual=0 | bounded/no deadlock | passed；Batch 4 |
| different keys | 6 isolated dimensions | overlap=true；result/future isolated | namespace/model/catalog/source/backend/binding-set variants all enter concurrently | no cross-key result sharing | bounded/no deadlock | passed；Batch 4 |
| winner failure/retry | 100+1 plus cancel/checked timeout | same exact Throwable; flight removed; retry succeeds | same failure instance shared；retry build total=2；residual=0 | cancellation/timeout also shared and retryable | bounded/no deadlock | passed；Batch 4 |
| refresh readers | blocked old readers + post-publish readers | old or new only | candidate publish=1；failed candidate publish=0 | QueryFacade identity/model/native SQLite result entirely old or new；old remains usable on failure | bounded latch/future；no deadlock | passed；Batch 5 REF-01/02 |
| failed catalog candidate | 1 owner + controlled failure | old only；generation unchanged | failed publish=0 | active snapshot same object/identity；pure fail cannot use no-op | bounded/no deadlock | passed；Batch 3 |
| two namespaces | exact affected namespace refresh | target only changes | target publish exactly once；other namespace publish=0 | other namespace identity/result/admission unchanged | bounded/no deadlock | passed；Batch 5 deterministic model/adapter evidence |
| thread-pool scope reuse | 1 worker / 2 sequential tasks | no leaked namespace | N/A | task 2 starts unset；root close removes ThreadLocal | bounded Future/termination；no deadlock | passed；Batch 2 |
| candidate captured by other thread | 1 owner + 1 wrong thread | wrong-thread mutation rejected；owner can publish | publish=1 | wrong-thread name absent；owner name present | bounded Future/no poisoned lock | passed；Batch 3 |
| binding mutation + held lease | controlled latch/manual deadline | old lease bounded drain；new query new-or-fail | one pool/handle per generation；close exactly once | old H2 sentinel remains OLD；new handle reads NEW；old new-borrow revoked | bounded/no sleep | passed；Batch 3 |
| source revision changes during build | controlled candidate build/event mutation | stale candidate publish=0；bounded retry/new committed revision wins | captured view rotates and stale build is discarded | committed event revision + exact/unknown scope admission behavior verified | bounded/no deadlock | passed；Batch 5 |
| Runtime validate temporary source | valid + invalid detached candidate | live generation/names/alias unchanged | detached context only；live publish=0 | exact live source/catalog/name/alias state preserved | N/A | passed；Batch 5 |
| catalog metadata changes during callback | deterministic publication/block/churn callbacks | complete old or new generation only；bounded failure | native + MCP whole-operation retry，maximum 3 | identity/names/aliases/models/resolutions/bindings remain one snapshot；partial/blocked/churn fail closed | bounded/no sleep | passed；Batch 6 `CATALOG-AUTHORITY` |
| two independent Spring contexts | 2 contexts / distinct provider+builder instances | same complete identity same L1/L2 keys；generation change rotates both | N/A | same identity equality；catalog and binding mutations both produce different keys | bounded/no sleep | passed；historical Step 3 checkpoint was `CACHE-GEN` partial，final criterion passed in Step 5 |
| process restart + shared Redis | 2 child JVMs / real Redis 7.4.6 | production serializer + real published resolution + independently audited physical-key controls/current-epoch behavior | 27 probes；old physical-key read controls=2；current identity misses=2；post-write hits=2；Redis keys=4 | same binding；snapshot-derived catalog/source identity rotates across PIDs `2629762`/`2630070` | bounded；two-layer checks；container cleaned | passed；Batch 6 Step 4 run `20260714T025444Z-2628329` |
| Pivot cache identity and mid-request switch | direct pipeline/QueryFacade/Semantic contexts | one complete strong identity for read/write；refusal/provider failure no cache I/O；generation switch before execution/store | direct 38 in 4 reports；provider exception blocker has explicit case | full SHA-256 catalog/source/model/exact bindings；manual/provider additive only；old key not stored after switch | bounded/no direct sleep or skip | passed；Batch 6 Step 5 run `20260714T032135Z-2678670` |
| full REAL-QUERY lifecycle/cache/native parity | serial model + 3 DB + Caffeine + Redis lanes | complete old/new only；same-name namespace and rebind isolation；cache hit then generation miss；native parity | 11 tests in 6 Failsafe reports | model 4、DB 3、cache 4；Redis initial/final keys=0 | bounded latches/futures；no sleep/skip | passed；Batch 6 Step 6 run `20260714T041047Z-2755326` |

## Database Evidence

| Profile | Product | Actual version | Non-sensitive physical identity | Sentinel | Status |
|---|---|---|---|---|---|
| SQLite | SQLite | 3.42 | `sqlite:<shared-memory>` | REAL-QUERY rows 8/2；entry ORDER_STATUS exact 5 | real-query-pass |
| MySQL 5.7 | MySQL | 5.7 | `mysql://127.0.0.1:13306/foggy_test` | REAL-QUERY rows 25/25；entry ORDER_STATUS exact 5 | real-query-pass |
| PostgreSQL 15 | PostgreSQL | 15.17 | `postgresql://localhost:15432/foggy_test` | REAL-QUERY rows 25/25；entry ORDER_STATUS exact 5 | real-query-pass |
| SQL Server 2022 regression | SQL Server | 16.0.4236.2 | `foggy_test` / `dbo` | fact_sales=5940、dim_product=500、dict_status=48；before/after equal | regression-pass |

## Acceptance Criteria Tracking

| ID | 状态 | Direct evidence |
|---|---|---|
| PRE-934A | passed | aggregate entry child `20260714T052029Z-2908765`：5 positive + 4/4 expected-negative |
| NS-SCOPE | passed | aggregate child `20260714T045604Z-2854237-10-batch2`：25 product + 7 compatibility criteria；additional harness probe asserted separately |
| SNAPSHOT | passed | Batch 3 catalog 46；immutable same-view/invariant/owner/seal/external-object tests |
| GENERATION | passed | Batch 3 store 10；read/no-op unchanged、materialization +1、failure unchanged |
| DS-GENERATION | passed | Runtime 28 + MCP 12；persisted/cold non-reused generation and pinned H2 sentinel |
| BINDING-REVOKE | passed | Runtime/MCP controlled DRAIN/HARD/admission/lease tests；old new-borrow rejected |
| SF-SAME | passed | Batch 4 core；100 callers/build=1/99 waiters/same result/residual=0 |
| SF-ISOLATION | passed | Batch 4 six-dimension exact-key overlap/isolation + TM/QM detached overlap |
| SF-FAIL | passed | Batch 4 shared exact failure/cancel/timeout、precise cleanup、retry succeeds |
| REFRESH-ATOMIC | passed | Batch 5 final run；REF-01 real SQLite/QueryFacade old-or-new + single publication |
| REFRESH-FAIL | passed | Batch 5 final run；REF-02 failed candidate zero-publish + current old query remains usable |
| REFRESH-SCOPE | passed | Batch 5 deterministic target/dependent/sibling + exact namespace adapter evidence；not mislabeled as SQLite IT |
| EVENT-CONVERGENCE | passed | register-first bounded fixed-point + 第二窗口 deterministic contract；OVERFLOW/invalid/delete/failure 全部 loss signal→unknown fail-closed；replacement watcher lane 36/4 |
| SOURCE-COMMIT | passed | stable scan、file event 与 authority-loss 均进入 committed revision；exact scope exactly-once，unknown scope admission blocked；replacement authority no blocker |
| QM-COMPLETE | passed | builder dependency failure + failed/pure-fail candidate + external object-graph tests |
| VALIDATE-ISOLATION | passed | detached valid/invalid Runtime validation preserves live source/catalog/name/alias state |
| CATALOG-AUTHORITY | passed | Batch 6 run `20260714T021057Z-2445113`：direct 17 + supporting 36 = 53 tests / 7 reports；one immutable namespace view、coordinator-only recovery、exact bindings、native/MCP seqlock、shared `SemanticModelCatalogService` consumer wiring；remaining-red `20260714T021251Z-2449309`=0 |
| CACHE-GEN | passed | Steps 2/3 L1/L2 strong identity retained；Step 5 authoritative run `20260714T032135Z-2678670` = direct 38/4 + supporting 61/3，99 tests / 7 reports / 0 failures/errors/skips；Pivot full SHA-256 catalog/source/model/exact-binding identity、provider/manual additive only、refusal/provider failure no lookup/store |
| CACHE-CROSS-JVM | passed | authoritative run `20260714T025444Z-2628329`：production auto-config template/provider、snapshot-derived exact resolution、2 child JVMs/27 probes/same binding、old physical-key read controls=2、current identity misses=2、post-write hits=2、Redis `SCAN`=`DBSIZE`=4、two-layer hashes；independent second review no blocker |
| REAL-QUERY | passed | authoritative run `20260714T041047Z-2755326`：11 tests / 6 Failsafe reports；model lifecycle 4、SQLite/MySQL 5.7/PostgreSQL 15 parity 各 1、Caffeine/Redis 各 2；QueryFacade/Semantic/Pivot production path and native rows/columns/order/values parity；independent review no blocker |
| BATCH6-EXIT | passed | aggregate `20260714T045604Z-2854237`：11 children、676 criteria / 677 XML testcases / 99 reports、4/4 expected-negative、0 remaining red、F/E/S=0；source/child/inner/outer/dirty/container checks passed；two independent reviews no blocker |
| API-COMPAT | passed | replacement API lane 62/6；legacy/additive envelope、typed lifecycle errors、脱敏和 Map/Collection depth/width bound 全绿 |
| REGRESSION | passed | replacement run `20260714T084351Z-3271604`=`3824/519/F0/E0/S3`；9.3.1=132/13、9.3.2=64/15、三库各18；独立复核 no blocker |
| POST-GATES | passed | self-check passed；formal quality=`ready-with-risks`；coverage=`ready-with-gaps`；version acceptance=`signed-off / accepted-with-risks` |

## Blockers / Risks

- blockers: none。8 个 Batch 7 workitem 均 closed；首次 run 只保留 superseded
  history，replacement authority 已通过独立复算。
- known risk: 9.3.1/9.3.2 baseline 尚未进入版本控制，但已用 Gate 0 manifest/checksum 冻结；后续必须继续避免清理或覆盖。
- closed risk: `BUG-933-NEW-MODEL-WATCHER` 已将递归目录 watcher
  收敛到 fsscript/source authority；replacement watcher lane 以 core 11、
  authority 4、lifecycle 5、management 16，合计 36/4 覆盖新目录、共享 root、
  注册窗口、authority loss、回滚和清理。
- closed risk: `BUG-933-MULTI-BINDING-RETIRE` 已以两阶段
  retire 先关闭全部 admission，调度失败 slot hard revoke、其他 slot
  bounded drain；focused lifecycle class 11/0/0/0，Batch 7 binding lane 16/2 green 后关闭。
- closed risk: `BUG-933-CATALOG-PREPARE-MONITOR` 已把
  validate/freeze/seal 移到 binding mutation monitor 外，guard 内仅保留
  binding/source/base/store 终检与 atomic swap；focused contract family
  41/0/0/0；Batch 7 SQLite lane 复跑 owning core 31/2、扩展契约族 41/5
  全绿后关闭（不得误挂到只含 Runtime binding tests 的 lane 04）。
- closed risk: `BUG-933-PIVOT-DIAGNOSTIC-ORDER` 与
  `BUG-933-RUNTIME-MODEL-CONTROLLER-FULL-SUITE` 均已由 owning focused suite 和
  Batch 7 SQLite/API/package lanes 关闭。
- closed critical risk: register/scan 两个窗口已由 register-first bounded fixed-point、
  stable-scan acceptance、超限 unknown fail-closed 和 deterministic second-window test
  关闭；workitem 与 fresh authority 均 closed。
- closed critical risk: OVERFLOW、invalid key、watched-directory deletion、file-watch
  failure 均发出 additive authority-loss signal，source owner unknown-scope commit 且重复
  信号幂等；shared root 保持有效。
- closed major hardening: Runtime diagnostics Map/Collection 共用 depth/width bound，
  20,000 层 List direct regression 与 API replacement lane 全绿。
- low follow-up: 尚缺持续目录 churn 超过 8 轮的专门 seam test；当前超限分支有
  静态 fail-closed proof，不构成已确认生产缺陷，进入 coverage audit 透明记录。
- low follow-up: watcher 状态仍分布在较大的 fsscript handler/core tracer；归
  9.3.5 大类拆解，不在 9.3.3 越界重构。
- known risk: cycle guard 只证明 frozen build scope 内的同线程/self-wait；不扩大声称任意跨线程 wait-for 图检测。
- closed risk: model/MCP names、aliases、models 与 binding resolutions 已统一为
  同一 immutable namespace view；无 independent names cache/watcher，metadata
  generation callback 由 bounded seqlock 防 hybrid。
- closed risk: `QueryCacheKeyBuilder` 已无 instance UUID/object-address
  fallback；Step 2 strong key 与 Step 3 two-context direct evidence 保留。
- closed risk: 旧 Step 4 的 test-only serializer、empty snapshot/manual
  resolution 与自证式 runner artifacts 已由 production-path replacement run
  收口；旧 runs 继续只作 diagnostic/superseded 历史。
- closed risk: Pivot outer cache/pipeline/telemetry 已消费同一 full SHA-256
  lifecycle identity；provider/manual token additive-only，provider failure no
  lookup/store；`CACHE-GEN` passed。
- closed risk: Step 6 已形成完整 namespace/datasource/generation/cache/Pivot
  `REAL-QUERY` 与 native SQL parity authority；`REAL-QUERY` passed。

## Downstream Readiness

- Batch 2 NamespaceScope: completed（NS-SCOPE passed）。
- Batch 3 Binding Identity + CatalogSnapshot: completed（五个 owning criteria passed）。
- Batch 4 single-flight: completed（三个 `SF-*` passed）。
- Batch 5 refresh: completed（六个 owning criteria passed；`API-COMPAT` supporting only）。
- Batch 6 catalog/cache/Pivot/real-query consumer: completed；Step 7 aggregate
  `20260714T045604Z-2854237` passed and independently reviewed。
- Batch 7 regressions: replacement authority completed and independently audited；
  first authority remains superseded。
- implementation self-check: `passed-to-formal-quality-gate`
- implementation quality gate: `ready-with-risks`；无 blocker/high/medium；
  record=`quality/model-lifecycle-concurrency-implementation-quality.md`
- test coverage audit: `ready-with-gaps`；无 critical/major evidence gap；
  record=`coverage/model-lifecycle-concurrency-coverage-audit.md`
- version acceptance: `signed-off / accepted-with-risks`；
  record=`acceptance/version-signoff.md`
- 9.3.4 full CI gate: ready to start；9.3.3 prerequisite satisfied
- 9.3.5 engine/public API: blocked until 9.3.3 identity/scope stable and 9.3.4 gate available
