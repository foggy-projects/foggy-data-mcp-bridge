---
doc_role: acceptance_record
doc_purpose: Sign off the ServiceTicket SLA semantic fixture and runtime gates.
acceptance_scope: feature
version: 9.1.0
target: ServiceTicket SLA Semantic Fixture
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex acceptance reviewer
signed_off_at: 2026-06-01
reviewed_by: Codex acceptance reviewer
blocking_items: []
follow_up_required: yes
evidence_count: 14
---

# Feature Acceptance

## Background

The ServiceTicket SLA work adds a governed lite fixture, executable DSL_CTE bridge, semantic scoring, holdout scoring, and fail-closed runtime guards for unsupported SLA variants. The acceptance scope is the signed first-response SLA recipe and its runtime boundary, not arbitrary service-ticket analytics.

## Acceptance Basis

| Source | Role |
|---|---|
| `workitems/P2-service-ticket-lite-semantic-fixture-20260601.md` | Requirement, implementation, evidence, and residual history. |
| `quality/service-ticket-sla-semantic-fixture-implementation-quality.md` | Implementation quality review and residual risks. |
| `experiments/spider-routing-eval/README.md` | Replay and gate runbook. |
| `experiments/spider-routing-eval/Makefile` | Stable gate entrypoints. |

## Checklist

| Item | Status | Notes |
|---|---|---|
| Visible model catalog includes ServiceTicket | accepted | Lite catalog exposes `ServiceTicketQueryModel`. |
| Runnable fixture data exists | accepted | `dim_team` and `service_ticket` rows cover June 2026 hit, miss, and NULL first-response cases. |
| Positive `biz-024` SLA recipe executes | accepted | Three-model runtime replay reached `stable_gate_ok=3/3`. |
| SLA holdout semantics execute | accepted | `holdout-005/006` reached `stable_gate_ok=6/6`. |
| Negative runtime boundary is enforced | accepted | Missing threshold, unsupported SLA, physical SQL, and prediction/personnel advice fail closed. |
| Unresponded-count canonicalization is enforced | accepted | `ticketCount - slaHitCount` remains a signed miss-count shape only for miss-count aliases; it is rejected when used as a requested unresponded-count field or explanation. |
| Combined gate is stable | accepted | `make gate-v39-service-ticket-sla-stable-suite` reached `13/13`, residuals `0`. |
| Unsupported variants stay out of scope | accepted-with-risks | Resolution SLA, contract calendar, custom work time, causality, and personnel recommendation are intentionally not executed. |

## Evidence

| Evidence | Result |
|---|---|
| `mvn -pl foggy-dataset-mcp -am -Dtest=QueryExpertServiceRoutingCalibrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | passed: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`. |
| `mvn -pl foggy-mcp-launcher -am -DskipTests package` | passed after the ServiceTicket SLA preflight guard. |
| Java lite fixture smoke | passed: catalog includes `ServiceTicketQueryModel`; fixture query returns rows. |
| Direct positive `biz-024` NL runtime smoke | passed: response `type=result`, `total=3`, `code=null`. |
| `make gate-v39-biz024-semantic-stable` on corrected runtime baseline | passed: `stable_gate_ok=3/3`, residuals `0`. |
| `make gate-v39-service-ticket-sla-holdout-semantic-stable` | passed: `stable_gate_ok=6/6`, residuals `0`. |
| Negative gate before runtime preflight | failed as intended: `stable_gate_ok=0/4`, residuals `20`. |
| Negative gate after runtime preflight | passed: `stable_gate_ok=4/4`, residuals `0`. |
| `make gate-v39-service-ticket-sla-stable-suite` | passed: `stable_gate_ok=13/13`, residuals `0`. |
| `python3 experiments/spider-routing-eval/scripts/test_score_biz024_semantic_gate.py` | passed. |
| `mvn -pl foggy-dataset-model -am -Dtest=DslCteAcceptanceSampleTest,DslCteSlaFixtureIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` after the 2026-06-02 unresponded-count hardening | passed: `Tests run: 176, Failures: 0, Errors: 0, Skipped: 0`. |
| `make -C experiments/spider-routing-eval gate-v39-semantic-promoted-offline-ci` after the 2026-06-02 unresponded-count hardening | passed: order `6/6`, ServiceTicket suite `13/13`, residuals `0`. |
| `python3 experiments/spider-routing-eval/scripts/test_score_service_ticket_negative_gate.py` | passed. |
| `python3 experiments/spider-routing-eval/scripts/test_build_service_ticket_sla_gate_suite_summary.py` | passed. |

## Failed Items

None blocking. The earlier negative baseline is retained as regression evidence because it proved the runtime previously executed out-of-scope ServiceTicket SLA requests.

## Risks / Open Items

| Risk | Disposition |
|---|---|
| SLA support is intentionally narrow. | Accepted with risk; only first-response SLA with explicit threshold is signed. |
| Resolution SLA, contract calendar, business-hours calendar, prediction, and personnel advice are not supported. | Fail closed through preflight guard; add new recipe contracts only with samples and tests. |
| Replay artifacts under `experiments/spider-routing-eval/output/` are ignored by git. | Gate scripts are committed; CI or release jobs must regenerate or publish evidence artifacts explicitly. |
| Java launcher URL shape differs from Python replay scripts. | Documented: Java Spring AI base URL omits `/v1`; Python replay uses `/v1`. |
| SLA miss count and unresponded count are easy for LLMs to conflate. | Accepted with guardrail: explicit cutoff/reference-time unresponded predicates are required; ambiguous difference arithmetic fails validation/scoring. |

## Final Decision

Accepted with risks. The ServiceTicket SLA fixture is signed for v3.9/v3.x engine promotion as a governed first-response SLA capability with strict negative runtime boundaries.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex acceptance reviewer
- signed_off_at: 2026-06-01
- blocking_items: none
- follow_up_required: yes
