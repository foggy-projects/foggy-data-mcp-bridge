package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheProvider;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "com.foggyframework.dataset.db.model.DbModelAutoConfiguration"
})
@EnableConfigurationProperties(PivotOuterCacheRedisProperties.class)
@ConditionalOnProperty(name = "foggy.dataset.pivot.outer-cache.redis.enabled", havingValue = "true")
public class PivotOuterCacheRedisAutoConfiguration {

    @Configuration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean({StringRedisTemplate.class, DatasetProperties.class})
    static class RedisProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(PivotOuterCacheProvider.class)
        public RedisPivotOuterCacheProvider redisPivotOuterCacheProvider(
                StringRedisTemplate redisTemplate,
                DatasetProperties datasetProperties,
                PivotOuterCacheRedisProperties redisProperties) {
            DatasetProperties.OuterCacheConfig outerCache = datasetProperties.getPivot().getOuterCache();
            log.info("Initializing Redis Pivot outer-cache provider, enabled={}, ttlMillis={}, keyPrefix={}",
                    outerCache.isEnabled(), outerCache.getTtlMillis(), redisProperties.getKeyPrefix());
            return new RedisPivotOuterCacheProvider(
                    redisTemplate,
                    outerCache.isEnabled(),
                    outerCache.getTtlMillis(),
                    redisProperties.getKeyPrefix());
        }
    }
}
