---
quality_scope: version
quality_mode: pre-coverage-audit
version: 9.3.3
target: model-lifecycle-concurrency
status: reviewed
decision: ready-with-risks
reviewed_by: Codex + independent read-only reviewer
reviewed_at: 2026-07-14
follow_up_required: yes
---

# Implementation Quality Gate

## Background

- 检查对象：9.3.3 模型生命周期与并发全版本实现。
- 当前阶段：version execution self-check 之后、test coverage audit 之前。
- 检查目标：确认 CatalogSnapshot、single-flight、atomic refresh、binding
  generation/admission、NamespaceScope、cache identity、watcher/source authority
  和 Runtime public contract 已收口，且无阻断覆盖审计的实现问题。

## Check Basis

- requirement: `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- contract: `docs/9.3.3/contract/model-lifecycle-concurrency-contract.md`
- implementation plan: `docs/9.3.3/implementation-plan.md`
- progress/self-check:
  `docs/9.3.3/progress/model-lifecycle-concurrency-progress.md`
- Batch 6 authority: `20260714T045604Z-2854237`
- replacement Batch 7 authority: `20260714T084351Z-3271604`
- replacement evidence:
  `docs/9.3.3/evidence/batch-7/batch-7-regression-exit-20260714-r2.md`
- changed surface: model lifecycle/catalog/concurrency/ports, fsscript source
  authority, Runtime/MCP datasource lifecycle, cache/Pivot consumers, public
  Runtime DTO/Controller, runners/tests/docs.

## Gate History

首轮正式检查结论为 `needs-fix-before-audit`，因此当时禁止进入 coverage：

1. critical：`registerDirectoryTree` 的 scan-before-register 窗口可能永久漏掉
   runtime 新 source。
2. critical：WatchService OVERFLOW、invalid key 和 watched child delete 只
   log/cleanup，未形成 source authority-loss / unknown-scope fail-closed。
3. medium：`RuntimeLifecycleSanitizer` 的纯 Collection 链可绕过 Map depth
   cutoff 并触发 StackOverflow。

三项均建立 workitem、RED/源证明、最小修复与 focused GREEN；首次 Batch 7 run
保持 superseded，未被拼接为 replacement authority。

## Changed Surface Review

- catalog：per-namespace immutable snapshot、opaque generation、candidate
  prepare/currentness/atomic swap、whole-operation metadata view。
- concurrency：exact six-dimension single-flight key、shared winner/failure、
  precise cleanup/retry、same-thread/self-wait cycle guard。
- refresh/source：detached validate/build、old-or-new publication、committed
  SourceRevision、exact/unknown affected scope、stale admission block。
- datasource：persisted binding generation、pinned handle/lease、DRAIN/HARD、
  post-commit old-binding admission revoke、multi-binding scheduler fail-closed。
- namespace：AutoCloseable nested scope、异常恢复和 thread-pool reuse cleanup。
- cache/Pivot：full SHA-256 catalog/source/model/exact-binding identity，
  incomplete identity/provider failure no lookup/no store，cross-JVM cold epoch。
- watcher：register-first bounded fixed-point、stable scan acceptance、additive
  authority-loss callback、subtree cleanup and per-epoch idempotence。
- Runtime API：additive opaque DTO fields、stable error codes/nullability、legacy
  diagnostics compatibility、credential/path redaction、bounded composite data。

## Quality Checklist

- scope conformance: 未混入 9.3.4 Failsafe/五库/JaCoCo/release gate，未提前做
  9.3.5 typed phase/QueryFacade 拆解或 9.4.0 SPI v2/物理模块化。
- code hygiene: source audit、`git diff --check` 与 replacement run integrity
  无 debug bypass、生产 sleep、明文 credential 或跳过测试的 authority 通道。
- duplication/authority: model/MCP catalog、names、alias 和 binding resolution
  消费同一 immutable snapshot；MCP 残留 watcher 已移除，无双 authority。
- concurrency/locking: build 在 publication guard 外；guard 内只做终检和 swap；
  waits/futures/executors 有界，失败清理 in-flight。
- error handling: binding mutation、unknown source scope、incomplete identity、
  watcher loss 和 provider failure均 fail closed；不吞异常后继续 ACTIVE。
- compatibility: NamespaceContext、resolver、Runtime DTO/HTTP/error code 维持
  additive/legacy compatibility；新 callback/default SPI 为 additive。
- documentation: critical invariants、DRAIN/HARD、generation opacity、unknown
  scope、cache refusal、superseded evidence 都已回写。
- release evidence: Batch 6 criteria + replacement Batch 7
  `3824/519/F0/E0/S3`、三数据库、root package/JAR audit 已独立复算。

## Findings

- blocker/high/medium: none after repair.
- Catalog identity、source revision、binding generation 和 cache/Pivot identity
  没有发现可逆物理连接信息或对象地址 fallback。
- atomic refresh、binding revoke 与 watcher authority-loss 的 success/failure
  边界与 requirement 一致。
- second-window watcher test证明 reconciliation 新 child 不会被一次补扫误判为
  stable；scan/event source commit 保持 exactly-once。
- 20,000 层 hostile Collection 不再递归到 JVM stack limit；Map/Collection 共享
  depth/width budget。
- replacement authority 的 source/container/artifact before-after 与 raw XML
  独立复算一致。

## Risks / Follow-ups

- low：尚无专门 seam 强制目录持续增长超过 8 轮；当前代码在第 8 轮后明确走
  `RECONCILIATION_LIMIT_EXCEEDED` unknown fail-closed。coverage audit 必须将其
  记录为 branch-level gap，后续补 deterministic test。
- low：watcher 状态分布于较大的 `FsscriptFileChangeHandler` 和
  `WatchServiceFileTracer`；Runtime lifecycle managers 与 `PivotPipeline` 也仍较大。
  归 9.3.5 大类/循环依赖拆解，不在 9.3.3 冒险重构。
- low：cross-thread arbitrary wait-for graph detection 不是本轮承诺；当前只证明
  frozen build scope 的 same-thread/self-wait guard，文档没有扩大声称。
- downstream：全仓 Surefire/Failsafe 分层、五数据库统一 required matrix、聚合
  JaCoCo 和 immutable release evidence 仍由 9.3.4 完成。

## Decision

- decision: `ready-with-risks`。
- can_enter_coverage_audit: yes。
- blocker/high/medium: none。
- 上述 low 均为明确的测试粒度或可维护性债，不是已确认的生产正确性缺陷；允许
  携带进入 coverage audit，但不得描述为已消除。
