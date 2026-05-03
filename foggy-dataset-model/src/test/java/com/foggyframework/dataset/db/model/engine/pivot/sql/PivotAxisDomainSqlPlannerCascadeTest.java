package com.foggyframework.dataset.db.model.engine.pivot.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
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

public class PivotAxisDomainSqlPlannerCascadeTest {

    private ManagedSqlRelation buildRelation(FDialect dialect, String sql, List<ManagedMetricMetadata> metrics) {
        return new ManagedSqlRelation(sql, Arrays.asList("baseParam1"), dialect, null, null,
                true, true, false, metrics);
    }

    private List<ManagedMetricMetadata> defaultMetrics() {
        return Arrays.asList(
                ManagedMetricMetadata.builder().metricName("salesAmount").additiveKind(AdditiveKind.ADDITIVE).aggregationFunction("SUM").build(),
                ManagedMetricMetadata.builder().metricName("orderCount").additiveKind(AdditiveKind.ADDITIVE).aggregationFunction("COUNT").build()
        );
    }

    @Test
    public void testTwoLevelRowsCascadeStagedCteShape() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation rel = buildRelation(dialect,
                "SELECT dim1, dim2, SUM(salesAmount) as salesAmount FROM tbl WHERE status = ?",
                defaultMetrics());

        PivotRequest pivot = new PivotRequest();

        // Level 1: dim1
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-salesAmount"));
        MetricFilter filter1 = new MetricFilter();
        filter1.setMetric("salesAmount");
        filter1.setOp(">");
        filter1.setValue(1000);
        f1.setHaving(Arrays.asList(filter1));

        // Level 2: dim2
        AxisField f2 = new AxisField();
        f2.setField("dim2");
        f2.setLimit(3);
        f2.setOrderBy(Arrays.asList("-orderCount"));

        pivot.setRows(Arrays.asList(f1, f2));
        pivot.setColumns(Collections.emptyList());

        PivotAxisDomainSqlPlanner.PlannedSql result = PivotAxisDomainSqlPlanner.plan(
                rel, pivot, Arrays.asList("dim1", "dim2"), Collections.emptyList(), Arrays.asList("salesAmount", "orderCount"));

        String sql = result.getSql();

        // Parent CTE Level 1
        assertTrue(sql.contains("_row_domain_1 AS"));
        assertTrue(sql.contains("SELECT b.\"dim1\" AS \"dim1\", SUM(b.\"salesAmount\") AS _agg_salesAmount"));
        assertTrue(sql.contains("_row_domain_filtered_1 AS"));
        assertTrue(sql.contains("_agg_salesAmount > ?"));
        assertTrue(sql.contains("_row_ranked_1 AS"));
        assertTrue(sql.contains("ORDER BY CASE WHEN _agg_salesAmount IS NULL THEN 1 ELSE 0 END ASC, _agg_salesAmount DESC, CASE WHEN \"dim1\" IS NULL THEN 1 ELSE 0 END ASC, \"dim1\" ASC"));
        assertTrue(sql.contains("_row_filtered_1 AS"));

        // Child CTE Level 2
        assertTrue(sql.contains("_row_domain_2 AS"));
        assertTrue(sql.contains("SELECT b.\"dim1\" AS \"dim1\", b.\"dim2\" AS \"dim2\", SUM(b.\"orderCount\") AS _agg_orderCount"));
        // Child CTE should join previous surviving domain
        assertTrue(sql.contains("INNER JOIN _row_filtered_1 _row_filtered_1 ON (b.\"dim1\" = _row_filtered_1.\"dim1\" OR (b.\"dim1\" IS NULL AND _row_filtered_1.\"dim1\" IS NULL))"));

        assertTrue(sql.contains("_row_ranked_2 AS"));
        // Partitioned by dim1
        assertTrue(sql.contains("PARTITION BY \"dim1\" ORDER BY CASE WHEN _agg_orderCount IS NULL THEN 1 ELSE 0 END ASC, _agg_orderCount DESC, CASE WHEN \"dim1\" IS NULL THEN 1 ELSE 0 END ASC, \"dim1\" ASC, CASE WHEN \"dim2\" IS NULL THEN 1 ELSE 0 END ASC, \"dim2\" ASC"));
        assertTrue(sql.contains("_row_filtered_2 AS"));

        // Final Join
        assertTrue(sql.contains("_filtered AS"));
        assertTrue(sql.contains("INNER JOIN _row_filtered_1 _row_filtered_1 ON (b.\"dim1\" = _row_filtered_1.\"dim1\" OR (b.\"dim1\" IS NULL AND _row_filtered_1.\"dim1\" IS NULL))"));
        assertTrue(sql.contains("INNER JOIN _row_filtered_2 _row_filtered_2 ON (b.\"dim1\" = _row_filtered_2.\"dim1\" OR (b.\"dim1\" IS NULL AND _row_filtered_2.\"dim1\" IS NULL)) AND (b.\"dim2\" = _row_filtered_2.\"dim2\" OR (b.\"dim2\" IS NULL AND _row_filtered_2.\"dim2\" IS NULL))"));

        // Parameter order verification
        List<Object> params = result.getParams();
        assertEquals(4, params.size());
        assertEquals("baseParam1", params.get(0));
        assertEquals(1000, params.get(1)); // Parent Having
        assertEquals(5, params.get(2));    // Parent Limit
        assertEquals(3, params.get(3));    // Child Limit
    }
}
