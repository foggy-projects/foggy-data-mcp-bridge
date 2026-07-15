package com.foggyframework.dataset.db.model.lifecycle.realquery;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Required-database real-query proof shared by the 9.3.3 regression gate and
 * the 9.3.4 five-database matrix.
 *
 * <p>The same owning test is executed once for each required 9.3.4 database:
 * SQLite, MySQL 5.7, MySQL 8, PostgreSQL 15 and SQL Server 2022. It uses the
 * production {@link QueryFacade}, a real TM/QM and
 * the profile's real {@link DataSource}; the native SQL is an independent
 * result oracle rather than the system under test.</p>
 */
@SpringBootTest(classes = {
        JdbcModelTestApplication.class,
        RequiredDatabaseQueryFacadeParityIT.TrackedBindingConfiguration.class
})
class RequiredDatabaseQueryFacadeParityIT {

    private static final Logger log =
            LoggerFactory.getLogger(RequiredDatabaseQueryFacadeParityIT.class);

    private static final String QUERY_MODEL = "FactSalesQueryModel";
    private static final String PARITY_ORDER_ID = "V934_PARITY_SENTINEL";
    private static final int PAGE_LIMIT = 25;
    private static final List<String> COLUMNS = List.of("orderId", "orderLineNo");
    private static final List<Integer> MUTUALLY_EXCLUSIVE_SENTINELS = List.of(1, 2);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryFacade queryFacade;

    @Autowired
    private QueryModelLoader queryModelLoader;

    @Autowired
    private TrackedRequiredDatabaseResolver trackedResolver;

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void requiredDatabaseExecutesQueryFacadeWithExactNativeParityAndLifecycleIdentity()
            throws Exception {
        RequiredDatabase requiredDatabase = RequiredDatabase.fromRequiredProperty();
        PhysicalDatabaseIdentity physicalIdentity;
        try (Connection connection = dataSource.getConnection()) {
            physicalIdentity = PhysicalDatabaseIdentity.capture(connection, requiredDatabase);
            requiredDatabase.verifyOrThrow(physicalIdentity);
        }

        assertEquals(requiredDatabase, trackedResolver.requiredDatabase());
        assertEquals(physicalIdentity, trackedResolver.physicalIdentity());
        assertSame(dataSource,
                trackedResolver.resolveProcessLocalDefaultBinding().dataSource());
        assertEquals(BindingCurrentness.CURRENT,
                trackedResolver.currentness(trackedResolver.defaultBindingIdentity()));

        CatalogResolution<QueryModel> resolution =
                queryModelLoader.resolveJdbcQueryModel(QUERY_MODEL, null);
        assertTrackedJdbcResolution(resolution, trackedResolver.defaultBindingIdentity());

        List<ParityObservation> observations = new ArrayList<>();
        for (int sentinel : MUTUALLY_EXCLUSIVE_SENTINELS) {
            observations.add(executeParity(sentinel, resolution));
        }

        assertEquals(MUTUALLY_EXCLUSIVE_SENTINELS,
                observations.stream().map(ParityObservation::sentinel).toList());
        assertTrue(observations.stream().allMatch(observation -> !observation.rows().isEmpty()),
                "both fixed sentinel fixtures must be non-empty");
        assertTrue(observations.get(0).rows().stream()
                        .allMatch(row -> numericValue(row.get("orderLineNo")).intValue() == 1),
                "sentinel lane one must contain only order_line_no=1");
        assertTrue(observations.get(1).rows().stream()
                        .allMatch(row -> numericValue(row.get("orderLineNo")).intValue() == 2),
                "sentinel lane two must contain only order_line_no=2");
        assertTrue(Collections.disjoint(
                        rowIdentities(observations.get(0).rows()),
                        rowIdentities(observations.get(1).rows())),
                "the two fixed sentinel lanes must be mutually exclusive");

        String probePrefix = RequiredDatabase.isV934Contract()
                ? "V934_REAL_QUERY_DB"
                : "V933_REAL_QUERY_DB";
        String probe = String.format(
                Locale.ROOT,
                probePrefix + " kind=%s physical_role=%s product=%s version=%d.%d "
                        + "catalog=%s schema=%s model=%s rows_sentinel_1=%d rows_sentinel_2=%d "
                        + "catalog_generation=%s source_revision=%s binding_key=%s "
                        + "binding_backend=%s binding_generation=%s",
                requiredDatabase.id(),
                requiredDatabase.physicalRole(),
                physicalIdentity.productName(),
                physicalIdentity.majorVersion(),
                physicalIdentity.minorVersion(),
                display(physicalIdentity.catalog()),
                display(physicalIdentity.schema()),
                QUERY_MODEL,
                observations.get(0).rows().size(),
                observations.get(1).rows().size(),
                resolution.catalogIdentity().generation().value(),
                resolution.catalogIdentity().sourceRevision().value(),
                trackedResolver.defaultBindingIdentity().bindingKey(),
                trackedResolver.defaultBindingIdentity().backendId(),
                trackedResolver.defaultBindingIdentity().generation().value());
        log.info(probe);
        System.out.println(probe);
    }

    private ParityObservation executeParity(
            int sentinel,
            CatalogResolution<QueryModel> expectedResolution
    ) {
        List<Map<String, Object>> nativeRows = nativeRows(sentinel);
        assertFalse(nativeRows.isEmpty(),
                "required FactSales fixture is empty for order_line_no=" + sentinel);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(COLUMNS);
        request.setStrictColumns(true);
        request.setReturnTotal(false);
        request.setSlice(RequiredDatabase.isV934Contract()
                ? List.of(
                        new SliceRequestDef("orderId", "=", PARITY_ORDER_ID),
                        new SliceRequestDef("orderLineNo", "=", sentinel)
                )
                : List.of(new SliceRequestDef("orderLineNo", "=", sentinel)));
        request.setOrderBy(List.of(
                orderBy("orderId", "ASC"),
                orderBy("orderLineNo", "ASC")
        ));

        ModelResultContext context = new ModelResultContext(
                PagingRequest.buildPagingRequest(request, PAGE_LIMIT), null);
        context.setNamespace(null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.noOptimization());

        DbQueryResult queryResult = queryFacade.queryModelResult(context);
        assertNotNull(queryResult);
        assertNotNull(queryResult.getQueryEngine());
        assertNotNull(queryResult.getPagingResult());
        assertNotNull(queryResult.getPagingResult().getItems());
        assertSame(context.getQueryModel(), queryResult.getQueryEngine().getJdbcQueryModel());

        assertContextIdentity(context, expectedResolution,
                trackedResolver.defaultBindingIdentity());

        List<Map<String, Object>> facadeRows = facadeRows(queryResult);
        assertEquals(nativeRows.size(), facadeRows.size(),
                "QueryFacade/native row count mismatch for sentinel=" + sentinel);
        for (int rowIndex = 0; rowIndex < nativeRows.size(); rowIndex++) {
            Map<String, Object> nativeRow = nativeRows.get(rowIndex);
            Map<String, Object> facadeRow = facadeRows.get(rowIndex);
            assertEquals(COLUMNS, new ArrayList<>(facadeRow.keySet()),
                    "QueryFacade column keys/order mismatch at row=" + rowIndex);
            assertEquals(COLUMNS, new ArrayList<>(nativeRow.keySet()),
                    "native oracle column keys/order mismatch at row=" + rowIndex);
            for (String column : COLUMNS) {
                assertEquals(
                        normalizeJdbcValue(nativeRow.get(column)),
                        normalizeJdbcValue(facadeRow.get(column)),
                        "indexed value mismatch: sentinel=" + sentinel
                                + ", row=" + rowIndex + ", column=" + column);
            }
        }
        return new ParityObservation(sentinel, facadeRows);
    }

    private List<Map<String, Object>> nativeRows(int sentinel) {
        RequiredDatabase requiredDatabase = trackedResolver.requiredDatabase();
        boolean v934Contract = RequiredDatabase.isV934Contract();
        String select = requiredDatabase == RequiredDatabase.SQLSERVER2022
                ? "SELECT TOP (" + PAGE_LIMIT + ") order_id, order_line_no FROM fact_sales "
                : "SELECT order_id, order_line_no FROM fact_sales ";
        String limit = requiredDatabase == RequiredDatabase.SQLSERVER2022
                ? ""
                : " LIMIT " + PAGE_LIMIT;
        String where = v934Contract
                ? "WHERE order_id = ? AND order_line_no = ? "
                : "WHERE order_line_no = ? ";
        String sql = select
                + where
                + "ORDER BY order_id ASC, order_line_no ASC"
                + limit;
        return jdbcTemplate.query(sql, statement -> {
            if (v934Contract) {
                statement.setString(1, PARITY_ORDER_ID);
                statement.setInt(2, sentinel);
            } else {
                statement.setInt(1, sentinel);
            }
        }, resultSet -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderId", resultSet.getObject(1));
                row.put("orderLineNo", resultSet.getObject(2));
                rows.add(row);
            }
            return rows;
        });
    }

    private List<Map<String, Object>> facadeRows(DbQueryResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : result.getPagingResult().getItems()) {
            Map<?, ?> raw = assertInstanceOf(Map.class, item,
                    "QueryFacade row must be a map");
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            rows.add(row);
        }
        return rows;
    }

    private void assertTrackedJdbcResolution(
            CatalogResolution<QueryModel> resolution,
            DatasourceBindingIdentity expectedBinding
    ) {
        assertNotNull(resolution, "QueryModelLoader must return a catalog resolution");
        assertInstanceOf(JdbcQueryModel.class, resolution.model());
        assertEquals(QUERY_MODEL, resolution.canonicalName());
        assertEquals(QUERY_MODEL, resolution.model().getName());
        assertNotNull(resolution.catalogIdentity());
        assertEquals("", resolution.catalogIdentity().namespace(),
                "the required-database lane must use the canonical default namespace");
        assertFalse(resolution.catalogIdentity().generation().value().isBlank());
        assertFalse(resolution.catalogIdentity().sourceRevision().value().isBlank());
        assertTrue(resolution.bindingIdentityComplete(),
                "JDBC lifecycle identity must be complete");
        assertFalse(resolution.dependencyBindings().isEmpty(),
                "JDBC lifecycle identity must not use an empty binding set");
        assertEquals(Map.of(expectedBinding.bindingKey(), expectedBinding),
                resolution.dependencyBindings());
        assertBindingMatchesPhysicalProfile(expectedBinding);
    }

    private void assertContextIdentity(
            ModelResultContext context,
            CatalogResolution<QueryModel> expectedResolution,
            DatasourceBindingIdentity expectedBinding
    ) {
        assertSame(expectedResolution.model(), context.getQueryModel());
        assertInstanceOf(JdbcQueryModel.class, context.getQueryModel());
        assertEquals(expectedResolution.canonicalName(), context.getCanonicalModelName());
        assertEquals(expectedResolution.catalogIdentity(), context.getCatalogIdentity());
        assertEquals("", context.getCatalogIdentity().namespace(),
                "QueryFacade must retain the canonical default namespace");
        assertFalse(context.getCatalogIdentity().generation().value().isBlank());
        assertFalse(context.getCatalogIdentity().sourceRevision().value().isBlank());
        assertTrue(context.isBindingIdentityComplete(),
                "QueryFacade must retain a complete datasource identity");
        assertFalse(context.getDatasourceBindingIdentities().isEmpty(),
                "a JDBC QueryFacade context must fail rather than expose an empty identity");
        assertEquals(expectedResolution.dependencyBindings(),
                context.getDatasourceBindingIdentities());
        assertEquals(Map.of(expectedBinding.bindingKey(), expectedBinding),
                context.getDatasourceBindingIdentities());
        assertEquals(BindingCurrentness.CURRENT,
                trackedResolver.currentness(expectedBinding));
        assertBindingMatchesPhysicalProfile(expectedBinding);
    }

    private void assertBindingMatchesPhysicalProfile(DatasourceBindingIdentity binding) {
        RequiredDatabase requiredDatabase = trackedResolver.requiredDatabase();
        PhysicalDatabaseIdentity physicalIdentity = trackedResolver.physicalIdentity();
        assertEquals("test:required-db:default", binding.bindingKey());
        assertEquals("jdbc-test:" + requiredDatabase.id(), binding.backendId());
        assertEquals(
                "fixture:" + requiredDatabase.id() + ":"
                        + physicalIdentity.majorVersion() + "." + physicalIdentity.minorVersion(),
                binding.generation().value());
    }

    private static OrderRequestDef orderBy(String field, String direction) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(direction);
        return order;
    }

    private static Set<String> rowIdentities(List<Map<String, Object>> rows) {
        Set<String> identities = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            identities.add(row.get("orderId") + "#" + normalizeJdbcValue(row.get("orderLineNo")));
        }
        return identities;
    }

    private static BigDecimal numericValue(Object value) {
        Object normalized = normalizeJdbcValue(value);
        assertInstanceOf(BigDecimal.class, normalized,
                "expected a numeric JDBC representation");
        return (BigDecimal) normalized;
    }

    private static Object normalizeJdbcValue(Object value) {
        return value instanceof Number number
                ? new BigDecimal(number.toString()).stripTrailingZeros()
                : value;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private record ParityObservation(int sentinel, List<Map<String, Object>> rows) {
        private ParityObservation {
            rows = List.copyOf(rows);
        }
    }

    private record PhysicalDatabaseIdentity(
            String productName,
            int majorVersion,
            int minorVersion,
            String catalog,
            String schema
    ) {
        static PhysicalDatabaseIdentity capture(
                Connection connection,
                RequiredDatabase requiredDatabase
        ) throws SQLException {
            DatabaseMetaData metadata = connection.getMetaData();
            return new PhysicalDatabaseIdentity(
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseMajorVersion(),
                    metadata.getDatabaseMinorVersion(),
                    connection.getCatalog(),
                    readSchema(connection, requiredDatabase));
        }

        private static String readSchema(
                Connection connection,
                RequiredDatabase requiredDatabase
        ) throws SQLException {
            try {
                return connection.getSchema();
            } catch (SQLFeatureNotSupportedException unsupported) {
                if (requiredDatabase != RequiredDatabase.SQLITE) {
                    throw unsupported;
                }
                return null;
            } catch (SQLException failure) {
                if (requiredDatabase != RequiredDatabase.SQLITE) {
                    throw failure;
                }
                return null;
            } catch (AbstractMethodError unsupported) {
                if (requiredDatabase != RequiredDatabase.SQLITE) {
                    throw new SQLException("JDBC driver does not implement Connection.getSchema()", unsupported);
                }
                return null;
            }
        }
    }

    private enum RequiredDatabase {
        SQLITE("sqlite", "SQLite", 3, 30, "embedded-shared-memory", null, null),
        MYSQL57("mysql57", "MySQL", 5, 7, "required-mysql57-container", "foggy_test", null),
        MYSQL8("mysql8", "MySQL", 8, 0, "required-mysql8-container", "foggy_test", null),
        POSTGRES15("postgres15", "PostgreSQL", 15, null,
                "required-postgresql15-container", "foggy_test", "public"),
        SQLSERVER2022("sqlserver2022", "Microsoft SQL Server", 16, 0,
                "required-sqlserver2022-container", "foggy_test", "dbo");

        private final String id;
        private final String productName;
        private final int majorVersion;
        private final Integer minorVersion;
        private final String physicalRole;
        private final String catalog;
        private final String schema;

        RequiredDatabase(
                String id,
                String productName,
                int majorVersion,
                Integer minorVersion,
                String physicalRole,
                String catalog,
                String schema
        ) {
            this.id = id;
            this.productName = productName;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.physicalRole = physicalRole;
            this.catalog = catalog;
            this.schema = schema;
        }

        static RequiredDatabase fromRequiredProperty() {
            String v934 = System.getProperty("v934.expectedDatabase");
            String v933 = System.getProperty("v933.expectedDatabase");
            if (v934 != null && !v934.isBlank()
                    && v933 != null && !v933.isBlank()
                    && !v934.equals(v933)) {
                throw new IllegalStateException(
                        "v934.expectedDatabase conflicts with compatibility property v933.expectedDatabase");
            }
            boolean v934Contract = v934 != null && !v934.isBlank();
            String value = v934Contract ? v934 : v933;
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        v934Contract
                                ? "v934.expectedDatabase is required"
                                : "v933.expectedDatabase is required");
            }
            return switch (value.trim()) {
                case "sqlite" -> SQLITE;
                case "mysql57" -> MYSQL57;
                case "mysql8" -> requireV934(v934Contract, MYSQL8, value);
                case "postgres15" -> POSTGRES15;
                case "sqlserver2022" -> requireV934(v934Contract, SQLSERVER2022, value);
                default -> throw new IllegalStateException(
                        "unsupported " + (v934Contract
                                ? "v934.expectedDatabase: "
                                : "v933.expectedDatabase: ") + value);
            };
        }

        private static RequiredDatabase requireV934(
                boolean v934Contract,
                RequiredDatabase database,
                String value
        ) {
            if (!v934Contract) {
                throw new IllegalStateException("unsupported v933.expectedDatabase: " + value);
            }
            return database;
        }

        static boolean isV934Contract() {
            String value = System.getProperty("v934.expectedDatabase");
            return value != null && !value.isBlank();
        }

        void verifyOrThrow(PhysicalDatabaseIdentity actual) {
            require(productName.equals(actual.productName()),
                    "unexpected database product: " + actual.productName());
            require(majorVersion == actual.majorVersion(),
                    "unexpected database major version: " + actual.majorVersion());
            if (this == SQLITE) {
                require(actual.minorVersion() >= minorVersion,
                        "SQLite minor version is below the required floor: "
                                + actual.minorVersion());
            }
            if (minorVersion != null && this != SQLITE) {
                require(Objects.equals(minorVersion, actual.minorVersion()),
                        "unexpected database minor version: " + actual.minorVersion());
            }
            require(Objects.equals(catalog, actual.catalog()),
                    "unexpected database catalog: " + display(actual.catalog()));
            require(Objects.equals(schema, actual.schema()),
                    "unexpected database schema: " + display(actual.schema()));
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }

        String id() {
            return id;
        }

        String physicalRole() {
            if (this == SQLITE) {
                String runScopedUrl = System.getProperty("v934.sqlite.expectedUrl");
                if (runScopedUrl != null && !runScopedUrl.isBlank()) {
                    return "run-scoped-sqlite-file";
                }
            }
            return physicalRole;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TrackedBindingConfiguration {

        @Bean
        @Primary
        TrackedRequiredDatabaseResolver requiredDatabaseTrackedResolver(DataSource dataSource)
                throws SQLException {
            return new TrackedRequiredDatabaseResolver(
                    dataSource, RequiredDatabase.fromRequiredProperty());
        }
    }

    static final class TrackedRequiredDatabaseResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {

        private final DataSource dataSource;
        private final RequiredDatabase requiredDatabase;
        private final PhysicalDatabaseIdentity physicalIdentity;
        private final DatasourceBindingIdentity defaultBindingIdentity;
        private final DatasourceBindingIdentity odooBindingIdentity;

        private TrackedRequiredDatabaseResolver(
                DataSource dataSource,
                RequiredDatabase requiredDatabase
        ) throws SQLException {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
            this.requiredDatabase = Objects.requireNonNull(requiredDatabase, "requiredDatabase");
            try (Connection connection = dataSource.getConnection()) {
                this.physicalIdentity = PhysicalDatabaseIdentity.capture(
                        connection, requiredDatabase);
            }
            requiredDatabase.verifyOrThrow(physicalIdentity);
            String generation = "fixture:" + requiredDatabase.id() + ":"
                    + physicalIdentity.majorVersion() + "." + physicalIdentity.minorVersion();
            this.defaultBindingIdentity = new DatasourceBindingIdentity(
                    "test:required-db:default",
                    "jdbc-test:" + requiredDatabase.id(),
                    new DatasourceBindingGeneration(generation));
            this.odooBindingIdentity = new DatasourceBindingIdentity(
                    "test:required-db:odoo",
                    "jdbc-test:" + requiredDatabase.id(),
                    new DatasourceBindingGeneration(generation));
        }

        @Override
        public DataSource resolve(String name) {
            return "odoo".equals(name) ? dataSource : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            return "odoo".equals(name)
                    ? ResolvedDatasourceBinding.tracked(dataSource, odooBindingIdentity)
                    : null;
        }

        @Override
        public DataSource resolveDefault(String namespace) {
            return dataSource;
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            return ResolvedDatasourceBinding.tracked(dataSource, defaultBindingIdentity);
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, defaultBindingIdentity);
        }

        @Override
        public boolean isConfigured(String name) {
            return "odoo".equals(name);
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
            return defaultBindingIdentity.equals(identity) || odooBindingIdentity.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public synchronized <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            Objects.requireNonNull(identities, "identities");
            Objects.requireNonNull(publication, "publication");
            boolean allCurrent = identities.stream()
                    .allMatch(identity -> currentness(identity) == BindingCurrentness.CURRENT);
            if (!allCurrent) {
                throw new IllegalStateException("STALE_REQUIRED_DATABASE_BINDING");
            }
            return publication.get();
        }

        RequiredDatabase requiredDatabase() {
            return requiredDatabase;
        }

        PhysicalDatabaseIdentity physicalIdentity() {
            return physicalIdentity;
        }

        DatasourceBindingIdentity defaultBindingIdentity() {
            return defaultBindingIdentity;
        }
    }
}
