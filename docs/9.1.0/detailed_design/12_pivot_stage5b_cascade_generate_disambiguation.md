---
doc_role: detailed_design
doc_purpose: Disambiguate LLM-safe execution boundaries for Stage 5B Cascade Generate before C2 implementation
version: 9.1.0
target: Pivot Semantic Layer
status: signed-off-with-risks
created_at: 2026-05-02
---

# Pivot Stage 5B: Cascade Generate Disambiguation

## 1. Purpose

This document is the `PIVOT-91-C1.1` semantic addendum for Cascade Generate and multi-level TopN.

`11_pivot_stage5b_cascade_generate_semantics.md` defines the desired semantic direction. This addendum defines the LLM-safe execution boundary for `PIVOT-91-C2`: which requests may execute, which requests must be rejected, and which SQL oracle cases must exist before implementation starts.

The main rule is:

> When Pivot cascade semantics cannot be uniquely inferred and executed through a proven deterministic path, the engine must reject the request with a rewriteable error instead of returning a best-effort result.

## 2. Goals and Non-Goals

**Goals**:
- Prevent LLM-generated Pivot requests from producing ambiguous or silently incorrect results.
- Require explicit fail-closed behavior when cascade semantics cannot be proven.
- Define a narrow C2 implementation whitelist.
- Define rewriteable error categories for unsupported request shapes.
- Define the minimum SQL oracle set required before C2 implementation.

**Non-Goals**:
- No public Pivot DSL changes.
- No MDX-compatible set algebra.
- No MongoDB or alternate backend implementation.
- No production Java code changes in this design step.
- No acceptance signoff; this document is a pre-implementation gate.

## 3. LLM-Safe First Principle

Cascade Generate must be deterministic, explainable, and rewriteable.

A cascade request may execute only when all of the following are true:

1. The ranking grain is unique.
2. Parent-domain filtering order is unique.
3. Having-before-TopN order is unique.
4. Row/column interaction is unique.
5. Subtotal/grandTotal surviving-domain semantics are unique.
6. The current engine can execute the request through a verified deterministic path.
7. The selected backend can express the request without unsafe fallback.

If any condition is not true, the request must fail closed with a structured error and a rewrite suggestion.

## 4. Cascade Detection

A request enters cascade semantics when either condition is true:

- The same axis contains two or more levels with `limit` or `having`.
- A multi-level axis contains a `limit` that requires parent-key partitioned ranking.

Intermediate levels without `limit` or `having` are not filtered domains. They only act as partition keys for lower-level ranking.

## 5. Fixed Semantic Assertions

These assertions must be treated as implementation invariants:

- A parent level is ranked before any child-level limit is applied.
- A child level is ranked only inside the surviving parent domain.
- Child-level having or limit never changes whether a parent survived.
- At each level, execution order is: parent surviving-domain filter, current-level aggregate, having, rank, limit.
- Ranking grain at level `L` is the axis prefix `fields[0..L]`.
- Row-axis ranking and column-axis ranking are independent.
- Row/column surviving-domain intersection is applied only during final cell and total calculation.
- Memory fallback is forbidden for cascade requests unless a staged memory implementation has its own parity oracle. (See §13 for full supersession rule and §14 for dialect-specific refusal behavior.)

## 6. C2 First-Version Whitelist

`PIVOT-91-C2` may start only with this narrow support set:

| Request Shape | C2 v1 Decision |
|---|---|
| rows axis two-level cascade | supported candidate |
| rows axis three-level cascade | rejected |
| columns axis cascade | rejected |
| rows cascade plus columns cascade | rejected |
| rows cascade plus column single-level limit | rejected in v1 |
| additive metric in `orderBy` / `having` | supported candidate |
| non-additive metric in `orderBy` / `having` | rejected |
| additive subtotal/grandTotal | supported candidate only with oracle |
| non-additive subtotal/grandTotal | rejected |
| missing `orderBy` on a limited level | rejected |
| tree mode plus cascade | rejected |
| parentShare / baselineRatio plus cascade | rejected |
| unsupported dialect or non-wrappable relation | rejected |
| SQL planner failure | rejected, no memory fallback |

The first version should prefer correctness over breadth. Requests outside the whitelist must not be executed as best-effort results.

## 7. Required Execution Capability

A cascade request requires all of the following:

- `queryFacade` managed relation is available.
- The managed relation is wrappable.
- Permissions have already been validated.
- Stable aliases are available.
- The dialect supports CTEs.
- The dialect supports `ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)`.
- The planner can generate explicit NULL-bucket tie-break ordering.
- The planner can express null-safe tuple matching.
- Parameter order is stable: base relation params, then cascade having params, then cascade limit params, unless a future accepted SQL shape explicitly proves a different order.

If any capability is unavailable, C2 must reject the request. It must not silently fall back to the current memory path.

## 8. OrderBy and Tie-Breaking

Every level with `limit > 0` must declare explicit `orderBy`.

Missing `orderBy` is rejected because default ranking would create hidden product semantics and could vary between memory, SQL, and database engines.

Each TopN stage must append deterministic tie-breakers:

1. Requested order metrics.
2. Explicit NULL bucket for each tie key.
3. Stable axis prefix keys in ASC order.

For a child level, the tie-breaker must include the full prefix key, not only the target child key, so cross-dialect ordering is stable.

## 9. Subtotal and GrandTotal

For additive metrics, subtotals and grandTotal may be supported only when SQL oracle coverage proves surviving-domain correctness.

Rules:

- Leaf cells are constrained by the surviving row domain and surviving column domain.
- Row subtotals are constrained by the surviving row domain and surviving column domain.
- Column subtotals are constrained by the surviving row domain and surviving column domain.
- GrandTotal is computed over the surviving row domain and surviving column domain, not the original full domain.

For non-additive metrics, cascade plus subtotal/grandTotal remains rejected.

## 10. Non-Additive Metrics

C2 v1 must reject non-additive metrics in cascade requests when any of the following is true:

- The non-additive metric is used in `orderBy`.
- The non-additive metric is used in axis `having`.
- `rowSubtotals`, `columnSubtotals`, or `grandTotal` is enabled.

If a non-additive metric is leaf output only and no totals/ranking/having depend on it, it remains out of C2 v1 scope unless a separate oracle proves correctness.

## 11. Structured Error Categories

Errors must guide an LLM toward a safer rewrite.

### PIVOT_CASCADE_ORDER_BY_REQUIRED

Trigger: a cascade level has `limit > 0` without explicit `orderBy`.

Required guidance:

```text
Multi-level TopN requires explicit orderBy at every limited level.
Field '<field>' has limit=<limit> but no orderBy.
Please add orderBy, remove this limit, or reduce the request to single-level TopN.
```

### PIVOT_CASCADE_SQL_REQUIRED

Trigger: staged SQL execution is unavailable or planner generation fails.

Required guidance:

```text
Multi-level TopN requires staged SQL execution. This request cannot safely fall back to memory execution.
Please remove one axis limit, simplify to single-level TopN, or run on a dialect with CTE and window function support.
```

### PIVOT_CASCADE_NON_ADDITIVE_REJECTED

Trigger: non-additive metrics participate in cascade ranking/having/totals.

Required guidance:

```text
Multi-level TopN with non-additive metrics is rejected because correctness cannot be guaranteed for ranking, having, subtotal, or grandTotal.
Please disable subtotal/grandTotal, use additive metrics only, or query leaf results without cascade.
```

### PIVOT_CASCADE_CROSS_AXIS_REJECTED

Trigger: both row and column axes require cascade semantics, or v1 scope sees row cascade plus column TopN.

Required guidance:

```text
This version supports cascade ranking on one rows axis shape only.
Please remove column limits, use single-level TopN, or split the analysis into separate queries.
```

### PIVOT_CASCADE_TREE_REJECTED

Trigger: `hierarchyMode=tree` appears with cascade.

Required guidance:

```text
Tree mode with multi-level TopN is not supported because tree ranking semantics are not frozen.
Please remove hierarchyMode=tree or remove TopN.
```

### PIVOT_CASCADE_SCOPE_UNSUPPORTED

Trigger: the request is semantically defined but outside C2 v1 scope, such as three-level cascade or SQL size thresholds.

Required guidance:

```text
This cascade request is outside the supported implementation scope.
Please reduce it to rows two-level cascade, remove extra limits, or split the request.
```

## 12. Required SQL Oracle Cases

C2 implementation may not start until these oracle cases are listed as implementation tests:

1. Parent TopN plus child TopN: child ranks only within surviving parents.
2. Parent ranking is based on the full current-level aggregate, not surviving child aggregate.
3. Parent having plus child TopN: parent having filters before child ranking.
4. Child having plus child TopN: child having runs before child limit and does not affect parent survival.
5. Missing `orderBy` on a limited cascade level is rejected.
6. Deterministic tie with NULL keys: metric tie, explicit NULL bucket, full prefix key order.
7. Additive row subtotal and grandTotal over surviving domain.
8. Unsupported dialect or missing SQL capability is rejected without memory fallback.
9. Non-additive ranking/having/totals are rejected.
10. Tree mode plus cascade is rejected.

At least SQLite, MySQL 8, and PostgreSQL must have real SQL parity for the supported candidate path. MySQL 5.7 must have explicit refusal evidence.

## 13. C1.1 Supersession of C1 Memory Fallback

`11_pivot_stage5b_cascade_generate_semantics.md` (C1) originally stated that if a dialect lacks window functions, it falls back to memory execution. **C1.1 supersedes that statement for cascade requests.**

For any request that enters cascade semantics (§4), the following rule applies:

> Memory fallback is **forbidden** for Cascade Generate / multi-level TopN requests. The current memory execution path lacks staged parent-domain filtering and cannot guarantee correct ranking grain. A cascade request must execute through a verified staged SQL path or be rejected.

A future staged memory implementation may be reconsidered only if it has its own parity oracle coverage equivalent to the SQL oracle matrix defined in this document.

For **single-level** TopN requests that do not enter cascade semantics, C1's original memory fallback rule still applies.

## 14. Dialect v1 Decision Matrix

C2 v1 implementation must explicitly declare support or refusal for each target dialect. The decision is based on SQL capability requirements (CTEs, `ROW_NUMBER() OVER (PARTITION BY ...)`, null-safe tuple matching) and available parity test infrastructure.

| Dialect | C2 v1 Decision | Rationale | Required Evidence |
|---|---|---|---|
| SQLite 3.30+ | supported candidate | Full CTE/window support; in-memory test infrastructure available | Real SQL parity tests for all 10 oracle cases |
| MySQL 8.0+ | supported candidate | Full CTE/window support since 8.0; JDBC metadata gate for 8.0.19+ already exists | Real SQL parity tests for all 10 oracle cases |
| PostgreSQL 12+ | supported candidate | Full CTE/window support; established CI profile | Real SQL parity tests for all 10 oracle cases |
| MySQL 5.7 | explicit refusal | No CTE support; no `ROW_NUMBER()`; cascade cannot be expressed as staged CTEs | Refusal test: cascade request → `PIVOT_CASCADE_SQL_REQUIRED` error; must not silently fall back to memory |
| SQL Server 2012+ | v1 rejected/deferred | CTE/window capability exists, but no established CI profile and no oracle coverage | Not included in C2 v1 scope; may be added in a future version if oracle coverage is established |

Any dialect not listed must be treated as unsupported and must produce `PIVOT_CASCADE_SQL_REQUIRED`.

## 15. Having-Only Cascade Decision

A multi-level axis request where levels have `having` filters but no `limit` (having-only cascade) raises a scoping question: does it enter cascade semantics?

**C2 v1 decision: rejected unless existing single-level semantics are fully proven.**

Rationale:

- Multi-level `having` without `limit` still requires parent-domain-aware aggregation at each level if the having condition at level L+1 must reflect only the parent domain surviving level L's having.
- Current single-level having is well-defined: aggregate → filter → done. But chained multi-level having implies staged domain filtering without an explicit ranking step, creating an ambiguous execution order.
- Proving correctness requires dedicated oracle cases that do not yet exist.

If a request has multi-level having without any limit:

- If levels are independent (each having is evaluated against the full base relation, not the parent's surviving domain), this is **not** cascade semantics and can use existing single-level paths.
- If levels are dependent (child having must reflect parent having survival), this **is** cascade semantics and must be rejected in C2 v1 with `PIVOT_CASCADE_SCOPE_UNSUPPORTED`.

The engine must detect the dependency pattern and reject ambiguous cases. The conservative default is to treat any multi-level having as cascade and reject it unless the request can be decomposed into independent single-level evaluations.

## 16. Oracle-to-Test Matrix

Each required SQL oracle case (§12) is mapped to expected test type, dialect profile, and expected pass/refusal behavior with error codes.

| # | Oracle Case | Test Type | Profiles | Expected Behavior | Error Code (if refusal) |
|---|---|---|---|---|---|
| 1 | Parent TopN + child TopN: child ranks within surviving parents | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: child domain is subset of parent surviving domain | N/A |
| 2 | Parent ranking by full current-level aggregate | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: parent rank unchanged by child limit | N/A |
| 3 | Parent having + child TopN | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: parent having filters before child ranking starts | N/A |
| 4 | Child having + child TopN: no parent side effect | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: child having does not remove a parent from surviving domain | N/A |
| 5 | Missing `orderBy` on a limited cascade level | Rejection unit test | SQLite (any) | REJECT: request refused before SQL generation | `PIVOT_CASCADE_ORDER_BY_REQUIRED` |
| 6 | Deterministic tie with NULL keys | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: identical metric values produce stable ordering via NULL bucket + prefix key | N/A |
| 7 | Additive row subtotal/grandTotal over surviving domain | SQL parity integration | SQLite, MySQL 8, PostgreSQL | PASS: totals reflect only surviving cells | N/A |
| 8 | Unsupported dialect / missing SQL capability | Rejection unit test | MySQL 5.7 (simulated) | REJECT: request refused, no memory fallback | `PIVOT_CASCADE_SQL_REQUIRED` |
| 9 | Non-additive ranking/having/totals | Rejection unit test | SQLite (any) | REJECT: request refused before execution | `PIVOT_CASCADE_NON_ADDITIVE_REJECTED` |
| 10 | Tree mode + cascade | Rejection unit test | SQLite (any) | REJECT: request refused before execution | `PIVOT_CASCADE_TREE_REJECTED` |

Additional coverage rules:

- SQL parity tests (cases 1-4, 6-7) must compare staged CTE output with a handwritten SQL oracle query on real data.
- Rejection tests (cases 5, 8-10) must verify the exact error code and that no partial or best-effort result is returned.
- MySQL 5.7 refusal (case 8) must verify that the cascade planner detects missing CTE/window capability and produces `PIVOT_CASCADE_SQL_REQUIRED` without falling back to the memory execution path.

## 17. C2 Start Gate

`PIVOT-91-C2` may start only after:

- This C1.1 disambiguation addendum is accepted.
- The required SQL oracle cases are mapped to tests.
- C2 scope is explicitly limited to the whitelist in this document.
- Unsupported request shapes have structured, rewriteable error behavior.

Until those conditions are met, C2 remains gated.
