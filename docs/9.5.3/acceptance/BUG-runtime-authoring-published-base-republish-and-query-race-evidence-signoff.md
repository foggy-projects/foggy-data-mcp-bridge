---
acceptance_scope: bug
version: 9.5.3
target: BUG-runtime-authoring-published-base-republish-and-query-race-evidence
status: signed-off
decision: accepted
signed_off_by: Codex R2 reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 8
assurance_level: elevated
---

# Published base 再发布与并发 live-query 证据修复正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对首次 publish/recovery feature 验收发现的 immutable-base 再发布缺陷与 AC-9 证据缺口形成
  独立、可复核的 R2 修复结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`
- target_outcome: verified Runtime-owned immutable artifact 可作为下一 workspace 的只读 base；连续发布与失败
  恢复不改写旧 artifact；publication 窗口 live query 只观察 old/new/not-current。
- signoff_scope: `main` base `80fe0fb2` 上当前未提交 tracked 与 untracked 工作树。
- critical_outcomes: immutable provenance、append-only artifacts、exact recovery、无 mixed catalog 均不可豁免。
- non_blocking_or_waivable_items: artifact GC、staging restart cleanup、单进程/非 shared-NFS 前提。

## Acceptance Basis

- approved delivery spec: canonical BUG 为 `READY_FOR_SIGNOFF`，assurance level 为 `elevated`。
- changed paths / diff: 修复生产改动只在 `foggy-runtime-api` publication/workspace/artifact services；整体候选
  仍仅含 `foggy-runtime-api` 与 docs，没有 Engine/Model SPI、Console、launcher、POM、依赖图或 schema 改动。
- test records:
  - RED：第二次 publish 1 test 修复前按预期以 `WORKSPACE_SOURCE_INELIGIBLE` 失败。
  - focused：7 classes / 61 tests，0 failures/errors/skips，56.768s。
  - 最终 affected：14 classes / 152 tests，0 failures/errors/skips，55.269s。
  - affected 中真实 filesystem + SQLite：6/6；tamper 定向回归：1/1。
- experience evidence: 完整 status/diff 范围复核；tracked `git diff --check` 与全部 untracked 文件逐个
  `git diff --no-index --check` 通过。
- migration / compatibility evidence: 不改 route/DTO/attempt schema；复用 v1 previous immutable metadata；auth、
  Bundle/resource/model compatibility selection 全绿。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | v1 publish 后修改 TM/QM/FSScript、validate、v2 publish/query；v1 不变 | registry/source/catalog 收敛 v2，live SQLite 仅返回 `DRAFT-V2-001`；逐文件快照确认 v1 resources/manifest 不变 | `secondWorkspacePublishesFromImmutableBaseAndPreservesFirstArtifact` | pass |
| AC-2 | owned completed matching artifact 才可进入 intent；失败零 mutation | root/path/attempt/status/Bundle/Namespace/revision/manifest/hash 全校验；tamper 返回 `WORKSPACE_ARTIFACT_CORRUPT` 且 artifact/attempt/registry/live/revision/refresh 不变 | publication store 7 tests；publish service tamper regression；source review | pass |
| AC-3 | 第二次 publish 失败精确恢复 v1 immutable source/catalog | attempt 保留 previous immutable/artifact revision；refresh fault 恢复同一 v1 path、record、script 与 catalog | `failedSecondPublicationRestoresTheExactImmutableBaseArtifact`；既有 registry/final/explicit recovery matrix | pass |
| AC-4 | 并发 live query 只允许 complete old/new 或稳定 not-current | 真实 SQLite/production semantic query 与 Bundle lifecycle；受控 source-applied 窗口 12 次观察，mixed 直接失败，最终 new current | `liveQueriesDuringPublicationObserveOnlyCompleteOldOrNewCatalogs` | pass |
| AC-5 | immutable save guard、首次 publish、单赢家、shared lock、restart/auth/compatibility 无回归 | focused/affected selections 全绿，specified tests 未跳过 | 61-test focused；152-test affected | pass |

## Implementation Quality

- scope and changed surface: Runtime-only 修复符合 do-not-touch；没有借机实现 Console、release 或历史 rollback。
- maintainability and duplication: provenance 集中在 artifact store，publication coordinator 只编排资格、intent、
  source/registry/refresh/recovery；没有 test-only production bypass。
- error handling and edge cases: foreign、tampered、missing/corrupt attempt、unknown status、identity/live drift、
  symlink 均 fail closed；dangerous recovery conflict 为 `safeToAutoRepair=false`。
- contract, data and compatibility: immutable 含义收敛为 artifact bytes 只读；升级追加新 artifact；公共 API 与
  attempt schema 不变。
- terminology and documentation: current architecture 与 9.5.3 README 已同步；原 R1 rejection 保持历史事实。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | critical | real SQLite v1→v2 + all three resource types + v1 snapshot | new | pass |
| AC-2 | core-blocker | critical | provenance source review + store/tamper zero-mutation tests | new | pass |
| AC-3 | core-blocker | critical | immutable-base recovery + existing fault/restart matrix | new/reused | pass |
| AC-4 | core-blocker | critical | controlled concurrent production semantic query | new | pass |
| AC-5 | core-blocker | critical | focused/affected compatibility selections | rerun | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 两个 R1 blocker 均有直接长期回归；最终 affected lane 同时覆盖
  immutable-specific change 与既有 API/auth/recovery/compatibility，足以判断 Runtime-local 单进程契约。
- new_validation_that_could_change_decision: none within approved scope。
- expensive_validation_omitted_and_reason: spec 明确不要求完整 reactor、Console/Playwright、launcher、数据库
  矩阵或 authority/replay/rehearsal/source-seal；修复未触及这些边界，追加验证不会改变本次决定。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 非 release/tag/promotion 候选，Runtime-only focused/affected evidence 已充分。
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

- `scoped-risk`：一致性承诺限单 Runtime 进程、非 shared-NFS writer；外部绕过 Runtime 的 filesystem tampering
  在下一次 store/publication 操作中 fail closed。
- `process-gap`：artifact GC 与 ownership-proven `.staging-*` restart cleanup 尚未实现；不得删除仍被引用的
  artifact。
- `out-of-scope`：历史 rollback、release package、生产 promotion、Git、JAR 多 Namespace、Console publish UI
  与高级 candidate query mode 由后续 workitem 交付。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-5 全部有 elevated 充分证据，两个 R1 core-blocker 已关闭，无 waiver 或新 blocker。
- blocking_items: none
- follow_up_owner_and_due: Runtime/Console owner；按独立 workitem 推进非阻断后续能力。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex R2 reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/BUG-runtime-authoring-published-base-republish-and-query-race-evidence-signoff.md`
- blocking_items: none
- follow_up_required: yes
