package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轴向截断算法 (Axis TopN Truncation)
 *
 * <p>根据 AxisField 上的局部截断配置（limit + orderBy），在内存中进行分组排序淘汰。</p>
 *
 * <p>隐式分区逻辑：在 rows 数组中排在当前字段前面的所有字段自动成为分区键。
 * 即 limit 的语义是"在每个父级分组内保留前 N 个子成员"。</p>
 */
public class AxisTopNTruncator {

    /**
     * 对结果集应用轴向 TopN 截断
     *
     * @param resultSet  当前结果集
     * @param axisFields 轴字段列表
     * @return 截断后的结果集
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<AxisField> axisFields) {
        if (axisFields == null || resultSet.isEmpty()) {
            return resultSet;
        }

        List<Map<String, Object>> current = new ArrayList<>(resultSet);

        for (int i = 0; i < axisFields.size(); i++) {
            AxisField field = axisFields.get(i);
            if (field.getLimit() == null || field.getLimit() <= 0) {
                continue;
            }

            // 隐式父级分区键 = 排在当前字段前面的所有字段
            List<String> partitionKeys = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                partitionKeys.add(axisFields.get(j).getField());
            }

            current = truncateByPartition(current, field, partitionKeys);
        }

        return current;
    }

    /**
     * 按分区键分组，在每个分组内按 orderBy 排序后取 Top N
     */
    private static List<Map<String, Object>> truncateByPartition(
            List<Map<String, Object>> resultSet,
            AxisField field,
            List<String> partitionKeys) {

        int offset = field.getEffectiveOffset();
        int limit = field.getLimit();
        List<String> orderBySpecs = field.getOrderBy();

        // 按分区键分组
        Map<List<Object>, List<Map<String, Object>>> partitions = resultSet.stream()
                .collect(Collectors.groupingBy(
                        row -> partitionKeys.stream()
                                .map(k -> row.getOrDefault(k, "__null__"))
                                .collect(Collectors.toList()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();

        for (List<Map<String, Object>> partition : partitions.values()) {
            // 排序
            if (orderBySpecs != null && !orderBySpecs.isEmpty()) {
                partition.sort(buildComparator(orderBySpecs));
            }
            // 窗口截断：start/offset 与 limit 一起作用于每个隐式父级分区。
            if (offset >= partition.size()) {
                continue;
            }
            int end = Math.min(offset + limit, partition.size());
            result.addAll(partition.subList(offset, end));
        }

        return result;
    }

    /**
     * 构建排序比较器
     *
     * @param orderBySpecs 排序规则列表，负号前缀表示降序
     */
    private static Comparator<Map<String, Object>> buildComparator(List<String> orderBySpecs) {
        Comparator<Map<String, Object>> comparator = null;

        for (String spec : orderBySpecs) {
            boolean desc = spec.startsWith("-");
            String fieldName = desc ? spec.substring(1) : spec;

            Comparator<Map<String, Object>> fieldComparator = (a, b) -> {
                Object va = a.get(fieldName);
                Object vb = b.get(fieldName);
                return compareValues(va, vb);
            };

            if (desc) {
                fieldComparator = fieldComparator.reversed();
            }

            comparator = (comparator == null) ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        return comparator != null ? comparator : (a, b) -> 0;
    }

    /**
     * 通用值比较（null-safe，数值优先）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }

        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }

        return a.toString().compareTo(b.toString());
    }
}
