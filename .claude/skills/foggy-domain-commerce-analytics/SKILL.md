---
name: foggy-domain-commerce-analytics
description: 电商领域语义建模与 AI 查询题库迭代。用于处理销售、订单生命周期、支付结算、发货履约、退货退款、库存、客户、商品、门店、渠道、促销、销售团队等 ecommerce TM/QM 和 AI route-intent/clarify 题库；避免把 orderStatus/paymentStatus 等业务字段写成引擎规则。
---

# Foggy Commerce Analytics Domain

Use this skill when modifying or evaluating ecommerce domain models, prompts, route-intent fixtures, or clarification templates.

## Files To Read First

- `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/model`
- `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/query`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-*-order-*-fixture-*.md`
- `docs/9.1.0/domain_models/commerce-order-domain-model.md`
- `docs/9.1.0/domain_models/domain-capability-review-20260605.md`

## Semantic Boundaries

| Family | Fields | Rule |
|---|---|---|
| Order lifecycle | `orderStatus` | Open, cancelled, shipped, completed, and backlog intents belong here when the model contract says so. |
| Payment settlement | `paymentStatus`, payment model `payStatus` | Paid, unpaid, partially paid, refunded, and settlement wording belongs here. |
| Fulfillment/shipping | `shipDate`, shipped/unshipped wording | Date presence is not a state predicate unless the model contract explicitly says so. |
| Master-data activation | product/customer/store/channel/promotion/team `status` | Treat as enabled/disabled or availability, not order lifecycle. |
| Classification | customer/store/channel/promotion/product/payment types | Treat as dimension filters, not status. |
| Refund/return | `returnStatus`, `returnType`, `returnReason` | Do not reuse order or payment status semantics. |

## Workflow

1. Read the TM/QM fields and existing tests before deciding which field a natural language intent should use.
2. If intent words mention two semantic families, require both predicates instead of choosing one. Example: completed and paid means lifecycle plus settlement.
3. For order lifecycle/payment/fulfillment work, read the domain map and keep `PAID` out of `orderStatus` unless the domain contract changes.
4. If the user asks for a metric with unclear denominator, amount basis, date window, or exclusion policy, add or route to clarification fixtures.
5. Add route-intent fixtures for stable ecommerce behavior; add negative checks when a model must not use the neighboring family.
6. Use `foggy-model-generate` for TM/QM mechanics when fields or query model groups need changes.

## Do Not

- Do not hard-code ecommerce field names into engine code.
- Do not treat enum values as globally owned. `PAID` belongs to payment settlement unless a domain model explicitly owns it elsewhere.
- Do not expand prompt text as the only fix when a fixture can express the expected payload contract.
