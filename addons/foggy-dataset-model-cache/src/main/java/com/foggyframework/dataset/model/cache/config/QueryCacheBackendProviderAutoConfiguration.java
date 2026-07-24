package com.foggyframework.dataset.model.cache.config;

import com.foggyframework.dataset.model.cache.provider.QueryCacheBackendProvider;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Publishes the configured query-cache addon through the migrated SPI v2 surface. */
@AutoConfiguration(
        after = QueryCacheAutoConfiguration.class,
        beforeName = "com.foggyframework.dataset.model.starter.ModelBackendAutoConfiguration")
@ConditionalOnClass({CacheInvalidationBackendProvider.class, QueryCacheProvider.class})
public class QueryCacheBackendProviderAutoConfiguration {

    @Bean
    @ConditionalOnBean(QueryCacheProvider.class)
    @ConditionalOnMissingBean(name = "queryCacheBackendProvider")
    public QueryCacheBackendProvider queryCacheBackendProvider(QueryCacheProvider provider) {
        return new QueryCacheBackendProvider(provider);
    }
}
