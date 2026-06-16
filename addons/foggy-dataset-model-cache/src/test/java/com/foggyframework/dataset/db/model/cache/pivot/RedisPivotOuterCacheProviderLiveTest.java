package com.foggyframework.dataset.db.model.cache.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheProvider;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisPivotOuterCacheProviderLiveTest {

    private final Set<LettuceConnectionFactory> connectionFactories = new LinkedHashSet<>();
    private StringRedisTemplate redisTemplate;
    private String keyPrefix;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.getBoolean("foggy.redis.live.enabled"),
                "Set -Dfoggy.redis.live.enabled=true to run live Redis tests");
        redisTemplate = redisTemplate(redisHost(), redisPort());
        keyPrefix = "foggy:live:pivot:" + UUID.randomUUID();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            assertEquals("PONG", connection.ping());
        } finally {
            connection.close();
        }
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null && keyPrefix != null) {
            Set<String> keys = redisTemplate.keys(keyPrefix + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        for (LettuceConnectionFactory connectionFactory : connectionFactories) {
            connectionFactory.destroy();
        }
        connectionFactories.clear();
    }

    @Test
    @DisplayName("live Redis provider stores hits evicts and shares data across instances")
    void storesHitsEvictsAndSharesAcrossInstances() {
        RedisPivotOuterCacheProvider writer = provider(60_000L);
        RedisPivotOuterCacheProvider reader = provider(60_000L);
        long now = System.currentTimeMillis();
        SemanticQueryResponse original = response("stable");

        writer.store("copy", original, now, "ns-a", "ModelA");
        original.getItems().get(0).put("value", "mutated-original");

        PivotOuterCacheProvider.LookupResult firstLookup = reader.lookup("copy", now + 1L);
        assertTrue(firstLookup.hit());
        assertEquals("stable", firstLookup.response().getItems().get(0).get("value"));
        firstLookup.response().getItems().get(0).put("value", "mutated-lookup");

        PivotOuterCacheProvider.LookupResult secondLookup = writer.lookup("copy", now + 2L);
        assertTrue(secondLookup.hit());
        assertNotSame(firstLookup.response(), secondLookup.response());
        assertEquals("stable", secondLookup.response().getItems().get(0).get("value"));

        assertEquals(1, reader.evict("ns-a", "ModelA"));
        assertFalse(writer.lookup("copy", now + 3L).hit());
    }

    @Test
    @DisplayName("live Redis provider honors namespace model and all-scope indexes")
    void evictionHonorsRedisIndexes() {
        RedisPivotOuterCacheProvider provider = provider(60_000L);
        long now = System.currentTimeMillis();
        provider.store("default-model-a", response("default-model-a"), now, "", "ModelA");
        provider.store("ns-a-model-a", response("ns-a-model-a"), now + 1L, "ns-a", "ModelA");
        provider.store("ns-a-model-b", response("ns-a-model-b"), now + 2L, "ns-a", "ModelB");
        provider.store("ns-b-model-a", response("ns-b-model-a"), now + 3L, "ns-b", "ModelA");

        assertEquals(1, provider.evict("ns-a", "ModelA"));
        assertFalse(provider.lookup("ns-a-model-a", now + 4L).hit());
        assertTrue(provider.lookup("ns-a-model-b", now + 4L).hit());
        assertTrue(provider.lookup("ns-b-model-a", now + 4L).hit());

        assertEquals(2, provider.evict(null, "ModelA"));
        assertFalse(provider.lookup("default-model-a", now + 5L).hit());
        assertFalse(provider.lookup("ns-b-model-a", now + 5L).hit());
        assertTrue(provider.lookup("ns-a-model-b", now + 5L).hit());

        assertEquals(1, provider.evict(null, null));
        assertFalse(provider.lookup("ns-a-model-b", now + 6L).hit());
    }

    @Test
    @DisplayName("live Redis provider removes expired and invalid payloads")
    void removesExpiredAndInvalidPayloads() throws InterruptedException {
        RedisPivotOuterCacheProvider provider = provider(120L);
        long now = System.currentTimeMillis();

        provider.store("ttl", response("ttl"), now, "ns-a", "ModelA");
        assertTrue(provider.lookup("ttl", now + 1L).hit());
        Thread.sleep(180L);
        assertFalse(provider.lookup("ttl", System.currentTimeMillis()).hit());

        redisTemplate.opsForValue().set(keyPrefix + ":response:bad", "not-base64", Duration.ofSeconds(30L));
        assertFalse(provider.lookup("bad", System.currentTimeMillis()).hit());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + ":response:bad")));
    }

    private RedisPivotOuterCacheProvider provider(long ttlMillis) {
        return new RedisPivotOuterCacheProvider(redisTemplate, true, ttlMillis, keyPrefix);
    }

    private StringRedisTemplate redisTemplate(String host, int port) {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2L))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactories.add(connectionFactory);
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private String redisHost() {
        return System.getProperty("foggy.redis.live.host", "127.0.0.1");
    }

    private int redisPort() {
        return Integer.parseInt(System.getProperty("foggy.redis.live.port", "16379"));
    }

    private SemanticQueryResponse response(String value) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("metric", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("nested", nested);
        response.setItems(List.of(row));
        response.setTotal(1L);

        SemanticQueryResponse.DebugInfo debug = new SemanticQueryResponse.DebugInfo();
        debug.setExtra(new LinkedHashMap<>(Map.of("pivotEngineContract", Map.of("signed", true))));
        response.setDebug(debug);
        return response;
    }
}
