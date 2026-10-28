package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Request state belongs to each relation instance, not its equal viewSql value. @since 9.3.0 */
class AggregateRelationSiblingIdentityTest extends EcommerceTestSupport {
    private static final String MODEL = "OrderSalesSiblingAggregateQueryModel";
    @Resource private SystemBundlesContext bundles;
    @Resource private SqlFormulaService formulas;

    private JdbcModelQueryEngine query(List<String> columns, List<SliceRequestDef> slices) {
        var request = new DbQueryRequestDef();
        request.setQueryModel(MODEL);
        request.setColumns(columns);
        request.setSlice(slices);
        var engine = new JdbcModelQueryEngine(getQueryModel(MODEL), formulas);
        engine.analysisQueryRequest(bundles, request);
        return engine;
    }

    private SliceRequestDef slice(String orderId) {
        var slice = new SliceRequestDef(); slice.setField("orderId"); slice.setOp("="); slice.setValue(orderId);
        return slice;
    }
    private CondRequestDef condition(String orderId) {
        var condition = new CondRequestDef(); condition.setField("orderId"); condition.setOp("="); condition.setValue(orderId);
        return condition;
    }
    private List<Map<String,Object>> rows(JdbcModelQueryEngine engine) {
        return jdbcTemplate.queryForList(engine.getSql(), engine.getValues().toArray());
    }
    private String matched() {
        return jdbcTemplate.queryForObject("SELECT order_id FROM fact_sales WHERE order_status='COMPLETED' GROUP BY order_id HAVING SUM(sales_amount)>0 ORDER BY order_id LIMIT 1", String.class);
    }
    private String unmatched() {
        return jdbcTemplate.queryForObject("SELECT o.order_id FROM fact_order o WHERE NOT EXISTS(SELECT 1 FROM fact_sales s WHERE s.order_id=o.order_id AND s.order_status='COMPLETED') ORDER BY o.order_id LIMIT 1", String.class);
    }
    private int occurrences(String sql, String needle) {
        return (sql.length() - sql.replace(needle, "").length()) / needle.length();
    }

    @Test void safeJoinKeyPredicateMustReachAllThreeSiblingInstances() {
        var engine = query(List.of("orderId","aAmount","bAmount","cAmount"), List.of(slice(matched())));
        assertEquals(3, occurrences(engine.getSql(), "agg_src.order_id = ?"));
        var row = rows(engine).get(0);
        assertEquals(row.get("aAmount"), row.get("bAmount"));
        assertEquals(row.get("aAmount"), row.get("cAmount"));
    }

    @Test void projectionPruningMustApplyToEachIndependentSibling() {
        var engine = query(List.of("orderId","aAmount","bQuantity","cCount"), List.of(slice(matched())));
        assertEquals(1, occurrences(engine.getSql(), "sum(agg_src.sales_amount)"));
        assertEquals(1, occurrences(engine.getSql(), "sum(agg_src.quantity)"));
        assertEquals(1, occurrences(engine.getSql(), "count(*)"));
        assertEquals(1, rows(engine).size());
    }

    @Test void consecutiveRequestsMustNotLeakPushdownsOrPrunedOutputsBetweenSiblings() {
        var first = query(List.of("orderId","aAmount","bAmount","cAmount"), List.of(slice(matched())));
        assertEquals(1, rows(first).size());
        var second = query(List.of("orderId","aCount","bCount","cCount"), List.of(slice(unmatched())));
        assertEquals(3, occurrences(second.getSql(), "agg_src.order_id = ?"));
        assertFalse(second.getValues().contains(matched()));
        var empty = rows(second);
        assertEquals(1, empty.size());
        assertNull(empty.get(0).get("aCount"));
        assertNull(empty.get(0).get("bCount"));
        assertNull(empty.get(0).get("cCount"));
        var third = query(List.of("orderId","aQuantity","bQuantity","cQuantity"), List.of(slice(matched())));
        assertEquals(3, occurrences(third.getSql(), "sum(agg_src.quantity)"));
        assertEquals(1, rows(third).size());
    }

    @Test void disjunctiveKeysMustRemainOuterOnlyAndKeepUnmatchedLeftRow() {
        var engine = query(List.of("orderId","aAmount","bAmount","cAmount"),
                List.of(SliceRequestDef.or(List.of(condition(matched()), condition(unmatched())))));
        assertEquals(0, occurrences(engine.getSql(), "agg_src.order_id = ?"));
        var result = rows(engine);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row -> unmatched().equals(row.get("orderId"))
                && row.get("aAmount") == null && row.get("bAmount") == null && row.get("cAmount") == null));
    }
}
