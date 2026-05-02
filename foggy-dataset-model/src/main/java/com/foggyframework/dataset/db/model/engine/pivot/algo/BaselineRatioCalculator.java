package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.engine.pivot.rollup.MetricAdditivityAnalyzer;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * BaselineRatio 基准引用计算器（Phase 2.9）
 *
 * <p>在 ParentShareCalculator 之后、ResultShaper 之前执行。</p>
 *
 * <p>语义：当前单元格指标值 / 同一行坐标下列轴首个或末个基准成员的指标值。</p>
 *
 * <p>第一版限制：
 * <ul>
 *   <li>只支持 axis = columns</li>
 *   <li>只支持 baseline = first 或 last</li>
 *   <li>of 引用必须是原生度量</li>
 *   <li>除零、基准缺失、当前值 null 时返回 null</li>
 *   <li>baselineRatio 只作为输出指标，不参与 having/orderBy/limit</li>
 * </ul>
 */
public class BaselineRatioCalculator {

    private static final Logger logger = LoggerFactory.getLogger(BaselineRatioCalculator.class);
    private static final String SYS_META_KEY = "_sys_meta";

    /**
     * 对结果集中的每行计算 baselineRatio 并写入
     */
    public static List<Map<String, Object>> apply(
            List<Map<String, Object>> resultSet,
            PivotRequest pivot,
            List<String> rowFields,
            List<String> colFields) {

        List<PivotMetricItem> brMetrics = pivot.getBaselineRatioMetrics();
        if (brMetrics.isEmpty()) {
            return resultSet;
        }

        // 1. 获取所有的基准列维度组合（非 subtotal 行的 colFields 组合），按自然排序确定 first/last
        //    不能依赖 DB 返回行顺序（PostgreSQL GROUP BY 顺序与 SQLite/MySQL 不同）
        //    跳过任何 colField 值为 null 的行（LEFT JOIN 产生的 null 不是有效基准成员）
        List<Map<String, Object>> colDomains = new ArrayList<>();
        Set<String> colDomainKeys = new HashSet<>();

        for (Map<String, Object> row : resultSet) {
            if (isSubtotalRow(row)) continue;

            Map<String, Object> colKeyMap = new LinkedHashMap<>();
            boolean hasNull = false;
            for (String col : colFields) {
                Object val = row.get(col);
                if (val == null) {
                    hasNull = true;
                    break;
                }
                colKeyMap.put(col, val);
            }
            if (hasNull) continue; // 跳过包含 null 列值的行

            String keyStr = colKeyMap.toString();
            if (!colDomainKeys.contains(keyStr)) {
                colDomainKeys.add(keyStr);
                colDomains.add(colKeyMap);
            }
        }

        // 按列轴字段值的自然排序确定 first/last（与 SQL FIRST_VALUE/LAST_VALUE ORDER BY 语义保持一致）
        colDomains.sort((a, b) -> {
            for (String col : colFields) {
                Object va = a.get(col);
                Object vb = b.get(col);
                int cmp = compareValues(va, vb);
                if (cmp != 0) return cmp;
            }
            return 0;
        });

        if (colDomains.isEmpty()) {
            // 没有有效数据列
            for (PivotMetricItem brMetric : brMetrics) {
                for (Map<String, Object> row : resultSet) {
                    row.put(brMetric.getName(), null);
                }
            }
            return resultSet;
        }

        // 2. 按 rowFields 将行分组，以便快速查找组内的 baseline 单元格
        // key: rowFields 对应的值组合
        Map<String, Map<String, Map<String, Object>>> groupedByRow = new HashMap<>();
        for (Map<String, Object> row : resultSet) {
            if (isSubtotalRow(row)) continue;

            String rowKey = buildKey(row, rowFields);
            String colKey = buildKey(row, colFields);

            groupedByRow.computeIfAbsent(rowKey, k -> new HashMap<>()).put(colKey, row);
        }

        // 3. 为每个 rowKey 找出 baseline 行
        for (PivotMetricItem brMetric : brMetrics) {
            String ofMetric = brMetric.getOf();
            String baselineType = brMetric.getBaseline(); // "first" or "last"

            Map<String, Object> firstColKeyMap = colDomains.get(0);
            Map<String, Object> lastColKeyMap = colDomains.get(colDomains.size() - 1);

            String firstColKey = buildKey(firstColKeyMap, colFields);
            String lastColKey = buildKey(lastColKeyMap, colFields);

            String targetColKey = "last".equals(baselineType) ? lastColKey : firstColKey;

            logger.debug("[Pivot] Phase 2.9: Computing baselineRatio '{}' of '{}', baseline={}, colDomains={}",
                    brMetric.getName(), ofMetric, baselineType, colDomains.size());

            for (Map<String, Object> row : resultSet) {
                if (isSubtotalRow(row)) {
                    row.put(brMetric.getName(), null);
                    continue;
                }

                Object currentVal = row.get(ofMetric);
                if (!(currentVal instanceof Number)) {
                    row.put(brMetric.getName(), null);
                    continue;
                }

                String rowKey = buildKey(row, rowFields);
                Map<String, Map<String, Object>> colMap = groupedByRow.get(rowKey);

                Map<String, Object> baselineRow = colMap != null ? colMap.get(targetColKey) : null;
                if (baselineRow == null) {
                    // 该行分组下缺失 baseline 列数据
                    row.put(brMetric.getName(), null);
                    continue;
                }

                Object baselineValObj = baselineRow.get(ofMetric);
                if (!(baselineValObj instanceof Number)) {
                    row.put(brMetric.getName(), null);
                    continue;
                }

                double baselineVal = ((Number) baselineValObj).doubleValue();
                if (baselineVal == 0.0) {
                    row.put(brMetric.getName(), null);
                } else {
                    double ratio = ((Number) currentVal).doubleValue() / baselineVal;
                    row.put(brMetric.getName(), ratio);
                }
            }
        }

        return resultSet;
    }

    /**
     * 判断是否为包含小计或总计的元数据行
     */
    private static boolean isSubtotalRow(Map<String, Object> row) {
        return row.containsKey(SYS_META_KEY);
    }

    /**
     * 提取指定字段列表的值构造用于 HashMap 的 Key
     */
    private static String buildKey(Map<String, Object> row, List<String> fields) {
        if (fields.isEmpty()) return "ALL";
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            Object val = row.get(field);
            sb.append(val != null ? val.toString() : "null").append("|");
        }
        return sb.toString();
    }

    /**
     * 自然排序比较两个列值
     *
     * <p>规则：null 排在最后；Number 按 double 比较；Comparable 使用 compareTo；兜底 toString 比较。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;  // null 排最后
        if (b == null) return -1;

        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof Comparable && b instanceof Comparable && a.getClass().equals(b.getClass())) {
            return ((Comparable) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    /**
     * 校验 baselineRatio 关联的原生度量是否支持基准占比计算
     */
    public static void validateAdditivity(PivotRequest pivot, QueryModel qm) {
        List<PivotMetricItem> brMetrics = pivot.getBaselineRatioMetrics();
        if (brMetrics.isEmpty() || qm == null) return;

        Set<DbAggregation> ADDITIVE = Set.of(
                DbAggregation.SUM, DbAggregation.COUNT,
                DbAggregation.MIN, DbAggregation.MAX);

        for (PivotMetricItem br : brMetrics) {
            DbAggregation agg = MetricAdditivityAnalyzer.resolveAggregation(br.getOf(), qm);
            if (agg == null) {
                logger.debug("[Pivot] baselineRatio '{}' of='{}' aggregation unknown, allowing",
                        br.getName(), br.getOf());
                continue;
            }

            if (!ADDITIVE.contains(agg)) {
                throw new IllegalArgumentException(
                        "baselineRatio 派生指标 '" + br.getName() + "' 依赖的度量 '" + br.getOf() +
                        "' 聚合类型为 " + agg + "，不可加，不支持进行基准计算。");
            }
        }
    }
}
