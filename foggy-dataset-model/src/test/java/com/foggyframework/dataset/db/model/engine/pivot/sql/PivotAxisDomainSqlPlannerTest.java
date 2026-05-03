package com.foggyframework.dataset.db.model.engine.pivot.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.PostgresDialect;
import com.foggyframework.dataset.db.dialect.SqlServerDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PivotAxisDomainSqlPlannerTest {

    private ManagedSqlRelation buildRelation(FDialect dialect, String sql, List<ManagedMetricMetadata> metrics,
                                              boolean wrappable, boolean permissionValidated) {
        return new ManagedSqlRelation(sql, Collections.emptyList(), dialect, null, null,
                wrappable, permissionValidated, false, metrics);
    }

    private List<ManagedMetricMetadata> defaultMetrics() {
        return Arrays.asList(
                ManagedMetricMetadata.builder().metricName("salesAmount").additiveKind(AdditiveKind.ADDITIVE).aggregationFunction("SUM").build(),
                ManagedMetricMetadata.builder().metricName("orderCount").additiveKind(AdditiveKind.ADDITIVE).aggregationFunction("COUNT").build(),
                ManagedMetricMetadata.builder().metricName("avgPrice").additiveKind(AdditiveKind.NON_ADDITIVE).aggregationFunction("AVG").build()
        );
    }

    @Test
    public void testBasicRowLimit() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, SUM(salesAmount) as salesAmount FROM tbl GROUP BY dim1",
                defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(), Arrays.asList("salesAmount", "orderCount"));

        String sql = result.getSql();
        System.out.println(sql);

        assertTrue(sql.contains("WITH _base_relation AS"));
        assertTrue(sql.contains("_row_domain_1 AS"));
        assertTrue(sql.contains("SUM(b.\"salesAmount\") AS _agg_salesAmount"));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertTrue(sql.contains("_agg_salesAmount DESC"));
        assertTrue(sql.contains("CASE WHEN \"dim1\" IS NULL THEN 1 ELSE 0 END ASC, \"dim1\" ASC"));
        assertTrue(sql.contains("rn <= ?"));
        assertTrue(sql.contains("INNER JOIN _row_filtered_1"));
        // Verify parameterization: limit value should be in params
        assertTrue(result.getParams().contains(5));
    }

    @Test
    public void testHavingBeforeTopN() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, SUM(salesAmount) as salesAmount FROM tbl GROUP BY dim1",
                defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(3);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">");
        filter.setValue(1000);
        f1.setHaving(Arrays.asList(filter));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(), Arrays.asList("salesAmount"));

        String sql = result.getSql();
        System.out.println(sql);

        // Having should appear BEFORE ranking
        assertTrue(sql.contains("_row_domain_filtered_1 AS"));
        assertTrue(sql.contains("_agg_salesAmount > ?"));
        // Ranked CTE sources from filtered domain
        assertTrue(sql.contains("FROM _row_domain_filtered_1"));
        // Final TopN
        assertTrue(sql.contains("rn <= ?"));
        // Verify parameterization: having value + limit in params
        assertEquals(2, result.getParams().size());
        assertEquals(1000, result.getParams().get(0));
        assertEquals(3, result.getParams().get(1));
    }

    @Test
    public void testHavingOnlyNoLimit() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, SUM(salesAmount) as salesAmount FROM tbl GROUP BY dim1",
                defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        // No limit, only having
        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">=");
        filter.setValue(500);
        f1.setHaving(Arrays.asList(filter));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(), Arrays.asList("salesAmount"));

        String sql = result.getSql();
        System.out.println(sql);

        assertTrue(sql.contains("_row_domain_filtered_1 AS"));
        assertTrue(sql.contains("_agg_salesAmount >= ?"));
        // No ROW_NUMBER since no limit
        assertFalse(sql.contains("ROW_NUMBER()"));
        // The filtered domain is used directly as join source
        assertTrue(sql.contains("INNER JOIN _row_domain_filtered_1"));
        // Verify parameterization: only having value in params
        assertEquals(1, result.getParams().size());
        assertEquals(500, result.getParams().get(0));
    }

    @Test
    public void testNonAdditiveOrderByFailsClosed() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, AVG(price) as avgPrice FROM tbl GROUP BY dim1",
                defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-avgPrice"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        assertThrows(PivotPushdownUnsupportedException.class, () -> {
            PivotAxisDomainSqlPlanner.plan(rel, pivot, Arrays.asList("dim1"),
                    Collections.emptyList(), Arrays.asList("avgPrice"));
        });
    }

    @Test
    public void testNotWrappableFailsClosed() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1 FROM tbl",
                defaultMetrics(), false, true); // wrappable = false

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        assertThrows(PivotPushdownUnsupportedException.class, () -> {
            PivotAxisDomainSqlPlanner.plan(rel, pivot, Arrays.asList("dim1"),
                    Collections.emptyList(), Arrays.asList("salesAmount"));
        });
    }

    @Test
    public void testNotPermissionValidatedFailsClosed() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1 FROM tbl",
                defaultMetrics(), true, false); // permissionValidated = false

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        // wrappable depends on permissionValidated, so isWrappable() is checked first
        // The backward-compat constructor would set wrappable=false if perm not validated
        // But here we explicitly pass wrappable=true, permissionValidated=false
        assertThrows(PivotPushdownUnsupportedException.class, () -> {
            PivotAxisDomainSqlPlanner.plan(rel, pivot, Arrays.asList("dim1"),
                    Collections.emptyList(), Arrays.asList("salesAmount"));
        });
    }

    @Test
    public void testMysql57Unsupported() {
        FDialect dialect = new MysqlDialect();
        ManagedSqlRelation rel = buildRelation(dialect, "SELECT 1", defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        assertThrows(PivotPushdownUnsupportedException.class, () -> {
            PivotAxisDomainSqlPlanner.plan(rel, pivot, Arrays.asList("dim1"),
                    Collections.emptyList(), Arrays.asList("salesAmount"));
        });
    }

    @Test
    public void testC2DialectWhitelist() {
        assertTrue(PivotAxisDomainSqlPlanner.isSupported(new SqliteDialect()));
        assertTrue(PivotAxisDomainSqlPlanner.isSupported(new PostgresDialect()));
        assertTrue(PivotAxisDomainSqlPlanner.isSupported(new Mysql8TestDialect()));
        assertFalse(PivotAxisDomainSqlPlanner.isSupported(new MysqlDialect()));
        assertFalse(PivotAxisDomainSqlPlanner.isSupported(new SqlServerDialect()));
    }

    private static class Mysql8TestDialect extends MysqlDialect {
        @Override
        public boolean supportsCte() {
            return true;
        }

        @Override
        public boolean supportsWindowFunctions() {
            return true;
        }
    }

    @Test
    public void testNoLimitNoHavingPassthrough() {
        FDialect dialect = new SqliteDialect();
        String baseSql = "SELECT dim1, SUM(salesAmount) as salesAmount FROM tbl GROUP BY dim1";
        ManagedSqlRelation rel = buildRelation(dialect, baseSql, defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        // No limit, no having
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(), Arrays.asList("salesAmount"));

        // Should pass through base SQL unchanged
        assertEquals(baseSql, result.getSql());
    }

    @Test
    public void testDifferentHavingAndOrderByMetrics() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, SUM(salesAmount) as salesAmount, SUM(orderCount) as orderCount FROM tbl GROUP BY dim1",
                defaultMetrics(), true, true);

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount")); // Order by salesAmount
        MetricFilter filter = new MetricFilter();
        filter.setMetric("orderCount"); // Having on different metric
        filter.setOp(">");
        filter.setValue(10);
        f1.setHaving(Arrays.asList(filter));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(),
                Arrays.asList("salesAmount", "orderCount"));

        String sql = result.getSql();
        System.out.println(sql);

        // Domain CTE must compute BOTH metrics
        assertTrue(sql.contains("SUM(b.\"salesAmount\") AS _agg_salesAmount"));
        assertTrue(sql.contains("SUM(b.\"orderCount\") AS _agg_orderCount"));
        // Having on orderCount (parameterized)
        assertTrue(sql.contains("_agg_orderCount > ?"));
        // OrderBy on salesAmount
        assertTrue(sql.contains("_agg_salesAmount DESC"));
        // Verify parameterization: having value + limit in params
        assertEquals(2, result.getParams().size());
        assertEquals(10, result.getParams().get(0));  // having value
        assertEquals(5, result.getParams().get(1));    // limit
    }

    @Test
    public void testBaseRelationParamsWithHavingAndLimit() {
        FDialect dialect = new SqliteDialect();
        // Simulate a base relation that already has parameters from preAgg or systemSlice
        List<Object> baseParams = Arrays.asList("preAggParam1", 2024);
        ManagedSqlRelation rel = new ManagedSqlRelation(
                "SELECT dim1, SUM(salesAmount) as salesAmount FROM tbl WHERE dim1 = ? AND year = ?",
                baseParams, dialect, null, null,
                true, true, false, defaultMetrics());

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        
        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">");
        filter.setValue(1000);
        f1.setHaving(Arrays.asList(filter));
        
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1"), Collections.emptyList(), Arrays.asList("salesAmount"));

        List<Object> params = result.getParams();
        // The order should be: baseParams, then having params, then limit params
        assertEquals(4, params.size(), "Parameter size mismatch");
        assertEquals("preAggParam1", params.get(0), "Base param 1 mismatch");
        assertEquals(2024, params.get(1), "Base param 2 mismatch");
        assertEquals(1000, params.get(2), "Having param mismatch"); // having value
        assertEquals(5, params.get(3), "Limit param mismatch"); // limit value
    }
}
