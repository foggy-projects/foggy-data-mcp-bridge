package com.foggyframework.dataset.mcp.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("可选 Odoo fixture 应支持多文件加载")
    void testCaseLoader_shouldLoadOptionalOdooFixtures() {
        List<EcommerceTestCase> testCases = new TestCaseLoader().loadMultiple(
                "ai-test-cases/ecommerce-tests.json",
                "ai-test-cases/odoo-accounting-tests.json",
                "ai-test-cases/odoo-sales-purchase-tests.json",
                "ai-test-cases/odoo-stock-mrp-tests.json"
        );

        assertTrue(testCases.stream().anyMatch(testCase -> "ROUTE-ORDER-001".equals(testCase.getId())));
        assertTrue(testCases.stream().anyMatch(testCase -> "ODOO-ACC-001".equals(testCase.getId())));
        assertTrue(testCases.stream().anyMatch(testCase -> "ODOO-SALE-001".equals(testCase.getId())));
        assertTrue(testCases.stream().anyMatch(testCase -> "ODOO-STOCK-001".equals(testCase.getId())));
    }

    @Test
    @DisplayName("Odoo 会计 fixture 应构造领域字段族过滤")
    @SuppressWarnings("unchecked")
    void buildToolArguments_shouldHonorOdooAccountingFixturePayload() {
        EcommerceTestCase testCase = new TestCaseLoader()
                .loadById("ai-test-cases/odoo-accounting-tests.json", "ODOO-ACC-001");

        Map<String, Object> args = executor.buildToolArguments(testCase);
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");
        List<Map<String, Object>> slice = (List<Map<String, Object>>) payload.get("slice");

        assertEquals("OdooAccountMoveQueryModel", args.get("model"));
        assertEquals(List.of("name", "partner$caption", "state", "paymentState", "amountResidual"),
                payload.get("columns"));
        assertFalse(slice.isEmpty());
        assertTrue(slice.contains(Map.of("field", "moveType", "op", "=", "value", "out_invoice")));
        assertTrue(slice.contains(Map.of("field", "state", "op", "=", "value", "posted")));
        assertTrue(slice.contains(Map.of(
                "field", "paymentState",
                "op", "in",
                "value", List.of("not_paid", "partial", "in_payment")
        )));
    }

    @Test
    @DisplayName("Odoo 销售 fixture 应区分订单生命周期和开票状态")
    @SuppressWarnings("unchecked")
    void buildToolArguments_shouldHonorOdooSaleLifecycleFixturePayload() {
        EcommerceTestCase testCase = new TestCaseLoader()
                .loadById("ai-test-cases/odoo-sales-purchase-tests.json", "ODOO-SALE-001");

        Map<String, Object> args = executor.buildToolArguments(testCase);
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");
        List<Map<String, Object>> slice = (List<Map<String, Object>>) payload.get("slice");

        assertEquals("OdooSaleOrderQueryModel", args.get("model"));
        assertEquals(List.of("name", "partner$caption", "state", "invoiceStatus", "amountTotal"),
                payload.get("columns"));
        assertTrue(slice.contains(Map.of(
                "field", "state",
                "op", "in",
                "value", List.of("sale", "done")
        )));
    }

    @Test
    @DisplayName("Odoo 库存 fixture 应区分调拨生命周期和完成日期")
    @SuppressWarnings("unchecked")
    void buildToolArguments_shouldHonorOdooStockLifecycleFixturePayload() {
        EcommerceTestCase testCase = new TestCaseLoader()
                .loadById("ai-test-cases/odoo-stock-mrp-tests.json", "ODOO-STOCK-001");

        Map<String, Object> args = executor.buildToolArguments(testCase);
        Map<String, Object> payload = (Map<String, Object>) args.get("payload");
        List<Map<String, Object>> slice = (List<Map<String, Object>>) payload.get("slice");

        assertEquals("OdooStockPickingQueryModel", args.get("model"));
        assertEquals(List.of("name", "pickingType$caption", "pickingType$code", "state", "scheduledDate", "dateDone"),
                payload.get("columns"));
        assertTrue(slice.contains(Map.of("field", "state", "op", "=", "value", "done")));
    }
}
