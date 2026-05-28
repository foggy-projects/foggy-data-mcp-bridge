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
    @DisplayName("auto-layers same-stage aliases without expanding expression subset")
    void autoLayersSameStageAliases() {
        List<String> unsupported = new ArrayList<>();

        List<DslCteDslRequestMapper.ResultStageDerivedMetrics> layers =
                RelationResultExpressionCompiler.compileLayered(
                        List.of(
                                derived("profitRate", "profitAmount / salesAmount"),
                                derived("profitBand", "CASE WHEN profitRate < 0.2 THEN 'low' ELSE 'normal' END")
                        ),
                        List.of("salesAmount", "profitAmount"),
                        List.of("salesAmount", "profitAmount"),
                        List.of(),
                        unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals(2, layers.size());
        assertEquals(List.of("profitRate"), layers.get(0).aliases());
        assertEquals(List.of("profitBand"), layers.get(1).labelAliases());
    }

    @Test
    @DisplayName("rejects cyclic same-stage alias DAG")
    void rejectsCyclicSameStageAliasDag() {
        List<String> unsupported = new ArrayList<>();

        List<DslCteDslRequestMapper.ResultStageDerivedMetrics> layers =
                RelationResultExpressionCompiler.compileLayered(
                        List.of(
                                derived("profitBand", "CASE WHEN riskBand < 1 THEN 'low' ELSE 'normal' END"),
                                derived("riskBand", "CASE WHEN profitBand < 1 THEN 'low' ELSE 'normal' END")
                        ),
                        List.of("salesAmount", "profitAmount"),
                        List.of("salesAmount", "profitAmount"),
                        List.of(),
                        unsupported);

        assertEquals(1, layers.size());
        assertTrue(layers.get(0).empty());
        assertTrue(unsupported.stream().anyMatch(message ->
                message.contains("same-stage alias DAG must be acyclic")));
    }

    @Test
    @DisplayName("compiles signed ratio and difference formulas")
    void compilesRatioAndDifference() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(
                        derived("profitRate", "profitAmount / salesAmount"),
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
    @DisplayName("normalizes explicit NULLIF denominator guard for ratio formulas")
    void normalizesExplicitNullifRatioGuard() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("profitRate", "profitAmount / NULLIF(salesAmount, 0)")),
                List.of("salesAmount", "profitAmount"),
                List.of("salesAmount", "profitAmount"),
                List.of(),
                unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals("profitAmount", result.ratios().get(0).numeratorAlias());
        assertEquals("salesAmount", result.ratios().get(0).denominatorAlias());
    }

    @Test
    @DisplayName("compiles absolute delta ratio with governed denominator")
    void compilesAbsoluteDeltaRatio() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("absoluteDeviationRate",
                        "ABS(actualAmount - targetAmount) / targetAmount")),
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
    @DisplayName("normalizes explicit NULLIF denominator guard for delta ratios")
    void normalizesExplicitNullifDeltaRatioGuard() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("deviationRate", "(actualAmount - targetAmount) / NULLIF(targetAmount, 0)")),
                List.of("actualAmount", "targetAmount"),
                List.of("actualAmount", "targetAmount"),
                List.of(),
                unsupported);

        assertTrue(unsupported.isEmpty());
        assertEquals("relation_metric_delta_ratio", result.arithmetic().get(0).kind());
        assertEquals("(1.0 * (\"actualAmount\" - \"targetAmount\") / NULLIF(\"targetAmount\", 0))",
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
        assertEquals("profitRate", result.arithmetic().get(0).descriptor().get("source_alias"));
        assertEquals("label_alias_equality_only", result.arithmetic().get(0).descriptor().get("postSlice_policy"));
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

    @Test
    @DisplayName("rejects arithmetic subset functions outside governed allowlist")
    void rejectsUnsupportedArithmeticFunctions() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("roundedRate", "ROUND(profitAmount / salesAmount, 2)")),
                List.of("salesAmount", "profitAmount"),
                List.of("salesAmount", "profitAmount"),
                List.of(),
                unsupported);

        assertTrue(result.empty());
        assertTrue(unsupported.stream().anyMatch(message ->
                message.contains("supports only metric ratio or metric difference formulas")));
    }

    @Test
    @DisplayName("rejects qualified aliases in arithmetic subset")
    void rejectsQualifiedArithmeticAliases() {
        List<String> unsupported = new ArrayList<>();

        DslCteDslRequestMapper.ResultStageDerivedMetrics result = RelationResultExpressionCompiler.compile(
                List.of(derived("badRate", "Order.profitAmount / salesAmount")),
                List.of("salesAmount", "profitAmount"),
                List.of("salesAmount", "profitAmount"),
                List.of(),
                unsupported);

        assertTrue(result.empty());
        assertTrue(unsupported.stream().anyMatch(message ->
                message.contains("supports only metric ratio or metric difference formulas")));
    }

    private static Map<String, Object> derived(String name, String expr) {
        return Map.of("name", name, "expr", expr);
    }
}
