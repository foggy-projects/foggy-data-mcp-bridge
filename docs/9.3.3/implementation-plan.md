---
doc_role: implementation-plan
doc_purpose: Define the ordered implementation and verification plan for 9.3.3 model lifecycle and concurrency.
version: 9.3.3
status: completed
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 Implementation Plan

## 文档作用

- doc_type: implementation-plan
- intended_for: release owner / execution agent / reviewer
- purpose: 规定前置门、失败契约、实现依赖顺序、并行 lane、每批产出和后置签收。

## Gate 0：冻结基线与 9.3.4-A

Status: completed（2026-07-13；initial run `20260713T104955Z-959834`；
latest post-Batch5 replay `20260713T201941Z-2133081`，5 positive + 4/4
expected-negative）

1. 以 9.3.2 acceptance 为权威确认 predecessor=`signed-off / accepted-with-risks`、无 blocker/high；记录其 feature-scope 限制。
2. 保存当前未提交/未跟踪成果的可追溯 baseline：git status、diff/patch、untracked manifest、必要产物 checksum；不得修改或清理既有成果来获得 clean tree。
3. 核对并持续同步 9.3.1 roadmap 的 predecessor、当前 Batch 与 gate 状态；若 execution baseline 与当前规划分离，先按 acceptance/progress 恢复一致状态。
4. 完成 [9.3.4-A minimum gate](preconditions/9.3.4-A-minimum-test-gate.md)：
   - 最小 Surefire/Failsafe 分层；
   - owning suite 0 tests 必须失败；
   - MySQL 5.7/PostgreSQL 15 product/version/physical identity preflight；
   - deterministic concurrency harness；
   - unit/IT 各实际运行一次且报告可定位。
5. 把正向与预期失败证据写入 progress/test record，并同步 README、precondition、progress 与本 Gate 状态。

Exit：9.3.4-A 所有 checkbox 通过。未通过时禁止 Batch 1 之后的生产代码。

## Batch 1：冻结契约与修复前失败基线

Status: completed（2026-07-13；evidence
`evidence/batch-1/step-7-exit-readiness-20260713.md`；expected-red run
`20260713T115746Z-1058249`）

1. 逐条确认 [lifecycle contract](contract/model-lifecycle-concurrency-contract.md) 的 Decision Status；DTO 字段/错误码、lease timeout 和 proposed 项全部确认或明确 owner，未决 blocker 必须先回写 contract。
2. 盘点全部 load/read/alias/catalog/clear/refresh/bundle/file/datasource/cache 入口，和 code inventory 对齐。
3. 先建立以下可控失败测试：
   - 100 same-key build count；
   - cross-key parallelism；
   - winner failure/retry；
   - refresh old/new/failed candidate；
   - nested NamespaceScope/thread-pool reuse；
   - datasource rebind generation；
   - remove/disable/rebind admission boundary 与 in-flight lease drain；
   - source revision stale candidate 与 unknown-scope admission block；
   - target namespace isolation；
   - root model 成功但关联 TM 失败时，candidate/QM 不得部分发布；
   - Runtime validate 不得把临时 bundle/model 写入 live snapshot。
   - 任一可观察 lazy materialization 必须 atomic publish 新 generation；普通 read/cache hit 不切代。
   - 两个独立 JVM/一次进程重启共享 Redis 时，不使用对象地址且不命中旧 epoch key。
4. 修复前失败报告需可复核；若旧实现意外通过，先校验断言是否真的覆盖竞态，不弱化契约。

Exit：critical contract 与 Runtime additive DTO 兼容契约均已确认，并有对应红色或明确源码证明的失败基线；测试不依赖 sleep。

## Batch 2：NamespaceScope

Status: completed（2026-07-13；evidence
`evidence/batch-2/namespace-scope-exit-20260713.md`；run
`20260713T130626Z-1313396`）

1. 实现 stack/token backed `NamespaceScope implements AutoCloseable`，规范化 unset/default/named state。
2. 迁移 QueryFacade、Semantic metadata、managed relation、QM loader/build 等生产 set/clear 入口到 try-with-resources。
3. 保留旧 set/get/clear 兼容入口，但增加用法说明/弃用方向，不允许新生产调用。
4. 验证 nested A→default→B、异常、early return、线程池复用、非法跨线程/乱序 close。

Exit：NS-SCOPE 全绿，生产源码审计不再出现新增裸 set/clear。

## Batch 3：Binding Identity 与 CatalogSnapshot

Status: completed（2026-07-13；evidence
`evidence/batch-3/catalog-binding-exit-20260713.md`；run
`20260713T150948Z-1719636`）

1. 在 model authority 内建立不可变 CatalogSnapshot/CatalogIdentity、per-namespace active reference 和 opaque SourceRevision port。
2. TM、QM、synthetic QM、discovery、alias 和 provenance 统一进入 snapshot；consumer 只读取只读视图。
3. 把 loader manager 的全局 map/lock/counters 降为 builder adapter；构建状态改为 candidate/request-local；任一累计 builder error 都阻止 publish。
4. 生成 deterministic alias；相同模型集合不因并发加载顺序改变 alias。
5. 冻结 datasource binding identity/generation、admission/lease port；Runtime registry/pool 与 MCP manager 提供兼容 adapter。
6. Runtime 持久 registry 记录不复用的 generation/epoch；generation-specific handle 不在旧 identity 下切换目标。remove/disable/rebind commit 后拒绝新旧-binding lease，在途 lease 按 contract 有界 drain。
7. Query/metadata 入口捕获一次 snapshot，并把强类型 catalog identity 与当前模型的 dependency binding identities 放入执行上下文；不把 namespace 误建模成单 datasource。

Exit：SNAPSHOT、GENERATION、DS-GENERATION、BINDING-REVOKE、QM-COMPLETE 结构/状态转移测试全绿；普通 read 不切 generation，可观察 materialization 恰好切一代。权威 run 合计 149 tests（0 failures/errors/skipped）；其中 Caffeine 30 tests 只是 identity supporting evidence，不提升 Batch 6 `CACHE-GEN`。

## Batch 4：Single-flight 读路径

Status: completed（2026-07-13；evidence
`evidence/batch-4/single-flight-exit-20260713.md`；run
`20260713T164144Z-1910217`，139 tests green）

1. 按 kind+namespace+model+catalog generation+committed source revision+canonical effective binding identity set（含 backend id/generation）建立 per-key flight。
2. winner 使用 detached builder；waiter 共享 success/failure，不重复注册 alias/model。
3. publish 前检查 generation；stale build 丢弃并有界重试。
4. 失败/取消/timeout 精确移除 future，下一调用可重试。
5. 检测 TM/QM/synthetic dependency cycle，禁止 self-wait deadlock。
6. 证明不同 key 有真实重叠执行，无 manager/global lock。

Exit：SF-SAME、SF-ISOLATION、SF-FAIL 全绿；100 callers build count=1；无残留 flight/thread/executor。

Batch 4 为 loader 最终发布补入 datasource binding currentness 原子护栏，
用于关闭 build-check 与 catalog swap 间的 TOCTOU；它不是 Batch 5 的 source
mutation/refresh coordinator，也不提升任何 `REFRESH-*` 或 `SOURCE-COMMIT`。

## Batch 5：离线构建与原子刷新

Status: completed（2026-07-14；exit evidence
`evidence/batch-5/atomic-refresh-exit-20260714.md`；authoritative run
`20260713T200646Z-2120785`，90 tests / 19 owning reports green）

1. 建立 namespace refresh coordinator：capture source/binding view → build/validate candidate → stale check → one publish。
2. namespace scope 构建完整目录；model scope 重建目标及依赖并保留 sibling。
3. RuntimeModelOperations 的 validate 使用 detached candidate，不临时注册到 live bundle/catalog；refresh 改调核心 coordinator，删除生产 clear-first/warm ownership，并增加 before/after generation 与诊断。
4. Bundle add/remove 与 file/import change 汇入同一入口；source registry、script cache、reverse-dependency index 一致 commit 后才发布 affected scope + SourceRevision；publish 前复核 revision。
5. unknown source scope 把无法证明不受影响的 catalog 标为 stale/admission-blocked 并持久化诊断，直到 atomic rebuild；不得 silent clear 或无限期继续旧查询。
6. datasource save/remove/disable/rebind 触发受影响 namespace refresh；mutation commit 同时关闭旧 binding 的新 lease，refresh 失败保持 fail-closed admission。
7. source/model refresh 失败、source/binding 变旧、并发 publish 竞争均不半发布；只有 bindings 仍有效时才继续 old snapshot 查询。
8. 成功后旧 snapshot 进入 retire/drain；cache eviction/broadcast 只做回收优化，不是正确性前提。

Exit：REFRESH-ATOMIC、REFRESH-FAIL、REFRESH-SCOPE、EVENT-CONVERGENCE、SOURCE-COMMIT、VALIDATE-ISOLATION 六项全绿；QM-COMPLETE 保持 Batch 3 passed；源码无生产全局 clear-first。

Actual exit：以上 criteria 已获得正常绿色证据；`API-COMPAT` 的 DTO、typed
error 与 sanitization supporting evidence 已绿，但 criterion 仍由 Batch 7 的
旧 consumer/Controller 回归提升。独立
Failsafe SQLite lane 直接证明 REF-01/02，namespace/sibling isolation 由
deterministic model/adapter tests 证明。最终审计证明 mixed provenance 中已知
binding 仍受 guard、catalog 在 pool callback 前先 admission-block、hostile map
key/value 均脱敏，且 logical id canonicalization 不改写 credential。post-Batch5
依次回放 Batch 4 `20260713T201031Z-2124453`=140、Batch 3
`20260713T201525Z-2129344`=166、9.3.4-A
`20260713T201941Z-2133081`=5 positive + 4/4 expected-negative、Batch 2
`20260713T202421Z-2138021`=25+7；remaining-red
`20260713T202645Z-2141955` 只保留 Batch 6 `CATALOG-AUTHORITY` 1/1。

## Batch 6：Catalog/Cache/Pivot 消费与真实查询

Status: completed（Steps 1–7 passed；exit evidence
`evidence/batch-6/batch-6-exit-20260714.md`；authoritative aggregate
`20260714T045604Z-2854237`）

1. model 与 MCP 两侧 catalog/resolver 按 namespace 读取同一 snapshot discovery/alias，移除各自全局 cached model names 和反射/双边失效。
2. L1/L2/Redis key 消费 catalog+binding generation，保留 9.3.1 namespace/datasource/security identity；routing/unknown identity 保持 no-read/no-write，不为命中率放宽。
3. 两个独立 application context 对相同稳定 identity 生成相同 key；任一 generation 变化后 key 必变。
4. 用两个独立 JVM 或“进程退出→registry reload→共享 Redis”证明 key 不含对象地址；本轮 boot epoch 产生 cold miss，绝不碰撞/命中旧进程 key。
5. Pivot outer cache/pipeline/telemetry 带入同一 identity，manual/provider token 仅作附加段；provider failure 必须 fail closed，不能 lookup/store。
6. 使用 old/new、双 namespace、双 datasource sentinel 执行 QueryFacade 真实查询，并逐行对比对应原生 SQL。
7. 冻结 Batch 6 runner，按序回放已通过 gate 并形成 Batch 6 exit record；完成前不得打开 Batch 7。

Exit：CATALOG-AUTHORITY、CACHE-GEN、CACHE-CROSS-JVM、REAL-QUERY 全绿；无跨 namespace/datasource/generation/process epoch 结果混用。

Actual exit：Steps 1–7 已全部收口，`CATALOG-AUTHORITY`、
`CACHE-CROSS-JVM`、`CACHE-GEN` 与 `REAL-QUERY` 均 passed。Step 7 aggregate
run `20260714T045604Z-2854237` 严格串行执行 11 children，闭合 676 criterion
tests / 677 asserted XML testcases / 99 reports / 4 expected-negative / 0
remaining red，failures/errors/skipped=`0/0/0`。所有 child manifest 初检/终检、
source manifest、inner/outer hashes、dirty worktree 与 fixed/run-owned container
before/after 均通过；两路 independent post-run review 无 blocker。Batch 6
completed；Batch 7 subsequently completed and signed off。

## Batch 7：回归与后置门

Status: completed（replacement authority `20260714T084351Z-3271604`；
post-gates signed-off）

1. 串行执行编译、unit、Failsafe IT、SQLite、required external DB、9.3.1 隔离、9.3.2 auto-config/Launcher 回归。
2. 记录实际 test/failure/error/skip、数据库 product/version/identity、报告目录、并发计数和 generation observations。
3. 更新 progress 并完成 Implementation Self-Check；不得仅写“已完成”。
4. 执行正式 `foggy-implementation-quality-gate`。
5. 质量门允许后执行 `foggy-test-coverage-audit`。
6. 覆盖审计允许后执行 `foggy-acceptance-signoff`，产出 `acceptance_scope: version` 的 `docs/9.3.3/acceptance/version-signoff.md`。
7. 回写 README、requirement、progress 和 9.3.1→9.4.0 roadmap 状态。

Exit：所有 critical criteria 有直接证据，version signoff 完成；否则 blocked/rejected，不得 accepted-with-risks。

Actual exit：replacement Batch 7=`3824 tests / 519 reports / F0/E0/S3`；
8 个 BUG closed；self-check→formal quality→coverage→version acceptance 按序完成，
最终 `signed-off / accepted-with-risks`。

## 并行执行建议

Contract 和 core port 冻结后可开三条 lane：

- Lane A：NamespaceScope + core snapshot/single-flight。
- Lane B：Runtime/MCP datasource binding generation 与 generation-pinned pool handle。
- Lane C：fsscript committed events + demo/test fixture。

Lane D（cache/catalog/pivot）必须等待 A 的 CatalogIdentity；最终 Maven evidence 必须串行，避免共享 `target` 和外部 fixture 竞争。

## 目标验证入口

最终命令由 Gate 0 固化，至少包含：

```bash
mvn -pl foggy-dataset-model -am -Pmodel-lifecycle verify
bash scripts/verify-model-lifecycle-9.3.3.sh --required-db mysql57,postgres15
mvn -pl foggy-runtime-api,foggy-dataset-mcp,addons/foggy-dataset-model-cache -am -Pmodel-lifecycle verify
mvn -pl foggy-mcp-launcher -am -P!multi-db -Dtest=AutoConfigurationBoundaryContractTest,FullAddonAutoConfigurationAssemblyTest,LauncherDefaultRouteIsolationSmokeTest test
mvn -P!multi-db -DskipTests package
```

- 最后一条只算编译/装配证据，不算测试通过。
- 若目标 profile/script 尚未创建，不能用近似命令冒充其结果。
- 外部环境不可用时 progress 标 `not-run`，不得标 completed。

## Stop Conditions

遇到以下任一情况先停更并回写 contract/plan：

- 需要 model 反向依赖 runtime/MCP/cache；
- candidate 无法隔离旧对象的可变状态；
- datasource old identity 无法固定物理目标；
- single-flight 需要全局锁才能避免递归；
- source event 无法判定 affected namespace；
- 必须提前实施 9.3.4 full gate、9.3.5 API 重构或 9.4.0 模块拆分。

## Final Execution Result

- completed_at: 2026-07-14
- Batch 6 authority: `20260714T045604Z-2854237`
- Batch 7 replacement authority: `20260714T084351Z-3271604`
- post-gates: self-check passed → quality `ready-with-risks` → coverage
  `ready-with-gaps` → version acceptance `signed-off / accepted-with-risks`
- next: 9.3.4 full test/CI evidence chain；9.3.5/9.4.0 scope 未提前实施
