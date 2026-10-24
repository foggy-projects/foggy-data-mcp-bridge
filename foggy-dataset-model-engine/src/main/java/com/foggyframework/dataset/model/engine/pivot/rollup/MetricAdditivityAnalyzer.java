package com.foggyframework.dataset.model.engine.pivot.rollup;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 度量可加性分析器
 *
 * <p>分析每个 metric 的聚合类型，确定 rollup 策略。</p>
 *
 * <p>判定规则（参考 04_non_additive_rollup_design.md §四）：</p>
 * <ul>
 *   <li>SUM/COUNT → IN_MEMORY_SUM</li>
 *   <li>MIN → IN_MEMORY_MIN</li>
 *   <li>MAX → IN_MEMORY_MAX</li>
 *   <li>AVG/COUNT_DISTINCT → AUX_REQUERY</li>
 *   <li>STDDEV/VAR → UNSUPPORTED (第一版)</li>
 *   <li>GROUP_CONCAT/CUSTOM/WINDOW/NONE/PK → UNSUPPORTED</li>
 *   <li>calculatedFields: 依赖图分析</li>
 * </ul>
 */
public class MetricAdditivityAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MetricAdditivityAnalyzer.class);

    /** 不可加的高风险函数 */
    private static final Set<String> UNSUPPORTED_FUNCTIONS = Set.of(
            "CALCULATE", "OFFSET", "REMOVE", "ROLLUP_TO", "CELL_AT", "AXIS_REF",
            "RANK", "ROW_NUMBER", "DENSE_RANK", "NTILE", "LAG", "LEAD",
            "NTH_VALUE", "FIRST_VALUE", "LAST_VALUE"
    );

    /**
     * 分析所有 metrics 的可加性，生成 rollup 计划
     *
     * @param metrics           Pivot 请求中的度量名列表
     * @param queryModel        QueryModel（可为 null，此时对所有 metric 默认 SUM）
     * @param calculatedFields  请求中的 calculatedFields（可为 null）
     * @return 每个 metric 的 RollupMetricPlan
     */
    public static List<RollupMetricPlan> analyze(
            List<String> metrics,
            QueryModel queryModel,
            List<CalculatedFieldDef> calculatedFields) {

        // 构建 calculatedField name → def 映射
        Map<String, CalculatedFieldDef> cfMap = new LinkedHashMap<>();
        if (calculatedFields != null) {
            for (CalculatedFieldDef cf : calculatedFields) {
                cfMap.put(cf.getName(), cf);
            }
        }

        List<RollupMetricPlan> plans = new ArrayList<>();
        for (String metric : metrics) {
            RollupMetricPlan plan = analyzeMetric(metric, queryModel, cfMap);
            plans.add(plan);
            logger.debug("[MetricAdditivityAnalyzer] {}: {} (agg={})",
                    metric, plan.getStrategy(), plan.getAggregation());
        }
        return plans;
    }

    /**
     * 分析单个 metric
     */
    private static RollupMetricPlan analyzeMetric(
            String metric,
            QueryModel queryModel,
            Map<String, CalculatedFieldDef> cfMap) {

        // 1. 检查是否是 calculatedField
        if (cfMap.containsKey(metric)) {
            return analyzeCalculatedField(metric, cfMap, queryModel, new HashSet<>());
        }

        // 2. 从 QueryModel/TableModel 获取聚合类型
        DbAggregation agg = resolveAggregation(metric, queryModel);

        if (agg == null) {
            // 严格的 Fail-closed 策略：如果无法在 QueryModel/TableModel 或 calculatedFields 中找到度量的聚合定义，
            // 必须坚决拒绝，而不是默认返回 IN_MEMORY_SUM/SUM。这避免了因拼写错误或字段缺失导致的隐式静默错误。
            logger.warn("[MetricAdditivityAnalyzer] Metric metadata missing, defaulting to UNSUPPORTED: {}", metric);
            return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, DbAggregation.NONE);
        }

        return new RollupMetricPlan(metric, classifyAggregation(agg), agg);
    }

    /**
     * 分析 calculatedField 的可加性
     *
     * @param visiting 正在访问的 calculatedField 集合，用于检测循环依赖（P0-4）
     */
    private static RollupMetricPlan analyzeCalculatedField(
            String metric,
            Map<String, CalculatedFieldDef> cfMap,
            QueryModel queryModel,
            Set<String> visiting) {

        // P0-4: 循环依赖检测
        if (!visiting.add(metric)) {
            logger.warn("[MetricAdditivityAnalyzer] Circular dependency detected for calculatedField: {}", metric);
            return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, null);
        }

        try {
            CalculatedFieldDef cf = cfMap.get(metric);
            String expression = cf.getExpression();
            if (cf.getWindowOrderBy() != null && !cf.getWindowOrderBy().isEmpty())
                return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, null);

            if (expression == null || expression.isBlank()) {
                return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, null);
            }

            // 检查是否包含不支持的函数
            String upperExpr = expression.toUpperCase();
            for (String func : UNSUPPORTED_FUNCTIONS) {
                if (upperExpr.contains(func + "(") || upperExpr.contains(func + " (")) {
                    logger.debug("[MetricAdditivityAnalyzer] {} contains unsupported function: {}",
                            metric, func);
                    return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, null);
                }
            }

            // 提取表达式中引用的标识符
            Set<String> referenced =
                    new LinkedHashSet<>(
                            com.foggyframework.dataset.model.engine.expression
                                    .CalculatedFieldService.extractColumnReferences(expression));

            // 在剩余标识符中找到实际的 base metrics
            List<String> baseMetrics = new ArrayList<>();
            for (String ref : referenced) {
                // 检查是否是另一个 calculatedField
                if (cfMap.containsKey(ref)) {
                    // 递归检查，如果依赖中有 non-additive 就传染
                    RollupMetricPlan depPlan = analyzeCalculatedField(ref, cfMap, queryModel, visiting);
                    if (depPlan.getStrategy() == RollupStrategy.UNSUPPORTED) {
                        return new RollupMetricPlan(metric, RollupStrategy.UNSUPPORTED, null);
                    }
                    // 把依赖的 base metrics 也加进来
                    if (!depPlan.getRequiredBaseMetrics().isEmpty()) {
                        baseMetrics.addAll(depPlan.getRequiredBaseMetrics());
                    } else {
                        baseMetrics.add(ref);
                    }
                } else {
                    // 可能是真正的度量
                    DbAggregation agg = resolveAggregation(ref, queryModel);
                    if (agg != null) {
                        baseMetrics.add(ref);
                    } else {
                        // queryModel 不可用或度量未注册时，仍假定为 base metric（乐观假定）
                        // 这样在无 TM 场景下 calculatedField 也能走 RECOMPUTE_FROM_BASE
                        baseMetrics.add(ref);
                    }
                }
            }


            return new RollupMetricPlan(metric, RollupStrategy.RECOMPUTE_FROM_BASE, null,
                    baseMetrics, expression);
        } finally {
            visiting.remove(metric);
        }
    }

    /**
     * 从 QueryModel 解析度量的默认聚合类型
     */
    public static DbAggregation resolveAggregation(String metricName, QueryModel queryModel) {
        if (queryModel == null) return null;

        var column = queryModel.findJdbcColumnForSelectByName(metricName, false);
        if (column != null) return column.getAggregation();
        TableModel tm = queryModel.getJdbcModel();
        if (tm == null) return null;

        DbMeasure measure = tm.findJdbcMeasureByName(metricName);
        if (measure == null) return null;

        return measure.getAggregation();
    }

    /**
     * 将聚合类型映射为 RollupStrategy
     */
    static RollupStrategy classifyAggregation(DbAggregation agg) {
        if (agg == null) return RollupStrategy.IN_MEMORY_SUM;

        return switch (agg) {
            case SUM, COUNT -> RollupStrategy.IN_MEMORY_SUM;
            case MIN -> RollupStrategy.IN_MEMORY_MIN;
            case MAX -> RollupStrategy.IN_MEMORY_MAX;
            case AVG, COUNT_DISTINCT -> RollupStrategy.AUX_REQUERY;
            case STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP -> RollupStrategy.UNSUPPORTED;
            case GROUP_CONCAT, CUSTOM, WINDOW, NONE, PK -> RollupStrategy.UNSUPPORTED;
        };
    }

    /**
     * 判断 metrics 中是否有需要辅助查询的度量
     */
    public static boolean hasNonAdditiveMetrics(List<RollupMetricPlan> plans) {
        return plans.stream().anyMatch(RollupMetricPlan::needsAuxQuery);
    }

    /**
     * 收集所有需要辅助查询的度量名（含 base metrics）
     */
    public static Set<String> collectAuxQueryMetrics(List<RollupMetricPlan> plans) {
        Set<String> result = new LinkedHashSet<>();
        for (RollupMetricPlan plan : plans) {
            if (plan.getStrategy() == RollupStrategy.AUX_REQUERY) {
                result.add(plan.getMetricName());
            } else if (plan.getStrategy() == RollupStrategy.RECOMPUTE_FROM_BASE) {
                result.add(plan.getMetricName());
            }
        }
        return result;
    }
}
