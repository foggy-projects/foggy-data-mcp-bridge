---
doc_role: workitem
doc_purpose: Define the staged design for an outer Pivot response cache in the Java engine.
version: 9.2.0
target: Java Pivot outer response cache
status: design-ready-no-implementation
created_at: 2026-06-12
updated_at: 2026-06-12
---

# Pivot Outer Cache Design

## Purpose

`PIVOT-92-E1` tracks an outer cache for completed Pivot responses. It is intended to reduce repeated expensive Pivot execution after semantics are proven.

This is not an inner query cache, rollup cache, materialized view, ETL promotion mechanism, or database result cache. It sits outside the Pivot pipeline and can only return an already-shaped response when the full request and execution context are equivalent.

## Cache Boundary

The cache may store successful Pivot response payloads and their stable response metadata:

- Result rows/tree/grid payload.
- Stable warnings or contract fields.
- `pivotEngineContract`.
- Stable `debug.extra.pivotDiagnostics` evidence, with cache-hit additions.

The cache must not reuse volatile execution fields such as the original `durationMs` as if they were current execution time. On cache hit, the response should add a new cache diagnostic instead of pretending the pipeline ran again.

## Cache Key

The key must include every dimension that can affect visible result data or permissioned visibility:

| Key Part | Notes |
|---|---|
| Namespace and bundle identity | Include namespace, bundle name, and model registry or bundle hash/version. |
| Query model identity | Include query model name and loaded model version/hash when available. |
| Normalized Pivot request | Include rows, columns, values, metrics, filters, slice, having, sort, limits, totals, output format, and options after request normalization. |
| Runtime context | Include `extData`, system slices, tenant, datasource, and semantic runtime flags that influence filtering. |
| Principal and permission hash | Include user/principal identity plus field-access and row-access permission fingerprints. |
| Semantic policy | Include money scale, date/time bucketing policy, timezone when it affects grouping, and formula policy/version. |
| Dialect and execution capability | Include dialect family and capability hash if SQL/memory execution can change response ordering or shape. |

Any missing key dimension must produce a miss or refusal, not a partial key hit.

## Eligibility Stages

| Stage | Scope | Exit Gate |
|---|---|---|
| E1a | Cache lookup/refusal telemetry only; no cached responses returned. | Prove key construction and refusal reasons in diagnostics without changing behavior. |
| E1b | Cache deterministic successful flat/grid responses that do not use tree, cascade, volatile formulas, dynamic current-time filters, or unsupported warnings. | Prove result equality, permission isolation, TTL expiration, and diagnostics in tests. |
| E1c | Consider tree or cascade only after their semantics and dialect oracle coverage are accepted. | Tree/cascade cache is blocked until `PIVOT-92-D1` and dialect evidence are signed. |

The first implementation should start with E1a. Returning cached data before telemetry is visible would make correctness issues harder to diagnose.

## Invalidation

Initial invalidation should be conservative:

- Short TTL by default.
- Automatic miss when model bundle hash/version changes.
- Automatic miss when query model loaded version/hash changes.
- Optional datasource/table freshness token when the hosting application can provide one.
- Manual namespace/model cache eviction endpoint or service hook for operators.

The engine should not attempt database-level invalidation in the first cut. If a freshness token is absent, TTL remains the correctness boundary.

## Diagnostics

Recommended diagnostic events:

| Event | Decision | Required Fields |
|---|---|---|
| `pivot.cache.lookup` | `started` | model, keyHash, eligibilityStage |
| `pivot.cache.hit` | `hit` | model, keyHash, ageMs, cacheName |
| `pivot.cache.miss` | `miss` | model, keyHash, reason |
| `pivot.cache.refused` | `refused` | model, reason, shapeClass |
| `pivot.cache.store` | `stored` | model, keyHash, payloadBytes, ttlMs |
| `pivot.cache.evicted` | `evicted` | model, reason |

Cache diagnostics should be exposed under `debug.extra.pivotDiagnostics` so the existing production telemetry surface remains the single consumer contract.

## Safety Rules

- Never share cache entries across principals or permission fingerprints.
- Never cache failed responses, partial pipeline output, or unsupported warnings unless explicitly accepted later.
- Never use cache as a fallback after SQL pushdown or cascade execution fails.
- Never cache free-form formula shapes that depend on volatile functions or undeclared runtime state.
- Prefer miss/refused over uncertain key equivalence.

## Test Plan

Minimum tests before E1b:

- Same request and same permission context returns the cached payload with cache-hit diagnostics.
- Same request with a different permission hash misses.
- Model version/hash change misses.
- TTL expiry misses and refreshes.
- Tree, cascade, volatile formula, and unsupported warning shapes are refused.
- Cache-hit response does not preserve the original pipeline `durationMs` as current execution time.
- Concurrent identical requests do not corrupt the stored response or leak diagnostics between callers.
