---
acceptance_scope: feature
version: 9.5.2-follow-up
target: runtime-console-tables-sql-tm-draft
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 14
assurance_level: standard
---

# Runtime Console Tables / SQL / TM Draft Delivery Signoff

## Background

- delivery_spec: `docs/9.5.2/workitems/OPT-runtime-console-tables-sql-tm-draft.md`
- target_outcome: inspect、只读 SELECT 和机械 TM draft 组成可控探索链；Namespace 权威为 `X-NS`。
- signoff_scope: `f7db91cd..dfd4b2d3`，HEAD `dfd4b2d3`。

## Acceptance Basis

- actual diff 33 paths，均在 Console frontend/docs；无 Runtime API、engine、数据库、发布配置改动；diff check exit 0。
- typecheck exit 0、unit 9/26、build 2470 modules、plain/CJK full Playwright 各 8/8。
- corrected independent Playwright probe desktop/mobile 2/2：default 和 finance 的 `X-NS`、dataSource、SQL 与页面上下文一致；console/page error 均为 0。
- CJK evidence screenshots：`corrected-sql-namespace-{desktop,mobile}.png` 与 `tables-tm-draft-{desktop,mobile}.png`。

## Contract Conformance

| AC | Expected / Evidence | Result |
|---|---|---|
| AC-1 | inspect 保存 datasource/schema/table/columns/PK；full E2E | pass |
| AC-2 | 仅安全 schema/table 生成 `SELECT * FROM ...`；unit + E2E | pass |
| AC-3 | `X-NS` 为 Namespace authority；dataSource、SQL、页面上下文一致；corrected probe default→finance | pass |
| AC-4–AC-6 | draft metadata mechanical mapping、unknown STRING、empty dimensions/measures | pass |
| AC-7–AC-8 | copy/download、本地 `.tm`、明确未校验/保存/注册/刷新、不调用写端点 | pass |
| AC-9 | Namespace reset 清 inspect/SQL/draft、保留编辑 SQL；full E2E | pass |
| AC-10 | desktop/mobile/typecheck/unit/build/full E2E/diff check | pass |
| AC-11 | exact changed-path audit | pass |

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1–AC-2 | core-blocker | major | inspect/SELECT full E2E + table draft unit | reused | pass |
| AC-3 | core-blocker | major | corrected independent probe: `X-NS` default/finance，body dataSource/SQL，页面 context | new | pass |
| AC-4–AC-8 | core-blocker | major | TM draft unit/full desktop-mobile E2E | reused | pass |
| AC-9 | core-blocker | major | Namespace reset full E2E | reused | pass |
| AC-10 | core-blocker | major | command results + full Playwright | reused + new | pass |
| AC-11 | core-blocker | major | path audit | new | pass |

## Findings / Evidence Correction

- The prior `body.namespace` assertion is explicitly classified as `out-of-scope probe assertion` under the corrected contract. It is not a product failure, not a waiver, and not a residual product risk.
- No frontend implementation change was made to satisfy that assertion; the request continues to use `X-NS` as the authoritative Namespace header.

## Residual Risks

- JDBC type mapping is intentionally conservative; complex dialect types may map to STRING and require human review.
- The draft has no business semantics and does not represent Runtime acceptance, registration or refresh.

## Final Decision

- decision: accepted
- rationale: all eleven ACs have sufficient source, unit, build and desktop/mobile browser evidence under the corrected canonical contract.
- blocking_items: none
- follow_up_owner_and_due: Console maintainer；复杂 JDBC 类型仍需人工复核。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record: docs/9.5.2/acceptance/OPT-runtime-console-tables-sql-tm-draft-signoff.md
- blocking_items: none
- follow_up_required: yes
