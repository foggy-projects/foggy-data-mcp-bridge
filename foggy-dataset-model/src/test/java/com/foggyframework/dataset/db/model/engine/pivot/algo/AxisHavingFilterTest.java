package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AxisHavingFilter 单元测试
 *
 * <p>验证轴级聚合后过滤的正确性。</p>
 */
@DisplayName("AxisHavingFilter 轴级过滤测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AxisHavingFilterTest {

    @Test
    @Order(1)
    @DisplayName("无 Having 条件 → 原样返回")
    void testNoHaving() {
        List<Map<String, Object>> data = buildSalesData();

        AxisField field = new AxisField();
        field.setField("region");
        // 不设置 having

        List<Map<String, Object>> result = AxisHavingFilter.apply(data, List.of(field), List.of("salesAmount"));

        assertEquals(data.size(), result.size(), "无 having 条件应不做任何过滤");
    }

    @Test
    @Order(2)
    @DisplayName("Having > 阈值 → 过滤掉低于阈值的成员")
    void testHavingGreaterThan() {
        List<Map<String, Object>> data = buildSalesData();
        // 数据：华东=300, 华北=100, 华南=500

        AxisField field = new AxisField();
        field.setField("region");

        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">");
        filter.setValue(200);
        field.setHaving(List.of(filter));

        List<Map<String, Object>> result = AxisHavingFilter.apply(
                data, List.of(field), List.of("salesAmount"));

        // 华北(100) 应被过滤，剩余华东(300)和华南(500)
        // 华东有2行，华南有1行 = 3行
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(row -> "华北".equals(row.get("region"))),
                "华北应被过滤掉");
    }

    @Test
    @Order(3)
    @DisplayName("Having = 精确匹配")
    void testHavingEquals() {
        List<Map<String, Object>> data = buildSalesData();

        AxisField field = new AxisField();
        field.setField("region");

        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp("=");
        filter.setValue(500);
        field.setHaving(List.of(filter));

        List<Map<String, Object>> result = AxisHavingFilter.apply(
                data, List.of(field), List.of("salesAmount"));

        // 华南=500，只保留华南的行
        assertEquals(1, result.size());
        assertEquals("华南", result.get(0).get("region"));
    }

    @Test
    @Order(4)
    @DisplayName("多重 Having 条件 → AND 语义")
    void testMultipleHaving() {
        List<Map<String, Object>> data = buildSalesData();

        AxisField field = new AxisField();
        field.setField("region");

        MetricFilter filter1 = new MetricFilter();
        filter1.setMetric("salesAmount");
        filter1.setOp(">=");
        filter1.setValue(200);

        MetricFilter filter2 = new MetricFilter();
        filter2.setMetric("salesAmount");
        filter2.setOp("<");
        filter2.setValue(400);

        field.setHaving(List.of(filter1, filter2));

        List<Map<String, Object>> result = AxisHavingFilter.apply(
                data, List.of(field), List.of("salesAmount"));

        // 华东=300 满足 200<=x<400，华北=100 不满足 >=200，华南=500 不满足 <400
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(row -> "华东".equals(row.get("region"))));
    }

    @Test
    @Order(5)
    @DisplayName("空结果集 → 原样返回")
    void testEmptyResultSet() {
        List<Map<String, Object>> data = Collections.emptyList();
        AxisField field = new AxisField();
        field.setField("region");

        List<Map<String, Object>> result = AxisHavingFilter.apply(data, List.of(field), List.of("salesAmount"));
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("null axisFields → 原样返回")
    void testNullAxisFields() {
        List<Map<String, Object>> data = buildSalesData();

        List<Map<String, Object>> result = AxisHavingFilter.apply(data, null, List.of("salesAmount"));
        assertEquals(data.size(), result.size());
    }

    @Test
    @Order(7)
    @DisplayName("多层轴 Having 按父级 tuple 隔离同名子成员")
    void testChildHavingUsesParentTuple() {
        List<Map<String, Object>> data = List.of(
                makeRegionCityRow("华东", "核心城市", 500),
                makeRegionCityRow("华东", "共享城市", 300),
                makeRegionCityRow("华北", "共享城市", 50),
                makeRegionCityRow("华北", "边缘城市", 30)
        );

        AxisField region = new AxisField();
        region.setField("region");

        AxisField city = new AxisField();
        city.setField("city");
        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">=");
        filter.setValue(100);
        city.setHaving(List.of(filter));

        List<Map<String, Object>> result = AxisHavingFilter.apply(
                data, List.of(region, city), List.of("salesAmount"));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row ->
                "华东".equals(row.get("region")) && "共享城市".equals(row.get("city"))));
        assertTrue(result.stream().noneMatch(row ->
                "华北".equals(row.get("region")) && "共享城市".equals(row.get("city"))),
                "华北/共享城市 不应因为华东同名城市达标而被保留");
    }

    // ========== 测试数据 ==========

    /**
     * 构建测试销售数据：
     *   华东: 100 + 200 = 300
     *   华北: 100
     *   华南: 500
     */
    private List<Map<String, Object>> buildSalesData() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(makeRow("华东", "手机", 100));
        data.add(makeRow("华东", "电脑", 200));
        data.add(makeRow("华北", "手机", 100));
        data.add(makeRow("华南", "电脑", 500));
        return data;
    }

    private Map<String, Object> makeRow(String region, String product, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("product", product);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeRegionCityRow(String region, String city, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("salesAmount", sales);
        return row;
    }
}
