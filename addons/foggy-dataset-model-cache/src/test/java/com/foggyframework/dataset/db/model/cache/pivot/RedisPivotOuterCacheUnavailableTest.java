package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.cache.config.PivotOuterCacheRedisAutoConfiguration;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheProvider;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheSafeProvider;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisPivotOuterCacheUnavailableTest {

    private final Set<LettuceConnectionFactory> connectionFactories = new LinkedHashSet<>();

    @AfterEach
    void tearDown() {
        for (LettuceConnectionFactory connectionFactory : connectionFactories) {
            connectionFactory.destroy();
        }
        connectionFactories.clear();
    }

    @Test
    @DisplayName("auto configuration starts against invalid Redis endpoint without connecting")
    void autoConfigurationStartsAgainstInvalidRedisEndpoint() {
        int port = unusedPort();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RedisAutoConfiguration.class,
                        PivotOuterCacheRedisAutoConfiguration.class))
                .withBean(DatasetProperties.class, () -> datasetProperties(true, 12_345L))
                .withPropertyValues(
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + port,
                        "spring.data.redis.timeout=100ms",
                        "foggy.dataset.pivot.outer-cache.redis.enabled=true",
                        "foggy.dataset.pivot.outer-cache.redis.key-prefix=foggy:invalid:pivot")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.containsBean("redisPivotOuterCacheProvider"));
                    RedisPivotOuterCacheProvider provider = context.getBean(RedisPivotOuterCacheProvider.class);
                    assertTrue(provider.isEnabled());
                    assertEquals(12_345L, provider.ttlMillis());
                });
    }

    @Test
    @DisplayName("unavailable Redis degrades through safe provider by default")
    void unavailableRedisDegradesThroughSafeProviderByDefault() {
        RedisPivotOuterCacheProvider delegate = unavailableProvider();
        PivotOuterCacheSafeProvider provider =
                (PivotOuterCacheSafeProvider) PivotOuterCacheSafeProvider.wrap(delegate, false);

        assertFalse(provider.lookup("missing", System.currentTimeMillis()).hit());
        assertUnavailable(provider.consumeLastUnavailable(), "lookup");

        provider.store("store", response("store"), System.currentTimeMillis(), "ns-a", "ModelA");
        assertUnavailable(provider.consumeLastUnavailable(), "store");

        assertEquals(0, provider.evict("ns-a", "ModelA"));
        assertUnavailable(provider.consumeLastUnavailable(), "evict");

        assertTrue(provider.estimatePayloadBytes(response("payload")) > 0);
        assertTrue(provider.consumeLastUnavailable().isEmpty());
    }

    @Test
    @DisplayName("unavailable Redis rethrows when fail-fast is configured")
    void unavailableRedisRethrowsWhenFailFastIsConfigured() {
        RedisPivotOuterCacheProvider delegate = unavailableProvider();
        PivotOuterCacheProvider provider = PivotOuterCacheSafeProvider.wrap(delegate, true);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> provider.lookup("missing", System.currentTimeMillis()));

        assertTrue(failure.getMessage().contains("lookup"));
        assertTrue(failure.getMessage().contains(RedisPivotOuterCacheProvider.CACHE_NAME));
    }

    private RedisPivotOuterCacheProvider unavailableProvider() {
        return new RedisPivotOuterCacheProvider(
                redisTemplate("127.0.0.1", unusedPort()),
                true,
                60_000L,
                "foggy:invalid:pivot");
    }

    private StringRedisTemplate redisTemplate(String host, int port) {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(150L))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactories.add(connectionFactory);
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private DatasetProperties datasetProperties(boolean enabled, long ttlMillis) {
        DatasetProperties properties = new DatasetProperties();
        properties.getPivot().getOuterCache().setEnabled(enabled);
        properties.getPivot().getOuterCache().setTtlMillis(ttlMillis);
        return properties;
    }

    private int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate unused local port", e);
        }
    }

    private void assertUnavailable(Optional<PivotOuterCacheSafeProvider.UnavailableEvent> event,
                                   String operation) {
        assertTrue(event.isPresent());
        assertEquals(operation, event.get().operation());
        assertEquals(RedisPivotOuterCacheProvider.CACHE_NAME, event.get().providerName());
    }

    private SemanticQueryResponse response(String value) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        response.setItems(List.of(row));
        response.setTotal(1L);
        return response;
    }
}
