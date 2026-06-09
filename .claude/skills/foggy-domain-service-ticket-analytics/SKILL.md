---
name: foggy-domain-service-ticket-analytics
description: 服务工单领域语义建模与 AI 查询题库迭代。用于处理 ServiceTicket TM/QM、SLA、首响、积压、超期、挂起、待客户回复、客服团队等语义；区分工单流程状态、团队启用状态、SLA 策略和澄清边界。
---

# Foggy Service Ticket Analytics Domain

Use this skill when working on service-ticket models, SLA recipes, backlog clarification, or AI fixtures.

## Files To Read First

- `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model/ServiceTicketModel.tm`
- `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query/ServiceTicketQueryModel.qm`
- `docs/9.1.0/domain_models/service-ticket-domain-model.md`
- `docs/9.1.0/detailed_design/15_service_ticket_sla_dsl_cte_contract_visibility.md`
- `docs/9.1.0/detailed_design/16_service_ticket_sla_recipe_contract_index.md`
- `docs/9.1.0/workitems/P2-service-ticket-lite-semantic-fixture-20260601.md`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/clarify-routing-tests.json`

## Semantic Boundaries

| Family | Fields/Concepts | Rule |
|---|---|---|
| Ticket workflow state | ticket `status` | Open, resolved, closed, pending, waiting, and backlog states must follow the model dictionary. |
| Team activation | `team$status` | Team availability is not ticket workflow. |
| SLA policy | first response, target threshold, business calendar, hold time | Execute only for signed recipe shapes; clarify missing policy slots. |
| Backlog policy | overdue, pending customer, held, aging bucket | Clarify unless the status policy, denominator, and date basis are explicit. |
| Priority | ticket priority and priority-specific SLA | Do not infer priority thresholds without a policy. |

## Workflow

1. Decide whether the request matches a signed SLA recipe, a simple ticket query, or an underspecified policy metric.
2. For signed SLA recipe work, verify the contract index and negative boundaries before adding prompt/schema text.
3. For backlog work, read the domain map first; held or pending-customer-reply backlog is clarify-only until model fields or policy slots exist.
4. For backlog or SLA-rate questions, clarify missing slots such as business calendar, hold-time handling, target threshold, and denominator.
5. Add fixtures only when the expected query payload and policy assumptions are fully explicit.
6. Use `foggy-model-generate` for model mechanics if fields need to be exposed or grouped.

## Do Not

- Do not convert every service-ticket question into a free-form formula.
- Do not make SLA behavior broader than the signed contract without negative gates.
- Do not treat team status as ticket status.
