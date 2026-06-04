package com.foggyframework.dataset.mcp.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Spring AI test executor")
class SpringAiTestExecutorTest {

    private final SpringAiTestExecutor executor =
            new SpringAiTestExecutor(null, null, "", null, "trace", null);

    @Test
    @DisplayName("直接 describe_model 应请求结构化 JSON")
    void buildToolArguments_shouldUseJsonForDirectDescribe() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .expectedTool("dataset.describe_model_internal")
                .targetModel("FactSalesQueryModel")
                .build();

        Map<String, Object> args = executor.buildToolArguments(testCase);

        assertEquals("FactSalesQueryModel", args.get("model"));
        assertEquals("json", args.get("format"));
    }

    @Test
    @DisplayName("直接 query_model 应继承期望 limit 和 orderBy")
    @SuppressWarnings("unchecked")
    void buildToolArguments_shouldHonorExpectedLimitAndOrder() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("SORT-001")
                .expectedTool("dataset.query_model")
                .targetModel("FactSalesQueryModel")
                .directToolArguments(Map.of("payload", Map.of("groupBy", List.of("product$caption"))))
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .requiredColumns(List.of("product$caption", "salesAmount"))
                        .maxRows(5)
                        .rules(List.of(EcommerceTestCase.ValidationRule.builder()
                                .type(EcommerceTestCase.RuleType.ORDER_BY)
                                .column("salesAmount")
                                .params(Map.of("direction", "DESC"))
                                .build()))
                        .build())
                .build();

        Map<String, Object> args = executor.buildToolArguments(testCase);
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");

        assertEquals(5, payload.get("limit"));
        assertEquals(List.of("product$caption"), payload.get("groupBy"));
        assertEquals(List.of(Map.of("column", "salesAmount", "direction", "DESC")), payload.get("orderBy"));
        assertEquals(List.of("product$caption", "salesAmount"), payload.get("columns"));
    }

    @Test
    @DisplayName("复杂 fixture 应补齐分组、排序和过滤")
    @SuppressWarnings("unchecked")
    void buildToolArguments_shouldAddKnownFixturePayloadForComplexCase() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("COMPLEX-001")
                .expectedTool("dataset.query_model")
                .targetModel("FactSalesQueryModel")
                .directToolArguments(Map.of("payload", Map.of(
                        "columns", List.of("store$caption", "salesAmount", "salesDate$year"),
                        "groupBy", List.of("store$caption", "salesDate$year"),
                        "orderBy", List.of(Map.of("column", "salesAmount", "direction", "DESC")),
                        "slice", List.of(
                                Map.of("field", "salesAmount", "op", ">", "value", 500),
                                Map.of("$or", List.of(
                                        Map.of("field", "salesDate$year", "op", "=", "value", 2024),
                                        Map.of("field", "salesDate$year", "op", "=", "value", 2025)
                                ))
                        )
                )))
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .requiredColumns(List.of("store$caption", "salesAmount"))
                        .maxRows(3)
                        .build())
                .build();

        Map<String, Object> args = executor.buildToolArguments(testCase);
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");
        List<String> columns = (List<String>) payload.get("columns");

        assertEquals(3, payload.get("limit"));
        assertEquals(List.of("store$caption", "salesDate$year"), payload.get("groupBy"));
        assertEquals(List.of(Map.of("column", "salesAmount", "direction", "DESC")), payload.get("orderBy"));
        assertTrue(columns.contains("salesDate$year"));
        assertTrue(payload.containsKey("slice"));
    }

    @Test
    @DisplayName("应从 JSON fixture 加载 direct 工具参数")
    void testCaseLoader_shouldLoadDirectToolArguments() {
        EcommerceTestCase testCase = new TestCaseLoader()
                .loadById("ai-test-cases/ecommerce-tests.json", "FILTER-001");

        assertTrue(testCase.getDirectToolArguments().containsKey("payload"));
    }
}
