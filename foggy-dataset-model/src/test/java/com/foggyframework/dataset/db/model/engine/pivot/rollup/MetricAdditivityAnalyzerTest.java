package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.spi.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricAdditivityAnalyzer 单元测试
 */
@DisplayName("度量可加性分析器")
class MetricAdditivityAnalyzerTest {

    @Test
    @DisplayName("SUM → IN_MEMORY_SUM")
    void testSumClassification() {
        assertEquals(RollupStrategy.IN_MEMORY_SUM,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.SUM));
    }

    @Test
    @DisplayName("COUNT → IN_MEMORY_SUM")
    void testCountClassification() {
        assertEquals(RollupStrategy.IN_MEMORY_SUM,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.COUNT));
    }

    @Test
    @DisplayName("MIN → IN_MEMORY_MIN")
    void testMinClassification() {
        assertEquals(RollupStrategy.IN_MEMORY_MIN,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.MIN));
    }

    @Test
    @DisplayName("MAX → IN_MEMORY_MAX")
    void testMaxClassification() {
        assertEquals(RollupStrategy.IN_MEMORY_MAX,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.MAX));
    }

    @Test
    @DisplayName("AVG → AUX_REQUERY")
    void testAvgClassification() {
        assertEquals(RollupStrategy.AUX_REQUERY,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.AVG));
    }

    @Test
    @DisplayName("COUNT_DISTINCT → AUX_REQUERY")
    void testCountDistinctClassification() {
        assertEquals(RollupStrategy.AUX_REQUERY,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.COUNT_DISTINCT));
    }

    @Test
    @DisplayName("STDDEV_POP → UNSUPPORTED")
    void testStddevClassification() {
        assertEquals(RollupStrategy.UNSUPPORTED,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.STDDEV_POP));
    }

    @Test
    @DisplayName("GROUP_CONCAT → UNSUPPORTED")
    void testGroupConcatClassification() {
        assertEquals(RollupStrategy.UNSUPPORTED,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.GROUP_CONCAT));
    }

    @Test
    @DisplayName("WINDOW → UNSUPPORTED")
    void testWindowClassification() {
        assertEquals(RollupStrategy.UNSUPPORTED,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.WINDOW));
    }

    @Test
    @DisplayName("CUSTOM → UNSUPPORTED")
    void testCustomClassification() {
        assertEquals(RollupStrategy.UNSUPPORTED,
                MetricAdditivityAnalyzer.classifyAggregation(DbAggregation.CUSTOM));
    }

    @Test
    @DisplayName("null agg → IN_MEMORY_SUM (向后兼容)")
    void testNullAggClassification() {
        assertEquals(RollupStrategy.IN_MEMORY_SUM,
                MetricAdditivityAnalyzer.classifyAggregation(null));
    }

    @Test
    @DisplayName("calculatedField a - b → RECOMPUTE_FROM_BASE")
    void testCalculatedFieldAddSub() {
        CalculatedFieldDef cf = new CalculatedFieldDef("grossProfit", "revenueAmount - costAmount");
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("grossProfit"), null, List.of(cf));

        assertEquals(1, plans.size());
        assertEquals(RollupStrategy.RECOMPUTE_FROM_BASE, plans.get(0).getStrategy());
        assertTrue(plans.get(0).getRequiredBaseMetrics().contains("revenueAmount"));
        assertTrue(plans.get(0).getRequiredBaseMetrics().contains("costAmount"));
    }

    @Test
    @DisplayName("calculatedField 含 CALCULATE → UNSUPPORTED")
    void testCalculatedFieldWithCalculate() {
        CalculatedFieldDef cf = new CalculatedFieldDef("ytd", "CALCULATE(salesAmount, YEAR)");
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("ytd"), null, List.of(cf));

        assertEquals(1, plans.size());
        assertEquals(RollupStrategy.UNSUPPORTED, plans.get(0).getStrategy());
    }

    @Test
    @DisplayName("calculatedField 含 ratio → RECOMPUTE_FROM_BASE 带 base metrics")
    void testCalculatedFieldRatio() {
        CalculatedFieldDef cf = new CalculatedFieldDef("profitRate",
                "profitAmount / NULLIF(salesAmount, 0) * 100");
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("profitRate"), null, List.of(cf));

        assertEquals(1, plans.size());
        RollupMetricPlan plan = plans.get(0);
        assertEquals(RollupStrategy.RECOMPUTE_FROM_BASE, plan.getStrategy());
        assertTrue(plan.getRequiredBaseMetrics().contains("profitAmount"));
        assertTrue(plan.getRequiredBaseMetrics().contains("salesAmount"));
    }

    @Test
    @DisplayName("hasNonAdditiveMetrics 正确识别")
    void testHasNonAdditiveMetrics() {
        List<RollupMetricPlan> plans = List.of(
                new RollupMetricPlan("salesAmount", RollupStrategy.IN_MEMORY_SUM, DbAggregation.SUM),
                new RollupMetricPlan("uniqueCustomers", RollupStrategy.AUX_REQUERY, DbAggregation.COUNT_DISTINCT)
        );
        assertTrue(MetricAdditivityAnalyzer.hasNonAdditiveMetrics(plans));
    }

    @Test
    @DisplayName("纯 additive metrics 不触发辅助查询")
    void testPureAdditiveNoAux() {
        List<RollupMetricPlan> plans = List.of(
                new RollupMetricPlan("salesAmount", RollupStrategy.IN_MEMORY_SUM, DbAggregation.SUM),
                new RollupMetricPlan("quantity", RollupStrategy.IN_MEMORY_SUM, DbAggregation.SUM)
        );
        assertFalse(MetricAdditivityAnalyzer.hasNonAdditiveMetrics(plans));
    }

    @Test
    @DisplayName("collectAuxQueryMetrics 收集 AUX_REQUERY 和 RECOMPUTE_FROM_BASE 的 base metrics")
    void testCollectAuxQueryMetrics() {
        List<RollupMetricPlan> plans = List.of(
                new RollupMetricPlan("salesAmount", RollupStrategy.IN_MEMORY_SUM, DbAggregation.SUM),
                new RollupMetricPlan("uniqueCustomers", RollupStrategy.AUX_REQUERY, DbAggregation.COUNT_DISTINCT),
                new RollupMetricPlan("profitRate", RollupStrategy.RECOMPUTE_FROM_BASE, null,
                        List.of("profitAmount", "salesAmount"), "profitAmount / salesAmount")
        );

        var auxMetrics = MetricAdditivityAnalyzer.collectAuxQueryMetrics(plans);
        assertTrue(auxMetrics.contains("uniqueCustomers"));
        assertTrue(auxMetrics.contains("profitAmount"));
        assertTrue(auxMetrics.contains("salesAmount"));
        assertFalse(auxMetrics.contains("profitRate")); // profitRate 本身不需要辅助查询
    }

    @Test
    @DisplayName("P0-4: calculatedField 循环依赖 → UNSUPPORTED（不崩溃）")
    void testCircularDependencyDoesNotCrash() {
        CalculatedFieldDef cfA = new CalculatedFieldDef("metricA", "metricB + 1");
        CalculatedFieldDef cfB = new CalculatedFieldDef("metricB", "metricA + 1");
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("metricA"), null, List.of(cfA, cfB));

        assertEquals(1, plans.size());
        assertEquals(RollupStrategy.UNSUPPORTED, plans.get(0).getStrategy());
    }

    @Test
    @DisplayName("P0-4: 三级循环依赖 → UNSUPPORTED")
    void testTripleCycleDoesNotCrash() {
        CalculatedFieldDef cfA = new CalculatedFieldDef("a", "b + 1");
        CalculatedFieldDef cfB = new CalculatedFieldDef("b", "c + 1");
        CalculatedFieldDef cfC = new CalculatedFieldDef("c", "a + 1");
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("a"), null, List.of(cfA, cfB, cfC));

        assertEquals(1, plans.size());
        assertEquals(RollupStrategy.UNSUPPORTED, plans.get(0).getStrategy());
    }

    @Test
    @DisplayName("S10.1: 度量元数据缺失时 fail-closed 负例测试")
    void testMissingMetricMetadataIsFailClosed() {
        // 请求一个在 QueryModel 中完全不存在，也没在 calculatedFields 中定义的度量
        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                List.of("unknown_metric"), null, java.util.Collections.emptyList());

        assertEquals(1, plans.size());
        assertEquals(RollupStrategy.UNSUPPORTED, plans.get(0).getStrategy(),
                "未知度量必须 fallback 到 UNSUPPORTED，不能通过");
    }
}
