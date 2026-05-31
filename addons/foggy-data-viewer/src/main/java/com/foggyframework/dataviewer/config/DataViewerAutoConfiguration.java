package com.foggyframework.dataviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.controller.ListPresetController;
import com.foggyframework.dataviewer.controller.SavedQueryController;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.mcp.OpenInViewerTool;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataviewer.repository.ListPresetRepository;
import com.foggyframework.dataviewer.service.ListPresetService;
import com.foggyframework.dataviewer.service.MemberQueryService;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataviewer.service.SavedQueryService;
import com.foggyframework.dataviewer.service.listpreset.FallbackListPresetStore;
import com.foggyframework.dataviewer.service.listpreset.FileSystemListPresetStore;
import com.foggyframework.dataviewer.service.listpreset.ListPresetFieldValidator;
import com.foggyframework.dataviewer.service.listpreset.ListPresetStore;
import com.foggyframework.dataviewer.service.listpreset.MongoListPresetStore;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 数据浏览器自动配置
 * <p>
 * 集成 QueryFacade 和使用类型安全的请求类
 */
@AutoConfiguration(after = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
//@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataViewerProperties.class)
@EnableMongoRepositories(basePackages = "com.foggyframework.dataviewer.repository")
public class DataViewerAutoConfiguration {

    @Value("${server.port:8080}")
    private int serverPort;

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
                                              DataViewerProperties properties,
                                              ObjectMapper objectMapper) {
        return new OpenInViewerTool(cacheService, constraintService, properties, objectMapper, serverPort);
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

    @Bean
    @ConditionalOnMissingBean
    public SavedQueryService savedQueryService() {
        return new SavedQueryService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SavedQueryController savedQueryController(SavedQueryService savedQueryService) {
        return new SavedQueryController(savedQueryService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MongoListPresetStore mongoListPresetStore(ListPresetRepository repository) {
        return new MongoListPresetStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileSystemListPresetStore fileSystemListPresetStore(DataViewerProperties properties,
                                                                ObjectMapper objectMapper) {
        return new FileSystemListPresetStore(properties, objectMapper);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "listPresetStore")
    public ListPresetStore listPresetStore(ObjectProvider<MongoListPresetStore> mongoStore,
                                           FileSystemListPresetStore fileStore,
                                           DataViewerProperties properties,
                                           Environment environment) {
        DataViewerProperties.ListPresetProperties.Storage storage = properties.getListPreset().getStorage();
        if (!shouldUseMongoListPresetStore(storage, environment)) {
            return fileStore;
        }
        return new FallbackListPresetStore(mongoStore.getIfAvailable(), fileStore);
    }

    static boolean shouldUseMongoListPresetStore(DataViewerProperties.ListPresetProperties.Storage storage,
                                                 Environment environment) {
        if (storage == DataViewerProperties.ListPresetProperties.Storage.FILE) {
            return false;
        }
        if (storage == DataViewerProperties.ListPresetProperties.Storage.MONGO) {
            return true;
        }
        return StringUtils.hasText(environment.getProperty("spring.data.mongodb.uri"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ListPresetFieldValidator listPresetFieldValidator() {
        return ListPresetFieldValidator.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public ListPresetService listPresetService(ListPresetStore listPresetStore,
                                               ListPresetFieldValidator listPresetFieldValidator) {
        return new ListPresetService(listPresetStore, listPresetFieldValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public ListPresetController listPresetController(ListPresetService listPresetService) {
        return new ListPresetController(listPresetService);
    }
}
