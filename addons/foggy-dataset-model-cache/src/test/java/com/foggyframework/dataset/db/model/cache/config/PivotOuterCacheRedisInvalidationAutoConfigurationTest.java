package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationConsumer;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationListenerLifecycle;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationMessageListener;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PivotOuterCacheRedisInvalidationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PivotOuterCacheRedisInvalidationAutoConfiguration.class);

    @Test
    @DisplayName("Redis invalidation auto configuration is opt-in")
    void redisInvalidationAutoConfigurationIsOptIn() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertTrue(context.getBeansOfType(PivotOuterCacheInvalidationBroadcaster.class).isEmpty());
                    assertTrue(context.getBeansOfType(RedisPivotOuterCacheInvalidationConsumer.class).isEmpty());
                });
    }

    @Test
    @DisplayName("Redis invalidation auto configuration creates beans without touching Redis")
    void createsInvalidationBeansWithoutTouchingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        contextRunner
                .withPropertyValues(
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.key-prefix=foggy:test:pivot",
                        "foggy.dataset.pivot.outer-cache.redis.node-id=node-a")
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .run(context -> {
                    RedisPivotOuterCacheInvalidationBroadcaster broadcaster =
                            context.getBean(RedisPivotOuterCacheInvalidationBroadcaster.class);
                    RedisPivotOuterCacheInvalidationConsumer consumer =
                            context.getBean(RedisPivotOuterCacheInvalidationConsumer.class);
                    assertEquals("foggy:test:pivot:invalidation", broadcaster.channel());
                    assertEquals("node-a", broadcaster.localNodeId());
                    assertEquals("node-a", consumer.localNodeId());
                    assertTrue(context.getBeansOfType(RedisMessageListenerContainer.class).isEmpty());
                    verify(redisTemplate, never()).convertAndSend(anyString(), any());
                });
    }

    @Test
    @DisplayName("Redis invalidation auto configuration creates a listener container when a connection factory exists")
    void createsListenerContainerWhenConnectionFactoryExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withPropertyValues(
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-auto-startup=false",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-channel=pivot:custom",
                        "foggy.dataset.pivot.outer-cache.redis.node-id=node-a")
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(RedisConnectionFactory.class, () -> redisConnectionFactory)
                .run(context -> {
                    assertTrue(context.containsBean("redisPivotOuterCacheInvalidationListenerContainer"));
                    assertTrue(context.containsBean("redisPivotOuterCacheInvalidationMessageListener"));
                    RedisMessageListenerContainer container =
                            context.getBean("redisPivotOuterCacheInvalidationListenerContainer",
                                    RedisMessageListenerContainer.class);
                    RedisPivotOuterCacheInvalidationListenerLifecycle lifecycle =
                            context.getBean(RedisPivotOuterCacheInvalidationListenerLifecycle.class);
                    assertFalse(container.isAutoStartup());
                    assertFalse(lifecycle.isAutoStartup());
                    verifyNoInteractions(redisConnectionFactory);
                    verify(redisTemplate, never()).convertAndSend(anyString(), any());
                });
    }

    @Test
    @DisplayName("Redis invalidation listener container can be disabled while keeping consumer primitives")
    void listenerContainerCanBeDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        contextRunner
                .withPropertyValues(
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-enabled=false")
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(RedisConnectionFactory.class, () -> redisConnectionFactory)
                .run(context -> {
                    assertTrue(context.getBeansOfType(RedisPivotOuterCacheInvalidationConsumer.class)
                            .containsKey("redisPivotOuterCacheInvalidationConsumer"));
                    assertTrue(context.getBeansOfType(RedisPivotOuterCacheInvalidationMessageListener.class)
                            .containsKey("redisPivotOuterCacheInvalidationMessageListener"));
                    assertTrue(context.getBeansOfType(RedisMessageListenerContainer.class).isEmpty());
                    assertTrue(context.getBeansOfType(RedisPivotOuterCacheInvalidationListenerLifecycle.class)
                            .isEmpty());
                    verifyNoInteractions(redisConnectionFactory);
                });
    }

    @Test
    @DisplayName("Redis invalidation listener auto configuration does not fail startup for an unavailable endpoint")
    void listenerAutoConfigurationStartsAgainstInvalidRedisEndpoint() {
        int port = unusedPort();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RedisAutoConfiguration.class,
                        PivotOuterCacheRedisInvalidationAutoConfiguration.class))
                .withPropertyValues(
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + port,
                        "spring.data.redis.timeout=100ms",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-recovery-interval-millis=100")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.containsBean("redisPivotOuterCacheInvalidationListenerContainer"));
                    assertTrue(context.containsBean("redisPivotOuterCacheInvalidationListenerLifecycle"));
                    assertTrue(context.containsBean("redisPivotOuterCacheInvalidationConsumer"));
                    assertFalse(context.getBean(RedisPivotOuterCacheInvalidationListenerLifecycle.class)
                            .isRunning());
                });
    }

    @Test
    @DisplayName("Redis invalidation auto configuration backs off existing broadcaster")
    void backsOffExistingBroadcaster() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PivotOuterCacheInvalidationBroadcaster existing = (namespace, model) -> 0;

        contextRunner
                .withPropertyValues("foggy.dataset.pivot.outer-cache.redis.invalidation-enabled=true")
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(PivotOuterCacheInvalidationBroadcaster.class, () -> existing)
                .run(context -> {
                    assertSame(existing, context.getBean(PivotOuterCacheInvalidationBroadcaster.class));
                    assertTrue(context.getBeansOfType(RedisPivotOuterCacheInvalidationBroadcaster.class).isEmpty());
                    verify(redisTemplate, never()).convertAndSend(anyString(), any());
                });
    }

    private int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate unused local port", e);
        }
    }
}
