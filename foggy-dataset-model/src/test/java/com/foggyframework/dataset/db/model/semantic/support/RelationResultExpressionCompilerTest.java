package com.foggyframework.dataset.db.model.semantic.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationResultExpressionCompilerTest {

    @Test
    @DisplayName("compiles signed ratio and difference formulas")
    void compilesRatioAndDifference() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(
                        derived("profitRate", "profitAmount / NULLIF(salesAmount, 0)"),
                        derived("nonProfitAmount", "salesAmount - profitAmount")
                ),
                List.of("salesAmount", "profitAmount"),
                List.of("salesAmount", "profitAmount"),
                List.of(),
                unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals(List.of("profitRate", "nonProfitAmount"), result.aliases());
        assertEquals("salesAmount", result.ratios().get(0).denominatorAlias());
        assertEquals("\"salesAmount\" - \"profitAmount\"", result.arithmetic().get(0).sqlExpression());
    }

    @Test
    @DisplayName("compiles absolute delta ratio with governed denominator")
    void compilesAbsoluteDeltaRatio() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("absoluteDeviationRate",
                        "ABS(actualAmount - targetAmount) / NULLIF(targetAmount, 0)")),
                List.of("actualAmount", "targetAmount"),
                List.of("actualAmount", "targetAmount"),
                List.of(),
                unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals("relation_metric_absolute_delta_ratio", result.arithmetic().get(0).kind());
        assertEquals("(1.0 * ABS(\"actualAmount\" - \"targetAmount\") / NULLIF(\"targetAmount\", 0))",
                result.arithmetic().get(0).sqlExpression());
    }

    @Test
    @DisplayName("compiles ordered bucket only on visible formula aliases")
    void compilesOrderedBucketOnVisibleAlias() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("profitBand",
                        "CASE WHEN profitRate < 0.1 THEN 'very_low' WHEN profitRate < 0.2 THEN 'low' ELSE 'normal' END")),
                List.of("salesAmount", "profitAmount"),
                List.of("salesAmount", "profitAmount", "profitRate"),
                List.of("profitRate"),
                unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals(List.of("profitBand"), result.labelAliases());
        assertEquals("relation_metric_ordered_bucket", result.arithmetic().get(0).kind());
    }

    @Test
    @DisplayName("rejects formulas outside visible aggregate and derived aliases")
    void rejectsInvisibleAliases() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("badRate", "physicalAmount / salesAmount")),
                List.of("salesAmount"),
                List.of("salesAmount"),
                List.of(),
                unsupported);

        assertTrue(result.empty());
        assertFalse(unsupported.isEmpty());
        assertTrue(unsupported.stream().anyMatch(message ->
                message.contains("must reference aggregate metric aliases")));
    }

    @Test
    @DisplayName("rejects delta ratio denominator that is not one operand")
    void rejectsThirdMetricDeltaDenominator() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("badDeviationRate", "(actualAmount - targetAmount) / NULLIF(budgetAmount, 0)")),
                List.of("actualAmount", "targetAmount", "budgetAmount"),
                List.of("actualAmount", "targetAmount", "budgetAmount"),
                List.of(),
                unsupported);

        assertTrue(result.empty());
        assertTrue(unsupported.stream().anyMatch(message ->
                message.contains("denominator must match one difference operand")));
    }

    private static Map<String, Object> derived(String name, String expr) {
        return Map.of("name", name, "expr", expr);
    }
}
