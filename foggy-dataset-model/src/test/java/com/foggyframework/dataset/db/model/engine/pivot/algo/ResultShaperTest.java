package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.engine.pivot.PivotResult;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotLayout;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResultShaper 单元测试
 *
 * <p>验证 tree / grid / flat 三种输出格式的整形正确性。</p>
 */
@DisplayName("ResultShaper 结果整形测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultShaperTest {

    @Test
    @Order(1)
    @DisplayName("tree 格式：两层行轴生成嵌套树")
    void testTreeFormat() {
        List<Map<String, Object>> data = List.of(
                makeRow("华东", "上海", "1月", 100),
                makeRow("华东", "上海", "2月", 150),
                makeRow("华东", "杭州", "1月", 200),
                makeRow("华北", "北京", "1月", 300)
        );

        PivotRequest pivot = buildPivot("tree");
        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("region", "city"), List.of("month"), List.of("salesAmount"));

        assertEquals("tree", result.getFormat());
        assertNotNull(result.getTreeData());

        // 应有2个根节点：华东、华北
        assertEquals(2, result.getTreeData().size());

        PivotResult.TreeNode huadong = result.getTreeData().stream()
                .filter(n -> "华东".equals(n.getNode().get("region")))
                .findFirst()
                .orElse(null);

        assertNotNull(huadong, "应存在华东节点");
        assertNotNull(huadong.getChildren(), "华东应有子节点");
        assertEquals(2, huadong.getChildren().size(), "华东应有上海和杭州两个子节点");
    }

    @Test
    @Order(2)
    @DisplayName("grid 格式：生成分离的行头、列头、矩阵")
    void testGridFormat() {
        List<Map<String, Object>> data = List.of(
                makeSimpleRow("上海", "1月", 100),
                makeSimpleRow("上海", "2月", 200),
                makeSimpleRow("北京", "1月", 300),
                makeSimpleRow("北京", "2月", 400)
        );

        PivotRequest pivot = buildSimplePivot("grid");
        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("city"), List.of("month"), List.of("salesAmount"));

        assertEquals("grid", result.getFormat());
        assertNotNull(result.getRowHeaders());
        assertNotNull(result.getColumnHeaders());
        assertNotNull(result.getCells());

        // 2 行 × 2 列（每月1个度量）
        assertEquals(2, result.getRowHeaders().size(), "应有2个行头");
        assertEquals(2, result.getColumnHeaders().size(), "应有2个列头（2月份×1度量）");
        assertEquals(2, result.getCells().size(), "应有2行数据");
        assertEquals(2, result.getCells().get(0).size(), "每行应有2个单元格");
    }

    @Test
    @Order(3)
    @DisplayName("flat 格式：透传原始数据")
    void testFlatFormat() {
        List<Map<String, Object>> data = List.of(
                makeSimpleRow("上海", "1月", 100),
                makeSimpleRow("北京", "1月", 200)
        );

        PivotRequest pivot = buildSimplePivot("flat");
        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("city"), List.of("month"), List.of("salesAmount"));

        assertEquals("flat", result.getFormat());
        assertNotNull(result.getFlatData());
        assertEquals(2, result.getFlatData().size());
        assertEquals(data, result.getFlatData());
    }

    @Test
    @Order(4)
    @DisplayName("grid 格式：多度量 → 列头包含度量名")
    void testGridMultipleMetrics() {
        List<Map<String, Object>> data = List.of(
                makeMultiMetricRow("上海", "1月", 100, 10),
                makeMultiMetricRow("上海", "2月", 200, 20)
        );

        PivotRequest pivot = new PivotRequest();
        AxisField row = new AxisField();
        row.setField("city");
        pivot.setRows(List.of(row));
        AxisField col = new AxisField();
        col.setField("month");
        pivot.setColumns(List.of(col));
        pivot.setMetrics(List.of("salesAmount", "orderCount"));
        pivot.setOutputFormat("grid");

        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("city"), List.of("month"), List.of("salesAmount", "orderCount"));

        assertEquals("grid", result.getFormat());
        // 2月 × 2度量 = 4列头
        assertEquals(4, result.getColumnHeaders().size(), "2月份 × 2度量 = 4列头");

        // 每个列头应包含 metric 字段
        assertTrue(result.getColumnHeaders().stream()
                        .allMatch(ch -> ch.containsKey("metric")),
                "每个列头应包含 metric 字段名");
    }

    @Test
    @Order(5)
    @DisplayName("tree 格式：叶子节点应包含 cells")
    void testTreeLeafCells() {
        List<Map<String, Object>> data = List.of(
                makeSimpleRow("上海", "1月", 100),
                makeSimpleRow("上海", "2月", 200)
        );

        PivotRequest pivot = buildSimplePivot("tree");
        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("city"), List.of("month"), List.of("salesAmount"));

        assertEquals(1, result.getTreeData().size());
        PivotResult.TreeNode node = result.getTreeData().get(0);
        assertEquals("上海", node.getNode().get("city"));
        assertNotNull(node.getCells(), "叶子节点应包含 cells");
        assertFalse(node.getCells().isEmpty(), "cells 不应为空");
    }

    @Test
    @Order(6)
    @DisplayName("layout 信息传递")
    void testLayoutPassthrough() {
        List<Map<String, Object>> data = List.of(makeSimpleRow("上海", "1月", 100));

        PivotRequest pivot = buildSimplePivot("flat");
        PivotLayout layout = new PivotLayout();
        layout.setMetricPlacement("rows");
        pivot.setLayout(layout);

        PivotResult result = ResultShaper.shape(
                data, pivot,
                List.of("city"), List.of("month"), List.of("salesAmount"));

        assertNotNull(result.getLayout());
        assertEquals("rows", result.getLayout().get("metricPlacement"));
    }

    // ========== 辅助方法 ==========

    private PivotRequest buildPivot(String format) {
        PivotRequest pivot = new PivotRequest();
        AxisField region = new AxisField();
        region.setField("region");
        AxisField city = new AxisField();
        city.setField("city");
        pivot.setRows(List.of(region, city));
        AxisField month = new AxisField();
        month.setField("month");
        pivot.setColumns(List.of(month));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat(format);
        return pivot;
    }

    private PivotRequest buildSimplePivot(String format) {
        PivotRequest pivot = new PivotRequest();
        AxisField city = new AxisField();
        city.setField("city");
        pivot.setRows(List.of(city));
        AxisField month = new AxisField();
        month.setField("month");
        pivot.setColumns(List.of(month));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat(format);
        return pivot;
    }

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

    private Map<String, Object> makeMultiMetricRow(String city, String month, int sales, int orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("city", city);
        row.put("month", month);
        row.put("salesAmount", sales);
        row.put("orderCount", orders);
        return row;
    }
}
