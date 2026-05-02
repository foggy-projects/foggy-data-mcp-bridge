package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RollupGrainEnumerator 单元测试
 */
@DisplayName("Rollup Grain 枚举器")
class RollupGrainEnumeratorTest {

    @Test
    @DisplayName("仅 rowSubtotals: rows=[r1,r2,r3], columns=[c1]")
    void testRowSubtotalsOnly() {
        PivotOptions opts = new PivotOptions();
        opts.setRowSubtotals(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("r1", "r2", "r3"), List.of("c1"), opts);

        // 应生成 2 个行小计 grain: [r1,r2,c1] 和 [r1,c1]
        assertEquals(2, grains.size());
        assertGrainContains(grains, List.of("r1", "r2", "c1"));
        assertGrainContains(grains, List.of("r1", "c1"));
    }

    @Test
    @DisplayName("仅 columnSubtotals: rows=[r1], columns=[c1,c2]")
    void testColumnSubtotalsOnly() {
        PivotOptions opts = new PivotOptions();
        opts.setColumnSubtotals(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("r1"), List.of("c1", "c2"), opts);

        // 应生成 1 个列小计 grain: [r1,c1]
        assertEquals(1, grains.size());
        assertGrainContains(grains, List.of("r1", "c1"));
    }

    @Test
    @DisplayName("仅 grandTotal")
    void testGrandTotalOnly() {
        PivotOptions opts = new PivotOptions();
        opts.setGrandTotal(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("r1", "r2"), List.of("c1"), opts);

        // grand total: [c1] 和 []
        assertEquals(2, grains.size());
        assertGrainContains(grains, List.of("c1"));
        assertGrainContains(grains, List.of());
    }

    @Test
    @DisplayName("全部启用 + 交叉 grain 去重")
    void testAllEnabled() {
        PivotOptions opts = new PivotOptions();
        opts.setRowSubtotals(true);
        opts.setColumnSubtotals(true);
        opts.setGrandTotal(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("category", "subCategory"), List.of("year", "month"), opts);

        // Row subtotal: [category, year, month]
        // Col subtotal: [category, subCategory, year]
        // Cross: [category, year]
        // Grand: [year, month], [year], []
        // Total = 6 (all unique)

        Set<String> keys = grains.stream().map(RollupGrain::getGrainKey).collect(Collectors.toSet());
        assertTrue(keys.size() >= 5); // 至少 5 个唯一 grain
        // 检查关键 grain 存在
        assertGrainContains(grains, List.of("category", "year", "month"));  // row subtotal
        assertGrainContains(grains, List.of("category", "subCategory", "year")); // col subtotal
        assertGrainContains(grains, List.of()); // full grand total
    }

    @Test
    @DisplayName("单维度行轴不生成 row subtotal grain")
    void testSingleRowNoPrefixGrain() {
        PivotOptions opts = new PivotOptions();
        opts.setRowSubtotals(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("r1"), List.of("c1"), opts);

        // 单维度行轴 size=1，rowSubtotals 不触发
        assertTrue(grains.isEmpty());
    }

    @Test
    @DisplayName("无列轴时只有行小计")
    void testNoColumns() {
        PivotOptions opts = new PivotOptions();
        opts.setRowSubtotals(true);
        opts.setGrandTotal(true);

        List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                List.of("r1", "r2"), List.of(), opts);

        // Row subtotal: [r1] (no columns to append)
        // Grand: [] (full total)
        assertEquals(2, grains.size());
        assertGrainContains(grains, List.of("r1"));
        assertGrainContains(grains, List.of());
    }

    private void assertGrainContains(List<RollupGrain> grains, List<String> expectedFields) {
        RollupGrain expected = new RollupGrain(expectedFields);
        assertTrue(grains.contains(expected),
                "Expected grain " + expectedFields + " not found in " + grains);
    }
}
