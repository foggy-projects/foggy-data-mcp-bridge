---
acceptance_scope: feature
version: 9.5.5
target: FEATURE-runtime-artifact-lifecycle-inventory
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 12
assurance_level: elevated
---

# Runtime Artifact Lifecycle 只读 Inventory 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.5 Runtime artifact lifecycle inventory 形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5/9.5.5/workitems/FEATURE-runtime-artifact-lifecycle-inventory.md`
- target_outcome: 交付 management-auth 保护的只读 lifecycle inventory，在单进程一致快照中诊断 workspace、
  published artifact/attempt 和 live Bundle registry 的容量、health、引用分类与 blocked reason，且零 filesystem
  mutation、零敏感信息泄漏。
- signoff_scope: 分支 `codex/runtime-artifact-lifecycle-inventory`、基线 `3bf83306` 上的 14 个实现候选 changed paths；
  签收写回另新增本记录并更新 canonical spec/9.5.5 README。
- critical_outcomes: auth/redaction、owner/schema/manifest/hash 校验、完整 cross-store/live reference graph、
  incomplete/corrupt/foreign/symlink preservation、真实 `@TempDir` 零 mutation、稳定 additive API 契约。
- non_blocking_or_waivable_items: 同步大目录扫描、外部 writer/shared NFS/多进程、retention/grace 和 terminal
  evidence retirement 均为批准的 non-goal；本次无 waiver。

## Acceptance Basis

- approved delivery spec: 验收前 canonical status 为 `READY_FOR_SIGNOFF`，assurance level 为 owner 批准的
  `elevated`；AC-1 至 AC-9 均为 must-pass，waivable items 为 none。
- changed paths / diff:
  - 6 个 production paths：route、capability、workspace snapshot hook，以及新增 controller/DTO/inventory service。
  - 4 个 test paths：新增 service/controller tests，扩展 random-port auth 与 capability tests。
  - 4 个 docs paths：canonical spec、9.5.5 README、architecture 与 dev guide。
  - 完整 tracked/untracked diff 已审查；无 POM/dependency、Engine/Model SPI、Console、launcher、database、真实
    `.foggy-runtime`、cleanup/repair/migration 或计划外文件。
  - `git diff --check` 与 5 个 untracked 文件逐项 `git diff --no-index --check /dev/null <file>` 均通过；未发现
    debug、TODO/FIXME、test bypass、secret、raw exception/path 输出或重复契约实现。
- test records:
  - compile：14-module reactor，`BUILD SUCCESS`，25.690s。
  - focused：6 classes / 109 tests，0 failures、0 errors、0 skipped，`BUILD SUCCESS`，1:00。
  - affected `Runtime*Test`：249 tests，0 failures、0 errors、0 skipped，`BUILD SUCCESS`，1:04。
- experience evidence: 无 UI/Playwright 要求；12 个 inventory service tests 使用真实 production store 与 JUnit
  `@TempDir`，对 scan 前后 size/mtime/SHA-256/layout 做 fingerprint，并覆盖 missing、partial、healthy、obsolete、
  active lease、rollback 两侧、live revision fallback、interrupted、foreign、symlink、corrupt 和 reference graph
  incomplete。
- migration / compatibility evidence: additive enabled-only GET/capability；无 persistence/schema/filesystem/database
  migration。复用的 9.5.3、9.5.4 和 accepted lifecycle SPIKE 前提未被本候选破坏，publication/workspace store
  baseline 已在 focused 中重新通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | additive envelope route/capability，authoring auth 始终强制 | 固定 GET route 和 capability；unconfigured 503、unauthorized 401、authorized 200 | routes/controller/capability；random-port/interceptor tests | pass |
| AC-2 | capturedAt、root/summary/object/blocked reason 与 deterministic ordering | DTO 字段完整；对象按 store/type/identity、引用去重排序，计数/bytes 有断言 | DTO/service；healthy aggregate/order assertions | pass |
| AC-3 | workspace owner/registry/marker/revision/head/staging/tombstone/temp 与 lease | v2 owner/record/revision/hash scan；base/candidate/lease retain，完整 obsolete revision 才是 candidate | service source；workspace/service tests | pass |
| AC-4 | published owner/artifact/hash/staging/attempt/rollback/temp，unsafe 零 follow/delete | manifest/content hash 与 attempt/rollback metadata 校验；partial/foreign/symlink/corrupt preserve | service source；publication characterization + service negatives | pass |
| AC-5 | 合并 workspace、attempt previous/candidate、rollback/recovery 与 live path/revision | candidate/previous 两侧、workspace revision、active lease、live path 或 revision fallback 全部 retain；图不完整全局保守降级 | rollback/live/corrupt graph service tests | pass |
| AC-6 | missing/partial/healthy/blocked health 且扫描零初始化/修复 | 双 missing、单边 initialized、healthy 与多种 blocked 均有断言；fingerprint 前后相等 | 12-test `@TempDir` service suite | pass |
| AC-7 | path/storeId/content/secret/raw exception redaction | response identity 与 reason 均为稳定 redacted 值；controller unexpected failure 使用稳定 code/phase 且 `safeToAutoRepair=false` | controller/service/auth redaction assertions | pass |
| AC-8 | 可维护单向实现，无 migration/dependency/cleanup；文档边界完整 | controller/DTO/service 分层，锁顺序为 publication lock → workspace monitor；architecture/dev guide 明确只读、candidate 非删除授权和 single-process | source/diff/docs review | pass |
| AC-9 | compile、focused、affected、完整 diff checks 全绿且无越界 | 14-module compile、109 focused、249 affected、tracked/untracked hygiene 全部通过 | Maven/Surefire records；git audit | pass |

## Implementation Quality

- scope and changed surface: 变更与批准的 Runtime API/docs 范围一致；没有把 diagnostics 扩张为 retention、GC、
  repair、migration、scheduler、Console 或跨进程协调。
- maintainability and duplication: route/capability 只各定义一次；DTO 只承载 immutable snapshot；scanner 将 root、
  object validation、reference merge 和 final classification 分层，workspace hook 仅提供 monitor-held read callback。
- error handling and edge cases: corrupt attempt/workspace/live registry 会使未引用 artifact 降为
  `UNKNOWN_PRESERVE`；invalid live path 用有效 revision 保守 retain；rollback metadata、active lease、unknown entry、
  unreadable/unsafe root 与 symlink 均 fail closed。
- contract, data and compatibility: enabled-only additive API；authoring path 复用强制 management auth；无请求 path/root
  参数、无持久状态或迁移；disabled Runtime 的既有装配边界不变。
- terminology and documentation: `MUST_RETAIN`、`PROVABLY_UNREACHABLE_CANDIDATE`、`UNKNOWN_PRESERVE`、
  workspace/attempt/artifact revision 与 accepted SPIKE/architecture 一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1/7 | core-blocker | critical | controller + random-port auth/redaction/capability；17-test auth class | reused + new | pass |
| AC-2 | core-blocker | major | DTO/order/reference/summary assertions | new | pass |
| AC-3 | core-blocker | critical | workspace snapshot/lease/obsolete/partial tests；18-test store baseline | reused + new | pass |
| AC-4 | core-blocker | critical | manifest/hash/attempt/rollback scanner；9-test publication characterization | reused + new | pass |
| AC-5 | core-blocker | critical | candidate/previous/rollback/live revision/incomplete graph tests | new | pass |
| AC-6 | core-blocker | critical | 12 `@TempDir` scenarios与 byte-level fingerprint | new | pass |
| AC-8 | core-blocker | major | source/dependency/docs review | new review | pass |
| AC-9 | core-blocker | major | compile 14 modules；focused 109；affected 249；diff checks | new | pass |
| retention/multi-process/pagination | out-of-scope | bounded | explicit non-goals and docs boundary | approved contract | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 公共 management API 的权限、redaction、完整性与 fail-closed guards 均有
  直接测试；真实 production stores + `@TempDir` fingerprint 证明零 mutation；focused 和 affected Runtime lanes
  覆盖直接与兼容半径，所有 AC 均可判定且没有关键 unknown。
- new_validation_that_could_change_decision: compile、focused 与 affected lane 已在最终 production/test 字节上运行；
  此后仅 documentation evidence writeback 改变，不使产品证据失效。
- expensive_validation_omitted_and_reason: 未运行完整无选择器 reactor、DB matrix、Console/Playwright、真实 deployment、
  shared-NFS/multi-process、authority/replay/rehearsal/source-seal/tag/release/publish；均为 delivery spec 明确 non-goal，
  不会改变本次 additive read-only Runtime API 的签收判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none；focused + affected 已覆盖局部公共 management API 的安全与兼容半径。
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

- `scoped-risk`: v1 为同步完整扫描且无分页/streaming；大 store 的响应时间与内存占用需要后续独立优化。
- `out-of-scope`: publication/workspace locks 只协调当前 Runtime 单进程；外部 writer、shared NFS 和多进程不在一致性
  保证内。
- `out-of-scope`: terminal attempt/tombstone evidence 仍默认 retain；retention/grace、evidence retirement 与真正
  cleanup/GC 尚未获授权，candidate 本身不构成删除许可。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-9 的核心产品、安全、artifact integrity、zero-mutation 与兼容证据充分；compile、focused、
  affected 和完整 diff checks 全绿，无阻断项、偏差或 waiver，结果符合 owner 批准的 elevated assurance 契约。
- blocking_items: none
- follow_up_owner_and_due: 本签收无 remediation；pagination、跨进程协调和 retention/GC 仅在独立 workitem 获批后推进。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5/9.5.5/acceptance/FEATURE-runtime-artifact-lifecycle-inventory-signoff.md`
- blocking_items: none
- follow_up_required: no
