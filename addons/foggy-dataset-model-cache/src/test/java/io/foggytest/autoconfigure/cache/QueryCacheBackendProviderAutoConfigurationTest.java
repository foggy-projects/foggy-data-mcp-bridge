package io.foggytest.autoconfigure.cache;

import com.foggyframework.dataset.db.model.cache.config.QueryCacheAutoConfiguration;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheBackendProviderAutoConfiguration;
import com.foggyframework.dataset.db.model.cache.provider.QueryCacheBackendProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.starter.ModelBackendAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueryCacheBackendProviderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    QueryCacheAutoConfiguration.class,
                    QueryCacheBackendProviderAutoConfiguration.class,
                    ModelBackendAutoConfiguration.class));

    @Test
    void caffeineAddonIsPublishedThroughTheV2Catalog() {
        contextRunner.withPropertyValues("foggy.query-cache.type=caffeine")
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).hasSingleBean(QueryCacheBackendProvider.class);
                    BackendProviderCatalog catalog = context.getBean(BackendProviderCatalog.class);
                    CacheInvalidationBackendProvider provider = catalog.require(
                            QueryCacheBackendProvider.QUERY_CACHE,
                            BackendCapability.CACHE_INVALIDATION,
                            CacheInvalidationBackendProvider.class);
                    assertThat(provider).isSameAs(context.getBean(QueryCacheBackendProvider.class));
                });
    }

    @Test
    void disabledOrMissingLegacyProviderCreatesNoPartialV2Adapter() {
        contextRunner.withPropertyValues("foggy.query-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QueryCacheBackendProvider.class);
                    assertThat(context.getBean(BackendProviderCatalog.class).providers()).isEmpty();
                });
    }

    @Test
    void namedUserAdapterBacksOffDefaultBean() {
        QueryCacheProvider legacyProvider = mock(QueryCacheProvider.class);
        QueryCacheBackendProvider userAdapter = new QueryCacheBackendProvider(legacyProvider);
        contextRunner.withBean(QueryCacheProvider.class, () -> legacyProvider)
                .withBean("queryCacheBackendProvider", QueryCacheBackendProvider.class,
                        () -> userAdapter)
                .run(context -> assertThat(context.getBean(QueryCacheBackendProvider.class))
                        .isSameAs(userAdapter));
    }

    @Test
    void missingV2ApiDisablesCompatibilityAdapter() {
        contextRunner.withBean(QueryCacheProvider.class, () -> mock(QueryCacheProvider.class))
                .withClassLoader(new FilteredClassLoader(CacheInvalidationBackendProvider.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(QueryCacheBackendProvider.class));
    }
}
