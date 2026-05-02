package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;

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

        for (AxisField axisField : axisFields) {
            if (axisField.getHaving() == null || axisField.getHaving().isEmpty()) {
                continue;
            }

            // 收集需要淘汰的成员值
            String fieldName = axisField.getField();
            Set<Object> eliminatedMembers = new HashSet<>();

            // 按成员分组，检查每个成员是否通过所有 having 条件
            Map<Object, List<Map<String, Object>>> groups = filtered.stream()
                    .collect(Collectors.groupingBy(row -> row.getOrDefault(fieldName, "__null__")));

            for (Map.Entry<Object, List<Map<String, Object>>> entry : groups.entrySet()) {
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
                    eliminatedMembers.add(entry.getKey());
                }
            }

            // 删除不满足条件的成员的所有行
            if (!eliminatedMembers.isEmpty()) {
                filtered = filtered.stream()
                        .filter(row -> !eliminatedMembers.contains(row.getOrDefault(fieldName, "__null__")))
                        .collect(Collectors.toList());
            }
        }

        return filtered;
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
