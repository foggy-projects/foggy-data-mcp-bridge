package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Default row-formula measures aggregate in HAVING while retaining row-level slices. @since 9.5.5 */
class RowFormulaHavingTest extends EcommerceTestSupport {
    @Resource private AdvancedQueryFacade queryFacade;

    @Test
    void havingComparesTheAggregatedWholeRowFormula() {
        var request = groupedRequest(List.of("warehouse", "weight"));
        request.setHaving(List.of(new SliceRequestDef("weight", ">", 10)));

        var result = execute(request);

        assertEquals(1, rows(result).size());
        assertEquals("A", rows(result).get(0).get("warehouse"));
        assertEquals(11, ((Number) rows(result).get(0).get("weight")).doubleValue(), 1e-9);
        assertTrue(sql(result).contains("having"));
    }

    @Test
    void havingCanReferenceADefaultMeasureOutsideTheProjection() {
        var request = groupedRequest(List.of("warehouse"));
        request.setHaving(List.of(new SliceRequestDef("weight", ">", 10)));

        var result = execute(request);

        assertEquals(List.of("A"), rows(result).stream().map(row -> row.get("warehouse")).toList());
    }

    @Test
    void sliceStillFiltersIndividualRowMeasurements() {
        var request = request(List.of("id", "weight"));
        request.setSlice(List.of(new SliceRequestDef("weight", ">", 5)));

        var result = execute(request);

        assertEquals(1, rows(result).size());
        assertEquals(1, ((Number) rows(result).get(0).get("id")).intValue());
        assertEquals(6, ((Number) rows(result).get(0).get("weight")).doubleValue(), 1e-9);
        assertFalse(sql(result).contains("having"));
    }

    @Test
    void groupedOrderByUsesTheSelectedDefaultMeasure() {
        var request = groupedRequest(List.of("warehouse", "weight"));
        var order = new OrderRequestDef();
        order.setField("weight");
        order.setDir("asc");
        request.setOrderBy(List.of(order));

        var result = execute(request);

        assertEquals(List.of("B", "A"), rows(result).stream().map(row -> row.get("warehouse")).toList());
        assertEquals(0, ((Number) rows(result).get(0).get("weight")).doubleValue(), 1e-9);
        assertEquals(11, ((Number) rows(result).get(1).get("weight")).doubleValue(), 1e-9);
    }

    @Test
    void explicitAggregateAliasIsNotAggregatedAgainInHaving() {
        var request = groupedRequest(List.of("warehouse", "SUM(weight) AS summed"));
        request.setHaving(List.of(new SliceRequestDef("summed", ">", 10)));

        var result = execute(request);

        assertEquals(1, rows(result).size());
        assertEquals("A", rows(result).get(0).get("warehouse"));
        assertEquals(11, ((Number) rows(result).get(0).get("summed")).doubleValue(), 1e-9);
        assertFalse(sql(result).replaceAll("\\s+", "").contains("sum(sum("));
    }

    @Test
    void anExistingDerivedAggregateFormulaRetainsItsHavingExpression() {
        var request = groupedRequest(List.of("warehouse", "doubleWeight"));
        request.setHaving(List.of(new SliceRequestDef("doubleWeight", ">", 20)));

        var result = execute(request);

        assertEquals(1, rows(result).size());
        assertEquals("A", rows(result).get(0).get("warehouse"));
        assertEquals(22, ((Number) rows(result).get(0).get("doubleWeight")).doubleValue(), 1e-9);
    }

    private DbQueryRequestDef request(List<String> columns) {
        var request = new DbQueryRequestDef();
        request.setQueryModel("RowFormulaMeasurementQuery");
        request.setColumns(columns);
        request.setReturnTotal(false);
        return request;
    }

    private DbQueryRequestDef groupedRequest(List<String> columns) {
        var request = request(columns);
        var group = new GroupRequestDef();
        group.setField("warehouse");
        request.setGroupBy(List.of(group));
        return request;
    }

    private DbQueryResult execute(DbQueryRequestDef request) {
        var context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 20));
        return queryFacade.queryModelResult(context);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(DbQueryResult result) {
        return (List<Map<String, Object>>) result.getPagingResult().getItems();
    }

    private String sql(DbQueryResult result) {
        return ((JdbcModelQueryEngine) result.getQueryEngine()).getSql().toLowerCase(java.util.Locale.ROOT);
    }
}
