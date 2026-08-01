---
acceptance_scope: feature
version: 9.5.5
target: SPIKE-runtime-artifact-store-lifecycle-foundations
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 8
assurance_level: standard
---

# Runtime Artifact / Store 生命周期基础探针正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.5 Runtime artifact/store 生命周期基础探针形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5/9.5.5/workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md`
- target_outcome: 建立真实双 store 磁盘 inventory、跨 workspace/publication/live registry 引用图、restart/failure
  matrix，以及后续 retention/recovery/diagnostics 的 fail-closed 安全边界；不实现删除或公共 API。
- signoff_scope: `HEAD` `084819667519` 上验收前未提交候选，包含 3 个 tracked 修改、0 个 untracked 文件；签收
  写回另新增本记录并更新 canonical status/迭代索引。
- critical_outcomes: ownership 与完整 identity、跨 store/live reachability、unknown/foreign/symlink/corrupt
  preservation、真实临时目录 characterization、零生产实现修改和证据真实性。
- non_blocking_or_waivable_items: 不选定默认 retention/grace，不交付 Console，不扩展 single-process/
  non-shared-NFS 边界；本次没有 waiver。

## Acceptance Basis

- approved delivery spec: canonical spec 验收前为 `READY_FOR_SIGNOFF`，assurance level 保持 owner 批准的
  `standard`；AC-1 至 AC-7 均为 must-pass，waivable items 为 none。
- changed paths / diff:
  - 候选修改仅为 canonical spec、9.5.5 README 和
    `RuntimeAuthoringPublicationStoreTest.java`；完整 tracked diff 已审查，验收前无 untracked 文件。
  - `git diff --name-only` 对 `src/main`、POM、Console、Engine/Model SPI、launcher 与 `.foggy-runtime` 返回空；
    零生产实现、依赖、配置和真实 Runtime 数据修改。
  - `git diff --check` 通过；未发现 debug、TODO、disabled test、Mockito filesystem Resource、secret 或计划外
    artifact。
- test records:
  - 独立重跑 canonical 4-class focused lane：46 tests，0 failures、0 errors、0 skipped，`BUILD SUCCESS`，
    Maven 总耗时 54.868s（2026-08-01T17:00:44+08:00）。
  - Surefire 独立分项：publication store 9、workspace store 18、workspace publish service 15、release package
    service 4，全部通过；新增两个 characterization 均包含在 publication store 9-test 结果内。
- experience evidence: 不适用 UI/Playwright；源码审计对照 production store、workspace state/evidence、publication
  lock、Bundle registry 与 release package 边界，新增测试直接实例化真实 production artifact store 并只使用
  JUnit `@TempDir`。
- migration / compatibility evidence: 本探针无迁移与产品行为变化；复用的 9.5.3 workspace ownership、publish/
  recovery、immutable-base/race R2 和 9.5.4 package/promotion/rollback 正式记录均为 `accepted`，对应生产输入在
  本候选中未变化。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 双 root 与 registry/marker/revision/head/staging/tombstone、artifact/manifest/attempt/temp/package inventory | inventory 逐项记录 owner/identity、创建引用与当前 cleanup；逻辑 head 由 registry 的 base/candidate 指针表达，无独立 head 文件；package v1 不持久化 | canonical inventory；`FoggyRuntimeApiProperties`、两个 store、release package source review | pass |
| AC-2 | workspace/attempt 全状态到 current/base/candidate/previous 的跨 store/live 引用图 | 覆盖全部 workspace 状态、publication `PUBLISHING/SOURCE_APPLIED/PUBLISHED/RECOVERED/RECOVERY_REQUIRED/FAILED` 与 rollback 四状态，并说明 restart 用途 | reference matrix；workspace/publication/store schemas | pass |
| AC-3 | 三类 reachability 且不可按 age/status/name 误删关键 artifact | live registry、所有 non-discarded base/candidate、lease、recovery/rollback 两侧与未知 identity 均明确 retain/preserve | reachability matrix；immutable-base/rollback accepted evidence | pass |
| AC-4 | 真实 store + 临时目录覆盖成功、中断、foreign、symlink、corrupt，且现场零删除 | 两个新增 interrupted-write characterization 与既有成功/negative tests 使用 production store 和 `@TempDir`；focused 9/9 publication tests、46/46 总计通过 | `RuntimeAuthoringPublicationStoreTest`；Surefire reports | pass |
| AC-5 | publication lock 内快照、全 identity 校验、plan-first、drift/unknown 零删除 | safe lifecycle algorithm 明确 lock、root/storeId/manifest/hash/attempt/registry 校验、mutation 前复核及 `NEEDS_REPLAN` 条件；retention 留给 owner | algorithm boundary；publication lock/source trace | pass |
| AC-6 | 最小 operability、auth/redaction 与 reusable/API/Console 分层 | 容量/对象/状态/reference/blocked reason/人工处置边界完整，明确 current gap、additive management API 与 Console-only 层 | minimum operability inventory；Runtime auth/registry capability review | pass |
| AC-7 | 有序拆分 recovery、retention/GC、read-only diagnostics，并限制下一实现半径 | diagnostics 先行；interrupted-write recovery 依赖事实模型且不与 inventory 合并；retention 等待前两项和 owner 策略；Console 后置，Engine/SPI/Git/JAR/Agent 排除 | recommended follow-up workitems | pass |

## Implementation Quality

- scope and changed surface: test-only/docs 候选与批准范围完全一致；没有生产、依赖、公共契约、架构或真实数据
  改动。
- maintainability and duplication: 两个 characterization 复用现有 test class/helper，场景名和断言直指中断现场；
  inventory、reference graph 与后续边界集中保留在唯一 canonical workitem。
- error handling and edge cases: 断言不仅检查错误码，还检查 staging partial TM、metadata temporary、final artifact
  与 foreign/symlink/corrupt sentinel 均未删除；覆盖 fail-closed 核心语义。
- contract, data and compatibility: 无 REST/DTO/config/schema/migration 变更；建议保持 additive management auth 与
  redaction，未把未来 diagnostics/GC 描述为现有能力。
- terminology and documentation: workspace、attempt、rollback、artifact revision、`must-retain`、
  `provably-unreachable-candidate`、`unknown-preserve` 与现有源码/历史 accepted 契约一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | 双 store/schema/release source trace + disk inventory | reused + new review | pass |
| AC-2 | core-blocker | major | workspace/publication/rollback state 与 live registry reference matrix | reused + new review | pass |
| AC-3 | core-blocker | critical | reachability classification + immutable-base/rollback accepted records | reused + new review | pass |
| AC-4 | core-blocker | major | production store `@TempDir` characterization；4-class focused 46/46 | new | pass |
| AC-5 | core-blocker | critical | publication lock/identity/drift source trace + safe algorithm boundary | reused + new review | pass |
| AC-6 | core-blocker | major | operability/auth/redaction capability split | new review | pass |
| AC-7 | core-blocker | major | ordered workitem boundaries/dependencies/non-goals | new review | pass |
| zero production mutation / evidence truthfulness | core-blocker | critical | complete status/diff/untracked audit、source-path negative diff、Surefire results | new | pass |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: 全部 must-pass AC 可回指未变化的 production source、仍有效的 accepted
  基线证据、canonical matrices 和独立 46-test focused 结果；新增 characterization 直接证明本次唯一代码改动所需
  的真实 filesystem 行为，关键结论不存在 unknown。
- new_validation_that_could_change_decision: canonical 4-class focused lane 已独立重跑并通过；无其他批准范围内验证
  能合理改变本 SPIKE 签收决定。
- expensive_validation_omitted_and_reason: affected lane、完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal/tag/release/publish 均未运行；delivery spec 明确不要求，且 test-only/docs
  changed surface 不使其前提失效，继续扩跑只增加 completeness 而非 sufficiency。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none；本候选不是 release candidate，不改变安全/权限/财务/迁移或公共 API/SPI。
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

- `scoped-risk`: published store 当前遇到 partial staging 或 metadata temporary 会持续 fail closed 并保留现场；本
  SPIKE 只做 characterization，不实现恢复。
- `out-of-scope`: 默认 retention/grace、terminal evidence retirement、所有 OS/power-loss 时序、多进程/shared-NFS
  一致性仍未承诺。
- `out-of-scope`: artifact/attempt GC、diagnostics API 与 Console 属于已排序的后续独立 workitems，不是本次已交付
  产品能力。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-7、临时目录 characterization、零生产实现修改和 evidence sufficiency 均有独立、可追溯
  证据；focused must-pass 全绿，无阻断项、偏差或 waiver，交付与批准的 standard assurance 契约一致。
- blocking_items: none
- follow_up_owner_and_due: 本签收无 remediation；后续 lifecycle inventory/recovery/retention workitems 按 canonical
  排序另行批准与验收。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5/9.5.5/acceptance/SPIKE-runtime-artifact-store-lifecycle-foundations-signoff.md`
- blocking_items: none
- follow_up_required: no
