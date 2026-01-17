package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.cache.provider.CaffeineQueryCacheProvider;
import com.foggyframework.dataset.db.model.cache.provider.RedisQueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

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
@Configuration
@EnableConfigurationProperties(QueryCacheProperties.class)
@ConditionalOnProperty(name = "foggy.query-cache.enabled", havingValue = "true", matchIfMissing = true)
public class QueryCacheAutoConfiguration {

    /**
     * 查询指纹构建器
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryFingerprintBuilder queryFingerprintBuilder() {
        return new QueryFingerprintBuilder();
    }

    /**
     * Redis 缓存配置
     */
    @Configuration
    @ConditionalOnProperty(name = "foggy.query-cache.type", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnClass(RedisTemplate.class)
    static class RedisQueryCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(QueryCacheProvider.class)
        public RedisQueryCacheProvider redisQueryCacheProvider(
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
    static class CaffeineQueryCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(QueryCacheProvider.class)
        public CaffeineQueryCacheProvider caffeineQueryCacheProvider(
                QueryFingerprintBuilder fingerprintBuilder,
                QueryCacheProperties properties) {
            log.info("Initializing Caffeine query cache provider");
            return new CaffeineQueryCacheProvider(fingerprintBuilder, properties);
        }
    }
}
