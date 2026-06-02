---
doc_role: llm_capability_matrix
doc_purpose: Define when LLM agents should use query_model DSL, timeWindow, Pivot, or CTE tooling.
version: 9.1.0
target: MCP query planning guidance
status: guidance-ready
created_at: 2026-05-03
---

# LLM Query Tool Capability Matrix

## 1. Decision Principle

This document is guidance for LLM routing only. It does not change MCP tool descriptions, public Pivot DSL, queryModel lifecycle, or SQL planner behavior.

For the signed/unsigned non-SLA query analysis recipe registry, use `17_query_analysis_recipe_contract_index.md` as the source of truth. For ServiceTicket SLA recipes, use `16_service_ticket_sla_recipe_contract_index.md`.

The primary rule is: choose the most structured accepted tool that exactly matches the user's intent. If the request falls outside an accepted semantic subset, fail closed with an explicit error or rewrite prompt. Do not produce a best-effort query with guessed semantics.

- `query_model` is the public DSL tool for model-backed business queries.
- `timeWindow` is a `query_model` capability for standard time-intelligence columns.
- `pivot` is a `query_model` mode for multidimensional axes, cells, totals, and TopN.
- CTE / compose-script tooling is an independent advanced tool for relation composition. It must not bypass queryModel lifecycle, permissions, pre-aggregation, or physical column checks for model-backed data.
- Stage 5A `DomainTransportPlan` is an internal Pivot engine transport mechanism. It is not an LLM-facing tool choice.

## 2. Top-Level Routing

| User intent | Preferred route | Use when | Do not use when |
|---|---|---|---|
| List, filter, aggregate, group, sort, paginate business data | `query_model` DSL | The answer is a normal table, summary table, or grouped result from one query model | The user needs multidimensional rows/columns/cells, multi-step relation composition, or unsupported custom SQL semantics |
| YoY, MoM, WoW, YTD, MTD, rolling 7/30/90 day | `query_model` with `timeWindow` | The request asks for standard time comparison/window output columns | The request is also a Pivot request, needs arbitrary lag/lead, custom partitions, or row-count windows |
| Cross-tab / multidimensional analysis | `query_model` with `pivot` | The user asks for rows, columns, metrics, subtotals, grandTotal, tree output, parentShare, baselineRatio, or axis TopN | The request also asks for `timeWindow`, arbitrary MDX set algebra, or unsupported cascade shape |
| Multi-level TopN cascade | `query_model` with `pivot` C2 subset | Rows-axis exactly two levels, explicit `orderBy` on limited levels, additive metrics, supported SQL dialect | Column-axis cascade, row cascade plus column TopN/having, tree mode, three-level cascade, having-only cascade, non-additive cascade totals, unsupported dialect |
| Multi-step derived relation, union, custom intermediate relation | CTE / compose-script tool | The user explicitly needs relation composition that cannot be expressed as one safe `query_model` request | The same result can be expressed safely with `query_model`, `timeWindow`, or `pivot`; the CTE would bypass model lifecycle or permission checks |
| Large-domain non-additive subtotal/grandTotal transport | No LLM route; internal Pivot engine | Pivot needs internal domain transport and the engine can prove dialect support | The LLM should not manually construct this transport as public CTE syntax |

## 3. Capability Matrix

| Capability | Tool surface | Best scenario | Strength | Current boundary |
|---|---|---|---|---|
| Standard DSL query | `query_model` | Operational lookup, filtered detail rows, grouped KPI table | Least ambiguous; preserves model lifecycle and permissions | Not a multidimensional cell/axis model |
| Calculated fields | `query_model.calculatedFields` | Simple derived scalar or aggregate expression inside one query | Keeps expression in the model-backed query pipeline | Must pass dependency and permission validation; unresolved dependencies fail closed |
| Time intelligence | `query_model.timeWindow` | Same metric compared to prior period or rolling standard window | Avoids LLM-written date-offset self joins | Mutually exclusive with Pivot; fixed accepted comparisons only |
| Pivot base grid | `query_model.pivot` | Product by month, region by category, metric cells, subtotals, grandTotal | Explicit axes and cell semantics | `columns` mode and `pivot` mode are mutually exclusive |
| Pivot single-level TopN/Having | `query_model.pivot.rows/columns[].limit/having` | Top N regions, categories after metric filter | SQL pushdown when supported, controlled fallback for non-cascade cases | Must preserve deterministic ordering and accepted fallback/refusal behavior |
| Pivot C2 cascade TopN | `query_model.pivot` | Top N parents, then Top N children within surviving parents | Staged SQL CTE/window ranking, parent-domain filtering, additive totals over surviving domain | Rows two-level only in 9.1.0; no memory fallback; unsupported shapes fail closed |
| Pivot parentShare | `query_model.pivot.metrics[]` object | Child contribution to parent on rows axis | Structured replacement for public `ROLLUP_TO` strings | Not with tree/cascade; cannot participate in having/orderBy/limit |
| Pivot baselineRatio | `query_model.pivot.metrics[]` object | Compare column cells to first/last baseline column | Structured replacement for unsafe coordinate functions | Not with tree/cascade; cannot participate in having/orderBy/limit |
| CTE / compose script | Independent CTE tool | Multi-step relation flow, custom intermediate tables, union/merge/report shaping | Makes relation flow explicit when one DSL request is insufficient | Higher ambiguity; must not be used as a shortcut around queryModel constraints |

## 4. Mutual Exclusion and Fail-Closed Matrix

| Combination or request shape | Decision | LLM guidance |
|---|---|---|
| `timeWindow` + `pivot` | Reject | Ask the user to choose a timeWindow table or a Pivot table. For Pivot time analysis, express periods as axis members or accepted calculated metrics, not `timeWindow`. |
| `columns` + `pivot` in one `query_model` request | Reject | Use normal projection mode or Pivot mode, not both. |
| Pivot cascade without explicit `orderBy` on a limited level | Reject with `PIVOT_CASCADE_ORDER_BY_REQUIRED` | Ask for a metric and direction to rank by. |
| Pivot cascade on unsupported SQL dialect or planner capability | Reject with `PIVOT_CASCADE_SQL_REQUIRED` | Suggest simplifying to single-level TopN or running on a supported dialect. |
| Pivot cascade + non-additive ranking/having/totals | Reject with `PIVOT_CASCADE_NON_ADDITIVE_REJECTED` | Ask for an additive metric or remove cascade/totals. |
| Pivot cascade across both row and column axes | Reject with `PIVOT_CASCADE_CROSS_AXIS_REJECTED` | Ask the user to limit cascade to one rows-axis hierarchy or simplify one axis. |
| Pivot cascade + tree mode | Reject with `PIVOT_CASCADE_TREE_REJECTED` | Ask the user to choose tree output or cascade TopN. |
| Pivot cascade outside C2 v1 scope | Reject with `PIVOT_CASCADE_SCOPE_UNSUPPORTED` | Covers column-axis cascade, rows plus column TopN/having, three-level cascade, having-only cascade, parentShare/baselineRatio with cascade. |
| Raw CTE intended to bypass model permissions or queryModel lifecycle | Reject | Route model-backed data access through `query_model`, or compose only managed query outputs. |

## 5. Practical Examples

| User request | Route | Reason |
|---|---|---|
| "查一下本月订单明细，按金额倒序" | `query_model` | Detail rows with filter/order/pagination. |
| "按月份看销售额同比和环比" | `query_model.timeWindow` | Standard time comparison columns. |
| "按品类和月份做销售额透视表，带总计" | `query_model.pivot` | Axes, cells, subtotal/grandTotal semantics. |
| "每个 Top 3 品类下面再取 Top 2 子品类" | `query_model.pivot` C2 if all whitelist conditions pass | Rows two-level cascade TopN. |
| "先聚合销售，再和预算表合并算差异" | CTE / compose-script if not expressible as one model query | Multi-step relation composition. |
| "透视表里同时做同比" | Reject and rewrite | `timeWindow` and Pivot are mutually exclusive; ask for a Pivot by period or a timeWindow summary table. |

## 6. Rewrite Guidance for LLMs

When refusing a request, return a concrete alternative instead of inventing semantics:

- For `timeWindow + pivot`: "I can produce a Pivot by month/quarter, or a timeWindow summary with YoY/MoM columns. Which one should I use?"
- For unsupported cascade dialect: "Cascade TopN requires supported CTE/window SQL. I can simplify to single-level TopN or run the same Pivot without cascade."
- For missing cascade `orderBy`: "Please specify the metric and direction for each limited level, such as salesAmount desc."
- For non-additive cascade totals: "Use an additive metric for cascade totals, or remove subtotal/grandTotal."
- For CTE bypass risk: "This data should be accessed through query_model first so model permissions and query lifecycle are preserved."

## 7. Current Boundary

This guidance is ready for use as release documentation. MCP tool descriptions are intentionally not updated here; the release owner will handle tool-description wording separately.
