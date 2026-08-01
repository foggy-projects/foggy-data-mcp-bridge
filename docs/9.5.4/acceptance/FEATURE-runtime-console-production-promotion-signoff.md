---
acceptance_scope: feature
version: 9.5.4
target: FEATURE-runtime-console-production-promotion
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 8
assurance_level: elevated
---

# Runtime Console production promotion 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.4 Console release package 与 production promotion UI 形成独立、可复核的正式结论。

## Background

- delivery_spec: `docs/9.5.4/workitems/FEATURE-runtime-console-production-promotion.md`
- target_outcome: 在同源 Runtime Console 中安全导出/导入 package，生产重验后 exact apply，并提供一步
  rollback 与 pinned recovery。
- signoff_scope: `addons/foggy-runtime-console/frontend` 当前 source/unit/Playwright 增量和 9.5.4 文档。
- critical_outcomes: package 不持久化；capability fail closed；imported immutable；exact confirmation；无普通
  publish/live fallback；desktop/mobile 可操作。
- non_blocking_or_waivable_items: drag/drop polish、history timeline、automatic polling。

## Acceptance Basis

- approved delivery spec: API 已先正式 `ACCEPTED`；Console canonical spec 为 `READY_FOR_SIGNOFF`，assurance
  level 保持 `elevated`。
- changed paths / diff: 只修改 Console frontend source/tests 与 `docs/9.5.4`；没有 API/Engine/launcher/POM、
  package-lock 或 npm dependency 改动。
- test records:
  - `npm run typecheck`：通过。
  - `npm run test:unit`：10 files / 34 tests，全部通过。
  - `npm run build`：通过。
  - focused authoring Playwright：desktop/mobile 10/10 通过。
  - full Playwright：desktop/mobile 18/18 通过。
- experience evidence: 真实浏览器执行 JSON download/file input、target selection、production validate/query、
  apply、apply recovery、rollback required recovery 与 rolled-back flow，并生成 desktop/mobile screenshots。
- migration / compatibility evidence: 复用 existing workspace head、routes、candidate inspector 和 publication
  recovery；capability 由服务端加载，旧 promotion-disabled authoring publish E2E 保持通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | export/import/apply/rollback capability 区分，disabled 零请求 | server capability matrix；production mode 隐藏普通 publish；disabled file/action controls | unit policy；focused/full E2E | pass |
| AC-2 | exact export confirmation、安全 JSON download、无浏览器持久化 | pins current candidate；sanitized filename + Blob download；不写 storage/log/URL | download E2E；source review | pass |
| AC-3 | 本地只读 preview、明确 Namespace/Bundle、无自动 apply | preview format/package/source/candidate/resource/validation/trust boundary，confirm 后单次 import | desktop/mobile import E2E | pass |
| AC-4 | imported provenance、编辑禁用、复用生产 validate/query | releaseImport panel；DRAFT/VALIDATED/STALE imported 均不可 mutate；existing exact inspector 可用 | unit + production flow E2E | pass |
| AC-5 | apply exact facts confirmation，只调用 promote，无 retry/fallback | package/candidate/base/source 全展示并由 helper pin；普通 publish/save 零调用 | request-body E2E；source review | pass |
| AC-6 | PUBLISHED evidence 与一步 direct-base rollback | attempt/source/catalog evidence、package/candidate/attempt confirm、最终 `ROLLED_BACK` | desktop/mobile rollback E2E | pass |
| AC-7 | rolling/required 关闭 mutation，显式 refresh/recover | `ROLLING_BACK`/`ROLLBACK_REQUIRED` policy；apply `RECOVERY_REQUIRED` 与 rollback recovery 均 pinned | unit + two recovery E2E flows | pass |
| AC-8 | 全部 frontend 与边界 checks 通过 | typecheck、34 unit、build、10 focused、18 full、diff/untracked checks | 独立重跑记录 | pass |

## Implementation Quality

- scope and changed surface: `WorkspaceReleasePromotion` 只负责 transfer/evidence/action presentation；父级仍持有
  唯一 selected workspace metadata，API orchestration 复用现有 composable/client。
- maintainability and duplication: request builder 集中 pin identity；candidate validate/query 与 publication recovery
  没有复制；没有新增 dependency、poller、retry 或跨 Runtime client。
- error handling and edge cases: invalid local JSON、disabled capability、dirty/unvalidated revision、imported stale、
  PUBLISHING/RECOVERY_REQUIRED/ROLLING_BACK/ROLLBACK_REQUIRED/ROLLED_BACK 均 fail closed并要求显式动作。
- contract, data and compatibility: additive TS fields/states；promotion-disabled 的旧开发 publish/recovery flow 与
  full Console E2E 全绿。
- terminology and documentation: UI 明确“完整性不等于身份”“生产重验”“直接前一 base”“非 history”。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | critical | capability matrix + production publish exclusion | new/rerun | pass |
| AC-2 | core-blocker | critical | exact download + storage/URL absence | new/rerun | pass |
| AC-3 | core-blocker | critical | local file preview + explicit target import | new/rerun | pass |
| AC-4 | core-blocker | critical | immutable policy + production validate/query | new + reused | pass |
| AC-5 | core-blocker | critical | exact promote request/confirmation + zero fallback | new/rerun | pass |
| AC-6 | core-blocker | critical | PUBLISHED evidence + pinned direct-base rollback | new/rerun | pass |
| AC-7 | core-blocker | critical | apply/rollback recovery states and requests | new + reused | pass |
| AC-8 | core-blocker | major | typecheck/unit/build/focused/full/diff | new/rerun | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 服务端不可豁免 guards 已独立 accepted；Console 的所有 mutation
  intent、浏览器持久化边界、desktop/mobile experience、旧页面 compatibility 均有当前自动化证据，满足停止条件。
- new_validation_that_could_change_decision: none within approved scope。
- expensive_validation_omitted_and_reason: Maven、launcher、DB、authority/release 与真实生产 Runtime 不属于
  Console spec；不会改变当前 UI contract 判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none；当前不是 tag/release 候选，full Console lane 已通过。
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

- `scoped-risk`: 本地 package 文件的传递与保管由操作者负责；Console 不证明 signer identity。
- `out-of-scope`: drag/drop、history timeline、automatic polling、签名/KMS、跨 Runtime orchestration。
- `process-gap`: build 仍报告既有第三方 PURE annotation 与 chunk-size warnings；本次未新增 dependency 或主 bundle
  架构，warnings 不影响功能/安全结论。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-8 全部有 elevated、desktop/mobile 且可追溯的通过证据；无 core blocker、无
  deviation、无需 owner waiver。
- blocking_items: none
- follow_up_owner_and_due: none；非目标按未来独立 workitem 评估。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/FEATURE-runtime-console-production-promotion-signoff.md`
- blocking_items: none
- follow_up_required: no
