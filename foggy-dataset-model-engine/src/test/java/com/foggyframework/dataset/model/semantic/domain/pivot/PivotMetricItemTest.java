package com.foggyframework.dataset.model.semantic.domain.pivot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PivotMetricItem 单元测试")
class PivotMetricItemTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ===== Factory / Query Methods =====

    @Test
    @DisplayName("ofNative 创建原生度量")
    void testOfNative() {
        PivotMetricItem item = PivotMetricItem.ofNative("salesAmount");
        assertEquals("salesAmount", item.getName());
        assertTrue(item.isNative());
        assertFalse(item.isExpr());
        assertFalse(item.isDerived());
        assertFalse(item.isParentShare());
    }

    @Test
    @DisplayName("expr 类型标记")
    void testExprType() {
        PivotMetricItem item = new PivotMetricItem();
        item.setName("grossProfit");
        item.setExpr("revenueAmount - costAmount");
        assertFalse(item.isNative());
        assertTrue(item.isExpr());
        assertFalse(item.isDerived());
    }

    @Test
    @DisplayName("parentShare 类型标记")
    void testParentShareType() {
        PivotMetricItem item = new PivotMetricItem();
        item.setName("share");
        item.setType("parentShare");
        item.setOf("salesAmount");
        assertFalse(item.isNative());
        assertFalse(item.isExpr());
        assertTrue(item.isDerived());
        assertTrue(item.isParentShare());
    }

    // ===== Validation =====

    @Nested
    @DisplayName("验证规则")
    class ValidationTests {

        @Test
        @DisplayName("name 为空 → 拒绝")
        void testNameRequired() {
            PivotMetricItem item = new PivotMetricItem();
            assertThrows(IllegalArgumentException.class, item::validate);
        }

        @Test
        @DisplayName("expr 与 type 互斥 → 拒绝")
        void testExprAndTypeMutuallyExclusive() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("bad");
            item.setExpr("a + b");
            item.setType("parentShare");
            item.setOf("a");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("互斥"));
        }

        @Test
        @DisplayName("未知 type → 拒绝")
        void testUnknownType() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("x");
            item.setType("unknown");
            item.setOf("salesAmount");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("不受支持"));
        }

        @Test
        @DisplayName("parentShare 缺 of → 拒绝")
        void testParentShareMissingOf() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("of"));
        }

        @Test
        @DisplayName("axis 非法值 (neither) → 拒绝")
        void testInvalidAxis() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            item.setOf("salesAmount");
            item.setAxis("neither");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("不支持"));
        }

        @Test
        @DisplayName("axis=columns → 第一版拒绝")
        void testAxisColumnsRejected() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            item.setOf("salesAmount");
            item.setAxis("columns");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("仅支持 axis=rows"));
        }

        @Test
        @DisplayName("parentShare denominatorScope=prePageParent → 通过")
        void testParentSharePrePageParentDenominatorScope() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            item.setOf("salesAmount");
            item.setDenominatorScope("prePageParent");

            assertDoesNotThrow(item::validate);
        }

        @Test
        @DisplayName("parentShare 未支持 denominatorScope → 拒绝")
        void testParentShareUnsupportedDenominatorScopeRejected() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            item.setOf("salesAmount");
            item.setDenominatorScope("visiblePageParent");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("denominatorScope"));
        }

        @Test
        @DisplayName("expr 含 ROLLUP_TO → 拒绝（expr 整体禁止）")
        void testExprForbiddenRollupTo() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("bad");
            item.setExpr("ROLLUP_TO(salesAmount, category)");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("expr 当前版本不支持"));
        }

        @Test
        @DisplayName("expr 含 CELL_AT → 拒绝（expr 整体禁止）")
        void testExprForbiddenCellAt() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("bad");
            item.setExpr("salesAmount / CELL_AT(salesAmount, 'total')");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("expr 当前版本不支持"));
        }

        @Test
        @DisplayName("原生度量通过验证")
        void testNativeValid() {
            PivotMetricItem item = PivotMetricItem.ofNative("salesAmount");
            assertDoesNotThrow(item::validate);
        }

        @Test
        @DisplayName("合法 parentShare 通过验证")
        void testValidParentShare() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("share");
            item.setType("parentShare");
            item.setOf("salesAmount");
            assertDoesNotThrow(item::validate);
        }

        @Test
        @DisplayName("合法 expr → 第一版拒绝")
        void testValidExprRejected() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("grossProfit");
            item.setExpr("revenueAmount - costAmount");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("expr 当前版本不支持"));
        }

        @Test
        @DisplayName("合法 baselineRatio 通过验证")
        void testValidBaselineRatio() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("salesIndex");
            item.setType("baselineRatio");
            item.setOf("salesAmount");
            item.setAxis("columns");
            item.setBaseline("first");
            assertDoesNotThrow(item::validate);
        }

        @Test
        @DisplayName("baselineRatio 缺失 baseline 拒绝")
        void testBaselineRatioMissingBaseline() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("salesIndex");
            item.setType("baselineRatio");
            item.setOf("salesAmount");
            item.setAxis("columns");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("必须指定 baseline 为 'first' 或 'last'"));
        }

        @Test
        @DisplayName("baselineRatio axis=rows 拒绝")
        void testBaselineRatioAxisRows() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("salesIndex");
            item.setType("baselineRatio");
            item.setOf("salesAmount");
            item.setAxis("rows");
            item.setBaseline("first");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("必须指定 axis='columns'"));
        }

        @Test
        @DisplayName("baselineRatio 携带 level 拒绝")
        void testBaselineRatioWithLevel() {
            PivotMetricItem item = new PivotMetricItem();
            item.setName("salesIndex");
            item.setType("baselineRatio");
            item.setOf("salesAmount");
            item.setAxis("columns");
            item.setBaseline("first");
            item.setLevel("someLevel");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, item::validate);
            assertTrue(ex.getMessage().contains("不支持 level 或 parentLevel"));
        }
    }

    // ===== PivotRequest.validateMetrics =====

    @Test
    @DisplayName("重复名称 → 拒绝")
    void testDuplicateNames() {
        PivotRequest pivot = new PivotRequest();
        pivot.setMetrics(List.of("salesAmount", "salesAmount"));
        assertThrows(IllegalArgumentException.class, pivot::validateMetrics);
    }

    // ===== JSON Deserialization =====

    @Nested
    @DisplayName("JSON 反序列化")
    class DeserializationTests {

        @Test
        @DisplayName("纯字符串数组 → backward compatible")
        void testStringArray() throws Exception {
            String json = """
                    {
                      "rows": [{"field":"category"}],
                      "metrics": ["salesAmount", "orderCount"]
                    }
                    """;
            PivotRequest request = MAPPER.readValue(json, PivotRequest.class);
            assertNotNull(request.getMetricItems());
            assertEquals(2, request.getMetricItems().size());
            assertTrue(request.getMetricItems().get(0).isNative());
            assertEquals("salesAmount", request.getMetricItems().get(0).getName());
            // 向后兼容 getMetrics()
            assertEquals(List.of("salesAmount", "orderCount"), request.getMetrics());
        }

        @Test
        @DisplayName("混合数组 → string + parentShare")
        void testMixedArray() throws Exception {
            String json = """
                    {
                      "rows": [{"field":"category"}, {"field":"subCategory"}],
                      "metrics": [
                        "salesAmount",
                        {"name": "share", "type": "parentShare", "of": "salesAmount"}
                      ]
                    }
                    """;
            PivotRequest request = MAPPER.readValue(json, PivotRequest.class);
            assertEquals(2, request.getMetricItems().size());
            assertTrue(request.getMetricItems().get(0).isNative());
            assertTrue(request.getMetricItems().get(1).isParentShare());
            assertEquals("salesAmount", request.getMetricItems().get(1).getOf());
            // getMetrics() 只返回原生
            assertEquals(List.of("salesAmount"), request.getMetrics());
            // getSqlMetricNames() 包含 parentShare.of
            assertEquals(List.of("salesAmount"), request.getSqlMetricNames());
        }

        @Test
        @DisplayName("混合数组 → string + expr：反序列化成功但 validate 拒绝")
        void testMixedArrayWithExpr() throws Exception {
            String json = """
                    {
                      "rows": [{"field":"category"}],
                      "metrics": [
                        "revenue",
                        "cost",
                        {"name": "profit", "expr": "revenue - cost"}
                      ]
                    }
                    """;
            PivotRequest request = MAPPER.readValue(json, PivotRequest.class);
            assertEquals(3, request.getMetricItems().size());
            assertTrue(request.getMetricItems().get(2).isExpr());
            // validate should reject expr
            assertThrows(IllegalArgumentException.class, request::validateMetrics);
        }

        @Test
        @DisplayName("parentShare 带显式 axis/level/parentLevel")
        void testParentShareExplicitLevels() throws Exception {
            String json = """
                    {
                      "rows": [{"field":"category"}, {"field":"subCategory"}],
                      "metrics": [
                        "salesAmount",
                        {"name": "share", "type": "parentShare", "of": "salesAmount",
                         "axis": "rows", "level": "subCategory", "parentLevel": "category"}
                      ]
                    }
                    """;
            PivotRequest request = MAPPER.readValue(json, PivotRequest.class);
            PivotMetricItem share = request.getMetricItems().get(1);
            assertEquals("rows", share.getAxis());
            assertEquals("subCategory", share.getLevel());
            assertEquals("category", share.getParentLevel());
        }
    }

    // ===== PivotRequest Helper Methods =====

    @Test
    @DisplayName("getParentShareMetrics() 正确过滤")
    void testGetParentShareMetrics() {
        PivotRequest pivot = new PivotRequest();
        PivotMetricItem native1 = PivotMetricItem.ofNative("salesAmount");
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("share");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        pivot.setMetricItems(List.of(native1, ps));

        assertEquals(1, pivot.getParentShareMetrics().size());
        assertEquals("share", pivot.getParentShareMetrics().get(0).getName());
    }

    @Test
    @DisplayName("getSqlMetricNames() 去重 of 引用")
    void testGetSqlMetricNamesDedup() {
        PivotRequest pivot = new PivotRequest();
        PivotMetricItem native1 = PivotMetricItem.ofNative("salesAmount");
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("share");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        pivot.setMetricItems(List.of(native1, ps));

        // salesAmount 不应重复
        assertEquals(List.of("salesAmount"), pivot.getSqlMetricNames());
    }
}
