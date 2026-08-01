---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-authoring-workspace-api
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 10
assurance_level: elevated
---

# Runtime authoring workspace API 正式重验 R2

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 在首次拒签 blocker 修复后，对 9.5.3 Runtime-local authoring workspace API 形成第二轮独立、
  可复核的正式签收结论；首次拒签记录保持不变。

## Background

- delivery_spec: `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`
- remediation_spec:
  `docs/9.5.3/workitems/BUG-runtime-authoring-workspace-store-root-ownership.md`
- target_outcome: 一个 Runtime-managed external Bundle 的持久 immutable revision 工作区，提供
  TM/QM/FSScript、diff、完整 detached validation 与受治理 candidate query，且不修改 live state。
- signoff_scope: 首次验收已通过的 AC-1、AC-3～AC-8、AC-10 证据，以及当前 `main` base HEAD
  `5c814cd8` 未提交工作树对 AC-2/AC-9 blocker 的修复和直接 affected regression。
- critical_outcomes: store integrity、foreign/live source zero mutation、path/symlink/quota、CAS、source
  stale、overlay ownership、管理 auth 与 live-state isolation 均不可豁免。
- non_blocking_or_waivable_items: standard `jar:` 不代表全部 nested/fat-JAR packaging；本次无 waiver。

## Acceptance Basis

- approved delivery spec: feature 与 remediation BUG 在本轮正式验收前均为 `READY_FOR_SIGNOFF`，
  assurance 保持 `elevated`。
- changed paths / diff: remediation 只修改 `foggy-runtime-api` store/Bundle filesystem boundary、对应测试与
  架构/版本文档；未修改 engine、公共 SPI、Console、launcher、POM 或既有 workspace API 成功语义。
- test records:
  - 独立 focused remediation：4 classes / 31 tests，0 failures/errors/skips，48.907s。
  - 独立 affected Runtime lane：12 classes / 121 tests，0 failures/errors/skips，54.156s。
  - 首次验收实际执行的 engine 4 classes / 22 tests 继续复用；remediation 未改 engine、fixture 或其前提。
- experience evidence: affected lane 包含真实 SQLite candidate execution、真实 external Bundle、完整
  workspace/controller/auth/capability/model compatibility；标准 JAR/FSScript/deletion/permission/cache 证据
  来自未失效的 engine lane。
- migration / compatibility evidence: v1 store 自动迁移至 ownership-bearing v2；公共 API additive contract、
  RuntimeEnvelope、auth 与既有 Bundle/model route compatibility tests 均通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 完整 Bundle inventory 与精确 eligibility | 首次验收通过；Bundle affected tests 再次通过 | inventory/controller/capability tests | pass |
| AC-2 | 安全 create/restart，source 与 foreign 数据零 mutation | v2 root/workspace ownership、unowned refusal、v1 recovery 与 overlap guard | BUG AC-1～AC-4 evidence | pass |
| AC-3 | path/symlink/quota/batch/persistence fault 原子性 | 原证据有效；store negative matrix 刷新通过 | store/service tests | pass |
| AC-4 | CAS、immutable revision、lease 与迟到结果 fail closed | 原实现未改；affected service/store tests 通过 | 121-test lane + prior engine evidence | pass |
| AC-5 | pinned deterministic resources/diff/discard | restart、不同 base/head、diff 与 terminal state 通过 | store/controller tests | pass |
| AC-6 | production detached external/JAR/FSScript validation 与隔离 | engine 未改，22-test evidence 有效；Runtime isolation 刷新 | reused engine + affected Runtime | pass |
| AC-7 | governed exact-revision real JDBC candidate query | 真实 SQLite workspace execution 与 candidate service 通过 | Runtime real execution/candidate tests | pass |
| AC-8 | selected replacement；other-owner overlay 三入口拒绝 | engine deletion/overlay evidence未失效；service lane通过 | reused engine + Runtime service | pass |
| AC-9 | source/head drift fail closed，live state 始终不变 | store/source 三向与 symlink overlap pre-mutation 拒绝；原竞态证据有效 | BUG AC-4/5 + prior race/isolation | pass |
| AC-10 | 全 workspace 管理 auth、Authorization 独立与兼容 | random-port auth、controller/model/Bundle compatibility 刷新通过 | 121-test lane | pass |

## Implementation Quality

- scope and changed surface: remediation 精确关闭首次拒签的 AC-2/AC-9，未扩大 feature 产品范围。
- maintainability and duplication: path policy 与 ownership/layout validation 集中；store/service/controller
  分层保持，未见测试绕过、debug 残留或重复 contract mapping。
- error handling and edge cases: destructive cleanup、migration interruption、identity mismatch、unknown/
  foreign entry 与 add/update/restore partial mutation 均有 fail-closed 证据。
- contract, data and compatibility: workspace store 内部 schema v1→v2 有明确迁移/回退边界；公共 route、
  DTO、配置键和 success semantics 不变。
- terminology and documentation: architecture/9.5.3 状态与实际能力一致；`runtime-model-authoring-design.md`
  仍为 `PROPOSED`，publish/Console/Git/JAR binding 未误报为已交付。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | Bundle inventory/capability matrix | refreshed | pass |
| AC-2 | core-blocker | critical | v2 ownership/sentinel/migration/overlap | new + rerun | pass |
| AC-3 | core-blocker | critical | path/symlink/quota/fault atomicity | refreshed | pass |
| AC-4 | core-blocker | critical | CAS/lease/late-result guards | refreshed + reused | pass |
| AC-5 | core-blocker | major | pinned resource/diff/discard | refreshed | pass |
| AC-6 | core-blocker | major | external/JAR/FSScript detached isolation | reused + refreshed Runtime | pass |
| AC-7 | core-blocker | critical | real SQLite governed candidate query | refreshed | pass |
| AC-8 | core-blocker | critical | selected deletion + three-entry overlay guard | reused + refreshed Runtime | pass |
| AC-9 | core-blocker | critical | source/head race + pre-mutation path disjointness | new + reused | pass |
| AC-10 | core-blocker | critical | auth scope/method + compatibility matrix | refreshed | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 首次通过项的代码与关键前提未被 remediation 改动；其直接
  Runtime 依赖已由 121-test lane 刷新。首次失败项获得 destructive negative、migration、path identity、
  controller/restore atomicity 的新自动化证据，足以判断全部不可豁免 guards。
- new_validation_that_could_change_decision: none。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal/tag/release/publish；feature 与 BUG 均明确禁止或排除，且这些验证
  不会改变已由 focused/affected 证据确定的结论。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 不是最终 release/tag/publish 候选；受影响面已由 elevated focused/affected lane 覆盖。
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

- `scoped-risk`：标准 `jar:` fixture 不代表全部 nested/fat-JAR packaging。
- `scoped-risk`：workspace store 只支持单 Runtime 进程；shared-NFS/多进程 writer 不受支持。
- `scoped-risk`：绕过 watcher 修改其他只读 dependency 时可能无法立即观察 drift。
- `scoped-risk`：共享 auth-code 不提供 workspace owner/RBAC/audit；model-author script 不是宿主 sandbox。
- `out-of-scope`：publish/apply/rollback/rebase、release package、Git、Console、JAR 多 Namespace 与高级
  candidate query；应由后续独立 workitem 冻结。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-10 全部通过；R1 的两个不可豁免 blocker 已由 ownership-bearing v2、foreign
  zero-delete 和 Bundle path disjointness 关闭，原未受影响证据仍有效且直接 Runtime lane 已刷新。
- blocking_items: none
- follow_up_owner_and_due: roadmap owner；后续 Console/publish/release-package 能力按独立 workitem 推进。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff-r2.md`
- blocking_items: none
- follow_up_required: yes
