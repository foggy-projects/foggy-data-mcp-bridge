package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real SQLite proof for the two refresh behaviors that unit contracts cannot
 * establish: readers pin one complete catalog generation, and a failed model
 * candidate preserves a still-current tracked binding and its old query.
 */
@SpringBootTest(classes = {
        JdbcModelTestApplication.class,
        CatalogRefreshQueryIT.LifecycleITConfiguration.class
})
@ActiveProfiles("sqlite")
class CatalogRefreshQueryIT {

    private static final String NAMESPACE = "v933-refresh-query-it";
    private static final String BUNDLE_NAME = "v933-refresh-query-it-bundle";
    private static final String SIBLING_BUNDLE_NAME =
            "v933-refresh-query-it-sibling-bundle";
    private static final String BINDING_NAME = "v933-refresh-query-it-sqlite";
    private static final String SELECTOR_BEAN = "v933AtomicRefreshSelector";
    private static final String TABLE_MODEL = "V933AtomicRefreshTableModel";
    private static final String QUERY_MODEL = "V933AtomicRefreshQueryModel";
    private static final String SIBLING_TABLE_MODEL =
            "V933SiblingTableModel";
    private static final String SIBLING_QUERY_MODEL =
            "V933SiblingQueryModel";
    private static final String OLD_TABLE = "v933_refresh_old";
    private static final String NEW_TABLE = "v933_refresh_new";
    private static final String SIBLING_TABLE = "v933_refresh_sibling";
    private static final DatasourceBindingIdentity BINDING_IDENTITY =
            new DatasourceBindingIdentity(
                    "test:v933-refresh-query-it",
                    "jdbc:sqlite-test",
                    new DatasourceBindingGeneration("v933-refresh-query-it-generation"));

    @Autowired
    private AdvancedQueryFacade queryFacade;

    @Autowired
    private CatalogRefreshCoordinator refreshCoordinator;

    @Autowired
    private CatalogSnapshotStore catalogSnapshotStore;

    @Autowired
    private SystemBundlesContext bundlesContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshModelSelector modelSelector;

    @Autowired
    private ReaderPinGateStep readerPinGateStep;

    @Autowired
    private TrackedSqliteResolver trackedSqliteResolver;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void prepareFixture() throws Exception {
        readerPinGateStep.releaseAndDisarm();
        modelSelector.releaseAndReset();
        NamespaceContext.clear();
        removeBundleIfPresent(SIBLING_BUNDLE_NAME);
        removeTestBundleIfPresent();
        catalogSnapshotStore.clearNamespace(NAMESPACE);
        recreateSqliteTables();
        assertSqliteBinding();
        registerTestBundle();

        CatalogSnapshot initial = catalogSnapshotStore.readCurrent(NAMESPACE)
                .orElseThrow(() -> new AssertionError(
                        "bundle add did not publish the initial catalog"));
        assertEquals(CatalogAdmissionState.ACTIVE,
                catalogSnapshotStore.admissionState(NAMESPACE));
        assertTrue(initial.queryModels().containsKey(QUERY_MODEL));
        assertTrue(initial.tableModels().containsKey(TABLE_MODEL));
    }

    @AfterEach
    void cleanFixture() {
        readerPinGateStep.releaseAndDisarm();
        modelSelector.releaseAndReset();
        NamespaceContext.clear();
        removeBundleIfPresent(SIBLING_BUNDLE_NAME);
        removeTestBundleIfPresent();
        catalogSnapshotStore.clearNamespace(NAMESPACE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + OLD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + NEW_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + SIBLING_TABLE);
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void concurrentReadersMustObserveOnlyCompleteOldOrNewCatalogResults() {
        List<FixtureRow> nativeOld = nativeRows(OLD_TABLE);
        List<FixtureRow> nativeNew = nativeRows(NEW_TABLE);
        assertNotEquals(nativeOld, nativeNew,
                "the SQLite fixture must make old and new results distinguishable");

        Observation seed = executeQuery();
        CatalogSnapshot before = catalogSnapshotStore.readCurrent(NAMESPACE)
                .orElseThrow();
        QueryModel oldModel = before.queryModels().get(QUERY_MODEL);
        assertObservation(seed, before.identity(), oldModel, nativeOld);

        ReaderGate readerGate = readerPinGateStep.arm(2);
        BuildGate candidateGate = null;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> submitted = new ArrayList<>();
        try {
            Future<Observation> oldReaderOne =
                    executor.submit(() -> executeQuery());
            Future<Observation> oldReaderTwo =
                    executor.submit(() -> executeQuery());
            submitted.add(oldReaderOne);
            submitted.add(oldReaderTwo);
            readerGate.awaitAllEntered();
            assertFalse(oldReaderOne.isDone());
            assertFalse(oldReaderTwo.isDone());

            candidateGate = modelSelector.selectNewTableAndBlockNextBuild();
            Future<CatalogRefreshResult> refresh = executor.submit(() ->
                    refreshCoordinator.refresh(modelRefreshRequest()));
            submitted.add(refresh);
            candidateGate.awaitBuildEntered();

            assertSame(before, catalogSnapshotStore.readCurrent(NAMESPACE)
                    .orElseThrow(),
                    "a blocked detached candidate must remain invisible");

            candidateGate.releaseBuild();
            CatalogRefreshResult refreshResult =
                    DeterministicConcurrencyTestSupport.get(refresh, "catalog refresh");
            CatalogSnapshot after = catalogSnapshotStore.readCurrent(NAMESPACE)
                    .orElseThrow();
            QueryModel newModel = after.queryModels().get(QUERY_MODEL);

            assertEquals(before.identity(), refreshResult.beforeIdentity());
            assertEquals(after.identity(), refreshResult.afterIdentity());
            assertNotEquals(before.identity().generation(),
                    after.identity().generation());
            assertNotSame(oldModel, newModel);
            assertEquals(CatalogAdmissionState.ACTIVE,
                    refreshResult.catalogState());

            Future<Observation> newReaderOne =
                    executor.submit(() -> executeQuery());
            Future<Observation> newReaderTwo =
                    executor.submit(() -> executeQuery());
            submitted.add(newReaderOne);
            submitted.add(newReaderTwo);

            Observation observedNewOne = DeterministicConcurrencyTestSupport.get(
                    newReaderOne, "new reader one");
            Observation observedNewTwo = DeterministicConcurrencyTestSupport.get(
                    newReaderTwo, "new reader two");
            assertObservation(observedNewOne, after.identity(), newModel, nativeNew);
            assertObservation(observedNewTwo, after.identity(), newModel, nativeNew);

            assertFalse(oldReaderOne.isDone(),
                    "old reader one must stay in-flight across publication");
            assertFalse(oldReaderTwo.isDone(),
                    "old reader two must stay in-flight across publication");
            readerGate.releaseReaders();

            Observation observedOldOne = DeterministicConcurrencyTestSupport.get(
                    oldReaderOne, "old reader one");
            Observation observedOldTwo = DeterministicConcurrencyTestSupport.get(
                    oldReaderTwo, "old reader two");
            assertObservation(observedOldOne, before.identity(), oldModel, nativeOld);
            assertObservation(observedOldTwo, before.identity(), oldModel, nativeOld);
        } finally {
            readerGate.releaseReaders();
            if (candidateGate != null) {
                candidateGate.releaseBuild();
            }
            DeterministicConcurrencyTestSupport.cancelIncomplete(submitted);
            DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated(
                    executor, "CatalogRefreshQueryIT atomic readers");
        }
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void failedCandidateWithCurrentBindingMustKeepOldRealQueryUsable() {
        List<FixtureRow> nativeOld = nativeRows(OLD_TABLE);
        Observation beforeFailure = executeQuery();
        CatalogSnapshot before = catalogSnapshotStore.readCurrent(NAMESPACE)
                .orElseThrow();
        QueryModel oldModel = before.queryModels().get(QUERY_MODEL);
        assertObservation(beforeFailure, before.identity(), oldModel, nativeOld);

        modelSelector.failBuilds();
        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> refreshCoordinator.refresh(modelRefreshRequest()));

        assertEquals("CATALOG_REFRESH_FAILED", failure.code());
        assertEquals(before.identity(), failure.beforeIdentity());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                failure.catalogState());
        assertEquals(BindingCurrentness.CURRENT,
                trackedSqliteResolver.currentness(BINDING_IDENTITY));
        assertSame(before, catalogSnapshotStore.current(NAMESPACE).orElseThrow());
        assertSame(before, catalogSnapshotStore.readCurrent(NAMESPACE).orElseThrow());

        modelSelector.selectOldTable();
        Observation afterFailure = executeQuery();
        assertObservation(afterFailure, before.identity(), oldModel, nativeOld);
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                catalogSnapshotStore.admissionState(NAMESPACE));
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void sameNamespaceBundlesMustRemainIndependentAcrossReplaceAndRemove()
            throws Exception {
        Path siblingRoot = temporaryDirectory.resolve(SIBLING_BUNDLE_NAME);
        writeStaticBundle(
                siblingRoot,
                SIBLING_TABLE_MODEL,
                SIBLING_QUERY_MODEL,
                SIBLING_TABLE);
        assertTrue(bundlesContext.addExternalBundle(
                SIBLING_BUNDLE_NAME,
                NAMESPACE,
                siblingRoot.toString(),
                false));

        assertEquals(nativeRows(OLD_TABLE), executeQuery(QUERY_MODEL).rows());
        assertEquals(nativeRows(SIBLING_TABLE),
                executeQuery(SIBLING_QUERY_MODEL).rows());
        assertBundleOwner(QUERY_MODEL, BUNDLE_NAME);
        assertBundleOwner(SIBLING_QUERY_MODEL, SIBLING_BUNDLE_NAME);
        assertTableBundleOwner(TABLE_MODEL, BUNDLE_NAME);
        assertTableBundleOwner(SIBLING_TABLE_MODEL, SIBLING_BUNDLE_NAME);

        Path replacementRoot = temporaryDirectory.resolve(
                BUNDLE_NAME + "-replacement");
        writeStaticBundle(
                replacementRoot,
                TABLE_MODEL,
                QUERY_MODEL,
                NEW_TABLE);
        assertTrue(bundlesContext.replaceExternalBundle(
                BUNDLE_NAME,
                NAMESPACE,
                replacementRoot.toString(),
                false));

        assertEquals(nativeRows(NEW_TABLE), executeQuery(QUERY_MODEL).rows());
        assertEquals(nativeRows(SIBLING_TABLE),
                executeQuery(SIBLING_QUERY_MODEL).rows());
        assertBundleOwner(QUERY_MODEL, BUNDLE_NAME);
        assertBundleOwner(SIBLING_QUERY_MODEL, SIBLING_BUNDLE_NAME);
        assertTableBundleOwner(TABLE_MODEL, BUNDLE_NAME);
        assertTableBundleOwner(SIBLING_TABLE_MODEL, SIBLING_BUNDLE_NAME);

        assertTrue(bundlesContext.removeBundle(BUNDLE_NAME));
        CatalogSnapshot afterRemove = catalogSnapshotStore.readCurrent(NAMESPACE)
                .orElseThrow();
        assertFalse(afterRemove.queryModels().containsKey(QUERY_MODEL));
        assertTrue(afterRemove.queryModels().containsKey(SIBLING_QUERY_MODEL));
        assertEquals(nativeRows(SIBLING_TABLE),
                executeQuery(SIBLING_QUERY_MODEL).rows());
        assertBundleOwner(SIBLING_QUERY_MODEL, SIBLING_BUNDLE_NAME);
        assertTableBundleOwner(SIBLING_TABLE_MODEL, SIBLING_BUNDLE_NAME);
    }

    private CatalogRefreshRequest modelRefreshRequest() {
        return CatalogRefreshRequest.models(
                NAMESPACE,
                Set.of(CatalogModelKey.query(QUERY_MODEL)),
                CatalogRefreshTrigger.EXPLICIT_RECOVERY);
    }

    private Observation executeQuery() {
        return executeQuery(QUERY_MODEL);
    }

    private Observation executeQuery(String queryModel) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(queryModel);
        request.setColumns(List.of("recordId", "payload"));
        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 100), null);
        context.setNamespace(NAMESPACE);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.noOptimization());

        DbQueryResult result = queryFacade.queryModelResult(context);
        assertNotNull(result);
        assertNotNull(result.getQueryEngine());
        assertSame(context.getQueryModel(),
                result.getQueryEngine().getJdbcQueryModel());

        return new Observation(
                context.getCatalogIdentity(),
                context.getQueryModel(),
                context.getDatasourceBindingIdentities(),
                context.isBindingIdentityComplete(),
                resultRows(result));
    }

    private void assertBundleOwner(String queryModel, String bundleName) {
        ModelProvenance provenance = catalogSnapshotStore
                .readCurrent(NAMESPACE)
                .orElseThrow()
                .queryModelProvenance(queryModel)
                .orElseThrow();
        assertNotNull(provenance.source());
        assertEquals(bundleName, provenance.source().bundleName());
        assertEquals(NAMESPACE, provenance.source().namespace());
    }

    private void assertTableBundleOwner(String tableModel, String bundleName) {
        ModelProvenance provenance = catalogSnapshotStore
                .readCurrent(NAMESPACE)
                .orElseThrow()
                .modelProvenance(CatalogModelKey.table(tableModel))
                .orElseThrow();
        assertNotNull(provenance.source());
        assertEquals(bundleName, provenance.source().bundleName());
        assertEquals(NAMESPACE, provenance.source().namespace());
    }

    private void assertObservation(
            Observation observation,
            CatalogIdentity identity,
            QueryModel model,
            List<FixtureRow> expectedRows
    ) {
        assertEquals(identity, observation.identity());
        assertSame(model, observation.model());
        assertEquals(
                Map.of(BINDING_IDENTITY.bindingKey(), BINDING_IDENTITY),
                observation.bindings());
        assertTrue(observation.bindingIdentityComplete());
        assertEquals(expectedRows, observation.rows());
    }

    private List<FixtureRow> resultRows(DbQueryResult result) {
        assertNotNull(result.getPagingResult());
        assertNotNull(result.getPagingResult().getItems());
        List<FixtureRow> rows = new ArrayList<>();
        for (Object item : result.getPagingResult().getItems()) {
            assertTrue(item instanceof Map<?, ?>,
                    () -> "unexpected query row type: " + item);
            Map<?, ?> row = (Map<?, ?>) item;
            rows.add(new FixtureRow(
                    numberValue(row, "recordId").longValue(),
                    String.valueOf(value(row, "payload"))));
        }
        rows.sort(Comparator.comparingLong(FixtureRow::recordId));
        return List.copyOf(rows);
    }

    private Number numberValue(Map<?, ?> row, String key) {
        Object value = value(row, key);
        assertTrue(value instanceof Number,
                () -> "query value is not numeric: key=" + key + ", value=" + value);
        return (Number) value;
    }

    private Object value(Map<?, ?> row, String expectedKey) {
        return row.entrySet().stream()
                .filter(entry -> String.valueOf(entry.getKey())
                        .equalsIgnoreCase(expectedKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing query result key " + expectedKey + " in " + row));
    }

    private List<FixtureRow> nativeRows(String tableName) {
        return jdbcTemplate.query(
                "SELECT record_id, payload FROM " + tableName + " ORDER BY record_id",
                (resultSet, rowNumber) -> new FixtureRow(
                        resultSet.getLong("record_id"),
                        resultSet.getString("payload")));
    }

    private void recreateSqliteTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + OLD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + NEW_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + SIBLING_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + OLD_TABLE
                + " (record_id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE " + NEW_TABLE
                + " (record_id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE " + SIBLING_TABLE
                + " (record_id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
        jdbcTemplate.batchUpdate(
                "INSERT INTO " + OLD_TABLE + " (record_id, payload) VALUES (?, ?)",
                List.<Object[]>of(
                        new Object[]{101L, "old-alpha"},
                        new Object[]{102L, "old-beta"}));
        jdbcTemplate.batchUpdate(
                "INSERT INTO " + NEW_TABLE + " (record_id, payload) VALUES (?, ?)",
                List.<Object[]>of(
                        new Object[]{201L, "new-alpha"},
                        new Object[]{202L, "new-beta"},
                        new Object[]{203L, "new-gamma"}));
        jdbcTemplate.batchUpdate(
                "INSERT INTO " + SIBLING_TABLE
                        + " (record_id, payload) VALUES (?, ?)",
                List.<Object[]>of(
                        new Object[]{301L, "sibling-alpha"},
                        new Object[]{302L, "sibling-beta"}));
    }

    private void assertSqliteBinding() throws Exception {
        assertSame(jdbcTemplate.getDataSource(),
                trackedSqliteResolver.resolveBinding(BINDING_NAME).dataSource());
        assertEquals(BindingCurrentness.CURRENT,
                trackedSqliteResolver.currentness(BINDING_IDENTITY));
        try (Connection connection = Objects.requireNonNull(
                jdbcTemplate.getDataSource()).getConnection()) {
            assertTrue(connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT).contains("sqlite"));
        }
    }

    private void registerTestBundle() throws IOException {
        Path bundleRoot = temporaryDirectory.resolve(BUNDLE_NAME);
        Path modelDirectory = bundleRoot.resolve("model");
        Path queryDirectory = bundleRoot.resolve("query");
        Files.createDirectories(modelDirectory);
        Files.createDirectories(queryDirectory);
        Files.writeString(modelDirectory.resolve(TABLE_MODEL + ".tm"), """
                import {currentTableName} from '@v933AtomicRefreshSelector';

                export const model = {
                    name: 'V933AtomicRefreshTableModel',
                    caption: 'V933 atomic refresh SQLite fixture',
                    tableName: currentTableName(),
                    dataSourceName: 'v933-refresh-query-it-sqlite',
                    idColumn: 'record_id',
                    properties: [
                        {
                            column: 'record_id',
                            name: 'recordId',
                            caption: 'Record ID',
                            type: 'LONG'
                        },
                        {
                            column: 'payload',
                            name: 'payload',
                            caption: 'Payload',
                            type: 'STRING'
                        }
                    ]
                };
                """);
        Files.writeString(queryDirectory.resolve(QUERY_MODEL + ".qm"), """
                const fixture = loadTableModel('V933AtomicRefreshTableModel');

                export const queryModel = {
                    name: 'V933AtomicRefreshQueryModel',
                    caption: 'V933 atomic refresh query fixture',
                    description: 'Real SQLite model used only by CatalogRefreshQueryIT',
                    model: fixture,
                    columnGroups: [
                        {
                            caption: 'Fixture fields',
                            items: [
                                { ref: fixture.recordId },
                                { ref: fixture.payload }
                            ]
                        }
                    ],
                    accesses: []
                };
                """);

        assertTrue(bundlesContext.addExternalBundle(
                BUNDLE_NAME, NAMESPACE, bundleRoot.toString(), false),
                "failed to register the isolated lifecycle test bundle");
    }

    private void writeStaticBundle(
            Path bundleRoot,
            String tableModel,
            String queryModel,
            String tableName
    ) throws IOException {
        Path modelDirectory = bundleRoot.resolve("model");
        Path queryDirectory = bundleRoot.resolve("query");
        Files.createDirectories(modelDirectory);
        Files.createDirectories(queryDirectory);
        Files.writeString(modelDirectory.resolve(tableModel + ".tm"), """
                export const model = {
                    name: '%s',
                    caption: 'Multi-bundle SQLite fixture',
                    tableName: '%s',
                    dataSourceName: '%s',
                    idColumn: 'record_id',
                    properties: [
                        {
                            column: 'record_id',
                            name: 'recordId',
                            caption: 'Record ID',
                            type: 'LONG'
                        },
                        {
                            column: 'payload',
                            name: 'payload',
                            caption: 'Payload',
                            type: 'STRING'
                        }
                    ]
                };
                """.formatted(tableModel, tableName, BINDING_NAME));
        Files.writeString(queryDirectory.resolve(queryModel + ".qm"), """
                const fixture = loadTableModel('%s');

                export const queryModel = {
                    name: '%s',
                    caption: 'Multi-bundle query fixture',
                    description: 'Real SQLite model used by CatalogRefreshQueryIT',
                    model: fixture,
                    columnGroups: [
                        {
                            caption: 'Fixture fields',
                            items: [
                                { ref: fixture.recordId },
                                { ref: fixture.payload }
                            ]
                        }
                    ],
                    accesses: []
                };
                """.formatted(tableModel, queryModel));
    }

    private void removeTestBundleIfPresent() {
        removeBundleIfPresent(BUNDLE_NAME);
    }

    private void removeBundleIfPresent(String bundleName) {
        if (bundlesContext.containBundle(bundleName)) {
            assertTrue(bundlesContext.removeBundle(bundleName),
                    "failed to remove lifecycle test bundle " + bundleName);
        }
    }

    private record FixtureRow(long recordId, String payload) {
    }

    private record Observation(
            CatalogIdentity identity,
            QueryModel model,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean bindingIdentityComplete,
            List<FixtureRow> rows
    ) {
        private Observation {
            bindings = Map.copyOf(bindings);
            rows = List.copyOf(rows);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LifecycleITConfiguration {

        @Bean(name = SELECTOR_BEAN)
        RefreshModelSelector refreshModelSelector() {
            return new RefreshModelSelector();
        }

        @Bean
        @Primary
        TrackedSqliteResolver v933TrackedSqliteResolver(DataSource dataSource) {
            return new TrackedSqliteResolver(dataSource);
        }

        @Bean
        ReaderPinGateStep v933ReaderPinGateStep() {
            return new ReaderPinGateStep();
        }
    }

    static final class TrackedSqliteResolver implements NamedDataSourceResolver {

        private final DataSource dataSource;
        private final Object publicationMonitor = new Object();

        private TrackedSqliteResolver(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        }

        @Override
        public DataSource resolve(String name) {
            return BINDING_NAME.equals(name) || "odoo".equals(name)
                    ? dataSource
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            if (BINDING_NAME.equals(name)) {
                return ResolvedDatasourceBinding.tracked(
                        dataSource, BINDING_IDENTITY);
            }
            DataSource resolved = resolve(name);
            return resolved == null
                    ? null
                    : ResolvedDatasourceBinding.untracked(resolved);
        }

        @Override
        public boolean isConfigured(String name) {
            return resolve(name) != null;
        }

        @Override
        public BindingCurrentness currentness(
                DatasourceBindingIdentity identity
        ) {
            return BINDING_IDENTITY.equals(identity)
                    ? BindingCurrentness.CURRENT
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
                    if (currentness(identity) != BindingCurrentness.CURRENT) {
                        throw new IllegalStateException(
                                "test datasource binding is not current: " + identity);
                    }
                }
                return publication.get();
            }
        }
    }

    public static final class RefreshModelSelector {

        private final AtomicReference<String> tableName =
                new AtomicReference<>(OLD_TABLE);
        private final AtomicBoolean failBuilds = new AtomicBoolean();
        private final AtomicReference<BuildGate> nextBuildGate =
                new AtomicReference<>();

        public String currentTableName() {
            BuildGate gate = nextBuildGate.getAndSet(null);
            if (gate != null) {
                gate.buildEntered.countDown();
                DeterministicConcurrencyTestSupport.await(
                        gate.releaseBuild, "refresh candidate build release");
            }
            if (failBuilds.get()) {
                throw new IllegalStateException(
                        "controlled V933 query-model candidate failure");
            }
            return tableName.get();
        }

        BuildGate selectNewTableAndBlockNextBuild() {
            tableName.set(NEW_TABLE);
            failBuilds.set(false);
            BuildGate gate = new BuildGate();
            if (!nextBuildGate.compareAndSet(null, gate)) {
                throw new IllegalStateException("a selector build gate is already armed");
            }
            return gate;
        }

        void failBuilds() {
            failBuilds.set(true);
        }

        void selectOldTable() {
            failBuilds.set(false);
            tableName.set(OLD_TABLE);
        }

        void releaseAndReset() {
            BuildGate gate = nextBuildGate.getAndSet(null);
            if (gate != null) {
                gate.releaseBuild();
            }
            selectOldTable();
        }
    }

    static final class BuildGate {

        private final CountDownLatch buildEntered = new CountDownLatch(1);
        private final CountDownLatch releaseBuild = new CountDownLatch(1);

        void awaitBuildEntered() {
            DeterministicConcurrencyTestSupport.await(
                    buildEntered, "refresh candidate build entry");
        }

        void releaseBuild() {
            releaseBuild.countDown();
        }
    }

    static final class ReaderPinGateStep implements DataSetResultStep {

        private final AtomicReference<ReaderGate> activeGate =
                new AtomicReference<>();

        @Override
        public int beforeQueryOrder() {
            // Run after request/model validation but before the MAX_VALUE cache step.
            return 1_000;
        }

        @Override
        public int beforeQuery(ModelResultContext context) {
            QueryModel model = context.getQueryModel();
            if (model == null
                    || !QUERY_MODEL.equals(model.getName())
                    || !NAMESPACE.equals(context.getNamespace())) {
                return CONTINUE;
            }
            ReaderGate gate = activeGate.get();
            if (gate != null && gate.claimReader()) {
                gate.readerEntered.countDown();
                DeterministicConcurrencyTestSupport.await(
                        gate.releaseReaders, "pinned reader release");
            }
            return CONTINUE;
        }

        ReaderGate arm(int readers) {
            ReaderGate gate = new ReaderGate(readers);
            ReaderGate previous = activeGate.getAndSet(gate);
            if (previous != null) {
                previous.releaseReaders();
            }
            return gate;
        }

        void releaseAndDisarm() {
            ReaderGate gate = activeGate.getAndSet(null);
            if (gate != null) {
                gate.releaseReaders();
            }
        }
    }

    static final class ReaderGate {

        private final AtomicInteger remainingClaims;
        private final CountDownLatch readerEntered;
        private final CountDownLatch releaseReaders = new CountDownLatch(1);

        private ReaderGate(int readers) {
            if (readers < 1) {
                throw new IllegalArgumentException("readers must be positive");
            }
            this.remainingClaims = new AtomicInteger(readers);
            this.readerEntered = new CountDownLatch(readers);
        }

        boolean claimReader() {
            int current;
            do {
                current = remainingClaims.get();
                if (current == 0) {
                    return false;
                }
            } while (!remainingClaims.compareAndSet(current, current - 1));
            return true;
        }

        void awaitAllEntered() {
            DeterministicConcurrencyTestSupport.await(
                    readerEntered, "all old readers to pin their catalog");
        }

        void releaseReaders() {
            releaseReaders.countDown();
        }
    }
}
