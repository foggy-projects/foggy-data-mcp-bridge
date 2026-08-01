---
acceptance_scope: bug
version: 9.5.5
target: BUG-runtime-published-store-interrupted-write-recovery
status: signed-off
decision: accepted
signed_off_by: independent Codex reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 8
assurance_level: elevated
---

# Bug Delivery Signoff: Published Store 中断写入安全恢复

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 owner 可证明的 published-store interrupted write recovery 形成独立、可复核的正式签收结论。

## Background

- delivery_spec: `docs/9.5/9.5.5/workitems/BUG-runtime-published-store-interrupted-write-recovery.md`
- target_outcome: 只恢复新格式、store/attempt/target/schema/filesystem type 全部匹配的 ephemeral writes；所有不确定
  对象及 final evidence 保留并 fail closed。
- signoff_scope: `foggy-runtime-api` store/inventory production 与 tests、lifecycle architecture/result writeback。
- critical_outcomes: owner-before-object、final/foreign/symlink zero-delete、幂等 recovery、inventory zero mutation。
- non_blocking_or_waivable_items: multi-process/shared-NFS 与全 power-loss 时序不在本地单进程契约内。

## Acceptance Basis

- approved delivery spec: 唯一 canonical spec，验收前状态 `READY_FOR_SIGNOFF`，assurance `elevated`。
- changed paths / diff: 独立审计 `git diff 7ec8e274` 的 8 个 tracked paths；`git ls-files --others
  --exclude-standard` 为空，`git diff --check 7ec8e274` 通过。
- test records: implementation compile/focused/affected 证据有效；reviewer 独立重跑 3-class focused lane。
- experience evidence: 真实 `@TempDir` filesystem assertions；本 BUG 不要求 UI/manual deployment。
- migration / compatibility evidence: final artifact/attempt schema 与 API 不变；marker 为 forward-only ephemeral schema，
  legacy 继续 preserve。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | artifact owner 先于 staging，绑定完整 identity，final 无残留 | write order 明确；成功 layout 断言无 owner | store source + success/partial/stale-owner tests | pass |
| AC-2 | attempt owner 先于 temp，final schema 不变 | owner→temp→atomic move→owner delete | begin/update + temporary/stale-owner tests | pass |
| AC-3 | owned marker-only/partial/final-committed 幂等收敛 | object-first、marker-last recovery | restart/idempotency tests | pass |
| AC-4 | missing/corrupt/mismatch/foreign/symlink preserve | 全 store preflight 后才 mutation，NOFOLLOW + sentinel assertions | legacy/corrupt/symlink tests | pass |
| AC-5 | final/live/recovery/rollback evidence 永不回收 | recovery target 仅匹配 owner 的 staging/temp/marker | source review + affected Runtime lane | pass |
| AC-6 | inventory redacted/deterministic/zero mutation | 新格式为 `*_RECOVERY_PENDING`，legacy 为 unknown | inventory fingerprint tests | pass |
| AC-7 | stable sanitized failure，`safeToAutoRepair=false` | 固定 code/phase/message，不传播 underlying detail | exception construction + Runtime safety tests | pass |
| AC-8 | compile/focused/affected/diff 且无越界 | 14-module compile、42 focused、255 affected，全绿 | command records + path audit | pass |

## Implementation Quality

- scope and changed surface: 仅 store/inventory、对应 regression tests 与 lifecycle 文档；未修改 Console、Engine、
  launcher、POM、数据库或真实 store。
- maintainability and duplication: marker records与 preflight/recovery 分层明确；删除集合在全 store 验证成功后执行。
- error handling and edge cases: marker-only、partial、stale marker、update 的 final+temp、retry、corrupt final、symlink、
  traversal 与 legacy temporary 均有保守处理。
- contract, data and compatibility: 无 public endpoint/DTO breaking change；inventory 只增加稳定 type/reason 值。
- terminology and documentation: architecture、spec、代码与测试一致使用 owner marker / recovery pending。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1/2/3 | core-blocker | critical | focused store tests 14/14 | reviewer new | pass |
| AC-4/5/7 | core-blocker | critical | negative `@TempDir` + affected safety/publication tests | reviewer new + affected reused | pass |
| AC-6 | core-blocker | critical | inventory tests 13/13 + before/after fingerprint | reviewer new | pass |
| AC-8 | core-blocker | major | compile 14 modules；focused 42；affected 255；diff audit | reusable + reviewer new | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 删除范围只含 owner 可证明的 ephemeral objects；核心正反向 crash-window、
  final/foreign/symlink guards、inventory immutability 和整个 affected Runtime lane 均有真实执行证据。
- new_validation_that_could_change_decision: none within approved local/single-process contract。
- expensive_validation_omitted_and_reason: DB matrix、Console、authority/replay/release chain 与本 BUG must-pass 无决策价值。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none
- estimated_wall_clock_and_basis: not-estimated
- scope_and_prerequisites: none
- maximum_attempts: 1
- decision_impact: none
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | N/A | N/A | N/A | ownership/final/foreign/symlink guards | none |

## Failed Items

- none

## Risks / Follow-ups

- 已知边界：partial/corrupt marker 继续人工保留；外部 multi-process/shared-NFS writer 不受单进程 publication lock 保护。
- 无本次阻断或必需 follow-up。

## Final Decision

- decision: accepted
- rationale: AC-1～AC-8 全部有与 elevated assurance 相称的独立证据，未发现范围偏离、删除边界缺口或阻断项。
- blocking_items: none
- follow_up_owner_and_due: none

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent Codex reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5/9.5.5/acceptance/BUG-runtime-published-store-interrupted-write-recovery-signoff.md`
- blocking_items: none
- follow_up_required: no
