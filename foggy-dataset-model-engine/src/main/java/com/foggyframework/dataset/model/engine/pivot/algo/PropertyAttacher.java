package com.foggyframework.dataset.model.engine.pivot.algo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pivot Properties 后置贴合器
 *
 * <p>在 Phase 2 内存加工完成后，利用预构建的 lookup table
 * 将维度属性值逐行贴合到结果集中。</p>
 *
 * <p>算法：O(N) 遍历 + O(1) 哈希查找，对内存结果集无副作用扩大。</p>
 */
public class PropertyAttacher {

    private static final Logger logger = LoggerFactory.getLogger(PropertyAttacher.class);

    /**
     * 从辅助查询结果构建 lookup table
     *
     * <p>将 SELECT DISTINCT dim$id, dim$prop1, dim$prop2 ... 的结果
     * 转换为 Map&lt;dimKeyValue, Map&lt;propFieldName, propValue&gt;&gt;</p>
     *
     * @param lookupRows    辅助查询返回的行
     * @param keyField      lookup key 字段名, e.g. "product$id"
     * @param propFields    要提取的 property 字段名列表, e.g. ["product$categoryName"]
     * @return lookup table
     */
    public static Map<Object, Map<String, Object>> buildLookupTable(
            List<Map<String, Object>> lookupRows,
            String keyField,
            List<String> propFields) {

        Map<Object, Map<String, Object>> table = new LinkedHashMap<>();

        for (Map<String, Object> row : lookupRows) {
            Object keyValue = row.get(keyField);
            if (keyValue == null) continue;

            Map<String, Object> props = new LinkedHashMap<>();
            for (String propField : propFields) {
                props.put(propField, row.get(propField));
            }
            table.put(keyValue, props);
        }

        logger.debug("[PropertyAttacher] Built lookup table: {} entries, key={}, props={}",
                table.size(), keyField, propFields);
        return table;
    }

    /**
     * 将 properties 贴合到结果集
     *
     * @param resultSet       Phase 2 完成后的主结果集
     * @param resolvedProps   已验证的属性列表
     * @param lookupTables    按维度名索引的 lookup table, key=dimName
     */
    public static void attach(List<Map<String, Object>> resultSet,
                               List<PropertyResolver.ResolvedProperty> resolvedProps,
                               Map<String, Map<Object, Map<String, Object>>> lookupTables) {

        if (resolvedProps == null || resolvedProps.isEmpty() || lookupTables.isEmpty()) {
            return;
        }

        // 按 lookupKeyField 分组 resolvedProps
        Map<String, List<PropertyResolver.ResolvedProperty>> byKey = resolvedProps.stream()
                .collect(Collectors.groupingBy(PropertyResolver.ResolvedProperty::getLookupKeyField));

        int attachedCount = 0;

        for (Map<String, Object> row : resultSet) {
            for (Map.Entry<String, List<PropertyResolver.ResolvedProperty>> entry : byKey.entrySet()) {
                String keyField = entry.getKey();
                Object keyValue = row.get(keyField);

                if (keyValue == null) continue;

                // 找到对应维度名
                String dimName = entry.getValue().get(0).getDimensionName();
                Map<Object, Map<String, Object>> lookup = lookupTables.get(dimName);
                if (lookup == null) continue;

                Map<String, Object> propValues = lookup.get(keyValue);
                if (propValues != null) {
                    row.putAll(propValues);
                    attachedCount++;
                }
            }
        }

        logger.debug("[PropertyAttacher] Attached properties to {} rows", attachedCount);
    }
}
