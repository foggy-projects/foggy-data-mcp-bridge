---
acceptance_scope: feature
version: 9.5.2-follow-up
target: runtime-console-post-signoff-cleanup
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 10
assurance_level: standard
---

# Runtime Console Post-signoff Cleanup Delivery Signoff

## Background

- delivery_spec: `docs/9.5.2/workitems/REF-runtime-console-post-signoff-cleanup.md`
- target_outcome: 删除不可达旧页面、保留兼容路由、准确记录 changed paths，并补充 CJK 浏览器证据。
- signoff_scope: `f7db91cd..dfd4b2d3`，HEAD `dfd4b2d3`。

## Acceptance Basis

- actual diff：33 paths，全部位于 `addons/foggy-runtime-console/frontend` 或 `docs/9.5.2`；工作树在验收前干净；`git diff --check` exit 0。
- `ModelsPage.vue`、`BundlesPage.vue` 已删除；router 仍将 `/models`、`/bundles` 兼容重定向到 Namespace workspace。
- typecheck exit 0；unit 9 files / 26 tests；build 2470 modules；plain full Playwright 8/8；CJK fontconfig full Playwright 8/8。
- desktop/mobile CJK screenshot：`test-results/runtime-console-namespace--3ae14--and-keyboard-focus-aligned-{desktop,mobile}-chromium/namespace-workspace-*.png`；相关 browser error assertions 通过。

## Contract Conformance

| AC | Evidence | Result |
|---|---|---|
| AC-1 | deleted legacy files、router redirect source、build output无旧 page chunk、full E2E旧路由断言 | pass |
| AC-2 | independent typecheck/unit/build/full Playwright | pass |
| AC-3 | `docs/9.5.2/README.md` 明确 `git diff --name-only <base>...HEAD` 规则 | pass |
| AC-4 | CJK fontconfig desktop/mobile screenshots；console/page error assertions为0 | pass |
| AC-5 | exact changed-path audit；无 Runtime API、engine、数据库、发布配置改动 | pass |

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | medium | source/router/build/full E2E | new | pass |
| AC-2 | core-blocker | medium | typecheck/unit/build/full E2E | new | pass |
| AC-3 | core-blocker | medium | README + diff audit | new | pass |
| AC-4 | core-blocker | medium | CJK fontconfig Playwright screenshots/errors | new | pass |
| AC-5 | core-blocker | medium | 33-path audit | new | pass |

## Findings / Evidence Correction

- The earlier `body.namespace` probe assertion was outside this canonical contract and is classified as `out-of-scope probe assertion`; it is not a product defect or waiver. The corrected probe validates `X-NS` only.
- Build emitted third-party PURE annotation and chunk-size warnings; `out-of-scope`, no impact on this cleanup.

## Final Decision

- decision: accepted
- rationale: all five ACs have current source, path, build and desktop/mobile browser evidence; no blocker remains.
- blocking_items: none
- follow_up_owner_and_due: Console maintainer；生产字体仍依赖宿主环境。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/REF-runtime-console-post-signoff-cleanup-signoff.md
- blocking_items: none
- follow_up_required: yes
