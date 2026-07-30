---
acceptance_scope: feature
version: 9.5.2-follow-up
target: runtime-console-global-namespace-context
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 12
assurance_level: standard
---

# Runtime Console Global Namespace Context Delivery Signoff

## Background

- delivery_spec: `docs/9.5.2/workitems/OPT-runtime-console-global-namespace-context.md`
- target_outcome: session、路由、页面状态和 `X-NS` 保持一致，切换时清理旧结果并隔离 stale response。
- signoff_scope: `f7db91cd..dfd4b2d3`，HEAD `dfd4b2d3`。

## Acceptance Basis

- 实际 diff 为 33 paths，全部在 Console frontend/docs；无 Runtime API、engine、数据库改动；`git diff --check` exit 0。
- typecheck、unit 9/26、build 2470 modules、full Playwright plain 8/8 和 CJK fontconfig 8/8 通过。
- existing full E2E 覆盖 default/finance/空 Namespace、reload、快速切换和 stale response；corrected independent probe 额外确认 Tables 的 default→finance `X-NS` 和 SQL 上下文。
- probe console/page errors 均为 0；CJK desktop/mobile screenshot 已保存。

## Contract Conformance

| AC | Evidence | Result |
|---|---|---|
| AC-1 | session revision、route query、top selector、request headers；full E2E finance/reload | pass |
| AC-2 | Query candidate/result reset；full E2E | pass |
| AC-3 | Tables datasource/list/result reload；corrected probe | pass |
| AC-4 | Compose/FSScript result reset with editor retained；full E2E | pass |
| AC-5 | delayed A→B response test；full E2E | pass |
| AC-6 | empty Namespace request omits `X-NS`；full E2E | pass |
| AC-7 | context rail/top state/accessibility names；full E2E | pass |
| AC-8 | typecheck/unit/build/full desktop/mobile Playwright | pass |
| AC-9 | exact path audit, no backend/API paths | pass |

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1–AC-6 | core-blocker | major | session/source + namespace-context E2E + corrected probe | reused + new | pass |
| AC-7 | core-blocker | medium | context rail/accessibility desktop/mobile E2E | reused | pass |
| AC-8 | core-blocker | major | typecheck/unit/build/full E2E | reused | pass |
| AC-9 | core-blocker | major | changed-path audit | new | pass |

## Findings / Residual Risks

- The earlier `body.namespace` assertion is recorded as `out-of-scope probe assertion`; canonical Namespace authority is `X-NS`, and the corrected probe passed without requiring duplicate body namespace.
- Revision filtering ignores stale responses but does not cancel read-only network work; bounded residual risk, no UI state leakage observed.

## Final Decision

- decision: accepted
- rationale: all must-pass Namespace criteria have current source and desktop/mobile browser evidence; `X-NS` is consistent across namespace transitions and no core-blocker remains.
- blocking_items: none
- follow_up_owner_and_due: Console maintainer；继续观察只读 stale 请求的服务端资源消耗。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/OPT-runtime-console-global-namespace-context-signoff.md
- blocking_items: none
- follow_up_required: yes
