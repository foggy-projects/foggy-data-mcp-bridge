package com.foggyframework.dataset.db.model.lifecycle.realquery;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.engine.pivot.CardinalityBreaker;
import com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQLite execution evidence for the lifecycle boundaries that cannot be
 * established by key-only or loader-only tests. Every subject query traverses
 * the real Spring QueryFacade/Semantic/Pivot path and is compared, in returned
 * order, with an independently executed native SQLite statement.
 */
@SpringBootTest(classes = {
        JdbcModelTestApplication.class,
        ModelLifecycleRealQueryIT.RealQueryConfiguration.class
})
@ActiveProfiles("sqlite")
class ModelLifecycleRealQueryIT {

    private static final String MAIN_NAMESPACE = "v933-real-query-main";
    private static final String NAMESPACE_A = "v933-real-query-a";
    private static final String NAMESPACE_B = "v933-real-query-b";

    private static final String MAIN_BUNDLE = "v933-real-query-main-bundle";
    private static final String NAMESPACE_A_BUNDLE = "v933-real-query-a-bundle";
    private static final String NAMESPACE_B_BUNDLE = "v933-real-query-b-bundle";
    private static final List<String> BUNDLES = List.of(
            MAIN_BUNDLE, NAMESPACE_A_BUNDLE, NAMESPACE_B_BUNDLE);
    private static final List<String> NAMESPACES = List.of(
            MAIN_NAMESPACE, NAMESPACE_A, NAMESPACE_B);

    private static final String SELECTOR_BEAN = "v933RealQuerySelector";
    private static final String TABLE_MODEL = "V933LifecycleRealTableModel";
    private static final String QUERY_MODEL = "V933LifecycleRealQueryModel";
    private static final String SIBLING_TABLE_MODEL = "V933LifecycleSiblingTableModel";
    private static final String SIBLING_QUERY_MODEL = "V933LifecycleSiblingQueryModel";

    private static final String MAIN_BINDING = "v933-real-query-main-binding";
    private static final String SIBLING_BINDING = "v933-real-query-sibling-binding";
    private static final String A_BINDING = "v933-real-query-a-binding";
    private static final String B_BINDING = "v933-real-query-b-binding";

    private static final String MAIN_OLD_TABLE = "v933_real_main_old";
    private static final String MAIN_NEW_TABLE = "v933_real_main_new";
    private static final String SIBLING_TABLE = "v933_real_sibling";
    private static final String A_OLD_TABLE = "v933_real_namespace_a_old";
    private static final String A_NEW_TABLE = "v933_real_namespace_a_new";
    private static final String B_TABLE = "v933_real_namespace_b";

    private static final List<String> DETAIL_COLUMNS =
            List.of("recordId", "payload", "bucket", "amount");

    @Autowired
    private QueryFacade queryFacade;

    @Autowired
    private SemanticQueryServiceV3 semanticQueryService;

    @Autowired
    private QueryModelLoader queryModelLoader;

    @Autowired
    private CatalogRefreshCoordinator refreshCoordinator;

    @Autowired
    private CatalogSnapshotStore catalogSnapshotStore;

    @Autowired
    private SystemBundlesContext bundlesContext;

    @Autowired
    private TrackedSqliteResolver resolver;

    @Autowired
    private RealQueryModelSelector selector;

    @Autowired
    private IdentityCaptureStep identityCaptureStep;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void prepareRealQueryFixture() throws Exception {
        NamespaceContext.clear();
        selector.releaseAndReset();
        identityCaptureStep.reset();
        removeBundles();
        NAMESPACES.forEach(catalogSnapshotStore::clearNamespace);

        resolver.initialize(temporaryDirectory.resolve("sqlite-bindings"));
        createPhysicalFixtures();
        registerBundles();

        assertInitialSnapshot(MAIN_NAMESPACE, QUERY_MODEL, TABLE_MODEL);
        assertInitialSnapshot(NAMESPACE_A, QUERY_MODEL, TABLE_MODEL);
        assertInitialSnapshot(NAMESPACE_B, QUERY_MODEL, TABLE_MODEL);
    }

    @AfterEach
    void cleanRealQueryFixture() {
        selector.releaseAndReset();
        identityCaptureStep.reset();
        NamespaceContext.clear();
        removeBundles();
        NAMESPACES.forEach(catalogSnapshotStore::clearNamespace);
        resolver.closeAll();
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void atomicModelRefreshSwitchesExactRowsAndPreservesSiblingModelIdentity() {
        List<DetailRow> nativeOld = nativeDetailRows(
                resolver.mainOldPhysical(), MAIN_OLD_TABLE);
        List<DetailRow> nativeNew = nativeDetailRows(
                resolver.mainOldPhysical(), MAIN_NEW_TABLE);
        List<DetailRow> nativeSibling = nativeDetailRows(
                resolver.siblingPhysical(), SIBLING_TABLE);
        assertMutuallyExclusive(nativeOld, "OLD_ONLY", "NEW_ONLY");
        assertMutuallyExclusive(nativeNew, "NEW_ONLY", "OLD_ONLY");

        CatalogSnapshot before = current(MAIN_NAMESPACE);
        QueryModel oldModel = before.queryModels().get(QUERY_MODEL);
        QueryModel siblingModel = before.queryModels().get(SIBLING_QUERY_MODEL);
        ModelProvenance siblingProvenance = before.queryModelProvenance(
                SIBLING_QUERY_MODEL).orElseThrow();
        assertObservation(
                executeDetail(MAIN_NAMESPACE, QUERY_MODEL),
                before.identity(), oldModel, resolver.mainOldIdentity(), nativeOld);
        assertObservation(
                executeDetail(MAIN_NAMESPACE, SIBLING_QUERY_MODEL),
                before.identity(), siblingModel, resolver.siblingIdentity(), nativeSibling);

        BuildGate gate = selector.selectMainNewAndBlockNextBuild();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<CatalogRefreshResult> refresh = executor.submit(() ->
                refreshCoordinator.refresh(modelRefresh(MAIN_NAMESPACE)));
        try {
            gate.awaitBuildEntered();
            assertSame(before, current(MAIN_NAMESPACE),
                    "detached refresh candidate must remain invisible");
            assertObservation(
                    executeDetail(MAIN_NAMESPACE, QUERY_MODEL),
                    before.identity(), oldModel, resolver.mainOldIdentity(), nativeOld);

            gate.releaseBuild();
            CatalogRefreshResult result = get(refresh, "main model refresh");
            CatalogSnapshot after = current(MAIN_NAMESPACE);
            QueryModel newModel = after.queryModels().get(QUERY_MODEL);

            assertEquals(before.identity(), result.beforeIdentity());
            assertEquals(after.identity(), result.afterIdentity());
            assertNotEquals(before.identity().generation(), after.identity().generation());
            assertEquals(before.identity().sourceRevision(), after.identity().sourceRevision());
            assertNotSame(oldModel, newModel);
            assertSame(siblingModel, after.queryModels().get(SIBLING_QUERY_MODEL));
            assertEquals(siblingProvenance,
                    after.queryModelProvenance(SIBLING_QUERY_MODEL).orElseThrow());
            assertObservation(
                    executeDetail(MAIN_NAMESPACE, QUERY_MODEL),
                    after.identity(), newModel, resolver.mainOldIdentity(), nativeNew);
            assertObservation(
                    executeDetail(MAIN_NAMESPACE, SIBLING_QUERY_MODEL),
                    after.identity(), siblingModel, resolver.siblingIdentity(), nativeSibling);
        } finally {
            gate.releaseBuild();
            refresh.cancel(true);
            shutdown(executor, "atomic refresh executor");
        }
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void sameNamedModelIsIsolatedAcrossNamespacesAndARefreshCannotMoveB() {
        List<DetailRow> nativeAOld = nativeDetailRows(
                resolver.namespaceAPhysical(), A_OLD_TABLE);
        List<DetailRow> nativeANew = nativeDetailRows(
                resolver.namespaceAPhysical(), A_NEW_TABLE);
        List<DetailRow> nativeB = nativeDetailRows(
                resolver.namespaceBPhysical(), B_TABLE);
        assertMutuallyExclusive(nativeAOld, "A_ONLY", "B_ONLY");
        assertMutuallyExclusive(nativeANew, "A_REFRESHED_ONLY", "B_ONLY");
        assertMutuallyExclusive(nativeB, "B_ONLY", "A_ONLY");

        CatalogSnapshot beforeA = current(NAMESPACE_A);
        CatalogSnapshot beforeB = current(NAMESPACE_B);
        QueryModel modelAOld = beforeA.queryModels().get(QUERY_MODEL);
        QueryModel modelB = beforeB.queryModels().get(QUERY_MODEL);
        assertNotSame(modelAOld, modelB);
        assertNotEquals(beforeA.identity(), beforeB.identity());
        assertObservation(executeDetail(NAMESPACE_A, QUERY_MODEL),
                beforeA.identity(), modelAOld, resolver.namespaceAIdentity(), nativeAOld);
        Observation bBefore = executeDetail(NAMESPACE_B, QUERY_MODEL);
        assertObservation(bBefore, beforeB.identity(), modelB,
                resolver.namespaceBIdentity(), nativeB);

        selector.selectNamespaceANew();
        CatalogRefreshResult refresh = refreshCoordinator.refresh(
                modelRefresh(NAMESPACE_A));
        CatalogSnapshot afterA = current(NAMESPACE_A);
        CatalogSnapshot afterB = current(NAMESPACE_B);

        assertNotEquals(beforeA.identity().generation(),
                afterA.identity().generation());
        assertNotSame(modelAOld, afterA.queryModels().get(QUERY_MODEL));
        assertEquals(afterA.identity(), refresh.afterIdentity());
        assertSame(beforeB, afterB,
                "namespace B catalog object and generation must remain unchanged");
        assertSame(modelB, afterB.queryModels().get(QUERY_MODEL));
        assertObservation(executeDetail(NAMESPACE_A, QUERY_MODEL),
                afterA.identity(), afterA.queryModels().get(QUERY_MODEL),
                resolver.namespaceAIdentity(), nativeANew);
        Observation bAfter = executeDetail(NAMESPACE_B, QUERY_MODEL);
        assertObservation(bAfter, beforeB.identity(), modelB,
                resolver.namespaceBIdentity(), nativeB);
        assertEquals(bBefore.rows(), bAfter.rows());
        assertEquals(bBefore.bindings(), bAfter.bindings());
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void datasourceRebindDrainsOldLeaseAndRejectsOldIdentityAfterCommit() {
        List<DetailRow> nativeOld = nativeDetailRows(
                resolver.mainOldPhysical(), MAIN_OLD_TABLE);
        List<DetailRow> nativeRebound = nativeDetailRows(
                resolver.mainNewPhysical(), MAIN_OLD_TABLE);
        assertMutuallyExclusive(nativeOld, "OLD_ONLY", "REBOUND_NEW_ONLY");
        assertMutuallyExclusive(nativeRebound, "REBOUND_NEW_ONLY", "OLD_ONLY");

        CatalogSnapshot before = current(MAIN_NAMESPACE);
        QueryModel oldModel = before.queryModels().get(QUERY_MODEL);
        CatalogResolution<QueryModel> oldResolution = queryModelLoader
                .resolveJdbcQueryModel(QUERY_MODEL, MAIN_NAMESPACE);
        assertNotNull(oldResolution);
        assertObservation(executeDetail(MAIN_NAMESPACE, QUERY_MODEL),
                before.identity(), oldModel, resolver.mainOldIdentity(), nativeOld);

        ConnectionGate connectionGate = resolver.armNextMainOldConnection();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Observation> oldInFlight = executor.submit(() -> {
            try {
                return executeDetail(MAIN_NAMESPACE, QUERY_MODEL);
            } finally {
                // End the request-scoped old-generation lease on the exact
                // worker that owned it. A later task on this executor cannot
                // inherit admission merely because it reuses the same thread.
                connectionGate.finishInFlight();
            }
        });
        try {
            connectionGate.awaitConnectionAcquired();
            assertEquals(1, resolver.mainOldActiveLeases());
            assertEquals(BindingAdmissionState.OPEN,
                    resolver.mainOldAdmissionState());

            resolver.rebindMain();
            assertEquals(BindingCurrentness.STALE,
                    resolver.currentness(resolver.mainOldIdentity()));
            assertEquals(BindingCurrentness.CURRENT,
                    resolver.currentness(resolver.mainNewIdentity()));
            assertEquals(BindingAdmissionState.RETIRING,
                    resolver.mainOldAdmissionState());
            assertThrows(SQLException.class, () ->
                    resolver.mainOldHandle().getConnection(),
                    "a new caller must not borrow from the retired old handle");
            assertThrows(StaleDatasourceBindingException.class, () ->
                    resolver.publishIfCurrent(
                            List.of(resolver.mainOldIdentity()), () -> "forbidden"));

            CatalogRefreshResult refresh = refreshCoordinator.refresh(
                    modelRefresh(MAIN_NAMESPACE));
            CatalogSnapshot after = current(MAIN_NAMESPACE);
            QueryModel newModel = after.queryModels().get(QUERY_MODEL);
            assertEquals(after.identity(), refresh.afterIdentity());
            assertNotEquals(before.identity().generation(),
                    after.identity().generation());
            assertNotSame(oldModel, newModel);

            Observation currentQuery = executeDetail(MAIN_NAMESPACE, QUERY_MODEL);
            assertObservation(currentQuery, after.identity(), newModel,
                    resolver.mainNewIdentity(), nativeRebound);

            ModelResultContext stalePinned = queryContext(
                    MAIN_NAMESPACE, QUERY_MODEL);
            stalePinned.pinCatalogResolution(oldResolution, MAIN_NAMESPACE);
            IllegalStateException repin = assertThrows(
                    IllegalStateException.class,
                    () -> queryFacade.queryModelResult(stalePinned));
            assertEquals("CONFLICTING_CATALOG_REPIN", repin.getMessage());

            connectionGate.releaseConnection();
            Observation completedOld = get(oldInFlight, "old in-flight query");
            assertObservation(completedOld, before.identity(), oldModel,
                    resolver.mainOldIdentity(), nativeOld);
            resolver.awaitMainOldClosed();
            assertEquals(0, resolver.mainOldActiveLeases());
            assertEquals(BindingAdmissionState.CLOSED,
                    resolver.mainOldAdmissionState());

            Observation afterDrain = executeDetail(MAIN_NAMESPACE, QUERY_MODEL);
            assertObservation(afterDrain, after.identity(), newModel,
                    resolver.mainNewIdentity(), nativeRebound);
        } finally {
            connectionGate.releaseConnection();
            connectionGate.finishInFlight();
            oldInFlight.cancel(true);
            shutdown(executor, "datasource rebind executor");
        }
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void pivotCacheMissHitAndRefreshGenerationMissHaveNativeAggregateParity() {
        SemanticQueryServiceV3Impl service =
                (SemanticQueryServiceV3Impl) semanticQueryService;
        PivotPipeline original = (PivotPipeline) ReflectionTestUtils.getField(
                service, "pivotPipeline");
        PivotPipeline cacheEnabled = new PivotPipeline(
                semanticQueryService,
                new CardinalityBreaker(),
                queryModelLoader,
                queryFacade,
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 32));
        ReflectionTestUtils.setField(service, "pivotPipeline", cacheEnabled);
        try {
            CatalogSnapshot before = current(MAIN_NAMESPACE);
            List<AggregateRow> nativeOld = nativeAggregateRows(
                    resolver.mainOldPhysical(), MAIN_OLD_TABLE);
            List<AggregateRow> nativeNew = nativeAggregateRows(
                    resolver.mainOldPhysical(), MAIN_NEW_TABLE);

            identityCaptureStep.reset();
            SemanticQueryResponse first = executePivot();
            assertEquals(nativeOld, pivotRows(first));
            Map<String, Object> firstLookup = diagnostic(
                    first, "pivot.cache.lookup");
            assertCompletePivotIdentity(first);
            assertEquals("cache_not_found",
                    diagnostic(first, "pivot.cache.miss").get("reason"));
            assertEquals(firstLookup.get("keyHash"),
                    diagnostic(first, "pivot.cache.store").get("keyHash"));
            int oldExecutionCount = identityCaptureStep.size();
            assertTrue(oldExecutionCount > 0,
                    "first Pivot miss must execute the real QueryFacade path");
            assertCapturedIdentity(before, resolver.mainOldIdentity());

            SemanticQueryResponse second = executePivot();
            assertEquals(nativeOld, pivotRows(second));
            assertEquals(firstLookup.get("keyHash"),
                    diagnostic(second, "pivot.cache.hit").get("keyHash"));
            assertEquals(oldExecutionCount, identityCaptureStep.size(),
                    "second Pivot query must be served by the outer cache");

            selector.selectMainNew();
            refreshCoordinator.refresh(modelRefresh(MAIN_NAMESPACE));
            CatalogSnapshot after = current(MAIN_NAMESPACE);
            assertNotEquals(before.identity().generation(),
                    after.identity().generation());

            SemanticQueryResponse third = executePivot();
            assertEquals(nativeNew, pivotRows(third));
            Map<String, Object> thirdLookup = diagnostic(
                    third, "pivot.cache.lookup");
            assertCompletePivotIdentity(third);
            assertNotEquals(firstLookup.get("keyHash"), thirdLookup.get("keyHash"));
            assertEquals("cache_not_found",
                    diagnostic(third, "pivot.cache.miss").get("reason"));
            assertEquals(thirdLookup.get("keyHash"),
                    diagnostic(third, "pivot.cache.store").get("keyHash"));
            int newExecutionCount = identityCaptureStep.size();
            assertTrue(newExecutionCount > oldExecutionCount,
                    "new catalog generation must execute instead of reusing the old cache entry");
            assertCapturedIdentity(after, resolver.mainOldIdentity());

            SemanticQueryResponse fourth = executePivot();
            assertEquals(nativeNew, pivotRows(fourth));
            assertEquals(thirdLookup.get("keyHash"),
                    diagnostic(fourth, "pivot.cache.hit").get("keyHash"));
            assertEquals(newExecutionCount, identityCaptureStep.size(),
                    "new generation's second Pivot query must hit its own cache entry");
        } finally {
            ReflectionTestUtils.setField(service, "pivotPipeline", original);
        }
    }

    private CatalogRefreshRequest modelRefresh(String namespace) {
        return CatalogRefreshRequest.models(
                namespace,
                Set.of(CatalogModelKey.query(QUERY_MODEL)),
                CatalogRefreshTrigger.EXPLICIT_RECOVERY);
    }

    private CatalogSnapshot current(String namespace) {
        return catalogSnapshotStore.readCurrent(namespace).orElseThrow();
    }

    private Observation executeDetail(String namespace, String queryModel) {
        ModelResultContext context = queryContext(namespace, queryModel);
        DbQueryResult result = queryFacade.queryModelResult(context);
        assertNotNull(result);
        assertNotNull(result.getQueryEngine());
        assertSame(context.getQueryModel(),
                result.getQueryEngine().getJdbcQueryModel());
        return new Observation(
                context.getCatalogIdentity(),
                context.getCanonicalModelName(),
                context.getQueryModel(),
                context.getDatasourceBindingIdentities(),
                context.isBindingIdentityComplete(),
                detailRows(result));
    }

    private ModelResultContext queryContext(String namespace, String queryModel) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(queryModel);
        request.setColumns(List.of("recordId", "payload", "bucket", "amount"));
        OrderRequestDef order = new OrderRequestDef();
        order.setField("recordId");
        order.setDir("ASC");
        request.setOrderBy(List.of(order));
        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 100), null);
        context.setNamespace(namespace);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.noOptimization());
        return context;
    }

    private void assertObservation(
            Observation observation,
            CatalogIdentity expectedCatalog,
            QueryModel expectedModel,
            DatasourceBindingIdentity expectedBinding,
            List<DetailRow> expectedRows
    ) {
        assertEquals(expectedCatalog, observation.catalogIdentity());
        assertEquals(expectedModel.getName(), observation.canonicalModelName());
        assertSame(expectedModel, observation.queryModel());
        assertEquals(Map.of(expectedBinding.bindingKey(), expectedBinding),
                observation.bindings());
        assertTrue(observation.bindingIdentityComplete());
        assertEquals(expectedRows, observation.rows());
    }

    private List<DetailRow> detailRows(DbQueryResult result) {
        assertNotNull(result.getPagingResult());
        assertNotNull(result.getPagingResult().getItems());
        List<DetailRow> rows = new ArrayList<>();
        for (Object item : result.getPagingResult().getItems()) {
            assertTrue(item instanceof Map<?, ?>,
                    () -> "unexpected QueryFacade row: " + item);
            Map<?, ?> row = (Map<?, ?>) item;
            assertEquals(DETAIL_COLUMNS, new ArrayList<>(row.keySet()),
                    "QueryFacade must return exactly the requested columns in order");
            rows.add(new DetailRow(
                    number(row, "recordId").longValue(),
                    String.valueOf(row.get("payload")),
                    String.valueOf(row.get("bucket")),
                    decimal(number(row, "amount"))));
        }
        return List.copyOf(rows);
    }

    private List<DetailRow> nativeDetailRows(DataSource dataSource, String table) {
        return new JdbcTemplate(dataSource).query(
                "SELECT record_id, payload, bucket, amount FROM " + table
                        + " ORDER BY record_id ASC",
                (rs, rowNum) -> new DetailRow(
                        rs.getLong("record_id"),
                        rs.getString("payload"),
                        rs.getString("bucket"),
                        decimal(rs.getBigDecimal("amount"))));
    }

    private List<AggregateRow> nativeAggregateRows(
            DataSource dataSource,
            String table
    ) {
        return new JdbcTemplate(dataSource).query(
                "SELECT bucket, SUM(amount) AS amount FROM " + table
                        + " GROUP BY bucket ORDER BY SUM(amount) ASC, bucket ASC",
                (rs, rowNum) -> new AggregateRow(
                        rs.getString("bucket"),
                        decimal(rs.getBigDecimal("amount"))));
    }

    private SemanticQueryResponse executePivot() {
        AxisField bucket = new AxisField();
        bucket.setField("bucket");
        bucket.setOrderBy(List.of("amount"));
        bucket.setLimit(100);
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(bucket));
        pivot.setMetrics(List.of("amount"));
        pivot.setOutputFormat("flat");
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        return semanticQueryService.queryModel(
                QUERY_MODEL,
                request,
                "execute",
                SemanticRequestContext.ofNamespace(MAIN_NAMESPACE));
    }

    private List<AggregateRow> pivotRows(SemanticQueryResponse response) {
        assertNotNull(response);
        assertNotNull(response.getItems());
        List<AggregateRow> rows = new ArrayList<>();
        for (Map<String, Object> row : response.getItems()) {
            assertEquals(List.of("bucket", "amount"),
                    new ArrayList<>(row.keySet()),
                    "Pivot must return exactly one axis and one metric in order");
            rows.add(new AggregateRow(
                    String.valueOf(row.get("bucket")),
                    decimal(number(row, "amount"))));
        }
        return List.copyOf(rows);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> diagnostic(
            SemanticQueryResponse response,
            String event
    ) {
        assertNotNull(response.getDebug());
        assertNotNull(response.getDebug().getExtra());
        Object raw = response.getDebug().getExtra().get("pivotDiagnostics");
        assertTrue(raw instanceof List<?>);
        return ((List<?>) raw).stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> event.equals(item.get("event")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing Pivot diagnostic " + event + " in " + raw));
    }

    private void assertCapturedIdentity(
            CatalogSnapshot snapshot,
            DatasourceBindingIdentity binding
    ) {
        CapturedIdentity captured = identityCaptureStep.latest();
        assertEquals(snapshot.identity(), captured.catalogIdentity());
        assertEquals(QUERY_MODEL, captured.canonicalModelName());
        assertSame(snapshot.queryModels().get(QUERY_MODEL), captured.queryModel());
        assertEquals(Map.of(binding.bindingKey(), binding), captured.bindings());
        assertTrue(captured.bindingIdentityComplete());
    }

    private void assertCompletePivotIdentity(SemanticQueryResponse response) {
        Map<String, Object> identity = diagnostic(response, "pivot.cache.identity");
        assertEquals("complete", identity.get("status"));
        assertEquals(1, ((Number) identity.get("bindingCount")).intValue());
        assertEquals(64, String.valueOf(identity.get("identityHash")).length());
    }

    private Number number(Map<?, ?> row, String key) {
        Object value = row.get(key);
        assertTrue(value instanceof Number,
                () -> "expected numeric " + key + " in " + row);
        return (Number) value;
    }

    private BigDecimal decimal(Number value) {
        assertNotNull(value);
        return new BigDecimal(String.valueOf(value)).stripTrailingZeros();
    }

    private void assertMutuallyExclusive(
            List<DetailRow> rows,
            String requiredSentinel,
            String forbiddenSentinel
    ) {
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(row ->
                row.payload().startsWith(requiredSentinel)));
        assertFalse(rows.stream().anyMatch(row ->
                row.payload().startsWith(forbiddenSentinel)));
    }

    private void createPhysicalFixtures() {
        recreateTable(resolver.mainOldPhysical(), MAIN_OLD_TABLE, List.<Object[]>of(
                row(101, "OLD_ONLY_ALPHA", "OLD-A", "10"),
                row(102, "OLD_ONLY_BETA", "OLD-A", "20"),
                row(103, "OLD_ONLY_GAMMA", "OLD-B", "50")));
        recreateTable(resolver.mainOldPhysical(), MAIN_NEW_TABLE, List.<Object[]>of(
                row(201, "NEW_ONLY_ALPHA", "NEW-A", "7"),
                row(202, "NEW_ONLY_BETA", "NEW-C", "100"),
                row(203, "NEW_ONLY_GAMMA", "NEW-C", "20")));
        recreateTable(resolver.mainNewPhysical(), MAIN_OLD_TABLE, List.<Object[]>of(
                row(301, "REBOUND_NEW_ONLY_ALPHA", "BOUND-A", "11"),
                row(302, "REBOUND_NEW_ONLY_BETA", "BOUND-B", "31")));
        recreateTable(resolver.mainNewPhysical(), MAIN_NEW_TABLE, List.<Object[]>of(
                row(401, "REBOUND_NEW_ONLY_NEW_TABLE", "BOUND-C", "41")));
        recreateTable(resolver.siblingPhysical(), SIBLING_TABLE, List.<Object[]>of(
                row(901, "SIBLING_ONLY_ALPHA", "SIBLING-A", "90"),
                row(902, "SIBLING_ONLY_BETA", "SIBLING-B", "91")));
        recreateTable(resolver.namespaceAPhysical(), A_OLD_TABLE, List.<Object[]>of(
                row(501, "A_ONLY_ALPHA", "A-OLD", "5"),
                row(502, "A_ONLY_BETA", "A-OLD", "6")));
        recreateTable(resolver.namespaceAPhysical(), A_NEW_TABLE, List.<Object[]>of(
                row(511, "A_REFRESHED_ONLY_ALPHA", "A-NEW", "15"),
                row(512, "A_REFRESHED_ONLY_BETA", "A-NEW", "16")));
        recreateTable(resolver.namespaceBPhysical(), B_TABLE, List.<Object[]>of(
                row(601, "B_ONLY_ALPHA", "B-STABLE", "25"),
                row(602, "B_ONLY_BETA", "B-STABLE", "26")));
    }

    private Object[] row(long id, String payload, String bucket, String amount) {
        return new Object[]{id, payload, bucket, new BigDecimal(amount)};
    }

    private void recreateTable(
            DataSource dataSource,
            String table,
            List<Object[]> rows
    ) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("CREATE TABLE " + table + " ("
                + "record_id INTEGER PRIMARY KEY, "
                + "payload TEXT NOT NULL, "
                + "bucket TEXT NOT NULL, "
                + "amount NUMERIC NOT NULL)");
        jdbc.batchUpdate("INSERT INTO " + table
                + " (record_id, payload, bucket, amount) VALUES (?, ?, ?, ?)",
                rows);
    }

    private void registerBundles() throws IOException {
        Path mainRoot = temporaryDirectory.resolve(MAIN_BUNDLE);
        writeBundleDirectories(mainRoot);
        writeTableModel(mainRoot, TABLE_MODEL, "mainTableName", MAIN_BINDING);
        writeQueryModel(mainRoot, QUERY_MODEL, TABLE_MODEL);
        writeFixedTableModel(mainRoot, SIBLING_TABLE_MODEL,
                SIBLING_TABLE, SIBLING_BINDING);
        writeQueryModel(mainRoot, SIBLING_QUERY_MODEL, SIBLING_TABLE_MODEL);

        Path aRoot = temporaryDirectory.resolve(NAMESPACE_A_BUNDLE);
        writeBundleDirectories(aRoot);
        writeTableModel(aRoot, TABLE_MODEL, "namespaceATableName", A_BINDING);
        writeQueryModel(aRoot, QUERY_MODEL, TABLE_MODEL);

        Path bRoot = temporaryDirectory.resolve(NAMESPACE_B_BUNDLE);
        writeBundleDirectories(bRoot);
        writeTableModel(bRoot, TABLE_MODEL, "namespaceBTableName", B_BINDING);
        writeQueryModel(bRoot, QUERY_MODEL, TABLE_MODEL);

        assertTrue(bundlesContext.addExternalBundle(
                MAIN_BUNDLE, MAIN_NAMESPACE, mainRoot.toString(), false));
        assertTrue(bundlesContext.addExternalBundle(
                NAMESPACE_A_BUNDLE, NAMESPACE_A, aRoot.toString(), false));
        assertTrue(bundlesContext.addExternalBundle(
                NAMESPACE_B_BUNDLE, NAMESPACE_B, bRoot.toString(), false));
    }

    private void writeBundleDirectories(Path root) throws IOException {
        Files.createDirectories(root.resolve("model"));
        Files.createDirectories(root.resolve("query"));
    }

    private void writeTableModel(
            Path root,
            String modelName,
            String selectorMethod,
            String bindingName
    ) throws IOException {
        Files.writeString(root.resolve("model").resolve(modelName + ".tm"), """
                import {%s} from '@%s';

                export const model = {
                    name: '%s',
                    caption: 'V933 lifecycle real-query table',
                    tableName: %s(),
                    dataSourceName: '%s',
                    idColumn: 'record_id',
                    properties: [
                        { column: 'record_id', name: 'recordId', caption: 'Record ID', type: 'LONG' },
                        { column: 'payload', name: 'payload', caption: 'Payload', type: 'STRING' },
                        { column: 'bucket', name: 'bucket', caption: 'Bucket', type: 'STRING' }
                    ],
                    measures: [
                        { column: 'amount', name: 'amount', caption: 'Amount', type: 'NUMBER', aggregation: 'SUM' }
                    ]
                };
                """.formatted(selectorMethod, SELECTOR_BEAN,
                modelName, selectorMethod, bindingName));
    }

    private void writeFixedTableModel(
            Path root,
            String modelName,
            String table,
            String bindingName
    ) throws IOException {
        Files.writeString(root.resolve("model").resolve(modelName + ".tm"), """
                export const model = {
                    name: '%s',
                    caption: 'V933 lifecycle stable sibling table',
                    tableName: '%s',
                    dataSourceName: '%s',
                    idColumn: 'record_id',
                    properties: [
                        { column: 'record_id', name: 'recordId', caption: 'Record ID', type: 'LONG' },
                        { column: 'payload', name: 'payload', caption: 'Payload', type: 'STRING' },
                        { column: 'bucket', name: 'bucket', caption: 'Bucket', type: 'STRING' }
                    ],
                    measures: [
                        { column: 'amount', name: 'amount', caption: 'Amount', type: 'NUMBER', aggregation: 'SUM' }
                    ]
                };
                """.formatted(modelName, table, bindingName));
    }

    private void writeQueryModel(
            Path root,
            String queryModelName,
            String tableModelName
    ) throws IOException {
        Files.writeString(root.resolve("query").resolve(queryModelName + ".qm"), """
                const fixture = loadTableModel('%s');

                export const queryModel = {
                    name: '%s',
                    caption: 'V933 lifecycle real-query model',
                    description: 'SQLite lifecycle execution evidence',
                    model: fixture,
                    columnGroups: [
                        {
                            caption: 'Lifecycle fields',
                            items: [
                                { ref: fixture.recordId },
                                { ref: fixture.payload },
                                { ref: fixture.bucket },
                                { ref: fixture.amount }
                            ]
                        }
                    ],
                    accesses: []
                };
                """.formatted(tableModelName, queryModelName));
    }

    private void removeBundles() {
        for (String bundle : BUNDLES) {
            if (bundlesContext.containBundle(bundle)) {
                assertTrue(bundlesContext.removeBundle(bundle),
                        () -> "failed to remove test bundle " + bundle);
            }
        }
    }

    private void assertInitialSnapshot(
            String namespace,
            String queryModel,
            String tableModel
    ) {
        CatalogSnapshot snapshot = current(namespace);
        assertEquals(namespace, snapshot.identity().namespace());
        assertTrue(snapshot.queryModels().containsKey(queryModel));
        assertTrue(snapshot.tableModels().containsKey(tableModel));
        ModelProvenance provenance = snapshot.queryModelProvenance(
                queryModel).orElseThrow();
        assertTrue(provenance.bindingIdentityComplete());
        assertFalse(provenance.datasourceBindings().isEmpty());
    }

    private <T> T get(Future<T> future, String description) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new AssertionError("timed out waiting for " + description, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + description,
                    interrupted);
        } catch (Exception failure) {
            throw new AssertionError(description + " failed", failure);
        }
    }

    private void shutdown(ExecutorService executor, String description) {
        executor.shutdownNow();
        try {
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    description + " did not terminate");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "interrupted while stopping " + description, interrupted);
        }
    }

    private record DetailRow(
            long recordId,
            String payload,
            String bucket,
            BigDecimal amount
    ) {
    }

    private record AggregateRow(String bucket, BigDecimal amount) {
    }

    private record Observation(
            CatalogIdentity catalogIdentity,
            String canonicalModelName,
            QueryModel queryModel,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean bindingIdentityComplete,
            List<DetailRow> rows
    ) {
        private Observation {
            bindings = Map.copyOf(bindings);
            rows = List.copyOf(rows);
        }
    }

    private record CapturedIdentity(
            CatalogIdentity catalogIdentity,
            String canonicalModelName,
            QueryModel queryModel,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean bindingIdentityComplete
    ) {
        private CapturedIdentity {
            bindings = Map.copyOf(bindings);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RealQueryConfiguration {

        @Bean(name = SELECTOR_BEAN)
        RealQueryModelSelector v933RealQuerySelector() {
            return new RealQueryModelSelector();
        }

        @Bean
        @Primary
        TrackedSqliteResolver v933TrackedSqliteResolver() {
            return new TrackedSqliteResolver();
        }

        @Bean
        IdentityCaptureStep v933IdentityCaptureStep() {
            return new IdentityCaptureStep();
        }
    }

    static final class IdentityCaptureStep implements DataSetResultStep {

        private final ConcurrentLinkedQueue<CapturedIdentity> captured =
                new ConcurrentLinkedQueue<>();

        @Override
        public int beforeQueryOrder() {
            return 900;
        }

        @Override
        public int beforeQuery(ModelResultContext context) {
            if (QUERY_MODEL.equals(context.getCanonicalModelName())
                    && MAIN_NAMESPACE.equals(context.getNamespace())) {
                captured.add(new CapturedIdentity(
                        context.getCatalogIdentity(),
                        context.getCanonicalModelName(),
                        context.getQueryModel(),
                        context.getDatasourceBindingIdentities(),
                        context.isBindingIdentityComplete()));
            }
            return CONTINUE;
        }

        void reset() {
            captured.clear();
        }

        int size() {
            return captured.size();
        }

        CapturedIdentity latest() {
            CapturedIdentity latest = null;
            for (CapturedIdentity item : captured) {
                latest = item;
            }
            return Objects.requireNonNull(latest,
                    "no QueryFacade identity was captured");
        }
    }

    public static final class RealQueryModelSelector {

        private final AtomicReference<String> mainTable =
                new AtomicReference<>(MAIN_OLD_TABLE);
        private final AtomicReference<String> namespaceATable =
                new AtomicReference<>(A_OLD_TABLE);
        private final AtomicReference<BuildGate> nextMainBuild =
                new AtomicReference<>();

        public String mainTableName() {
            BuildGate gate = nextMainBuild.getAndSet(null);
            if (gate != null) {
                gate.buildEntered.countDown();
                gate.awaitRelease();
            }
            return mainTable.get();
        }

        public String namespaceATableName() {
            return namespaceATable.get();
        }

        public String namespaceBTableName() {
            return B_TABLE;
        }

        BuildGate selectMainNewAndBlockNextBuild() {
            mainTable.set(MAIN_NEW_TABLE);
            BuildGate gate = new BuildGate();
            if (!nextMainBuild.compareAndSet(null, gate)) {
                throw new IllegalStateException("main refresh gate is already armed");
            }
            return gate;
        }

        void selectMainNew() {
            mainTable.set(MAIN_NEW_TABLE);
        }

        void selectNamespaceANew() {
            namespaceATable.set(A_NEW_TABLE);
        }

        void releaseAndReset() {
            BuildGate gate = nextMainBuild.getAndSet(null);
            if (gate != null) {
                gate.releaseBuild();
            }
            mainTable.set(MAIN_OLD_TABLE);
            namespaceATable.set(A_OLD_TABLE);
        }
    }

    static final class BuildGate {

        private final CountDownLatch buildEntered = new CountDownLatch(1);
        private final CountDownLatch releaseBuild = new CountDownLatch(1);

        void awaitBuildEntered() {
            await(buildEntered, "refresh candidate build entry");
        }

        void awaitRelease() {
            await(releaseBuild, "refresh candidate build release");
        }

        void releaseBuild() {
            releaseBuild.countDown();
        }
    }

    static final class ConnectionGate {

        private final CountDownLatch connectionAcquired = new CountDownLatch(1);
        private final CountDownLatch releaseConnection = new CountDownLatch(1);
        private final AtomicReference<BindingSlot> slot = new AtomicReference<>();
        private final AtomicReference<Thread> owner = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();

        void bind(BindingSlot bindingSlot, Thread ownerThread) {
            if (!slot.compareAndSet(null, bindingSlot)
                    || !owner.compareAndSet(null, ownerThread)) {
                throw new IllegalStateException(
                        "old-query lease permit was already bound");
            }
        }

        boolean admits(Thread caller) {
            return !finished.get() && owner.get() == caller;
        }

        void awaitConnectionAcquired() {
            await(connectionAcquired, "old datasource connection lease");
        }

        void awaitRelease() {
            await(releaseConnection, "old datasource connection release");
        }

        void releaseConnection() {
            releaseConnection.countDown();
        }

        void finishInFlight() {
            BindingSlot bindingSlot = slot.get();
            if (bindingSlot != null && finished.compareAndSet(false, true)) {
                bindingSlot.finishInFlight(this);
            }
        }
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "timed out waiting for " + description);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "interrupted while waiting for " + description, interrupted);
        }
    }

    static final class TrackedSqliteResolver
            implements NamedDataSourceResolver,
            ProcessLocalDefaultDataSourceResolver {

        private final Object publicationMonitor = new Object();
        private final Map<String, BindingSlot> current = new ConcurrentHashMap<>();
        private final Map<DatasourceBindingIdentity, BindingSlot> known =
                new ConcurrentHashMap<>();

        private BindingSlot mainOld;
        private BindingSlot mainNew;
        private BindingSlot sibling;
        private BindingSlot namespaceA;
        private BindingSlot namespaceB;

        void initialize(Path directory) throws IOException {
            Files.createDirectories(directory);
            synchronized (publicationMonitor) {
                closeAllLocked();
                mainOld = slot(directory, "main-old.sqlite", MAIN_BINDING,
                        "sqlite:v933-main-old", "main-generation-1");
                mainNew = slot(directory, "main-new.sqlite", MAIN_BINDING,
                        "sqlite:v933-main-new", "main-generation-2");
                sibling = slot(directory, "sibling.sqlite", SIBLING_BINDING,
                        "sqlite:v933-sibling", "sibling-generation-1");
                namespaceA = slot(directory, "namespace-a.sqlite", A_BINDING,
                        "sqlite:v933-namespace-a", "namespace-a-generation-1");
                namespaceB = slot(directory, "namespace-b.sqlite", B_BINDING,
                        "sqlite:v933-namespace-b", "namespace-b-generation-1");
                current.put(MAIN_BINDING, mainOld);
                current.put(SIBLING_BINDING, sibling);
                current.put(A_BINDING, namespaceA);
                current.put(B_BINDING, namespaceB);
                known.put(mainOld.identity(), mainOld);
                known.put(mainNew.identity(), mainNew);
                known.put(sibling.identity(), sibling);
                known.put(namespaceA.identity(), namespaceA);
                known.put(namespaceB.identity(), namespaceB);
            }
        }

        private BindingSlot slot(
                Path directory,
                String file,
                String logicalName,
                String backend,
                String generation
        ) {
            SQLiteDataSource physical = new SQLiteDataSource();
            physical.setUrl("jdbc:sqlite:" + directory.resolve(file));
            DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                    "test:v933-real-query:" + logicalName,
                    backend,
                    new DatasourceBindingGeneration(generation));
            return new BindingSlot(physical, identity);
        }

        @Override
        public DataSource resolve(String name) {
            ResolvedDatasourceBinding binding = resolveBinding(name);
            return binding == null ? null : binding.dataSource();
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            synchronized (publicationMonitor) {
                BindingSlot slot = current.get(name);
                return slot == null
                        ? null
                        : ResolvedDatasourceBinding.tracked(
                        slot.handle(), slot.identity());
            }
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            String name = switch (CatalogIdentity.canonicalNamespace(namespace)) {
                case MAIN_NAMESPACE -> MAIN_BINDING;
                case NAMESPACE_A -> A_BINDING;
                case NAMESPACE_B -> B_BINDING;
                default -> null;
            };
            return name == null ? null : resolveBinding(name);
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return resolveBinding(MAIN_BINDING);
        }

        @Override
        public boolean isConfigured(String name) {
            synchronized (publicationMonitor) {
                return current.containsKey(name);
            }
        }

        @Override
        public BindingCurrentness currentness(
                DatasourceBindingIdentity identity
        ) {
            synchronized (publicationMonitor) {
                return currentnessLocked(identity);
            }
        }

        private BindingCurrentness currentnessLocked(
                DatasourceBindingIdentity identity
        ) {
            if (identity == null) {
                return BindingCurrentness.UNKNOWN;
            }
            BindingSlot active = current.values().stream()
                    .filter(slot -> slot.identity().equals(identity))
                    .findFirst()
                    .orElse(null);
            if (active != null) {
                return BindingCurrentness.CURRENT;
            }
            return known.containsKey(identity)
                    ? BindingCurrentness.STALE
                    : BindingCurrentness.UNKNOWN;
        }

        @Override
        public <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            Objects.requireNonNull(identities, "identities");
            Objects.requireNonNull(publication, "publication");
            synchronized (publicationMonitor) {
                for (DatasourceBindingIdentity identity : identities) {
                    if (currentnessLocked(identity) != BindingCurrentness.CURRENT) {
                        throw new StaleDatasourceBindingException(
                                identity.bindingKey());
                    }
                }
                return publication.get();
            }
        }

        void rebindMain() {
            synchronized (publicationMonitor) {
                assertSame(mainOld, current.put(MAIN_BINDING, mainNew));
                mainOld.retire();
            }
        }

        ConnectionGate armNextMainOldConnection() {
            return mainOld.armNextConnection();
        }

        void awaitMainOldClosed() {
            mainOld.awaitClosed();
        }

        int mainOldActiveLeases() {
            return mainOld.activeLeases();
        }

        BindingAdmissionState mainOldAdmissionState() {
            return mainOld.state();
        }

        DatasourceBindingIdentity mainOldIdentity() {
            return mainOld.identity();
        }

        DatasourceBindingIdentity mainNewIdentity() {
            return mainNew.identity();
        }

        DatasourceBindingIdentity siblingIdentity() {
            return sibling.identity();
        }

        DatasourceBindingIdentity namespaceAIdentity() {
            return namespaceA.identity();
        }

        DatasourceBindingIdentity namespaceBIdentity() {
            return namespaceB.identity();
        }

        DataSource mainOldPhysical() {
            return mainOld.physical();
        }

        DataSource mainOldHandle() {
            return mainOld.handle();
        }

        DataSource mainNewPhysical() {
            return mainNew.physical();
        }

        DataSource siblingPhysical() {
            return sibling.physical();
        }

        DataSource namespaceAPhysical() {
            return namespaceA.physical();
        }

        DataSource namespaceBPhysical() {
            return namespaceB.physical();
        }

        void closeAll() {
            synchronized (publicationMonitor) {
                closeAllLocked();
            }
        }

        private void closeAllLocked() {
            known.values().forEach(BindingSlot::revoke);
            current.clear();
            known.clear();
        }
    }

    static final class BindingSlot {

        private final DataSource physical;
        private final DatasourceBindingIdentity identity;
        private final LeaseTrackingDataSource handle;
        private final AtomicReference<ConnectionGate> nextConnectionGate =
                new AtomicReference<>();
        private final CountDownLatch closed = new CountDownLatch(1);

        private BindingAdmissionState state = BindingAdmissionState.OPEN;
        private int activeConnections;
        private int activeInFlightLeases;
        private ConnectionGate drainingInFlight;

        BindingSlot(
                DataSource physical,
                DatasourceBindingIdentity identity
        ) {
            this.physical = Objects.requireNonNull(physical, "physical");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.handle = new LeaseTrackingDataSource(this);
        }

        DataSource physical() {
            return physical;
        }

        DataSource handle() {
            return handle;
        }

        DatasourceBindingIdentity identity() {
            return identity;
        }

        synchronized BindingAdmissionState state() {
            return state;
        }

        synchronized int activeLeases() {
            return activeInFlightLeases;
        }

        Connection acquire(SqlConnectionSupplier supplier) throws SQLException {
            Connection delegate;
            ConnectionGate gate;
            synchronized (this) {
                Thread caller = Thread.currentThread();
                boolean admittedInFlight = state == BindingAdmissionState.RETIRING
                        && drainingInFlight != null
                        && drainingInFlight.admits(caller);
                if (state != BindingAdmissionState.OPEN && !admittedInFlight) {
                    throw new SQLException("STALE_DATASOURCE_BINDING: "
                            + identity.bindingKey());
                }
                delegate = supplier.get();
                activeConnections++;
                gate = nextConnectionGate.getAndSet(null);
                if (gate != null) {
                    if (drainingInFlight != null) {
                        throw new IllegalStateException(
                                "an old-query lease is already active");
                    }
                    gate.bind(this, caller);
                    drainingInFlight = gate;
                    activeInFlightLeases++;
                }
            }
            Connection connection = lease(delegate);
            if (gate != null) {
                gate.connectionAcquired.countDown();
                try {
                    gate.awaitRelease();
                } catch (RuntimeException | Error failure) {
                    try {
                        connection.close();
                    } catch (SQLException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                    throw failure;
                }
            }
            return connection;
        }

        private Connection lease(Connection delegate) {
            AtomicBoolean released = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("close".equals(methodName)) {
                            if (released.compareAndSet(false, true)) {
                                try {
                                    delegate.close();
                                } finally {
                                    releaseLease();
                                }
                            }
                            return null;
                        }
                        if ("isClosed".equals(methodName) && released.get()) {
                            return true;
                        }
                        if ("unwrap".equals(methodName)
                                && args != null
                                && args.length == 1
                                && args[0] instanceof Class<?> type
                                && type.isInstance(proxy)) {
                            return proxy;
                        }
                        if ("isWrapperFor".equals(methodName)
                                && args != null
                                && args.length == 1
                                && args[0] instanceof Class<?> type
                                && type.isInstance(proxy)) {
                            return true;
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException invocation) {
                            throw invocation.getCause();
                        }
                    });
        }

        private synchronized void releaseLease() {
            if (activeConnections < 1) {
                throw new IllegalStateException("binding connection underflow");
            }
            activeConnections--;
            closeIfDrained();
        }

        synchronized void finishInFlight(ConnectionGate gate) {
            if (drainingInFlight != gate || activeInFlightLeases != 1) {
                throw new IllegalStateException(
                        "old-query lease permit is not active");
            }
            drainingInFlight = null;
            activeInFlightLeases--;
            closeIfDrained();
        }

        private void closeIfDrained() {
            if (activeConnections == 0
                    && activeInFlightLeases == 0
                    && state == BindingAdmissionState.RETIRING) {
                state = BindingAdmissionState.CLOSED;
                closed.countDown();
            }
        }

        synchronized void retire() {
            if (state != BindingAdmissionState.OPEN) {
                throw new IllegalStateException(
                        "only an open binding can begin drain");
            }
            state = activeConnections == 0 && activeInFlightLeases == 0
                    ? BindingAdmissionState.CLOSED
                    : BindingAdmissionState.RETIRING;
            if (state == BindingAdmissionState.CLOSED) {
                closed.countDown();
            }
        }

        synchronized void revoke() {
            ConnectionGate gate = nextConnectionGate.getAndSet(null);
            if (gate != null) {
                gate.releaseConnection();
            }
            if (state != BindingAdmissionState.CLOSED
                    && state != BindingAdmissionState.REVOKED) {
                state = BindingAdmissionState.REVOKED;
                closed.countDown();
            }
        }

        ConnectionGate armNextConnection() {
            ConnectionGate gate = new ConnectionGate();
            if (!nextConnectionGate.compareAndSet(null, gate)) {
                throw new IllegalStateException(
                        "connection lease gate is already armed");
            }
            return gate;
        }

        void awaitClosed() {
            await(closed, "retired datasource binding to close");
        }
    }

    @FunctionalInterface
    interface SqlConnectionSupplier {
        Connection get() throws SQLException;
    }

    static final class LeaseTrackingDataSource implements DataSource {

        private final BindingSlot slot;

        LeaseTrackingDataSource(BindingSlot slot) {
            this.slot = slot;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return slot.acquire(slot.physical()::getConnection);
        }

        @Override
        public Connection getConnection(String username, String password)
                throws SQLException {
            return slot.acquire(() ->
                    slot.physical().getConnection(username, password));
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return slot.physical().getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            slot.physical().setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            slot.physical().setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return slot.physical().getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return slot.physical().getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return slot.physical().unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this)
                    || slot.physical().isWrapperFor(iface);
        }
    }
}
