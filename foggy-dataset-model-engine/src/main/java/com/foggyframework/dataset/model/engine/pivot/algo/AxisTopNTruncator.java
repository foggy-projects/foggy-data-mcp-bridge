package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.spi.DbAggregation;

import java.math.BigDecimal;

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
     * Window additive member domains, retaining every cell of a surviving member.
     * Ranking across other axis buckets uses the metric's declared reaggregation.
     * Non-additive/unknown rankings keep the existing fallback contract; their
     * parent value cannot be inferred from already aggregated cells.
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                 List<AxisField> axisFields,
                                                 Map<String, DbAggregation> aggregations) {
        if (axisFields == null || resultSet.isEmpty()) return resultSet;
        Set<String> axisNames = axisFields.stream().map(AxisField::getField).collect(Collectors.toSet());
        for (AxisField field : axisFields) {
            if (field.getLimit() == null || field.getLimit() <= 0 || field.getOrderBy() == null) continue;
            for (String spec : field.getOrderBy()) {
                String name = spec.startsWith("-") ? spec.substring(1) : spec;
                if (!axisNames.contains(name) && !aggregations.containsKey(name)) return apply(resultSet, axisFields);
            }
        }
        List<Map<String, Object>> current = new ArrayList<>(resultSet);
        List<String> parents = new ArrayList<>();
        for (AxisField field : axisFields) {
            if (field.getLimit() != null && field.getLimit() > 0) {
                Map<List<Object>, Map<Object, List<Map<String, Object>>>> partitions = new LinkedHashMap<>();
                for (var row : current) {
                    List<Object> key = parents.stream().map(row::get).collect(Collectors.toList());
                    partitions.computeIfAbsent(key, k -> new LinkedHashMap<>())
                            .computeIfAbsent(row.get(field.getField()), k -> new ArrayList<>()).add(row);
                }
                List<Map<String, Object>> selected = new ArrayList<>();
                for (var members : partitions.values()) {
                    List<Map<String, Object>> ranking = new ArrayList<>();
                    for (var cells : members.values()) {
                        Map<String, Object> member = new LinkedHashMap<>(cells.get(0));
                        for (var metric : aggregations.entrySet()) {
                            List<Number> values = cells.stream().map(r -> r.get(metric.getKey()))
                                    .filter(Number.class::isInstance).map(Number.class::cast).toList();
                            if (values.isEmpty()) member.put(metric.getKey(), null);
                            else member.put(metric.getKey(), switch (metric.getValue()) {
                                case MIN -> values.stream().min(AxisTopNTruncator::compareValues).orElseThrow();
                                case MAX -> values.stream().max(AxisTopNTruncator::compareValues).orElseThrow();
                                default -> values.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
                            });
                        }
                        ranking.add(member);
                    }
                    if (field.getOrderBy() != null && !field.getOrderBy().isEmpty()) ranking.sort(buildComparator(field.getOrderBy()));
                    int start = Math.min(field.getEffectiveOffset(), ranking.size());
                    int end = (int) Math.min((long) start + field.getLimit(), ranking.size());
                    for (var member : ranking.subList(start, end)) selected.addAll(members.get(member.get(field.getField())));
                }
                current = selected;
            }
            parents.add(field.getField());
        }
        return current;
    }

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
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }

        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }

        return a.toString().compareTo(b.toString());
    }
}
