---
doc_role: execution_plan
doc_purpose: Plan domain Skill and domain model packages without moving business semantics into the engine core.
version: 9.1.0
status: draft
created_at: 2026-06-05
---

# Domain Skill And Model Plan

## Goal

Build reusable domain Skills and model packages around AI semantic querying. The purpose is to make domain intent, field-family boundaries, enum ownership, metric recipes, and clarification rules explicit before expanding prompt text or engine code.

## Layering

| Layer | Owns | Must Not Own |
|---|---|---|
| Engine | query_model/Pivot/timeWindow/DSL_CTE contracts, validation, trace, field-family primitives, enum ownership checks | Business field names such as `orderStatus` or `invoice_status` as hard-coded behavior |
| Domain model | TM/QM files, dictionaries, semantic families, time roles, governed relations, metric recipes | Runtime query compilation rules |
| Domain Skill | Agent workflow for reading/patching domain models, selecting fixtures, deciding clarification vs execution | Generic Foggy model generation rules already covered by `foggy-model-generate` |
| AI fixture pack | Route-intent tests, clarify tests, negative semantic-conflict tests, report evidence | Product feature promises without model evidence |

## Domain Package Shape

Each domain should converge to this package shape:

| Artifact | Location | Purpose |
|---|---|---|
| Domain Skill | `.claude/skills/foggy-domain-*/SKILL.md` | Lightweight domain guidance for future AI/model iterations. |
| Domain map | `docs/9.1.0/domain_models/*-domain-model.md` | Field families, enum ownership, required metrics, clarify boundaries. |
| TM/QM model files | Debug in `foggy-dataset-demo/src/main/resources/foggy/templates/{domain}`; promote verified artifacts to `https://github.com/foggy-projects/foggy-model-registry.git` | Executable model contract and final reusable model package. |
| Fixture pack | `foggy-dataset-mcp/src/test/resources/ai-test-cases/*.json` | AI routing and clarification evidence. |
| Workitem/acceptance | `docs/9.1.0/workitems` and `docs/9.1.0/acceptance` | Change record and signoff evidence. |

## Prioritized Domains

| Priority | Domain | Reason | Next Deliverable |
|---|---|---|---|
| P0 | Commerce order lifecycle/payment/fulfillment | Most AI route fixtures; known enum contamination risk | Consolidate domain map and add negative semantic-family fixture cases. |
| P0 | Service ticket SLA/backlog | Signed narrow recipe exists; clarify templates already present | Expand from SLA to backlog only after policy slots are modeled. |
| P1 | Odoo/ERP accounting + procurement + inventory + MRP | Broad model surface with many state/payment/date families | Sales/purchase, stock, MRP, accounting/payment, paid-vs-posted, AP vendor-bill direction, credit-note direction, reversed settlement, open AP outstanding, AP overdue metrics, vendor-payment direction, payment-to-bill matching, partial-bill matching, split-payment allocation, sales-order to delivery to customer-invoice to customer-payment flow routing, purchase-order to receipt to vendor-bill flow routing, due-date, completion-date, and invoice-date trend fixtures are recorded; next replace demo matching facts / demo sales/purchase flow facts with native Odoo relations when full line data is available. |
| P1 | Commerce return/refund | Models exist and clarify refund-rate template exists | Define refund denominator, refund amount basis, and return lifecycle semantics. |
| P2 | CRM/sales funnel | Models exist but funnel metric rules are underspecified | Start with clarify-only domain card, then add governed conversion recipe. |
| P2 | Inventory/manufacturing quality | Odoo and ecommerce inventory models exist; manufacturing clarify template exists | Keep clarify-first until yield/turnover formulas are signed. |
| P3 | Finance budget, subscription, marketing attribution, sales target | Clarify fixtures exist, but no scanned TM/QM package | Do not build full model until real schema appears. |
| P3 | MCP audit and vector search | Models exist but not business analytics priority | Add later as observability/search domain Skills. |

## Skill Creation Rules

1. A domain Skill must describe workflow and boundaries, not copy a large schema dump.
2. Detailed field lists belong in reference files only when a domain is too large; first drafts can keep a compact table in `SKILL.md`.
3. Domain Skills call or reference `foggy-model-generate` for TM/QM mechanics instead of reimplementing model generation rules.
4. Every domain Skill must state which intents require clarification rather than execution.
5. Every domain Skill must state which status/type/stage fields must not be conflated.

## Model Creation Rules

1. Add or patch TM/QM only when a real field or business decision exists.
2. Use `dictRef` for owned enum fields; do not reuse enum values across unrelated fields without dictionary ownership.
3. Use field descriptions to express domain semantics, but keep them concise.
4. Use `timeRole` and `recommendedUse` for business dates when multiple date fields compete.
5. Route AI evaluation through fixture packs instead of relying on prompt-only behavior.
6. Treat bridge-repo TM/QM edits as pre-promotion debug changes. Once direct baselines and target Maven tests pass, move the verified TM/QM package into `foggy-model-registry` so downstream projects consume the registry version rather than the bridge demo copy.

## Execution Plan

| Step | Deliverable | Status |
|---|---|---|
| D1 | Domain inventory and boundary review | complete-initial |
| D2 | First project-level domain Skills for commerce, service ticket, and Odoo/ERP | complete-initial |
| D3 | Domain map documents for P0 domains | complete-initial |
| D4 | Negative semantic-family fixtures for ecommerce order/payment/fulfillment | complete-initial |
| D5 | Odoo state/payment/date field-family fixture packs plus direct runner | sales-cross-document-flow-direct-baseline-passed |
| D6 | Clarify-only domain cards for finance/subscription/marketing/sales-target | planned |
| D7 | Engine-neutral metadata proposal for field semantic family and enum ownership | planned |

## Open Questions

1. Whether field semantic family should be represented as first-class TM metadata or initially only as QM description/dictRef conventions.
2. Whether AI report warnings should become hard failures for enum-domain contamination such as `PAID` inside `orderStatus`.
3. Whether domain Skills should live in repo-level `.claude/skills` only, or also be published to the company skill marketplace after stabilization.
