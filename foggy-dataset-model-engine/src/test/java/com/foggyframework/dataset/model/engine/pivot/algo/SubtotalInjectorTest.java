package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubtotalInjector 单元测试
 *
 * <p>验证小计/总计注入算法的正确性。</p>
 */
@DisplayName("SubtotalInjector 小计注入测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubtotalInjectorTest {

    @Test
    @Order(1)
    @DisplayName("行轴小计：两级行轴注入父级小计")
    void testRowSubtotals() {
        // 华东-上海=100, 华东-杭州=200, 华北-北京=300
        List<Map<String, Object>> data = new ArrayList<>(List.of(
                makeRow("华东", "上海", "1月", 100),
                makeRow("华东", "杭州", "1月", 200),
                makeRow("华北", "北京", "1月", 300)
        ));

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);

        List<Map<String, Object>> result = SubtotalInjector.apply(
                data, List.of("region", "city"), List.of("month"), List.of("salesAmount"), options);

        // 原始 3 行 + 华东小计(300) + 华北小计(300)
        assertTrue(result.size() > 3, "应注入小计行");

        // 找到华东的小计行
        List<Map<String, Object>> subtotalRows = result.stream()
                .filter(r -> "华东".equals(r.get("region")) && "ALL".equals(r.get("city")))
                .collect(Collectors.toList());

        assertFalse(subtotalRows.isEmpty(), "应存在华东的小计行");
        Map<String, Object> huadongSubtotal = subtotalRows.get(0);
        assertEquals(300.0, ((Number) huadongSubtotal.get("salesAmount")).doubleValue(), 0.01,
                "华东小计应为 100+200=300");

        // 验证小计标记
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) huadongSubtotal.get("_sys_meta");
        assertNotNull(meta, "小计行应携带 _sys_meta");
        assertEquals(true, meta.get("isRowSubtotal"));
    }

    @Test
    @Order(2)
    @DisplayName("总计行注入")
    void testGrandTotal() {
        List<Map<String, Object>> data = new ArrayList<>(List.of(
                makeRow("华东", "上海", "1月", 100),
                makeRow("华北", "北京", "1月", 300)
        ));

        PivotOptions options = new PivotOptions();
        options.setGrandTotal(true);

        List<Map<String, Object>> result = SubtotalInjector.apply(
                data, List.of("region", "city"), List.of("month"), List.of("salesAmount"), options);

        // 查找总计行
        List<Map<String, Object>> grandTotalRows = result.stream()
                .filter(r -> "GRAND_TOTAL".equals(r.get("region")))
                .collect(Collectors.toList());

        assertFalse(grandTotalRows.isEmpty(), "应存在总计行");
        Map<String, Object> grandTotal = grandTotalRows.get(0);
        assertEquals(400.0, ((Number) grandTotal.get("salesAmount")).doubleValue(), 0.01,
                "总计应为 100+300=400");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) grandTotal.get("_sys_meta");
        assertEquals(true, meta.get("isGrandTotal"));
    }

    @Test
    @Order(3)
    @DisplayName("无小计配置 → 原样返回")
    void testNoSubtotals() {
        List<Map<String, Object>> data = new ArrayList<>(List.of(
                makeRow("华东", "上海", "1月", 100)
        ));

        PivotOptions options = new PivotOptions(); // 全部 false

        List<Map<String, Object>> result = SubtotalInjector.apply(
                data, List.of("region", "city"), List.of("month"), List.of("salesAmount"), options);

        assertEquals(1, result.size(), "无小计配置应不注入任何行");
    }

    @Test
    @Order(4)
    @DisplayName("单层行轴 → 不注入行小计（因为无父级）")
    void testSingleLevelNoRowSubtotal() {
        List<Map<String, Object>> data = new ArrayList<>(List.of(
                makeSimpleRow("上海", "1月", 100),
                makeSimpleRow("北京", "1月", 200)
        ));

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);

        List<Map<String, Object>> result = SubtotalInjector.apply(
                data, List.of("city"), List.of("month"), List.of("salesAmount"), options);

        // 单层行轴，rowSubtotals 不应触发（因为 rowFields.size() <= 1）
        assertEquals(2, result.size(), "单层行轴不应注入行小计");
    }

    @Test
    @Order(5)
    @DisplayName("含 null 度量值 → 正确聚合")
    void testNullMetricValues() {
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row1 = makeRow("华东", "上海", "1月", 100);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("region", "华东");
        row2.put("city", "杭州");
        row2.put("month", "1月");
        row2.put("salesAmount", null); // null 度量
        data.add(row1);
        data.add(row2);

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);

        List<Map<String, Object>> result = SubtotalInjector.apply(
                data, List.of("region", "city"), List.of("month"), List.of("salesAmount"), options);

        // 华东小计应为 100（只有上海有值）
        List<Map<String, Object>> subtotals = result.stream()
                .filter(r -> "华东".equals(r.get("region")) && "ALL".equals(r.get("city")))
                .collect(Collectors.toList());

        assertFalse(subtotals.isEmpty());
        assertEquals(100.0, ((Number) subtotals.get(0).get("salesAmount")).doubleValue(), 0.01);
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> makeRow(String region, String city, String month, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("month", month);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeSimpleRow(String city, String month, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("city", city);
        row.put("month", month);
        row.put("salesAmount", sales);
        return row;
    }
}
