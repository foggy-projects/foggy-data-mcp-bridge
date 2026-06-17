package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationConsumer;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
                    verify(redisTemplate, never()).convertAndSend(anyString(), any());
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
}
