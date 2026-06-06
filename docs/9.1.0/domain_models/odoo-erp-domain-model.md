---
doc_role: domain_model
doc_purpose: Record Odoo/ERP state, payment, invoicing, stage, activation, type, and date-role semantic boundaries.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Odoo ERP Domain Model

## Scope

This document records the current Odoo/ERP semantic-family map for the generated Odoo TM/QM package.

It is a domain model contract, not an engine rule. Odoo field names such as `state`, `payment_state`, `invoice_status`, `kanban_state`, `active`, and `move_type` must stay in domain documents, TM/QM descriptions, dictionaries, and AI fixtures.

## Core Field Families

| Family | Odoo Fields | Domain Meaning | Must Not Be Confused With |
|---|---|---|---|
| Document lifecycle | Sale/purchase/account/stock/MRP `state`, account line `parent_state`, project task `state` | Workflow state of a business document or task. Enum ownership is per model/dictionary. | Payment settlement, billing status, activation, geographic state/province. |
| Payment settlement | `account.move.payment_state`, account line `move$paymentState`, payment reconciliation flags | Paid, unpaid, partial, in-payment, reversed, reconciled/open settlement. | Account move posting state or sale/purchase lifecycle. |
| Billing/invoicing | Sale/purchase `invoice_status`, product `invoice_policy` | Whether a sale/purchase document is invoiced/to invoice or how product invoicing is triggered. | Payment settlement and document lifecycle. |
| Classification type | `move_type`, `payment_type`, `partner_type`, CRM `type`, product `detailed_type`, display type | Object category or direction. | Lifecycle state. |
| Workflow stage / board display | CRM stage fields when present, project task `date_last_stage_update`, `kanban_state` | Pipeline/board display and attention state. | Task lifecycle state or closed/won semantics. |
| Activation | `active`, product sale/purchase flags, company/partner/employee active flags | Master-data availability. | Transaction status or document lifecycle. |
| Business date role | `date_order`, `invoice_date`, `scheduled_date`, `date_finished`, `date_deadline` and similar date fields | Time-window, trend, aging, deadline, or completion role based on `timeRole` and `recommendedUse`. | Column-name similarity or generic created/updated timestamps. |

## Accounting And Payment Contracts

| Intent | Required Field Family | Domain Contract |
|---|---|---|
| Customer invoice vs vendor bill | Classification type | Use `move_type=out_invoice` for customer invoices and `move_type=in_invoice` for vendor bills. |
| Customer/vendor credit notes | Classification type + settlement | Use `move_type=out_refund` for customer credit notes and `move_type=in_refund` for vendor credit notes. Use `payment_state=reversed` when the refund/reversal settlement matters; do not use `state=reversed`, and do not include refunds in ordinary invoice/bill analysis unless requested. |
| Posted accounting entries | Document lifecycle | Use `state=posted` on `account.move` or `parent_state=posted` on account move lines. |
| Paid/unpaid/partial/in-payment invoice state | Payment settlement | Use `payment_state`; do not infer settlement from `state=posted`. |
| Open AR/AP outstanding | Payment settlement + lifecycle + residual amount | Use posted entries plus `payment_state in [not_paid, partial, in_payment]` and residual amount. |
| Open vendor bills | Classification type + lifecycle + settlement | Use `move_type=in_invoice`, `state=posted`, and `payment_state in [not_paid, partial, in_payment]`; do not include draft bills or customer invoices. |
| Paid vendor bills | Classification type + lifecycle + settlement | Use `move_type=in_invoice`, `state=posted`, and `payment_state=paid`; do not use customer invoice direction or `state=paid`. |
| AR/AP aging and overdue | Due-date role + open settlement | Use `invoice_date_due` or `date_maturity` plus open settlement for aging, not `invoice_date` or posting `date`. AP overdue metrics should use `apOverdueAmount` and `apOverdueVendorCount` when available. |
| Invoice/revenue trend | Business date role | Use `invoice_date` / `invoiceDate$yearMonth` for customer invoice amount and invoice-count period analysis, not due date, posting date, or creation date. |
| Payment received vs payment sent | Classification type | Use `payment_type=inbound/outbound`; pair with `partner_type=customer/supplier` when the counterparty role matters. Customer receipts are `inbound + customer`; vendor payments are `outbound + supplier`. |
| Reconciled payment or line | Settlement/reconciliation | Use `reconciled` or payment matching semantics, not document posting state alone. |
| Payment matched to vendor bill | Explicit match relation | Use `OdooAccountPaymentBillMatchQueryModel` / `account_payment_bill_match` when the user asks which payment matched which bill; do not treat `is_reconciled=true` alone as proof of a specific bill match. Keep partial-bill and split-payment allocations as match facts, not as inferred payment state. |
| Purchase order to receipt to vendor bill | Explicit cross-document flow | Use `OdooPurchaseDocumentFlowQueryModel` / `purchase_document_flow` when the user asks which receipt or vendor bill belongs to a purchase order. Do not rely only on string matching `origin` / `invoice_origin` for governed cross-document analysis. |
| Sales order to delivery to customer invoice to payment | Explicit cross-document flow | Use `OdooSaleDocumentFlowQueryModel` / `sale_document_flow` when the user asks which delivery, customer invoice, or customer receipt belongs to a sales order. Do not rely only on string matching `origin` / `invoice_origin` for governed cross-document analysis. |

## Sales, Purchase, Inventory, Manufacturing

| Subdomain | Lifecycle Field | Billing/Settlement Field | Primary Date Role | Boundary |
|---|---|---|---|---|
| Sale order | `state` with `sale_order_state` | `invoice_status` with `sale_invoice_status` | `date_order` as business date | Sales lifecycle is not invoice completion. |
| Sale document flow | Sale `state`, delivery `state`, invoice `state` | Sale `invoice_status` plus invoice `payment_state` and customer payment direction | Sale `date_order`, delivery `date_done`, invoice `invoice_date_due` by question role | Cross-document flow analysis must keep sales lifecycle, delivery completion, customer invoice direction, invoice settlement, and customer receipt direction separate. |
| Purchase order | `state` with `purchase_order_state` | `invoice_status` with `purchase_invoice_status` | `date_order` as business date; `date_planned` for expected arrival | Procurement lifecycle is not billing status or planned receipt date. |
| Purchase document flow | Purchase `state`, receipt `state`, bill `state` | Purchase `invoice_status` plus bill `payment_state` | Purchase `date_order`, receipt `date_done`, bill `invoice_date_due` by question role | Cross-document flow analysis must keep purchase lifecycle, receipt completion, bill direction, and bill settlement separate. |
| Stock picking | `state` with `stock_picking_state` | none in current model | `scheduled_date` as business date with `scheduledDate$year/month/yearMonth`; `date_done` as completion date | Done/ready/waiting state is not date presence. |
| Manufacturing order | `state` with `mrp_production_state` | none in current model | `date_finished` as completion business date; `date_start` as planned start; `date_deadline` for lateness | Manufacturing throughput should use completion date, not create/write timestamps. |

## CRM And Project Contracts

| Subdomain | Field Family | Domain Contract |
|---|---|---|
| CRM lead/opportunity | `type`, `active`, date roles | `type` distinguishes lead/opportunity; `active` is availability/archival state; expected close, assigned, closed, conversion, and stage-update dates have different roles. |
| Project task | `state`, `kanban_state`, `priority`, `active` | Task lifecycle state is separate from board display/blocked state and activation. |
| Sales funnel | Cross-model policy | Funnel stage definitions, conversion denominator, dedup grain, and drop-off attribution remain clarify-first until signed. |

## Date Role Rules

Use `timeRole` and `recommendedUse` before choosing a date field:

| Date Role | Example Fields | Correct Use |
|---|---|---|
| `business_date` | sale `dateOrder`, purchase `dateOrder`, account `invoiceDate`, stock `scheduledDate`, MRP `dateFinished`, CRM `dateDeadline` | Primary trend or timeWindow for that model's business analysis. |
| `posting_date` | account move `date`, account move line `date` | GL posting-period analysis. |
| `due_date` | account move `invoiceDateDue`, move line `dateMaturity` | AR/AP aging, overdue receivable/payable analysis. |
| `completion_date` | stock `dateDone`, MRP `dateFinished` | Actual completion throughput. |
| `deadline_date` | stock/MRP `dateDeadline`, project task `dateDeadline` | Deadline and lateness analysis. |
| `approval_date` / assigned / closed / conversion | purchase `dateApprove`, CRM `dateOpen`, `dateClosed`, `dateConversion` | Cycle-time, conversion, closure, and workflow transition analysis. |

Do not use `create_date` or `write_date` as the business date unless the user explicitly asks about record creation or update activity.

## Fixture Direction

Three focused Odoo fixture packs now exist as optional AI route fixture files:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-accounting-tests.json`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-sales-purchase-tests.json`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-stock-mrp-tests.json`

Enable them only when the matching Odoo dataset/model bundle is available. Example:

```bash
AI_TEST_CASE_FILES=ai-test-cases/ecommerce-tests.json,ai-test-cases/odoo-sales-purchase-tests.json
```

Keep Odoo fixture packs focused, not broad:

1. Accounting/payment: invoice vs bill, posted vs paid, open AR/AP, overdue aging, vendor payment to bill matching, partial-bill matching, split-payment allocation, credit-note direction, and reversed settlement.
2. Sales/purchase: lifecycle `state` vs `invoice_status`, explicit sales-order to delivery to customer-invoice to customer-payment flows, and explicit purchase-order to receipt to vendor-bill flows.
3. Stock picking: lifecycle `state` vs scheduled/done date roles.
4. MRP: lifecycle `state` vs start/finished/deadline date roles.
5. CRM/project clarify-only first: funnel and stage definitions require policy slots before execution.

## Local Evidence

2026-06-05 local MySQL evidence:

- `scripts/ensure-ai-test-mysql.sh --no-start` confirmed `127.0.0.1:13306/foggy_test` is reachable.
- `sale_order`, `purchase_order`, `stock_picking`, `purchase_document_flow`, `account_move`, `account_move_line`, `account_payment`, `account_payment_bill_match`, `mrp_bom`, and `mrp_production` exist locally.
- Accounting fixture precheck: local demo data now contains 2 overdue AR lines, overdue AR amount `8768.70`, 2 overdue AR customers, 2 posted open vendor bills, overdue AP amount `14215.00`, 2 overdue AP vendors, 1 unreconciled inbound customer payment, 4 vendor payment-to-bill match facts including 1 partial-bill match and 1 split payment across 2 bills, 1 posted customer credit note, and 1 posted vendor credit note.
- MRP fixture precheck: local demo data contains 6 manufacturing orders across `cancel`, `confirmed`, `done`, `progress`, and `to_close`; completed manufacturing orders aggregate to `2025-01` qty `10.0000` and `2025-03` qty `3.0000`.
- Direct MCP baseline passed for `odoo-sales-purchase-tests.json`: `ODOO-SALE-001`, `ODOO-SALE-002`, `ODOO-PURCHASE-001`, `ODOO-PURCHASE-002`, `ODOO-PURCHASE-XDOC-001`, `ODOO-PURCHASE-XDOC-002`, `ODOO-SALE-XDOC-001`, and `ODOO-SALE-XDOC-002` all passed with `OdooSaleOrderQueryModel` / `OdooPurchaseOrderQueryModel` / `OdooPurchaseDocumentFlowQueryModel` / `OdooSaleDocumentFlowQueryModel` in the configured model list.
- Evidence command:
  `scripts/run-ai-domain-direct.sh --case-files ai-test-cases/odoo-sales-purchase-tests.json --models OdooSaleOrderQueryModel,OdooPurchaseOrderQueryModel,OdooPurchaseDocumentFlowQueryModel,OdooSaleDocumentFlowQueryModel`
- Direct MCP baseline command for optional domain packs is now available via `scripts/run-ai-domain-direct.sh`.
- Direct MCP baseline passed for `odoo-stock-mrp-tests.json`: `ODOO-STOCK-001` through `ODOO-STOCK-003` and `ODOO-MRP-001` through `ODOO-MRP-005` all passed with `OdooStockPickingQueryModel` and `OdooMrpProductionQueryModel` in the configured model list.
- Stock/MRP evidence command:
  `scripts/run-ai-domain-direct.sh --case-files ai-test-cases/odoo-stock-mrp-tests.json --models OdooStockPickingQueryModel,OdooMrpProductionQueryModel`
- Stock fixture evidence exposed and fixed a domain-model gap: `scheduledDate$yearMonth` now comes from the Stock Picking self time dimension and generates MySQL `DATE_FORMAT(stock_picking.scheduled_date, '%Y-%m')`.
- MRP fixture evidence exposed and fixed two domain-model gaps: `dateFinished$yearMonth` now comes from the Manufacturing Order self time dimension and generates MySQL `DATE_FORMAT(mrp_production.date_finished, '%Y-%m')`; source/destination location captions now use `stock_location.complete_name` in the demo schema.
- Direct MCP baseline passed for `odoo-accounting-tests.json`: `ODOO-ACC-001` through `ODOO-ACC-016` all passed with `OdooAccountMoveQueryModel`, `OdooAccountMoveLineQueryModel`, `OdooAccountPaymentQueryModel`, and `OdooAccountPaymentBillMatchQueryModel` in the configured model list.
- Accounting evidence command:
  `scripts/run-ai-domain-direct.sh --case-files ai-test-cases/odoo-accounting-tests.json --models OdooAccountMoveQueryModel,OdooAccountMoveLineQueryModel,OdooAccountPaymentQueryModel,OdooAccountPaymentBillMatchQueryModel`
- Accounting fixture evidence exposed and fixed four execution-contract gaps: `arOverdueCustomerCount` now uses supported `COUNT_DISTINCT(if(..., null))` formula syntax, AP overdue uses first-class `apOverdueAmount` / `apOverdueVendorCount` measures, the direct overdue detail fixture uses fixed date literal `2026-06-05` instead of passing `now()` as a slice value, and `invoiceDate$yearMonth` now comes from the Account Move self time dimension and generates MySQL `DATE_FORMAT(account_move.invoice_date, '%Y-%m')`.
- Negative direct fixtures now cover paid-vs-posted settlement (`paymentState=paid` plus `state=posted`), AP vendor-bill direction (`moveType=in_invoice`), customer credit-note direction (`moveType=out_refund`), vendor credit-note direction (`moveType=in_refund`), reversed settlement (`paymentState=reversed` plus `state=posted`), open vendor bill settlement (`paymentState in [not_paid, partial, in_payment]`), vendor payment direction (`paymentType=outbound` plus `partnerType=supplier`), explicit payment-to-bill matching, partial-bill matching with residual amount, split vendor-payment allocation, sales-order to delivery to customer-invoice to customer-payment flow routing, delivered-but-not-fully-collected flow semantics, purchase-order to receipt to vendor-bill flow routing, received-but-open payable flow semantics, overdue AP metrics, overdue due-date role, invoice trend by `invoiceDate$yearMonth`, MRP lifecycle-vs-completion-date presence, and MRP completion throughput by `dateFinished$yearMonth`.

## Negative Fixture Candidates

| Candidate | Expected Contract |
|---|---|
| Posted but unpaid invoices | Require `state=posted` and `payment_state in [not_paid, partial, in_payment]`; forbid treating `state=posted` as paid. |
| Open vendor bills | Require `move_type=in_invoice`, `state=posted`, and `payment_state in [not_paid, partial, in_payment]`; forbid `move_type=out_invoice`, `payment_state=paid`, and `state=paid`. |
| Paid vendor bills | Require `move_type=in_invoice`, `state=posted`, and `payment_state=paid`; forbid `move_type=out_invoice` and `state=paid`. |
| Customer credit notes | Require `move_type=out_refund`, `state=posted`, and `payment_state=reversed` when querying reversed credit notes; forbid `move_type=out_invoice` and `state=reversed`. |
| Vendor credit notes | Require `move_type=in_refund` and `state=posted`; forbid `move_type=in_invoice` and customer refund direction. |
| Reconciled vendor payments | Require `payment_type=outbound`, `partner_type=supplier`, and `is_reconciled=true`; forbid inbound/customer substitution and generic `state` filters. |
| Vendor payments matched to bills | Use explicit payment-bill match facts plus outbound/supplier payment and `move_type=in_invoice` bill filters; preserve partial-bill residuals and split-payment multi-row allocations; do not rely on `is_reconciled=true` alone. |
| Overdue payables | Use due-date role plus `apOverdueAmount` / `apOverdueVendorCount`; do not use AR metrics or invoice date. |
| Fully invoiced sales orders | Use `invoice_status=invoiced`; do not use `state=done` as a billing substitute. |
| Sales cross-document flows | Use `OdooSaleDocumentFlowQueryModel` for sales order, delivery, customer invoice, and customer payment linkage; do not rely only on `origin` / `invoice_origin` string filters. |
| Delivered but not fully collected sales flows | Require sales lifecycle, delivery `state=done`, invoice `move_type=out_invoice`, invoice `payment_state in [partial, in_payment]`, and inbound customer payment direction when payment evidence is requested; do not use sale `state` as payment status. |
| Waiting bills for purchase orders | Use purchase `invoice_status=to invoice`; do not use purchase `state=purchase` alone. |
| Purchase cross-document flows | Use `OdooPurchaseDocumentFlowQueryModel` for purchase order, receipt, and vendor bill linkage; do not rely only on `origin` / `invoice_origin` string filters. |
| Received but open payable purchase flows | Require purchase lifecycle, receipt `state=done`, bill `move_type=in_invoice`, and bill `payment_state in [not_paid, partial, in_payment]`; do not use purchase `state` as payment status. |
| Overdue receivables | Use due-date role plus open settlement and posted lifecycle; do not use `invoice_date` as due date. |
| Customer invoice trend by month | Use `state=posted`, `move_type=out_invoice`, and group by `invoiceDate$yearMonth`; do not use due date, posting date, create date, or write date. |
| Done stock transfers this month | Use `state=done` and choose `dateDone` for actual completion analysis when requested; do not infer done from `dateDone is not null` unless signed. |
| Completed manufacturing throughput by month | Use `state=done` and group by `dateFinished$yearMonth`; do not use `dateStart`, `dateDeadline`, `createDate`, or `writeDate` for completion throughput. |
| Active customers/products/employees | Use `active`; do not use transaction `state`. |

## Related Files

- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/dicts.fsscript`
- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/model`
- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/query`
- Final model-registry target after local verification: `https://github.com/foggy-projects/foggy-model-registry.git`
- `docs/9.1.0/detailed_design/17_query_analysis_recipe_contract_index.md`
- `docs/9.1.0/domain_models/domain-capability-review-20260605.md`

## Open Follow-Ups

1. Add clarify fixtures for receivable aging policy, sales funnel policy, and inventory turnover policy only when the domain map points to missing policy slots.
2. Replace the demo `account_payment_bill_match` fixture table with native Odoo partial-reconcile relation mapping when full Odoo accounting line data is imported.
3. Revisit whether generic field-semantic-family metadata is needed after route fixtures reveal repeated cross-model failures.
