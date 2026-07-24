package com.foggyframework.dataset.model.cache.config;

import com.foggyframework.dataset.model.cache.controller.QueryCacheController;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration(after = QueryCacheAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
@ConditionalOnProperty(
        prefix = "foggy.query-cache",
        name = {"enabled", "api.enabled"},
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(QueryCacheProvider.class)
@EnableConfigurationProperties(QueryCacheProperties.class)
public class QueryCacheWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(QueryCacheController.class)
    public QueryCacheController queryCacheController(
            QueryCacheProvider queryCacheProvider,
            QueryCacheProperties properties) {
        log.info("Initializing Query Cache REST API controller");
        return new QueryCacheController(queryCacheProvider, properties);
    }
}
