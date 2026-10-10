package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.DbAggregation;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real SQL regression for relation counts, separate from raw parent COUNT. */
class AggregateCountRollupRegressionTest extends EcommerceTestSupport {
    @Resource private AdvancedQueryFacade queryFacade;

    @BeforeEach
    void fixture() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS aggregate_count_parent (id INTEGER PRIMARY KEY, tenant_id INTEGER, order_id INTEGER, source VARCHAR(16))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS aggregate_count_child (id INTEGER PRIMARY KEY, tenant_id INTEGER, order_id INTEGER, valid INTEGER, label VARCHAR(16), amount DECIMAL(10,2))");
        jdbcTemplate.update("DELETE FROM aggregate_count_child");
        jdbcTemplate.update("DELETE FROM aggregate_count_parent");
        jdbcTemplate.update("INSERT INTO aggregate_count_parent VALUES (1,1,1,'A'),(2,1,2,'A'),(3,1,3,'A'),(4,1,4,'A'),(5,2,1,'B')");
        jdbcTemplate.update("INSERT INTO aggregate_count_child VALUES (1,1,1,1,'x',10),(2,1,1,1,NULL,20),(3,1,2,1,'x',10),(4,1,2,1,'y',20),(5,1,3,1,'x',10),(6,1,3,1,'y',20),(7,1,1,0,'invalid',90),(8,2,1,1,'foreign',90)");
    }

    @Test
    void countOutputMetadataShouldUseSumAndRetainInnerCount() {
        var model = getQueryModel("AggregateCountRollupQuery");
        var count = model.findJdbcColumnForCond("childCount", true, true);
        assertEquals(DbAggregation.SUM, count.getAggregation());
        assertEquals("count(*)", ((AggregateRelationOutputColumn) count).getAggregateRelationAggregateExpression());
        assertEquals(DbAggregation.COUNT, model.findJdbcColumnForCond("parentCount", true, true).getAggregation());
        assertEquals(DbAggregation.COUNT_DISTINCT, model.findJdbcColumnForCond("distinctLabels", true, true).getAggregation());
        assertEquals(DbAggregation.AVG, model.findJdbcColumnForCond("averageAmount", true, true).getAggregation());
    }

    @Test
    void detailTotalsShouldSumCountsInBothSqlPaths() {
        for (boolean optimize : List.of(true, false)) {
            var result = query(request(optimize, "orderId", "childCount", "labelCount", "parentCount"), 0, 100);
            assertEquals(4, result.getPagingResult().getTotal());
            assertCounts(total(result), 6, 5, 4);
            var rows = rows(result);
            for (int i = 0; i < 3; i++) assertEquals(2, number(rows.get(i).get("childCount")));
            assertNull(rows.get(3).get("childCount"));
        }
    }

    @Test
    void filterAndPaginationShouldKeepFullScopeTotal() {
        for (boolean optimize : List.of(true, false)) {
            var request = request(optimize, "orderId", "childCount", "labelCount", "parentCount");
            request.setSlice(List.of(slice("tenantId", 1), slice("orderId", 1)));
            var result = query(request, 0, 100);
            assertCounts(total(result), 2, 1, 1);
            request = request(optimize, "orderId", "childCount", "labelCount", "parentCount");
            result = query(request, 1, 2);
            assertEquals(2, rows(result).size());
            assertEquals(4, result.getPagingResult().getTotal());
            assertCounts(total(result), 6, 5, 4);
        }
    }

    @Test
    void defaultGroupsShouldSumRelationCountsAndCountParentRows() {
        for (boolean optimize : List.of(true, false)) {
            for (String group : List.of("orderId", "source")) {
                var request = request(optimize, group, "childCount", "labelCount", "parentCount");
                GroupRequestDef grouping = new GroupRequestDef();
                grouping.setField(group);
                request.setGroupBy(List.of(grouping));
                request.setOrderBy(List.of());
                var result = query(request, 0, 100);
                if (group.equals("source")) {
                    assertEquals(1, rows(result).size());
                    assertCounts(rows(result).get(0), 6, 5, 4);
                } else {
                    assertEquals(4, rows(result).size());
                    assertEquals(6, rows(result).stream().mapToLong(r -> r.get("childCount") == null ? 0 : number(r.get("childCount"))).sum());
                }
                assertCounts(total(result), 6, 5, 4);
                assertEquals(group.equals("source") ? 1 : 4, result.getPagingResult().getTotal());
            }
        }
    }

    @Test
    void unmatchedAndEmptyScopesShouldPreserveSqlNullSemantics() {
        for (boolean optimize : List.of(true, false)) {
            var request = request(optimize, "orderId", "childCount", "parentCount");
            request.setSlice(List.of(slice("tenantId", 1), slice("orderId", 4)));
            var result = query(request, 0, 100);
            assertNull(rows(result).get(0).get("childCount"));
            assertNull(total(result).get("childCount"));
            assertEquals(1, number(total(result).get("parentCount")));
            request.setSlice(List.of(slice("tenantId", 1), slice("orderId", 999)));
            result = query(request, 0, 100);
            assertTrue(rows(result).isEmpty());
            assertEquals(0, result.getPagingResult().getTotal());
            assertNull(total(result).get("childCount"));
            assertEquals(0, number(total(result).get("parentCount")));
        }
    }

    @Test
    void explicitSumExpressionShouldKeepRowsAndExposeKnownTotalLimitation() {
        var request = request(true, "source", "sum(childCount) as childSum");
        GroupRequestDef group = new GroupRequestDef();
        group.setField("source");
        request.setGroupBy(List.of(group));
        request.setOrderBy(List.of());
        var result = query(request, 0, 100);
        assertEquals(6, number(rows(result).get(0).get("childSum")));
        // Separate expression-total defect; the default relation rollup fix
        // must not be described as a fix for calculated aggregate aliases.
        assertNull(total(result).get("childSum"));
    }

    private DbQueryRequestDef request(boolean optimize, String... columns) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("AggregateCountRollupQuery");
        request.setColumns(List.of(columns));
        request.setReturnTotal(true);
        request.setOptimizeAggSql(optimize);
        request.setSlice(List.of(slice("tenantId", 1)));
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId"); order.setDir("asc");
        request.setOrderBy(List.of(order));
        return request;
    }

    private DbQueryResult query(DbQueryRequestDef request, int start, int limit) {
        ModelResultContext context = new ModelResultContext();
        PagingRequest<DbQueryRequestDef> paging = PagingRequest.buildPagingRequest(request, limit);
        paging.setStart(start);
        context.setRequest(paging);
        return queryFacade.queryModelResult(context);
    }

    private SliceRequestDef slice(String field, Object value) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(field); slice.setOp("="); slice.setValue(value);
        return slice;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(DbQueryResult result) {
        return (List<Map<String, Object>>) result.getPagingResult().getItems();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> total(DbQueryResult result) {
        return (Map<String, Object>) result.getPagingResult().getTotalData();
    }

    private long number(Object value) { return ((Number) value).longValue(); }

    private void assertCounts(Map<String, Object> row, long child, long labels, long parents) {
        assertEquals(child, number(row.get("childCount")));
        assertEquals(labels, number(row.get("labelCount")));
        assertEquals(parents, number(row.get("parentCount")));
    }
}
