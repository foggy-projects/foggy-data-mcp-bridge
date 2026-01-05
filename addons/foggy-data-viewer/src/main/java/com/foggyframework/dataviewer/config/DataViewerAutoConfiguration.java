package com.foggyframework.dataviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.mcp.OpenInViewerTool;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * 数据浏览器自动配置
 * <p>
 * 集成 QueryFacade 和使用类型安全的请求类
 */
@AutoConfiguration(after = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataViewerProperties.class)
@EnableMongoRepositories(basePackages = "com.foggyframework.dataviewer.repository")
public class DataViewerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueryCacheService queryCacheService(CachedQueryRepository repository,
                                                DataViewerProperties properties,
                                                QueryModelLoader queryModelLoader) {
        return new QueryCacheService(repository, properties, queryModelLoader);
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
                                              DataViewerProperties properties,
                                              ObjectMapper objectMapper) {
        return new OpenInViewerTool(cacheService, constraintService, properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerApiController viewerApiController(QueryCacheService cacheService,
                                                    QueryFacade queryFacade,
                                                    JdbcService jdbcService) {
        return new ViewerApiController(cacheService, queryFacade, jdbcService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewerPageController viewerPageController() {
        return new ViewerPageController();
    }
}
