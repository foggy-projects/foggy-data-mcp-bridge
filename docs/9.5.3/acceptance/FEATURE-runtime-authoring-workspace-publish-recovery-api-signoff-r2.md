---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-authoring-workspace-publish-recovery-api
status: signed-off
decision: accepted
signed_off_by: Codex R2 reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 12
assurance_level: elevated
---

# Runtime authoring workspace publish 与失败恢复 API R2 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 在保留 R1 rejected 历史记录的前提下，对 remediation 后的完整 feature 候选形成 R2 正式结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
- remediation_spec:
  `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`
- target_outcome: exact validated workspace revision 可重复发布为 immutable Runtime-managed Bundle artifact，
  full-Namespace refresh 成功收敛，失败可确定恢复 exact base。
- signoff_scope: `main` base `80fe0fb2` 上当前全部未提交 tracked 与 untracked 工作树。
- critical_outcomes: exact identity、owned immutable artifact、repeat publish、source/registry/catalog convergence、
  recovery、并发无 mixed catalog、management auth 与 redaction。
- non_blocking_or_waivable_items: artifact GC、staging cleanup、单进程/非 shared-NFS 前提；无 waiver。

## Acceptance Basis

- approved delivery spec: feature 在 R1 保持 `READY_FOR_SIGNOFF`，remediation BUG 为
  `READY_FOR_SIGNOFF`；两者 assurance level 均为 `elevated`。
- changed paths / diff: 所有生产变更只在 `foggy-runtime-api`；其余为架构、9.5.3 workitem/acceptance 文档；
  没有 Engine/Model SPI、Console、launcher、POM、依赖图或数据库 schema 变更。
- test records:
  - R2 focused：7 classes / 61 tests，0 failures/errors/skips，56.768s。
  - R2 最终 affected：14 classes / 152 tests，0 failures/errors/skips，55.269s。
  - affected 中真实 filesystem + SQLite：6/6，含连续两次 publish 与并发 live query。
- experience evidence: tracked/untracked 实际工作树全部审查；tracked `git diff --check` 与每个 untracked
  文件的 `git diff --no-index --check` 通过；未发现 debug/TODO、disabled test 或越界文件。
- migration / compatibility evidence: additive routes/DTO/state/registry，attempt v1 schema 不变，restart
  reconciliation、random-port auth、旧 Bundle/resource/model compatibility 均通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | exact preflight，拒绝时零 mutation | candidate/base/source/overlay pin，immutable provenance 与 current check 均在 intent/live mutation 前 | preflight/tamper zero-mutation tests；source review | pass |
| AC-2 | canonical owned immutable artifact | atomic owned root、TM/QM/FSScript allowlist、manifest/hash/symlink/foreign fail closed | publication store 7 tests | pass |
| AC-3 | durable publish 收敛 source/registry/catalog | 真实 SQLite candidate 成为 live query，workspace/attempt 为 `PUBLISHED` | real execution success | pass |
| AC-4 | `PUBLISHED` 只读，下一 workspace 可继续修改并发布 | direct writes 拒绝；v1→v2 修改 TM/QM/FSScript、validate/publish/query 成功，v1 全字节保留 | real execution double publish；service double publish | pass |
| AC-5 | Bundle mutation 不修改/删除 artifact | immutable resource guard、mutable compatibility、replace/remove retention | resource/Bundle controller tests | pass |
| AC-6 | source/registry/refresh/final evidence fault 恢复 exact base | 自动补偿 source/record/full catalog；immutable v1 recovery 已直接覆盖 | publish fault matrix；real SQLite recovery | pass |
| AC-7 | restart 与 pinned explicit recovery 幂等 | interrupted intent 转 `RECOVERY_REQUIRED`，attempt/candidate pin，重复 recovery 一致 | store + publication tests | pass |
| AC-8 | drift/corruption unsafe fail closed | third-party drift、root/manifest/hash/attempt/symlink/foreign corruption保留现场 | negative store/recovery tests | pass |
| AC-9 | 单赢家且并发 live query 无 mixed catalog | shared publication lock；真实 production semantic query 观察仅 old/new/not-current，最终 new current | concurrent publish/lock tests；real concurrent query test | pass |
| AC-10 | auth、envelope/redaction、旧 API compatibility | routes 全部受 management auth；稳定错误且不泄露 path/cause/secret；affected 全绿 | auth/controller/compatibility tests | pass |

## Implementation Quality

- scope and changed surface: module ownership 与 feature scope 一致；未把 Console、release 或跨 Runtime 能力
  混入 API primitive。
- maintainability and duplication: workspace store、artifact store、registry、publication coordinator 与 shared lock
  职责分离；关键 immutable provenance 集中，无 test-only production branch。
- error handling and edge cases: durable intent、自动/显式 recovery、restart、third-party drift、final persistence、
  registry fault、tamper/corruption 与 concurrent writer 均有风险相称处理。
- contract, data and compatibility: API additive；immutable 定义为 artifact bytes 只读且 lifecycle 可通过新
  workspace 追加新 artifact；旧 mutable Bundle 首次 publish 行为保持。
- terminology and documentation: canonical architecture/README 已同步，PROPOSED 后续路线未误报为已交付。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | critical | exact + provenance zero-mutation | rerun/new | pass |
| AC-2 | core-blocker | critical | owned artifact integrity matrix | rerun/new | pass |
| AC-3 | core-blocker | critical | real SQLite first publish | rerun | pass |
| AC-4 | core-blocker | critical | real SQLite repeat publish + v1 retention | new | pass |
| AC-5 | core-blocker | critical | immutable save/retention compatibility | rerun | pass |
| AC-6 | core-blocker | critical | automatic fault/recovery matrix | rerun/new | pass |
| AC-7 | core-blocker | critical | restart/pinned/idempotent recovery | rerun | pass |
| AC-8 | core-blocker | critical | drift/corruption zero-overwrite | rerun/new | pass |
| AC-9 | core-blocker | critical | single-winner/shared lock + real concurrent query | new/rerun | pass |
| AC-10 | core-blocker | critical | auth/envelope/redaction/compatibility | rerun | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: R1 已通过的首次 publish/recovery/auth/compatibility 证据在最终
  affected lane 重跑；R1 两个失败项由 remediation 的直接 real-execution 回归关闭，所有关键结论可判断。
- new_validation_that_could_change_decision: none within approved feature scope。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal、tag/release/publish；spec 明确排除且 changed surface 不触发。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前不是最终 release candidate，Runtime-only affected evidence 足以支持 R2。
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

- `scoped-risk`：单 Runtime 进程、非 shared-NFS writer 是当前一致性边界；外部 filesystem tampering 在后续
  store/publication 操作时 fail closed。
- `process-gap`：artifact GC 与 ownership-proven staging restart cleanup 后置。
- `out-of-scope`：成功发布后的历史 rollback、release package/promotion、Git、JAR 多 Namespace、Console
  publish UI、高级 candidate query mode 尚未交付。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-10 均有 elevated 充分证据；R1 repeat-publish 与 concurrent live-query blocker 已
  关闭，没有 waiver、未知核心项或越界改动。
- blocking_items: none
- follow_up_owner_and_due: Console/Runtime owner；按后续 workitem 接入 Console publish/recovery 体验。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex R2 reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-publish-recovery-api-signoff-r2.md`
- blocking_items: none
- follow_up_required: yes
