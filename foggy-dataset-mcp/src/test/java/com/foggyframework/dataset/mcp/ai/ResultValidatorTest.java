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
}
