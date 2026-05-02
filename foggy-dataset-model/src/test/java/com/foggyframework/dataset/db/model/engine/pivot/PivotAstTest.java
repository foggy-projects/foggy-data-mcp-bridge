package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pivot AST 模型单元测试
 *
 * <p>验证 AST 节点的便捷方法、校验逻辑和边界条件。</p>
 */
@DisplayName("Pivot AST 模型测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PivotAstTest {

    // ========== PivotRequest ==========

    @Test
    @Order(1)
    @DisplayName("getRowLevelCount / getColumnLevelCount 正确计算层级数")
    void testLevelCounts() {
        PivotRequest pivot = new PivotRequest();

        // null 情况
        assertEquals(0, pivot.getRowLevelCount());
        assertEquals(0, pivot.getColumnLevelCount());

        AxisField f1 = new AxisField();
        f1.setField("region");
        AxisField f2 = new AxisField();
        f2.setField("city");
        pivot.setRows(List.of(f1, f2));

        AxisField c1 = new AxisField();
        c1.setField("month");
        pivot.setColumns(List.of(c1));

        assertEquals(2, pivot.getRowLevelCount());
        assertEquals(1, pivot.getColumnLevelCount());
    }

    @Test
    @Order(2)
    @DisplayName("hasHierarchyField 检测父子维度字段")
    void testHasHierarchyField() {
        PivotRequest pivot = new PivotRequest();

        AxisField normal = new AxisField();
        normal.setField("region");
        pivot.setRows(List.of(normal));

        assertFalse(pivot.hasHierarchyField(), "无 hierarchyMode 时应返回 false");

        AxisField hierarchy = new AxisField();
        hierarchy.setField("org");
        hierarchy.setHierarchyMode("tree");
        pivot.setRows(List.of(normal, hierarchy));

        assertTrue(pivot.hasHierarchyField(), "存在 hierarchyMode=tree 时应返回 true");
    }

    // ========== AxisField ==========

    @Test
    @Order(10)
    @DisplayName("isTreeMode 与 getEffectiveExpandDepth 默认值")
    void testAxisFieldDefaults() {
        AxisField field = new AxisField();

        assertFalse(field.isTreeMode());
        assertEquals(-1, field.getEffectiveExpandDepth(), "默认展开深度应为 -1（全展开）");

        field.setHierarchyMode("tree");
        assertTrue(field.isTreeMode());

        field.setExpandDepth(3);
        assertEquals(3, field.getEffectiveExpandDepth());
    }

    // ========== MetricFilter ==========

    @Test
    @Order(20)
    @DisplayName("MetricFilter.evaluate 各种运算符")
    void testMetricFilterEvaluate() {
        MetricFilter f = new MetricFilter();
        f.setMetric("sales");
        f.setValue(100);

        f.setOp(">");
        assertTrue(f.evaluate(200));
        assertFalse(f.evaluate(100));
        assertFalse(f.evaluate(50));

        f.setOp(">=");
        assertTrue(f.evaluate(100));
        assertTrue(f.evaluate(200));
        assertFalse(f.evaluate(50));

        f.setOp("<");
        assertTrue(f.evaluate(50));
        assertFalse(f.evaluate(100));

        f.setOp("<=");
        assertTrue(f.evaluate(100));
        assertTrue(f.evaluate(50));

        f.setOp("=");
        assertTrue(f.evaluate(100));
        assertFalse(f.evaluate(101));

        f.setOp("!=");
        assertTrue(f.evaluate(101));
        assertFalse(f.evaluate(100));
    }

    @Test
    @Order(21)
    @DisplayName("MetricFilter.evaluate null 值 → 返回 false")
    void testMetricFilterNullValue() {
        MetricFilter f = new MetricFilter();
        f.setMetric("sales");
        f.setOp(">");
        f.setValue(100);

        assertFalse(f.evaluate(null), "null 度量应返回 false");
    }

    @Test
    @Order(22)
    @DisplayName("MetricFilter.evaluate 无效运算符 → 抛异常")
    void testMetricFilterInvalidOp() {
        MetricFilter f = new MetricFilter();
        f.setMetric("sales");
        f.setOp("LIKE");
        f.setValue(100);

        assertThrows(IllegalArgumentException.class, () -> f.evaluate(200));
    }

    // ========== PivotOptions ==========

    @Test
    @Order(30)
    @DisplayName("PivotOptions.validate: crossjoin + hierarchyMode 互斥")
    void testOptionsValidation() {
        PivotRequest pivot = new PivotRequest();

        AxisField treeField = new AxisField();
        treeField.setField("org");
        treeField.setHierarchyMode("tree");
        pivot.setRows(List.of(treeField));
        pivot.setColumns(List.of());
        pivot.setMetrics(List.of("sales"));

        PivotOptions options = new PivotOptions();
        options.setCrossjoin(true);

        assertThrows(IllegalArgumentException.class, () -> options.validate(pivot),
                "crossjoin + hierarchyMode=tree 应触发互斥校验异常");
    }

    @Test
    @Order(31)
    @DisplayName("PivotOptions.validate: 无互斥冲突 → 通过")
    void testOptionsValidationPass() {
        PivotRequest pivot = new PivotRequest();

        AxisField normalField = new AxisField();
        normalField.setField("region");
        pivot.setRows(List.of(normalField));
        pivot.setColumns(List.of());
        pivot.setMetrics(List.of("sales"));

        PivotOptions options = new PivotOptions();
        options.setCrossjoin(true);

        assertDoesNotThrow(() -> options.validate(pivot));
    }

    // ========== PivotLayout ==========

    @Test
    @Order(40)
    @DisplayName("PivotLayout 默认值和 isMetricOnRows")
    void testPivotLayout() {
        PivotLayout layout = new PivotLayout();

        assertEquals("columns", layout.getMetricPlacement(), "默认 metricPlacement 应为 columns");
        assertFalse(layout.isMetricOnRows());

        layout.setMetricPlacement("rows");
        assertTrue(layout.isMetricOnRows());
    }
}
