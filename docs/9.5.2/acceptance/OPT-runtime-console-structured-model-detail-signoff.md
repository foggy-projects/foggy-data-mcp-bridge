---
acceptance_scope: feature
version: 9.5.2-follow-up
target: runtime-console-structured-model-detail
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 11
assurance_level: standard
---

# Runtime Console Structured Model Detail Delivery Signoff

## Background

- delivery_spec: `docs/9.5.2/workitems/OPT-runtime-console-structured-model-detail.md`
- target_outcome: 结构化展示模型契约，同时保留原始 JSON、错误/空态、drawer 焦点和移动端行为。
- signoff_scope: `f7db91cd..dfd4b2d3`，HEAD `dfd4b2d3`。

## Acceptance Basis

- changed paths 真实审计为 33 paths，均在 Console frontend/docs；typecheck exit 0、unit 9/26、build 2470 modules、full Playwright plain/CJK 各 8/8；diff check exit 0。
- desktop/mobile screenshots：`structured-model-detail-{desktop,mobile}.png`；full E2E 断言模型身份、字段过滤/搜索、物理表 role、原文、Escape 和 focus return。
- API contract unchanged；没有从 physicalTables 推测 typed QM→TM dependency。

## Evidence Matrix

| AC | Classification | Evidence | Result |
|---|---|---|---|
| AC-1–AC-2 | core-blocker | model drawer manifest/source/purpose/scenarios；desktop/mobile E2E | pass |
| AC-3 | core-blocker | field search、all/dimension/measure/calculated filters、type/aggregation/source/description | pass |
| AC-4–AC-5 | core-blocker | physical table role and explicit “no typed dependency” message | pass |
| AC-6–AC-7 | core-blocker | collapsed raw JSON；data/content/invalid/empty normalization unit tests | pass |
| AC-8 | core-blocker | loading/error/empty/retry/Escape/focus/request-version E2E | pass |
| AC-9 | core-blocker | typecheck/unit/build/full desktop/mobile Playwright | pass |
| AC-10 | core-blocker | exact changed-path audit; no backend contract files | pass |

## Findings / Residual Risks

- Unknown future metadata shapes are preserved in raw response but may not appear in structured sections; bounded diagnostic risk.
- Earlier SQL `body.namespace` probe assertion was out of scope for this and the canonical Console contract; corrected probe passed using `X-NS` authority. No waiver was used.

## Final Decision

- decision: accepted
- rationale: all ten ACs have sufficient current source, unit, build and desktop/mobile browser evidence.
- blocking_items: none
- follow_up_owner_and_due: Console maintainer；未来 metadata 扩展时补 normalization coverage。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/OPT-runtime-console-structured-model-detail-signoff.md
- blocking_items: none
- follow_up_required: yes
