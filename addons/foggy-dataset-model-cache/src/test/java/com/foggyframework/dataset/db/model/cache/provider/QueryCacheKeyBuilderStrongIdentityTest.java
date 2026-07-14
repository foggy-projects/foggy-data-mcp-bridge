package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCacheKeyBuilderStrongIdentityTest {

    private static final String AUTHORIZATION = "Bearer cache-contract-token";
    private static final String SQL = "SELECT id FROM orders WHERE id = ?";

    private QueryCacheKeyBuilder keyBuilder;

    @BeforeEach
    void setUp() {
        QueryCacheProperties properties = new QueryCacheProperties();
        properties.setKeyPrefix("qc:");
        keyBuilder = new QueryCacheKeyBuilder(new QueryFingerprintBuilder(), properties);
    }

    @Test
    void independentContextsWithTheSameStrongIdentityProduceTheSameKeys() {
        ModelResultContext first = trackedJdbc(
                "OrderAlias", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        ModelResultContext second = trackedJdbc(
                "OrderAlias", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);

        assertEquals(keyBuilder.buildL1CacheKey(first, AUTHORIZATION),
                keyBuilder.buildL1CacheKey(second, AUTHORIZATION));
        assertEquals(keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), first),
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), second));
    }

    @Test
    void catalogOrBindingGenerationChangeChangesBothKeys() {
        ModelResultContext base = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        ModelResultContext catalogChanged = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:2", "binding:1", true);
        ModelResultContext bindingChanged = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:2", true);

        String baseL1 = keyBuilder.buildL1CacheKey(base, AUTHORIZATION);
        String baseL2 = keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), base);
        assertNotNull(baseL1);
        assertNotNull(baseL2);
        assertNotEquals(baseL1, keyBuilder.buildL1CacheKey(catalogChanged, AUTHORIZATION));
        assertNotEquals(baseL1, keyBuilder.buildL1CacheKey(bindingChanged, AUTHORIZATION));
        assertNotEquals(baseL2,
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), catalogChanged));
        assertNotEquals(baseL2,
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), bindingChanged));
    }

    @Test
    void sourceRevisionOrBackendIdChangeChangesBothKeys() {
        ModelResultContext base = tracked(
                "OrderModel",
                jdbcModel("OrderModel"),
                catalog("tenant-a", "catalog:1", "source:1"),
                bindings("runtime-registry", "binding:1"),
                true);
        ModelResultContext sourceChanged = tracked(
                "OrderModel",
                jdbcModel("OrderModel"),
                catalog("tenant-a", "catalog:1", "source:2"),
                bindings("runtime-registry", "binding:1"),
                true);
        ModelResultContext backendChanged = tracked(
                "OrderModel",
                jdbcModel("OrderModel"),
                catalog("tenant-a", "catalog:1", "source:1"),
                bindings("warehouse-registry", "binding:1"),
                true);

        String baseL1 = keyBuilder.buildL1CacheKey(base, AUTHORIZATION);
        String baseL2 = keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), base);
        assertNotEquals(baseL1, keyBuilder.buildL1CacheKey(sourceChanged, AUTHORIZATION));
        assertNotEquals(baseL1, keyBuilder.buildL1CacheKey(backendChanged, AUTHORIZATION));
        assertNotEquals(baseL2,
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), sourceChanged));
        assertNotEquals(baseL2,
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), backendChanged));
    }

    @Test
    void bindingInsertionOrderDoesNotChangeEitherKey() {
        DatasourceBindingIdentity primary = binding(
                "primary", "runtime-registry", "binding:primary:1");
        DatasourceBindingIdentity warehouse = binding(
                "warehouse", "warehouse-registry", "binding:warehouse:1");
        Map<String, DatasourceBindingIdentity> firstOrder = new LinkedHashMap<>();
        firstOrder.put(primary.bindingKey(), primary);
        firstOrder.put(warehouse.bindingKey(), warehouse);
        Map<String, DatasourceBindingIdentity> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put(warehouse.bindingKey(), warehouse);
        reverseOrder.put(primary.bindingKey(), primary);

        ModelResultContext first = tracked(
                "OrderModel", jdbcModel("OrderModel"),
                catalog("tenant-a", "catalog:1"), firstOrder, true);
        ModelResultContext second = tracked(
                "OrderModel", jdbcModel("OrderModel"),
                catalog("tenant-a", "catalog:1"), reverseOrder, true);

        assertEquals(keyBuilder.buildL1CacheKey(first, AUTHORIZATION),
                keyBuilder.buildL1CacheKey(second, AUTHORIZATION));
        assertEquals(keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), first),
                keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), second));
    }

    @Test
    void namespaceMismatchFailsClosedAtTheConsumerBoundary() {
        ModelResultContext context = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        context.setNamespace("tenant-b");

        assertNoKeys(context, "OrderModel");
    }

    @Test
    void canonicalModelMismatchFailsClosedAtTheConsumerBoundary() {
        JdbcQueryModel model = jdbcModel("OrderModel");
        ModelResultContext context = tracked(
                "OrderAlias", model, "tenant-a", "catalog:1", bindings("binding:1"), true);
        when(model.getName()).thenReturn("AnotherModel");

        assertNoKeys(context, "OrderModel");
    }

    @Test
    void missingOrIncompleteLifecycleIdentityFailsClosed() {
        JdbcQueryModel model = jdbcModel("OrderModel");
        ModelResultContext untracked = baseContext("OrderModel", "tenant-a");
        untracked.pinUntrackedQueryModel(model);
        ModelResultContext incomplete = tracked(
                "OrderModel", model, "tenant-a", "catalog:1", bindings("binding:1"), false);

        assertNoKeys(untracked, "OrderModel");
        assertNoKeys(incomplete, "OrderModel");
    }

    @Test
    void malformedBindingMapFailsClosedEvenAfterAValidPin() {
        ModelResultContext context = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        DatasourceBindingIdentity identity = context.getDatasourceBindingIdentities().get("primary");
        context.setDatasourceBindingIdentities(Map.of("wrong-key", identity));

        assertNoKeys(context, "OrderModel");
    }

    @Test
    void blankBackendOrNullGenerationFailsClosedAtTheConsumerBoundary() {
        DatasourceBindingIdentity blankBackend = mock(DatasourceBindingIdentity.class);
        when(blankBackend.bindingKey()).thenReturn("primary");
        when(blankBackend.backendId()).thenReturn(" ");
        when(blankBackend.generation()).thenReturn(
                new DatasourceBindingGeneration("binding:1"));
        ModelResultContext blankBackendContext = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        blankBackendContext.setDatasourceBindingIdentities(Map.of("primary", blankBackend));

        DatasourceBindingIdentity nullGeneration = mock(DatasourceBindingIdentity.class);
        when(nullGeneration.bindingKey()).thenReturn("primary");
        when(nullGeneration.backendId()).thenReturn("runtime-registry");
        when(nullGeneration.generation()).thenReturn(null);
        ModelResultContext nullGenerationContext = trackedJdbc(
                "OrderModel", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);
        nullGenerationContext.setDatasourceBindingIdentities(Map.of("primary", nullGeneration));

        assertNoKeys(blankBackendContext, "OrderModel");
        assertNoKeys(nullGenerationContext, "OrderModel");
    }

    @Test
    void jdbcRequiresABindingButConfirmedDatasourceFreeNonJdbcMayUseAnEmptySet() {
        JdbcQueryModel jdbcModel = jdbcModel("OrderModel");
        ModelResultContext jdbc = tracked(
                "OrderModel", jdbcModel, "tenant-a", "catalog:1", Map.of(), true);
        QueryModel datasourceFreeModel = queryModel("VectorModel");
        ModelResultContext datasourceFree = tracked(
                "VectorAlias", datasourceFreeModel, "tenant-a", "catalog:1", Map.of(), true);

        assertNoKeys(jdbc, "OrderModel");
        assertNotNull(keyBuilder.buildL1CacheKey(datasourceFree, AUTHORIZATION));
        assertNotNull(keyBuilder.buildL2CacheKey(
                "VectorModel", SQL, List.of(1), datasourceFree));
    }

    @Test
    void aliasIsAllowedForL1ButL2RequiresTheCanonicalModelName() {
        ModelResultContext context = trackedJdbc(
                "OrderAlias", "OrderModel", "tenant-a", "catalog:1", "binding:1", true);

        assertNotNull(keyBuilder.buildL1CacheKey(context, AUTHORIZATION));
        assertNotNull(keyBuilder.buildL2CacheKey("OrderModel", SQL, List.of(1), context));
        assertNull(keyBuilder.buildL2CacheKey("OrderAlias", SQL, List.of(1), context));
    }

    @Test
    void canonicalNameHasNoPublicSetterAndConflictingRepinPreservesIt() {
        assertThrows(NoSuchMethodException.class,
                () -> ModelResultContext.class.getMethod("setCanonicalModelName", String.class));

        QueryModel modelWithoutReportedName = mock(QueryModel.class);
        CatalogIdentity identity = catalog("tenant-a", "catalog:1");
        ModelResultContext context = baseContext("OrderAlias", "tenant-a");
        context.pinCatalogResolution(
                new CatalogResolution<>(
                        "OrderModel", modelWithoutReportedName, identity, Map.of(), true),
                "tenant-a");

        assertThrows(IllegalStateException.class,
                () -> context.pinCatalogResolution(
                        new CatalogResolution<>(
                                "AnotherModel", modelWithoutReportedName, identity, Map.of(), true),
                        "tenant-a"));
        assertEquals("OrderModel", context.getCanonicalModelName());
    }

    private void assertNoKeys(ModelResultContext context, String canonicalModelName) {
        assertNull(keyBuilder.buildL1CacheKey(context, AUTHORIZATION));
        assertNull(keyBuilder.buildL2CacheKey(
                canonicalModelName, SQL, List.of(1), context));
    }

    private ModelResultContext trackedJdbc(String requestedModelName,
                                           String canonicalModelName,
                                           String namespace,
                                           String catalogGeneration,
                                           String bindingGeneration,
                                           boolean complete) {
        return tracked(
                requestedModelName,
                jdbcModel(canonicalModelName),
                namespace,
                catalogGeneration,
                bindings(bindingGeneration),
                complete);
    }

    private ModelResultContext tracked(String requestedModelName,
                                       QueryModel model,
                                       String namespace,
                                       String catalogGeneration,
                                       Map<String, DatasourceBindingIdentity> bindings,
                                       boolean complete) {
        return tracked(
                requestedModelName,
                model,
                catalog(namespace, catalogGeneration),
                bindings,
                complete);
    }

    private ModelResultContext tracked(String requestedModelName,
                                       QueryModel model,
                                       CatalogIdentity catalogIdentity,
                                       Map<String, DatasourceBindingIdentity> bindings,
                                       boolean complete) {
        String namespace = catalogIdentity.namespace();
        ModelResultContext context = baseContext(requestedModelName, namespace);
        context.pinCatalogResolution(
                new CatalogResolution<>(
                        model.getName(),
                        model,
                        catalogIdentity,
                        bindings,
                        complete),
                namespace);
        return context;
    }

    private ModelResultContext baseContext(String requestedModelName, String namespace) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(requestedModelName);
        request.setColumns(List.of("id"));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(request);
        pagingRequest.setPage(1);
        pagingRequest.setPageSize(20);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace(namespace);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization(AUTHORIZATION)
                .userId("cache-user")
                .tenantId("cache-tenant")
                .roles(List.of("reader"))
                .build());
        return context;
    }

    private CatalogIdentity catalog(String namespace, String generation) {
        return catalog(namespace, generation, "source:1");
    }

    private CatalogIdentity catalog(String namespace, String generation, String sourceRevision) {
        return new CatalogIdentity(
                namespace,
                new CatalogGeneration(generation),
                new SourceRevision(sourceRevision));
    }

    private Map<String, DatasourceBindingIdentity> bindings(String generation) {
        return bindings("runtime-registry", generation);
    }

    private Map<String, DatasourceBindingIdentity> bindings(
            String backendId, String generation) {
        DatasourceBindingIdentity identity = binding("primary", backendId, generation);
        return Map.of(identity.bindingKey(), identity);
    }

    private DatasourceBindingIdentity binding(
            String bindingKey, String backendId, String generation) {
        return new DatasourceBindingIdentity(
                bindingKey,
                backendId,
                new DatasourceBindingGeneration(generation));
    }

    private JdbcQueryModel jdbcModel(String modelName) {
        JdbcQueryModel model = mock(JdbcQueryModel.class);
        when(model.getName()).thenReturn(modelName);
        return model;
    }

    private QueryModel queryModel(String modelName) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(modelName);
        return model;
    }
}
