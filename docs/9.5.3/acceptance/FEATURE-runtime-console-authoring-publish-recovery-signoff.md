---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-console-authoring-publish-recovery
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 10
assurance_level: standard
---

# Feature / Bug Delivery Signoff

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 Runtime Console authoring publish 与失败恢复闭环形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/FEATURE-runtime-console-authoring-publish-recovery.md`
- target_outcome: Console 对 exact validated candidate 提供受控 publish，忠实展示 publication state/evidence，
  只对服务端 pinned failed attempt 提供 recovery，并从 immutable published workspace 进入下一独立 workspace。
- signoff_scope: 当前 `main` 实际工作树中的 Console frontend 与 9.5.3 文档改动，包括三个 untracked 文件。
- critical_outcomes: exact identity、no automatic mutation retry、PUBLISHING/RECOVERY_REQUIRED/PUBLISHED guards、
  terminal evidence、eligible next workspace、zero live fallback 和零后端/依赖改动。
- non_blocking_or_waivable_items: 自动 polling、publication 历史、artifact 下载、成功发布后的历史 rollback、
  release/production promotion、Git 和 JAR 多 Namespace binding 均未纳入本交付。

## Acceptance Basis

- approved delivery spec: canonical spec 已由用户批准，验收时状态为 `READY_FOR_SIGNOFF`，
  `assurance_level=standard`，AC-1 至 AC-10 均为 must-pass。
- changed paths / diff: `git status --short`、完整 tracked diff 和三个 untracked 文件逐一读取；实际路径只在
  `addons/foggy-runtime-console/frontend` 与 `docs/9.5.3`，无 package lock、POM、Runtime API、Engine 或 launcher。
- test records: typecheck、10 files / 33 unit、production build、focused Playwright 6/6、full Console Playwright
  14/14 均在当前源码实际通过；最终 tracked/untracked whitespace checks 通过。
- experience evidence: desktop/mobile published 与 recovered screenshots 已人工检查，状态、evidence、主动作与
  disabled/terminal 层级清楚，无横向溢出和 dialog/toast 遮挡。
- migration / compatibility evidence: additive frontend types/composable/component；无数据 schema、浏览器持久化、
  API 或依赖变更。现有草稿闭环由 full Console lane 基于当前源码重跑。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 新状态/evidence 完整且未知值 fail closed | 类型覆盖三种 publication state；未知 state 零动作，缺失/mismatch attempt 不开放 recovery | policy unit + source review | pass |
| AC-2 | exact current validated、clean、idle 才可 publish | validation identity/valid/state 与 dirty/busy gates 均存在，confirmation 展示完整边界 | unit + desktop/mobile Playwright | pass |
| AC-3 | publish 只提交三个 exact identity，无 live fallback | 独立 request builder 只返回三个字段；E2E 逐字断言 body、route、`X-NS` 与零 fallback | unit + Playwright request capture | pass |
| AC-4 | PUBLISHED evidence 与 immutable 终态准确 | 显示 attempt/applied/source/catalog facts；save/validate/query/discard 均关闭，resource/diff 可读 | Playwright + screenshots + source review | pass |
| AC-5 | eligible Bundle 才创建下一 workspace | 入口依赖当前 Bundle inventory 的 `workspaceEligible=true`，复用 create API 并打开新 workspace identity | desktop/mobile Playwright | pass |
| AC-6 | PUBLISHING 仅 metadata refresh | mutation/validate/query/discard/publish/recover 全关；无 polling，显式 GET 后收敛 | policy unit + desktop/mobile Playwright | pass |
| AC-7 | RECOVERY_REQUIRED 只 exact pinned recovery | attempt/candidate 双重 gate、二次确认、单次 recover；错误保留 Runtime code/phase/action | unit + failure/recovery Playwright | pass |
| AC-8 | recovery 后 STALE/RECOVERED，非历史 rollback | 显示 recovered catalog/diagnostics 与明确文案；草稿保留并沿用 STALE 迁移路径 | desktop/mobile Playwright + screenshots | pass |
| AC-9 | desktop/mobile 状态与旧流程兼容 | publish/recovery 两条新 flow 与原完整 authoring flow 均在两 viewport 通过 | focused 6/6 + full 14/14 | pass |
| AC-10 | 所有规定验证与范围检查通过 | typecheck/unit/build/E2E/diff/untracked checks 全绿，路径和依赖面合规 | 当前命令记录 + status/diff audit | pass |

## Implementation Quality

- scope and changed surface: 改动严格在批准模块；生产修改集中于 authoring feature，文档只增加 canonical
  workitem 与版本索引，未改后端产品实现。
- maintainability and duplication: publish/recover/refresh API 编排提取到单一 composable；展示事实放在独立
  component，`AuthoringWorkspace.vue` 仍是唯一 workspace head owner，没有复制 revision state。
- error handling and edge cases: 未知 state、invalid validation、dirty/busy、attempt 缺失或 candidate mismatch
  均 fail closed；publish failure 只读取 metadata，publish/recover 都没有自动 mutation retry。
- contract, data and compatibility: DTO 字段和 route/body 与已验收 API 一致；不发送 caller target/path override，
  不持久化 client publication state，不新增依赖；既有 create/edit/diff/validate/query/discard 保持通过。
- terminology and documentation: UI 与文档一致使用 publish、failed publication recovery、immutable terminal、
  exact revision，并明确 recovery 不是历史 rollback、开发 Runtime 不等于生产 promotion。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | policy unit、types/source review | new | pass |
| AC-2 | core-blocker | major | current-validation unit、confirmation/enablement E2E | new | pass |
| AC-3 | core-blocker | critical | exact request body/header/route 与 zero-fallback E2E | new + accepted API contract | pass |
| AC-4 | core-blocker | critical | PUBLISHED transition、terminal guards、evidence screenshots | new | pass |
| AC-5 | core-blocker | major | eligible create-next desktop/mobile flow | new + accepted create API | pass |
| AC-6 | core-blocker | major | PUBLISHING matrix + explicit refresh E2E | new | pass |
| AC-7 | core-blocker | critical | recovery request helper + failure/recovery E2E | new + accepted recovery API | pass |
| AC-8 | core-blocker | critical | STALE/RECOVERED state/evidence/screenshots | new + accepted recovery semantics | pass |
| AC-9 | core-blocker | major | focused 6/6、full Console 14/14 | new + existing Console flow | pass |
| AC-10 | core-blocker | major | typecheck/unit/build/diff/scope audit | new | pass |

## Evidence Sufficiency

- assurance_level: standard；保持 canonical spec 规定等级，未因 publish/signoff 字样升级。
- why_existing_evidence_is_sufficient_or_not: 当前改动是对已验收 API 的纯 Console 接入。新增 unit 与
  desktop/mobile Playwright 覆盖 exact request、成功、中间态、失败恢复、终态和 zero fallback；full lane
  同时重新覆盖既有 Console 行为。后端生产实现未变，其 R2 exact/recovery/auth evidence 复用前提仍成立。
- new_validation_that_could_change_decision: none；must-pass 已有当前源码直接证据，继续运行 Maven 或真实
  publication 环境不会与批准的 frontend changed surface 相称。
- expensive_validation_omitted_and_reason: 未运行 Maven reactor、Runtime/Engine、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal、release 或 production publish；canonical spec 明确不要求且本 diff
  不触及这些面。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: none；不是 release candidate，也未改变公共 API、安全、迁移或跨模块拓扑。
- estimated_wall_clock_and_basis: not-estimated
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

- none。首轮 focused run 仅暴露 UI 结构变化后的 locator/同文案 toast 严格匹配问题；断言被收窄到真实
  action/最新 toast，mutation count 与 exact revision 断言未放宽，最终 focused 6/6 与 full 14/14 均通过。

## Risks / Follow-ups

- scoped-risk: Console 不自动 polling；离开 `PUBLISHING` 后需重新打开或显式刷新 metadata。服务端事务不受
  影响，且这是 approved non-blocking behavior。
- process-gap: 当前测试宿主缺少 CJK 字体，截图使用缺字 fallback；DOM/accessibility/interaction assertions
  通过，产品 font stack 未改变。具备 CJK 字体的视觉回归环境可在后续基础设施维护中补齐。
- out-of-scope: release package、生产 promotion/import、成功发布后的历史 rollback、Git 与 JAR 多 Namespace
  binding 仍需后续独立 workitem；Console 未声明这些能力。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-10 全部具备与 standard assurance 相称的当前证据；实现严格符合 exact identity、
  pinned recovery、immutable terminal、no retry/no fallback 和范围边界，没有 core blocker 或需 waiver 项。
- blocking_items: none
- follow_up_owner_and_due: product/runtime roadmap；后续能力按 9.5.3 路线另立契约，无本次签收前置期限。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-console-authoring-publish-recovery-signoff.md`
- blocking_items: none
- follow_up_required: yes
