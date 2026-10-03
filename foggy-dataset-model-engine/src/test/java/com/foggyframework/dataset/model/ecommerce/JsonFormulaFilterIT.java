package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JSON 公式字段筛选集成测试")
class JsonFormulaFilterIT extends EcommerceTestSupport {

    private static final String QUERY_MODEL = "FactJsonFormulaFilterQueryModel";
    private static final String FIXTURE_TABLE = "json_formula_filter_fixture";

    @Resource
    private AdvancedQueryFacade queryFacade;

    @Test
    @DisplayName("未选入结果列的 JSON 映射公式属性仍可用于 slice")
    void jsonMappedFormulaPropertyCanFilterWhenNotSelected() {
        resetJsonFormulaFixture();

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(QUERY_MODEL);
        request.setColumns(List.of("id"));
        request.setSlice(List.of(new SliceRequestDef("mappedCityCode", "=", "HZ-01")));
        OrderRequestDef order = new OrderRequestDef();
        order.setField("id");
        order.setDir("asc");
        request.setOrderBy(List.of(order));

        ModelResultContext context = new ModelResultContext();
        context.setRequest(PagingRequest.buildPagingRequest(request, 20));
        DbQueryResult dbResult = queryFacade.queryModelResult(context);
        PagingResultImpl result = dbResult.getPagingResult();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getItems();
        assertEquals(List.of("MATCH-1", "MATCH-2"),
                rows.stream().map(row -> String.valueOf(row.get("id"))).toList());
    }

    private void resetJsonFormulaFixture() {
        String payloadType = switch (getDialectKey()) {
            case "sqlserver" -> "NVARCHAR(MAX)";
            default -> "TEXT";
        };
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + FIXTURE_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + FIXTURE_TABLE + " ("
                + "id VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "json_payload " + payloadType + " NOT NULL)");
        jdbcTemplate.update("INSERT INTO " + FIXTURE_TABLE + " (id, json_payload) VALUES (?, ?)",
                "MATCH-1", "{\"cityCode\":\"HZ-01\"}");
        jdbcTemplate.update("INSERT INTO " + FIXTURE_TABLE + " (id, json_payload) VALUES (?, ?)",
                "SKIP-1", "{\"cityCode\":\"NB-02\"}");
        jdbcTemplate.update("INSERT INTO " + FIXTURE_TABLE + " (id, json_payload) VALUES (?, ?)",
                "MATCH-2", "{\"cityCode\":\"HZ-01\"}");
    }
}
