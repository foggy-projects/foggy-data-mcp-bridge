---
doc_role: workitem
doc_purpose: Define the startup-safe boundary for a future Redis/KV Pivot outer-cache provider module.
version: 9.2.0
target: Java Pivot outer-cache distributed provider boundary
status: planned-contract
created_at: 2026-06-14
updated_at: 2026-06-14
---

# Pivot Outer Cache Redis Provider Boundary

## Scope

The Java engine now has a cache-provider SPI, a safe-provider wrapper, a distributed-provider contract descriptor, and a distributed adapter skeleton. This workitem records the acceptance boundary for a future Redis/KV provider implementation.

This is a provider-module contract. It must not move a Redis client, Redis auto-configuration, or Redis startup requirement into the core `foggy-dataset-model` engine module.

## Required Behavior

- Core engine startup must not require Redis, even when `foggy.dataset.pivot.outer-cache.enabled=true`.
- A Redis/KV provider bean must not connect, ping, authenticate, create keys, or fail because Redis is unavailable during construction.
- A Redis/KV provider must not perform Redis network I/O from `@PostConstruct`, eager lifecycle callbacks, or `isEnabled()`.
- `isEnabled()` should be configuration-state only, or otherwise degrade through the provider operation path without blocking Spring context startup.
- Redis network I/O belongs in provider operations: `lookup`, `store`, `evict`, and any provider-owned index cleanup.
- Runtime Redis failures should surface as `RuntimeException` from provider operations. With the default `fail-on-provider-unavailable=false`, `PivotOuterCacheSafeProvider` converts them to miss/no-op/zero-result behavior.
- `estimatePayloadBytes` must not perform Redis network I/O.
- Missing Redis must degrade cache acceleration only. It must not block Pivot query execution by default.
- Setting `foggy.dataset.pivot.outer-cache.fail-on-provider-unavailable=true` remains the explicit fail-fast mode for deployments that require distributed cache availability.

## Provider Module Gates

A Redis/KV provider is not accepted until all gates below are green:

| Gate | Required Evidence |
|---|---|
| No core dependency | `foggy-dataset-model` has no transitive Redis client dependency and no Redis auto-configuration requirement. |
| Startup safety | Spring context starts with the provider configured against an invalid Redis endpoint when `fail-on-provider-unavailable=false`. |
| Disabled safety | Provider disabled state does not connect to Redis and does not require Redis credentials. |
| Operation downgrade | `lookup`, `store`, and `evict` failures wrapped by `PivotOuterCacheSafeProvider` degrade to miss/no-op/zero-result by default. |
| Fail-fast mode | The same operation failures rethrow when `fail-on-provider-unavailable=true`. |
| Provider contract | The provider extends `PivotOuterCacheProviderContractTest` or an equivalent subclass and proves copy isolation, TTL expiry, namespace/model eviction, all-scope eviction, and byte estimation. |
| Distributed contract | The provider preserves `PivotOuterCacheDistributedProviderContract` key prefix, response/index layouts, JSON payload version, positive TTL, absolute expiry, and namespace/model index semantics. |
| Response diagnostics | Real Pivot execution with an unavailable provider returns normal query results and emits `pivot.cache.provider_unavailable` plus `provider_unavailable` miss/store-skip diagnostics. |

## Test Skeleton

Provider modules should add tests equivalent to:

```java
class RedisPivotOuterCacheProviderStartupSafetyTest {

    @Test
    void contextStartsWithInvalidRedisEndpointWhenSafeModeIsDefault() {
        // Start provider configuration with an unreachable Redis endpoint.
        // Assert Spring context creation succeeds.
        // Assert no query execution is required for startup.
    }

    @Test
    void disabledProviderDoesNotConnectToRedis() {
        // Configure provider enabled=false with invalid endpoint.
        // Assert context starts and provider operations stay disabled-safe.
    }
}
```

```java
class RedisPivotOuterCacheProviderContractTest extends PivotOuterCacheProviderContractTest {

    @Override
    protected PivotOuterCacheProvider createProvider(long ttlMillis, int maximumSize) {
        // Return Redis-backed provider using an isolated Redis database/container.
    }
}
```

```java
class RedisPivotOuterCacheProviderUnavailableTest {

    @Test
    void unavailableRedisDegradesThroughSafeProviderByDefault() {
        // Wrap a Redis-backed provider with PivotOuterCacheSafeProvider.
        // Stop or misconfigure Redis.
        // Assert lookup miss, store no-op, evict zero, and one-shot unavailable event.
    }
}
```

The concrete Redis client can be Lettuce, Redisson, Jedis, or a host-provided adapter, but it must be isolated to the provider module and keep the engine SPI stable.

## Operational Notes

- First production rollout should keep short TTL and monitor `pivot.cache.provider_unavailable`, `pivot.cache.miss`, `pivot.cache.store_skipped`, and admin eviction result failures.
- Operators should treat provider unavailable as cache acceleration loss, not query failure, when safe mode is default.
- If Redis is mandatory for a specific deployment, the deployment should opt into `fail-on-provider-unavailable=true` and own the resulting startup/query failure boundary.

## Current Engine Evidence

| Command | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotDiagnosticCollectorTest,PivotOuterCacheStartupSafetyTest,PivotOuterCacheSafeProviderTest,PivotIntegrationTest#testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b+testOuterCacheHitFlatPivotE1b+testOuterCacheSkipsWarningResponsesE1b' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false clean test` | success; provider-unavailable diagnostic contract, no-Redis startup safety, safe-provider one-shot unavailable event, real Pivot unavailable-provider downgrade, E1b cache hit, and warning store-skip behavior pass together; Tests run: 11, Failures: 0, Errors: 0, Skipped: 0. |

## Links

- `PIVOT-outer-cache-design.md` owns the engine-side cache SPI and default-off E1b behavior.
- `PIVOT-production-telemetry-diagnostics.md` owns the production diagnostics consumption guidance.
