---
doc_role: acceptance_record
doc_purpose: Formal phase-level acceptance signoff for the current 9.1.0 Pivot Engine closeout.
acceptance_scope: version
version: 9.1.0
target: 9.1.0 Pivot Engine Phase Closeout
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-05-02
reviewed_by: Codex acceptance reviewer
blocking_items: []
follow_up_required: yes
evidence_count: 5
---

# Version Acceptance

## Background

This record signs off the current 9.1.0 Pivot Engine phase closeout. The scope is release hardening, telemetry, Stage 5A design and renderer preparation, and Stage 5B semantic design.

This is not a Release Candidate signoff. The release recommendation remains `beta / not RC` because Stage 5A production integration is not enabled and Stage 5B implementation remains gated.

## Acceptance Basis

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
- `docs/9.1.0/detailed_design/10_pivot_stage5a_domain_transport_plan.md`
- `docs/9.1.0/detailed_design/11_pivot_stage5b_cascade_generate_semantics.md`
- `docs/9.1.0/acceptance/pivot-stage5a-b1-domain-transport-acceptance.md`
- `docs/9.1.0/acceptance/pivot-stage5a-b2-prep-acceptance.md`
- `docs/9.1.0/acceptance/pivot-stage5b-c1-cascade-generate-semantics-acceptance.md`
- `docs/9.1.0/quality/pivot-stage5a-b2-prep-implementation-quality.md`

## Checklist

- [x] Baseline branch still contains `b3482490` as an ancestor.
- [x] A1/A2 release hardening and telemetry are complete for the current phase.
- [x] B1 Stage 5A domain transport design is signed off with risks.
- [x] B2-Prep renderer skeleton is accepted with risks and remains isolated from production runtime paths.
- [x] C1 Cascade Generate semantics are signed off with risks.
- [x] `domain > 500` production behavior remains fail-closed unless a future B2 production hook explicitly enables safe transport.
- [x] No public Pivot DSL expansion is included in this phase.
- [x] B2 production integration, C2 implementation, and D/E roadmap tracks remain pending, gated, or deferred.

## Module Summary

| Area | Status | Decision | Notes |
|---|---|---|---|
| PIVOT-91-A1 | complete | accepted | Release readiness workflow/script coverage is present. |
| PIVOT-91-A2 | complete | accepted | Safe pushdown/fallback/domain-limit telemetry markers are wired. |
| PIVOT-91-B1 | signed-off | accepted-with-risks | Stage 5A design preserves queryModel lifecycle and fail-closed behavior. |
| PIVOT-91-B2-Prep | signed-off | accepted-with-risks | Internal renderers exist, but production behavior is not enabled. |
| PIVOT-91-C1 | signed-off | accepted-with-risks | Multi-level TopN semantics are frozen for future implementation. |
| PIVOT-91-C2 | gated | N/A | Implementation must not start before explicit approval. |
| PIVOT-91-D/E | deferred | N/A | Tree advanced semantics and outer Pivot cache are outside this closeout. |

## Evidence

| Evidence | Result | Notes |
|---|---|---|
| `git merge-base --is-ancestor b3482490 HEAD` | PASS | Confirmed `baseline ancestor: yes`. |
| `git status --short --branch` | PASS | Worktree is dirty with the expected accumulated 9.1.0 changes. |
| `./scripts/verify-pivot-v9-release.ps1 -SkipFullRegression -SkipMcp` | PASS | Script exited 0 and printed release readiness success. Full regression and MCP guardrail were intentionally skipped by flags. |
| `git diff --check` | PASS | Only Windows CRLF conversion warnings were reported. |
| Feature-level acceptance records | PASS | B1, B2-Prep, and C1 acceptance records exist under `docs/9.1.0/acceptance/`. |

## Risks / Open Items

- Stage 5A production hook is still pending. `DomainTransportPlan` and renderers are not yet wired into `NonAdditiveRollupExecutor` or `JdbcModelQueryEngine`.
- MySQL 8 production renderer selection still needs runtime version probing via JDBC metadata, with a safe gate for MySQL 8.0.19+ behavior.
- MySQL 5.7 derived-table transport remains threshold-limited and must fail closed when tuple count, parameter count, or SQL length exceeds safe limits.
- C2 Cascade Generate implementation is still gated and requires careful translation from the accepted C1 semantics into planner code.
- Full regression and MCP schema guardrail were not part of the quick local closeout run; they remain required before any RC or release promotion.

## Final Decision

The current 9.1.0 Pivot Engine phase is **signed off with risks**.

Release recommendation: keep 9.1.0 as `beta`. Do not promote to Release Candidate until Stage 5A production enablement is either implemented and verified across supported databases, or explicitly deferred by release ownership with updated acceptance criteria.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-05-02
- acceptance_record: `docs/9.1.0/acceptance/version-phase-signoff.md`
- blocking_items: none
- follow_up_required: yes
