package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Row formulas retain model-owned default aggregation without aggregating their inputs. @since 9.5.5 */
class RowFormulaAggregationTest extends EcommerceTestSupport {
    @Resource private AdvancedQueryFacade queryFacade;

    @Test
    void detailKeepsRowValuesNullAndKnownZero() {
        var result = execute(List.of("id", "weight"), null, true);
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        assertEquals(4, rows.size());
        var byId = rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> ((Number) row.get("id")).intValue(), row -> row));
        assertEquals(6, ((Number) byId.get(1).get("weight")).doubleValue(), 1e-9);
        assertEquals(5, ((Number) byId.get(2).get("weight")).doubleValue(), 1e-9);
        assertEquals(0, ((Number) byId.get(3).get("weight")).doubleValue(), 1e-9);
        assertNull(byId.get(4).get("weight"));
        var total = (Map<?, ?>) result.getPagingResult().getTotalData();
        assertEquals(11, ((Number) total.get("weight")).doubleValue(), 1e-9);
        assertEquals(4, result.getPagingResult().getTotal());
    }

    @Test
    void defaultGroupedSumAndDerivedFormulaUseRowMeasurements() {
        var result = execute(List.of("warehouse", "weight", "doubleWeight"), "warehouse", true);
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) result.getPagingResult().getItems();
        var first = rows.stream().filter(row -> "A".equals(row.get("warehouse"))).findFirst().orElseThrow();
        assertEquals(11, ((Number) first.get("weight")).doubleValue(), 1e-9);
        assertEquals(22, ((Number) first.get("doubleWeight")).doubleValue(), 1e-9);
        var total = (Map<?, ?>) result.getPagingResult().getTotalData();
        assertEquals(11, ((Number) total.get("weight")).doubleValue(), 1e-9);
    }

    @Test
    void explicitSumCountAndAverageCanOverrideTheDefaultWithoutNestedAggregation() {
        var result = execute(List.of("SUM(weight) AS summed", "COUNT(weight) AS known",
                "AVG(weight) AS average"), null, false);
        var row = (Map<?, ?>) result.getPagingResult().getItems().get(0);
        assertEquals(11, ((Number) row.get("summed")).doubleValue(), 1e-9);
        assertEquals(3, ((Number) row.get("known")).intValue());
        assertEquals(11.0 / 3, ((Number) row.get("average")).doubleValue(), 1e-9);
    }

    @Test
    void incompatibleAggregateDefaultFailsInsteadOfCoalescingIndividualNullRows() {
        var error = assertThrows(RuntimeException.class,
                () -> getQueryModel("RowFormulaEmptyDefaultInvalidQuery"));
        assertTrue(error.getMessage().contains("emptyDefault/window"), error.getMessage());
    }

    @Test
    void selectingOnlyTheRowMeasureStillReturnsIndividualRows() {
        var result = execute(List.of("weight"), null, false);
        assertEquals(4, result.getPagingResult().getItems().size());
    }

    @Test
    void defaultAggregationCannotBeSuppliedByRequestJson() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var field = mapper.readValue("{\"name\":\"x\",\"expression\":\"1\",\"defaultAggregation\":\"COUNT\"}",
                com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef.class);
        assertNull(field.getDefaultAggregation());
        field.setDefaultAggregation("SUM");
        assertFalse(mapper.writeValueAsString(field).contains("defaultAggregation"));
    }

    private com.foggyframework.dataset.model.engine.query.DbQueryResult execute(
            List<String> columns, String group, boolean total) {
        var request = new DbQueryRequestDef();
        request.setQueryModel("RowFormulaMeasurementQuery");
        request.setColumns(columns);
        request.setReturnTotal(total);
        if (group != null) {
            var definition = new GroupRequestDef();
            definition.setField(group);
            request.setGroupBy(List.of(definition));
        }
        var context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 20));
        return queryFacade.queryModelResult(context);
    }
}
