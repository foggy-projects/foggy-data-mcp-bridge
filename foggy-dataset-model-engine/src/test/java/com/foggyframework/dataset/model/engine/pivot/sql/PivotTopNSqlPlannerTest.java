package com.foggyframework.dataset.model.engine.pivot.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PivotTopNSqlPlannerTest {

    @Test
    public void testSqliteRowLimit() {
        FDialect dialect = new SqliteDialect();
        ManagedSqlRelation baseRelation = new ManagedSqlRelation(
                "SELECT dim1, SUM(m1) as m1 FROM tbl GROUP BY dim1",
                List.of(), dialect, null, null
        );

        PivotRequest pivot = new PivotRequest();
        AxisField f1 = new AxisField();
        f1.setField("dim1");
        f1.setLimit(5);
        f1.setOrderBy(Arrays.asList("-m1"));
        pivot.setRows(Arrays.asList(f1));
        pivot.setColumns(List.of());

        QueryModel qm = Mockito.mock(QueryModel.class);
        List<String> rowFields = Arrays.asList("dim1");
        List<String> colFields = List.of();
        List<String> metrics = Arrays.asList("m1");

        PivotTopNSqlPlanner.PlannedSql planned = PivotTopNSqlPlanner.plan(
                baseRelation, pivot, rowFields, colFields, metrics, qm
        );

        System.out.println(planned.getSql());
        assertTrue(planned.getSql().contains("WITH _base_relation AS"));
        assertTrue(planned.getSql().contains("_row_domain_1 AS"));
        assertTrue(planned.getSql().contains("ROW_NUMBER() OVER"));
        assertTrue(planned.getSql().contains("ORDER BY"));
        assertTrue(planned.getSql().contains("DESC"));
        assertTrue(planned.getSql().contains("rn <="));
        assertTrue(planned.getSql().contains("INNER JOIN _row_filtered_1"));
    }

    @Test
    public void testMysqlUnsupported() {
        FDialect dialect = new MysqlDialect();
        ManagedSqlRelation baseRelation = new ManagedSqlRelation(
                "SELECT dim1, m1 FROM tbl",
                List.of(), dialect, null, null
        );

        assertThrows(UnsupportedOperationException.class, () -> {
            PivotTopNSqlPlanner.plan(baseRelation, new PivotRequest(), List.of(), List.of(), List.of(), null);
        });
    }
}
