package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheProvider;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PivotOuterCacheRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PivotOuterCacheRedisAutoConfiguration.class);

    @Test
    @DisplayName("auto configuration is opt-in and does not create provider by default")
    void autoConfigurationIsOptIn() {
        contextRunner
                .withBean(DatasetProperties.class, DatasetProperties::new)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertTrue(context.getBeansOfType(PivotOuterCacheProvider.class).isEmpty()));
    }

    @Test
    @DisplayName("auto configuration backs off without dataset properties")
    void autoConfigurationBacksOffWithoutDatasetProperties() {
        contextRunner
                .withPropertyValues("foggy.dataset.pivot.outer-cache.redis.enabled=true")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertTrue(context.getBeansOfType(PivotOuterCacheProvider.class).isEmpty()));
    }

    @Test
    @DisplayName("auto configuration creates provider when explicitly enabled")
    void autoConfigurationCreatesProviderWhenEnabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        contextRunner
                .withPropertyValues(
                        "foggy.dataset.pivot.outer-cache.redis.enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.key-prefix=foggy:test:pivot")
                .withBean(DatasetProperties.class, () -> {
                    DatasetProperties properties = new DatasetProperties();
                    properties.getPivot().getOuterCache().setEnabled(true);
                    properties.getPivot().getOuterCache().setTtlMillis(12_345L);
                    return properties;
                })
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .run(context -> {
                    assertTrue(context.containsBean("redisPivotOuterCacheProvider"));
                    RedisPivotOuterCacheProvider provider = context.getBean(RedisPivotOuterCacheProvider.class);
                    assertTrue(provider.isEnabled());
                    assertEquals(12_345L, provider.ttlMillis());
                    assertEquals("foggy:test:pivot", provider.contract().keyPrefix());
                    verify(redisTemplate, never()).opsForValue();
                    verify(redisTemplate, never()).opsForSet();
                });
    }

    @Test
    @DisplayName("auto configuration creates provider without touching Redis")
    void createsProviderWithoutTouchingRedis() {
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getPivot().getOuterCache().setEnabled(true);
        datasetProperties.getPivot().getOuterCache().setTtlMillis(12_345L);
        PivotOuterCacheRedisProperties redisProperties = new PivotOuterCacheRedisProperties();
        redisProperties.setKeyPrefix("foggy:test:pivot");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        RedisPivotOuterCacheProvider provider = new PivotOuterCacheRedisAutoConfiguration.RedisProviderConfiguration()
                .redisPivotOuterCacheProvider(redisTemplate, datasetProperties, redisProperties);

        assertTrue(provider.isEnabled());
        assertEquals(12_345L, provider.ttlMillis());
        assertEquals("foggy:test:pivot", provider.contract().keyPrefix());
        verifyNoInteractions(redisTemplate);
    }
}
