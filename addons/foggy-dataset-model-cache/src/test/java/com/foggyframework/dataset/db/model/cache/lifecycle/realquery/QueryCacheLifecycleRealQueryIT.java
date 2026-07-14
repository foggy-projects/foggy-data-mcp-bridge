package com.foggyframework.dataset.db.model.cache.lifecycle.realquery;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.provider.CaffeineQueryCacheProvider;
import com.foggyframework.dataset.db.model.cache.provider.RedisQueryCacheProvider;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-query cache lifecycle evidence. The coordinated runner executes this
 * same class once with Caffeine and once with Redis; every cache observation
 * still traverses the production Spring {@link QueryFacade} and is checked
 * against an independent native SQLite oracle.
 */
@SpringBootTest(
        classes = {
                QueryCacheLifecycleRealQueryIT.CacheRealQueryApplication.class,
                QueryCacheLifecycleRealQueryIT.CacheRealQueryConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:v933-cache-real-query?mode=memory&cache=shared",
                "spring.datasource.driver-class-name=org.sqlite.JDBC",
                "spring.datasource.hikari.minimum-idle=1",
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.sql.init.mode=never",
                "foggy.dataset.show-sql=false",
                "foggy.query-cache.enabled=true",
                "foggy.query-cache.default-ttl=10m",
                "foggy.query-cache.max-result-size=100",
                "foggy.query-cache.caffeine.record-stats=true",
                "foggy.query-cache.redis.ttl-jitter=false"
        })
class QueryCacheLifecycleRealQueryIT {

    private static final String NAMESPACE = "v933-cache-real-query";
    private static final String BUNDLE_NAME = "v933-cache-real-query-bundle";
    private static final String BINDING_NAME = "v933-cache-real-query-sqlite";
    private static final String SELECTOR_BEAN = "v933CacheLifecycleTableSelector";
    private static final String TABLE_MODEL = "V933CacheLifecycleTableModel";
    private static final String QUERY_MODEL = "V933CacheLifecycleQueryModel";
    private static final String OLD_TABLE = "v933_cache_lifecycle_old";
    private static final String NEW_TABLE = "v933_cache_lifecycle_new";
    private static final String AUTHORIZATION = "Bearer v933-cache-real-query";
    private static final String OLD_SENTINEL = "OLD_ONLY";
    private static final String NEW_SENTINEL = "NEW_ONLY";
    private static final List<String> COLUMNS = List.of("recordId", "payload");
    private static final DatasourceBindingIdentity BINDING_IDENTITY =
            new DatasourceBindingIdentity(
                    "test:v933-cache-real-query",
                    "jdbc:sqlite-real",
                    new DatasourceBindingGeneration(
                            "v933-cache-real-query-binding-generation"));

    @Autowired
    private QueryFacade queryFacade;

    @Autowired
    private CatalogRefreshCoordinator refreshCoordinator;

    @Autowired
    private CatalogSnapshotStore catalogSnapshotStore;

    @Autowired
    private SystemBundlesContext bundlesContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryCacheProvider queryCacheProvider;

    @Autowired
    private QueryCacheProperties cacheProperties;

    @Autowired
    private CacheLifecycleTableSelector tableSelector;

    @Autowired
    private TrackedSqliteResolver trackedSqliteResolver;

    @Autowired
    private ApplicationContext applicationContext;

    @TempDir
    Path temporaryDirectory;

    @DynamicPropertySource
    static void cacheProviderProperties(DynamicPropertyRegistry registry) {
        String provider = requiredProvider();
        registry.add("foggy.query-cache.type", () -> provider);
        if ("redis".equals(provider)) {
            String host = requiredProperty("v933.redis.host");
            int port = requiredPort("v933.redis.port");
            String keyPrefix = requiredProperty("v933.redis.key-prefix");
            registry.add("spring.data.redis.host", () -> host);
            registry.add("spring.data.redis.port", () -> port);
            registry.add("foggy.query-cache.key-prefix", () -> keyPrefix);
        } else {
            registry.add("foggy.query-cache.key-prefix",
                    () -> "v933:cache-real-query:caffeine:");
        }
    }

    @BeforeEach
    void prepareRealQueryFixture() throws Exception {
        NamespaceContext.clear();
        tableSelector.selectOldTable();
        removeBundleIfPresent();
        catalogSnapshotStore.clearNamespace(NAMESPACE);
        recreateSqliteTables();
        assertTrackedSqliteBinding();
        assertProductionAutoConfiguration();
        queryCacheProvider.evictAll();
        assertEquals(0L, layerEntryCount("l1"));
        assertEquals(0L, layerEntryCount("l2"));
        registerBundle();
        assertSnapshot(currentSnapshot());
    }

    @AfterEach
    void cleanRealQueryFixture() {
        NamespaceContext.clear();
        tableSelector.selectOldTable();
        removeBundleIfPresent();
        catalogSnapshotStore.clearNamespace(NAMESPACE);
        queryCacheProvider.evictAll();
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + OLD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + NEW_TABLE);
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void l1MissWriteHitThenAtomicRefreshUsesANewGenerationMissWriteHit() {
        CatalogSnapshot oldSnapshot = currentSnapshot();
        List<FixtureRow> nativeOld = nativeRows(OLD_TABLE);
        List<FixtureRow> nativeNew = nativeRows(NEW_TABLE);
        assertNotEquals(nativeOld, nativeNew);

        CacheStats beforeOldMiss = cacheStats();
        Observation oldMiss = executeQuery(cacheConfig(true, false));
        assertPhase(oldMiss, oldSnapshot, nativeOld,
                OLD_SENTINEL, NEW_SENTINEL, false, false, true);
        CacheStats afterOldMiss = cacheStats();
        assertStatsDelta(beforeOldMiss, afterOldMiss, 0, 1, 0, 0);
        assertEquals(1L, layerEntryCount("l1"),
                "the old-generation L1 miss must write one entry");
        assertEquals(0L, layerEntryCount("l2"));

        Observation oldHit = executeQuery(cacheConfig(true, false));
        assertPhase(oldHit, oldSnapshot, nativeOld,
                OLD_SENTINEL, NEW_SENTINEL, true, false, false);
        CacheStats afterOldHit = cacheStats();
        assertStatsDelta(afterOldMiss, afterOldHit, 1, 0, 0, 0);
        assertEquals(1L, layerEntryCount("l1"));

        CatalogSnapshot newSnapshot = refreshAtomically(oldSnapshot);

        Observation newMiss = executeQuery(cacheConfig(true, false));
        assertPhase(newMiss, newSnapshot, nativeNew,
                NEW_SENTINEL, OLD_SENTINEL, false, false, true);
        CacheStats afterNewMiss = cacheStats();
        assertStatsDelta(afterOldHit, afterNewMiss, 0, 1, 0, 0);
        assertEquals(2L, layerEntryCount("l1"),
                "the new catalog generation must write a distinct L1 entry");

        Observation newHit = executeQuery(cacheConfig(true, false));
        assertPhase(newHit, newSnapshot, nativeNew,
                NEW_SENTINEL, OLD_SENTINEL, true, false, false);
        CacheStats afterNewHit = cacheStats();
        assertStatsDelta(afterNewMiss, afterNewHit, 1, 0, 0, 0);
        assertEquals(2L, layerEntryCount("l1"));
        assertEquals(0L, layerEntryCount("l2"));
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void l2WithL1ExplicitlyOffMissesWritesHitsAndRefreshesByCatalogGeneration() {
        CatalogSnapshot oldSnapshot = currentSnapshot();
        List<FixtureRow> nativeOld = nativeRows(OLD_TABLE);
        List<FixtureRow> nativeNew = nativeRows(NEW_TABLE);
        assertNotEquals(nativeOld, nativeNew);

        CacheStats beforeOldMiss = cacheStats();
        Observation oldMiss = executeQuery(cacheConfig(false, true));
        assertPhase(oldMiss, oldSnapshot, nativeOld,
                OLD_SENTINEL, NEW_SENTINEL, false, false, true);
        CacheStats afterOldMiss = cacheStats();
        assertStatsDelta(beforeOldMiss, afterOldMiss, 0, 0, 0, 1);
        assertEquals(0L, layerEntryCount("l1"),
                "L1 must remain physically empty in the L2 lane");
        assertEquals(1L, layerEntryCount("l2"),
                "the old-generation L2 miss must write one entry");

        Observation oldHit = executeQuery(cacheConfig(false, true));
        assertPhase(oldHit, oldSnapshot, nativeOld,
                OLD_SENTINEL, NEW_SENTINEL, false, true, true);
        CacheStats afterOldHit = cacheStats();
        assertStatsDelta(afterOldMiss, afterOldHit, 0, 0, 1, 0);
        assertEquals(0L, layerEntryCount("l1"));
        assertEquals(1L, layerEntryCount("l2"));

        CatalogSnapshot newSnapshot = refreshAtomically(oldSnapshot);

        Observation newMiss = executeQuery(cacheConfig(false, true));
        assertPhase(newMiss, newSnapshot, nativeNew,
                NEW_SENTINEL, OLD_SENTINEL, false, false, true);
        CacheStats afterNewMiss = cacheStats();
        assertStatsDelta(afterOldHit, afterNewMiss, 0, 0, 0, 1);
        assertEquals(0L, layerEntryCount("l1"));
        assertEquals(2L, layerEntryCount("l2"),
                "the new catalog generation must write a distinct L2 entry");

        Observation newHit = executeQuery(cacheConfig(false, true));
        assertPhase(newHit, newSnapshot, nativeNew,
                NEW_SENTINEL, OLD_SENTINEL, false, true, true);
        CacheStats afterNewHit = cacheStats();
        assertStatsDelta(afterNewMiss, afterNewHit, 0, 0, 1, 0);
        assertEquals(0L, layerEntryCount("l1"));
        assertEquals(2L, layerEntryCount("l2"));
    }

    private Observation executeQuery(ModelResultContext.QueryCacheConfig cacheConfig) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(COLUMNS);
        request.setStrictColumns(true);
        request.setReturnTotal(false);
        request.setOrderBy(List.of(orderBy("recordId", "ASC")));

        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, 100), null);
        context.setNamespace(NAMESPACE);
        context.setSecurityContext(
                ModelResultContext.SecurityContext.fromAuthorization(AUTHORIZATION));
        context.setCacheConfig(cacheConfig);

        DbQueryResult result = queryFacade.queryModelResult(context);
        assertNotNull(result);
        assertNotNull(result.getPagingResult());
        assertNotNull(result.getPagingResult().getItems());
        if (result.getQueryEngine() != null) {
            assertSame(context.getQueryModel(),
                    result.getQueryEngine().getJdbcQueryModel());
        }

        return new Observation(
                context.getCatalogIdentity(),
                context.getCanonicalModelName(),
                context.getQueryModel(),
                context.getDatasourceBindingIdentities(),
                context.isBindingIdentityComplete(),
                context.getCacheConfig().isL1CacheHit(),
                context.getCacheConfig().isL2CacheHit(),
                context.getCacheConfig().getProvider(),
                result.getQueryEngine() != null,
                facadeRows(result));
    }

    private void assertPhase(
            Observation observation,
            CatalogSnapshot expectedSnapshot,
            List<FixtureRow> nativeRows,
            String expectedSentinel,
            String forbiddenSentinel,
            boolean expectedL1Hit,
            boolean expectedL2Hit,
            boolean expectedQueryEngine
    ) {
        CatalogIdentity expectedIdentity = expectedSnapshot.identity();
        assertEquals(NAMESPACE, observation.catalogIdentity().namespace());
        assertEquals(expectedIdentity, observation.catalogIdentity());
        assertFalse(observation.catalogIdentity().generation().value().isBlank());
        assertFalse(observation.catalogIdentity().sourceRevision().value().isBlank());
        assertEquals(QUERY_MODEL, observation.canonicalModelName());
        assertSame(expectedSnapshot.queryModels().get(QUERY_MODEL),
                observation.queryModel());
        assertInstanceOf(JdbcQueryModel.class, observation.queryModel());
        assertEquals(Map.of(BINDING_IDENTITY.bindingKey(), BINDING_IDENTITY),
                observation.bindings());
        assertTrue(observation.bindingIdentityComplete());
        assertSame(queryCacheProvider, observation.provider());
        assertEquals(expectedL1Hit, observation.l1CacheHit());
        assertEquals(expectedL2Hit, observation.l2CacheHit());
        assertEquals(expectedQueryEngine, observation.queryEnginePresent());
        assertEquals(nativeRows, observation.rows(),
                "QueryFacade rows/values/order must exactly match native SQLite");
        assertFalse(observation.rows().isEmpty());
        assertTrue(observation.rows().stream()
                .allMatch(row -> row.payload().startsWith(expectedSentinel + "-")));
        assertTrue(observation.rows().stream()
                .noneMatch(row -> row.payload().contains(forbiddenSentinel)));
    }

    private CatalogSnapshot refreshAtomically(CatalogSnapshot before) {
        tableSelector.selectNewTable();
        CatalogRefreshResult refreshResult = refreshCoordinator.refresh(
                CatalogRefreshRequest.models(
                        NAMESPACE,
                        Set.of(CatalogModelKey.query(QUERY_MODEL)),
                        CatalogRefreshTrigger.EXPLICIT_RECOVERY));
        CatalogSnapshot after = currentSnapshot();
        assertEquals(before.identity(), refreshResult.beforeIdentity());
        assertEquals(after.identity(), refreshResult.afterIdentity());
        assertNotEquals(before.identity().generation(),
                after.identity().generation());
        assertSame(after, catalogSnapshotStore.readCurrent(NAMESPACE).orElseThrow());
        assertSnapshot(after);
        return after;
    }

    private void assertSnapshot(CatalogSnapshot snapshot) {
        assertEquals(NAMESPACE, snapshot.identity().namespace());
        assertFalse(snapshot.identity().generation().value().isBlank());
        assertFalse(snapshot.identity().sourceRevision().value().isBlank());
        assertTrue(snapshot.queryModels().containsKey(QUERY_MODEL));
        assertTrue(snapshot.tableModels().containsKey(TABLE_MODEL));
        ModelProvenance provenance = snapshot.queryModelProvenance(QUERY_MODEL)
                .orElseThrow();
        assertTrue(provenance.bindingIdentityComplete());
        assertEquals(Map.of(BINDING_IDENTITY.bindingKey(), BINDING_IDENTITY),
                provenance.datasourceBindings());
    }

    private CatalogSnapshot currentSnapshot() {
        return catalogSnapshotStore.readCurrent(NAMESPACE)
                .orElseThrow(() -> new AssertionError(
                        "missing cache real-query catalog snapshot"));
    }

    private ModelResultContext.QueryCacheConfig cacheConfig(
            boolean l1Enabled,
            boolean l2Enabled
    ) {
        return ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(l1Enabled)
                .l2Enabled(l2Enabled)
                .preAggEnabled(false)
                .build();
    }

    private OrderRequestDef orderBy(String field, String order) {
        OrderRequestDef orderRequest = new OrderRequestDef();
        orderRequest.setField(field);
        orderRequest.setDir(order);
        return orderRequest;
    }

    private List<FixtureRow> facadeRows(DbQueryResult result) {
        List<FixtureRow> rows = new ArrayList<>();
        for (Object item : result.getPagingResult().getItems()) {
            Map<?, ?> raw = assertInstanceOf(Map.class, item,
                    "QueryFacade row must be a map");
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((key, value) -> row.put(String.valueOf(key), value));
            assertEquals(COLUMNS, new ArrayList<>(row.keySet()),
                    "QueryFacade column keys/order must be exact");
            rows.add(new FixtureRow(
                    numericValue(row.get("recordId")).longValueExact(),
                    String.valueOf(row.get("payload"))));
        }
        return List.copyOf(rows);
    }

    private List<FixtureRow> nativeRows(String tableName) {
        return jdbcTemplate.query(
                "SELECT record_id, payload FROM " + tableName
                        + " ORDER BY record_id ASC",
                resultSet -> {
                    List<FixtureRow> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        LinkedHashMap<String, Object> nativeRow = new LinkedHashMap<>();
                        nativeRow.put("recordId", resultSet.getObject(1));
                        nativeRow.put("payload", resultSet.getObject(2));
                        assertEquals(COLUMNS, new ArrayList<>(nativeRow.keySet()));
                        rows.add(new FixtureRow(
                                numericValue(nativeRow.get("recordId")).longValueExact(),
                                String.valueOf(nativeRow.get("payload"))));
                    }
                    return List.copyOf(rows);
                });
    }

    private static BigDecimal numericValue(Object value) {
        assertInstanceOf(Number.class, value,
                "recordId must remain a numeric JDBC value");
        return new BigDecimal(value.toString()).stripTrailingZeros();
    }

    private CacheStats cacheStats() {
        Map<String, Object> stats = queryCacheProvider.getStats();
        assertEquals(requiredProvider(), stats.get("type"));
        assertEquals(Boolean.TRUE, stats.get("enabled"));
        return new CacheStats(
                stat(stats, "l1Hits"),
                stat(stats, "l1Misses"),
                stat(stats, "l2Hits"),
                stat(stats, "l2Misses"));
    }

    private long stat(Map<String, Object> stats, String name) {
        Number value = assertInstanceOf(Number.class, stats.get(name),
                "missing numeric cache statistic " + name);
        return value.longValue();
    }

    private void assertStatsDelta(
            CacheStats before,
            CacheStats after,
            long l1Hits,
            long l1Misses,
            long l2Hits,
            long l2Misses
    ) {
        assertEquals(l1Hits, after.l1Hits() - before.l1Hits(), "L1 hit delta");
        assertEquals(l1Misses, after.l1Misses() - before.l1Misses(),
                "L1 miss delta");
        assertEquals(l2Hits, after.l2Hits() - before.l2Hits(), "L2 hit delta");
        assertEquals(l2Misses, after.l2Misses() - before.l2Misses(),
                "L2 miss delta");
    }

    private long layerEntryCount(String layer) {
        if ("caffeine".equals(requiredProvider())) {
            return stat(queryCacheProvider.getStats(),
                    "l1".equals(layer) ? "l1EstimatedSize" : "l2EstimatedSize");
        }
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> template = applicationContext.getBean(
                "foggyQueryCacheRedisTemplate", RedisTemplate.class);
        Set<String> keys = template.keys(cacheProperties.getKeyPrefix() + layer + ":*");
        assertNotNull(keys, "Redis KEYS must return a concrete key set");
        return keys.size();
    }

    private void assertProductionAutoConfiguration() {
        Map<String, QueryCacheProvider> providers =
                applicationContext.getBeansOfType(QueryCacheProvider.class);
        assertEquals(1, providers.size());
        assertSame(queryCacheProvider, providers.values().iterator().next());
        assertEquals(requiredProvider(), cacheProperties.getType());
        if ("caffeine".equals(requiredProvider())) {
            assertEquals(Set.of("caffeineQueryCacheProvider"), providers.keySet());
            assertInstanceOf(CaffeineQueryCacheProvider.class, queryCacheProvider);
            assertFalse(applicationContext.containsBean("foggyQueryCacheRedisTemplate"));
        } else {
            assertEquals(Set.of("redisQueryCacheProvider"), providers.keySet());
            assertInstanceOf(RedisQueryCacheProvider.class, queryCacheProvider);
            RedisTemplate<?, ?> template = applicationContext.getBean(
                    "foggyQueryCacheRedisTemplate", RedisTemplate.class);
            assertInstanceOf(StringRedisSerializer.class,
                    template.getKeySerializer());
            assertInstanceOf(GenericJackson2JsonRedisSerializer.class,
                    template.getValueSerializer());
            assertEquals(requiredProperty("v933.redis.key-prefix"),
                    cacheProperties.getKeyPrefix());
        }
    }

    private void assertTrackedSqliteBinding() throws Exception {
        ResolvedDatasourceBinding named =
                trackedSqliteResolver.resolveBinding(BINDING_NAME);
        ResolvedDatasourceBinding processDefault =
                trackedSqliteResolver.resolveProcessLocalDefaultBinding();
        assertSame(jdbcTemplate.getDataSource(), named.dataSource());
        assertSame(jdbcTemplate.getDataSource(), processDefault.dataSource());
        assertEquals(BINDING_IDENTITY, named.identity());
        assertEquals(BINDING_IDENTITY, processDefault.identity());
        assertEquals(BindingCurrentness.CURRENT,
                trackedSqliteResolver.currentness(BINDING_IDENTITY));
        try (Connection connection = Objects.requireNonNull(
                jdbcTemplate.getDataSource()).getConnection()) {
            assertTrue(connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT).contains("sqlite"));
        }
    }

    private void recreateSqliteTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + OLD_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + NEW_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + OLD_TABLE
                + " (record_id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE " + NEW_TABLE
                + " (record_id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
        jdbcTemplate.batchUpdate(
                "INSERT INTO " + OLD_TABLE + " (record_id, payload) VALUES (?, ?)",
                List.<Object[]>of(
                        new Object[]{101L, OLD_SENTINEL + "-alpha"},
                        new Object[]{102L, OLD_SENTINEL + "-beta"}));
        jdbcTemplate.batchUpdate(
                "INSERT INTO " + NEW_TABLE + " (record_id, payload) VALUES (?, ?)",
                List.<Object[]>of(
                        new Object[]{201L, NEW_SENTINEL + "-alpha"},
                        new Object[]{202L, NEW_SENTINEL + "-beta"},
                        new Object[]{203L, NEW_SENTINEL + "-gamma"}));
    }

    private void registerBundle() throws IOException {
        Path root = temporaryDirectory.resolve(BUNDLE_NAME);
        Path modelDirectory = root.resolve("model");
        Path queryDirectory = root.resolve("query");
        Files.createDirectories(modelDirectory);
        Files.createDirectories(queryDirectory);
        Files.writeString(modelDirectory.resolve(TABLE_MODEL + ".tm"), """
                import {currentTableName} from '@v933CacheLifecycleTableSelector';

                export const model = {
                    name: 'V933CacheLifecycleTableModel',
                    caption: 'V933 cache lifecycle SQLite fixture',
                    tableName: currentTableName(),
                    dataSourceName: 'v933-cache-real-query-sqlite',
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
                const fixture = loadTableModel('V933CacheLifecycleTableModel');

                export const queryModel = {
                    name: 'V933CacheLifecycleQueryModel',
                    caption: 'V933 cache lifecycle real query',
                    description: 'Real SQLite cache lifecycle fixture',
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
                BUNDLE_NAME, NAMESPACE, root.toString(), false));
    }

    private void removeBundleIfPresent() {
        if (bundlesContext.containBundle(BUNDLE_NAME)) {
            assertTrue(bundlesContext.removeBundle(BUNDLE_NAME));
        }
    }

    private static String requiredProvider() {
        String provider = requiredProperty("v933.cache.provider").toLowerCase(Locale.ROOT);
        if (!Set.of("caffeine", "redis").contains(provider)) {
            throw new IllegalStateException(
                    "v933.cache.provider must be caffeine or redis, got " + provider);
        }
        return provider;
    }

    private static int requiredPort(String name) {
        String value = requiredProperty(name);
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new IllegalStateException(name + " is outside the TCP port range");
            }
            return port;
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException(name + " must be an integer", invalid);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing required system property -D" + name);
        }
        return value.trim();
    }

    private record FixtureRow(long recordId, String payload) {
    }

    private record CacheStats(
            long l1Hits,
            long l1Misses,
            long l2Hits,
            long l2Misses
    ) {
    }

    private record Observation(
            CatalogIdentity catalogIdentity,
            String canonicalModelName,
            QueryModel queryModel,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean bindingIdentityComplete,
            boolean l1CacheHit,
            boolean l2CacheHit,
            QueryCacheProvider provider,
            boolean queryEnginePresent,
            List<FixtureRow> rows
    ) {
        private Observation {
            bindings = Map.copyOf(bindings);
            rows = List.copyOf(rows);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFoggyFramework(bundleName = "v933-cache-real-query-test")
    static class CacheRealQueryApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheRealQueryConfiguration {

        @Bean(name = SELECTOR_BEAN)
        CacheLifecycleTableSelector cacheLifecycleTableSelector() {
            return new CacheLifecycleTableSelector();
        }

        @Bean
        @Primary
        TrackedSqliteResolver trackedSqliteResolver(DataSource dataSource) {
            return new TrackedSqliteResolver(dataSource);
        }
    }

    public static final class CacheLifecycleTableSelector {

        private final AtomicReference<String> table =
                new AtomicReference<>(OLD_TABLE);

        public String currentTableName() {
            return table.get();
        }

        void selectOldTable() {
            table.set(OLD_TABLE);
        }

        void selectNewTable() {
            table.set(NEW_TABLE);
        }
    }

    static class TrackedSqliteResolver implements
            NamedDataSourceResolver,
            ProcessLocalDefaultDataSourceResolver {

        private final DataSource dataSource;
        private final Object publicationMonitor = new Object();

        private TrackedSqliteResolver(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        }

        @Override
        public DataSource resolve(String name) {
            return BINDING_NAME.equals(name) ? dataSource : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            return BINDING_NAME.equals(name)
                    ? ResolvedDatasourceBinding.tracked(dataSource, BINDING_IDENTITY)
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, BINDING_IDENTITY);
        }

        @Override
        public boolean isConfigured(String name) {
            return BINDING_NAME.equals(name);
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
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
                                "stale cache lifecycle test binding: " + identity);
                    }
                }
                return publication.get();
            }
        }
    }
}
