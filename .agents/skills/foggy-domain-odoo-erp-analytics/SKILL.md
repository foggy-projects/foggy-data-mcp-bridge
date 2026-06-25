---
name: foggy-domain-odoo-erp-analytics
description: Odoo/ERP 领域语义建模与 AI 查询题库迭代。用于处理 Odoo 会计、应收应付、支付、CRM、销售、采购、库存调拨、制造、项目、商品、伙伴、员工等 TM/QM；重点区分 state、payment_state、invoice_status、stage、kanban_state、type、active 和业务日期角色。
---

# Foggy Odoo ERP Analytics Domain

Use this skill when working on Odoo/ERP domain models, AI fixtures, or clarification templates.

## Files To Read First

- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/model`
- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/query`
- `docs/9.1.0/domain_models/odoo-erp-domain-model.md`
- `docs/9.1.0/detailed_design/17_query_analysis_recipe_contract_index.md`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/clarify-routing-tests.json`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-accounting-tests.json`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-sales-purchase-tests.json`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/odoo-stock-mrp-tests.json`
- `docs/9.1.0/domain_models/domain-capability-review-20260605.md`

## Semantic Boundaries

| Family | Odoo Examples | Rule |
|---|---|---|
| Document lifecycle | sale/purchase/account/stock/MRP `state` | Draft, posted, confirmed, done, cancelled style workflow belongs here. |
| Settlement/payment | `payment_state`, payment reconciliation flags | Paid/unpaid/in-payment belongs here, not document lifecycle. |
| Billing/invoicing | `invoice_status` | Billing completion is not purchase/sale lifecycle. |
| Workflow stage | CRM lead stage, project task stage | Stage is a pipeline/work-board concept, not necessarily final state. |
| Kanban state | `kanban_state` | Kanban display or attention state must not replace task stage/state. |
| Classification type | `move_type`, `payment_type`, product/partner/task types | Type is classification, not lifecycle. |
| Activation | `active`, product sale/purchase flags | Master-data availability, not transaction status. |
| Business date role | invoice date, due date, posting date, order date, expected arrival, done date | Choose the date based on the requested analysis, not column name similarity alone. |

## Workflow

1. Read the specific Odoo TM/QM before choosing `state`, `payment_state`, `invoice_status`, `stage`, or `type`.
2. Read the domain map before adding Odoo route fixtures; keep accounting/payment and sales/purchase in separate fixture packs.
3. If a request combines lifecycle and settlement wording, require both fields when both are available.
4. If a metric depends on accounting policy, receivable aging bucket, risk rating, or budget/version definition, route to clarification until modeled.
5. Add AI route fixtures for one Odoo subdomain at a time; current packs cover accounting/payment, sales/purchase, stock picking, and MRP.
6. Use `foggy-model-generate` for model changes and keep Odoo-specific business rules in this domain layer.
7. Use this bridge repo as the local debug/evidence workspace for Odoo TM/QM changes. After a TM/QM is verified, promote the model artifacts to `https://github.com/foggy-projects/foggy-model-registry.git`; do not treat `foggy-dataset-demo/src/main/resources/foggy/templates/odoo` as the long-term source of truth.

## Current Evidence

- Sales/purchase, stock picking, MRP, accounting/payment, and first negative semantic-conflict direct fixtures have local passing evidence.
- `mrp_production` and `mrp_bom` are available in the local fixture database for MRP direct tests.
- For manufacturing completion throughput, use `state=done` plus `dateFinished$yearMonth`; do not use start/deadline/create/write dates as completion-period substitutes.
- For customer invoice amount/count trends, use `state=posted`, `moveType=out_invoice`, and `invoiceDate$yearMonth`; do not use due date, posting date, create date, or write date as invoice-period substitutes.
- For customer credit notes / refunds, use `moveType=out_refund`; do not include them in ordinary customer invoice trend unless explicitly requested.
- For vendor credit notes / refunds, use `moveType=in_refund`; do not treat them as ordinary vendor bills (`in_invoice`).
- For reversed/refund settlement, use `paymentState=reversed` while keeping accounting lifecycle `state=posted`; do not use `state=reversed`.
- For paid invoices, require `paymentState=paid` and keep `state=posted` as accounting lifecycle; do not use `state=paid`.
- For paid vendor bills, require `moveType=in_invoice`, `state=posted`, and `paymentState=paid`; do not use customer invoice direction.
- For open vendor bills, require `moveType=in_invoice`, `state=posted`, and `paymentState in ('not_paid', 'partial', 'in_payment')`; do not include draft bills or customer invoices.
- For overdue payables, use `apOverdueAmount` and `apOverdueVendorCount`; do not reuse AR metrics or invoice-date period logic.
- For vendor payments, require `paymentType=outbound` and `partnerType=supplier`; use `isReconciled` for matched/settled payment state, not generic document `state`.
- For "which payment matched which vendor bill" questions, use `OdooAccountPaymentBillMatchQueryModel`; do not rely on `isReconciled=true` alone as proof of a specific bill match.
- For matched vendor payments where the bill remains open, keep the payment-bill match fact and require `billMove$paymentState=partial`; preserve bill residual amount instead of treating the matched payment as fully paid.
- For split vendor payments, use one payment-bill match row per bill; the same `payment$caption` can return multiple vendor bill rows.
- For purchase-order to receipt to vendor-bill questions, use `OdooPurchaseDocumentFlowQueryModel`; do not infer the cross-document relationship only by string-matching `origin` / `invoice_origin`.
- For received-but-open payable purchase flows, require purchase lifecycle (`purchaseOrder$state in ('purchase','done')`), receipt completion (`receiptPicking$state=done`), vendor bill direction (`billMove$moveType=in_invoice`), and bill settlement (`billMove$paymentState in ('not_paid','partial','in_payment')`).
- For sales-order to delivery to customer-invoice to customer-payment questions, use `OdooSaleDocumentFlowQueryModel`; do not infer the cross-document relationship only by string-matching `origin` / `invoice_origin`.
- For delivered-but-not-fully-collected sales flows, require sales lifecycle (`saleOrder$state in ('sale','done')`), delivery completion (`deliveryPicking$state=done`), customer invoice direction (`invoiceMove$moveType=out_invoice`), invoice settlement (`invoiceMove$paymentState in ('partial','in_payment')` when customer payment evidence is requested), and inbound customer payment (`payment$paymentType=inbound`, `payment$partnerType=customer`).
- For overdue AR customer count, use `COUNT_DISTINCT(if(..., null))` in QM formulas; do not use SQL-style `count(distinct(...))` inside the expression DSL.

## Do Not

- Do not infer Odoo enum meaning from English words alone; respect `dictRef` and field descriptions.
- Do not use invoice due date as business date for revenue trend unless the model contract requests aging/overdue analysis.
- Do not hard-code Odoo field names into engine validation.
