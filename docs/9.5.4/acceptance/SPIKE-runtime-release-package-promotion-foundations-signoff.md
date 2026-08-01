---
acceptance_scope: feature
version: 9.5.4
target: SPIKE-runtime-release-package-promotion-foundations
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 5
assurance_level: elevated
---

# Release package / production promotion 基础探针正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.4 release package 与生产 promotion 技术探针形成独立、可复核的正式结论。

## Background

- delivery_spec: `docs/9.5.4/workitems/SPIKE-runtime-release-package-promotion-foundations.md`
- target_outcome: 证明可复用的 9.5.3 Runtime authoring 原语，冻结 portable package、生产 guard、exact
  apply 和一步 rollback/recovery 边界。
- signoff_scope: 9.5.3 accepted 基线源码、9.5.4 design/README 与后续 canonical specs。
- critical_outcomes: 源码事实映射准确，不把开发 publish 包装为 production promotion，不要求 Engine、临时
  Namespace、Git、JAR 或跨 Runtime 控制面。
- non_blocking_or_waivable_items: v1 无签名、无 artifact GC、无任意历史 rollback。

## Acceptance Basis

- approved delivery spec: canonical spec 为 `READY_FOR_SIGNOFF`，assurance level 保持 `elevated`。
- changed paths / diff: spike 自身只新增 `docs/9.5.4` design、README 与三个 workitem；未修改历史验收正文。
- test records: 本 spike 不改生产实现，契约明确不要求 Maven；tracked/untracked whitespace checks 通过。
- experience evidence: 对 `HEAD` 9.5.3 workspace/store/query/publication/auth 能力与当前 9.5.4 additive diff 交叉
  审计，能够区分 reusable、small extension 与 new primitive。
- migration / compatibility evidence: 设计明确 API 后续只做 Runtime API additive extension，无 Engine/SPI、
  launcher、依赖或数据库 migration。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 证明 snapshot/limits、detached validate/query、artifact、atomic refresh 可复用 | design 第 2 节逐项归类并回指 9.5.3 accepted 原语 | 9.5.3 source/signoff；design review | pass |
| AC-2 | 证明基线没有 package/promotion/成功后 rollback | `HEAD` routes/state/publication 无这些契约，当前均表现为 9.5.4 additive diff | `git show HEAD` 与当前 diff | pass |
| AC-3 | 冻结 canonical identity、trust root、production opt-in | v1 JSON、length-delimited SHA-256、hash 非签名、默认关闭与普通 publish guard 均明确 | design 第 3–4 节 | pass |
| AC-4 | 冻结 import/revalidation/exact apply/rollback recovery | imported immutable candidate、生产重验、pinned apply、durable rollback/forward recovery 状态机完整 | design 第 5–6 节 | pass |
| AC-5 | 明确非目标 | Engine/SPI/临时 Namespace/Git/JAR/Agent/跨 Runtime 控制面均明确后置 | design 第 2、8 节；后续 specs | pass |

## Implementation Quality

- scope and changed surface: 文档探针边界清晰，与后续 API/Console spec 的顺序和职责一致。
- maintainability and duplication: 设计只记录差异和不变量，不复制生产实现或历史验收正文。
- error handling and edge cases: tamper、drift、restart、rollback failure 与信任边界均进入后续 must-pass AC。
- contract, data and compatibility: package 与 workspace additive data shape、默认关闭和旧 publish compatibility 已冻结。
- terminology and documentation: package、promotion/apply、rollback/recovery 与 workspace state 术语一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | 9.5.3 accepted source/evidence mapping | reused + review | pass |
| AC-2 | core-blocker | major | base `HEAD` 与 additive route/state diff | new review | pass |
| AC-3 | core-blocker | critical | package/trust/enablement design | new review | pass |
| AC-4 | core-blocker | critical | import/apply/rollback state machine | new review | pass |
| AC-5 | core-blocker | major | scope/non-goal mapping与 changed paths | new review | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: spike 只读且目标是技术边界冻结；基线源码、accepted 9.5.3 证据、
  当前 design 与后续 AC 可逐项闭环，继续运行产品测试不会改变设计探针判断。
- new_validation_that_could_change_decision: none。
- expensive_validation_omitted_and_reason: Maven、Console、launcher、DB matrix、authority/replay/release 均非本
  spike 验收项且不会改变文档事实判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none
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

- `scoped-risk`: package v1 hash 不证明发布者身份；configured management auth-code 操作者是信任根。
- `out-of-scope`: 签名/KMS、artifact GC、任意历史 rollback、Git/JAR/Agent 与跨 Runtime orchestration。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-5 均由基线源码与冻结设计充分支撑，范围无扩张且没有阻断项。
- blocking_items: none
- follow_up_owner_and_due: 后续 API 与 Console canonical workitems 按既定顺序交付。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/SPIKE-runtime-release-package-promotion-foundations-signoff.md`
- blocking_items: none
- follow_up_required: no
