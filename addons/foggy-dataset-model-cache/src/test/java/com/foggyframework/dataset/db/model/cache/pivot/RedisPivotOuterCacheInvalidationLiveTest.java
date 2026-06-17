package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

class RedisPivotOuterCacheInvalidationLiveTest {

    private final RedisPivotOuterCacheInvalidationCodec codec = new RedisPivotOuterCacheInvalidationCodec();
    private final List<LettuceConnectionFactory> connectionFactories = new ArrayList<>();
    private final List<RedisMessageListenerContainer> listenerContainers = new ArrayList<>();
    private final List<RedisPivotOuterCacheInvalidationListenerLifecycle> listenerLifecycles = new ArrayList<>();

    private StringRedisTemplate redisTemplate;
    private String channel;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.getBoolean("foggy.redis.live.enabled"),
                "Set -Dfoggy.redis.live.enabled=true to run live Redis tests");
        redisTemplate = redisTemplate(redisHost(), redisPort());
        channel = "foggy:live:pivot:invalidation:" + UUID.randomUUID();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            assertEquals("PONG", connection.ping());
        } finally {
            connection.close();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        for (RedisPivotOuterCacheInvalidationListenerLifecycle lifecycle : listenerLifecycles) {
            lifecycle.stop();
        }
        listenerLifecycles.clear();
        for (RedisMessageListenerContainer container : listenerContainers) {
            container.destroy();
        }
        listenerContainers.clear();
        for (LettuceConnectionFactory connectionFactory : connectionFactories) {
            connectionFactory.destroy();
        }
        connectionFactories.clear();
    }

    @Test
    @DisplayName("live Redis Pub/Sub invalidation reaches remote nodes and skips the source node")
    void pubSubInvalidationReachesRemoteAndSkipsSelfLoop() throws InterruptedException {
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

        startListener(sourceService, "node-a");
        startListener(remoteService, "node-b");

        RedisPivotOuterCacheInvalidationBroadcaster broadcaster = new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                provider(sourceService),
                codec,
                channel,
                "node-a");
        PivotOuterCacheInvalidationResult result = broadcaster.evict(
                PivotOuterCacheInvalidationEvent.of("ns-live", "ModelLive")
                        .withMetadata("evt-live", "node-a", System.currentTimeMillis()));

        assertTrue(result.success());
        assertEquals(2, result.removed());
        assertTrue(remoteInvalidated.await(5L, TimeUnit.SECONDS),
                "remote Redis invalidation listener did not receive the published event");
        assertEquals("ns-live", remoteNamespace.get());
        assertEquals("ModelLive", remoteModel.get());
        verify(remoteService).evictPivotOuterCache("ns-live", "ModelLive");
        verify(sourceService, after(500L).times(1)).evictPivotOuterCache("ns-live", "ModelLive");
    }

    private void startListener(SemanticQueryServiceV3 service, String localNodeId) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory(redisHost(), redisPort()));
        container.addMessageListener(
                new RedisPivotOuterCacheInvalidationMessageListener(
                        new RedisPivotOuterCacheInvalidationConsumer(
                                provider(service),
                                codec,
                                localNodeId,
                                60_000L,
                                1024)),
                new ChannelTopic(channel));
        container.afterPropertiesSet();
        listenerContainers.add(container);

        RedisPivotOuterCacheInvalidationListenerLifecycle lifecycle =
                new RedisPivotOuterCacheInvalidationListenerLifecycle(container, true);
        lifecycle.start();
        assertTrue(lifecycle.isRunning());
        listenerLifecycles.add(lifecycle);
    }

    private StringRedisTemplate redisTemplate(String host, int port) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory(host, port));
        template.afterPropertiesSet();
        return template;
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
        connectionFactories.add(connectionFactory);
        return connectionFactory;
    }

    private String redisHost() {
        return System.getProperty("foggy.redis.live.host", "127.0.0.1");
    }

    private int redisPort() {
        return Integer.parseInt(System.getProperty("foggy.redis.live.port", "16379"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SemanticQueryServiceV3> provider(SemanticQueryServiceV3 service) {
        ObjectProvider<SemanticQueryServiceV3> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
