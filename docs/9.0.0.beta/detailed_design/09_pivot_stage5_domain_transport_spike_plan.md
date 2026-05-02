---
doc_role: spike_plan
doc_purpose: Define a pre-release Stage 5A spike for Pivot large-domain transport without changing the public Pivot DSL.
version: 9.0.0.beta
target: Pivot Stage 5A Large-Domain Transport Spike
status: completed
decision: no-go-for-9.0.0.beta-defer-to-9.1.0
created_at: 2026-05-02
---

# Pivot Stage 5A Large-Domain Transport Spike Plan

## 1. Purpose

This document separates the Stage 5 discussion into two tracks:

- **Stage 5A: Large-domain transport** for non-additive subtotal/grandTotal surviving domains larger than the current `500` threshold.
- **Stage 5B: Advanced Pivot semantics** such as cascade Generate, multi-level TopN, and tree SQL TopN.

Stage 5A was evaluated as a release-preparation spike. The spike result is **No-Go for 9.0.0.beta** and defer to 9.1.0. It must not change the public Pivot DSL and must not silently expand the accepted 9.0.0.beta semantics.

## 2. Current Baseline

Stage 1-4 are accepted with risks:

- SQL TopN/Having pushdown is implemented through managed queryModel relation.
- Permission validation, pre-aggregation, physical column validation, dialect capability checks, and parameter binding stay inside the queryModel lifecycle.
- Non-additive subtotal/grandTotal auxiliary queries are bounded to the post-TopN surviving domain.
- `domain > 500` fails closed through `NonAdditiveRollupDomainTooLargeException`.
- Performance baseline concludes that Stage 5A is not required to release 9.0.0.beta.

## 3. Spike Question

Should the engine replace oversized `IN` / `OR-of-AND` domain predicates with a joinable domain relation for large non-additive subtotal/grandTotal workloads?

The spike should answer:

- Which transport shape is viable per dialect?
- Can it preserve queryModel permissions, preAgg, params, logging, and sanitizer behavior?
- Can it be added behind an internal feature flag without changing DSL behavior?
- Does it reduce future implementation cost enough to justify touching release-prep code?

## 4. Non-Goals

- Do not change external Pivot DSL syntax.
- Do not add public options such as `domainTransport`, `largeDomainMode`, or SQL hints.
- Do not implement cascade Generate or multi-level TopN.
- Do not implement tree SQL TopN.
- Do not bypass queryModel, managed relation, or existing permission/preAgg capabilities.
- Do not make Stage 5A the default execution path during the spike.

## 5. Candidate Internal SQL Shapes

### 5.1 VALUES CTE

Concept:

```sql
WITH base AS (...),
domain_values(axis_key_1, axis_key_2) AS (
  VALUES (?, ?), (?, ?), ...
)
SELECT ...
FROM base
JOIN domain_values d
  ON base.axis_key_1 = d.axis_key_1
 AND base.axis_key_2 = d.axis_key_2
```

Pros:

- Clean relational shape.
- Preserves tuple correlation.
- Easy to explain and test with SQL oracle.

Risks:

- Dialect syntax differs, especially MySQL.
- Parameter count limits still matter.
- Null-safe equality must be dialect-rendered.

### 5.2 UNION ALL Domain CTE

Concept:

```sql
WITH domain_values AS (
  SELECT ? AS axis_key_1, ? AS axis_key_2
  UNION ALL
  SELECT ?, ?
)
SELECT ...
FROM base
JOIN domain_values d ON ...
```

Pros:

- Often more portable than `VALUES` in older dialects.
- Similar to existing non-additive rollup batch thinking.

Risks:

- SQL length grows linearly.
- Very large domains still need batching.
- Column typing/casting must be explicit on some dialects.

### 5.3 Temporary Table / Session Table

Concept:

1. Create a session-scoped temp table for the domain.
2. Batch insert domain tuples.
3. Join auxiliary queries against the temp table.
4. Clean up reliably.

Pros:

- Handles larger domains better than huge SQL text.
- More scalable if domain transport becomes a common workload.

Risks:

- Highest lifecycle complexity.
- Requires transaction/session control.
- Cleanup and connection pooling behavior are risky.
- Cache keys and query logging become harder.

## 6. Recommended Spike Scope

Stage 5A spike should implement design and optionally a disabled prototype:

1. Add a small internal `DomainTransportPlan` model or equivalent design note.
2. Add dialect capability matrix for:
   - `VALUES` relation support;
   - `UNION ALL` domain CTE support;
   - temp table support;
   - null-safe equality rendering;
   - practical parameter count limits.
3. Prototype one transport shape behind an internal flag, preferably `UNION ALL Domain CTE` first.
4. Keep default behavior unchanged: `domain > 500` still fail-closed.
5. Add tests only if prototype code is committed:
   - tuple correlation;
   - null tuple matching;
   - params order: base params first, domain params after;
   - preAgg + systemSlice + domain transport;
   - fallback when dialect capability is missing.

## 7. Acceptance Criteria for Spike

The spike is successful if it produces a clear go/no-go decision:

- Recommended transport shape per dialect.
- Required production code touchpoints.
- Required tests and cross-db matrix.
- Confirmed no external DSL change.
- Confirmed Python mirror impact.
- Decision whether to implement in 9.0.0.beta, defer to 9.0.x, or defer to 9.1.0.

The spike should not be accepted as production implementation unless it also passes the full Stage 1-4 acceptance subset plus cross-database parity.

## 8. Python Engine Impact

Stage 5A does not change the public DSL or request/response snapshots. It can still affect Python parity planning through behavior:

- Current Java behavior for oversized non-additive rollup domains is a fail-closed error.
- If Java enables Stage 5A by default, the same request may start succeeding instead of failing.
- Python parity must either implement the same domain transport behavior or explicitly declare Stage 5A out of scope for its current milestone.

Recommended Python coordination:

- Python should keep the external Pivot DSL unchanged.
- Python should implement the current `domain > 500` fail-closed behavior first if it is mirroring 9.0.0.beta.
- Python may introduce an internal `DomainTransport` abstraction now, with only a fail-closed implementation, to reduce future migration cost.
- Python should not block its current mirror plan on Java Stage 5A unless release ownership decides Java Stage 5A will be enabled by default.

## 9. Release Recommendation

For 9.0.0.beta, keep the current `domain > 500` fail-closed behavior. Do not merge a default-on large-domain transport without a separate acceptance record.

Stage 5A production work and Stage 5B advanced semantics are moved to the 9.1.0 roadmap:

- `docs/9.1.0/README.md`
- `docs/9.1.0/detailed_design/00_java_pivot_engine_roadmap.md`
- `docs/9.1.0/workitems/pivot-java-engine-9.1.0-followups.md`
