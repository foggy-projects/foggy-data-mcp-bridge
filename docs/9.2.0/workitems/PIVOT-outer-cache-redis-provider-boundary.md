---
doc_role: workitem
doc_purpose: Define the startup-safe boundary for Redis/KV Pivot outer-cache provider modules.
version: 9.2.0
target: Java Pivot outer-cache distributed provider boundary
status: redis-provider-invalidation-listener-local-verified
created_at: 2026-06-14
updated_at: 2026-06-17
---

# Pivot Outer Cache Redis Provider Boundary

## Scope

The Java engine now has a cache-provider SPI, a safe-provider wrapper, a distributed-provider contract descriptor, and a distributed adapter skeleton. This workitem records the acceptance boundary for Redis/KV provider implementations.

This is a provider-module contract. It must not move a Redis client, Redis auto-configuration, or Redis startup requirement into the core `foggy-dataset-model` engine module.

The optional `addons/foggy-dataset-model-cache` module now contains a first Redis provider fixture plus Redis Pub/Sub invalidation publish/consume/listen primitives. This fixture is local-verified with mock/in-memory storage, unavailable Redis operation tests, invalidation codec/broadcaster/consumer/listener tests, startup-safe listener auto-wiring tests, a local Docker Redis live provider run, and a local Docker Redis Pub/Sub invalidation run. It is not yet a full production Redis or cross-process coherency signoff.

## Required Behavior

- Core engine startup must not require Redis, even when `foggy.dataset.pivot.outer-cache.enabled=true`.
- A Redis/KV provider bean must not connect, ping, authenticate, create keys, or fail because Redis is unavailable during construction.
- A Redis/KV provider must not perform Redis network I/O from `@PostConstruct`, eager lifecycle callbacks, or `isEnabled()`.
- `isEnabled()` should be configuration-state only, or otherwise degrade through the provider operation path without blocking Spring context startup.
- Redis network I/O belongs in provider operations: `lookup`, `store`, `evict`, and any provider-owned index cleanup.
- Runtime Redis failures should surface as `RuntimeException` from provider operations. With the default `fail-on-provider-unavailable=false`, `PivotOuterCacheSafeProvider` converts them to miss/no-op/zero-result behavior.
- Redis invalidation publishing must be runtime-only. Broadcaster construction and auto-configuration must not connect to Redis, and publish failure should return an aggregate invalidation result with diagnostics rather than blocking local eviction.
- Redis invalidation listener startup must not block Spring context startup or Pivot query execution. If Redis is absent or unreachable, the listener lifecycle should log a warning and remain non-running until the context/lifecycle is restarted or host supervision starts it after Redis recovers.
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
| Invalidation fan-out boundary | Optional Redis invalidation implementations preserve `PivotOuterCacheInvalidationEvent` scope semantics, default-namespace preservation, explicit-event replay dedupe, source-node self-loop filtering, partial publish failure reporting, no constructor Redis I/O, listener disablement/backoff, and startup-safe listener behavior when Redis is unavailable. |
| Response diagnostics | Real Pivot execution with an unavailable provider returns normal query results and emits `pivot.cache.provider_unavailable` plus `provider_unavailable` miss/store-skip diagnostics. |
| No false store diagnostics | Provider failures during payload estimation, TTL lookup, or store do not emit `pivot.cache.store`; they emit `pivot.cache.provider_unavailable` plus `pivot.cache.store_skipped(reason=provider_unavailable)`. |

## Current Provider Fixture

- Module: `addons/foggy-dataset-model-cache`.
- Provider: `RedisPivotOuterCacheProvider`.
- Storage adapter: `StringRedisTemplatePivotOuterCacheStore`.
- Auto-configuration: `PivotOuterCacheRedisAutoConfiguration`, registered through addon `spring.factories`; Redis provider bean creation is isolated behind nested `StringRedisTemplate` class/bean conditions.
- Opt-in property: `foggy.dataset.pivot.outer-cache.redis.enabled=true`.
- Invalidation primitives: `RedisPivotOuterCacheInvalidationCodec`, `RedisPivotOuterCacheInvalidationBroadcaster`, `RedisPivotOuterCacheInvalidationConsumer`, `RedisPivotOuterCacheInvalidationMessageListener`, `RedisPivotOuterCacheInvalidationListenerLifecycle`, and `PivotOuterCacheRedisInvalidationAutoConfiguration`.
- Invalidation opt-in property: `foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true`.
- Invalidation channel/node configuration: `foggy.dataset.pivot.outer-cache.redis.invalidation-channel`, `node-id`, `replay-window-millis`, and `replay-window-maximum-entries`. If no channel is configured, the addon uses `${key-prefix}:invalidation`.
- Invalidation listener configuration: `foggy.dataset.pivot.outer-cache.redis.invalidation-listener-enabled`, `invalidation-listener-auto-startup`, and `invalidation-listener-recovery-interval-millis`.
- Core dependency boundary: `foggy-dataset-model` still has no Redis dependency, Redis client, or Redis auto-configuration requirement.
- Payload storage: Base64-encoded distributed JSON payload bytes in Redis string values.
- Index storage: Redis sets keyed by the distributed provider contract namespace/model index layout.
- Verified locally: constructor has no Redis interaction, disabled provider does not touch storage, `estimatePayloadBytes` does not touch storage, copy isolation holds, TTL expiry removes stale payloads, namespace/model eviction follows adapter indexes, invalid Base64 payloads are removed as misses, auto-config covers opt-in/backoff plus no Redis data-operation interaction during provider construction, invalid Redis endpoints do not block Spring context startup, unavailable Redis operations downgrade through `PivotOuterCacheSafeProvider` by default, explicit fail-fast rethrows, Redis invalidation publishing preserves event metadata and default namespace scope, publish failures are reported as partial invalidation results, consumer replay/self-loop/invalid-payload boundaries are covered, listener forwarding/invalid-payload handling is covered, listener auto-configuration is disable-able and startup-safe against an invalid Redis endpoint, a local Docker Redis run verifies live store/hit/evict/index/TTL/corrupt-payload behavior, and a local Docker Redis Pub/Sub run verifies publisher-to-listener invalidation reaches a remote node while skipping the source-node self-loop.
- Still required for production promotion: production Redis deployment evidence, production listener rollout evidence, cross-process race/replay evidence, and operational rollout evidence under real namespace/model registry refresh traffic.

## Live Redis Local Evidence

The live test is opt-in so default developer and CI runs do not require Redis:

```bash
docker run -d --rm --name foggy-pivot-redis-live -p 16379:6379 redis:7-alpine
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-dataset-model-cache -am -Dtest='RedisPivotOuterCacheProviderLiveTest,RedisPivotOuterCacheInvalidationLiveTest' -Dfoggy.redis.live.enabled=true -Dfoggy.redis.live.host=127.0.0.1 -Dfoggy.redis.live.port=16379 -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

The default addon test run includes `RedisPivotOuterCacheProviderLiveTest` and `RedisPivotOuterCacheInvalidationLiveTest` but skips them unless `foggy.redis.live.enabled=true`.

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
- Operators can use the addon listener auto-wiring when a Spring `RedisConnectionFactory` is present, or disable it with `invalidation-listener-enabled=false` when the host application owns Redis subscription. Redis absence or an unreachable endpoint must leave the listener non-running rather than failing the engine.
- If Redis is mandatory for a specific deployment, the deployment should opt into `fail-on-provider-unavailable=true` and own the resulting startup/query failure boundary.

## Current Engine Evidence

| Command | Result |
|---|---|
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotDiagnosticCollectorTest,PivotOuterCacheStartupSafetyTest,PivotOuterCacheSafeProviderTest,PivotIntegrationTest#testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b+testOuterCacheHitFlatPivotE1b+testOuterCacheSkipsWarningResponsesE1b' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false clean test` | success; provider-unavailable diagnostic contract, no-Redis startup safety, safe-provider one-shot unavailable event, real Pivot unavailable-provider downgrade, E1b cache hit, and warning store-skip behavior pass together; Tests run: 11, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheStartupSafetyTest,PivotOuterCacheSafeProviderTest,PivotIntegrationTest#testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b+testOuterCacheStoreUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCacheHitFlatPivotE1b+testOuterCacheSkipsWarningResponsesE1b,PivotOuterCacheDistributedProviderAdapterTest,PivotOuterCacheDistributedProviderContractTest,PivotOuterCacheInvalidationReplayWindowTest,PivotOuterCacheInvalidationEventTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest,PivotOuterCacheOperationalSpiTest,PivotOuterResponseCacheTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; no-Redis startup safety, safe-provider downgrade, store-stage provider failure no-false-store diagnostics, distributed adapter contract, invalidation/admin/bundle lifecycle, operational SPI, and local provider contracts pass together; Tests run: 60, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotIntegrationTest#testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b+testOuterCacheStoreUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCachePayloadEstimateUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCacheTtlUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCacheHitFlatPivotE1b+testOuterCacheSkipsWarningResponsesE1b,PivotOuterCacheSafeProviderTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; provider unavailable, store failure, payload-estimate failure, TTL failure, cache hit, warning store-skip, and safe-provider one-shot unavailable behavior pass together; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-dataset-model-cache -am -Dtest='RedisPivotOuterCacheProviderTest,PivotOuterCacheRedisAutoConfigurationTest,RedisPivotOuterCacheUnavailableTest,RedisPivotOuterCacheProviderLiveTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; optional addon Redis provider fixture verifies no constructor I/O, disabled-state no store access, response copy isolation, TTL expiry, namespace/model eviction, invalid Base64 cleanup, payload byte estimation without store access, auto-config opt-in/backoff with no Redis data operations during provider construction, invalid-endpoint startup safety, safe-provider downgrade, and explicit fail-fast behavior; Tests run: 16, Failures: 0, Errors: 0, Skipped: 3. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-dataset-model-cache -am -Dtest='RedisPivotOuterCacheProviderTest,PivotOuterCacheRedisAutoConfigurationTest,RedisPivotOuterCacheUnavailableTest,RedisPivotOuterCacheProviderLiveTest,RedisPivotOuterCacheInvalidationBroadcasterTest,RedisPivotOuterCacheInvalidationConsumerTest,RedisPivotOuterCacheInvalidationMessageListenerTest,RedisPivotOuterCacheInvalidationLiveTest,PivotOuterCacheRedisInvalidationAutoConfigurationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; existing Redis provider fixture and Redis invalidation primitives pass together. Invalidation tests cover no constructor Redis publish, local eviction plus event publish, explicit metadata preservation, default namespace scope preservation, publish failure partial result, missing local service still publishing, consumer payload decode, explicit-event replay dedupe, source-node self-loop skip, invalid payload diagnostics, message-listener forwarding and invalid payload isolation, listener auto-config disablement, listener no-start construction/backoff, invalid Redis endpoint startup safety, and a default-skipped live Pub/Sub invalidation path; Tests run: 36, Failures: 0, Errors: 0, Skipped: 4. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-dataset-model-cache -am -Dtest='RedisPivotOuterCacheProviderLiveTest,RedisPivotOuterCacheInvalidationLiveTest' -Dfoggy.redis.live.enabled=true -Dfoggy.redis.live.host=127.0.0.1 -Dfoggy.redis.live.port=16379 -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success against local Docker `redis:7-alpine` on `127.0.0.1:16379`; live Redis provider stores, hits from another provider instance, preserves copy isolation, evicts by namespace/model and all-scope indexes, expires TTL entries, removes corrupt payloads as misses, and live Redis Pub/Sub invalidation delivers a namespace/model eviction event from a source node to a remote listener while the source-node self-loop is skipped; Tests run: 4, Failures: 0, Errors: 0, Skipped: 0. |
| `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-model,addons/foggy-dataset-model-cache -am -P'!multi-db' -Dspring.profiles.active=sqlite -Dtest='PivotOuterCacheStartupSafetyTest,PivotOuterCacheSafeProviderTest,PivotIntegrationTest#testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b+testOuterCacheStoreUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCachePayloadEstimateUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCacheTtlUnavailableDoesNotEmitStoredDiagnosticsE1b+testOuterCacheHitFlatPivotE1b+testOuterCacheSkipsWarningResponsesE1b,PivotOuterCacheDistributedProviderAdapterTest,PivotOuterCacheDistributedProviderContractTest,PivotOuterCacheInvalidationReplayWindowTest,PivotOuterCacheInvalidationEventTest,PivotOuterCacheInvalidationFanOutContractTest,PivotOuterCacheAdminControllerTest,BundleLifecycleListenerTest,PivotOuterCacheOperationalSpiTest,PivotOuterResponseCacheTest,RedisPivotOuterCacheProviderTest,PivotOuterCacheRedisAutoConfigurationTest,RedisPivotOuterCacheUnavailableTest,RedisPivotOuterCacheProviderLiveTest,RedisPivotOuterCacheInvalidationBroadcasterTest,RedisPivotOuterCacheInvalidationConsumerTest,RedisPivotOuterCacheInvalidationMessageListenerTest,PivotOuterCacheRedisInvalidationAutoConfigurationTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` | success; core no-Redis startup/safe-provider/diagnostic/contract suites, addon Redis provider fixture, and Redis invalidation publish/consume/listen primitives pass together without requiring a live Redis service; Core tests run: 62, Addon tests run: 35, Failures: 0, Errors: 0, Skipped: 3. |

## Links

- `PIVOT-outer-cache-design.md` owns the engine-side cache SPI and default-off E1b behavior.
- `PIVOT-production-telemetry-diagnostics.md` owns the production diagnostics consumption guidance.
