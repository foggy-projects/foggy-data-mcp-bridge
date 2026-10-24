package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.engine.pivot.rollup.RollupCache;
import com.foggyframework.dataset.model.engine.pivot.rollup.RollupCoordinate;
import com.foggyframework.dataset.model.engine.pivot.rollup.RollupMetricPlan;
import com.foggyframework.dataset.model.engine.pivot.rollup.RollupStrategy;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 小计与总计注入 (Subtotal & Grand Total Injection)
 *
 * <p>在多维树形结构中生成"父级节点"的过程。</p>
 *
 * <p>S8.3 增强：支持 cache-aware 的 non-additive 度量 rollup。
 * 根据 {@link RollupMetricPlan} 的策略，分别使用内存 SUM/MIN/MAX、
 * 从 {@link RollupCache} 读取辅助查询结果，或读取计算指标在目标 grain 的 SQL 结果。</p>
 *
 * <p>小计节点强行打上 {@code _sys_meta: { isRowSubtotal: true, level: ... }} 的标签。</p>
 */
public class SubtotalInjector {

    private static final Logger logger = LoggerFactory.getLogger(SubtotalInjector.class);
    private static final String SYS_META_KEY = "_sys_meta";

    /**
     * 注入小计和总计行（旧签名，纯内存 SUM 路径）
     *
     * <p>兼容入口：无 rollup plan / cache 时使用。</p>
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<String> rowFields,
                                                   List<String> colFields,
                                                   List<String> metrics,
                                                   PivotOptions options) {
        return apply(resultSet, rowFields, colFields, metrics, options,
                Collections.emptyList(), new RollupCache());
    }

    /**
     * 注入小计和总计行（S8.3 cache-aware 版本）
     *
     * @param resultSet    当前结果集
     * @param rowFields    行轴字段名列表
     * @param colFields    列轴字段名列表
     * @param metrics      度量字段名列表
     * @param options      Pivot 行为开关
     * @param rollupPlans  每个 metric 的 rollup 计划
     * @param rollupCache  辅助查询结果缓存
     * @return 注入小计后的结果集
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<String> rowFields,
                                                   List<String> colFields,
                                                   List<String> metrics,
                                                   PivotOptions options,
                                                   List<RollupMetricPlan> rollupPlans,
                                                   RollupCache rollupCache) {

        // 构建 metric → plan 索引
        Map<String, RollupMetricPlan> planIndex = new LinkedHashMap<>();
        for (RollupMetricPlan plan : rollupPlans) {
            planIndex.put(plan.getMetricName(), plan);
        }

        List<Map<String, Object>> result = new ArrayList<>(resultSet);

        // 行轴小计：按每个层级的父级分组聚合
        if (options.isRowSubtotals() && rowFields.size() > 1) {
            result = injectAxisSubtotals(result, rowFields, colFields, metrics, true,
                    planIndex, rollupCache);
        }

        // 列轴小计
        if (options.isColumnSubtotals() && colFields.size() > 1) {
            result = injectAxisSubtotals(result, colFields, rowFields, metrics, false,
                    planIndex, rollupCache);
        }

        // 总计行
        if (options.isGrandTotal()) {
            result = injectGrandTotal(result, rowFields, colFields, metrics,
                    planIndex, rollupCache);
        }

        return result;
    }

    /**
     * 按轴层级注入小计行
     */
    private static List<Map<String, Object>> injectAxisSubtotals(
            List<Map<String, Object>> resultSet,
            List<String> axisFields,
            List<String> crossFields,
            List<String> metrics,
            boolean isRow,
            Map<String, RollupMetricPlan> planIndex,
            RollupCache rollupCache) {

        List<Map<String, Object>> result = new ArrayList<>(resultSet);

        // 从最内层开始向外层逐层生成小计
        for (int level = axisFields.size() - 1; level >= 1; level--) {
            List<String> groupKeys = new ArrayList<>();
            for (int i = 0; i < level; i++) {
                groupKeys.add(axisFields.get(i));
            }
            groupKeys.addAll(crossFields);

            // 按分组键聚合
            Map<List<Object>, List<Map<String, Object>>> groups = result.stream()
                    .filter(row -> !isSubtotalRow(row))
                    .collect(Collectors.groupingBy(
                            row -> groupKeys.stream()
                                    .map(k -> row.getOrDefault(k, "__null__"))
                                    .collect(Collectors.toList())));

            for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : groups.entrySet()) {
                Map<String, Object> subtotalRow = new LinkedHashMap<>();

                // 填充分组键
                List<Object> keyValues = entry.getKey();
                for (int i = 0; i < level; i++) {
                    subtotalRow.put(axisFields.get(i), keyValues.get(i));
                }
                // 后续轴字段设为 "ALL"（小计标记）
                for (int i = level; i < axisFields.size(); i++) {
                    subtotalRow.put(axisFields.get(i), "ALL");
                }
                // 交叉轴字段
                for (int i = 0; i < crossFields.size(); i++) {
                    subtotalRow.put(crossFields.get(i), keyValues.get(level + i));
                }

                // 按策略填充度量值
                fillMetricValues(subtotalRow, entry.getValue(), metrics, planIndex, rollupCache,
                        axisFields, crossFields, level, isRow);

                // 打标
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put(isRow ? "isRowSubtotal" : "isColSubtotal", true);
                meta.put("level", level);
                subtotalRow.put(SYS_META_KEY, meta);

                result.add(subtotalRow);
            }
        }

        return result;
    }

    /**
     * 注入总计行
     */
    private static List<Map<String, Object>> injectGrandTotal(
            List<Map<String, Object>> resultSet,
            List<String> rowFields,
            List<String> colFields,
            List<String> metrics,
            Map<String, RollupMetricPlan> planIndex,
            RollupCache rollupCache) {

        List<Map<String, Object>> result = new ArrayList<>(resultSet);

        // 按列域分组，为每个列组合生成一条总计行
        Map<List<Object>, List<Map<String, Object>>> colGroups = resultSet.stream()
                .filter(row -> !isSubtotalRow(row))
                .collect(Collectors.groupingBy(
                        row -> colFields.stream()
                                .map(k -> row.getOrDefault(k, "__null__"))
                                .collect(Collectors.toList())));

        for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : colGroups.entrySet()) {
            Map<String, Object> grandRow = new LinkedHashMap<>();

            // 行轴字段全部设为 "GRAND_TOTAL"
            for (String rf : rowFields) {
                grandRow.put(rf, "GRAND_TOTAL");
            }
            // 列轴字段
            List<Object> colValues = entry.getKey();
            for (int i = 0; i < colFields.size(); i++) {
                grandRow.put(colFields.get(i), colValues.get(i));
            }

            // 按策略填充度量值
            fillGrandTotalMetrics(grandRow, entry.getValue(), metrics, planIndex, rollupCache,
                    rowFields, colFields);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("isGrandTotal", true);
            grandRow.put(SYS_META_KEY, meta);

            result.add(grandRow);
        }

        return result;
    }

    /**
     * 按策略填充小计行的度量值
     */
    private static void fillMetricValues(
            Map<String, Object> subtotalRow,
            List<Map<String, Object>> childRows,
            List<String> metrics,
            Map<String, RollupMetricPlan> planIndex,
            RollupCache rollupCache,
            List<String> axisFields,
            List<String> crossFields,
            int level,
            boolean isRow) {

        // 第一遍：填充 IN_MEMORY_* 和 AUX_REQUERY 策略
        for (String metric : metrics) {
            RollupMetricPlan plan = planIndex.get(metric);
            RollupStrategy strategy = plan != null ? plan.getStrategy() : RollupStrategy.IN_MEMORY_SUM;

            switch (strategy) {
                case IN_MEMORY_SUM:
                    subtotalRow.put(metric, sumChildren(childRows, metric));
                    break;
                case IN_MEMORY_MIN:
                    subtotalRow.put(metric, minChildren(childRows, metric));
                    break;
                case IN_MEMORY_MAX:
                    subtotalRow.put(metric, maxChildren(childRows, metric));
                    break;
                case AUX_REQUERY:
                    // 从 rollupCache 读取
                    Object cacheValue = tryReadFromCache(rollupCache, subtotalRow,
                            axisFields, crossFields, level, isRow, metric);
                    subtotalRow.put(metric, cacheValue);
                    break;
                case RECOMPUTE_FROM_BASE:
                    // Expression was compiled at this grain by the governed SQL compiler.
                    subtotalRow.put(
                            metric,
                            tryReadFromCache(
                                    rollupCache,
                                    subtotalRow,
                                    axisFields,
                                    crossFields,
                                    level,
                                    isRow,
                                    metric));
                    break;
                case UNSUPPORTED:
                    subtotalRow.put(metric, null);
                    break;
            }
        }


    }

    /**
     * 按策略填充总计行的度量值
     */
    private static void fillGrandTotalMetrics(
            Map<String, Object> grandRow,
            List<Map<String, Object>> childRows,
            List<String> metrics,
            Map<String, RollupMetricPlan> planIndex,
            RollupCache rollupCache,
            List<String> rowFields,
            List<String> colFields) {

        for (String metric : metrics) {
            RollupMetricPlan plan = planIndex.get(metric);
            RollupStrategy strategy = plan != null ? plan.getStrategy() : RollupStrategy.IN_MEMORY_SUM;

            switch (strategy) {
                case IN_MEMORY_SUM:
                    grandRow.put(metric, sumChildren(childRows, metric));
                    break;
                case IN_MEMORY_MIN:
                    grandRow.put(metric, minChildren(childRows, metric));
                    break;
                case IN_MEMORY_MAX:
                    grandRow.put(metric, maxChildren(childRows, metric));
                    break;
                case AUX_REQUERY:
                    Object cacheValue = tryReadGrandTotalFromCache(rollupCache, grandRow,
                            rowFields, colFields, metric);
                    grandRow.put(metric, cacheValue);
                    break;
                case RECOMPUTE_FROM_BASE:
                    grandRow.put(
                            metric,
                            tryReadGrandTotalFromCache(
                                    rollupCache, grandRow, rowFields, colFields, metric));
                    break;
                case UNSUPPORTED:
                    grandRow.put(metric, null);
                    break;
            }
        }


    }

    // ========== 内存聚合辅助方法 ==========

    private static Object sumChildren(List<Map<String, Object>> rows, String metric) {
        double sum = 0;
        boolean hasValue = false;
        for (Map<String, Object> row : rows) {
            Object val = row.get(metric);
            if (val instanceof Number) {
                sum += ((Number) val).doubleValue();
                hasValue = true;
            }
        }
        return hasValue ? sum : null;
    }

    private static Object minChildren(List<Map<String, Object>> rows, String metric) {
        double min = Double.MAX_VALUE;
        boolean hasValue = false;
        for (Map<String, Object> row : rows) {
            Object val = row.get(metric);
            if (val instanceof Number) {
                double v = ((Number) val).doubleValue();
                if (v < min) min = v;
                hasValue = true;
            }
        }
        return hasValue ? min : null;
    }

    private static Object maxChildren(List<Map<String, Object>> rows, String metric) {
        double max = -Double.MAX_VALUE;
        boolean hasValue = false;
        for (Map<String, Object> row : rows) {
            Object val = row.get(metric);
            if (val instanceof Number) {
                double v = ((Number) val).doubleValue();
                if (v > max) max = v;
                hasValue = true;
            }
        }
        return hasValue ? max : null;
    }

    // ========== Cache 读取辅助方法 ==========

    private static Object tryReadFromCache(
            RollupCache cache, Map<String, Object> subtotalRow,
            List<String> axisFields, List<String> crossFields,
            int level, boolean isRow, String metric) {

        if (cache == null || cache.isEmpty()) {
            logger.debug("[SubtotalInjector] No rollup cache for metric={}, falling back to SUM", metric);
            return null;
        }

        // 构建坐标
        List<RollupCoordinate> coords = new ArrayList<>();
        for (int i = 0; i < axisFields.size(); i++) {
            String field = axisFields.get(i);
            if (i < level) {
                coords.add(RollupCoordinate.of(field, subtotalRow.get(field)));
            } else {
                coords.add(RollupCoordinate.rolledUp(field));
            }
        }
        for (String cf : crossFields) {
            coords.add(RollupCoordinate.of(cf, subtotalRow.get(cf)));
        }

        // 构建 grain key
        List<String> grainFields = new ArrayList<>();
        for (int i = 0; i < level; i++) {
            grainFields.add(axisFields.get(i));
        }
        grainFields.addAll(crossFields);
        String grainKey = String.join("\u001F", grainFields);

        return cache.getOrNull(grainKey, coords, metric);
    }

    private static Object tryReadGrandTotalFromCache(
            RollupCache cache, Map<String, Object> grandRow,
            List<String> rowFields, List<String> colFields, String metric) {

        if (cache == null || cache.isEmpty()) return null;

        List<RollupCoordinate> coords = new ArrayList<>();
        for (String rf : rowFields) {
            coords.add(RollupCoordinate.grandTotal(rf));
        }
        for (String cf : colFields) {
            Object val = grandRow.get(cf);
            // Grand totals retain the complete column grain, including literal business markers.
            coords.add(RollupCoordinate.of(cf, val));
        }

        // Grand total grain key = only column fields
        String grainKey = String.join("\u001F", colFields);

        return cache.getOrNull(grainKey, coords, metric);
    }

    @SuppressWarnings("unchecked")
    static boolean isSubtotalRow(Map<String, Object> row) {
        Object meta = row.get(SYS_META_KEY);
        if (meta instanceof Map) {
            Map<String, Object> metaMap = (Map<String, Object>) meta;
            return Boolean.TRUE.equals(metaMap.get("isRowSubtotal"))
                    || Boolean.TRUE.equals(metaMap.get("isColSubtotal"))
                    || Boolean.TRUE.equals(metaMap.get("isGrandTotal"));
        }
        return false;
    }
}
