---
doc_role: skill_blueprint
doc_purpose: Record the first domain Skill drafts and the model boundaries they enforce.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Initial Domain Skill Blueprint

## First Batch

| Skill | Domain | Why First |
|---|---|---|
| `foggy-domain-commerce-analytics` | Ecommerce sales/order/payment/refund/inventory | Most current AI query fixtures and the clearest field-family conflict examples. |
| `foggy-domain-service-ticket-analytics` | Customer support tickets, SLA, backlog | Existing signed narrow SLA recipe and clarify templates. |
| `foggy-domain-odoo-erp-analytics` | Odoo accounting, payment, CRM, purchase, stock, MRP, project, product, partner | Broad model surface and repeated state/payment/date ambiguity. |

## Common Skill Contract

Each domain Skill must guide the agent to:

1. Read the domain TM/QM files before making field assumptions.
2. Identify the domain semantic family for status/stage/type/date fields.
3. Choose execution only when required fields, metric definitions, and enum ownership are clear.
4. Choose clarification when policy slots or denominator definitions are missing.
5. Add AI fixtures for behavior that should become stable.
6. Avoid adding engine-level code keyed to business field names.

## Model Boundary Pattern

| Boundary | Example | Correct Handling |
|---|---|---|
| Lifecycle vs settlement | `orderStatus` vs `paymentStatus` | Use both when the user asks for completed and paid; use only the matching field otherwise. |
| Lifecycle vs date presence | shipped state vs `shipDate` | Treat date presence as an attribute unless model contract says it defines state. |
| Document state vs invoice/payment state | purchase `state` vs `invoice_status`, invoice `state` vs `payment_state` | Keep document lifecycle and settlement/billing status separate. |
| Workflow stage vs workflow state | CRM/project `stage`, task `state`, `kanban_state` | Do not collapse stage, state, and kanban display state. |
| Activation state vs business process state | product/store/team `status` or Odoo `active` | Treat as master-data availability, not process lifecycle. |

## First Domain Map

`commerce-order-domain-model.md` records the first concrete field-family map. It covers order lifecycle, payment settlement, fulfillment date semantics, enum ownership, and the ecommerce route fixtures that enforce those boundaries.

`service-ticket-domain-model.md` records the second P0 field-family map. It keeps signed natural-hour SLA recipes separate from backlog policy, business-calendar SLA, hold/customer-wait exclusions, and team activation status.

`odoo-erp-domain-model.md` records the first P1 ERP field-family map. It focuses on `state`, `payment_state`, `invoice_status`, `type`, `active`, `kanban_state`, and business date roles before adding Odoo route fixtures.

## Candidate Follow-Up Skills

| Candidate | Trigger Condition |
|---|---|
| `foggy-domain-finance-receivables` | Add focused AR/AP aging TM/QM and fixtures beyond clarify routing. |
| `foggy-domain-commerce-refund` | Promote refund-rate and return lifecycle fixtures beyond current order/payment coverage. |
| `foggy-domain-sales-funnel` | Add governed lead-opportunity-order relation and conversion denominator rules. |
| `foggy-domain-inventory-manufacturing` | Sign inventory turnover and manufacturing yield formulas. |
| `foggy-domain-governance-export` | Productize sensitive export, masking, row-limit, and audit-domain policy. |
