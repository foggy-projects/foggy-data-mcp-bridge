---
acceptance_scope: bug
version: 9.5.3
target: BUG-runtime-authoring-workspace-store-root-ownership
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 6
assurance_level: elevated
---

# Workspace store root ownership 修复正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.3 workspace store ownership、destructive cleanup、v1 migration 与 Bundle path
  disjointness 修复形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`
- target_outcome: authoring store 只初始化和清理有明确 ownership 证明的内部状态；foreign 数据与 live
  Bundle source 零删除、零写入，合法 v1 store 可无损幂等迁移。
- signoff_scope: `main` base HEAD `5c814cd8` 上的未提交实际工作树，包括两个 untracked Java 文件。
- critical_outcomes: root/workspace ownership、foreign data zero deletion、migration no-loss、Bundle path
  disjointness、pre-mutation rejection 与 evidence truthfulness 均不可豁免。
- non_blocking_or_waivable_items: 多进程/shared-NFS、nested/fat-JAR 与绕过 watcher 的 dependency drift
  维持原 feature scoped risks；本次没有 waiver。

## Acceptance Basis

- approved delivery spec: 验收前 canonical BUG spec 为 `READY_FOR_SIGNOFF`，assurance 保持 owner 批准的
  `elevated`。
- changed paths / diff:
  - 生产改动限于 `foggy-runtime-api` workspace store、Bundle controller/registry、auto-configuration 与
    集中 path policy；测试和架构/9.5.3 文档同步更新。
  - 未修改 engine、公共 Model SPI、Console、launcher、POM/Maven 依赖或数据库 schema。
  - `git diff --check` 通过；两个 untracked Java 文件逐个执行 no-index check，无 whitespace diagnostics。
- test records:
  - 独立 focused：4 classes / 31 tests，0 failures/errors/skips，`BUILD SUCCESS`，48.907s。
  - 独立 affected Runtime lane：12 classes / 121 tests，0 failures/errors/skips，`BUILD SUCCESS`，54.156s。
  - 原 feature 验收中未受本 BUG 改动影响的 engine 4 classes / 22 tests 继续复用。
- experience evidence: source review 核对两阶段 cleanup、删除前 ownership/no-symlink 重验证、v1 切换
  顺序、路径身份比较与不泄露绝对路径的稳定错误。
- migration / compatibility evidence: real v1 fixture 使用不同 base/head，保留 exact metadata、state、
  validation evidence、content 与 tombstone；中断 migration 可重试，Runtime compatibility lane 通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | safe root 初始化 v2；非空 unowned root 零 mutation | v2 registry/storeId 与 workspace marker；sentinel/结构逐项保持 | `RuntimeAuthoringWorkspaceStoreTest` | pass |
| AC-2 | 只删除 ownership-bearing internal targets；foreign/unknown/symlink 全保留 | 完整扫描后两阶段删除，root/marker/name/hash/symlink 均验证 | store source + cleanup/crash/foreign tests | pass |
| AC-3 | 合法 v1 无损、幂等迁移；invalid/fault fail closed | migration identity、marker-first、registry-last；不同 base/head/evidence/tombstone 恢复 | legacy migration/retry/invalid tests | pass |
| AC-4 | configured/active/inactive source 三向与 symlink overlap pre-mutation 拒绝 | lexical + nearest-existing-ancestor real identity 集中判定 | path policy/store overlap tests | pass |
| AC-5 | Bundle add/update/enable/restore 使用稳定错误且无部分 mutation | route=`BUNDLE_PATH_CONFLICT`；startup ready check；registry/live calls 未发生 | controller/registry tests | pass |
| AC-6 | 原 feature 结果与兼容性保持 | 12-class / 121-test affected lane 全绿；engine 22-test evidence 前提未变 | independent rerun + reused engine record | pass |

## Implementation Quality

- scope and changed surface: 与批准范围一致，没有提前实现 publish、Console、Git、rebase 或 JAR binding。
- maintainability and duplication: ownership DTO、layout validation、cleanup target 与 path policy 职责集中；
  未发现 debug bypass、`@Disabled`、无解释 TODO 或重复外部路径判定。
- error handling and edge cases: 非空 unowned、foreign marker/temp、unknown entry、symlink、hash mismatch、
  crash staging、old revision、migration interruption 和 persistence failure 均 fail closed。
- contract, data and compatibility: 公共 route/DTO/config 成功语义不变；新增错误为 additive；v1→v2 是
  Runtime-local filesystem migration，无数据库或外部依赖变化。
- terminology and documentation: canonical architecture 已写明 ownership-bearing v2、foreign preservation、
  migration 与 Bundle disjointness；设计路线仍保持 `PROPOSED`，没有误报后续能力。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | critical | init + sentinel zero-mutation | new + rerun | pass |
| AC-2 | core-blocker | critical | owned cleanup / foreign zero-delete / symlink matrix | new + rerun | pass |
| AC-3 | core-blocker | critical | real v1 migration + interrupted retry + exact state/content | new + rerun | pass |
| AC-4 | core-blocker | critical | configured/registry/direct/symlink path matrix | new + rerun | pass |
| AC-5 | core-blocker | critical | controller/restore atomicity and stable error | new + rerun | pass |
| AC-6 | core-blocker | major | 121 Runtime tests + unchanged 22 engine tests | refreshed + reused | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: destructive negative paths、real migration、source/store overlap、
  Spring wiring、真实 SQLite workspace flow、auth 和 compatibility 均由 focused/affected 自动化证据覆盖；
  source review 进一步确认所有删除发生在完整验证之后。
- new_validation_that_could_change_decision: none；must-pass 已全部可判断并通过。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal/tag/release/publish；delivery spec 明确禁止或排除，且不会改变本
  filesystem/config boundary 的签收判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: focused destructive matrix 与 affected Runtime lane 已覆盖全部 must-pass。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: N/A
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- none

## Risks / Follow-ups

- `scoped-risk`：store 只承诺单 Runtime 进程；shared-NFS/多进程 writer 没有 lease/consensus。
- `scoped-risk`：原 feature 的 nested/fat-JAR packaging 与绕过 watcher dependency drift 边界不变。
- `out-of-scope`：publish/apply/rollback/rebase、Git、Console、JAR 多 Namespace 与高级 candidate mode。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-6 全部有独立、风险相称的通过证据；首次拒签的 destructive cleanup 与
  store/source overlap blocker 已关闭，没有未豁免失败或未知核心结果。
- blocking_items: none
- follow_up_owner_and_due: none for this BUG；后续路线按独立 workitem 冻结。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/BUG-runtime-authoring-workspace-store-root-ownership-signoff.md`
- blocking_items: none
- follow_up_required: no
