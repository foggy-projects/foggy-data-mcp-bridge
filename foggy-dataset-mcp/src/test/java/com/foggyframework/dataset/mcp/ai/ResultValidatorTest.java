package com.foggyframework.dataset.mcp.ai;

import com.foggyframework.dataset.mcp.service.ToolCallCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AI result validator")
class ResultValidatorTest {

    private final ResultValidator validator = new ResultValidator();

    @Test
    @DisplayName("query case should accept export_with_chart when it uses required query columns")
    void validateFromAiResponse_shouldAcceptChartExportAsQueryCompatibleTool() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("SORT-001")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .requiredColumns(List.of("product$caption", "salesAmount"))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.export_with_chart")
                .springToolName("dataset_export_with_chart")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("product$caption", "sum(salesAmount) as totalSalesAmount"),
                                "groupBy", List.of(Map.of("field", "product$caption"))
                        ),
                        "chart", Map.of(
                                "xAxis", Map.of("field", "product$caption"),
                                "yAxis", Map.of("field", "totalSalesAmount")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("product$caption", "商品1", "totalSalesAmount", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "销售额最高的商品是商品1，总销售额 1000 元。",
                List.of(call)
        );

        assertTrue(result.isPassed(), () -> result.getFailedRules().toString());
    }

    @Test
    @DisplayName("required column validation should still fail when neither result nor arguments reference it")
    void validateFromAiResponse_shouldRejectMissingRequiredColumn() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("AGG-001")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .requiredColumns(List.of("profitAmount"))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("product$caption", "sum(salesAmount) as totalSalesAmount"),
                                "groupBy", List.of("product$caption")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("product$caption", "商品1", "totalSalesAmount", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "按商品统计的总销售额。",
                List.of(call)
        );

        assertFalse(result.isPassed());
        assertTrue(result.getFailedRules().stream()
                .anyMatch(rule -> rule.contains("Required content not found: profitAmount")));
    }

    @Test
    @DisplayName("required column validation should not match another column by prefix")
    void validateFromAiResponse_shouldRejectPrefixOnlyColumnReference() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("AGG-002")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .requiredColumns(List.of("salesAmount"))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("sum(salesAmount2) as totalSalesAmount2")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("totalSalesAmount2", 2000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "按税额*2统计。",
                List.of(call)
        );

        assertFalse(result.isPassed());
        assertTrue(result.getFailedRules().stream()
                .anyMatch(rule -> rule.contains("Required content not found: salesAmount")));
    }

    @Test
    @DisplayName("tool argument validation should accept required detail-row slice predicate")
    void validateFromAiResponse_shouldAcceptExpectedToolArgumentPredicateScope() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("COMPLEX-001")
                .expectedTool("dataset.query_model")
                .expected(complexExpectedWithPredicateScopeRules())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesDate$year", "sum(salesAmount) as totalSales"),
                                "slice", List.of(
                                        Map.of("field", "salesDate$year", "op", "in", "value", List.of(2024, 2025)),
                                        Map.of("field", "salesAmount", "op", ">", "value", 500)
                                ),
                                "groupBy", List.of("store$caption", "salesDate$year")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("store$caption", "门店1", "salesDate$year", 2024, "totalSales", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "门店1 在 2024 年总销售额 1000。",
                List.of(call)
        );

        assertTrue(result.isPassed(), () -> result.getFailedRules().toString());
        assertTrue(result.getPassedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:slice:salesAmount")));
    }

    @Test
    @DisplayName("tool argument validation should reject detail predicate moved to having")
    void validateFromAiResponse_shouldRejectPredicateScopeMovedToHaving() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("COMPLEX-001")
                .expectedTool("dataset.query_model")
                .expected(complexExpectedWithPredicateScopeRules())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("store$caption", "salesDate$year", "sum(salesAmount) as totalSales"),
                                "slice", List.of(Map.of("field", "salesDate$year", "op", "in", "value", List.of(2024, 2025))),
                                "having", List.of(Map.of("field", "totalSales", "op", ">", "value", 500)),
                                "groupBy", List.of("store$caption", "salesDate$year")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("store$caption", "门店1", "salesDate$year", 2024, "totalSales", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "门店1 在 2024 年总销售额 1000。",
                List.of(call)
        );

        assertFalse(result.isPassed());
        assertTrue(result.getFailedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:slice:salesAmount")));
        assertTrue(result.getFailedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:having:totalSales")));
    }

    @Test
    @DisplayName("tool argument validation should accept groupBy string field")
    void validateFromAiResponse_shouldAcceptGroupByStringFieldRule() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("AGG-001")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .toolArgumentRules(List.of(
                                EcommerceTestCase.ToolArgumentRule.builder()
                                        .tool("dataset.query_model")
                                        .path("groupBy")
                                        .field("product$caption")
                                        .mustExist(true)
                                        .build()
                        ))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("product$caption", "sum(salesAmount) as totalSales"),
                                "groupBy", List.of("product$caption")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("product$caption", "商品1", "totalSales", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "商品1 总销售额 1000。",
                List.of(call)
        );

        assertTrue(result.isPassed(), () -> result.getFailedRules().toString());
        assertTrue(result.getPassedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:groupBy:product$caption")));
    }

    @Test
    @DisplayName("tool argument validation should accept normalized groupBy from query result")
    void validateFromAiResponse_shouldAcceptNormalizedGroupByFieldRule() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("SORT-001")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .toolArgumentRules(List.of(
                                EcommerceTestCase.ToolArgumentRule.builder()
                                        .tool("dataset.query_model")
                                        .path("groupBy")
                                        .field("product$caption")
                                        .mustExist(true)
                                        .build()
                        ))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of("product$caption", "sum(salesAmount) as totalSales"),
                                "orderBy", List.of("-totalSales"),
                                "limit", 5
                        )
                ))
                .result(Map.of("data", Map.of(
                        "items", List.of(Map.of("product$caption", "商品1", "totalSales", 1000)),
                        "debug", Map.of("normalized", Map.of(
                                "groupBy", List.of(
                                        Map.of("field", "product$caption"),
                                        Map.of("field", "totalSales", "agg", "SUM")
                                )
                        ))
                )))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "商品1 总销售额 1000。",
                List.of(call)
        );

        assertTrue(result.isPassed(), () -> result.getFailedRules().toString());
        assertTrue(result.getPassedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:groupBy:product$caption")));
    }

    @Test
    @DisplayName("tool argument validation should explain observed payload paths on failure")
    void validateFromAiResponse_shouldExplainToolArgumentFailureWithObservedPaths() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("FILTER-001")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .toolArgumentRules(List.of(
                                EcommerceTestCase.ToolArgumentRule.builder()
                                        .tool("dataset.query_model")
                                        .path("slice")
                                        .field("salesAmount")
                                        .operator(">")
                                        .mustExist(true)
                                        .build()
                        ))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "slice", List.of(Map.of("field", "salesDate$year", "op", "=", "value", 2025)),
                                "having", List.of(Map.of("field", "salesAmount", "op", ">", "value", 1000))
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("salesDate$year", 2025, "salesAmount", 1200)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "2025 年销售金额超过 1000。",
                List.of(call)
        );

        assertFalse(result.isPassed());
        assertTrue(result.getFailedRules().stream()
                .anyMatch(rule -> rule.contains("observed payload paths")
                        && rule.contains("slice=[salesDate$year =]")
                        && rule.contains("having=[salesAmount >]")));
    }

    @Test
    @DisplayName("tool argument validation should accept column expression references")
    void validateFromAiResponse_shouldAcceptColumnExpressionReferenceRule() {
        EcommerceTestCase testCase = EcommerceTestCase.builder()
                .id("AGG-002")
                .expectedTool("dataset.query_model")
                .expected(EcommerceTestCase.ExpectedResult.builder()
                        .toolArgumentRules(List.of(
                                EcommerceTestCase.ToolArgumentRule.builder()
                                        .tool("dataset.query_model")
                                        .path("columns")
                                        .field("quantity")
                                        .mustExist(true)
                                        .build(),
                                EcommerceTestCase.ToolArgumentRule.builder()
                                        .tool("dataset.query_model")
                                        .path("columns")
                                        .field("salesAmount")
                                        .mustExist(true)
                                        .build()
                        ))
                        .build())
                .build();

        ToolCallCollector.ToolCallRecord call = ToolCallCollector.ToolCallRecord.builder()
                .toolName("dataset.query_model")
                .springToolName("dataset_query_model")
                .arguments(Map.of(
                        "model", "FactSalesQueryModel",
                        "payload", Map.of(
                                "columns", List.of(
                                        "store$caption",
                                        "sum(quantity) as totalQuantity",
                                        "sum(salesAmount) as totalSales"
                                ),
                                "groupBy", List.of("store$caption")
                        )
                ))
                .result(Map.of("data", Map.of("items", List.of(
                        Map.of("store$caption", "门店1", "totalQuantity", 12, "totalSales", 1000)
                ))))
                .success(true)
                .durationMs(10)
                .timestamp(Instant.now())
                .sequence(0)
                .build();

        ResultValidator.ValidationResult result = validator.validateFromAiResponse(
                testCase,
                "门店1 销售数量 12，总销售额 1000。",
                List.of(call)
        );

        assertTrue(result.isPassed(), () -> result.getFailedRules().toString());
        assertTrue(result.getPassedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:columns:quantity")));
        assertTrue(result.getPassedRules().stream()
                .anyMatch(rule -> rule.contains("TOOL_ARGUMENT:columns:salesAmount")));
    }

    private EcommerceTestCase.ExpectedResult complexExpectedWithPredicateScopeRules() {
        return EcommerceTestCase.ExpectedResult.builder()
                .requiredColumns(List.of("store$caption", "salesAmount"))
                .toolArgumentRules(List.of(
                        EcommerceTestCase.ToolArgumentRule.builder()
                                .tool("dataset.query_model")
                                .path("slice")
                                .field("salesAmount")
                                .operator(">")
                                .mustExist(true)
                                .build(),
                        EcommerceTestCase.ToolArgumentRule.builder()
                                .tool("dataset.query_model")
                                .path("slice")
                                .field("salesDate$year")
                                .mustExist(true)
                                .build(),
                        EcommerceTestCase.ToolArgumentRule.builder()
                                .tool("dataset.query_model")
                                .path("groupBy")
                                .field("store$caption")
                                .mustExist(true)
                                .build(),
                        EcommerceTestCase.ToolArgumentRule.builder()
                                .tool("dataset.query_model")
                                .path("groupBy")
                                .field("salesDate$year")
                                .mustExist(true)
                                .build(),
                        EcommerceTestCase.ToolArgumentRule.builder()
                                .tool("dataset.query_model")
                                .path("having")
                                .field("totalSales")
                                .operator(">")
                                .mustExist(false)
                                .build()
                ))
                .build();
    }
}
