---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.4
ticket: SPIKE-runtime-release-package-promotion-foundations
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Release Package / Production Promotion 基础探针

## Goal

- target_outcome: 以现有生产源码证明跨环境交付可复用与必须新增的边界，冻结 package、production guard、
  apply/recovery/rollback 语义，避免把开发 Runtime publish 直接包装为生产 promotion。
- success_is_sufficient_when: 设计文档逐项映射 workspace/store/candidate/publication/registry/auth 源码事实，
  结论不要求 Engine 或临时 Namespace，并为后续两个 feature spec 清零实质开放问题。

## Scope

- in_scope: 当前 architecture、workspace、published artifact、publication coordinator、Bundle inventory、
  Runtime auth/capability 与 Console authoring contract 的只读审计及 9.5.4 设计冻结。
- affected_modules: `docs/9.5.4`。
- external_dependencies: none。

## Non-Goals

- out_of_scope: 生产代码、测试、package 签名、Git/JAR/Agent、跨 Runtime 控制面。
- do_not_touch: 已签收 9.5.3 历史验收正文。
- non_blocking_or_waivable_items: v1 无签名、无 GC、无任意历史 rollback。

## Acceptance Criteria

- [x] AC-1: 证明 workspace snapshot/limits、detached validate/query、immutable artifact 与 atomic refresh 可复用。
- [x] AC-2: 证明当前没有 release package、production promotion 或成功发布后 rollback API/state。
- [x] AC-3: 冻结 canonical JSON/package identity、management-auth trust root 与 production enablement。
- [x] AC-4: 冻结 import workspace、生产 revalidation、exact apply 和一步 rollback/forward recovery 状态机。
- [x] AC-5: 明确 Engine/SPI/临时 Namespace/Git/JAR/Agent 均不属于本阶段。

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；设计触及后续生产写入，但本 spike 只读。
- lightweight_validation: source/doc review、`git diff --check`。
- medium_validation: none。
- expensive_validation: none。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none
- user_approval_status: not-requested
- maximum_expensive_attempts: 0
- reusable_evidence: 9.5.3 workspace/publish R2 signoff。
- stop_when_evidence_is_sufficient: 设计结论与后续 AC/guards 可逐项回指真实源码后停止。
- validation_not_required: Maven、Console、launcher、database、authority/release。

## Risks and Open Questions

- known_risks: hash 不提供发布者身份；一步 rollback 不构成 revision history。
- open_questions: none

## Implementation Result

- implementation_summary: 已形成 9.5.4 design、版本 README 和后续 API/Console canonical specs。
- changed_paths:
  - `docs/9.5.4/README.md`
  - `docs/9.5.4/runtime-release-package-promotion-design.md`
  - `docs/9.5.4/workitems/SPIKE-runtime-release-package-promotion-foundations.md`
  - `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`
  - `docs/9.5.4/workitems/FEATURE-runtime-console-production-promotion.md`
- tests_and_results: source review complete；最终 diff checks 随交付统一记录。
- manual_or_experience_evidence: N/A
- deviations: none
- residual_risks: v1 trust 与 rollback 边界已写入设计，不误报为签名/历史管理能力。
- reused_evidence: 9.5.3 accepted foundation/workspace/publish evidence。
- omitted_validation_and_reason: 本 spike 不改生产实现。
- readiness: READY_FOR_SIGNOFF

## References

- architecture / glossary: `docs/architecture/runtime-and-model-lifecycle.md`
- related work items: `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/SPIKE-runtime-release-package-promotion-foundations-signoff.md`
- blocking_items: none
- follow_up_required: no
