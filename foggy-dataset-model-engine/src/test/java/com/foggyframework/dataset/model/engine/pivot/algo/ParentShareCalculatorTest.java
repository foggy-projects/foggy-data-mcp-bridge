package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParentShareCalculator 单元测试")
class ParentShareCalculatorTest {

    // ===== Core Calculation =====

    @Test
    @DisplayName("基本 parentShare 计算：子级占比 = 子级值 / 父级 SUM")
    void testBasicParentShare() {
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of(),
                List.of("salesAmount"),
                List.of(psMetric("share", "salesAmount"))
        );

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "subCategory", "A1", "salesAmount", 30.0));
        resultSet.add(row("category", "A", "subCategory", "A2", "salesAmount", 70.0));
        resultSet.add(row("category", "B", "subCategory", "B1", "salesAmount", 40.0));
        resultSet.add(row("category", "B", "subCategory", "B2", "salesAmount", 60.0));

        ParentShareCalculator.apply(resultSet, pivot,
                List.of("category", "subCategory"), List.of());

        assertEquals(0.3, ((Number) resultSet.get(0).get("share")).doubleValue(), 0.001);
        assertEquals(0.7, ((Number) resultSet.get(1).get("share")).doubleValue(), 0.001);
        assertEquals(0.4, ((Number) resultSet.get(2).get("share")).doubleValue(), 0.001);
        assertEquals(0.6, ((Number) resultSet.get(3).get("share")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("外部分母索引用于 prePageParent：可见子集仍按截断前父级求占比")
    void testExternalParentAggIndex() {
        PivotMetricItem share = psMetric("share", "salesAmount");
        share.setDenominatorScope("prePageParent");
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of(),
                List.of("salesAmount"),
                List.of(share)
        );

        List<Map<String, Object>> prePageRows = new ArrayList<>();
        prePageRows.add(row("category", "A", "subCategory", "A1", "salesAmount", 30.0));
        prePageRows.add(row("category", "A", "subCategory", "A2", "salesAmount", 70.0));

        Map<String, Map<String, Number>> externalIndex =
                ParentShareCalculator.buildExternalParentAggIndex(prePageRows, pivot,
                        List.of("category", "subCategory"), List.of());

        List<Map<String, Object>> visibleRows = new ArrayList<>();
        visibleRows.add(new LinkedHashMap<>(prePageRows.get(0)));

        ParentShareCalculator.apply(visibleRows, pivot,
                List.of("category", "subCategory"), List.of(), externalIndex);

        assertEquals(0.3, ((Number) visibleRows.get(0).get("share")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("除零 → null")
    void testDivideByZero() {
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of(),
                List.of("salesAmount"),
                List.of(psMetric("share", "salesAmount"))
        );

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "subCategory", "A1", "salesAmount", 0.0));
        resultSet.add(row("category", "A", "subCategory", "A2", "salesAmount", 0.0));

        ParentShareCalculator.apply(resultSet, pivot,
                List.of("category", "subCategory"), List.of());

        // parent sum = 0, so result should be null
        assertNull(resultSet.get(0).get("share"));
    }

    @Test
    @DisplayName("当前值 null → null")
    void testCurrentValueNull() {
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of(),
                List.of("salesAmount"),
                List.of(psMetric("share", "salesAmount"))
        );

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "subCategory", "A1", "salesAmount", 30.0));
        Map<String, Object> nullRow = row("category", "A", "subCategory", "A2");
        nullRow.put("salesAmount", null);
        resultSet.add(nullRow);

        ParentShareCalculator.apply(resultSet, pivot,
                List.of("category", "subCategory"), List.of());

        assertNotNull(resultSet.get(0).get("share"));
        assertNull(resultSet.get(1).get("share"));
    }

    @Test
    @DisplayName("subtotal 行 → null（不计算 parentShare）")
    void testSubtotalRowSkipped() {
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of(),
                List.of("salesAmount"),
                List.of(psMetric("share", "salesAmount"))
        );

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("category", "A", "subCategory", "A1", "salesAmount", 50.0));
        resultSet.add(row("category", "A", "subCategory", "A2", "salesAmount", 50.0));

        // Add a subtotal row
        Map<String, Object> subtotalRow = row("category", "A", "subCategory", "ALL", "salesAmount", 100.0);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("isRowSubtotal", true);
        meta.put("level", 1);
        subtotalRow.put("_sys_meta", meta);
        resultSet.add(subtotalRow);

        ParentShareCalculator.apply(resultSet, pivot,
                List.of("category", "subCategory"), List.of());

        assertEquals(0.5, ((Number) resultSet.get(0).get("share")).doubleValue(), 0.001);
        assertNull(resultSet.get(2).get("share")); // subtotal → null
    }

    @Test
    @DisplayName("带列轴的 parentShare（行轴占比）")
    void testWithColumnAxis() {
        PivotRequest pivot = buildPivot(
                List.of("category", "subCategory"),
                List.of("region"),
                List.of("salesAmount"),
                List.of(psMetric("share", "salesAmount"))
        );

        List<Map<String, Object>> resultSet = new ArrayList<>();
        // category=A, region=East
        resultSet.add(row("category", "A", "subCategory", "A1", "region", "East", "salesAmount", 30.0));
        resultSet.add(row("category", "A", "subCategory", "A2", "region", "East", "salesAmount", 70.0));
        // category=A, region=West
        resultSet.add(row("category", "A", "subCategory", "A1", "region", "West", "salesAmount", 20.0));
        resultSet.add(row("category", "A", "subCategory", "A2", "region", "West", "salesAmount", 80.0));

        ParentShareCalculator.apply(resultSet, pivot,
                List.of("category", "subCategory"), List.of("region"));

        // East: A1 = 30/100 = 0.3, A2 = 70/100 = 0.7
        assertEquals(0.3, ((Number) resultSet.get(0).get("share")).doubleValue(), 0.001);
        assertEquals(0.7, ((Number) resultSet.get(1).get("share")).doubleValue(), 0.001);
        // West: A1 = 20/100 = 0.2, A2 = 80/100 = 0.8
        assertEquals(0.2, ((Number) resultSet.get(2).get("share")).doubleValue(), 0.001);
        assertEquals(0.8, ((Number) resultSet.get(3).get("share")).doubleValue(), 0.001);
    }

    // ===== Resolution & Inference =====

    @Nested
    @DisplayName("层级推断")
    class ResolutionTests {

        @Test
        @DisplayName("隐式推断：rows 最后两级")
        void testImplicitResolution() {
            PivotRequest pivot = buildPivot(
                    List.of("region", "category", "subCategory"),
                    List.of(),
                    List.of("salesAmount"),
                    List.of(psMetric("share", "salesAmount"))
            );

            ParentShareCalculator.ResolvedParentShare resolved =
                    ParentShareCalculator.resolve(
                            pivot.getParentShareMetrics().get(0),
                            List.of("region", "category", "subCategory"),
                            List.of(),
                            pivot);

            assertEquals("rows", resolved.axis);
            assertEquals("subCategory", resolved.level);
            assertEquals("category", resolved.parentLevel);
        }

        @Test
        @DisplayName("显式指定 level/parentLevel")
        void testExplicitResolution() {
            PivotMetricItem ps = new PivotMetricItem();
            ps.setName("share");
            ps.setType("parentShare");
            ps.setOf("salesAmount");
            ps.setLevel("category");
            ps.setParentLevel("region");

            PivotRequest pivot = buildPivot(
                    List.of("region", "category", "subCategory"),
                    List.of(),
                    List.of("salesAmount"),
                    List.of(ps)
            );

            ParentShareCalculator.ResolvedParentShare resolved =
                    ParentShareCalculator.resolve(ps,
                            List.of("region", "category", "subCategory"),
                            List.of(),
                            pivot);

            assertEquals("rows", resolved.axis);
            assertEquals("category", resolved.level);
            assertEquals("region", resolved.parentLevel);
        }

        @Test
        @DisplayName("非相邻层级 → 拒绝")
        void testNonAdjacentLevelsFail() {
            PivotMetricItem ps = new PivotMetricItem();
            ps.setName("share");
            ps.setType("parentShare");
            ps.setOf("salesAmount");
            ps.setLevel("subCategory");
            ps.setParentLevel("region"); // region 和 subCategory 不相邻

            PivotRequest pivot = buildPivot(
                    List.of("region", "category", "subCategory"),
                    List.of(),
                    List.of("salesAmount"),
                    List.of(ps)
            );

            assertThrows(IllegalArgumentException.class, () ->
                    ParentShareCalculator.resolve(ps,
                            List.of("region", "category", "subCategory"),
                            List.of(),
                            pivot));
        }

        @Test
        @DisplayName("推断为 columns 轴 → 拒绝")
        void testImplicitColumnsAxisFail() {
            PivotMetricItem ps = new PivotMetricItem();
            ps.setName("share");
            ps.setType("parentShare");
            ps.setOf("salesAmount");
            ps.setLevel("subCategory");
            ps.setParentLevel("category");

            PivotRequest pivot = buildPivot(
                    List.of("region"),
                    List.of("category", "subCategory"),
                    List.of("salesAmount"),
                    List.of(ps)
            );

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ParentShareCalculator.resolve(ps,
                            List.of("region"),
                            List.of("category", "subCategory"),
                            pivot));
            assertTrue(ex.getMessage().contains("暂不支持"), "Should mention it is not supported in version 1");
        }

        @Test
        @DisplayName("单层轴 → 推断失败")
        void testSingleLevelAxisFails() {
            PivotRequest pivot = buildPivot(
                    List.of("category"),
                    List.of(),
                    List.of("salesAmount"),
                    List.of(psMetric("share", "salesAmount"))
            );

            assertThrows(IllegalArgumentException.class, () ->
                    ParentShareCalculator.resolve(
                            pivot.getParentShareMetrics().get(0),
                            List.of("category"),
                            List.of(),
                            pivot));
        }
    }

    // ===== Validation Guards =====

    @Nested
    @DisplayName("前置校验")
    class ValidationTests {

        @Test
        @DisplayName("tree + parentShare → fail-closed")
        void testTreePlusParentShareFails() {
            PivotRequest pivot = new PivotRequest();
            AxisField treeField = new AxisField();
            treeField.setField("department");
            treeField.setHierarchyMode("tree");
            pivot.setRows(List.of(treeField, axis("subDept")));

            PivotMetricItem ps = psMetric("share", "salesAmount");
            PivotMetricItem native1 = PivotMetricItem.ofNative("salesAmount");
            pivot.setMetricItems(List.of(native1, ps));

            assertThrows(IllegalArgumentException.class, () ->
                    ParentShareCalculator.validateParentShareMetrics(
                            pivot, List.of("department", "subDept"), List.of()));
        }

        @Test
        @DisplayName("of 引用不存在 → fail-closed")
        void testOfNotFoundFails() {
            PivotRequest pivot = buildPivot(
                    List.of("category", "subCategory"),
                    List.of(),
                    List.of("salesAmount"),
                    List.of(psMetric("share", "nonExistentMetric"))
            );

            assertThrows(IllegalArgumentException.class, () ->
                    ParentShareCalculator.validateParentShareMetrics(
                            pivot,
                            List.of("category", "subCategory"),
                            List.of()));
        }
    }

    // ===== Helpers =====

    private PivotRequest buildPivot(List<String> rowFieldNames, List<String> colFieldNames,
                                     List<String> nativeMetrics, List<PivotMetricItem> derivedMetrics) {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(rowFieldNames.stream().map(this::axis).collect(java.util.stream.Collectors.toList()));
        if (!colFieldNames.isEmpty()) {
            pivot.setColumns(colFieldNames.stream().map(this::axis).collect(java.util.stream.Collectors.toList()));
        }

        List<PivotMetricItem> items = new ArrayList<>();
        for (String m : nativeMetrics) {
            items.add(PivotMetricItem.ofNative(m));
        }
        items.addAll(derivedMetrics);
        pivot.setMetricItems(items);

        return pivot;
    }

    private AxisField axis(String field) {
        AxisField f = new AxisField();
        f.setField(field);
        return f;
    }

    private PivotMetricItem psMetric(String name, String of) {
        PivotMetricItem item = new PivotMetricItem();
        item.setName(name);
        item.setType("parentShare");
        item.setOf(of);
        return item;
    }

    private Map<String, Object> row(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
