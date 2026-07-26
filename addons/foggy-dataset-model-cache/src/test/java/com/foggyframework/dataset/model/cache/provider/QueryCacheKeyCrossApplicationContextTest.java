package com.foggyframework.dataset.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCacheKeyCrossApplicationContextTest {

    private static final String AUTHORIZATION = "Bearer cross-context-token";
    private static final String SQL = "SELECT id FROM orders WHERE id = ?";
    private static final List<Integer> PARAMS = List.of(42);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QueryCacheAutoConfiguration.class))
            .withPropertyValues("foggy.query-cache.type=caffeine");

    @Test
    void independentApplicationContextsShareOnlyCanonicalLifecycleKeys() {
        contextRunner.run(firstContext -> contextRunner.run(secondContext -> {
            assertNotSame(firstContext, secondContext);

            CaffeineQueryCacheProvider firstProvider =
                    firstContext.getBean(CaffeineQueryCacheProvider.class);
            CaffeineQueryCacheProvider secondProvider =
                    secondContext.getBean(CaffeineQueryCacheProvider.class);
            assertNotSame(firstProvider, secondProvider);

            QueryCacheKeyBuilder firstBuilder = keyBuilder(firstProvider);
            QueryCacheKeyBuilder secondBuilder = keyBuilder(secondProvider);
            assertNotSame(firstBuilder, secondBuilder);

            ModelResultContext firstBase = context("catalog:1", "binding:1");
            ModelResultContext secondBase = context("catalog:1", "binding:1");
            String firstL1 = firstBuilder.buildL1CacheKey(firstBase, AUTHORIZATION);
            String secondL1 = secondBuilder.buildL1CacheKey(secondBase, AUTHORIZATION);
            String firstL2 = firstBuilder.buildL2CacheKey("OrderModel", SQL, PARAMS, firstBase);
            String secondL2 = secondBuilder.buildL2CacheKey("OrderModel", SQL, PARAMS, secondBase);

            assertNotNull(firstL1);
            assertNotNull(secondL1);
            assertNotNull(firstL2);
            assertNotNull(secondL2);
            assertEquals(firstL1, secondL1);
            assertEquals(firstL2, secondL2);

            ModelResultContext catalogChanged = context("catalog:2", "binding:1");
            String catalogChangedL1 = secondBuilder.buildL1CacheKey(
                    catalogChanged, AUTHORIZATION);
            String catalogChangedL2 = secondBuilder.buildL2CacheKey(
                    "OrderModel", SQL, PARAMS, catalogChanged);
            assertNotNull(catalogChangedL1);
            assertNotNull(catalogChangedL2);
            assertNotEquals(firstL1, catalogChangedL1);
            assertNotEquals(firstL2, catalogChangedL2);

            ModelResultContext bindingChanged = context("catalog:1", "binding:2");
            String bindingChangedL1 = secondBuilder.buildL1CacheKey(
                    bindingChanged, AUTHORIZATION);
            String bindingChangedL2 = secondBuilder.buildL2CacheKey(
                    "OrderModel", SQL, PARAMS, bindingChanged);
            assertNotNull(bindingChangedL1);
            assertNotNull(bindingChangedL2);
            assertNotEquals(firstL1, bindingChangedL1);
            assertNotEquals(firstL2, bindingChangedL2);
        }));
    }

    private QueryCacheKeyBuilder keyBuilder(CaffeineQueryCacheProvider provider) {
        return assertInstanceOf(
                QueryCacheKeyBuilder.class,
                ReflectionTestUtils.getField(provider, "cacheKeyBuilder"));
    }

    private ModelResultContext context(String catalogGeneration, String bindingGeneration) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("OrderAlias");
        request.setColumns(List.of("id"));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(request);
        pagingRequest.setPage(1);
        pagingRequest.setPageSize(20);

        String namespace = "tenant-a";
        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace(namespace);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization(AUTHORIZATION)
                .userId("cross-context-user")
                .tenantId("cross-context-tenant")
                .roles(List.of("reader"))
                .build());
        context.setAuthorizationSignature(
                new AuthorizationSignature("PUBLIC:test", true, null));

        JdbcQueryModel model = mock(JdbcQueryModel.class);
        when(model.getName()).thenReturn("OrderModel");
        CatalogIdentity catalogIdentity = new CatalogIdentity(
                namespace,
                new CatalogGeneration(catalogGeneration),
                new SourceRevision("source:1"));
        DatasourceBindingIdentity bindingIdentity = new DatasourceBindingIdentity(
                "primary",
                "runtime-registry",
                new DatasourceBindingGeneration(bindingGeneration));
        context.pinCatalogResolution(
                new CatalogResolution<>(
                        "OrderModel",
                        model,
                        catalogIdentity,
                        Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                        true),
                namespace);
        return context;
    }
}
