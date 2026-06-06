---
doc_role: domain_review
doc_purpose: Review domain semantics currently involved by Foggy AI query fixtures, demo TM/QM models, and 9.1.0 documentation.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Domain Capability Review

## Scope

This review separates industry/domain semantics from engine capabilities.

Domain semantics include business words such as `orderStatus`, `paymentStatus`, SLA, receivable aging, sales funnel, refund rate, and Odoo document states. They are useful model contracts and fixtures, but they must not become hard-coded engine rules.

Engine capabilities are the neutral primitives that make those domains reliable: field semantic families, enum ownership, governed metric recipes, ambiguity clarification, trace-visible tool payload checks, and fail-closed validation.

## Evidence Sources

| Source | Evidence |
|---|---|
| `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce` | Ecommerce sales, order, payment, return, inventory, promotion, customer, channel, store, team, CRM lead, and service ticket TM/QM templates. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/odoo` | Odoo/ERP accounting, payment, CRM, product, purchase, stock, MRP, project, HR, partner, company TM/QM templates. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/mcp_audit` | MCP tool-call audit log model with tool, role, result, and error taxonomy. |
| `foggy-dataset-demo/src/main/resources/foggy/templates/vector_search` and `vector_demo` | Product/document/template vector search models. |
| `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json` | Promoted ecommerce query and route-intent cases, especially order lifecycle vs payment settlement vs shipping intent. |
| `foggy-dataset-mcp/src/test/resources/ai-test-cases/clarify-routing-tests.json` | Domain-risk clarification fixtures for service ticket, sales funnel, receivables, budget, inventory, refund, subscription, marketing, manufacturing, governance, retention, and sales target. |
| `docs/9.1.0/workitems` and `docs/9.1.0/detailed_design` | Workitems and contract indexes for AI routing, ServiceTicket SLA, query recipes, and route-intent evidence. |

## Domain Inventory

| Domain | Current State | Existing Model Surface | Existing Test/Doc Surface | Maturity |
|---|---|---|---|---|
| Commerce sales analytics | Active demo model | `FactSalesQueryModel`, product/customer/store/channel/promotion/date dimensions | `QUERY-*`, `FILTER-*`, `AGG-*`, `DIM-*`, `SORT-*`, `COMPLEX-*` | mature sample |
| Commerce order lifecycle | Active demo model and focused AI fixtures | `FactOrderQueryModel`, `orderStatus`, `paymentStatus`, `shipDate`, `salesTeam$status` | `ROUTE-ORDER-001..011`, order semantic workitems | promoted fixture family |
| Commerce payment/refund/return | Active model, partial AI fixtures | `FactPaymentQueryModel`, `FactReturnQueryModel`, `payStatus`, `returnStatus`, `returnType`, refund metrics | payment/refunded route fixtures; clarify refund-rate template | partial |
| Inventory analytics | Active model plus clarify template | `FactInventorySnapshotQueryModel`, product/store snapshot metrics | `CLARIFY-INVENTORY-TURNOVER-001` | candidate |
| Service ticket / customer support | Active model and signed recipe notes | `ServiceTicketQueryModel`, ticket status, team status, first response fields | ServiceTicket SLA docs, clarify SLA/backlog fixtures | promoted narrow recipe |
| Sales funnel / CRM | Active ecommerce CRM lead and Odoo CRM models | `CrmLead`, `OdooCrmLeadQueryModel`, stage/type/probability/close dates | `CLARIFY-SALES-FUNNEL-001` | candidate |
| Odoo accounting / receivables | Active Odoo models | `OdooAccountMoveQueryModel`, `OdooAccountMoveLineQueryModel`, payment state, due date, residual amount | receivable-aging clarify fixture; Odoo engine recipe evidence | model-ready, fixture-light |
| Odoo payment | Active Odoo model | `OdooAccountPaymentQueryModel`, payment type, partner type, reconciliation flags | Odoo fixture evidence in query recipe contract | model-ready |
| Odoo procurement / inventory / manufacturing | Active Odoo models | purchase order state/invoice status, stock picking state, MRP state, product type | inventory/manufacturing clarify fixtures | model-ready, fixture-light |
| Odoo project / HR / partner master data | Active Odoo models | task stage/state/kanban state, employee type, partner active/rank/type | no focused AI fixture yet | candidate |
| Finance budget | Clarify-only | no complete finance budget TM/QM in scanned templates | `CLARIFY-BUDGET-VARIANCE-001` | clarify-only |
| Subscription revenue | Clarify-only | no subscription TM/QM in scanned templates | `CLARIFY-SUBSCRIPTION-RENEWAL-001` | clarify-only |
| Marketing attribution | Clarify-only | no marketing attribution TM/QM in scanned templates | `CLARIFY-MARKETING-ATTRIBUTION-001` | clarify-only |
| Customer retention/cohort | Clarify-only with customer model support | customer/order lifecycle models can support part of it | `CLARIFY-COHORT-RETENTION-001` | candidate |
| Sales target | Clarify-only | no target/version model in scanned templates | `CLARIFY-SALES-TARGET-001` | clarify-only |
| Governance / sensitive export | Clarify-only, engine-adjacent | audit model exists but export policy is not a business model | governance clarify fixtures | engine-policy candidate |
| Cross-domain risk | Clarify-only | requires governed joins across commerce/support/finance | `CLARIFY-CROSS-DOMAIN-RISK-001` | future composite |
| MCP audit analytics | Active model | `McpAuditLogQueryModel` | no focused AI fixture yet | observability candidate |
| Vector search | Active model | `DocumentSearchQueryModel`, `ProductSearchQueryModel`, `QueryTemplateVectorQueryModel` | no focused AI fixture yet | search candidate |

## Status-Like Semantic Families Observed

| Semantic Family | Domain Examples | Engine-Level Treatment |
|---|---|---|
| Lifecycle state | `orderStatus`, Odoo sale/purchase/account/stock/MRP `state` | Generic `lifecycle_state`; enum values belong to the field dictionary. |
| Settlement/payment state | `paymentStatus`, `payment_state`, `payStatus`, reconciliation flags | Generic `settlement_state`; must not be conflated with document lifecycle. |
| Fulfillment/logistics state | shipped/unshipped intent, stock picking state, shipping dates | Generic `fulfillment_state`; date presence is not equivalent to state unless the domain model says so. |
| Activation state | product/customer/store/channel/team/promotion `status`, Odoo `active` | Generic `activation_state`; not a workflow state. |
| Workflow stage | CRM lead stage, project task stage, kanban state | Generic `workflow_stage`; stage and state can coexist. |
| Classification type | customer/store/channel/promotion/product/payment/partner types | Generic `classification_type`; usually dimension filtering, not lifecycle. |
| Priority/risk | ticket priority, CRM priority, MRP priority, risk rating | Generic `priority_or_risk`; requires domain-specific ranking semantics. |

## Review Findings

1. The strongest promoted business-domain evidence is still commerce order routing and ServiceTicket SLA. These are useful fixtures, not engine contracts.
2. Odoo/ERP has the broadest model surface, but its AI intent fixtures are thinner than ecommerce. It is a good next domain for systematic field-family and enum ownership coverage.
3. Clarify routing already references many domains whose models are not yet present. Those should remain clarify templates until TM/QM and fixture evidence exist.
4. `orderStatus = PAID` is a data/model anomaly, not a valid reason to teach the engine that `PAID` belongs to lifecycle state.
5. Current docs scatter domain knowledge across workitems. This makes future prompt/model iteration easy to overfit to a single business word.

## Boundary Decision

Business terms such as `orderStatus`, `paymentStatus`, `invoice_status`, `kanban_state`, `returnStatus`, and SLA policy names belong in domain model contracts and domain Skills.

Engine-level work should only add neutral primitives:

- field semantic family metadata;
- enum/dictionary ownership validation;
- query-payload semantic diff;
- route-intent fixture evaluation;
- clarification templates for underspecified domain metrics;
- fail-closed gates for unsupported formulas, joins, or exported detail scope.
