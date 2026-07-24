package io.foggytest.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheBackendProviderAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheEvictionAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheWebAutoConfiguration;
import com.foggyframework.dataset.model.cache.controller.QueryCacheController;
import com.foggyframework.dataset.model.cache.eviction.CacheEvictionAspect;
import com.foggyframework.dataset.model.cache.provider.CaffeineQueryCacheProvider;
import com.foggyframework.dataset.model.cache.provider.QueryCacheBackendProvider;
import com.foggyframework.dataset.model.impl.mongo.TmMongoModelLoaderImpl;
import com.foggyframework.dataset.model.impl.vector.TmVectorModelLoaderImpl;
import com.foggyframework.dataset.model.mongo.MongoModelAutoConfiguration;
import com.foggyframework.dataset.model.preagg.config.PreAggAutoConfiguration;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshService;
import com.foggyframework.dataset.model.preagg.scheduler.PreAggScheduler;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.vector.VectorModelAutoConfiguration;
import com.foggyframework.dataset.graphql.GraphqlAddonAutoConfiguration;
import com.foggyframework.dataset.graphql.controller.GraphqlEndpointController;
import com.foggyframework.dataset.graphql.converter.GraphqlToDslConverter;
import com.foggyframework.dataset.mongo.DataSetMongoAutoConfiguration;
import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.starter.ModelBackendAutoConfiguration;
import com.foggyframework.dataset.vector.DataSetVectorAutoConfiguration;
import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FullAddonAutoConfigurationAssemblyTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSetMongoAutoConfiguration.class,
                    MongoModelAutoConfiguration.class,
                    DataSetVectorAutoConfiguration.class,
                    VectorModelAutoConfiguration.class,
                    QueryCacheAutoConfiguration.class,
                    QueryCacheBackendProviderAutoConfiguration.class,
                    QueryCacheEvictionAutoConfiguration.class,
                    QueryCacheWebAutoConfiguration.class,
                    ModelBackendAutoConfiguration.class,
                    GraphqlAddonAutoConfiguration.class,
                    PreAggAutoConfiguration.class))
            .withPropertyValues(
                    "foggy.dataset.mongo.enabled=true",
                    "foggy.vector.enabled=true",
                    "spring.ai.vectorstore.enabled=true",
                    "foggy.query-cache.enabled=true",
                    "foggy.query-cache.type=caffeine",
                    "foggy.query-cache.api.enabled=true",
                    "foggy.query-cache.eviction.enabled=true",
                    "foggy.dataset.graphql.enabled=true",
                    "foggy.preagg.enabled=true")
            .withBean(SystemBundlesContext.class, () -> mock(SystemBundlesContext.class))
            .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
            .withBean(FsscriptFileChangeHandler.class, () -> mock(FsscriptFileChangeHandler.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(MongoClient.class, () -> mock(MongoClient.class))
            .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
            .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
            .withBean(VectorStore.class, () -> mock(VectorStore.class))
            .withBean(AdvancedQueryFacade.class, () -> mock(AdvancedQueryFacade.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void allAddonsAssembleExactlyOnceWithoutExternalConnectionsOrBeanCycles() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasSingleBean(MongoFileFsscriptLoader.class);
            assertThat(context).hasSingleBean(TmMongoModelLoaderImpl.class);
            assertThat(context).hasSingleBean(VectorFileFsscriptLoader.class);
            assertThat(context).hasSingleBean(TmVectorModelLoaderImpl.class);

            assertThat(context).hasSingleBean(QueryCacheProvider.class);
            assertThat(context).hasSingleBean(CaffeineQueryCacheProvider.class);
            assertThat(context).hasSingleBean(QueryCacheBackendProvider.class);
            BackendProviderCatalog catalog = context.getBean(BackendProviderCatalog.class);
            assertThat(catalog.require(
                    QueryCacheBackendProvider.QUERY_CACHE,
                    BackendCapability.CACHE_INVALIDATION,
                    CacheInvalidationBackendProvider.class))
                    .isSameAs(context.getBean(QueryCacheBackendProvider.class));
            assertThat(context).hasSingleBean(QueryCacheController.class);
            assertThat(context).hasSingleBean(CacheEvictionAspect.class);

            assertThat(context).hasSingleBean(GraphqlToDslConverter.class);
            assertThat(context).hasSingleBean(GraphqlEndpointController.class);
            assertThat(context).hasSingleBean(PreAggRefreshService.class);
            assertThat(context).hasSingleBean(PreAggScheduler.class);
        });
    }
}
