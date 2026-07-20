---
doc_type: delivery-spec
delivery_type: cross-module
version: 9.3.4
ticket: step4-replacement-coverage-gate-signoff
status: DRAFT
canonical: true
execution_mode: ultra
approved_by: pending-project-owner-authorization
approved_at: pending
open_questions:
  - "是否批准本 Step 4 replacement-signoff scope，并指定签收 owner？"
---

# Delivery Spec: 9.3.4 Step 4 Replacement Coverage Gate Signoff

## Document Purpose

- intended_for: project-owner / independent-signoff / 9.3.4 root session
- purpose: 固定已完成的 Step 4 replacement evidence 的签收边界；不复制或替换 Steps 5–7 的
  release-authority delivery spec。
- canonical_path: `docs/9.3.4/workitems/FEATURE-step4-replacement-coverage-gate-signoff.md`

## Goal

- version_goal: 在不复用历史失效 authority 的前提下，完成 9.3.4 Step 4 JaCoCo coverage gate 的
  replacement feature signoff，并仅在正式接受后开放 Step 5。
- target_outcome: 对 fresh formal-r11、同 Cfreeze Pivot companion、独立质量审查、35-row
  replacement audit 与独立 receipt gate 作出可追溯的 feature acceptance 决定。

## Scope

- in_scope:
  - 以 Cfreeze `4b17dbe34ae17168d44377c19ea6637b4c37a200` 的 fresh formal-r11 作为唯一
    replacement formal authority。
  - 审核 final artifact、public receipt mode、source/provenance、cleanup、Pivot companion、
    implementation quality 与 replacement audit 的一致性。
  - 在签收 owner 明确批准后，依交付签收程序形成 Step 4 feature acceptance record，并更新
    Step 5 的入口状态。
- affected_modules: `scripts/v934/step4` 的既有证据契约与 `docs/9.3.4` 的 Step 4
  evidence/quality/coverage/acceptance records。
- external_dependencies: project-owner authorization and independent signoff ownership.

## Non-Goals

- out_of_scope:
  - 不实施或预先启动 Step 5–7、9.3.5 或 9.4.0。
  - 不重新运行 formal-r11，除非独立签收发现 authority 或证据不一致。
  - 不把 r10、r34、r35 diagnostic 或任何历史 raw runtime artifact 拼接为 formal authority。
  - 不关闭 `DEBT-unit-mysql57-fixture-classification-migration`。
- do_not_touch:
  - 不降低 coverage threshold、扩大 exclusion、放宽 public-output 或 cleanup 约束。
  - 不修改 `FEATURE-v934-release-authority-and-ci.md` 的 Steps 5–7 scope 或其
    `ULTRA_EXECUTING` 状态。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| formal-r11 is the sole replacement formal authority | fresh clean-clone execution proves the final Cfreeze state | historical diagnostics remain regression context only |
| audit denominator is 35 plus one independent gate | legacy31 + supplemental4 are the audit rows; report-stage receipt binding is separately blocking | do not call it a 36-row audit |
| Pivot companion is supplemental only | it closes a focused legacy branch on the same Cfreeze | it must not change formal execution or aggregate totals |
| MySQL fixture debt remains open | Step 4 proves the run-owned boundary, not the permanent classification migration | debt is due before 9.3.5 version acceptance |
| acceptance is feature-scoped | Step 4 closure is a prerequisite, not 9.3.4 version signoff | Step 5 may become ready only after accepted decision |

## Acceptance Criteria

- [x] AC-1: fresh formal-r11 independently proves required `773+59/5707/F0E0S0`, exact public receipt
  `0644`, final artifact replay, and zero cleanup residue.
- [x] AC-2: same-Cfreeze Pivot companion passes and is explicitly excluded from formal aggregate totals.
- [x] AC-3: final implementation quality is `APPROVE / B/H/M/L=0/0/0/0`.
- [x] AC-4: replacement coverage audit covers 35 rows, reports zero critical/major gaps, and separately
  passes the report-stage receipt gate.
- [ ] AC-5: project owner approves this Step 4-scoped delivery contract and names or delegates the
  independent signoff owner.
- [ ] AC-6: independent signoff confirms the sole-authority boundary, issues an accepted/rejected decision,
  and updates the Step 4/Step 5 entry state without opening later version scope prematurely.

## Contract / Data / Security Constraints

- API or event contract: no production API, SPI, module boundary, workflow, or data contract change is in
  this feature-signoff scope.
- data and migration: no migration; preserve the Unit MySQL fixture classification debt and its 9.3.5
  closure deadline.
- compatibility and rollback: an acceptance decision only changes roadmap gating; if evidence is rejected,
  retain current fail-closed status and replan from the discrepancy.
- permissions and secrets: persist only safe summaries and reproducible digests; do not copy raw logs, exec
  payloads, container identities, credentials, or runtime report trees into Git evidence.

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | critical | independent final-artifact replay and Cfreeze topology check | `evidence/step-4/step4-coverage-formal-r11-pass-20260720.md` |
| AC-2 | major | focused Failsafe result and direct source/report identity review | `evidence/step-4/step4-pivot-legacy-companion-r11-20260720.md` |
| AC-3 | critical | independent implementation-quality review | `quality/step4-formal-r11-final-implementation-quality-20260720.md` |
| AC-4 | critical | workitem-to-evidence mapping review | `coverage/step4-replacement-coverage-audit-20260720.md` and `coverage/step4-replacement-audit-scope-20260720.json` |
| AC-5/AC-6 | critical | delivery-signoff procedure against this canonical spec | `acceptance/step4-coverage-gate-replacement-signoff-prerequisite-20260720.md` and final acceptance record |

## Bug Context

N/A. This is a bounded feature-signoff contract for already completed evidence, not a new bug remediation.

## Risks and Open Questions

- known_risks: approving or signing off against the existing Steps 5–7 canonical spec would conflate scopes
  and could incorrectly open downstream work.
- open_questions: project-owner authorization and signoff-owner designation remain required.

## Ultra Execution Contract

- Before approval, do not change this document to `APPROVED`, `ULTRA_EXECUTING`, or `READY_FOR_SIGNOFF`.
- After approval, read this document, the linked evidence, the project rules, and the delivery-signoff
  procedure before making an acceptance decision.
- If an authority mismatch, missing evidence, changed scope, or new blocking risk appears, set
  `NEEDS_REPLAN` and stop downstream opening.
- Do not modify production code or begin Step 5–7 while executing this signoff scope.
- Record the acceptance result, exact reviewed evidence, deviations, and residual risks in the canonical
  acceptance record; only an independent signoff owner may set acceptance outcome.

## Implementation Result

- implementation_summary: formal-r11 and all defined post-formal evidence gates are complete; this DRAFT
  records the missing owner-authorization boundary only.
- changed_paths: evidence and governance records listed in References.
- tests_and_results: AC-1 through AC-4 are evidenced; AC-5 and AC-6 are intentionally pending.
- manual_or_experience_evidence: N/A; this is a backend evidence/signoff feature with no UI scope.
- deviations: none
- residual_risks: absent project-owner authorization would make any acceptance assertion invalid.
- readiness: BLOCKED

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- architecture / glossary: `docs/9.3.4/implementation-plan.md`
- related work items:
  - `docs/9.3.4/acceptance/step4-coverage-gate-replacement-signoff-prerequisite-20260720.md`
  - `docs/9.3.4/evidence/step-4/step4-coverage-formal-r11-pass-20260720.md`
  - `docs/9.3.4/evidence/step-4/step4-pivot-legacy-companion-r11-20260720.md`
  - `docs/9.3.4/quality/step4-formal-r11-final-implementation-quality-20260720.md`
  - `docs/9.3.4/coverage/step4-replacement-coverage-audit-20260720.md`
  - `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`
