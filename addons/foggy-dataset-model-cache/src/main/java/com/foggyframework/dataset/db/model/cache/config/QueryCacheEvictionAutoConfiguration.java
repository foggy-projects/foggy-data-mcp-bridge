package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.eviction.CacheEvictionAspect;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration(after = QueryCacheAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.aspectj.lang.JoinPoint",
        "org.aspectj.lang.ProceedingJoinPoint",
        "org.aspectj.lang.annotation.Aspect"
})
@ConditionalOnProperty(
        prefix = "foggy.query-cache",
        name = {"enabled", "eviction.enabled"},
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(QueryCacheProvider.class)
public class QueryCacheEvictionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheEvictionAspect.class)
    public CacheEvictionAspect cacheEvictionAspect(QueryCacheProvider queryCacheProvider) {
        log.info("Initializing Cache Eviction AOP aspect");
        return new CacheEvictionAspect(queryCacheProvider);
    }
}
