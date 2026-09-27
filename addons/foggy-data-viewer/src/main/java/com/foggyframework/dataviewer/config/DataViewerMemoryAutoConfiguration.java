package com.foggyframework.dataviewer.config;

import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.plugins.LargeResultTruncationStep;
import com.foggyframework.dataviewer.service.InMemoryCachedQueryStore;
import com.foggyframework.dataviewer.service.MemberQueryService;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.config.DatasetProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Mongo-free, process-local DataViewer for development and test runtimes. */
@AutoConfiguration(afterName = "com.foggyframework.dataset.model.DbModelAutoConfiguration")
@ConditionalOnBean(QueryFacade.class)
@ConditionalOnExpression("${foggy.data-viewer.enabled:false}")
@ConditionalOnProperty(prefix = "foggy.data-viewer.cache", name = "store", havingValue = "memory")
@EnableConfigurationProperties(DataViewerProperties.class)
public class DataViewerMemoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public QueryCacheService queryCacheService(DataViewerProperties properties) {
        return new QueryCacheService(
                new InMemoryCachedQueryStore(properties.getCache().getMaxEntries()), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryScopeConstraintService queryScopeConstraintService(DataViewerProperties properties) {
        return new QueryScopeConstraintService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LargeResultTruncationStep largeResultTruncationStep(QueryCacheService cacheService,
                                                               DataViewerProperties properties) {
        return new LargeResultTruncationStep(cacheService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemberQueryService memberQueryService(QueryFacade queryFacade) {
        return new MemberQueryService(queryFacade);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerApiController viewerApiController(QueryCacheService cacheService,
                                                    QueryFacade queryFacade,
                                                    DatasetProperties datasetProperties,
                                                    MemberQueryService memberQueryService) {
        return new ViewerApiController(cacheService, queryFacade, datasetProperties, memberQueryService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerPageController viewerPageController() {
        return new ViewerPageController();
    }
}
