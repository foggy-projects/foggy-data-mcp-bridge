package com.foggyframework.dataset.model.preagg.ddl;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.db.table.SqlColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PreAggSqlBuilder 单元测试
 * <p>
 * 验证：
 * <ul>
 *   <li>增量 SQL 使用参数化查询（防 SQL 注入）</li>
 *   <li>SQL 结构正确性</li>
 *   <li>全量刷新 SQL 生成</li>
 * </ul>
 * </p>
 */
@DisplayName("PreAggSqlBuilder Tests")
class PreAggSqlBuilderTest {

    private PreAggSqlBuilder sqlBuilder;
    private PreAggregation mockPreAgg;
    private TableModel mockSourceModel;
    private PreAggRefreshDef refreshConfig;

    @BeforeEach
    void setUp() {
        sqlBuilder = new PreAggSqlBuilder();

        // 构建 mock PreAggregation
        mockPreAgg = mock(PreAggregation.class);
        when(mockPreAgg.getName()).thenReturn("daily_sales");
        when(mockPreAgg.getTableName()).thenReturn("preagg_daily_sales");
        when(mockPreAgg.getSchema()).thenReturn(null);
        when(mockPreAgg.getQualifiedTableName()).thenCallRealMethod();

        // 维度
        Set<String> dimensionNames = new LinkedHashSet<>(Arrays.asList("salesDate", "product"));
        when(mockPreAgg.getDimensionNames()).thenReturn(dimensionNames);

        // 粒度
        Map<String, TimeGranularity> granularities = new LinkedHashMap<>();
        granularities.put("salesDate", TimeGranularity.DAY);
        when(mockPreAgg.getGranularities()).thenReturn(granularities);
        when(mockPreAgg.getGranularity("salesDate")).thenReturn(TimeGranularity.DAY);
        when(mockPreAgg.getGranularity("product")).thenReturn(null);

        // 维度属性
        when(mockPreAgg.getDimensionProperties("salesDate")).thenReturn(Set.of());
        when(mockPreAgg.getDimensionProperties("product")).thenReturn(Set.of());
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id"
        ));

        // 度量
        Map<String, DbAggregation> measureAggs = new LinkedHashMap<>();
        measureAggs.put("amount", DbAggregation.SUM);
        measureAggs.put("quantity", DbAggregation.SUM);
        when(mockPreAgg.getMeasureAggregations()).thenReturn(measureAggs);

        Map<String, String> measureColumns = new LinkedHashMap<>();
        measureColumns.put("amount", "amount_sum");
        measureColumns.put("quantity", "quantity_sum");
        when(mockPreAgg.getMeasureColumnNames()).thenReturn(measureColumns);

        // 构建 mock TableModel
        mockSourceModel = mock(TableModel.class);
        when(mockSourceModel.getName()).thenReturn("SalesModel");
        when(mockSourceModel.getTableName()).thenReturn("sales_order");
        QueryObject sourceQueryObject = mock(QueryObject.class);
        when(sourceQueryObject.getBody()).thenReturn("sales_order");
        when(sourceQueryObject.getAlias()).thenReturn("sales_source");
        when(mockSourceModel.getQueryObject()).thenReturn(sourceQueryObject);

        // Mock 维度
        DbDimension salesDateDim = createMockDimension("salesDate", "order_date", DbColumnType.DAY);
        DbDimension productDim = createMockDimension("product", "product_id", DbColumnType.INTEGER);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDateDim);
        when(mockSourceModel.findJdbcDimensionByName("product")).thenReturn(productDim);

        // Mock 度量
        DbMeasure amountMeasure = createMockMeasure("amount", "amount");
        DbMeasure quantityMeasure = createMockMeasure("quantity", "quantity");
        when(mockSourceModel.findJdbcMeasureByName("amount")).thenReturn(amountMeasure);
        when(mockSourceModel.findJdbcMeasureByName("quantity")).thenReturn(quantityMeasure);

        // RefreshConfig
        refreshConfig = new PreAggRefreshDef();
        refreshConfig.setStrategy("INCREMENTAL");
        refreshConfig.setWatermarkColumn("salesDate$id");
        refreshConfig.setLookbackDays(3);
        when(mockPreAgg.getRefreshConfig()).thenReturn(refreshConfig);
    }

    // ==================== 增量删除 SQL 测试 ====================

    @Test
    @DisplayName("buildIncrementalDeleteSql should return ParameterizedSql with placeholders")
    void testIncrementalDeleteSqlIsParameterized() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        ParameterizedSql result = sqlBuilder.buildIncrementalDeleteSql(mockPreAgg, refreshConfig, startDate, endDate);

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getSql(), "SQL should not be null");
        assertNotNull(result.getParams(), "Params should not be null");

        // SQL 不应包含直接拼接的日期值
        assertFalse(result.getSql().contains("2024-01-01"),
                "SQL should not contain literal date values (SQL injection risk)");
        assertFalse(result.getSql().contains("2024-01-31"),
                "SQL should not contain literal date values (SQL injection risk)");

        // SQL 应使用 ? 占位符
        assertTrue(result.getSql().contains("?"),
                "SQL should use ? placeholders for parameters");

        // 应有 2 个参数（startDate, endDate）
        assertEquals(2, result.getParams().size(),
                "Should have 2 parameters (startDate and endDate)");
        assertEquals(startDate, result.getParams().get(0));
        assertEquals(endDate, result.getParams().get(1));

        // SQL 结构检查
        String sql = result.getSql().toUpperCase();
        assertTrue(sql.startsWith("DELETE FROM"), "Should be a DELETE statement");
        assertTrue(sql.contains("WHERE"), "Should have WHERE clause");
        assertTrue(sql.contains(">="), "Should have >= comparison");
        assertTrue(sql.contains(" < ?"), "End boundary must be exclusive");
        assertFalse(sql.contains(" <= ?"), "Inclusive end would close an open DATE bucket");
    }

    @Test
    @DisplayName("buildIncrementalDeleteSql should use correct table name with schema")
    void testIncrementalDeleteSqlWithSchema() {
        when(mockPreAgg.getSchema()).thenReturn("analytics");

        LocalDate startDate = LocalDate.of(2024, 6, 1);
        LocalDate endDate = LocalDate.of(2024, 6, 30);

        ParameterizedSql result = sqlBuilder.buildIncrementalDeleteSql(mockPreAgg, refreshConfig, startDate, endDate);

        assertTrue(result.getSql().contains("analytics.preagg_daily_sales"),
                "SQL should include schema prefix");
    }

    // ==================== 增量插入 SQL 测试 ====================

    @Test
    @DisplayName("buildIncrementalInsertSql should return ParameterizedSql with placeholders")
    void testIncrementalInsertSqlIsParameterized() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        ParameterizedSql result = sqlBuilder.buildIncrementalInsertSql(
                mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate);

        assertNotNull(result);

        // SQL 不应包含直接拼接的日期值
        assertFalse(result.getSql().contains("2024-01-01"),
                "SQL should not contain literal date values");
        assertFalse(result.getSql().contains("2024-01-31"),
                "SQL should not contain literal date values");

        // SQL 应使用 ? 占位符
        assertTrue(result.getSql().contains("?"),
                "SQL should use ? placeholders");

        // 应有 2 个参数
        assertEquals(2, result.getParams().size(),
                "Should have 2 parameters (startDate and endDate)");

        // SQL 结构检查
        String sql = result.getSql().toUpperCase();
        assertTrue(sql.contains("INSERT INTO"), "Should be an INSERT statement");
        assertTrue(sql.contains("SELECT"), "Should have SELECT clause");
        assertTrue(sql.contains("WHERE"), "Should have WHERE clause for date range");
        assertTrue(sql.contains("GROUP BY"), "Should have GROUP BY clause");
        assertTrue(sql.contains(" < ?"), "End boundary must be exclusive");
        assertFalse(sql.contains(" <= ?"), "Inclusive end would miss later rows in that bucket");
    }

    @Test
    @DisplayName("buildIncrementalInsertSql WHERE should be before GROUP BY")
    void testIncrementalInsertSqlWhereBeforeGroupBy() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        ParameterizedSql result = sqlBuilder.buildIncrementalInsertSql(
                mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate);

        String sqlUpper = result.getSql().toUpperCase();
        int whereIndex = sqlUpper.indexOf("WHERE");
        int groupByIndex = sqlUpper.indexOf("GROUP BY");

        assertTrue(whereIndex > 0, "Should have WHERE clause");
        assertTrue(groupByIndex > 0, "Should have GROUP BY clause");
        assertTrue(whereIndex < groupByIndex, "WHERE should be before GROUP BY");
    }

    @Test
    @DisplayName("semantic watermark should resolve source and materialized columns independently")
    void testSemanticWatermarkUsesIndependentPhysicalColumns() {
        refreshConfig.setWatermarkColumn("salesDate$id");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_date_key",
                "product$id", "materialized_product_id"
        ));

        DbDimension salesDate = createMockDimension("salesDate", "source_date_key", DbColumnType.DAY);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        ParameterizedSql deleteSql = sqlBuilder.buildIncrementalDeleteSql(
                mockPreAgg, refreshConfig, startDate, endDate);
        ParameterizedSql insertSql = sqlBuilder.buildIncrementalInsertSql(
                mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate);

        assertTrue(deleteSql.getSql().contains("materialized_date_key >= ?"),
                "DELETE must use the explicitly mapped materialized watermark column");
        assertTrue(deleteSql.getSql().contains("materialized_date_key < ?"));
        assertTrue(insertSql.getSql().contains("source_date_key >= ?"),
                "source WHERE must use the fact-table foreign key");
        assertTrue(insertSql.getSql().contains("source_date_key < ?"));
        assertFalse(insertSql.getSql().contains("WHERE materialized_date_key"),
                "the materialized watermark must not leak into the source WHERE clause");

        refreshConfig.setWatermarkColumn("salesDate$caption");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_date_key",
                "salesDate$caption", "materialized_full_date",
                "product$id", "materialized_product_id"
        ));
        DbColumn caption = createMockColumn(
                "source_full_date", DbColumnType.DAY, salesDate.getQueryObject());
        when(salesDate.getCaptionDbColumn()).thenReturn(caption);

        ParameterizedSql captionInsertSql = sqlBuilder.buildIncrementalInsertSql(
                mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate);
        assertTrue(captionInsertSql.getSql().contains(
                "LEFT JOIN dim_salesDate dim_salesDate"));
        assertTrue(captionInsertSql.getSql().contains(
                "WHERE dim_salesDate.source_full_date >= ?"),
                "caption watermark must use its proven dimension JOIN");
    }

    @Test
    @DisplayName("bare physical watermark should remain explicit on both sides")
    void testBarePhysicalWatermarkRemainsUnchanged() {
        refreshConfig.setWatermarkColumn("event_date");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "event_date",
                "product$id", "materialized_product_id"
        ));
        DbDimension salesDate = createMockDimension("salesDate", "event_date", DbColumnType.DAY);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);
        SqlColumn eventDate = mock(SqlColumn.class);
        when(eventDate.getJdbcType()).thenReturn(java.sql.Types.DATE);
        when(mockSourceModel.getQueryObject().getSqlColumn("event_date", false))
                .thenReturn(eventDate);

        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);
        ParameterizedSql deleteSql = sqlBuilder.buildIncrementalDeleteSql(
                mockPreAgg, refreshConfig, startDate, endDate);
        ParameterizedSql insertSql = sqlBuilder.buildIncrementalInsertSql(
                mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate);

        assertTrue(deleteSql.getSql().contains("event_date >= ?"));
        assertTrue(insertSql.getSql().contains("event_date >= ?"));
    }

    @Test
    @DisplayName("bare LocalDate watermark should retain and validate its target dimension grain")
    void testBarePhysicalWatermarkRejectsCoarseTargetGrain() {
        refreshConfig.setWatermarkColumn("event_date");
        when(mockPreAgg.getGranularity("salesDate")).thenReturn(TimeGranularity.MONTH);
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "event_date",
                "product$id", "materialized_product_id"
        ));
        DbDimension salesDate = createMockDimension("salesDate", "event_date", DbColumnType.DAY);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);
        SqlColumn eventDate = mock(SqlColumn.class);
        when(eventDate.getJdbcType()).thenReturn(java.sql.Types.DATE);
        when(mockSourceModel.getQueryObject().getSqlColumn("event_date", false))
                .thenReturn(eventDate);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PreAggPhysicalColumnContract.resolveWatermark(
                        mockPreAgg, mockSourceModel, refreshConfig, FDialect.MYSQL_DIALECT));

        assertTrue(error.getMessage().contains("exact DAY materialized granularity"));
        assertTrue(error.getMessage().contains("MONTH"));
    }

    @Test
    @DisplayName("bare watermark outside the materialized schema should fail closed")
    void testBareWatermarkOutsideMaterializedSchemaFailsClosed() {
        refreshConfig.setWatermarkColumn("event_date");

        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalDeleteSql(
                        mockPreAgg, refreshConfig, startDate, endDate));

        assertTrue(error.getMessage().contains("event_date"));
        assertTrue(error.getMessage().contains("declared materialized column"));
    }

    @Test
    @DisplayName("bare watermark source and target types should match")
    void testBareWatermarkSourceAndTargetTypeMismatchFailsClosed() {
        refreshConfig.setWatermarkColumn("event_date");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "event_date",
                "product$id", "materialized_product_id"
        ));
        DbDimension salesDate = createMockDimension(
                "salesDate", "source_date_key", DbColumnType.INTEGER);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);
        SqlColumn eventDate = mock(SqlColumn.class);
        when(eventDate.getJdbcType()).thenReturn(java.sql.Types.DATE);
        when(mockSourceModel.getQueryObject().getSqlColumn("event_date", false))
                .thenReturn(eventDate);

        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalInsertSql(
                        mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate));

        assertTrue(error.getMessage().contains("source/target types differ"));
    }

    @Test
    @DisplayName("numeric semantic id cannot consume LocalDate incremental bounds")
    void testNumericSemanticIdWatermarkFailsClosed() {
        refreshConfig.setWatermarkColumn("salesDate$id");
        DbDimension salesDate = createMockDimension(
                "salesDate", "source_date_key", DbColumnType.INTEGER);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalInsertSql(
                        mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate));

        assertTrue(error.getMessage().contains("salesDate$id"));
        assertTrue(error.getMessage().contains("DATE source watermark"));
    }

    @Test
    @DisplayName("SQLite text date requires a governed time caption and stays dialect-local")
    void testSqliteTextDateWatermarkIsDialectScoped() {
        refreshConfig.setWatermarkColumn("salesDate$caption");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_date_key",
                "salesDate$caption", "materialized_full_date",
                "product$id", "materialized_product_id"
        ));
        DbDimension salesDate = createMockDimension(
                "salesDate", "source_date_key", DbColumnType.INTEGER);
        when(salesDate.getTimeRole()).thenReturn("business_date");
        QueryObject salesDateQueryObject = salesDate.getQueryObject();
        DbColumn textDateCaption = createMockColumn(
                "source_full_date", DbColumnType.TEXT, salesDateQueryObject);
        when(salesDate.getCaptionDbColumn()).thenReturn(textDateCaption);
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);

        PreAggPhysicalColumnContract.WatermarkColumns sqlite =
                PreAggPhysicalColumnContract.resolveWatermark(
                        mockPreAgg, mockSourceModel, refreshConfig, FDialect.SQLITE_DIALECT);
        assertEquals("source_full_date", sqlite.sourceColumn().physicalName());

        assertThrows(IllegalArgumentException.class,
                () -> PreAggPhysicalColumnContract.resolveWatermark(
                        mockPreAgg, mockSourceModel, refreshConfig, FDialect.MYSQL_DIALECT),
                "a non-SQLite VARCHAR must not masquerade as a LocalDate contract");

        when(salesDate.getTimeRole()).thenReturn("foo");
        assertThrows(IllegalArgumentException.class,
                () -> PreAggPhysicalColumnContract.resolveWatermark(
                        mockPreAgg, mockSourceModel, refreshConfig, FDialect.SQLITE_DIALECT),
                "an arbitrary non-empty role is not a governed date contract");

        when(salesDate.getTimeRole()).thenReturn("system_time");
        assertThrows(IllegalArgumentException.class,
                () -> PreAggPhysicalColumnContract.resolveWatermark(
                        mockPreAgg, mockSourceModel, refreshConfig, FDialect.SQLITE_DIALECT),
                "a system timestamp role is not a LocalDate contract");
    }

    @Test
    @DisplayName("semantic watermark without explicit materialized mapping should fail closed")
    void testSemanticWatermarkWithoutExplicitMappingFailsClosed() {
        refreshConfig.setWatermarkColumn("salesDate$id");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "product$id", "materialized_product_id"
        ));
        when(mockPreAgg.getDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "inferred_date_key"
        ));

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalDeleteSql(
                        mockPreAgg, refreshConfig, startDate, endDate));
        assertTrue(error.getMessage().contains("salesDate$id"));
        assertTrue(error.getMessage().contains("explicit"));

        refreshConfig.setWatermarkColumn("unknown$id");
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id",
                "unknown$id", "materialized_unknown_key"
        ));
        IllegalArgumentException unknownError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalInsertSql(
                        mockPreAgg, mockSourceModel, refreshConfig, startDate, endDate));
        assertTrue(unknownError.getMessage().contains("Unknown pre-aggregation dimension"));
    }

    @Test
    @DisplayName("semantic id without source foreign key should fail closed")
    void testSemanticIdWithoutSourceForeignKeyFailsClosed() {
        DbDimension salesDate = mock(DbDimension.class);
        when(salesDate.getName()).thenReturn("salesDate");
        when(mockSourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel));

        assertTrue(error.getMessage().contains("salesDate$id"));
        assertTrue(error.getMessage().contains("source fact-table foreign key"));
    }

    // ==================== 全量刷新 SQL 测试 ====================

    @Test
    @DisplayName("buildFullRefreshInsertSql should generate correct INSERT...SELECT")
    void testFullRefreshInsertSql() {
        String sql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertNotNull(sql);
        String sqlUpper = sql.toUpperCase();

        assertTrue(sqlUpper.contains("INSERT INTO PREAGG_DAILY_SALES"), "Should target pre-agg table");
        assertTrue(sqlUpper.contains("SELECT"), "Should have SELECT");
        assertTrue(sqlUpper.contains("FROM SALES_ORDER"), "Should select from source table");
        assertTrue(sqlUpper.contains("GROUP BY"), "Should have GROUP BY");
        assertTrue(sqlUpper.contains("SUM("), "Should have SUM aggregation");
        assertTrue(sqlUpper.contains("COUNT(*)"), "Should have COUNT(*) for row count");
    }

    @Test
    @DisplayName("buildFullRefreshInsertSql should include _preagg_row_count and _preagg_created_at")
    void testFullRefreshInsertSqlMetadataColumns() {
        String sql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(sql.contains("_preagg_row_count"), "Should include row count column");
        assertTrue(sql.contains("_preagg_created_at"), "Should include created_at column");
        assertTrue(sql.contains("COUNT(*)"), "Should count rows");
    }

    @Test
    @DisplayName("COUNT materialization should not require a source measure column")
    void testCountMaterializationUsesCountStarAndBigint() {
        when(mockPreAgg.getMeasureAggregations()).thenReturn(
                new LinkedHashMap<>(Map.of("orderCount", DbAggregation.COUNT)));
        when(mockPreAgg.getMeasureColumnNames()).thenReturn(Map.of(
                "orderCount", "order_count"));
        when(mockSourceModel.findJdbcMeasureByName("orderCount")).thenReturn(null);

        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);
        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("order_count BIGINT"));
        assertTrue(refreshSql.contains("COUNT(*) AS order_count"));
        assertFalse(refreshSql.contains("src.orderCount"));
    }

    @Test
    @DisplayName("formula and semantic scale measures should use the model-rendered expression")
    void testFormulaMeasureUsesRenderedSourceExpression() {
        when(mockPreAgg.getMeasureAggregations()).thenReturn(
                new LinkedHashMap<>(Map.of("salesAmountFormulaYuan", DbAggregation.SUM)));
        when(mockPreAgg.getMeasureColumnNames()).thenReturn(Map.of(
                "salesAmountFormulaYuan", "sales_amount_formula_yuan_sum"));
        DbMeasure measure = mock(DbMeasure.class);
        DbColumn formulaColumn = mock(DbColumn.class);
        when(measure.getJdbcColumn()).thenReturn(formulaColumn);
        when(formulaColumn.getSqlColumnName()).thenReturn("salesAmountFormulaYuan");
        when(formulaColumn.getDeclare(null, "src", FDialect.MYSQL_DIALECT))
                .thenReturn("((src.sales_amount + 0) / 100)");
        when(mockSourceModel.findJdbcMeasureByName("salesAmountFormulaYuan"))
                .thenReturn(measure);

        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(refreshSql.contains(
                "SUM(((src.sales_amount + 0) / 100)) AS sales_amount_formula_yuan_sum"));
        assertFalse(refreshSql.contains("src.salesAmountFormulaYuan"));
    }

    // ==================== DDL 测试 ====================

    @Test
    @DisplayName("buildCreateTableDdl should generate correct CREATE TABLE")
    void testCreateTableDdl() {
        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);

        assertNotNull(ddl);
        assertTrue(ddl.contains("CREATE TABLE preagg_daily_sales"), "Should create pre-agg table");
        assertTrue(ddl.contains("PRIMARY KEY"), "Should have primary key");
        assertTrue(ddl.contains("_preagg_row_count"), "Should have row count column");
        assertTrue(ddl.contains("_preagg_created_at"), "Should have timestamp column");
        assertTrue(ddl.contains("materialized_order_date"),
                "dimension id must use the explicit materialized mapping");
        assertTrue(ddl.contains("materialized_product_id"),
                "dimension id must use the explicit materialized mapping");
    }

    @Test
    @DisplayName("SQL Server metadata columns should use DATETIME2 rather than rowversion TIMESTAMP")
    void testSqlServerMetadataColumnsUseDatetime2() {
        PreAggSqlBuilder sqlServerBuilder = new PreAggSqlBuilder(FDialect.SQLSERVER_DIALECT);

        String ddl = sqlServerBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("_preagg_created_at DATETIME2 DEFAULT GETDATE()"));
        assertTrue(ddl.contains("_preagg_updated_at DATETIME2"));
        assertFalse(ddl.contains(" TIMESTAMP"));
    }

    @Test
    @DisplayName("SQLite create-table DDL should execute with a legal timestamp default")
    void testSqliteCreateTableDdlExecutes() throws Exception {
        PreAggSqlBuilder sqliteBuilder = new PreAggSqlBuilder(FDialect.SQLITE_DIALECT);
        String ddl = sqliteBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("DEFAULT CURRENT_TIMESTAMP"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            assertEquals(0, statement.executeQuery(
                    "SELECT COUNT(*) FROM preagg_daily_sales").getInt(1));
        }
    }

    @Test
    @DisplayName("dimension properties should use explicit target and model source columns")
    void testDimensionPropertiesUseExplicitPhysicalContract() {
        when(mockPreAgg.getDimensionProperties("product"))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("caption", "categoryName")));
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id",
                "product$caption", "materialized_product_caption",
                "product$categoryName", "materialized_category_name"
        ));

        DbDimension product = mockSourceModel.findJdbcDimensionByName("product");
        QueryObject productQueryObject = product.getQueryObject();
        DbColumn captionColumn = createMockColumn(
                "source_product_caption", DbColumnType.TEXT, productQueryObject);
        when(product.getCaptionDbColumn()).thenReturn(captionColumn);
        DbProperty category = mock(DbProperty.class);
        when(category.getName()).thenReturn("categoryName");
        DbColumn categoryColumn = createMockColumn(
                "source_category_name", DbColumnType.TEXT, productQueryObject);
        when(category.getPropertyDbColumn()).thenReturn(categoryColumn);
        when(product.findPropertyByName("categoryName")).thenReturn(category);

        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);
        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("materialized_product_caption"));
        assertTrue(ddl.contains("materialized_category_name"));
        assertFalse(ddl.contains("\n    product_caption "),
                "DDL must not synthesize dimName_propertyName columns");
        assertTrue(refreshSql.contains(
                "LEFT JOIN dim_product dim_product ON src.product_id = dim_product.product_id"));
        assertTrue(refreshSql.contains("MAX(dim_product.source_product_caption)"));
        assertTrue(refreshSql.contains("AS materialized_product_caption"));
        assertTrue(refreshSql.contains("MAX(dim_product.source_category_name)"));
        assertTrue(refreshSql.contains("AS materialized_category_name"));
        assertFalse(refreshSql.contains("MAX(caption)"),
                "refresh must resolve source metadata instead of guessing property tokens");
    }

    @Test
    @DisplayName("time bucket property should use its explicit materialized column")
    void testTimeBucketPropertyUsesExplicitPhysicalContract() {
        when(mockPreAgg.getDimensionProperties("salesDate")).thenReturn(Set.of("month"));
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "salesDate$month", "materialized_month_bucket",
                "product$id", "materialized_product_id"
        ));

        DbDimension salesDate = mockSourceModel.findJdbcDimensionByName("salesDate");
        DbProperty month = mock(DbProperty.class);
        when(month.getName()).thenReturn("month");
        DbColumn monthColumn = createMockColumn(
                "source_month_number", DbColumnType.INTEGER, salesDate.getQueryObject());
        when(month.getPropertyDbColumn()).thenReturn(monthColumn);
        when(salesDate.findPropertyByName("month")).thenReturn(month);

        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);
        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("materialized_month_bucket"));
        assertTrue(refreshSql.contains(
                "MAX(dim_salesDate.source_month_number) AS materialized_month_bucket"));
        assertFalse(refreshSql.contains("salesDate_month"));
    }

    @Test
    @DisplayName("coarse time grain should truncate an explicit date caption contract")
    void testCoarseTimeGrainUsesExplicitDateCaptionContract() {
        refreshConfig.setStrategy("FULL");
        refreshConfig.setWatermarkColumn(null);
        when(mockPreAgg.getGranularity("salesDate")).thenReturn(TimeGranularity.MONTH);
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$caption", "materialized_month_bucket",
                "product$id", "materialized_product_id"
        ));

        DbDimension salesDate = mockSourceModel.findJdbcDimensionByName("salesDate");
        DbColumn captionColumn = createMockColumn(
                "source_full_date", DbColumnType.DAY, salesDate.getQueryObject());
        when(salesDate.getCaptionDbColumn()).thenReturn(captionColumn);

        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);
        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("materialized_month_bucket"));
        assertTrue(ddl.contains("PRIMARY KEY (materialized_month_bucket, materialized_product_id)"));
        assertTrue(refreshSql.contains("source_full_date"));
        assertTrue(refreshSql.contains("AS materialized_month_bucket"));
        assertFalse(refreshSql.contains("AS materialized_order_date"));
    }

    @Test
    @DisplayName("incremental LocalDate watermark should reject a coarse materialized grain")
    void testIncrementalDateWatermarkRejectsCoarseMaterializedGrain() {
        refreshConfig.setWatermarkColumn("salesDate$caption");
        when(mockPreAgg.getGranularity("salesDate")).thenReturn(TimeGranularity.MONTH);
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$caption", "materialized_month_bucket",
                "product$id", "materialized_product_id"
        ));

        DbDimension salesDate = mockSourceModel.findJdbcDimensionByName("salesDate");
        DbColumn captionColumn = createMockColumn(
                "source_full_date", DbColumnType.DAY, salesDate.getQueryObject());
        when(salesDate.getCaptionDbColumn()).thenReturn(captionColumn);

        IllegalArgumentException ddlError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel));
        IllegalArgumentException refreshError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel));
        IllegalArgumentException incrementalError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalInsertSql(
                        mockPreAgg, mockSourceModel, refreshConfig,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)));

        for (IllegalArgumentException error : List.of(
                ddlError, refreshError, incrementalError)) {
            assertTrue(error.getMessage().contains("exact DAY materialized granularity"));
            assertTrue(error.getMessage().contains("MONTH"));
        }
    }

    @Test
    @DisplayName("explicit mapping alone should be included in the materialization schema")
    void testExplicitMappingAloneIsMaterialized() {
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id",
                "product$caption", "materialized_product_caption"
        ));

        DbDimension product = mockSourceModel.findJdbcDimensionByName("product");
        DbColumn captionColumn = createMockColumn(
                "source_product_caption", DbColumnType.TEXT, product.getQueryObject());
        when(product.getCaptionDbColumn()).thenReturn(captionColumn);

        String ddl = sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel);
        String refreshSql = sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel);

        assertTrue(ddl.contains("materialized_product_caption"));
        assertTrue(refreshSql.contains(
                "MAX(dim_product.source_product_caption) AS materialized_product_caption"));
    }

    @Test
    @DisplayName("inferred property column name should not satisfy the Addon contract")
    void testInferredDimensionPropertyMappingFailsClosed() {
        when(mockPreAgg.getDimensionProperties("product")).thenReturn(Set.of("caption"));
        when(mockPreAgg.getDimensionPropertyColumnNames()).thenReturn(Map.of(
                "product$caption", "inferred_product_name"
        ));

        IllegalArgumentException ddlError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel));
        IllegalArgumentException refreshError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel));

        assertTrue(ddlError.getMessage().contains("product$caption"));
        assertTrue(refreshError.getMessage().contains("product$caption"));
    }

    @Test
    @DisplayName("unknown dimension property source should fail closed")
    void testUnknownDimensionPropertySourceFailsClosed() {
        when(mockPreAgg.getDimensionProperties("product")).thenReturn(Set.of("unknownProperty"));
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id",
                "product$unknownProperty", "materialized_unknown"
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildFullRefreshInsertSql(mockPreAgg, mockSourceModel));
        assertTrue(error.getMessage().contains("product$unknownProperty"));
        assertTrue(error.getMessage().contains("source"));

        when(mockPreAgg.getDimensionProperties("product")).thenReturn(Set.of());
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "salesDate$caption", "materialized_full_date",
                "product$id", "materialized_product_id"
        ));
        refreshConfig.setWatermarkColumn("salesDate$caption");
        DbDimension salesDate = mockSourceModel.findJdbcDimensionByName("salesDate");
        QueryObject unjoinedQueryObject = mock(QueryObject.class);
        when(unjoinedQueryObject.getAlias()).thenReturn("unjoined_date");
        when(unjoinedQueryObject.getBody()).thenReturn("rogue_date");
        DbColumn unjoinedCaption = createMockColumn(
                "full_date", DbColumnType.DAY, unjoinedQueryObject);
        when(salesDate.getCaptionDbColumn()).thenReturn(unjoinedCaption);

        IllegalArgumentException joinError = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildIncrementalInsertSql(
                        mockPreAgg, mockSourceModel, refreshConfig,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
        assertTrue(joinError.getMessage().contains("Cannot prove source table"));
    }

    @Test
    @DisplayName("boolean property should fail closed until every dialect has safe DDL and aggregation")
    void testBooleanDimensionPropertyFailsClosed() {
        when(mockPreAgg.getDimensionProperties("product")).thenReturn(Set.of("active"));
        when(mockPreAgg.getExplicitDimensionPropertyColumnNames()).thenReturn(Map.of(
                "salesDate$id", "materialized_order_date",
                "product$id", "materialized_product_id",
                "product$active", "materialized_active"
        ));
        DbDimension product = mockSourceModel.findJdbcDimensionByName("product");
        DbProperty active = mock(DbProperty.class);
        DbColumn activeColumn = createMockColumn(
                "source_active", DbColumnType.BOOL, product.getQueryObject());
        when(active.getPropertyDbColumn()).thenReturn(activeColumn);
        when(product.findPropertyByName("active")).thenReturn(active);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sqlBuilder.buildCreateTableDdl(mockPreAgg, mockSourceModel));

        assertTrue(error.getMessage().contains("BOOL"));
    }

    @Test
    @DisplayName("built-in DATE watermark contract should stay aligned across TM and external schemas")
    void testBuiltInDateWatermarkContract() throws Exception {
        Path repositoryRoot = findRepositoryRoot();
        String salesModel = read(repositoryRoot.resolve(
                "foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactSalesPreAggModel.tm"));
        String returnModel = read(repositoryRoot.resolve(
                "foggy-dataset-demo/src/main/resources/foggy/templates/preagg_test/model/FactReturnPreAggModel.tm"));

        assertTrue(salesModel.contains("timeRole: 'business_date'"));
        assertTrue(salesModel.contains("watermarkColumn: 'salesDate$caption'"));
        assertTrue(returnModel.contains("timeRole: 'business_date'"));
        assertTrue(returnModel.contains("watermarkColumn: 'returnDate$caption'"));

        assertNativeDateColumn(repositoryRoot.resolve(
                "foggy-dataset-demo/docker/mysql/init/01-schema.sql"), "`full_date`");
        assertNativeDateColumn(repositoryRoot.resolve(
                "foggy-dataset-demo/docker/postgres/init/01-schema.sql"), "full_date");
        assertNativeDateColumn(repositoryRoot.resolve(
                "foggy-dataset-demo/docker/sqlserver/init/01-schema.sql"), "full_date");
    }

    // ==================== Helper Methods ====================

    private Path findRepositoryRoot() {
        String start = System.getProperty(
                "maven.multiModuleProjectDirectory", System.getProperty("user.dir", "."));
        Path current = Path.of(start).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("foggy-dataset-demo"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + start);
    }

    private void assertNativeDateColumn(Path schema, String quotedColumn) throws Exception {
        String ddl = read(schema).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        String column = quotedColumn.toUpperCase(Locale.ROOT);
        assertTrue(ddl.contains(column + " DATE NOT NULL"),
                () -> schema + " must declare " + quotedColumn + " as native DATE");
        assertFalse(ddl.contains(column + " VARCHAR"),
                () -> schema + " must not expose the LocalDate watermark as VARCHAR");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private DbDimension createMockDimension(String name, String columnName, DbColumnType type) {
        DbDimension dim = mock(DbDimension.class);
        when(dim.getName()).thenReturn(name);

        DbColumn idColumn = createMockColumn(columnName, type);
        when(dim.getPrimaryKeyDbColumn()).thenReturn(idColumn);
        when(dim.getForeignKeyDbColumn()).thenReturn(idColumn);
        when(dim.getForeignKey()).thenReturn(columnName);
        QueryObject dimensionQueryObject = mock(QueryObject.class);
        when(dimensionQueryObject.getBody()).thenReturn("dim_" + name);
        when(dimensionQueryObject.getAlias()).thenReturn("dim_" + name);
        when(dimensionQueryObject.getPrimaryKey()).thenReturn(columnName);
        when(dim.getQueryObject()).thenReturn(dimensionQueryObject);

        return dim;
    }

    private DbColumn createMockColumn(String columnName, DbColumnType type) {
        return createMockColumn(columnName, type, null);
    }

    private DbColumn createMockColumn(String columnName, DbColumnType type, QueryObject queryObject) {
        DbColumn column = mock(DbColumn.class);
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(columnName);
        when(column.getSqlColumnName()).thenReturn(columnName);
        when(column.getType()).thenReturn(type);
        when(column.getSqlColumn()).thenReturn(sqlColumn);
        when(column.getQueryObject()).thenReturn(queryObject);
        return column;
    }

    private DbMeasure createMockMeasure(String name, String columnName) {
        DbMeasure measure = mock(DbMeasure.class);
        when(measure.getName()).thenReturn(name);

        DbColumn jdbcColumn = mock(DbColumn.class);
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(columnName);
        when(jdbcColumn.getSqlColumnName()).thenReturn(columnName);
        when(jdbcColumn.getSqlColumn()).thenReturn(sqlColumn);
        when(jdbcColumn.getDeclare(null, "src", FDialect.MYSQL_DIALECT))
                .thenReturn("src." + columnName);
        when(measure.getJdbcColumn()).thenReturn(jdbcColumn);

        return measure;
    }
}
