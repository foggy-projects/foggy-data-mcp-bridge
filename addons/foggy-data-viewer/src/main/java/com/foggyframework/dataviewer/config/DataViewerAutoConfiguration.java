package com.foggyframework.dataviewer.config;

import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.mcp.OpenInViewerTool;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * 数据浏览器自动配置
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataViewerProperties.class)
@EnableMongoRepositories(basePackages = "com.foggyframework.dataviewer.repository")
@ComponentScan(basePackages = "com.foggyframework.dataviewer")
public class DataViewerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueryCacheService queryCacheService(CachedQueryRepository repository,
                                                DataViewerProperties properties) {
        return new QueryCacheService(repository, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryScopeConstraintService queryScopeConstraintService(DataViewerProperties properties) {
        return new QueryScopeConstraintService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenInViewerTool openInViewerTool(QueryCacheService cacheService,
                                              QueryScopeConstraintService constraintService,
                                              DataViewerProperties properties) {
        return new OpenInViewerTool(cacheService, constraintService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerApiController viewerApiController(QueryCacheService cacheService) {
        return new ViewerApiController(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerPageController viewerPageController() {
        return new ViewerPageController();
    }
}
