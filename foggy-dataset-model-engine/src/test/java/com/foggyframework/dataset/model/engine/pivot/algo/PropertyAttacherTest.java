package com.foggyframework.dataset.model.engine.pivot.algo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PropertyAttacher 单元测试
 *
 * <p>验证 lookup table 构建和属性贴合逻辑。</p>
 */
@DisplayName("PropertyAttacher 属性贴合")
class PropertyAttacherTest {

    private static final Logger log = LoggerFactory.getLogger(PropertyAttacherTest.class);

    @Test
    @DisplayName("构建 lookup table")
    void testBuildLookupTable() {
        List<Map<String, Object>> lookupRows = List.of(
                Map.of("product$id", 1, "product$brand", "BrandA"),
                Map.of("product$id", 2, "product$brand", "BrandB"),
                Map.of("product$id", 3, "product$brand", "BrandC")
        );

        Map<Object, Map<String, Object>> table = PropertyAttacher.buildLookupTable(
                lookupRows, "product$id", List.of("product$brand"));

        assertEquals(3, table.size());
        assertEquals("BrandA", table.get(1).get("product$brand"));
        assertEquals("BrandB", table.get(2).get("product$brand"));
        assertEquals("BrandC", table.get(3).get("product$brand"));
    }

    @Test
    @DisplayName("贴合属性到结果集")
    void testAttachProperties() {
        // 准备主结果集
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(new LinkedHashMap<>(Map.of("product$id", 1, "salesAmount", 1000)));
        resultSet.add(new LinkedHashMap<>(Map.of("product$id", 2, "salesAmount", 2000)));
        resultSet.add(new LinkedHashMap<>(Map.of("product$id", 3, "salesAmount", 3000)));

        // 准备 resolved properties
        List<PropertyResolver.ResolvedProperty> resolvedProps = List.of(
                new PropertyResolver.ResolvedProperty("product", "brand", "product$id", "product$brand")
        );

        // 准备 lookup tables
        Map<String, Map<Object, Map<String, Object>>> lookupTables = new LinkedHashMap<>();
        Map<Object, Map<String, Object>> productLookup = new LinkedHashMap<>();
        productLookup.put(1, Map.of("product$brand", "BrandA"));
        productLookup.put(2, Map.of("product$brand", "BrandB"));
        productLookup.put(3, Map.of("product$brand", "BrandC"));
        lookupTables.put("product", productLookup);

        // 执行贴合
        PropertyAttacher.attach(resultSet, resolvedProps, lookupTables);

        // 断言
        assertEquals("BrandA", resultSet.get(0).get("product$brand"));
        assertEquals("BrandB", resultSet.get(1).get("product$brand"));
        assertEquals("BrandC", resultSet.get(2).get("product$brand"));
        log.info("贴合后第一行: {}", resultSet.get(0));
    }

    @Test
    @DisplayName("贴合时 key 不存在不报错（优雅降级）")
    void testAttachWithMissingKey() {
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(new LinkedHashMap<>(Map.of("product$id", 999, "salesAmount", 500)));

        List<PropertyResolver.ResolvedProperty> resolvedProps = List.of(
                new PropertyResolver.ResolvedProperty("product", "brand", "product$id", "product$brand")
        );

        Map<String, Map<Object, Map<String, Object>>> lookupTables = new LinkedHashMap<>();
        Map<Object, Map<String, Object>> productLookup = new LinkedHashMap<>();
        productLookup.put(1, Map.of("product$brand", "BrandA"));
        lookupTables.put("product", productLookup);

        // 不应抛出异常，只是没有贴合到
        assertDoesNotThrow(() -> PropertyAttacher.attach(resultSet, resolvedProps, lookupTables));

        // product$brand 不应出现
        assertFalse(resultSet.get(0).containsKey("product$brand"));
    }

    @Test
    @DisplayName("空 resolvedProps 不执行贴合")
    void testAttachWithEmptyProps() {
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(new LinkedHashMap<>(Map.of("product$id", 1, "salesAmount", 1000)));

        assertDoesNotThrow(() -> PropertyAttacher.attach(
                resultSet, Collections.emptyList(), Collections.emptyMap()));

        // 结果集不被修改
        assertEquals(2, resultSet.get(0).size()); // product$id + salesAmount
    }
}
