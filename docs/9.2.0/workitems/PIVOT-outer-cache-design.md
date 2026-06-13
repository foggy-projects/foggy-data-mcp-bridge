---
doc_role: workitem
doc_purpose: Define the staged design for an outer Pivot response cache in the Java engine.
version: 9.2.0
target: Java Pivot outer response cache
status: e1b-provider-boundary-verified-default-off
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
- A default `PivotOuterCacheModelIdentityProvider` now computes a runtime bundle fingerprint from the loaded QM resource and its related TM resources. Explicit `bundle-fingerprint` / `model-freshness-token` configuration still takes precedence, so production deployments can replace the default provider with a signed registry or deployment manifest provider.
- `PivotOuterCacheProvider` is the cache-provider boundary. The default implementation remains `PivotOuterResponseCache`, a local in-memory provider. Distributed providers such as Redis must implement the same lookup/store/evict/TTL/copy-isolation contract before being enabled.
- `PivotOuterCacheDistributedProviderContract` is a client-neutral descriptor for distributed provider implementers. It defines the default key prefix `foggy:pivot:outer`, JSON `v1` payload metadata, positive TTL requirement, absolute-expiry storage expectation, namespace/model index requirement, copy-isolation requirement, response key layout, namespace index layout, model index layout, and eviction scope mapping. It is not a Redis client, serializer module, or bundled distributed cache provider.
- `PivotOuterCacheDistributedPayload`, `PivotOuterCacheDistributedPayloadCodec`, `PivotOuterCacheJsonPayloadCodec`, and `PivotOuterCacheDistributedProviderAdapter` provide the provider-module adapter skeleton for Redis/KV implementations. The adapter owns response-key construction, payload envelope metadata, JSON codec boundary, TTL expiry check, namespace/model index maintenance, safe corrupt-payload miss/removal, and lookup/store copy isolation while leaving actual storage primitives to subclasses.
- `PivotOuterCacheProviderContractTest` is now the reusable provider contract fixture. Any external provider should extend it and prove disabled behavior, store/lookup copy isolation, TTL expiry, namespace/model scoped eviction, all-scope eviction, and payload byte estimation before being wired into production.
- `PivotOuterCacheInvalidationEvent` is the transport-safe invalidation payload. It preserves the cache-provider scope rules while normalizing blank model to all-model scope and blank namespace to the default namespace. `replayDeduplicationKey()` is available only when an explicit `eventId` is present; scope-only manual cleanup intentionally has no deduplication key so repeated operator actions are not dropped as replays.
- `PivotOuterCacheInvalidationResult` is the aggregate invalidation result. It records removed entries, attempted/succeeded/failed node counts, and immutable error details so distributed broadcasters can report partial success without binding the engine to Redis/MQ.
- `PivotOuterCacheInvalidationBroadcaster` is the invalidation fan-out boundary. The default `LocalPivotOuterCacheInvalidationBroadcaster` delegates to `SemanticQueryServiceV3.evictPivotOuterCache(namespace, model)` in the current JVM. Multi-node deployments can replace this bean with an event bus, registry publish hook, or distributed cache invalidation implementation. The legacy `evict(namespace, model)` method remains supported; new implementations should prefer `evict(PivotOuterCacheInvalidationEvent)`.
- `PivotOuterCacheInvalidationReplayWindow` is a process-local guard for event-bus-backed consumers. It deduplicates explicit `eventId` values within a bounded TTL window, leaves scope-only manual cleanup repeatable, and filters `sourceNodeId == localNodeId` self-loop consumption. It is not a distributed dedupe store.
- `PivotOuterCacheInvalidationBroadcasterContractTest` is now the reusable invalidation fan-out contract fixture. External broadcaster implementations should prove namespace+model eviction, namespace all-model eviction, blank-model handling, model across all namespaces, default namespace isolation, all-scope eviction, aggregate removed counts, event result counts, and idempotent repeat eviction.
- `PivotOuterCacheInvalidationFanOutContractTest` provides an in-memory two-node simulation for the broadcaster contract and now uses `PivotOuterCacheInvalidationReplayWindow` for per-node consumer gating. This proves the intended fan-out, explicit event-id replay deduplication, source-node self-loop filtering, and partial-failure reporting semantics without introducing a Redis/MQ dependency into the engine.
- `BundleRemovedEvent` now clears TM/QM caches and invokes the invalidation broadcaster for the affected namespace. A broadcaster failure is isolated and logged as a Pivot outer-cache invalidation warning after TM/QM cleanup instead of being reported as a model-cache cleanup failure.
- `SemanticQueryServiceV3.evictPivotOuterCache(namespace, model)` is available as the local operational cleanup hook. `namespace == null` clears all namespaces, `namespace == ""` targets the default namespace, and `model == null` or blank clears all models in the selected namespace scope.
- A production admin endpoint is available but default-off: setting `foggy.dataset.pivot.outer-cache.admin-endpoint-enabled=true` enables `DELETE /semantic/v3/admin/pivot-outer-cache/evict?namespace=...&model=...`, which delegates to the invalidation broadcaster. Direct broadcaster exceptions are converted to a `success=false` payload with failed-node and error details instead of surfacing as an endpoint failure.
- Maximum-size eviction, deep-copy response isolation, and concurrent cache hits are covered by `PivotOuterResponseCacheTest`.

Current code touchpoints:

- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/config/DatasetProperties.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/DbModelAutoConfiguration.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/LocalPivotOuterCacheInvalidationBroadcaster.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedPayload.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedPayloadCodec.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedProviderAdapter.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedProviderContract.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheJsonPayloadCodec.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationBroadcaster.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationEvent.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationReplayWindow.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationResult.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentity.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentityProvider.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheProvider.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetry.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterResponseCache.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotDiagnosticCollector.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/RuntimeBundlePivotOuterCacheModelIdentityProvider.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/event/BundleLifecycleListener.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/controller/PivotOuterCacheAdminController.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/SemanticQueryServiceV3.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedProviderAdapterTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheDistributedProviderContractTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationBroadcasterContractTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationEventTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationFanOutContractTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheInvalidationReplayWindowTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheProviderContractTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheOperationalSpiTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetryTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterResponseCacheTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/controller/PivotOuterCacheAdminControllerTest.java`
- `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotCascadeGenerateSqlParityIntegrationTest.java`

## Invalidation

Initial invalidation should be conservative:

- Short TTL by default.
- Automatic miss when the configured signed model bundle fingerprint changes.
- Automatic miss when the configured model freshness token changes.
- Automatic miss when query model loaded identity changes.
- Optional datasource/table freshness token when the hosting application can provide one.
- Manual namespace/model cache eviction via `SemanticQueryServiceV3.evictPivotOuterCache(namespace, model)` for operators or model-registry publish flows.
- Manual namespace/model cache eviction via the default-off admin endpoint when the host explicitly enables it.
- Bundle removal events clear the affected namespace through the invalidation broadcaster.

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
        admin-endpoint-enabled: ${FOGGY_PIVOT_OUTER_CACHE_ADMIN_ENDPOINT_ENABLED:false}
```

Operational rules:

- Keep `enabled=false` until the serving deployment can trust either the runtime bundle fingerprint, a signed `bundle-fingerprint`, an explicit `model-freshness-token`, or a custom `PivotOuterCacheModelIdentityProvider`.
- If replacing the default provider, extend `PivotOuterCacheProviderContractTest` in the provider module and keep all provider-contract cases green.
- If replacing the default provider with a distributed provider, preserve `PivotOuterCacheDistributedProviderContract`: stable key prefix, response/index key layout, JSON payload versioning, positive TTL, absolute expiry, namespace/model indexes, and lookup/store copy isolation. Prefer subclassing `PivotOuterCacheDistributedProviderAdapter` unless the provider needs a fundamentally different storage layout; in that case it must still pass the provider contract tests and preserve the documented key/payload semantics.
- If replacing the default broadcaster, extend `PivotOuterCacheInvalidationBroadcasterContractTest` or an equivalent provider-module subclass and keep all fan-out contract cases green. Prefer `evict(PivotOuterCacheInvalidationEvent)` for new implementations and report aggregate `PivotOuterCacheInvalidationResult` counts, including partial failures. Use `eventId` as the replay deduplication source; do not derive a replay key from namespace/model scope alone. Event-bus-backed consumers can use `PivotOuterCacheInvalidationReplayWindow` for process-local replay and `sourceNodeId` self-loop filtering, but a real multi-process window still belongs to the event bus or distributed provider.
- Use a short TTL first, then increase only after cache hit/miss diagnostics show stable key separation.
- On model registry publish, namespace reload, or emergency rollback, call `evictPivotOuterCache(namespace, model)` or the default-off admin endpoint. Passing `namespace=null` clears all namespaces; passing `model=null` clears all models in the selected namespace scope.
- Bundle removal triggers the default invalidation broadcaster automatically for the affected namespace.
- In multi-node deployments, the default broadcaster is local to one JVM. Operators must replace `PivotOuterCacheInvalidationBroadcaster`, fan out the admin/service call, bump the freshness token, or use a distributed `PivotOuterCacheProvider`.
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
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheAdminControllerTest,PivotIntegrationTest#testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b+testOuterCacheMissesAcrossSecurityContextsE1b+testOuterCacheEvictByNamespaceAndModelE1b+testOuterCacheSkipsWarningResponsesE1b,BundleLifecycleListenerTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; runtime bundle fingerprint provider changes hash on QM/TM resource changes, local invalidation broadcaster delegates to the service hook, admin eviction delegates to the broadcaster, and BundleLifecycleListener stays green with namespace cache cleanup; Tests run: 20, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='FieldPermissionResolverTest,SyntheticMemberPermissionResolverTest,FieldAccessPermissionStepTest,FieldAccessPermissionIntegrationTest,PhysicalColumnPermissionIntegrationTest,SemanticServiceV3Test,PivotIntegrationTest,PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; permission chain, SemanticServiceV3, full PivotIntegrationTest, existing outer-cache tests, new SPI tests, admin endpoint delegation, and bundle lifecycle cleanup pass together; Tests run: 176, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterResponseCacheTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheAdminControllerTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; local provider now extends the reusable provider contract covering disabled behavior, copy isolation, TTL expiry, namespace/model scoped eviction, all-scope eviction, and payload byte estimation; Tests run: 11, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheTelemetryTest,PivotOuterResponseCacheTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheAdminControllerTest,PivotIntegrationTest#testOuterCacheHitFlatPivotE1b+testOuterCacheTtlExpiryFlatPivotE1b+testOuterCacheMissesAcrossSecurityContextsE1b+testOuterCacheEvictByNamespaceAndModelE1b+testOuterCacheSkipsWarningResponsesE1b,BundleLifecycleListenerTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; provider contract tests, telemetry, operational SPI, admin delegation, key E1b Pivot cache scenarios, and bundle lifecycle cleanup pass together after the contract fixture was introduced; Tests run: 24, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheOperationalSpiTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; simulated two-node invalidation fan-out covers namespace/model, namespace all-model, blank model, model across all namespaces, default namespace isolation, all-scope idempotence, aggregate removed counts, local broadcaster delegation, and provider contract behavior; Tests run: 17, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheInvalidationEventTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheAdminControllerTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; invalidation event normalization, result aggregation/error immutability, event-result fan-out counts, simulated partial-failure reporting, local broadcaster unavailable-service result semantics, admin response diagnostics, and provider contract behavior pass together; Tests run: 23, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheInvalidationEventTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest,PivotOuterCacheOperationalSpiTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; replay deduplication requires explicit event id, admin eviction surfaces partial-failure diagnostics, BundleRemovedEvent isolates broadcaster failures after TM/QM cleanup, and the existing invalidation/provider contracts remain green; Tests run: 31, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheDistributedProviderContractTest,PivotOuterCacheInvalidationEventTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest,PivotOuterCacheOperationalSpiTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; distributed provider contract descriptor/key layout, explicit-event replay dedupe, source-node self-loop filtering simulation, admin broadcaster-exception failure payload, bundle lifecycle failure isolation, operational SPI, and provider behavior remain green together; Tests run: 37, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheDistributedProviderAdapterTest,PivotOuterCacheDistributedProviderContractTest,PivotOuterCacheInvalidationReplayWindowTest,PivotOuterCacheInvalidationFanOutContractTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; distributed provider adapter passes the reusable provider contract through an in-memory KV implementation, stores JSON payload envelopes under contract response/index keys, removes corrupt payloads as misses, and fan-out simulation now uses the replay window for explicit-event dedupe and source-node self-loop filtering; Tests run: 26, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheDistributedProviderAdapterTest,PivotOuterCacheDistributedProviderContractTest,PivotOuterCacheInvalidationReplayWindowTest,PivotOuterCacheInvalidationEventTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest,PivotOuterCacheOperationalSpiTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; distributed adapter/codec skeleton, replay-window guard, invalidation event/result, fan-out simulation, admin failure payload, bundle lifecycle failure isolation, operational SPI, and local provider contract all remain green together; Tests run: 50, Failures: 0, Errors: 0, Skipped: 0. |

## Remaining Boundary

- E1b remains default-off. The default provider and broadcaster are still process-local; no Redis provider, MQ publisher, or cross-node coherency implementation is bundled.
- The current `keyHash` includes the available `QueryModel` / `TableModel` fingerprint, runtime bundle resource fingerprint, and optional deployment-provided `bundle-fingerprint` / `model-freshness-token`; the engine does not verify a signed registry hash by itself.
- Real registry model-file invalidation is supported through the runtime bundle fingerprint, external fingerprint/freshness token, manual service/admin hook, and replaceable invalidation broadcaster; automatic registry event fan-out is an SPI boundary, not a bundled event bus.
- Permission isolation, warning skip, maximum-size eviction, provider contract behavior, distributed provider descriptor/key layout, distributed adapter/payload codec skeleton, process-local replay window, simulated two-node broadcaster fan-out semantics, replay deduplication key semantics, source-node self-loop filtering simulation, admin partial-failure and broadcaster-exception diagnostics, deep-copy isolation, concurrent-hit behavior, runtime fingerprint provider, local broadcaster, admin endpoint delegation, and bundle-removal cleanup with broadcaster-failure isolation now have local tests, but no Redis client, bundled distributed cache provider, real event bus, cross-process dedupe store/window, or cross-process race evidence exists.
- Tree and cascade cache eligibility remains blocked until their semantics and dialect evidence are separately signed.
