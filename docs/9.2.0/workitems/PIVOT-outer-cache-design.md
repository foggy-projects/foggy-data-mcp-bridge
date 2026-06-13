---
doc_role: workitem
doc_purpose: Define the staged design for an outer Pivot response cache in the Java engine.
version: 9.2.0
target: Java Pivot outer response cache
status: e1b-operational-verified-default-off
created_at: 2026-06-12
updated_at: 2026-06-13
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

## Implementation Check-In

E1a is implemented as telemetry-only cache evaluation:

- Added `PivotOuterCacheTelemetry` as the request/context fingerprint and eligibility evaluator.
- Added `pivot.cache.lookup` to successful Pivot response diagnostics.
- Added `pivot.cache.miss` for currently eligible flat/grid requests, with reason `telemetry_only`.
- Added `pivot.cache.refused` for excluded shapes, including `tree_mode` and `cascade_shape`.
- The fingerprint includes model, namespace, normalized Pivot request, slice, calculated fields, `extData`, system slice, security context, field-access set, denied physical columns, and the currently available `QueryModel` / `TableModel` identity snapshot.
- When the outer cache is disabled, the runtime still emits E1a `lookup` plus `miss` / `refused` diagnostics and never returns cached data.

E1b is implemented as a conservative local response cache:

- Added `foggy.dataset.pivot.outer-cache.enabled`, `ttl-millis`, `maximum-size`, `bundle-fingerprint`, and `model-freshness-token` configuration under `DatasetProperties`.
- The default remains `enabled=false`, preserving existing production behavior unless an application explicitly opts in.
- Added `PivotOuterResponseCache`, a process-local in-memory cache for fully shaped Pivot responses.
- Flat/grid successful responses with no warnings can be stored and returned by key.
- Tree mode, tree output, cascade shape, streaming response, volatile calculated fields, and unsupported output formats are refused.
- Cache hit returns a copied response, refreshes the current `durationMs`, replaces `debug.extra.pivotDiagnostics`, and does not emit `pivot.execution_path`.
- Cache store writes `pivot.cache.store` into response diagnostics before storing a response copy.
- TTL expiry removes the stale entry, emits `pivot.cache.evicted` with reason `ttl_expired`, and refreshes the response through normal Pivot execution.
- Warning responses are not stored and now emit `pivot.cache.store_skipped` with reason `response_warning`.
- Permission isolation is verified both at fingerprint level and through an enabled-pipeline test: different `SecurityContext` values produce different keys and miss instead of sharing cache entries.
- QueryModel/TableModel fingerprint changes are covered by key-change unit tests.
- Deployment-provided model identity is part of the key: `bundle-fingerprint` is intended for a signed registry version, bundle digest, release artifact hash, or git SHA; `model-freshness-token` is an operator-controlled bump token for model-file or datasource snapshot changes.
- `SemanticQueryServiceV3.evictPivotOuterCache(namespace, model)` is available as the local operational cleanup hook. `namespace == null` clears all namespaces, `namespace == ""` targets the default namespace, and `model == null` or blank clears all models in the selected namespace scope.
- Maximum-size eviction, deep-copy response isolation, and concurrent cache hits are covered by `PivotOuterResponseCacheTest`.

Current code touchpoints:

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/config/DatasetProperties.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetry.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterResponseCache.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotDiagnosticCollector.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/SemanticQueryServiceV3.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetryTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterResponseCacheTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotCascadeGenerateSqlParityIntegrationTest.java`

## Invalidation

Initial invalidation should be conservative:

- Short TTL by default.
- Automatic miss when the configured signed model bundle fingerprint changes.
- Automatic miss when the configured model freshness token changes.
- Automatic miss when query model loaded identity changes.
- Optional datasource/table freshness token when the hosting application can provide one.
- Manual namespace/model cache eviction via `SemanticQueryServiceV3.evictPivotOuterCache(namespace, model)` for operators or model-registry publish flows.

The engine should not attempt database-level invalidation in the first cut. If a freshness token is absent, TTL remains the correctness boundary.

## Production Enablement Checklist

E1b is still default-off. A host application should enable it only after wiring a deployment freshness boundary:

```yaml
foggy:
  dataset:
    pivot:
      outer-cache:
        enabled: ${FOGGY_PIVOT_OUTER_CACHE_ENABLED:false}
        ttl-millis: ${FOGGY_PIVOT_OUTER_CACHE_TTL_MS:300000}
        maximum-size: ${FOGGY_PIVOT_OUTER_CACHE_MAX_SIZE:256}
        bundle-fingerprint: ${FOGGY_MODEL_BUNDLE_FINGERPRINT:}
        model-freshness-token: ${FOGGY_MODEL_FRESHNESS_TOKEN:}
```

Operational rules:

- Keep `enabled=false` until the serving deployment can provide either a signed `bundle-fingerprint`, an explicit `model-freshness-token`, or both.
- Use a short TTL first, then increase only after cache hit/miss diagnostics show stable key separation.
- On model registry publish, bundle removal, namespace reload, or emergency rollback, call `evictPivotOuterCache(namespace, model)` on each running service instance. Passing `namespace=null` clears all namespaces; passing `model=null` clears all models in the selected namespace scope.
- In multi-node deployments, this hook is local to one JVM. Operators must fan out the cleanup call, bump the freshness token, or use a future distributed provider.
- Treat absent fingerprint/token as TTL-only correctness. It is acceptable for local demo and controlled tests, but not enough for broad production promotion.

## Diagnostics

Recommended diagnostic events:

| Event | Decision | Required Fields |
|---|---|---|
| `pivot.cache.lookup` | `started` | model, keyHash, eligibilityStage, shapeClass |
| `pivot.cache.hit` | `hit` | model, keyHash, eligibilityStage, ageMs, cacheName, shapeClass |
| `pivot.cache.miss` | `miss` | model, keyHash, eligibilityStage, reason, shapeClass |
| `pivot.cache.refused` | `refused` | model, keyHash, eligibilityStage, reason, shapeClass |
| `pivot.cache.store` | `stored` | model, keyHash, eligibilityStage, payloadBytes, ttlMs, shapeClass |
| `pivot.cache.store_skipped` | `skipped` | model, keyHash, eligibilityStage, reason, shapeClass |
| `pivot.cache.evicted` | `evicted` | model, keyHash, eligibilityStage, reason, shapeClass |

Cache diagnostics should be exposed under `debug.extra.pivotDiagnostics` so the existing production telemetry surface remains the single consumer contract.

Default-off E1a emits only `lookup` plus either `miss` or `refused`. When E1b is explicitly enabled, flat/grid cacheable responses can also emit `hit`, `store`, `store_skipped`, and `evicted`.

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

## Verification

| Command | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotIntegrationTest#testBasicFlatPivot+testHierarchyTreeSubtotalsRejected,PivotCascadeGenerateSqlParityIntegrationTest#testRowsTwoLevelCascadeSubset' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; flat Pivot exposes `pivot.cache.lookup` + `pivot.cache.miss`, tree Pivot exposes `pivot.cache.refused` with `tree_mode`, and C2 cascade exposes `pivot.cache.refused` with `cascade_shape`; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotIntegrationTest#testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; E1b flat Pivot stores a response, second identical request hits local cache without `pivot.execution_path`, and 1ms TTL expiry evicts plus refreshes through normal execution; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest,PivotIntegrationTest#testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b+testOuterCacheMissesAcrossSecurityContextsE1b+testOuterCacheSkipsWarningResponsesE1b' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; key changes are verified for permission context and QueryModel/TableModel fingerprint; local cache eviction/deep-copy/concurrency behavior is covered; enabled E1b misses across users and emits `pivot.cache.store_skipped` for warning responses; Tests run: 9, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotIntegrationTest#testBasicFlatPivot+testGridPivot+testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b+testHierarchyTreeSubtotalsRejected,PivotCascadeGenerateSqlParityIntegrationTest#testRowsTwoLevelCascadeSubset' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; E1a telemetry, E1b hit/store/TTL expiry, tree refusal, grid baseline, and cascade refusal stay green together; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotIntegrationTest,PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; existing Pivot integration response contracts plus E1b safety tests remain green together; PivotIntegrationTest Tests run: 53; total Tests run: 58, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='FieldPermissionResolverTest,SyntheticMemberPermissionResolverTest,FieldAccessPermissionStepTest,FieldAccessPermissionIntegrationTest,PhysicalColumnPermissionIntegrationTest,SemanticServiceV3Test,PivotIntegrationTest,PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; permission resolver, field-access, physical denied-column, SemanticServiceV3, Pivot integration, and E1b outer-cache tests pass together after the cache key started carrying permission fingerprints; Tests run: 164, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest,PivotIntegrationTest#testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b+testOuterCacheMissesAcrossSecurityContextsE1b+testOuterCacheEvictByNamespaceAndModelE1b+testOuterCacheSkipsWarningResponsesE1b' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; bundle fingerprint and freshness token now alter cache keys, local cache evicts by namespace/model, and the service hook forces the next Pivot request to miss and re-execute before storing again; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='FieldPermissionResolverTest,SyntheticMemberPermissionResolverTest,FieldAccessPermissionStepTest,FieldAccessPermissionIntegrationTest,PhysicalColumnPermissionIntegrationTest,SemanticServiceV3Test,PivotIntegrationTest,PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; permission, SemanticServiceV3, Pivot integration, and outer-cache suites remain green after deployment model identity keying and namespace/model eviction hook; Tests run: 168, Failures: 0, Errors: 0, Skipped: 0. |

## Remaining Boundary

- E1b remains default-off and process-local; no distributed provider or cross-node coherency is implemented.
- The current `keyHash` includes the available `QueryModel` / `TableModel` fingerprint plus deployment-provided `bundle-fingerprint` and `model-freshness-token`; the engine does not yet compute or verify a signed registry hash by itself.
- Real registry/bundle model-file invalidation is supported only through the external fingerprint/freshness token or the manual service hook; automatic registry event fan-out is not implemented.
- Permission isolation, warning skip, maximum-size eviction, deep-copy isolation, and concurrent-hit behavior now have local tests, but no distributed cache provider or multi-node race evidence exists.
- Tree and cascade cache eligibility remains blocked until their semantics and dialect evidence are separately signed.
