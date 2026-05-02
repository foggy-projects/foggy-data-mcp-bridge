package com.foggyframework.dataset.db.model.engine.pivot.algo;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossJoinFiller 单元测试
 *
 * <p>验证笛卡尔积骨架补全算法的正确性。</p>
 */
@DisplayName("CrossJoinFiller 骨架补全测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossJoinFillerTest {

    @Test
    @Order(1)
    @DisplayName("有缺失坐标 → 补全空行")
    void testFillMissingCells() {
        // 华东×1月=100, 华东×2月=200, 华北×1月=300
        // 缺失: 华北×2月
        List<Map<String, Object>> data = List.of(
                makeRow("华东", "1月", 100),
                makeRow("华东", "2月", 200),
                makeRow("华北", "1月", 300)
        );

        Set<List<Object>> rowDomain = Set.of(List.of("华东"), List.of("华北"));
        Set<List<Object>> colDomain = Set.of(List.of("1月"), List.of("2月"));

        List<Map<String, Object>> result = CrossJoinFiller.apply(
                data, List.of("region"), List.of("month"), List.of("salesAmount"),
                rowDomain, colDomain);

        // 2 rows × 2 cols = 4 cells
        assertEquals(4, result.size());

        // 找到补全的 华北×2月
        Map<String, Object> filled = result.stream()
                .filter(r -> "华北".equals(r.get("region")) && "2月".equals(r.get("month")))
                .findFirst()
                .orElse(null);

        assertNotNull(filled, "应补全 华北×2月 的空行");
        assertNull(filled.get("salesAmount"), "补全行的度量应为 null");
    }

    @Test
    @Order(2)
    @DisplayName("无缺失 → 保留原始数据")
    void testNoMissingCells() {
        List<Map<String, Object>> data = List.of(
                makeRow("华东", "1月", 100),
                makeRow("华东", "2月", 200),
                makeRow("华北", "1月", 300),
                makeRow("华北", "2月", 400)
        );

        Set<List<Object>> rowDomain = Set.of(List.of("华东"), List.of("华北"));
        Set<List<Object>> colDomain = Set.of(List.of("1月"), List.of("2月"));

        List<Map<String, Object>> result = CrossJoinFiller.apply(
                data, List.of("region"), List.of("month"), List.of("salesAmount"),
                rowDomain, colDomain);

        assertEquals(4, result.size());
        // 所有值都应保留
        assertTrue(result.stream().allMatch(r -> r.get("salesAmount") != null));
    }

    @Test
    @Order(3)
    @DisplayName("多维行轴 + 多维列轴 → 正确笛卡尔积")
    void testMultiDimensionCrossJoin() {
        // 行轴: region×city, 列轴: month
        List<Map<String, Object>> data = List.of(
                makeMultiRow("华东", "上海", "1月", 100),
                makeMultiRow("华北", "北京", "2月", 200)
        );

        Set<List<Object>> rowDomain = Set.of(
                List.of("华东", "上海"),
                List.of("华北", "北京")
        );
        Set<List<Object>> colDomain = Set.of(List.of("1月"), List.of("2月"));

        List<Map<String, Object>> result = CrossJoinFiller.apply(
                data, List.of("region", "city"), List.of("month"), List.of("salesAmount"),
                rowDomain, colDomain);

        // 2 行组合 × 2 列组合 = 4
        assertEquals(4, result.size());
    }

    @Test
    @Order(4)
    @DisplayName("空域 → 原样返回")
    void testEmptyDomain() {
        List<Map<String, Object>> data = List.of(makeRow("华东", "1月", 100));

        List<Map<String, Object>> result = CrossJoinFiller.apply(
                data, List.of("region"), List.of("month"), List.of("salesAmount"),
                Collections.emptySet(), Set.of(List.of("1月")));

        assertEquals(data.size(), result.size());
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> makeRow(String region, String month, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("month", month);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeMultiRow(String region, String city, String month, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("month", month);
        row.put("salesAmount", sales);
        return row;
    }
}
