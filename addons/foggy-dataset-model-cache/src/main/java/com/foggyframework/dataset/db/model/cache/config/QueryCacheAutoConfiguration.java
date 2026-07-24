package com.foggyframework.dataset.db.model.cache.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.cache.provider.CaffeineQueryCacheProvider;
import com.foggyframework.dataset.db.model.cache.provider.RedisQueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 查询缓存自动配置
 * <p>
 * 根据配置和可用依赖自动选择缓存实现：
 * <ul>
 *   <li>type=redis 且 RedisTemplate 可用 → RedisQueryCacheProvider</li>
 *   <li>type=caffeine 且 Caffeine 可用 → CaffeineQueryCacheProvider</li>
 *   <li>其他情况 → 不注册 Provider（使用默认的 NoOp）</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@AutoConfiguration(
        afterName = {
                "com.foggyframework.dataset.db.model.DbModelAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        })
@EnableConfigurationProperties(QueryCacheProperties.class)
@ConditionalOnProperty(name = "foggy.query-cache.enabled", havingValue = "true", matchIfMissing = true)
public class QueryCacheAutoConfiguration {

    /**
     * Redis 缓存配置
     */
    @Configuration
    @ConditionalOnProperty(name = "foggy.query-cache.type", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnClass({RedisTemplate.class, GenericJackson2JsonRedisSerializer.class})
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(QueryCacheProvider.class)
    static class RedisQueryCacheConfiguration {

        static final String CACHE_REDIS_TEMPLATE_BEAN = "foggyQueryCacheRedisTemplate";

        /**
         * Query results are not Java-serializable, so the Boot default
         * RedisTemplate value serializer cannot safely be reused. Keep a
         * dedicated template whose wire format is stable across JVM restarts.
         */
        @Bean(name = CACHE_REDIS_TEMPLATE_BEAN)
        @ConditionalOnMissingBean(name = CACHE_REDIS_TEMPLATE_BEAN)
        public RedisTemplate<String, Object> foggyQueryCacheRedisTemplate(
                RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            StringRedisSerializer keySerializer = new StringRedisSerializer();
            GenericJackson2JsonRedisSerializer valueSerializer =
                    new GenericJackson2JsonRedisSerializer()
                            .configure(mapper -> mapper.disable(
                                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
            template.setKeySerializer(keySerializer);
            template.setHashKeySerializer(keySerializer);
            template.setValueSerializer(valueSerializer);
            template.setHashValueSerializer(valueSerializer);
            return template;
        }

        @Bean
        @ConditionalOnMissingBean(QueryFingerprintBuilder.class)
        public QueryFingerprintBuilder queryFingerprintBuilder() {
            return new QueryFingerprintBuilder();
        }

        @Bean
        public RedisQueryCacheProvider redisQueryCacheProvider(
                @Qualifier(CACHE_REDIS_TEMPLATE_BEAN)
                RedisTemplate<String, Object> redisTemplate,
                QueryFingerprintBuilder fingerprintBuilder,
                QueryCacheProperties properties) {
            log.info("Initializing Redis query cache provider");
            return new RedisQueryCacheProvider(redisTemplate, fingerprintBuilder, properties);
        }
    }

    /**
     * Caffeine 缓存配置
     */
    @Configuration
    @ConditionalOnProperty(name = "foggy.query-cache.type", havingValue = "caffeine")
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnMissingBean(QueryCacheProvider.class)
    static class CaffeineQueryCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(QueryFingerprintBuilder.class)
        public QueryFingerprintBuilder queryFingerprintBuilder() {
            return new QueryFingerprintBuilder();
        }

        @Bean
        public CaffeineQueryCacheProvider caffeineQueryCacheProvider(
                QueryFingerprintBuilder fingerprintBuilder,
                QueryCacheProperties properties) {
            log.info("Initializing Caffeine query cache provider");
            return new CaffeineQueryCacheProvider(fingerprintBuilder, properties);
        }
    }

}
