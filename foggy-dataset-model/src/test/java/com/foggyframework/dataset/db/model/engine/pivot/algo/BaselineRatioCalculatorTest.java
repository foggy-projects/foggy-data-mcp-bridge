package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaselineRatio 计算器单元测试")
class BaselineRatioCalculatorTest {

    private Map<String, Object> row(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(kvs[i].toString(), kvs[i + 1]);
        }
        return map;
    }

    private PivotMetricItem brMetric(String name, String of, String baseline) {
        PivotMetricItem item = new PivotMetricItem();
        item.setName(name);
        item.setType("baselineRatio");
        item.setOf(of);
        item.setAxis("columns");
        item.setBaseline(baseline);
        return item;
    }

    private PivotRequest buildPivot(List<String> brMetricsConfig) {
        PivotRequest pivot = new PivotRequest();
        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));

        for (int i = 0; i < brMetricsConfig.size(); i += 3) {
            items.add(brMetric(
                brMetricsConfig.get(i),
                brMetricsConfig.get(i + 1),
                brMetricsConfig.get(i + 2)
            ));
        }
        pivot.setMetricItems(items);
        return pivot;
    }

    @Test
    @DisplayName("基准：first (列轴第一个成员)")
    void testBaselineFirst() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "first"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "month", 1, "salesAmount", 100.0));
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));
        resultSet.add(row("category", "A", "month", 3, "salesAmount", 150.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // first is month=1 (100.0)
        assertEquals(1.0, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001); // 100/100
        assertEquals(1.2, ((Number) resultSet.get(1).get("idx")).doubleValue(), 0.001); // 120/100
        assertEquals(1.5, ((Number) resultSet.get(2).get("idx")).doubleValue(), 0.001); // 150/100
    }

    @Test
    @DisplayName("基准：last (列轴最后一个成员)")
    void testBaselineLast() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "last"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "month", 1, "salesAmount", 100.0));
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));
        resultSet.add(row("category", "A", "month", 3, "salesAmount", 150.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // last is month=3 (150.0)
        assertEquals(100.0 / 150.0, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001);
        assertEquals(120.0 / 150.0, ((Number) resultSet.get(1).get("idx")).doubleValue(), 0.001);
        assertEquals(1.0, ((Number) resultSet.get(2).get("idx")).doubleValue(), 0.001); // 150/150
    }

    @Test
    @DisplayName("多个 row group 相互隔离")
    void testMultipleRowGroups() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "first"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "month", 1, "salesAmount", 100.0));
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));

        resultSet.add(row("category", "B", "month", 1, "salesAmount", 200.0));
        resultSet.add(row("category", "B", "month", 2, "salesAmount", 180.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // Group A: first is month=1 (100.0)
        assertEquals(1.0, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001); // 100/100
        assertEquals(1.2, ((Number) resultSet.get(1).get("idx")).doubleValue(), 0.001); // 120/100

        // Group B: first is month=1 (200.0)
        assertEquals(1.0, ((Number) resultSet.get(2).get("idx")).doubleValue(), 0.001); // 200/200
        assertEquals(0.9, ((Number) resultSet.get(3).get("idx")).doubleValue(), 0.001); // 180/200
    }

    @Test
    @DisplayName("缺失基准、除零、subtotal 输出 null")
    void testEdgeCases() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "first"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        List<Map<String, Object>> resultSet = new ArrayList<>();
        // Group A: first (month=1) is missing
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));

        // Group B: first (month=1) is 0
        resultSet.add(row("category", "B", "month", 1, "salesAmount", 0.0));
        resultSet.add(row("category", "B", "month", 2, "salesAmount", 180.0));

        // Subtotal
        resultSet.add(row("category", "C", "month", 1, "salesAmount", 100.0, "_sys_meta", "row_subtotal"));

        // 为了构造 column domain，需要一个正常的 month=1 行，否则全局列 domain 没有 month=1
        resultSet.add(0, row("category", "D", "month", 1, "salesAmount", 50.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // Group D (Jan): 50/50 = 1.0
        assertEquals(1.0, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001);

        // Group A (Feb): first (Jan) is missing -> null
        assertNull(resultSet.get(1).get("idx"));

        // Group B (Jan/Feb): first (Jan) is 0 -> null
        assertNull(resultSet.get(2).get("idx"));
        assertNull(resultSet.get(3).get("idx"));

        // Subtotal row -> null
        assertNull(resultSet.get(4).get("idx"));
    }

    @Test
    @DisplayName("DB 返回行顺序不同（PostgreSQL 场景）时仍正确判定 first/last")
    void testOutOfOrderRows() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "first"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        // 模拟 PostgreSQL 返回顺序：month=3, month=1, month=2（非自然排序）
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "month", 3, "salesAmount", 150.0));
        resultSet.add(row("category", "A", "month", 1, "salesAmount", 100.0));
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // 自然排序: 1 < 2 < 3, 所以 first = month=1 (100.0)
        // month=3: 150/100 = 1.5
        // month=1: 100/100 = 1.0
        // month=2: 120/100 = 1.2
        assertEquals(1.5, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001); // month=3: 150/100
        assertEquals(1.0, ((Number) resultSet.get(1).get("idx")).doubleValue(), 0.001); // month=1: 100/100
        assertEquals(1.2, ((Number) resultSet.get(2).get("idx")).doubleValue(), 0.001); // month=2: 120/100
    }

    @Test
    @DisplayName("DB 返回行顺序不同时 last 基准正确")
    void testOutOfOrderRowsLast() {
        PivotRequest pivot = buildPivot(List.of("idx", "salesAmount", "last"));
        List<String> rowFields = List.of("category");
        List<String> colFields = List.of("month");

        // 模拟非自然排序：month=3, month=1, month=2
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "month", 3, "salesAmount", 150.0));
        resultSet.add(row("category", "A", "month", 1, "salesAmount", 100.0));
        resultSet.add(row("category", "A", "month", 2, "salesAmount", 120.0));

        BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);

        // 自然排序: 1 < 2 < 3, 所以 last = month=3 (150.0)
        assertEquals(1.0, ((Number) resultSet.get(0).get("idx")).doubleValue(), 0.001);            // month=3: 150/150
        assertEquals(100.0 / 150.0, ((Number) resultSet.get(1).get("idx")).doubleValue(), 0.001);  // month=1: 100/150
        assertEquals(120.0 / 150.0, ((Number) resultSet.get(2).get("idx")).doubleValue(), 0.001);  // month=2: 120/150
    }
}
