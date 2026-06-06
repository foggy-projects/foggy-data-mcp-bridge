---
doc_role: domain_model
doc_purpose: Record ServiceTicket SLA, backlog, status, priority, and time-field semantic contracts.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Service Ticket Domain Model

## Scope

This document records the current service-ticket semantic contract for `ServiceTicketModel` and `ServiceTicketQueryModel`.

It is a domain model contract, not a generic engine rule. The engine owns DSL_CTE validation, query execution, and clarify/reject mechanics. Service-ticket field names, SLA policy slots, and backlog policy wording belong in this document, the ServiceTicket TM/QM files, and the service-ticket fixtures.

## Current Model Surface

| Family | Field Or Concept | Current Contract | Must Not Be Confused With |
|---|---|---|---|
| Ticket workflow state | `status` | Current model dictionary text only exposes `OPEN` and `RESOLVED`. | Team activation, backlog sub-state, SLA hit/miss state. |
| Team activation | `team$status` | Team enabled/disabled or availability state. | Ticket workflow state. |
| Priority | `priority` | `P1`, `P2`, `P3`; usable for explicit priority-threshold SLA policies. | Ticket lifecycle or inferred default SLA thresholds. |
| Created time | `createdAt` | Ticket creation time; denominator time scope and elapsed-duration start. | First response time or resolution time. |
| First response time | `firstResponseAt` | First response timestamp; null means unresponded and is not an SLA hit. | Resolution time, generic handled time, customer-wait time. |
| Resolution time | `resolvedAt` | Resolution timestamp; null means unresolved and is not a resolution-SLA hit. | First response time. |
| Channel | `channel` | Source-channel dimension. | SLA policy or workflow status. |
| Ticket count | `ticketCount` | Count of tickets in the scoped denominator. | SLA-hit count, SLA-miss count, unresponded-overdue count. |

## Signed SLA Recipes

The source of truth for executable SLA recipes is `docs/9.1.0/detailed_design/16_service_ticket_sla_recipe_contract_index.md`.

| Recipe | Status | Domain Meaning |
|---|---|---|
| First-response SLA, natural hours | signed | `hours_between(createdAt, firstResponseAt)` with an explicit threshold. |
| Priority-aware first-response SLA | signed | `P1/P2/P3` thresholds must be explicit; no default threshold policy. |
| Resolution SLA, natural hours | signed | `hours_between(createdAt, resolvedAt)` with an explicit threshold. |
| Dual first-response and resolution SLA | signed | Both signed natural-hour predicates in one DSL_CTE plan. |
| Combined SLA | signed | First-response hit and resolution hit combined only when both predicates are signed. |
| Unresponded overdue count | signed | `firstResponseAt is null` plus explicit cutoff or reference-time threshold. |
| SLA miss count alias | signed alias only | `ticketCount - slaHitCount` may mean SLA miss count, not unresponded-overdue count. |

## Clarify Or Reject Boundaries

| User Intent Or Variant | Required Behavior | Missing Or Unsupported Contract |
|---|---|---|
| SLA request without threshold | Clarify | `target_response_threshold` or resolution threshold. |
| Threshold number without unit | Clarify | Threshold unit. |
| Priority-specific SLA without P1/P2/P3 policy | Clarify | `priority_sla_policy`. |
| Business hours, workday, holiday, 9:00-18:00 SLA | Clarify | Business calendar, working-hour window, holiday policy, timezone, cross-day semantics. |
| Pause, hold, or customer-wait exclusion | Clarify | Pause/hold interval fields, customer-wait policy, overlap handling. |
| Backlog by overdue, held, pending customer reply | Clarify | Backlog status policy, overdue definition, customer-wait policy, ratio denominator. |
| Direct physical `service_ticket` SQL | Reject | Physical SQL is outside semantic-query routing. |
| Prediction, causality, staffing advice | Reject | Product/analysis layer issue, not a signed query recipe. |
| First-response SLA calculated from `resolvedAt` | Clarify or fail validation | Field-family mismatch. |
| Unresponded count as `ticketCount - slaHitCount` | Clarify or fail validation | This includes responded-late tickets and is only valid as SLA miss count. |

## Backlog Policy

Backlog is intentionally not promoted to an executable query recipe yet. The current model only exposes `status=OPEN/RESOLVED`; it does not expose held, pending customer reply, pause interval, or customer-wait fields.

Therefore:

1. Simple open ticket counts may use `status=OPEN` when the user explicitly asks for open/unresolved ticket counts.
2. Backlog severity, overdue backlog, held backlog, pending-customer-reply backlog, and backlog ratio must clarify policy slots first.
3. Do not map held or pending-customer-reply wording onto `status=OPEN` unless the domain model gains those states or policy fields.

## Fixture Coverage

| Fixture Or Gate | Contract |
|---|---|
| `CLARIFY-SERVICE-TICKET-SLA-001` | SLA wording with missing business calendar, priority policy, hold-time policy, and target response threshold routes to clarify. |
| `CLARIFY-SERVICE-TICKET-BACKLOG-001` | Backlog wording with overdue, held, pending-customer-reply, and ratio semantics routes to clarify. |
| `QueryExpertServiceRoutingCalibrationTest` | Negative ServiceTicket SLA variants fail closed before ChatClient and MCP tool dispatch. |
| `DslCteSlaFixtureIntegrationTest` | Signed natural-hour SLA recipe shapes compile and execute. |
| `DslCteAcceptanceSampleTest` | Signed aliases and unsigned formula guards are covered. |
| `score_biz024_semantic_gate.py` | Positive replay scorer rejects ambiguous unresponded-count explanations. |
| `score_service_ticket_negative_gate.py` | Negative runtime scorer rejects rows that execute unsupported ServiceTicket requests. |

## Related Documents

- `docs/9.1.0/detailed_design/15_service_ticket_sla_dsl_cte_contract_visibility.md`
- `docs/9.1.0/detailed_design/16_service_ticket_sla_recipe_contract_index.md`
- `docs/9.1.0/workitems/P2-service-ticket-lite-semantic-fixture-20260601.md`
- `docs/9.1.0/quality/service-ticket-sla-semantic-fixture-implementation-quality.md`
- `docs/9.1.0/acceptance/service-ticket-sla-semantic-fixture-acceptance.md`

## Open Follow-Ups

1. Add explicit backlog model fields before promoting held, pending-customer-reply, pause, or customer-wait metrics.
2. Add calendar fixtures before signing business-hours or contract-calendar SLA.
3. Consider a generic semantic-family metadata proposal only after commerce order and service-ticket field-family maps stabilize.
