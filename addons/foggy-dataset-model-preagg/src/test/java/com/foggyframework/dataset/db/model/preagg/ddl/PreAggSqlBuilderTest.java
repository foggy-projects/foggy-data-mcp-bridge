package com.foggyframework.dataset.db.model.preagg.ddl;

import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.db.table.SqlColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        refreshConfig.setWatermarkColumn("order_date");
        refreshConfig.setLookbackDays(3);
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
        assertTrue(sql.contains("<="), "Should have <= comparison");
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
    }

    // ==================== Helper Methods ====================

    private DbDimension createMockDimension(String name, String columnName, DbColumnType type) {
        DbDimension dim = mock(DbDimension.class);
        when(dim.getName()).thenReturn(name);

        DbColumn idColumn = mock(DbColumn.class);
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(columnName);
        when(idColumn.getSqlColumnName()).thenReturn(columnName);
        when(idColumn.getType()).thenReturn(type);
        when(idColumn.getSqlColumn()).thenReturn(sqlColumn);
        when(dim.getPrimaryKeyDbColumn()).thenReturn(idColumn);

        return dim;
    }

    private DbMeasure createMockMeasure(String name, String columnName) {
        DbMeasure measure = mock(DbMeasure.class);
        when(measure.getName()).thenReturn(name);

        DbColumn jdbcColumn = mock(DbColumn.class);
        SqlColumn sqlColumn = mock(SqlColumn.class);
        when(sqlColumn.getName()).thenReturn(columnName);
        when(jdbcColumn.getSqlColumnName()).thenReturn(columnName);
        when(jdbcColumn.getSqlColumn()).thenReturn(sqlColumn);
        when(measure.getJdbcColumn()).thenReturn(jdbcColumn);

        return measure;
    }
}
