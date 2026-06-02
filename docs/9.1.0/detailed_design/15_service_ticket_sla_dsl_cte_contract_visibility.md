---
doc_role: detailed_design
doc_purpose: Make the signed ServiceTicket first-response SLA DSL_CTE contract visible to planner and evaluator owners.
version: 9.1.0
target: ServiceTicket SLA DSL_CTE contract visibility
status: active-contract
created_at: 2026-06-02
updated_at: 2026-06-02
---

# ServiceTicket SLA DSL_CTE Contract Visibility

## Purpose

ServiceTicket SLA is now a signed engine recipe, but only for a narrow first-response SLA shape. This note records the executable shape and the fail-closed variants so planner, prompt, evaluator, and runtime owners use the same boundary.

For the full signed/unsigned recipe registry, use `16_service_ticket_sla_recipe_contract_index.md` as the source of truth.

## Signed Recipe

| Element | Contract |
|---|---|
| Model | `ServiceTicketQueryModel` |
| Time scope | One explicit created-time window, such as current month or last 30 days, not both. |
| Threshold | Explicit first-response SLA threshold with time unit, currently represented as hours. |
| Hit predicate | `firstResponseAt - createdAt <= threshold` through the signed elapsed-hours recipe. |
| Natural-hour resolution SLA | Signed only as `hours_between(createdAt, resolvedAt)` with explicit threshold policy; it does not imply contract-calendar or service-calendar semantics. |
| Denominator | Count of tickets created inside the time scope. |
| Achievement rate | NULL-safe ratio of SLA-hit count to denominator. |
| Unresponded overdue count | Explicit predicate: `firstResponseAt is null` and created time is earlier than the SLA cutoff/reference-time expression. |
| SLA miss count | Signed only as a miss-count alias; it is not the same as unresponded overdue count. |
| Priority-aware first-response threshold | Signed only when the threshold policy is explicit, for example `priority_threshold(priority, P1=4, P2=24, P3=48)` or equivalent P1/P2/P3 hour declarations. |

## Unsigned Variants

| Variant | Required behavior |
|---|---|
| Missing threshold | Clarify before LLM/tool dispatch. |
| Threshold number without unit | Clarify before LLM/tool dispatch. |
| Contract calendar, work calendar, holidays, business hours | Clarify as out of current recipe scope. |
| Physical `service_ticket` SQL | Reject before LLM/tool dispatch. |
| Prediction, causality, staffing advice | Reject before LLM/tool dispatch. |
| Unresponded count as `ticketCount - slaHitCount` | Clarify or fail validation; that expression also includes responded-late tickets. |
| First-response SLA calculated from `resolvedAt` / resolution duration | Clarify; this is a field mismatch. |
| Conflicting time scopes | Clarify; do not auto-select one window. |
| Priority-aware SLA without P1/P2/P3 threshold policy | Clarify before LLM/tool dispatch; do not assume a default priority policy. |
| Pause, hold, or customer-wait time exclusion | Clarify before LLM/tool dispatch; the current recipe has no pause interval fields or exclusion policy. |
| First-response SLA with business hours, workdays, holidays, or 9:00-18:00 windows | Clarify before LLM/tool dispatch; the current recipe only signs natural elapsed hours. |
| Direct DSL_CTE `business_hours_between(...)` / `working_hours_between(...)` payloads | Fail validation with an explicit unsigned business-hours duration reason; do not compile through the natural-hour bridge. |
| Direct DSL_CTE `contract_calendar_hours_between(...)` / `service_calendar_hours_between(...)` / `calendar_hours_between(...)` payloads | Fail validation with an explicit unsigned contract-calendar duration reason; do not compile through the natural-hour bridge. |
| Direct DSL_CTE `net_hours_between(...)` / `pause_excluded_hours_between(...)` / `hold_excluded_hours_between(...)` / `customer_wait_excluded_hours_between(...)` payloads | Fail validation with an explicit unsigned pause/hold exclusion duration reason; do not compile through the natural-hour bridge. |

## Runtime Evidence

| Evidence | Meaning |
|---|---|
| `DslCteAcceptanceSampleTest` and `DslCteSlaFixtureIntegrationTest` | Signed DSL_CTE recipe shapes compile and execute. |
| `DslCteAcceptanceSampleTest#validationDefersUnsignedBusinessHoursSlaDuration` | Direct DSL_CTE business-hours duration formulas defer before compilation. |
| `DslCteAcceptanceSampleTest#validationDefersUnsignedContractCalendarSlaDuration` | Direct DSL_CTE contract-calendar duration formulas defer before compilation. |
| `DslCteAcceptanceSampleTest#validationDefersUnsignedPauseHoldExclusionSlaDuration` | Direct DSL_CTE pause/hold/customer-wait net-duration formulas defer before compilation. |
| `QueryExpertServiceRoutingCalibrationTest` | Negative ServiceTicket SLA variants fail closed before ChatClient and MCP tool dispatch. |
| `score_biz024_semantic_gate.py` | Positive replay scorer rejects ambiguous unresponded-count explanations even when output fields exist. |
| `score_service_ticket_negative_gate.py` | Negative runtime scorer fails rows that call or succeed through `dataset.query_model`. |

## Extension Rule

New SLA capabilities should enter as small signed recipes with fixture rows and negative gates. Do not broaden this contract with a free-form formula language or a prompt-only instruction.
