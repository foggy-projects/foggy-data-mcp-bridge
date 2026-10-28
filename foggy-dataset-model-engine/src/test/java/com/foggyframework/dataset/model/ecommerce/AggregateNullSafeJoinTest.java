package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.model.impl.model.AggregateRelationQueryObject;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Null-safe keys remain governed aggregate equi-join keys. @since 9.3.0 */
class AggregateNullSafeJoinTest extends EcommerceTestSupport {
    private static final String MODEL = "OrderSalesNullSafeAggregateQueryModel";
    @Resource private SystemBundlesContext bundles;
    @Resource private SqlFormulaService formulas;

    @Test
    void aggregateNullSafeKeyUsesResolvedPhysicalColumns() {
        var engine = query(List.of());
        String sql = normalize(engine.getSql());
        assertTrue(sql.contains("order_key = nullsafesales.saleskey or"), sql);
        assertTrue(sql.contains("order_key is null and nullsafesales.saleskey is null"), sql);
        assertFalse(sql.contains("<=>"), "SQL must remain portable across dialects");
    }

    @Test
    void typedEqualityFilterOnNullSafeKeyStillPushesToAggregateSource() {
        var engine = query(List.of(slice("=", 1L)));
        assertTrue(normalize(engine.getSql()).contains("agg_src.sales_key = ?"), engine.getSql());
        assertTrue(engine.getValues().stream().filter(value -> Long.valueOf(1L).equals(value)).count() >= 2,
                "outer and RHS filters must retain typed bound values");
    }

    @Test
    void typedInFilterOnNullSafeKeyStillPushesToAggregateSource() {
        var engine = query(List.of(slice("in", List.of(1L, 2L))));
        assertTrue(normalize(engine.getSql()).contains("agg_src.sales_key in (?, ?)"), engine.getSql());
        assertTrue(engine.getValues().containsAll(List.of(1L, 2L)));
    }

    @Test
    void memberFilterCorrelationPreservesNullSafeKey() {
        var column = getQueryModel(MODEL).findJdbcColumnForCond("paymentMethods", true, true);
        assertInstanceOf(AggregateRelationOutputColumn.class, column);
        var relation = (AggregateRelationQueryObject) column.getQueryObject();
        try {
            var member = relation.buildAggregateRelationMemberFilter((AggregateRelationOutputColumn) column,
                    "=", "CASH", outer -> "outer_row." + outer.getSqlColumnName()).orElseThrow();
            String sql = normalize(member.sql());
            assertTrue(sql.contains("(agg_src.sales_key = outer_row.order_key or "
                    + "(agg_src.sales_key is null and outer_row.order_key is null))"), sql);
            assertTrue(sql.contains("agg_src.order_id = outer_row.order_id"), sql);
            assertEquals(List.of("CASH"), member.values());
        } finally {
            relation.clearAggregateRelationPushdowns();
        }
    }

    private JdbcModelQueryEngine query(List<SliceRequestDef> slices) {
        var request = new DbQueryRequestDef();
        request.setQueryModel(MODEL);
        request.setColumns(List.of("orderKey", "orderId", "salesAmount"));
        request.setSlice(slices);
        var engine = new JdbcModelQueryEngine(getQueryModel(MODEL), formulas);
        engine.analysisQueryRequest(bundles, request);
        return engine;
    }

    private SliceRequestDef slice(String op, Object value) {
        var slice = new SliceRequestDef();
        slice.setField("orderKey"); slice.setOp(op); slice.setValue(value);
        return slice;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
