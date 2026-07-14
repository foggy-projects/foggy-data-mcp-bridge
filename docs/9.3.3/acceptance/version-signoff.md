---
acceptance_scope: version
version: 9.3.3
target: model-lifecycle-concurrency
doc_role: acceptance-record
doc_purpose: Record the formal version acceptance decision for 9.3.3 model lifecycle and concurrency.
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-07-14
reviewed_by: Codex + independent read-only reviewers
blocking_items: []
follow_up_required: yes
evidence_count: 14
---

# 9.3.3 Model Lifecycle and Concurrency Version Signoff

## Background

- Version: 9.3.3
- Scope: version acceptance
- Goal: 以 immutable CatalogSnapshot、exact single-flight、atomic refresh、
  committed SourceRevision、binding generation/admission、NamespaceScope 和
  generation-aware cache/Pivot identity 收口模型生命周期与并发。
- Version boundary: 继承 9.3.1 fail-closed 与 9.3.2 Boot 3 装配；只消费
  9.3.4-A 最小门，未提前完成 9.3.4 full CI、9.3.5 typed execution/API 或
  9.4.0 SPI v2/module split。

## Acceptance Basis

- `docs/9.3.3/README.md`
- `docs/9.3.3/requirement/P0-model-lifecycle-concurrency.md`
- `docs/9.3.3/contract/model-lifecycle-concurrency-contract.md`
- `docs/9.3.3/module-responsibility.md`
- `docs/9.3.3/code-inventory.md`
- `docs/9.3.3/implementation-plan.md`
- `docs/9.3.3/progress/model-lifecycle-concurrency-progress.md`
- `docs/9.3.3/test/model-lifecycle-concurrency-test-plan.md`
- `docs/9.3.3/evidence/batch-6/batch-6-exit-20260714.md`
- `docs/9.3.3/evidence/batch-7/batch-7-regression-exit-20260714-r2.md`
- `docs/9.3.3/quality/model-lifecycle-concurrency-implementation-quality.md`
- `docs/9.3.3/coverage/model-lifecycle-concurrency-coverage-audit.md`
- eight closed BUG workitems under `docs/9.3.3/workitems/`.
- raw Surefire/Failsafe XML、real DB identity/fixture、source/container and
  packaged JAR manifests under exact authority run directories.

## Acceptance Checklist

- [x] PRE-934A：0/missing/wrong test or DB evidence fail closed；minimum gate
  positive 5 + expected-negative 4/4。
- [x] NS-SCOPE：嵌套、异常、默认/非默认覆盖和线程池复用无泄漏。
- [x] SNAPSHOT / GENERATION：per-namespace immutable same-generation view；
  material publish +1，read/failure/no-op不切代。
- [x] DS-GENERATION / BINDING-REVOKE：persisted opaque generation、pinned
  handle、commit 后旧 admission 关闭、DRAIN/HARD 有界。
- [x] SF-SAME / SF-ISOLATION / SF-FAIL：100 同 key build=1，不同 key
  overlap，不共享 future；winner failure cleanup/retry。
- [x] REFRESH-ATOMIC / REFRESH-FAIL / REFRESH-SCOPE：读只见完整 old/new，
  source/model failure 保留有效 old，target 不误伤 sibling/namespace。
- [x] EVENT-CONVERGENCE / SOURCE-COMMIT：Runtime/bundle/file/datasource
  汇入同一 authority；stable scan、committed revision、stale check 和 unknown
  admission block 完整。
- [x] QM-COMPLETE / VALIDATE-ISOLATION：dependency/builder error 不发布
  partial QM；临时 validate 不改变 live catalog/name/alias。
- [x] CATALOG-AUTHORITY：model/MCP names、aliases、models、binding resolutions
  消费同一 immutable namespace view。
- [x] CACHE-GEN / CACHE-CROSS-JVM：L1/L2/Redis/Pivot 使用完整 strong
  identity；跨 JVM 无对象地址依赖，cold epoch 不误命中旧 key。
- [x] REAL-QUERY：model lifecycle、三种 required parity 和 Caffeine/Redis
  lifecycle 与 native rows/columns/order/values 一致。
- [x] API-COMPAT：Runtime DTO additive、opaque、稳定 nullability/error code，
  diagnostics 脱敏且 Map/Collection 深宽有界。
- [x] REGRESSION：9.3.1 `132/13`、9.3.2 `64/15`、SQLite 全量、三数据库和
  root package/JAR audit 不回退。
- [x] POST-GATES：progress/self-check → formal quality → coverage audit →
  version acceptance 顺序执行，未跳门。
- [x] experience=N/A；本轮没有 UI、浏览器或人工交互交付。

## Evidence

- Batch 6 aggregate：676 criteria / 677 XML testcases / 99 reports，
  F/E/S=`0/0/0`，expected-negative `4/4`，remaining red `0`，两路独立复核
  no blocker。
- Batch 7 replacement：run `20260714T084351Z-3271604`，`3824` tests /
  `519` reports / F/E/S=`0/0/3`；三项 exact SQLite skip，其余 lane S0。
- replacement API：`62/6`；watcher：`36/4`；binding：`16/2`；
  REAL-QUERY：`11/6`；SQLite：`3449/470/S3`。
- MySQL 5.7、PostgreSQL 15、SQL Server 2022 regression each `18/18`；DB
  identity 和 fixture before/after 一致。
- root compile/package：25/25；24 main JARs；21 unique Boot imports entries；
  zero duplicate/legacy registration；Launcher nested checksum `12/12`。
- source/worktree/container before-after、12 lane manifests、REAL-QUERY child
  manifests 和 packaged artifacts 经独立只读复算，无 blocker。
- all eight Batch 7 BUG workitems are closed against replacement authority；
  first run `20260714T074009Z-3153871` remains superseded and excluded。
- implementation quality：`ready-with-risks`，blocker/high/medium none。
- coverage audit：`ready-with-gaps`，critical/major evidence gap none。

## Failed Items

- none

## Risks / Open Items

- watcher reconciliation limit 的持续 8 轮 churn 尚缺 direct branch test；
  current implementation明确 unknown fail-closed。Owner: 9.3.4 test evidence lane。
- watcher、Runtime lifecycle managers 和 PivotPipeline 仍有大类维护债；
  Owner: 9.3.5 engine/public API decomposition。
- arbitrary cross-thread wait-for graph detection 非本轮承诺；当前冻结的是
  same-thread/self-wait build-scope guard，文档未扩大保证。
- 当前工作树包含 9.3.1–9.3.3 未提交成果，虽有 source manifest/dirty hash
  authority，但 9.3.4 clean-commit release gate 尚未完成。
- 全仓 Surefire/Failsafe 分层、MySQL 8 required lane、五库统一 parity、aggregate
  JaCoCo、required CI aggregator 和 immutable release artifact 归 9.3.4；本签收
  不代表这些能力已存在。

上述均为明确的 downstream/可维护性/证据粒度风险，不是未关闭的 critical
生产缺陷或 major acceptance gap。

## Final Decision

- decision: `accepted-with-risks`。
- 9.3.3 全部 critical requirement 已有直接、可复跑证据；8 个已确认 BUG 全部
  closed；无 blocker/high/medium implementation finding，也无 critical/major
  evidence gap。
- 允许 9.3.4“测试与 CI 证据链”进入正式迭代；9.3.5/9.4.0 仍按 roadmap
  顺序等待，不得与 9.3.4 混做。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-14
- acceptance_record: `docs/9.3.3/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: yes
- next_iteration: 9.3.4
