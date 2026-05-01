package com.foggyframework.dataset.db.model.engine.pivot.algo;

import java.util.*;

/**
 * 骨架补全算法 (CrossJoin Cartesian Fill)
 *
 * <p>当 options.crossjoin == true 时触发。SQL 返回的结果只包含有事实数据的格子（稠密集）。
 * 财报需求要求即使华东区3月无销量，也要输出一行/列。</p>
 *
 * <p>算法：对行域 × 列域做笛卡尔积，缺失的坐标点度量填充 null。</p>
 */
public class CrossJoinFiller {

    /**
     * 执行骨架补全
     *
     * @param resultSet 当前结果集
     * @param rowFields 行轴字段名列表
     * @param colFields 列轴字段名列表
     * @param metrics   度量字段名列表
     * @param rowDomain 行域成员集合（已提取）
     * @param colDomain 列域成员集合（已提取）
     * @return 补全后的结果集
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<String> rowFields,
                                                   List<String> colFields,
                                                   List<String> metrics,
                                                   Set<List<Object>> rowDomain,
                                                   Set<List<Object>> colDomain) {
        if (rowDomain.isEmpty() || colDomain.isEmpty()) {
            return resultSet;
        }

        // 构建现有坐标索引：key = rowTuple + colTuple
        Map<List<Object>, Map<String, Object>> existingIndex = new HashMap<>();
        for (Map<String, Object> row : resultSet) {
            List<Object> fullKey = new ArrayList<>();
            for (String rf : rowFields) fullKey.add(row.get(rf));
            for (String cf : colFields) fullKey.add(row.get(cf));
            existingIndex.put(fullKey, row);
        }

        // 笛卡尔积 + 填充
        List<Map<String, Object>> filled = new ArrayList<>();

        for (List<Object> rowTuple : rowDomain) {
            for (List<Object> colTuple : colDomain) {
                List<Object> fullKey = new ArrayList<>(rowTuple);
                fullKey.addAll(colTuple);

                Map<String, Object> existing = existingIndex.get(fullKey);
                if (existing != null) {
                    filled.add(existing);
                } else {
                    // 构造空行
                    Map<String, Object> emptyRow = new LinkedHashMap<>();
                    for (int i = 0; i < rowFields.size(); i++) {
                        emptyRow.put(rowFields.get(i), rowTuple.get(i));
                    }
                    for (int i = 0; i < colFields.size(); i++) {
                        emptyRow.put(colFields.get(i), colTuple.get(i));
                    }
                    for (String metric : metrics) {
                        emptyRow.put(metric, null);
                    }
                    filled.add(emptyRow);
                }
            }
        }

        return filled;
    }
}
