package com.foggyframework.dataset.model.preagg.ddl;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.preagg.PreAggQueryRewriter;
import com.foggyframework.dataset.model.engine.preagg.PreAggRewriteResult;
import com.foggyframework.dataset.model.engine.preagg.PreAggregationMatchResult;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Executable contract for AVG pre-aggregation states.
 *
 * <p>This deliberately crosses the Addon/Engine boundary: production DDL and refresh SQL
 * materialize SUM/COUNT states, then the production query rewriter merges those states.
 * Unequal group sizes and a null input make an accidental AVG-of-AVG observable.</p>
 */
class PreAggAverageStateLifecycleTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2025, 1, 1);
    private static final LocalDate SECOND_DAY = LocalDate.of(2026, 1, 1);

    @Test
    void generatedSqlPreservesWeightedAverageAcrossFullAndIncrementalRefresh() throws Exception {
        Fixture fixture = fixture();
        PreAggSqlBuilder builder = new PreAggSqlBuilder(FDialect.SQLITE_DIALECT);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            execute(connection, "CREATE TABLE sales_order ("
                    + "order_date DATE NOT NULL, amount DECIMAL(18,2))");
            execute(connection, builder.buildCreateTableDdl(
                    fixture.preAggregation(), fixture.sourceModel()));

            insertSource(connection, FIRST_DAY, "100.00");
            insertSource(connection, FIRST_DAY, null);
            insertSource(connection, SECOND_DAY, "900.00");
            insertSource(connection, SECOND_DAY, "900.00");
            insertSource(connection, SECOND_DAY, "900.00");

            execute(connection, builder.buildFullRefreshInsertSql(
                    fixture.preAggregation(), fixture.sourceModel()));

            PreAggRewriteResult rewrite = rewrite(fixture);
            assertTrue(rewrite.isApplied());
            assertTrue(rewrite.getSql().contains("SUM(pa.amount_avg__sum)"));
            assertTrue(rewrite.getSql().contains("SUM(pa.amount_avg__count)"));
            assertEquals(700.0d, queryScalar(connection, rewrite.getSql()), 0.000000001d);
            assertEquals(4L, queryLong(connection,
                    "SELECT SUM(amount_avg__count) FROM preagg_daily_amount"),
                    "AVG denominator must count only non-null source expressions");
            assertNotEquals(500.0d, queryScalar(connection, rewrite.getSql()),
                    "weighted AVG must differ from the simple average of 100 and 900");

            insertSource(connection, SECOND_DAY, "1300.00");
            ParameterizedSql delete = builder.buildIncrementalDeleteSql(
                    fixture.preAggregation(), fixture.refreshConfig(),
                    SECOND_DAY, SECOND_DAY.plusDays(1));
            execute(connection, delete);
            ParameterizedSql insert = builder.buildIncrementalInsertSql(
                    fixture.preAggregation(), fixture.sourceModel(), fixture.refreshConfig(),
                    SECOND_DAY, SECOND_DAY.plusDays(1));
            execute(connection, insert);

            assertEquals(820.0d, queryScalar(connection, rewrite.getSql()), 0.000000001d,
                    "incremental refresh must replace the affected state bucket before rollup");
            assertEquals(queryScalar(connection, "SELECT AVG(amount) FROM sales_order"),
                    queryScalar(connection, rewrite.getSql()), 0.000000001d,
                    "rewritten pre-aggregation query must equal the raw-detail AVG");
        }
    }

    private Fixture fixture() {
        PreAggRefreshDef refresh = new PreAggRefreshDef();
        refresh.setStrategy("INCREMENTAL");
        refresh.setWatermarkColumn("salesDate$id");
        refresh.setLookbackDays(3);

        PreAggregation preAggregation = mock(PreAggregation.class);
        when(preAggregation.getName()).thenReturn("daily_amount");
        when(preAggregation.getTableName()).thenReturn("preagg_daily_amount");
        when(preAggregation.getSchema()).thenReturn(null);
        when(preAggregation.getQualifiedTableName()).thenCallRealMethod();
        when(preAggregation.getDimensionNames()).thenReturn(Set.of("salesDate"));
        when(preAggregation.getGranularities()).thenReturn(
                Map.of("salesDate", TimeGranularity.DAY));
        when(preAggregation.getGranularity("salesDate")).thenReturn(TimeGranularity.DAY);
        when(preAggregation.getDimensionProperties("salesDate")).thenReturn(Set.of());
        when(preAggregation.getExplicitDimensionPropertyColumnNames()).thenReturn(
                Map.of("salesDate$id", "materialized_order_date"));
        when(preAggregation.getMeasureAggregations()).thenReturn(
                Map.of("averageAmount", DbAggregation.AVG));
        when(preAggregation.getMeasureColumnNames()).thenReturn(
                Map.of("averageAmount", "amount_avg"));
        when(preAggregation.getRefreshConfig()).thenReturn(refresh);

        QueryObject sourceQueryObject = mock(QueryObject.class);
        when(sourceQueryObject.getBody()).thenReturn("sales_order");
        when(sourceQueryObject.getAlias()).thenReturn("sales_source");

        DbColumn dateColumn = mock(DbColumn.class);
        when(dateColumn.getSqlColumnName()).thenReturn("order_date");
        when(dateColumn.getType()).thenReturn(DbColumnType.DAY);
        SqlColumn dateSqlColumn = mock(SqlColumn.class);
        when(dateSqlColumn.getName()).thenReturn("order_date");
        when(dateColumn.getSqlColumn()).thenReturn(dateSqlColumn);

        DbDimension salesDate = mock(DbDimension.class);
        when(salesDate.getName()).thenReturn("salesDate");
        when(salesDate.getForeignKey()).thenReturn("order_date");
        when(salesDate.getForeignKeyDbColumn()).thenReturn(dateColumn);
        when(salesDate.getPrimaryKeyDbColumn()).thenReturn(dateColumn);

        DbColumn amountColumn = mock(DbColumn.class);
        when(amountColumn.getSqlColumnName()).thenReturn("amount");
        when(amountColumn.getType()).thenReturn(DbColumnType.NUMBER);
        when(amountColumn.getDeclare(null, "src", FDialect.SQLITE_DIALECT))
                .thenReturn("src.amount");
        DbMeasure amountMeasure = mock(DbMeasure.class);
        when(amountMeasure.getName()).thenReturn("averageAmount");
        when(amountMeasure.getJdbcColumn()).thenReturn(amountColumn);

        TableModel sourceModel = mock(TableModel.class);
        when(sourceModel.getName()).thenReturn("SalesModel");
        when(sourceModel.getTableName()).thenReturn("sales_order");
        when(sourceModel.getQueryObject()).thenReturn(sourceQueryObject);
        when(sourceModel.findJdbcDimensionByName("salesDate")).thenReturn(salesDate);
        when(sourceModel.findJdbcMeasureByName("averageAmount")).thenReturn(amountMeasure);

        DbColumn queryAverage = mock(DbColumn.class);
        when(queryAverage.isMeasure()).thenReturn(true);
        when(queryAverage.getName()).thenReturn("averageAmount");
        when(queryAverage.getAlias()).thenReturn("averageAmount");
        when(queryAverage.getAggregation()).thenReturn(DbAggregation.AVG);
        when(queryAverage.getType()).thenReturn(DbColumnType.NUMBER);

        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getDialect()).thenReturn(FDialect.SQLITE_DIALECT);
        when(queryModel.findJdbcColumnForCond(eq("averageAmount"), anyBoolean(), anyBoolean()))
                .thenReturn(queryAverage);

        return new Fixture(preAggregation, sourceModel, refresh, queryModel, queryAverage);
    }

    private PreAggRewriteResult rewrite(Fixture fixture) {
        JdbcQuery query = new JdbcQuery();
        query.setSelect(query.new JdbcSelect());
        query.getSelect().setColumns(List.of(fixture.queryAverage()));
        return new PreAggQueryRewriter(fixture.queryModel(), null).rewrite(
                PreAggregationMatchResult.matched(fixture.preAggregation(), true, 100),
                query, new DbQueryRequestDef(), mock(JdbcModelQueryEngine.class));
    }

    private void insertSource(Connection connection, LocalDate day, String amount) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales_order(order_date, amount) VALUES (?, ?)")) {
            statement.setString(1, day.toString());
            statement.setString(2, amount);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void execute(Connection connection, ParameterizedSql sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql.getSql())) {
            for (int index = 0; index < sql.getParams().size(); index++) {
                Object value = sql.getParams().get(index);
                statement.setString(index + 1,
                        value instanceof LocalDate day ? day.toString() : String.valueOf(value));
            }
            statement.executeUpdate();
        }
    }

    private double queryScalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getDouble(1);
        }
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }

    private record Fixture(PreAggregation preAggregation,
                           TableModel sourceModel,
                           PreAggRefreshDef refreshConfig,
                           JdbcQueryModel queryModel,
                           DbColumn queryAverage) {
    }
}
