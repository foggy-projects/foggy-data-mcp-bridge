package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.exception.TooManyPivotCellsException;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardinalityBreaker 单元测试
 *
 * <p>验证基数熔断器的阈值计算、小计膨胀系数和域提取逻辑。</p>
 */
@DisplayName("CardinalityBreaker 基数熔断器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CardinalityBreakerTest {

    // ========== 阈值校验 ==========

    @Test
    @Order(1)
    @DisplayName("安全范围内不应触发熔断")
    void testSafeRange() {
        CardinalityBreaker breaker = new CardinalityBreaker(100_000);
        PivotRequest pivot = buildSimplePivot(2, 1); // 2行层级, 1列层级

        // 100 rows × 50 cols = 5000 cells，远低于阈值
        assertDoesNotThrow(() -> breaker.checkEstimate(100, 50, pivot));
    }

    @Test
    @Order(2)
    @DisplayName("超过阈值应触发 TooManyPivotCellsException")
    void testExceedThreshold() {
        CardinalityBreaker breaker = new CardinalityBreaker(10_000);
        PivotRequest pivot = buildSimplePivot(1, 1);

        // 200 rows × 60 cols = 12000 > 10000
        TooManyPivotCellsException ex = assertThrows(
                TooManyPivotCellsException.class,
                () -> breaker.checkEstimate(200, 60, pivot)
        );

        assertEquals(200, ex.getRowDomainSize());
        assertEquals(60, ex.getColDomainSize());
        assertEquals(12_000, ex.getCellCount());
        assertEquals(10_000, ex.getMaxAllowed());
        assertNotNull(ex.getSuggestion());
    }

    @Test
    @Order(3)
    @DisplayName("刚好等于阈值不应触发熔断")
    void testExactThreshold() {
        CardinalityBreaker breaker = new CardinalityBreaker(10_000);
        PivotRequest pivot = buildSimplePivot(1, 1);

        // 100 × 100 = 10000 == threshold
        assertDoesNotThrow(() -> breaker.checkEstimate(100, 100, pivot));
    }

    // ========== 小计膨胀系数 ==========

    @Test
    @Order(10)
    @DisplayName("单层级 + 无小计 → 无膨胀")
    void testNoExpansionSingleLevel() {
        CardinalityBreaker breaker = new CardinalityBreaker(10_000);
        PivotRequest pivot = buildSimplePivot(1, 1);

        // 100 × 99 = 9900 < 10000，不触发
        assertDoesNotThrow(() -> breaker.checkEstimate(100, 99, pivot));
    }

    @Test
    @Order(11)
    @DisplayName("多层级 + 有小计 → 膨胀后触发熔断")
    void testExpansionWithSubtotals() {
        CardinalityBreaker breaker = new CardinalityBreaker(10_000);

        // 3 行层级 + rowSubtotals
        PivotRequest pivot = buildSimplePivot(3, 1);
        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        pivot.setOptions(options);

        // 基础 80 × 100 = 8000 < 10000（不膨胀时安全）
        // 膨胀后：80 × (1 + 3*0.1) = 80 × 1.3 = 104
        // 104 × 100 = 10400 > 10000（膨胀后触发）
        assertThrows(TooManyPivotCellsException.class,
                () -> breaker.checkEstimate(80, 100, pivot));
    }

    @Test
    @Order(12)
    @DisplayName("多层级但无小计 → 不膨胀")
    void testMultiLevelWithoutSubtotals() {
        CardinalityBreaker breaker = new CardinalityBreaker(10_000);

        // 3 行层级，但无 subtotals
        PivotRequest pivot = buildSimplePivot(3, 1);

        // 80 × 100 = 8000 < 10000（不膨胀，安全）
        assertDoesNotThrow(() -> breaker.checkEstimate(80, 100, pivot));
    }

    // ========== 域提取 ==========

    @Test
    @Order(20)
    @DisplayName("extractRowDomain 正确提取唯一行坐标")
    void testExtractRowDomain() {
        List<Map<String, Object>> resultSet = List.of(
                Map.of("region", "华东", "city", "上海", "sales", 100),
                Map.of("region", "华东", "city", "杭州", "sales", 200),
                Map.of("region", "华北", "city", "北京", "sales", 300),
                Map.of("region", "华东", "city", "上海", "sales", 150)  // 重复坐标
        );

        Set<List<Object>> domain = CardinalityBreaker.extractRowDomain(
                resultSet, List.of("region", "city"));

        // 应去重：3 个唯一行坐标
        assertEquals(3, domain.size());
        assertTrue(domain.contains(List.of("华东", "上海")));
        assertTrue(domain.contains(List.of("华东", "杭州")));
        assertTrue(domain.contains(List.of("华北", "北京")));
    }

    @Test
    @Order(21)
    @DisplayName("extractColumnDomain 正确提取唯一列坐标")
    void testExtractColumnDomain() {
        List<Map<String, Object>> resultSet = List.of(
                Map.of("product", "手机", "month", "1月", "sales", 100),
                Map.of("product", "电脑", "month", "1月", "sales", 200),
                Map.of("product", "手机", "month", "2月", "sales", 300)
        );

        Set<List<Object>> domain = CardinalityBreaker.extractColumnDomain(
                resultSet, List.of("month"));

        assertEquals(2, domain.size());
        assertTrue(domain.contains(List.of("1月")));
        assertTrue(domain.contains(List.of("2月")));
    }

    // ========== 辅助方法 ==========

    private PivotRequest buildSimplePivot(int rowLevels, int colLevels) {
        PivotRequest pivot = new PivotRequest();

        List<AxisField> rows = new ArrayList<>();
        for (int i = 0; i < rowLevels; i++) {
            AxisField f = new AxisField();
            f.setField("row_" + i);
            rows.add(f);
        }
        pivot.setRows(rows);

        List<AxisField> cols = new ArrayList<>();
        for (int i = 0; i < colLevels; i++) {
            AxisField f = new AxisField();
            f.setField("col_" + i);
            cols.add(f);
        }
        pivot.setColumns(cols);

        pivot.setMetrics(List.of("salesAmount"));
        return pivot;
    }
}
