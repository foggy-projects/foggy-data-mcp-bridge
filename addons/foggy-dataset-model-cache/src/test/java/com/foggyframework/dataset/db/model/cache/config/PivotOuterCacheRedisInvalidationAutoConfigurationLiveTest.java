package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationListenerLifecycle;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PivotOuterCacheRedisInvalidationAutoConfigurationLiveTest {

    private String channel;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.getBoolean("foggy.redis.live.enabled"),
                "Set -Dfoggy.redis.live.enabled=true to run live Redis tests");
        assertLiveRedisAvailable();
        channel = "foggy:live:pivot:auto-invalidation:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("live Redis auto configured listener consumes invalidation across independent Spring contexts")
    void autoConfiguredListenerConsumesAcrossIndependentSpringContexts() throws InterruptedException {
        SemanticQueryServiceV3 sourceService = mock(SemanticQueryServiceV3.class);
        SemanticQueryServiceV3 remoteService = mock(SemanticQueryServiceV3.class);
        CountDownLatch remoteInvalidated = new CountDownLatch(1);
        AtomicReference<String> remoteNamespace = new AtomicReference<>();
        AtomicReference<String> remoteModel = new AtomicReference<>();

        when(sourceService.evictPivotOuterCache(anyString(), anyString())).thenReturn(2);
        when(remoteService.evictPivotOuterCache(anyString(), anyString())).thenAnswer(invocation -> {
            remoteNamespace.set(invocation.getArgument(0));
            remoteModel.set(invocation.getArgument(1));
            remoteInvalidated.countDown();
            return 3;
        });

        liveContext("node-b", remoteService).run(remoteContext ->
                liveContext("node-a", sourceService).run(sourceContext -> {
                    assertListenerRunning(remoteContext);
                    assertListenerRunning(sourceContext);
                    waitForSubscription();

                    RedisPivotOuterCacheInvalidationBroadcaster broadcaster =
                            sourceContext.getBean(RedisPivotOuterCacheInvalidationBroadcaster.class);
                    assertEquals(channel, broadcaster.channel());
                    assertEquals("node-a", broadcaster.localNodeId());

                    PivotOuterCacheInvalidationResult result = broadcaster.evict(
                            PivotOuterCacheInvalidationEvent.of("ns-auto-live", "ModelAutoLive")
                                    .withMetadata("evt-auto-config-live", "node-a", System.currentTimeMillis()));

                    assertTrue(result.success());
                    assertEquals(2, result.removed());
                    assertTrue(remoteInvalidated.await(5L, TimeUnit.SECONDS),
                            "remote auto-configured Redis listener did not receive the published event");
                    assertEquals("ns-auto-live", remoteNamespace.get());
                    assertEquals("ModelAutoLive", remoteModel.get());
                    verify(remoteService).evictPivotOuterCache("ns-auto-live", "ModelAutoLive");
                    verify(sourceService, after(500L).times(1))
                            .evictPivotOuterCache("ns-auto-live", "ModelAutoLive");
                }));
    }

    private ApplicationContextRunner liveContext(String nodeId, SemanticQueryServiceV3 service) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RedisAutoConfiguration.class,
                        PivotOuterCacheRedisInvalidationAutoConfiguration.class))
                .withPropertyValues(
                        "spring.data.redis.host=" + redisHost(),
                        "spring.data.redis.port=" + redisPort(),
                        "spring.data.redis.timeout=2s",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-channel=" + channel,
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-recovery-interval-millis=100",
                        "foggy.dataset.pivot.outer-cache.redis.node-id=" + nodeId)
                .withBean(SemanticQueryServiceV3.class, () -> service);
    }

    private void assertListenerRunning(AssertableApplicationContext context) {
        assertTrue(context.isRunning());
        assertTrue(context.containsBean("redisPivotOuterCacheInvalidationListenerContainer"));
        assertTrue(context.containsBean("redisPivotOuterCacheInvalidationListenerLifecycle"));
        assertTrue(context.getBean(RedisPivotOuterCacheInvalidationListenerLifecycle.class).isRunning());
        assertTrue(context.getBean("redisPivotOuterCacheInvalidationListenerContainer",
                RedisMessageListenerContainer.class).isRunning());
    }

    private void waitForSubscription() {
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for Redis listener subscriptions", e);
        }
    }

    private void assertLiveRedisAvailable() {
        LettuceConnectionFactory connectionFactory = connectionFactory(redisHost(), redisPort());
        RedisConnection connection = connectionFactory.getConnection();
        try {
            assertEquals("PONG", connection.ping());
        } finally {
            connection.close();
            connectionFactory.destroy();
        }
    }

    private LettuceConnectionFactory connectionFactory(String host, int port) {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2L))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private String redisHost() {
        return System.getProperty("foggy.redis.live.host", "127.0.0.1");
    }

    private int redisPort() {
        return Integer.parseInt(System.getProperty("foggy.redis.live.port", "16379"));
    }
}
