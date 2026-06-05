---
doc_role: workitem
doc_purpose: Record fact_order order_status enum anomaly observed during AI route-intent fixture calibration.
version: 9.1.0
status: recorded
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 Order Status Paid Enum Anomaly

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a data/enum contract anomaly that can affect AI semantic routing.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

During order route-intent fixture calibration, the local MySQL fixture data showed `fact_order.order_status = PAID` for 2,006 rows. This value belongs naturally to payment settlement semantics and overlaps with `payment_status = PAID`.

Current AI route-intent fixtures treat `orderStatus` as the governed order lifecycle field and `paymentStatus` as the governed settlement field. Therefore `orderStatus = PAID` is recorded as an anomaly rather than added as a valid lifecycle expectation.

## Observed Distribution

| payment_status | order_status | count |
|---|---|---:|
| PAID | COMPLETED | 11,966 |
| PAID | SHIPPED | 2,051 |
| UNPAID | CANCELLED | 2,041 |
| PAID | PAID | 2,006 |
| UNPAID | PENDING | 1,940 |

## Risk

- LLMs may learn from catalog or result examples that `orderStatus = PAID` is a valid lifecycle state.
- Completed-order and paid-order intent can become harder to disambiguate.
- Future fixture rows or demos may accidentally encode payment semantics into lifecycle examples.

## AI Matrix Evidence

The `order-route-suite-gemini3flash-20260605` batch passed all order route cases, but reported a semantic payload-shape divergence on `ROUTE-ORDER-007`: direct baseline used `orderStatus in [PENDING, CONFIRMED, PROCESSING]`, while `gemini-3-flash` used `orderStatus in [PENDING, CONFIRMED, PROCESSING, PAID]`.

This confirms the anomaly is observable in AI routing behavior even when the current fixture still passes. It should remain a data/model contract cleanup candidate instead of being normalized into route-intent expectations.

## Current Decision

- Do not use `orderStatus = PAID` as an expected lifecycle predicate in AI fixtures.
- Keep completed-order intent strict on `orderStatus = COMPLETED`.
- Keep paid-order intent strict on `paymentStatus = PAID`.
- Treat this as a data/model contract cleanup candidate, not as prompt-tuning work.

## Follow-Up Options

- Normalize seed data so `order_status` only contains lifecycle values.
- Add enum dictionary metadata that makes lifecycle and settlement status families explicit.
- Add a model-validation check that flags payment-like enum values under lifecycle status fields.
- Re-run AI route-intent matrix after data normalization to detect behavior changes.

## Validation

Recorded from local fixture calibration:

- `orderStatus = COMPLETED` and `paymentStatus = PAID`: 11,966 rows.
- `orderStatus = PAID` and `paymentStatus = PAID`: 2,006 rows.
