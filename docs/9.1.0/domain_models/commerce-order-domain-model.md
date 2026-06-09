---
doc_role: domain_model
doc_purpose: Record ecommerce order lifecycle, payment settlement, and fulfillment field-family contracts.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Commerce Order Domain Model

## Scope

This document records the current ecommerce order semantic contract for `FactOrderModel` and `FactOrderQueryModel`.

It is a domain model contract, not an engine rule. The engine should continue to validate generic field/value/tool-argument contracts; ecommerce field names belong here, the ecommerce TM/QM files, and ecommerce AI fixtures.

## Core Field Families

| Family | Field | Owned Values Or Role | Correct Use | Must Not Be Confused With |
|---|---|---|---|---|
| Order lifecycle | `orderStatus` | `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `COMPLETED`, `CANCELLED`, `REFUNDED` | Open backlog, unshipped-by-contract, shipped, completed, cancelled order lifecycle predicates. | `paymentStatus=PAID`; `shipDate is null/not null`; master-data `status`. |
| Payment settlement | `paymentStatus` | `UNPAID`, `PAID`, `PARTIAL`, `REFUNDING`, `REFUNDED` | Paid, unpaid, partially paid, refunding, refunded settlement predicates. | `orderStatus=COMPLETED`; order lifecycle state. |
| Fulfillment date | `shipDate` | Shipping date attribute | Display, grouping, ordering, date comparison, and elapsed-time analysis. | Shipped/unshipped state predicate unless a future domain contract explicitly changes that. |
| Order business date | `orderDate`, `orderTime` | Order creation/business date | Time windows and order trend analysis. | Fulfillment date and payment date semantics. |
| Master-data activation | `salesTeam$status` and similar dimension `status` fields | Enabled/disabled or availability | Dimension availability filters. | Order lifecycle or payment settlement. |

## Intent Mapping

| User Intent | Required Predicate | Negative Contract |
|---|---|---|
| 打开订单积压 / 待处理订单 | `orderStatus in [PENDING, CONFIRMED, PROCESSING]` | Do not use `paymentStatus`; do not include `orderStatus=PAID`. |
| 未发货订单 | `orderStatus in [PENDING, CONFIRMED, PROCESSING]` | Do not use `shipDate is null`; do not use `paymentStatus`; do not include `orderStatus=PAID`. |
| 已发货订单 | `orderStatus=SHIPPED` | Do not use `shipDate is not null`; do not use `paymentStatus`; do not include `orderStatus=PAID`. |
| 未完全支付订单 | `paymentStatus in [UNPAID, PARTIAL]` | Do not use `orderStatus`. |
| 已完成支付或已退款订单 | `paymentStatus in [PAID, REFUNDED]` | Do not use `orderStatus`. |
| 已取消订单 | `orderStatus=CANCELLED` | Do not use `paymentStatus`; do not include `orderStatus=PAID`. |
| 已完成订单 | `orderStatus=COMPLETED` | Do not use `paymentStatus`; do not include `orderStatus=PAID`. |
| 已完成且已支付订单 | `orderStatus=COMPLETED` and `paymentStatus=PAID` | Do not collapse both predicates into either single field; do not include `orderStatus=PAID`. |
| 已支付且已发货订单 | `paymentStatus=PAID` and `orderStatus=SHIPPED` | Do not replace shipped state with `shipDate`; do not include `orderStatus=PAID`. |

## Enum Ownership

`PAID` is owned by payment settlement in the current ecommerce dictionary contract. It must not be added to `dicts.order_status` or generated as an `orderStatus` predicate to make an AI route pass.

The historical fixture anomaly where a model produced `orderStatus in [PENDING, CONFIRMED, PROCESSING, PAID]` should be treated as enum-domain contamination. The current route fixtures now encode this as a value-aware negative tool-argument rule:

```json
{
  "tool": "dataset.query_model",
  "path": "slice",
  "field": "orderStatus",
  "value": "PAID",
  "must_exist": false
}
```

This rule forbids the specific field/value pair while still allowing legitimate `orderStatus` lifecycle predicates in the same `slice`.

## Fixture Coverage

| Fixture | Contract |
|---|---|
| `ROUTE-ORDER-001` | Open backlog uses lifecycle open states and forbids `orderStatus=PAID`. |
| `ROUTE-ORDER-003` | Unpaid/partial payment uses `paymentStatus`, not `orderStatus`. |
| `ROUTE-ORDER-004` | Paid/refunded settlement uses `paymentStatus`, not `orderStatus`. |
| `ROUTE-ORDER-005` | Shipped uses `orderStatus=SHIPPED`, not `paymentStatus` or `shipDate` as predicate. |
| `ROUTE-ORDER-006` | Cancelled uses `orderStatus=CANCELLED`, not `paymentStatus`. |
| `ROUTE-ORDER-007` | Unshipped uses lifecycle open states, not `shipDate is null`, `paymentStatus`, or `orderStatus=PAID`. |
| `ROUTE-ORDER-008` | Unpaid and unshipped requires both settlement and lifecycle predicates. |
| `ROUTE-ORDER-009` | Paid and shipped requires both settlement and lifecycle predicates. |
| `ROUTE-ORDER-010` | Completed uses `orderStatus=COMPLETED`, not `paymentStatus`. |
| `ROUTE-ORDER-011` | Completed and paid requires both lifecycle and settlement predicates. |

## Related Workitems

- `docs/9.1.0/workitems/P2-order-status-paid-enum-anomaly-20260605.md`
- `docs/9.1.0/workitems/P2-ai-unshipped-order-intent-fixture-20260605.md`
- `docs/9.1.0/workitems/P2-ai-shipped-order-intent-fixture-20260605.md`
- `docs/9.1.0/workitems/P2-ai-unpaid-unshipped-order-intent-fixture-20260605.md`
- `docs/9.1.0/workitems/P2-ai-paid-shipped-order-intent-fixture-20260605.md`
- `docs/9.1.0/workitems/P2-ai-completed-paid-order-intent-fixtures-20260605.md`

## Open Follow-Ups

1. Decide whether field semantic family becomes first-class TM metadata or stays as domain documentation plus fixture contracts.
2. Add similar domain maps for return/refund, sales funnel, and Odoo state/payment/date fields before expanding those fixture packs.
3. Keep `shipDate` as a date attribute until product requirements explicitly define date-presence state semantics.
