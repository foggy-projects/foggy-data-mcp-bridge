package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轴级聚合后过滤 (Axis Having)
 *
 * <p>执行位置：基础聚合完成之后，orderBy/limit 之前。</p>
 *
 * <p>语义上对应 MDX {@code Filter(Set, [Measures].[X] > value)} 的高频用法。
 * Having 过滤后，不满足条件的成员及其下游子成员被整行删除。</p>
 */
public class AxisHavingFilter {

    /**
     * 对结果集应用轴级 Having 过滤
     *
     * @param resultSet  当前结果集
     * @param axisFields 轴字段列表（可能携带 having 条件）
     * @param metrics    度量字段名列表（用于定位度量值）
     * @return 过滤后的结果集
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<AxisField> axisFields,
                                                   List<String> metrics) {
        if (axisFields == null || resultSet.isEmpty()) {
            return resultSet;
        }

        List<Map<String, Object>> filtered = new ArrayList<>(resultSet);

        for (int i = 0; i < axisFields.size(); i++) {
            AxisField axisField = axisFields.get(i);
            if (axisField.getHaving() == null || axisField.getHaving().isEmpty()) {
                continue;
            }

            // 收集需要淘汰的成员值
            List<String> tupleFields = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                tupleFields.add(axisFields.get(j).getField());
            }
            Set<List<Object>> eliminatedTuples = new HashSet<>();

            // 按当前轴的完整父子 tuple 分组，避免同名子成员跨父级相互影响。
            Map<List<Object>, List<Map<String, Object>>> groups = filtered.stream()
                    .collect(Collectors.groupingBy(
                            row -> tupleKey(row, tupleFields),
                            LinkedHashMap::new,
                            Collectors.toList()));

            for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : groups.entrySet()) {
                // 用该成员下所有行的度量值总和来判断
                // （简化实现：如果是聚合后结果，每个成员通常只有一行对应该度量）
                boolean passAll = true;
                for (MetricFilter filter : axisField.getHaving()) {
                    Number aggregatedValue = aggregateMetricForMember(entry.getValue(), filter.getMetric());
                    if (!filter.evaluate(aggregatedValue)) {
                        passAll = false;
                        break;
                    }
                }
                if (!passAll) {
                    eliminatedTuples.add(entry.getKey());
                }
            }

            // 删除不满足条件的成员的所有行
            if (!eliminatedTuples.isEmpty()) {
                filtered = filtered.stream()
                        .filter(row -> !eliminatedTuples.contains(tupleKey(row, tupleFields)))
                        .collect(Collectors.toList());
            }
        }

        return filtered;
    }

    private static List<Object> tupleKey(Map<String, Object> row, List<String> fields) {
        List<Object> key = new ArrayList<>(fields.size());
        for (String field : fields) {
            key.add(row.getOrDefault(field, "__null__"));
        }
        return key;
    }

    /**
     * 聚合某个成员下的度量值（对多行取 SUM）
     */
    private static Number aggregateMetricForMember(List<Map<String, Object>> rows, String metricName) {
        double sum = 0;
        boolean hasValue = false;
        for (Map<String, Object> row : rows) {
            Object val = row.get(metricName);
            if (val instanceof Number) {
                sum += ((Number) val).doubleValue();
                hasValue = true;
            }
        }
        return hasValue ? sum : null;
    }
}
