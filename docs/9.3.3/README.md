---
doc_role: version_execution_index
doc_purpose: Index the planned 9.3.3 model lifecycle and concurrency delivery package.
version: 9.3.3
status: signed-off
acceptance_decision: accepted-with-risks
entry_gate: passed
created_at: 2026-07-13
updated_at: 2026-07-14
---

# 9.3.3 模型生命周期与并发

## 文档作用

- doc_type: requirement+contract+plan+progress+acceptance-evidence-index
- intended_for: release owner / module owner / execution agent / reviewer
- purpose: 把 Catalog Snapshot、single-flight、原子刷新、datasource binding generation 和 NamespaceScope 拆成可执行、可验证、可签收的单仓迭代。

## 基线结论

- 9.3.2 的权威记录为 `signed-off / accepted-with-risks`，无 blocker/high，允许进入 9.3.3。
- 9.3.3 已完成 version-scope signoff：`signed-off / accepted-with-risks`，无
  blocker/high/medium implementation finding，亦无 critical/major evidence gap。
- Batch 6 authority `20260714T045604Z-2854237` 闭合 676 criteria / 677 XML
  testcases / 99 reports / 4 expected-negative / 0 remaining red，F/E/S=`0/0/0`。
- Batch 7 replacement authority `20260714T084351Z-3271604` 闭合
  `3824 tests / 519 reports / F0/E0/S3`，独立复算 XML、manifests、数据库、
  source/container 和制品均 no blocker。首次 run 保持 superseded。
- self-check、formal quality、coverage audit、version acceptance 已严格按序执行；
  9.3.4 full“测试与 CI 证据链”现在可开工。

## 版本目标

把当前“可变 Map + 全局锁/无锁 + 先清后热 + 手工 ThreadLocal”的模型生命周期，收敛为：

1. 每个 namespace 一个不可变 `CatalogSnapshot`，查询在模型解析完成后固定使用同一代目录视图；任何可观察的 catalog enrichment 都必须切换 generation。
2. 同一模型构建按 namespace、model、catalog generation、datasource binding generation 做 single-flight。
3. refresh 在不可见 candidate 中构建并验证，成功后一次原子切换；source/model 失败且 bindings 仍有效时保留旧查询，binding 已撤销时保留对象但阻断新查询。
4. namespace/model/bundle/file/datasource 变更汇入同一生命周期入口；source mutation 用 committed `SourceRevision` 防 stale publish，不再以全局 clear-first 作为生产刷新协议。
5. `NamespaceScope implements AutoCloseable` 支持嵌套恢复、异常清理和线程池复用。
6. catalog generation 和精确 dependency binding generations 进入 L1/L2/Redis/Pivot freshness identity，并用独立 JVM/重启证据证明不依赖对象地址。
7. datasource remove/disable/rebind commit 后立即关闭旧 binding 的新查询入口；candidate 失败时 fail closed，不回退旧物理目标。

## 执行资料

- requirement: [requirement/P0-model-lifecycle-concurrency.md](requirement/P0-model-lifecycle-concurrency.md)
- lifecycle contract: [contract/model-lifecycle-concurrency-contract.md](contract/model-lifecycle-concurrency-contract.md)
- 9.3.4-A entry gate: [preconditions/9.3.4-A-minimum-test-gate.md](preconditions/9.3.4-A-minimum-test-gate.md)
- module responsibility: [module-responsibility.md](module-responsibility.md)
- code inventory: [code-inventory.md](code-inventory.md)
- implementation plan: [implementation-plan.md](implementation-plan.md)
- progress template: [progress/model-lifecycle-concurrency-progress.md](progress/model-lifecycle-concurrency-progress.md)
- test plan/evidence template: [test/model-lifecycle-concurrency-test-plan.md](test/model-lifecycle-concurrency-test-plan.md)
- acceptance evidence plan: [acceptance-evidence-plan.md](acceptance-evidence-plan.md)
- Gate 0 baseline evidence: [evidence/gate-0/baseline-20260713.md](evidence/gate-0/baseline-20260713.md)
- Gate 0 negative runner evidence: [evidence/gate-0/negative-runner-evidence-20260713.md](evidence/gate-0/negative-runner-evidence-20260713.md)
- Gate 0 database preflight evidence: [evidence/gate-0/database-preflight-evidence-20260713.md](evidence/gate-0/database-preflight-evidence-20260713.md)
- Gate 0 deterministic harness evidence: [evidence/gate-0/deterministic-harness-evidence-20260713.md](evidence/gate-0/deterministic-harness-evidence-20260713.md)
- Gate 0 initial authoritative run: [evidence/gate-0/gate-0-run-20260713.md](evidence/gate-0/gate-0-run-20260713.md)（latest post-Batch1 replay 见 Step 7）
- Batch 1 entry check-in: [evidence/batch-1/entry-checkin-20260713.md](evidence/batch-1/entry-checkin-20260713.md)
- Batch 1 Step 1 contract freeze: [evidence/batch-1/step-1-contract-freeze-20260713.md](evidence/batch-1/step-1-contract-freeze-20260713.md)
- Batch 1 Step 2 namespace/QM red: [evidence/batch-1/step-2-namespace-qm-red-20260713.md](evidence/batch-1/step-2-namespace-qm-red-20260713.md)
- Batch 1 Step 3 single-flight/alias red: [evidence/batch-1/step-3-single-flight-alias-red-20260713.md](evidence/batch-1/step-3-single-flight-alias-red-20260713.md)
- Batch 1 Step 4 datasource binding red: [evidence/batch-1/step-4-datasource-binding-red-20260713.md](evidence/batch-1/step-4-datasource-binding-red-20260713.md)
- Batch 1 Step 5 source/validate/refresh red: [evidence/batch-1/step-5-source-validate-refresh-red-20260713.md](evidence/batch-1/step-5-source-validate-refresh-red-20260713.md)
- Batch 1 Step 6 catalog authority red: [evidence/batch-1/step-6-catalog-authority-red-20260713.md](evidence/batch-1/step-6-catalog-authority-red-20260713.md)
- Batch 1 Step 7 exit/readiness: [evidence/batch-1/step-7-exit-readiness-20260713.md](evidence/batch-1/step-7-exit-readiness-20260713.md)
- Batch 2 NamespaceScope exit: [evidence/batch-2/namespace-scope-exit-20260713.md](evidence/batch-2/namespace-scope-exit-20260713.md)
- Batch 3 entry check-in: [evidence/batch-3/entry-checkin-20260713.md](evidence/batch-3/entry-checkin-20260713.md)
- Batch 3 CatalogSnapshot/binding exit: [evidence/batch-3/catalog-binding-exit-20260713.md](evidence/batch-3/catalog-binding-exit-20260713.md)
- Batch 4 entry check-in: [evidence/batch-4/entry-checkin-20260713.md](evidence/batch-4/entry-checkin-20260713.md)
- Batch 4 single-flight exit: [evidence/batch-4/single-flight-exit-20260713.md](evidence/batch-4/single-flight-exit-20260713.md)
- Batch 5 entry check-in: [evidence/batch-5/entry-checkin-20260713.md](evidence/batch-5/entry-checkin-20260713.md)
- Batch 5 atomic refresh exit: [evidence/batch-5/atomic-refresh-exit-20260714.md](evidence/batch-5/atomic-refresh-exit-20260714.md)
- Batch 6 catalog/cache/Pivot entry: [evidence/batch-6/entry-checkin-20260714.md](evidence/batch-6/entry-checkin-20260714.md)
- Batch 6 Catalog Authority green: [evidence/batch-6/catalog-authority-green-20260714.md](evidence/batch-6/catalog-authority-green-20260714.md)
- Batch 6 Cache Identity/Cross-JVM green: [evidence/batch-6/cache-identity-cross-jvm-green-20260714.md](evidence/batch-6/cache-identity-cross-jvm-green-20260714.md)
- Batch 6 Pivot Cache Generation green: [evidence/batch-6/pivot-cache-generation-green-20260714.md](evidence/batch-6/pivot-cache-generation-green-20260714.md)
- Batch 6 REAL-QUERY green: [evidence/batch-6/real-query-green-20260714.md](evidence/batch-6/real-query-green-20260714.md)
- Batch 6 Cache Identity fresh replay: [evidence/batch-6/cache-identity-replay-green-20260714.md](evidence/batch-6/cache-identity-replay-green-20260714.md)
- Batch 6 aggregate exit: [evidence/batch-6/batch-6-exit-20260714.md](evidence/batch-6/batch-6-exit-20260714.md)
- Batch 7 superseded history: [evidence/batch-7/batch-7-regression-exit-20260714.md](evidence/batch-7/batch-7-regression-exit-20260714.md)
- Batch 7 replacement authority: [evidence/batch-7/batch-7-regression-exit-20260714-r2.md](evidence/batch-7/batch-7-regression-exit-20260714-r2.md)
- implementation quality: [quality/model-lifecycle-concurrency-implementation-quality.md](quality/model-lifecycle-concurrency-implementation-quality.md)
- test coverage audit: [coverage/model-lifecycle-concurrency-coverage-audit.md](coverage/model-lifecycle-concurrency-coverage-audit.md)
- version acceptance: [acceptance/version-signoff.md](acceptance/version-signoff.md)

## 当前状态

| Gate/Stage | 状态 | 准出条件 |
|---|---|---|
| 9.3.2 predecessor | signed-off / accepted-with-risks | feature scope；无 blocker/high |
| 9.3.4-A minimum gate | passed | aggregate child run `20260714T052029Z-2908765`；5 positive + 4/4 expected-negative；preflight-only，不代表 9.3.4 completed |
| 9.3.3 Batch 1 | completed | marker-bound run `20260713T115746Z-1058249`：10 suites / 12 expected-red assertions；产品 criteria 仍 pending |
| 9.3.3 Batch 2 | completed | historical exit 保持；aggregate child `20260714T045604Z-2854237-10-batch2`：25 product + 7 compatibility criteria，另 1 harness probe，33 XML testcases / 6 reports 全绿 |
| 9.3.3 Batch 3 | completed | historical exit 保持；Batch 6 aggregate current-source replay `20260714T045604Z-2854237-08-batch3`：168 tests / 20 reports 全绿 |
| 9.3.3 Batch 4 | completed | historical exit 保持；Batch 6 aggregate current-source replay `20260714T045604Z-2854237-07-batch4`：142 tests / 22 reports 全绿 |
| 9.3.3 Batch 5 | completed | aggregate child `20260714T045604Z-2854237-06-batch5`：90 tests / 19 reports 全绿；六个 refresh/source/event/validate criteria passed，`API-COMPAT` 仅 supporting |
| 9.3.3 Batch 6 | completed | Step 7 authority `20260714T045604Z-2854237`：11 children、676 criteria / 677 XML testcases / 99 reports、4/4 expected-negative、0 red、F/E/S=`0/0/0`；两路独立二审 no blocker |
| 9.3.3 Batch 7 | completed | replacement run `20260714T084351Z-3271604`=`3824/519/F0/E0/S3`；8 BUG closed；independent audit no blocker |
| 9.3.3 production implementation | completed | all critical criteria passed；version signoff completed |
| quality / coverage / acceptance | completed | `ready-with-risks` → `ready-with-gaps` → `signed-off / accepted-with-risks` |
| 9.3.4 full test/CI evidence chain | ready | predecessor signed off；执行文档见 `docs/9.3.4/` |

## 范围边界

- 本轮是单仓多模块交付，不物理拆分 Maven 模块。
- 9.3.4-A 只建立本轮所需最小测试门；完整五数据库矩阵、聚合覆盖率门禁和 release immutable evidence 归 9.3.4。
- QueryFacade 统一、执行阶段类型化、大类/循环依赖拆解归 9.3.5。
- `model-api/core/jdbc/starter/web`、BackendProvider、Addon TCK 和 SPI v2 归 9.4.0。
- experience: N/A，本轮无 UI/浏览器交付。
