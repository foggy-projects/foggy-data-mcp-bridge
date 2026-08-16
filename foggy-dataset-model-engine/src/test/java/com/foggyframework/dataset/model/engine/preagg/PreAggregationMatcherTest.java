package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.def.preagg.PreAggMeasureDef;
import com.foggyframework.dataset.model.def.preagg.PreAggFilterDef;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.def.preagg.PreAggregationDef;
import com.foggyframework.dataset.model.impl.preagg.PreAggregationImpl;
import com.foggyframework.dataset.model.semantic.permission.PermissionPredicate;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregationBuildMode;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.time.LocalDate;

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
        assertEquals("PREAGG_NOT_CONFIGURED", result.getReasonCode());

        result = matcher.findBestMatch(requirement, Collections.emptyList());
        assertFalse(result.isMatched());
        assertEquals("PREAGG_NOT_CONFIGURED", result.getReasonCode());
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
        assertEquals("PREAGG_GROUP_BY_REQUIRED", result.getReasonCode());
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
        assertTrue(result.isNeedsRollup(),
                "omitting the pre-aggregation salesDate dimension must trigger rollup");
    }

    @Test
    @DisplayName("按维度属性分组时必须从维度键粒度 rollup")
    void testDimensionPropertyRequiresRollup() {
        PreAggregation preAgg = createProductPreAgg("salesAmount", "SUM");

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addDimensionProperty("product", "categoryName");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, List.of(preAgg));

        assertTrue(result.isMatched());
        assertTrue(result.isNeedsRollup(),
                "categoryName can repeat across product keys and must be aggregated again");
    }

    @Test
    @DisplayName("推导列名和时间粒度不能冒充物化列契约")
    void inferredColumnNamesAndGrainDoNotProveMaterializedProperties() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("monthly_product_sales");
        def.setTableName("preagg_monthly_product_sales");
        def.setDimensions(List.of("salesDate", "product"));
        def.setGranularity(Map.of("salesDate", "month"));
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        def.setEnabled(true);
        PreAggregation preAgg = new PreAggregationImpl(def, null);

        assertEquals("product_name",
                preAgg.getDimensionPropertyColumnNames().get("product$caption"),
                "runtime may retain a naming-convention hint");
        assertTrue(preAgg.getExplicitDimensionPropertyColumnNames().isEmpty(),
                "fixture deliberately declares no physical dimension columns");

        PreAggQueryRequirement captionRequirement = new PreAggQueryRequirement();
        captionRequirement.setHasGroupBy(true);
        captionRequirement.addDimension("product");
        captionRequirement.addDimensionProperty("product", "caption");
        captionRequirement.addMeasure("salesAmount", DbAggregation.SUM);
        assertFalse(captionRequirement.isSatisfiableBy(preAgg),
                "guessed product_name must not prove a physical caption column");

        PreAggQueryRequirement monthRequirement = new PreAggQueryRequirement();
        monthRequirement.setHasGroupBy(true);
        monthRequirement.addDimension("salesDate");
        monthRequirement.addDimensionProperty("salesDate", "month");
        monthRequirement.setTimeGranularity("salesDate", TimeGranularity.MONTH);
        monthRequirement.addMeasure("salesAmount", DbAggregation.SUM);
        assertFalse(monthRequirement.isSatisfiableBy(preAgg),
                "MONTH grain must not prove that a physical month column exists");
    }

    @ParameterizedTest
    @EnumSource(value = DbAggregation.class, names = {"AVG", "COUNT_DISTINCT"})
    @DisplayName("非可分解度量只允许精确粒度，粗粒度 rollup 必须拒绝")
    void nonDecomposableMeasuresRequireExactGrain(DbAggregation aggregation) {
        PreAggregation preAgg = createDailyProductPreAgg("derivedMeasure", aggregation.name());

        PreAggQueryRequirement exact = new PreAggQueryRequirement();
        exact.setHasGroupBy(true);
        exact.addDimension("product");
        exact.addDimension("salesDate");
        exact.addMeasure("derivedMeasure", aggregation);

        PreAggregationMatchResult exactResult = matcher.findBestMatch(exact, List.of(preAgg));
        assertTrue(exactResult.isMatched(), "exact pre-aggregation grain may reuse a materialized value");
        assertFalse(exactResult.isNeedsRollup());

        PreAggQueryRequirement coarser = new PreAggQueryRequirement();
        coarser.setHasGroupBy(true);
        coarser.addDimension("product");
        coarser.addMeasure("derivedMeasure", aggregation);

        PreAggregationMatchResult coarserResult = matcher.findBestMatch(coarser, List.of(preAgg));
        assertFalse(coarserResult.isMatched(),
                aggregation + " cannot be recomputed from unequal-size pre-aggregation groups");

        PreAggregation hybridPreAgg = createDailyProductPreAgg(
                "derivedMeasure", aggregation.name(), true);
        hybridPreAgg.setDataWatermark(LocalDate.now().minusDays(1));
        PreAggregationMatchResult hybridResult = matcher.findBestMatch(exact, List.of(hybridPreAgg));
        assertFalse(hybridResult.isMatched(),
                aggregation + " cannot be merged with raw source rows in hybrid mode");
    }

    @Test
    @DisplayName("hybrid watermark 未初始化时必须 fail closed")
    void uninitializedHybridWatermarkDoesNotMatch() {
        PreAggregation preAgg = createDailyProductPreAgg("salesAmount", "SUM", true);
        assertNull(preAgg.getDataWatermark());

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addDimension("salesDate");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        assertFalse(matcher.findBestMatch(requirement, List.of(preAgg)).isMatched(),
                "null watermark cannot define either hybrid SQL branch");

        preAgg.setDataWatermark(LocalDate.now());
        PreAggregationMatchResult hybrid = matcher.findBestMatch(requirement, List.of(preAgg));
        assertTrue(hybrid.isMatched());
        assertTrue(hybrid.isHybridQuery(),
                "exclusive boundary at today must keep the current DATE bucket on source");

        preAgg.setDataWatermark(LocalDate.now().plusDays(30));
        assertFalse(matcher.findBestMatch(requirement, List.of(preAgg)).isMatched(),
                "future watermark cannot prove a complete open DATE bucket");

        preAgg.setDataWatermark(20240101);
        assertFalse(matcher.findBestMatch(requirement, List.of(preAgg)).isMatched(),
                "a foreign watermark domain must fail closed");

        matcher.setHybridQueryEnabled(false);
        PreAggregationMatchResult snapshotOnly = matcher.findBestMatch(
                requirement, List.of(preAgg));
        assertTrue(snapshotOnly.isMatched(),
                "an explicit snapshot-only policy may ignore incremental freshness");
        assertFalse(snapshotOnly.isHybridQuery());
    }

    @Test
    @DisplayName("hybrid semantic watermark 必须有物化列契约")
    void hybridSemanticWatermarkRequiresMaterializedColumnContract() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("unsafe_incremental_sales");
        def.setTableName("unsafe_incremental_sales");
        def.setDimensions(List.of("salesDate", "product"));
        def.setGranularity(Map.of("salesDate", "day"));
        def.setDimensionProperties(Map.of("product", List.of("category_name")));
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        PreAggRefreshDef refresh = new PreAggRefreshDef();
        refresh.setStrategy("INCREMENTAL");
        refresh.setWatermarkColumn("salesDate$id");
        def.setRefresh(refresh);
        def.setEnabled(true);
        PreAggregation preAgg = new PreAggregationImpl(def, null);
        preAgg.setDataWatermark(LocalDate.now().minusDays(1));

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        assertFalse(matcher.findBestMatch(requirement, List.of(preAgg)).isMatched(),
                "hybrid SQL must not guess a date_key for an undeclared salesDate$id watermark");
    }

    @Test
    @DisplayName("自然周在缺少日历对齐元数据时不得 rollup 到月季年")
    void calendarWeekDoesNotRollUpAcrossNonNestedBoundaries() {
        assertTrue(TimeGranularity.DAY.canRollupTo(TimeGranularity.WEEK));
        assertTrue(TimeGranularity.DAY.canRollupTo(TimeGranularity.MONTH));
        assertTrue(TimeGranularity.MONTH.canRollupTo(TimeGranularity.QUARTER));
        assertTrue(TimeGranularity.QUARTER.canRollupTo(TimeGranularity.YEAR));

        assertTrue(TimeGranularity.WEEK.canRollupTo(TimeGranularity.WEEK));
        assertFalse(TimeGranularity.WEEK.canRollupTo(TimeGranularity.MONTH));
        assertFalse(TimeGranularity.WEEK.canRollupTo(TimeGranularity.QUARTER));
        assertFalse(TimeGranularity.WEEK.canRollupTo(TimeGranularity.YEAR));
    }

    @Test
    @DisplayName("同一时间维同时要求周和月时必须收紧到日粒度")
    void weekAndCalendarPeriodRequirementsNeedDailyMaterialization() {
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);
        requirement.setTimeGranularity("salesDate", TimeGranularity.WEEK);
        requirement.setTimeGranularity("salesDate", TimeGranularity.MONTH);

        assertEquals(TimeGranularity.DAY,
                requirement.getQueryGranularities().get("salesDate"));

        PreAggregationDef weeklyDef = new PreAggregationDef();
        weeklyDef.setName("weekly_sales");
        weeklyDef.setTableName("preagg_weekly_sales");
        weeklyDef.setPriority(100);
        weeklyDef.setDimensions(List.of("salesDate"));
        weeklyDef.setGranularity(Map.of("salesDate", "week"));
        weeklyDef.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        weeklyDef.setEnabled(true);

        assertFalse(matcher.findBestMatch(
                        requirement, List.of(new PreAggregationImpl(weeklyDef, null))).isMatched(),
                "weekly rows cannot be repartitioned into calendar months");
        assertTrue(matcher.findBestMatch(
                        requirement, List.of(createDailyProductPreAgg())).isMatched(),
                "daily rows can satisfy both calendar-week and calendar-month shapes");
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

    @Test
    @DisplayName("查询时间粒度已证明时拒绝未声明粒度的候选")
    void testMissingMaterializationGranularityFailsClosed() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("unknown_grain_product_sales");
        def.setTableName("preagg_unknown_grain_product_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product", "salesDate"));
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        def.setEnabled(true);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.setTimeGranularity("salesDate", TimeGranularity.DAY);
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        PreAggregationMatchResult result = matcher.findBestMatch(
                requirement, List.of(new PreAggregationImpl(def, null)));

        assertFalse(result.isMatched(),
                "an unknown materialization grain must not masquerade as DAY");
    }

    @Test
    @DisplayName("永久过滤预聚合在过滤含义未可证时 fail closed")
    void filteredPreAggregationRequiresPredicateImplicationProof() {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("completed_product_sales");
        def.setTableName("preagg_completed_product_sales");
        def.setPriority(100);
        def.setDimensions(List.of("product"));
        def.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        PreAggFilterDef filter = new PreAggFilterDef();
        filter.setField("orderStatus");
        filter.setOp("=");
        filter.setValue("COMPLETED");
        def.setFilters(List.of(filter));
        def.setEnabled(true);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);

        assertFalse(matcher.findBestMatch(
                        requirement, List.of(new PreAggregationImpl(def, null))).isMatched(),
                "a filtered materialization cannot serve an unproven unfiltered query");
    }

    @Test
    @DisplayName("权限谓词仅在授权签名与物化安全列均可证明时匹配")
    void securityPredicateRequiresSignatureAndMaterializedIdentity() {
        PreAggregation complete = createDailyProductPreAgg();
        PermissionPredicate predicate = PermissionPredicate.provable(
                PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                "product",
                "product",
                "in",
                List.of(101L, 102L));

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);
        requirement.setSecurityPredicates(List.of(predicate));
        requirement.setSecurityContextCacheable(true);

        assertTrue(matcher.findBestMatch(requirement, List.of(complete)).isMatched());

        requirement.setSecurityContextCacheable(false);
        assertEquals("MISSING_AUTHORIZATION_SIGNATURE",
                requirement.securityFailureReason(complete));
        assertFalse(matcher.findBestMatch(requirement, List.of(complete)).isMatched());
    }

    @Test
    @DisplayName("缺少安全维度、非法算子与不可证明谓词均 fail closed")
    void unsafeSecurityPredicatesFailClosed() {
        PreAggregation missingProductId = createProductPreAgg("salesAmount", "SUM");
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);
        requirement.setSecurityPredicates(List.of(PermissionPredicate.provable(
                PermissionPredicate.Origin.TM_BASE_PERMISSION,
                "product",
                "product",
                "=",
                101L)));
        assertEquals("MISSING_SECURITY_DIMENSION",
                requirement.securityFailureReason(missingProductId));

        requirement.setSecurityPredicates(List.of(PermissionPredicate.provable(
                PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                "product",
                "product$id",
                "not in",
                List.of(101L))));
        assertEquals("UNSUPPORTED_SECURITY_OPERATOR",
                requirement.securityFailureReason(createDailyProductPreAgg()));

        requirement.setSecurityPredicates(List.of(
                PermissionPredicate.unprovable("product$id", "legacy SQL access")));
        assertEquals("UNPROVABLE_SECURITY_PREDICATE",
                requirement.securityFailureReason(createDailyProductPreAgg()));
    }

    @Test
    @DisplayName("高优先级候选缺少安全列时继续选择可证明的低优先级候选")
    void matcherSkipsUnsafeHigherPriorityCandidate() {
        PreAggregationDef unsafeDef = new PreAggregationDef();
        unsafeDef.setName("unsafe_high_priority");
        unsafeDef.setTableName("unsafe_high_priority");
        unsafeDef.setPriority(100);
        unsafeDef.setDimensions(List.of("product"));
        unsafeDef.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        unsafeDef.setEnabled(true);

        PreAggregationDef safeDef = new PreAggregationDef();
        safeDef.setName("safe_low_priority");
        safeDef.setTableName("safe_low_priority");
        safeDef.setPriority(10);
        safeDef.setDimensions(List.of("product"));
        safeDef.setDimensionProperties(Map.of("product", List.of("id")));
        safeDef.setMeasures(List.of(createMeasureDef("salesAmount", "SUM")));
        safeDef.setEnabled(true);

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("product");
        requirement.addMeasure("salesAmount", DbAggregation.SUM);
        requirement.setSecurityPredicates(List.of(PermissionPredicate.provable(
                PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                "product",
                "product",
                "=",
                101L)));

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, List.of(
                new PreAggregationImpl(unsafeDef, null),
                new PreAggregationImpl(safeDef, null)));

        assertTrue(result.isMatched());
        assertEquals("safe_low_priority", result.getPreAggName());
    }

    @Test
    @DisplayName("预聚合构建模式默认 GLOBAL，SECURITY_SCOPED 在加载时 fail fast")
    void scopedBuildModeFailsFast() {
        PreAggregationDef global = new PreAggregationDef();
        global.setName("global_sales");
        global.setTableName("global_sales");
        global.setEnabled(true);
        assertEquals(PreAggregationBuildMode.GLOBAL,
                new PreAggregationImpl(global, null).getBuildMode());

        PreAggregationDef scoped = new PreAggregationDef();
        scoped.setName("scoped_sales");
        scoped.setTableName("scoped_sales");
        scoped.setBuildMode("SECURITY_SCOPED");
        scoped.setEnabled(true);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PreAggregationImpl(scoped, null));
        assertTrue(error.getMessage().contains("SECURITY_SCOPED"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建日粒度产品销售预聚合
     */
    private PreAggregation createDailyProductPreAgg() {
        return createDailyProductPreAgg("salesAmount", "SUM");
    }

    private PreAggregation createDailyProductPreAgg(String measureName, String aggregation) {
        return createDailyProductPreAgg(measureName, aggregation, false);
    }

    private PreAggregation createDailyProductPreAgg(String measureName, String aggregation,
                                                     boolean incremental) {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("daily_product_sales");
        def.setTableName("preagg_daily_product_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product", "salesDate"));
        def.setGranularity(Map.of("salesDate", "day"));
        def.setDimensionProperties(Map.of(
                "product", List.of("id", "category_name", "brand"),
                "salesDate", List.of("id")
        ));
        def.setMeasures(List.of(
                createMeasureDef(measureName, aggregation)
        ));
        if (incremental) {
            PreAggRefreshDef refresh = new PreAggRefreshDef();
            refresh.setStrategy("INCREMENTAL");
            refresh.setWatermarkColumn("salesDate$id");
            def.setRefresh(refresh);
        }
        def.setEnabled(true);

        return new PreAggregationImpl(def, null);
    }

    private PreAggregation createProductPreAgg(String measureName, String aggregation) {
        PreAggregationDef def = new PreAggregationDef();
        def.setName("product_sales");
        def.setTableName("preagg_product_sales");
        def.setPriority(50);
        def.setDimensions(List.of("product"));
        def.setDimensionProperties(Map.of("product", List.of("category_name")));
        def.setMeasures(List.of(createMeasureDef(measureName, aggregation)));
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
