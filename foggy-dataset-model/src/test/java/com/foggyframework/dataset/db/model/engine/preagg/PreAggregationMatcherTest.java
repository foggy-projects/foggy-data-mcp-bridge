package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.preagg.PreAggMeasureDef;
import com.foggyframework.dataset.db.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.db.model.impl.preagg.PreAggregationImpl;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预聚合匹配器测试
 *
 * @author foggy-framework
 * @since 8.2.0
 */
class PreAggregationMatcherTest {

    private PreAggregationMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new PreAggregationMatcher();
    }

    @Test
    @DisplayName("无预聚合配置时返回 noMatch")
    void testNoPreAggregations() {
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, null);
        assertFalse(result.isMatched());

        result = matcher.findBestMatch(requirement, Collections.emptyList());
        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("无 GROUP BY 时不使用预聚合")
    void testNoGroupBy() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(false); // 无分组
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("维度完全匹配 - 成功")
    void testDimensionExactMatch() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addDimension("salesDate");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertTrue(result.isMatched());
        assertEquals("daily_product_sales", result.getPreAggName());
    }

    @Test
    @DisplayName("查询维度是预聚合维度的子集 - 成功")
    void testDimensionSubset() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product"); // 只查 product，不查 salesDate
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertTrue(result.isMatched());
    }

    @Test
    @DisplayName("查询维度不在预聚合中 - 失败")
    void testDimensionNotInPreAgg() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addDimension("customer"); // customer 不在预聚合中
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("度量不在预聚合中 - 失败")
    void testMeasureNotInPreAgg() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("profitAmount", DbAggregation.SUM); // profit 不在预聚合中

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("formulaDef 度量未物化到预聚合列时不匹配")
    void formulaMeasureWithoutMaterializedPreAggColumnDoesNotMatch() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmountFormulaYuan", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);

        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("formulaDef 度量只有配置对应物化列时才可匹配")
    void formulaMeasureMatchesWhenMaterializedPreAggColumnConfigured() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("daily_product_formula_sales");
        def.setTableName("preagg_daily_product_formula_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product"));
        PreAggMeasureDef formulaMeasure = createMeasureDef("salesAmountFormulaYuan", "SUM");
        formulaMeasure.setColumnName("sales_amount_formula_yuan_sum");
        def.setMeasures(List.of(formulaMeasure));
        def.setEnabled(true);

        PreAggregation preAgg = new PreAggregationImpl(def, null);
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmountFormulaYuan", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, List.of(preAgg));

        assertTrue(result.isMatched());
        assertEquals("sales_amount_formula_yuan_sum",
                result.getPreAggregation().getMeasureColumnNames().get("salesAmountFormulaYuan"));
    }

    @Test
    @DisplayName("时间粒度 rollup - 日到月")
    void testTimeGranularityRollup() {
        PreAggregation preAgg = createDailyProductPreAgg();
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.setTimeGranularity("salesDate", TimeGranularity.MONTH); // 查询月粒度
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertTrue(result.isMatched());
        assertTrue(result.isNeedsRollup()); // 需要 rollup
    }

    @Test
    @DisplayName("时间粒度不兼容 - 月预聚合不能查日")
    void testTimeGranularityIncompatible() {
        // 创建月粒度预聚合
        PreAggregationDef def = new PreAggregationDef();
        def.setName("monthly_product_sales");
        def.setTableName("preagg_monthly_product_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product", "salesDate"));
        def.setGranularity(Map.of("salesDate", "month")); // 月粒度
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        def.setEnabled(true);

        PreAggregation preAgg = new PreAggregationImpl(def, null);
        List<PreAggregation> preAggregations = List.of(preAgg);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.setTimeGranularity("salesDate", TimeGranularity.DAY); // 查询日粒度
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        // 月粒度预聚合不能用于日粒度查询
        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertFalse(result.isMatched());
    }

    @Test
    @DisplayName("优先级选择 - 选择高优先级预聚合")
    void testPrioritySelection() {
        // 创建两个预聚合：低优先级和高优先级
        PreAggregationDef lowDef = new PreAggregationDef();
        lowDef.setName("low_priority");
        lowDef.setTableName("preagg_low");
        lowDef.setPriority(30);
        lowDef.setDimensions(List.of("product"));
        lowDef.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        lowDef.setEnabled(true);

        PreAggregationDef highDef = new PreAggregationDef();
        highDef.setName("high_priority");
        highDef.setTableName("preagg_high");
        highDef.setPriority(80);
        highDef.setDimensions(List.of("product"));
        highDef.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        highDef.setEnabled(true);

        PreAggregation lowPriority = new PreAggregationImpl(lowDef, null);
        PreAggregation highPriority = new PreAggregationImpl(highDef, null);

        // 顺序：低优先级在前
        List<PreAggregation> preAggregations = List.of(lowPriority, highPriority);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertTrue(result.isMatched());
        assertEquals("high_priority", result.getPreAggName()); // 应选择高优先级
    }

    @Test
    @DisplayName("禁用的预聚合不被选择")
    void testDisabledPreAggSkipped() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("disabled_preagg");
        def.setTableName("preagg_disabled");
        def.setPriority(100);
        def.setDimensions(List.of("product"));
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        def.setEnabled(false); // 禁用

        PreAggregation disabled = new PreAggregationImpl(def, null);
        List<PreAggregation> preAggregations = List.of(disabled);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
        assertFalse(result.isMatched());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建日粒度产品销售预聚合
     */
    private PreAggregation createDailyProductPreAgg() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("daily_product_sales");
        def.setTableName("preagg_daily_product_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product", "salesDate"));
        def.setGranularity(Map.of("salesDate", "day"));
        def.setDimensionProperties(Map.of(
                "product", List.of("category_name", "brand")
        ));
        def.setMeasures(List.of(
                createMeasureDef("salesAmount", "SUM"),
                createMeasureDef("quantity", "SUM"),
                createMeasureDef("orderCount", "COUNT")
        ));
        def.setEnabled(true);

        return new PreAggregationImpl(def, null);
    }

    private PreAggMeasureDef createMeasureDef(String name, String aggregation) {
        PreAggMeasureDef measure = new PreAggMeasureDef();
        measure.setName(name);
        measure.setAggregation(aggregation);
        return measure;
    }
}
