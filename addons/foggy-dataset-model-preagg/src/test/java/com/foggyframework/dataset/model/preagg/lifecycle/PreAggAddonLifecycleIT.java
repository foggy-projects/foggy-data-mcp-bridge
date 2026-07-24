package com.foggyframework.dataset.model.preagg.lifecycle;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.preagg.PreAggRewriteResult;
import com.foggyframework.dataset.model.engine.preagg.PreAggregationInterceptor;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.plugins.query_execution.PreAggRewriteStep;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshContext;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshResult;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshService;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.DbProperty;
import com.foggyframework.dataset.model.spi.DbPropertyColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.db.table.SqlColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step 3 supplemental Addon lifecycle evidence.
 *
 * <p>The authority runner executes this exact class once on SQLite and once on
 * a fresh MySQL 5.7 instance. Missing runner configuration is an error: these
 * tests must never turn an unavailable database into a skipped or green run.</p>
 */
@DisplayName("Pre-aggregation Addon lifecycle")
class PreAggAddonLifecycleIT {

    private static final String SOURCE_TABLE = "v934_preagg_source";
    private static final String TARGET_TABLE = "v934_preagg_daily_product";

    private DataSource dataSource;
    private FDialect dialect;

    @BeforeEach
    void setUpDatabase() throws Exception {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        Class.forName(config.driverClass());
        if (config.sqlitePath() != null) {
            Path parent = config.sqlitePath().toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
        dataSource = new DriverManagerDataSource(
                config.jdbcUrl(), config.username(), config.password());
        dialect = config.dialect();
        dropTables();
    }

    @AfterEach
    void cleanDatabase() throws SQLException {
        if (dataSource != null) {
            dropTables();
        }
    }

    @Test
    @DisplayName("real create, full refresh, incremental refresh and query parity")
    void realLifecycleHasNativeQueryParity() throws Exception {
        Fixture fixture = fixture(true, DbColumnType.DAY, "eventDate$caption");
        PreAggSqlBuilder sqlBuilder = new PreAggSqlBuilder(dialect);

        execute("CREATE TABLE " + SOURCE_TABLE + " ("
                + "event_date DATE NOT NULL, "
                + "product_id INTEGER NOT NULL, "
                + "amount DECIMAL(18,2) NOT NULL)");
        execute(sqlBuilder.buildCreateTableDdl(fixture.preAggregation(), fixture.sourceModel()));
        assertTrue(tableExists(TARGET_TABLE), "builder DDL must create the physical table");

        LocalDate today = LocalDate.now();
        LocalDate oldDate = today.minusDays(20);
        insertSource(oldDate, 1, "10.00");
        insertSource(today.minusDays(3), 1, "20.00");
        insertSource(today.minusDays(2), 2, "30.00");
        insertSource(today.minusDays(1), 1, "40.00");

        PreAggRefreshService refreshService = new PreAggRefreshService();
        assertNull(fixture.preAggregation().getDataWatermark(),
                "unrefreshed runtime pre-aggregation must not expose a query boundary");
        PreAggRefreshContext fullContext = context(fixture, false);
        PreAggRefreshResult full = refreshService.refresh(
                fixture.preAggregation(), fixture.sourceModel(), dataSource, fullContext);
        assertTrue(full.isSuccess(), () -> "full refresh failed: " + full.getErrorMessage());
        assertEquals("FULL", full.getStrategy());
        LocalDate safeWatermark = today;
        assertEquals(safeWatermark, full.getNewWatermark(),
                "DATE refresh must publish the first open day as its exclusive boundary");
        assertEquals(safeWatermark, fixture.preAggregation().getDataWatermark(),
                "successful refresh must publish its boundary to the query matcher");
        assertNotNull(fixture.preAggregation().getLastRefreshTime(),
                "successful refresh must publish its completion time");
        assertEquals(queryNativeOracle(), queryMaterialized(),
                "full refresh result must equal an independent native GROUP BY query");
        AggregateRow oldRowBefore = queryMaterialized().stream()
                .filter(row -> row.eventDate().equals(oldDate))
                .findFirst()
                .orElseThrow();

        insertSource(today.minusDays(1), 1, "5.25");
        insertSource(today, 2, "7.50");
        updateSourceAmount(today.minusDays(2), 2, "33.00");

        PreAggRefreshContext incrementalContext = context(fixture, false);
        PreAggRefreshResult incremental = refreshService.refresh(
                fixture.preAggregation(), fixture.sourceModel(), dataSource, incrementalContext);
        assertTrue(incremental.isSuccess(),
                () -> "incremental refresh failed: " + incremental.getErrorMessage());
        assertEquals("INCREMENTAL", incremental.getStrategy());
        assertEquals(safeWatermark, incremental.getNewWatermark());
        assertEquals(safeWatermark, fixture.preAggregation().getDataWatermark());
        assertEquals(queryNativeOracle().stream()
                        .filter(row -> row.eventDate().isBefore(safeWatermark))
                        .toList(),
                queryMaterialized(),
                "incremental materialization must equal the closed native history");
        AggregateRow oldRowAfter = queryMaterialized().stream()
                .filter(row -> row.eventDate().equals(oldDate))
                .findFirst()
                .orElseThrow();
        assertEquals(oldRowBefore, oldRowAfter,
                "incremental refresh must not rewrite data before its bounded lookback range");

        // The current day remains an open DATE bucket. A row arriving after
        // commit must be read from the source branch, while closed history is
        // read from the materialized branch produced by this same refresh.
        insertSource(today, 2, "2.50");
        assertFalse(queryNativeMetrics().equals(queryMaterializedMetrics()),
                "late-row fixture must prove that materialized-only data is now stale");
        PreAggRewriteResult rewrite = rewriteAfterRefresh(fixture);
        assertTrue(rewrite.isApplied(), "published watermark must let matcher/rewriter take over");
        assertTrue(rewrite.isHybridQuery(), "an open current-day bucket requires hybrid mode");
        assertEquals(safeWatermark, rewrite.getWatermark());
        assertEquals(List.of(safeWatermark, safeWatermark), rewrite.getParams());
        assertTrue(rewrite.getSql().contains("pa.event_day < ?"));
        assertTrue(rewrite.getSql().contains("src.event_date >= ?"));
        assertEquals(queryNativeMetrics(), queryRewrittenMetrics(rewrite),
                "refresh -> matcher -> rewriter must retain a same-day late row");

        if (dialect.getDbType() == DbType.SQLITE) {
            dropTables();
            assertSqliteTextDateLifecycle();
        }
    }

    private void assertSqliteTextDateLifecycle() throws Exception {
        Fixture fixture = fixture(
                true, DbColumnType.TEXT, DbColumnType.TEXT, "eventDate$caption");
        PreAggSqlBuilder sqlBuilder = new PreAggSqlBuilder(dialect);
        execute("CREATE TABLE " + SOURCE_TABLE + " ("
                + "event_date TEXT NOT NULL, "
                + "product_id INTEGER NOT NULL, "
                + "amount DECIMAL(18,2) NOT NULL)");
        execute(sqlBuilder.buildCreateTableDdl(fixture.preAggregation(), fixture.sourceModel()));

        LocalDate today = LocalDate.now();
        insertSource(today.minusDays(20), 1, "10.00", true);
        insertSource(today.minusDays(2), 2, "30.00", true);
        insertSource(today.minusDays(1), 1, "40.00", true);

        PreAggRefreshService refreshService = new PreAggRefreshService();
        PreAggRefreshResult full = refreshService.refresh(
                fixture.preAggregation(), fixture.sourceModel(), dataSource, context(fixture, false));
        assertTrue(full.isSuccess(), () -> "SQLite TEXT full refresh failed: "
                + full.getErrorMessage());
        assertEquals(today, full.getNewWatermark());

        insertSource(today.minusDays(1), 1, "5.25", true);
        insertSource(today, 2, "7.50", true);
        updateSourceAmount(today.minusDays(2), 2, "33.00", true);

        PreAggRefreshResult incremental = refreshService.refresh(
                fixture.preAggregation(), fixture.sourceModel(), dataSource, context(fixture, false));
        assertTrue(incremental.isSuccess(), () -> "SQLite TEXT incremental refresh failed: "
                + incremental.getErrorMessage());
        assertEquals(queryNativeOracle().stream()
                        .filter(row -> row.eventDate().isBefore(today))
                        .toList(),
                queryMaterialized(),
                "ISO TEXT bounds must delete and rebuild the closed lookback range");

        insertSource(today, 2, "2.50", true);
        PreAggRewriteResult rewrite = rewriteAfterRefresh(fixture);
        assertTrue(rewrite.isApplied());
        assertTrue(rewrite.isHybridQuery());
        assertEquals(queryNativeMetrics(), queryRewrittenMetrics(rewrite, true),
                "SQLite TEXT refresh and hybrid query must share the ISO LocalDate domain");
    }

    @Test
    @DisplayName("missing explicit physical mapping fails before database mutation")
    void missingExplicitMappingFailsClosed() throws Exception {
        Fixture invalid = fixture(false, DbColumnType.DAY, "eventDate$caption");
        PreAggSqlBuilder sqlBuilder = new PreAggSqlBuilder(dialect);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildCreateTableDdl(
                        invalid.preAggregation(), invalid.sourceModel()));

        assertTrue(error.getMessage().contains("product$id"));
        assertTrue(error.getMessage().contains("explicit materialized column"));
        assertFalse(tableExists(TARGET_TABLE),
                "an invalid physical contract must not create a partial target table");
    }

    @Test
    @DisplayName("numeric semantic id rejects LocalDate incremental bounds without fallback")
    void numericWatermarkFailsClosedWithoutMutation() throws Exception {
        Fixture valid = fixture(true, DbColumnType.DAY, "eventDate$caption");
        execute("CREATE TABLE " + SOURCE_TABLE + " ("
                + "event_date DATE NOT NULL, "
                + "product_id INTEGER NOT NULL, "
                + "amount DECIMAL(18,2) NOT NULL)");
        execute(new PreAggSqlBuilder(dialect).buildCreateTableDdl(
                valid.preAggregation(), valid.sourceModel()));
        execute("INSERT INTO " + TARGET_TABLE
                + " (event_date, product_id, event_day, amount_sum, order_count, "
                + "_preagg_row_count, _preagg_created_at) VALUES "
                + "('2000-01-01', 99, '2000-01-01', 123.00, 1, 1, CURRENT_TIMESTAMP)");
        List<AggregateRow> before = queryMaterialized();

        Fixture invalid = fixture(true, DbColumnType.INTEGER, "eventDate$id");
        PreAggRefreshContext context = context(invalid, false);
        context.setLastWatermark(LocalDate.now().minusDays(1));
        PreAggRefreshResult result = new PreAggRefreshService().refresh(
                invalid.preAggregation(), invalid.sourceModel(), dataSource, context);

        assertFalse(result.isSuccess(), "invalid LocalDate/numeric watermark must fail closed");
        assertTrue(result.getErrorMessage().contains("DATE source watermark"));
        assertNull(invalid.preAggregation().getDataWatermark(),
                "failed refresh must not publish a query boundary");
        assertNull(invalid.preAggregation().getLastRefreshTime(),
                "failed refresh must not publish a successful completion time");
        assertEquals(before, queryMaterialized(),
                "contract validation must fail before the delete/insert transaction mutates data");

        invalid.preAggregation().setDataWatermark(LocalDate.now().minusDays(1));
        PreAggRewriteResult unsafeQuery = rewrite(invalid, false);
        assertFalse(unsafeQuery.isApplied(),
                "query rewrite must independently reject LocalDate bounds for a numeric source key");
        assertNull(unsafeQuery.getSql());
        assertTrue(unsafeQuery.getParams().isEmpty());
    }

    private PreAggRefreshContext context(Fixture fixture, boolean forceFull) {
        PreAggRefreshContext context = PreAggRefreshContext.of(
                fixture.sourceModel().getName(), fixture.preAggregation().getName());
        context.setForceFullRefresh(forceFull);
        return context;
    }

    private Fixture fixture(boolean includeProductMapping,
                            DbColumnType watermarkIdType,
                            String watermarkColumn) {
        return fixture(
                includeProductMapping, watermarkIdType, DbColumnType.DAY, watermarkColumn);
    }

    private Fixture fixture(boolean includeProductMapping,
                            DbColumnType watermarkIdType,
                            DbColumnType watermarkCaptionType,
                            String watermarkColumn) {
        QueryObject sourceQuery = mock(QueryObject.class);
        when(sourceQuery.getBody()).thenReturn(SOURCE_TABLE);
        when(sourceQuery.getAlias()).thenReturn("source_fixture");

        TableModel sourceModel = mock(TableModel.class);
        when(sourceModel.getName()).thenReturn("V934PreAggSource");
        when(sourceModel.getTableName()).thenReturn(SOURCE_TABLE);
        when(sourceModel.getQueryObject()).thenReturn(sourceQuery);

        DbDimension eventDate = dimension(
                "eventDate", "event_date", watermarkIdType, sourceQuery);
        DbColumn caption = column("event_date", watermarkCaptionType, sourceQuery);
        when(eventDate.getCaptionDbColumn()).thenReturn(caption);
        when(eventDate.getTimeRole()).thenReturn("business_date");
        DbDimension product = dimension(
                "product", "product_id", DbColumnType.INTEGER, sourceQuery);
        when(sourceModel.findJdbcDimensionByName("eventDate")).thenReturn(eventDate);
        when(sourceModel.findJdbcDimensionByName("product")).thenReturn(product);

        DbMeasure amount = mock(DbMeasure.class);
        DbColumn amountColumn = column("amount", DbColumnType.MONEY, sourceQuery);
        when(amountColumn.getDeclare(null, "src", dialect)).thenReturn("src.amount");
        when(amount.getJdbcColumn()).thenReturn(amountColumn);
        when(sourceModel.findJdbcMeasureByName("amount")).thenReturn(amount);

        PreAggRefreshDef refresh = new PreAggRefreshDef();
        refresh.setStrategy("INCREMENTAL");
        refresh.setWatermarkColumn(watermarkColumn);
        refresh.setLookbackDays(3);

        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("eventDate$id", "event_date");
        mappings.put("eventDate$caption", "event_day");
        if (includeProductMapping) {
            mappings.put("product$id", "product_id");
        }

        PreAggregation preAggregation = mock(PreAggregation.class);
        AtomicReference<Object> dataWatermark = new AtomicReference<>();
        AtomicReference<LocalDateTime> lastRefreshTime = new AtomicReference<>();
        when(preAggregation.getDataWatermark()).thenAnswer(ignored -> dataWatermark.get());
        doAnswer(invocation -> {
            dataWatermark.set(invocation.getArgument(0));
            return null;
        }).when(preAggregation).setDataWatermark(org.mockito.ArgumentMatchers.any());
        when(preAggregation.getLastRefreshTime()).thenAnswer(ignored -> lastRefreshTime.get());
        doAnswer(invocation -> {
            lastRefreshTime.set(invocation.getArgument(0));
            return null;
        }).when(preAggregation).setLastRefreshTime(org.mockito.ArgumentMatchers.any());
        when(preAggregation.getName()).thenReturn("v934_daily_product");
        when(preAggregation.getTableName()).thenReturn(TARGET_TABLE);
        when(preAggregation.getSchema()).thenReturn(null);
        when(preAggregation.getQualifiedTableName()).thenCallRealMethod();
        when(preAggregation.getDimensionNames()).thenReturn(
                new LinkedHashSet<>(List.of("eventDate", "product")));
        when(preAggregation.getGranularities()).thenReturn(
                Map.of("eventDate", TimeGranularity.DAY));
        when(preAggregation.getGranularity("eventDate")).thenReturn(TimeGranularity.DAY);
        when(preAggregation.getDimensionProperties()).thenReturn(
                Map.of("eventDate", Set.of("caption")));
        when(preAggregation.getDimensionProperties("eventDate"))
                .thenReturn(Set.of("caption"));
        when(preAggregation.getDimensionProperties("product")).thenReturn(Set.of());
        when(preAggregation.getExplicitDimensionPropertyColumnNames())
                .thenReturn(Map.copyOf(mappings));
        when(preAggregation.getDimensionPropertyColumnNames())
                .thenReturn(Map.copyOf(mappings));
        Map<String, DbAggregation> aggregations = new LinkedHashMap<>();
        aggregations.put("amount", DbAggregation.SUM);
        aggregations.put("orderCount", DbAggregation.COUNT);
        when(preAggregation.getMeasureAggregations()).thenReturn(aggregations);
        when(preAggregation.getMeasureColumnNames()).thenReturn(Map.of(
                "amount", "amount_sum",
                "orderCount", "order_count"));
        when(preAggregation.getRefreshConfig()).thenReturn(refresh);
        when(preAggregation.isEnabled()).thenReturn(true);
        when(preAggregation.supportsHybridQuery()).thenCallRealMethod();
        when(preAggregation.getWatermarkColumn()).thenCallRealMethod();
        when(preAggregation.hasDimension("eventDate")).thenReturn(true);
        when(preAggregation.hasDimension("product")).thenReturn(true);
        when(preAggregation.hasMeasure("amount")).thenReturn(true);
        when(preAggregation.hasMaterializedDimensionProperty(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
                mappings.containsKey(invocation.getArgument(0) + "$" + invocation.getArgument(1)));
        when(preAggregation.getFilters()).thenReturn(List.of());
        when(preAggregation.getDimensionCount()).thenReturn(2);
        when(preAggregation.getGranularityLevel()).thenReturn(0);
        when(sourceModel.getPreAggregations()).thenReturn(List.of(preAggregation));

        return new Fixture(preAggregation, sourceModel, sourceQuery);
    }

    private PreAggRewriteResult rewriteAfterRefresh(Fixture fixture) {
        return rewrite(fixture, true);
    }

    private PreAggRewriteResult rewrite(Fixture fixture, boolean verifyProductionStep) {
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getJdbcModel()).thenReturn(fixture.sourceModel());
        when(queryModel.getDialect()).thenReturn(dialect);
        when(queryModel.getAlias(fixture.sourceQuery())).thenReturn("src");

        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.setFrom(jdbcQuery.new JdbcFrom(fixture.sourceQuery()));
        JdbcQuery.JdbcSelect select = jdbcQuery.new JdbcSelect();
        select.setColumns(List.of(
                queryColumn("eventDate$caption", "event_date", fixture.sourceQuery(), false),
                queryColumn("product$id", "product_id", fixture.sourceQuery(), false),
                queryColumn("amount", "amount", fixture.sourceQuery(), true)));
        jdbcQuery.setSelect(select);

        JdbcModelQueryEngine queryEngine = mock(JdbcModelQueryEngine.class);
        when(queryEngine.getJdbcQuery()).thenReturn(jdbcQuery);
        when(queryEngine.getJdbcQueryModel()).thenReturn(queryModel);
        DbQueryRequestDef request = new DbQueryRequestDef();
        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(null);
        interceptor.setHybridQueryEnabled(true);
        PreAggRewriteResult rewrite = interceptor.tryRewrite(
                queryEngine, queryModel, request);
        if (!verifyProductionStep) {
            return rewrite;
        }
        assertNotNull(rewrite.getSql(), "matched refresh boundary must produce executable SQL");

        ModelResultContext modelContext = new ModelResultContext();
        modelContext.setRequest(PagingRequest.buildPagingRequest(request));
        QueryExecutionContext executionContext = new QueryExecutionContext();
        executionContext.setQueryEngine(queryEngine);
        executionContext.setModelResultContext(modelContext);
        new PreAggRewriteStep(null).beforeExecute(executionContext);
        assertEquals("hybrid", executionContext.getExtData("preAggMode"),
                "production Step must preserve the source tail by default");
        assertEquals(rewrite.getSql(), executionContext.getSql());
        assertEquals(rewrite.getParams(), executionContext.getParams());
        assertTrue(modelContext.getCacheConfig().isHybridQueryEnabled());
        return rewrite;
    }

    private DbColumn queryColumn(String semanticName,
                                 String physicalName,
                                 QueryObject queryObject,
                                 boolean measure) {
        DbColumn column;
        if (measure) {
            column = mock(DbColumn.class);
        } else {
            DbPropertyColumn propertyColumn = mock(DbPropertyColumn.class);
            DbProperty property = mock(DbProperty.class);
            when(propertyColumn.getProperty()).thenReturn(property);
            when(propertyColumn.getDecorate(DbPropertyColumn.class)).thenReturn(propertyColumn);
            column = propertyColumn;
        }
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(physicalName);
        when(column.getSqlColumn()).thenReturn(sqlColumn);
        when(column.getSqlColumnName()).thenReturn(physicalName);
        when(column.getQueryObject()).thenReturn(queryObject);
        when(column.getName()).thenReturn(semanticName);
        when(column.getAlias()).thenReturn(semanticName);
        when(column.isMeasure()).thenReturn(measure);
        when(column.isDimension()).thenReturn(false);
        when(column.isProperty()).thenReturn(!measure);
        when(column.getAggregation()).thenReturn(measure ? DbAggregation.SUM : DbAggregation.NONE);
        when(column.getDeclare(null, "src", dialect)).thenReturn("src." + physicalName);
        return column;
    }

    private DbDimension dimension(String name,
                                  String physicalColumn,
                                  DbColumnType type,
                                  QueryObject sourceQuery) {
        DbDimension dimension = mock(DbDimension.class);
        DbColumn id = column(physicalColumn, type, sourceQuery);
        when(dimension.getName()).thenReturn(name);
        when(dimension.getForeignKey()).thenReturn(physicalColumn);
        when(dimension.getForeignKeyDbColumn()).thenReturn(id);
        when(dimension.getPrimaryKeyDbColumn()).thenReturn(id);
        when(dimension.getQueryObject()).thenReturn(sourceQuery);
        return dimension;
    }

    private DbColumn column(String name, DbColumnType type, QueryObject queryObject) {
        DbColumn column = mock(DbColumn.class);
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(name);
        when(sqlColumn.getJdbcType()).thenReturn(type.getJdbcType());
        when(column.getSqlColumn()).thenReturn(sqlColumn);
        when(column.getSqlColumnName()).thenReturn(name);
        when(column.getType()).thenReturn(type);
        when(column.getQueryObject()).thenReturn(queryObject);
        return column;
    }

    private void insertSource(LocalDate date, int productId, String amount) throws SQLException {
        insertSource(date, productId, amount, false);
    }

    private void insertSource(LocalDate date,
                              int productId,
                              String amount,
                              boolean isoTextDate) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + SOURCE_TABLE
                             + " (event_date, product_id, amount) VALUES (?, ?, ?)")) {
            bindDate(statement, 1, date, isoTextDate);
            statement.setInt(2, productId);
            statement.setBigDecimal(3, new BigDecimal(amount));
            statement.executeUpdate();
        }
    }

    private void updateSourceAmount(LocalDate date, int productId, String amount)
            throws SQLException {
        updateSourceAmount(date, productId, amount, false);
    }

    private void updateSourceAmount(LocalDate date,
                                    int productId,
                                    String amount,
                                    boolean isoTextDate) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + SOURCE_TABLE
                             + " SET amount = ? WHERE event_date = ? AND product_id = ?")) {
            statement.setBigDecimal(1, new BigDecimal(amount));
            bindDate(statement, 2, date, isoTextDate);
            statement.setInt(3, productId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private List<AggregateRow> queryNativeOracle() throws SQLException {
        return queryRows("SELECT event_date, product_id, SUM(amount), COUNT(*), COUNT(*) "
                + "FROM " + SOURCE_TABLE + " GROUP BY event_date, product_id "
                + "ORDER BY event_date, product_id");
    }

    private List<AggregateRow> queryMaterialized() throws SQLException {
        return queryRows("SELECT event_day, product_id, amount_sum, order_count, "
                + "_preagg_row_count FROM " + TARGET_TABLE
                + " ORDER BY event_day, product_id");
    }

    private Map<String, BigDecimal> queryNativeMetrics() throws SQLException {
        return queryMetrics("SELECT event_date, product_id, SUM(amount) FROM "
                + SOURCE_TABLE + " GROUP BY event_date, product_id", List.of());
    }

    private Map<String, BigDecimal> queryMaterializedMetrics() throws SQLException {
        return queryMetrics("SELECT event_day, product_id, amount_sum FROM "
                + TARGET_TABLE, List.of());
    }

    private Map<String, BigDecimal> queryRewrittenMetrics(PreAggRewriteResult rewrite)
            throws SQLException {
        return queryMetrics(rewrite.getSql(), rewrite.getParams());
    }

    private Map<String, BigDecimal> queryRewrittenMetrics(PreAggRewriteResult rewrite,
                                                           boolean isoTextDate)
            throws SQLException {
        return queryMetrics(rewrite.getSql(), rewrite.getParams(), isoTextDate);
    }

    private Map<String, BigDecimal> queryMetrics(String sql, List<Object> params)
            throws SQLException {
        return queryMetrics(sql, params, false);
    }

    private Map<String, BigDecimal> queryMetrics(String sql,
                                                  List<Object> params,
                                                  boolean isoTextDate)
            throws SQLException {
        Map<String, BigDecimal> rows = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                if (value instanceof LocalDate localDate) {
                    bindDate(statement, i + 1, localDate, isoTextDate);
                } else {
                    statement.setObject(i + 1, value);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String key = readLocalDate(resultSet, 1) + "|" + resultSet.getInt(2);
                    rows.put(key, resultSet.getBigDecimal(3).setScale(2, RoundingMode.UNNECESSARY));
                }
            }
        }
        return rows;
    }

    private void bindDate(PreparedStatement statement,
                          int parameter,
                          LocalDate date,
                          boolean isoTextDate) throws SQLException {
        if (isoTextDate) {
            statement.setString(parameter, date.toString());
        } else {
            statement.setDate(parameter, Date.valueOf(date));
        }
    }

    private List<AggregateRow> queryRows(String sql) throws SQLException {
        List<AggregateRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(new AggregateRow(
                        readLocalDate(resultSet, 1),
                        resultSet.getInt(2),
                        resultSet.getBigDecimal(3).setScale(2, RoundingMode.UNNECESSARY),
                        resultSet.getLong(4),
                        resultSet.getLong(5)));
            }
        }
        return rows;
    }

    private LocalDate readLocalDate(ResultSet resultSet, int column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Number epochMillis) {
            return new Date(epochMillis.longValue()).toLocalDate();
        }
        if (value instanceof CharSequence text) {
            String normalized = text.toString();
            if (normalized.matches("-?[0-9]+")) {
                return new Date(Long.parseLong(normalized)).toLocalDate();
            }
            if (normalized.length() >= 10) {
                return LocalDate.parse(normalized.substring(0, 10));
            }
        }
        throw new SQLException("Unsupported DATE representation from lifecycle database: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT),
                    new String[]{"TABLE"})) {
                return tables.next();
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TARGET_TABLE);
            statement.execute("DROP TABLE IF EXISTS " + SOURCE_TABLE);
        }
    }

    private record Fixture(PreAggregation preAggregation,
                           TableModel sourceModel,
                           QueryObject sourceQuery) {
    }

    private record AggregateRow(LocalDate eventDate,
                                int productId,
                                BigDecimal amount,
                                long orderCount,
                                long sourceRowCount) {
    }

    private record DatabaseConfig(String jdbcUrl,
                                  String username,
                                  String password,
                                  String driverClass,
                                  FDialect dialect,
                                  Path sqlitePath) {

        private static DatabaseConfig fromEnvironment() {
            String database = required("V934_PREAGG_DATABASE");
            if ("sqlite".equals(database)) {
                Path path = Path.of(required("V934_PREAGG_SQLITE_PATH"));
                return new DatabaseConfig(
                        "jdbc:sqlite:" + path.toAbsolutePath(), "", "",
                        "org.sqlite.JDBC", FDialect.SQLITE_DIALECT, path);
            }
            if ("mysql57".equals(database)) {
                return new DatabaseConfig(
                        required("V934_PREAGG_JDBC_URL"),
                        required("V934_PREAGG_JDBC_USER"),
                        required("V934_PREAGG_JDBC_PASSWORD"),
                        "com.mysql.cj.jdbc.Driver", FDialect.MYSQL_DIALECT, null);
            }
            throw new IllegalStateException(
                    "V934_PREAGG_DATABASE must be exactly sqlite or mysql57, got: " + database);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required authority environment is missing: " + name);
            }
            return value;
        }
    }
}
