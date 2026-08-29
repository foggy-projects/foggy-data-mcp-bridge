package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HistoricalFullTruckWaybillQuery AVG totalData 直接回归")
class HistoricalFullTruckWaybillQueryRegressionTest extends EcommerceTestSupport {
    private static final BigDecimal EPSILON = new BigDecimal("0.000001");

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Test
    @DisplayName("年度样本量不均衡时 total AVG 来自完整事实范围而非 AVG(group AVG)")
    void groupedAverageTotalMatchesOriginalFactsAndIgnoresPaging() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("HistoricalFullTruckWaybillQuery");
        request.setColumns(List.of(
                "openingTime$year",
                "waybillCount",
                "receivableTransportAmount",
                "averageTransportAmountPerWaybill"));
        request.setGroupBy(List.of(group("openingTime$year")));
        request.setOrderBy(List.of(order("openingTime$year", "desc")));
        request.setReturnTotal(true);

        PagingRequest<DbQueryRequestDef> paging =
                new PagingRequest<>(1, 5, 0, 5, request);
        DbQueryResult queryResult = queryFacade.queryModelResult(paging);
        PagingResultImpl<?> result = queryResult.getPagingResult();

        assertEquals(2, result.getItems().size());
        assertGroup(row(result.getItems().get(0)), 2026,
                7189L, "65570288.70", "9120.91927945");
        assertGroup(row(result.getItems().get(1)), 2025,
                2714L, "22452062.24", "8272.68321297");

        Map<String, Object> totalData = totalData(result);
        assertEquals(9903L, ((Number) totalData.get("waybillCount")).longValue());
        assertDecimal("88022350.94", totalData.get("receivableTransportAmount"));
        assertDecimal("8888.45308896",
                totalData.get("averageTransportAmountPerWaybill"));
        assertTrue(new BigDecimal("8696.80124621")
                        .subtract(decimal(totalData.get("averageTransportAmountPerWaybill")))
                        .abs().compareTo(EPSILON) > 0,
                "total AVG must not equal the unweighted average of annual averages");

        JdbcModelQueryEngine engine =
                (JdbcModelQueryEngine) queryResult.getQueryEngine();
        String totalSql = engine.getAggSql().toUpperCase();
        assertFalse(totalSql.contains("ORDER BY"), totalSql);
        assertFalse(totalSql.contains("LIMIT"), totalSql);
        assertFalse(totalSql.contains("OFFSET"), totalSql);
    }

    private void assertGroup(Map<String, Object> row,
                             int year,
                             long count,
                             String amount,
                             String average) {
        assertEquals(year, ((Number) row.get("openingTime$year")).intValue());
        assertEquals(count, ((Number) row.get("waybillCount")).longValue());
        assertDecimal(amount, row.get("receivableTransportAmount"));
        assertDecimal(average, row.get("averageTransportAmountPerWaybill"));
    }

    private GroupRequestDef group(String field) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        return group;
    }

    private OrderRequestDef order(String field, String direction) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(direction);
        return order;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> row(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> totalData(PagingResultImpl<?> result) {
        assertNotNull(result.getTotalData());
        return (Map<String, Object>) result.getTotalData();
    }

    private void assertDecimal(String expected, Object actual) {
        BigDecimal expectedDecimal = new BigDecimal(expected);
        BigDecimal actualDecimal = decimal(actual);
        assertTrue(expectedDecimal.subtract(actualDecimal).abs().compareTo(EPSILON) <= 0,
                () -> "expected=" + expectedDecimal + ", actual=" + actualDecimal);
    }

    private BigDecimal decimal(Object value) {
        assertNotNull(value);
        return value instanceof BigDecimal decimal
                ? decimal : new BigDecimal(String.valueOf(value));
    }
}
