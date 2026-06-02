---
doc_role: detailed_design
doc_purpose: Provide a single ServiceTicket SLA recipe contract index for signed and unsigned DSL_CTE boundaries.
version: 9.1.0
target: ServiceTicket SLA recipe contract index
status: active-contract
created_at: 2026-06-02
updated_at: 2026-06-02
---

# ServiceTicket SLA Recipe Contract Index

## Purpose

This index is the first lookup point for ServiceTicket SLA recipe status. It keeps signed recipes, unsigned variants, planner schema text, mapper validation, fixture tests, and replay gates aligned.

Do not add a new ServiceTicket SLA formula only in prompt/schema text. A capability is signed only when the contract row below has model fields, DSL_CTE bridge behavior, fixture evidence, and negative boundaries.

## Contract Matrix

| Recipe id | Status | Formula / trigger | Engine behavior | Evidence |
|---|---|---|---|---|
| `service_ticket.first_response_sla.natural_hours` | signed | `hours_between(createdAt, firstResponseAt)` + explicit threshold | Compile DSL_CTE to DSL, aggregate hit count, return NULL-safe rate. | `DslCteAcceptanceSampleTest`, `DslCteSlaFixtureIntegrationTest`, `biz-024` gate |
| `service_ticket.first_response_sla.priority_natural_hours` | signed | `priority_threshold(priority, P1=..., P2=..., P3=...)` with natural first-response duration | Compile priority-aware threshold policy only when P1/P2/P3 hours are explicit. | `DslCteSlaFixtureIntegrationTest#priorityAwareSlaRatePostSliceBridgeSqlMatchesManualBaseline`, `holdout-007` |
| `service_ticket.resolution_sla.natural_hours` | signed | `hours_between(createdAt, resolvedAt)` + explicit threshold policy | Compile natural-hour resolution SLA only; no contract-calendar semantics. | `DslCteSlaFixtureIntegrationTest#priorityAwareResolutionSlaRatePostSliceBridgeSqlMatchesManualBaseline` |
| `service_ticket.dual_sla.natural_hours` | signed | First-response and resolution natural-hour rates in the same DSL_CTE plan | Compile both signed elapsed-hour metrics and NULL-safe rates. | `DslCteSlaFixtureIntegrationTest#priorityAwareDualSlaRateBridgeSqlMatchesManualBaseline` |
| `service_ticket.combined_sla.natural_hours` | signed | First-response hit and resolution hit combined into one SLA rate | Compile only when both hit predicates are signed natural-hour predicates. | `DslCteSlaFixtureIntegrationTest#priorityAwareCombinedSlaRateBridgeSqlMatchesManualBaseline` |
| `service_ticket.unresponded_overdue_count` | signed | `firstResponseAt is null` with cutoff/reference-time threshold | Compile explicit unresponded-overdue count; reject `ticketCount - slaHitCount` as a substitute. | `DslCteAcceptanceSampleTest`, `score_biz024_semantic_gate.py` |
| `service_ticket.sla_miss_count` | signed alias only | `ticketCount - slaHitCount` as `notHitCount` / `slaMissCount` | Allow only as SLA miss count, not unresponded-overdue count. | `DslCteAcceptanceSampleTest` |
| `service_ticket.business_hours_duration` | compile-deferred | `business_hours_between(...)` / `working_hours_between(...)` | Validation fails with unsigned business-hours reason. | `DslCteAcceptanceSampleTest#validationDefersUnsignedBusinessHoursSlaDuration` |
| `service_ticket.contract_calendar_duration` | compile-deferred | `contract_calendar_hours_between(...)` / `service_calendar_hours_between(...)` / `calendar_hours_between(...)` | Validation fails with unsigned contract-calendar reason. | `DslCteAcceptanceSampleTest#validationDefersUnsignedContractCalendarSlaDuration` |
| `service_ticket.pause_hold_net_duration` | compile-deferred | `net_hours_between(...)` / `pause_excluded_hours_between(...)` / `hold_excluded_hours_between(...)` / `customer_wait_excluded_hours_between(...)` | Validation fails with unsigned pause/hold exclusion reason. | `DslCteAcceptanceSampleTest#validationDefersUnsignedPauseHoldExclusionSlaDuration` |
| `service_ticket.missing_threshold` | runtime-clarify | SLA request without threshold | Clarify before LLM/tool dispatch. | `QueryExpertServiceRoutingCalibrationTest`, negative gate |
| `service_ticket.missing_priority_policy` | runtime-clarify | Priority-specific SLA without P1/P2/P3 hour policy | Clarify before LLM/tool dispatch. | `QueryExpertServiceRoutingCalibrationTest`, `er0r-015` |
| `service_ticket.business_hours_request` | runtime-clarify | NL request asks for workday, holiday, business-hour, or 9:00-18:00 elapsed time | Clarify before LLM/tool dispatch. | `QueryExpertServiceRoutingCalibrationTest`, `er0r-017` |
| `service_ticket.pause_hold_request` | runtime-clarify | NL request asks to subtract pause, hold, or customer-wait time | Clarify before LLM/tool dispatch. | `QueryExpertServiceRoutingCalibrationTest`, `er0r-016` |
| `service_ticket.physical_sql_or_advice` | runtime-reject | Direct physical SQL, prediction, causality, or personnel advice | Reject before LLM/tool dispatch. | `QueryExpertServiceRoutingCalibrationTest`, negative gate |

## Required Touchpoints For New Recipes

| Touchpoint | Required update |
|---|---|
| TM/QM fixture | Add fields and dimensions required by the business policy. |
| DSL_CTE mapper | Add a signed bridge branch or an explicit compile-deferred branch. |
| Java fixture test | Add direct DSL_CTE integration coverage against seeded fixture rows. |
| Acceptance test | Add validation coverage for unsupported close neighbors. |
| MCP schema descriptions | Update `query_model_v3.md`, `query_model_v3_basic.md`, and `query_model_v3_no_vector.md` together. |
| Description consistency test | Add snippets to `QueryModelDescriptionConsistencyTest` when planner-visible schema text changes. |
| Runtime preflight | Add clarify/reject boundaries for under-specified NL requests. |
| Replay scorer | Add positive and negative gate profiles before promotion. |
| Version docs | Update this index, `15_service_ticket_sla_dsl_cte_contract_visibility.md`, and the owning v3.9 workitem. |

## Current Extension Queue

| Candidate | Status | Blocking evidence |
|---|---|---|
| Business-hours SLA | unsigned | Needs calendar fixture, working-hour window, holiday policy, timezone, and cross-day elapsed-time semantics. |
| Pause/hold/customer-wait net SLA | unsigned | Needs pause interval fields, customer-wait policy, overlap handling, and net-duration semantics. |
| Contract/service-calendar SLA | unsigned | Needs customer contract calendar fixture, service window, holiday policy, timezone, and partial-window semantics. |

