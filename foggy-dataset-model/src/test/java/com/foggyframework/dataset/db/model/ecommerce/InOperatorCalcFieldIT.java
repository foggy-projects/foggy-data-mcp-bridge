package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v8.1.11.beta 集成测试：DSL {@code calculatedFields} + slice {@code $expr} 支持
 * SQL 风格 {@code v in (...)} / {@code v not in (...)} 成员测试算子。
 *
 * <p>按 CLAUDE.md 强制要求进行真实 SQL 数据比对：
 * 把 IN/NOT IN 包在 IIF 里作为计算列 / 作为 slice 过滤谓词，生成的 SQL 与原生 SQL 逐行等值比对。</p>
 *
 * <p>QM {@code columnGroups.items[].formula} 与 DSL {@code calculatedFields} 共享
 * 同一条 {@link com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService#compileExpression(String)}
 * 编译链路（见 {@code QueryModelLoaderImpl.loadColumnGroups} 把 formula 转成
 * {@code CalculatedFieldDef}），所以 DSL 测试通过等价于 QM formula 也通过。</p>
 *
 * <p>slice {@code $expr} 路径先前被 {@code QueryRequestValidationStep} 的 field 必填
 * 检查误拦，已由 {@code BUG-001-slice-expr-validation-gap} 修复（v8.1.11.beta）。</p>
 *
 * <p>基础数据：{@code dim_product} 表中 {@code brand} 列目前仅出现
 * {@code Apple / Nike / Adidas} 三种值（见 {@code sqlite/03-test-data.sql}）。</p>
 *
 * @since 8.1.11.beta
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IN / NOT IN 计算字段与 slice 集成测试 (v8.1.11.beta)")
class InOperatorCalcFieldIT extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    // ================================================================
    // slice $expr —— SQL 风格 IN/NOT IN 作为过滤条件（BUG-001 修复后启用）
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("slice $expr: brand in ('Apple','Nike') 只返回匹配品牌行")
    void sliceExprIn_filtersByBrand() {
        String baseline = "SELECT product_id FROM dim_product "
                + "WHERE brand IN ('Apple','Nike') ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);
        log.info("原生 SQL IN 基线行数: {}", expected.size());
        assertFalse(expected.isEmpty(),
                "测试数据必须包含 Apple 或 Nike 的产品；否则基线无效");

        Set<String> expectedIds = new HashSet<>();
        for (Map<String, Object> row : expected) {
            expectedIds.add(String.valueOf(row.get("product_id")));
        }

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");
        req.setColumns(Arrays.asList("productId", "brand"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("brand in ('Apple', 'Nike')");
        req.setSlice(Collections.singletonList(slice));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();
        log.info("DSL slice IN 查询结果行数: {}", rows.size());

        assertEquals(expectedIds.size(), rows.size(),
                "行数必须等于基线");
        for (Map<String, Object> row : rows) {
            Object brand = row.get("brand");
            assertTrue(brand != null && (brand.equals("Apple") || brand.equals("Nike")),
                    "每行 brand 必须命中 IN 列表: " + brand);
            assertTrue(expectedIds.contains(String.valueOf(row.get("productId"))),
                    "productId 必须在基线集合里: " + row.get("productId"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("slice $expr: brand not in ('Apple','Nike') 取反集")
    void sliceExprNotIn_filtersByBrand() {
        String baseline = "SELECT product_id FROM dim_product "
                + "WHERE brand NOT IN ('Apple','Nike') ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);
        log.info("原生 SQL NOT IN 基线行数: {}", expected.size());
        assertFalse(expected.isEmpty(),
                "测试数据应当含 Apple / Nike 以外的品牌（例如 Adidas）");

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");
        req.setColumns(Arrays.asList("productId", "brand"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("brand not in ('Apple', 'Nike')");
        req.setSlice(Collections.singletonList(slice));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size(), "NOT IN 行数必须等于基线");
        for (Map<String, Object> row : rows) {
            Object brand = row.get("brand");
            assertNotEquals("Apple", brand);
            assertNotEquals("Nike", brand);
        }
    }

    @Test
    @Order(3)
    @DisplayName("slice $expr: 组合 IN + 其他条件 brand in (...) && unitPrice > 100")
    void sliceExpr_inCombinedWithComparison() {
        String baseline = "SELECT product_id FROM dim_product "
                + "WHERE brand IN ('Apple','Nike') AND unit_price > 100 ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);
        log.info("组合 IN + 比较 基线行数: {}", expected.size());

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");
        req.setColumns(Arrays.asList("productId", "brand", "unitPrice"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("brand in ('Apple', 'Nike') && unitPrice > 100");
        req.setSlice(Collections.singletonList(slice));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size(), "组合条件行数须等于基线");
        for (Map<String, Object> row : rows) {
            Object brand = row.get("brand");
            assertTrue("Apple".equals(brand) || "Nike".equals(brand),
                    "brand 必须命中 IN 列表: " + brand);
            BigDecimal price = toBigDecimal(row.get("unitPrice"));
            assertTrue(price.compareTo(new BigDecimal("100")) > 0,
                    "unitPrice 必须 > 100: " + price);
        }
    }

    // ================================================================
    // DSL calculatedFields —— 通过 IIF 包裹 IN 作为计算列输出 0/1
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("DSL calculatedFields: IIF(brand in (...), 1, 0) 逐行等值对比原生 SQL")
    void calcField_iifInExpression() {
        String baseline = "SELECT product_id, "
                + "CASE WHEN brand IN ('Apple','Nike') THEN 1 ELSE 0 END AS is_top "
                + "FROM dim_product ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);
        log.info("基线行数: {}", expected.size());

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        // IIF(x, then, else) 会在 SqlFunctionExp 里降级为 CASE WHEN
        calcFields.add(new CalculatedFieldDef(
                "isTopBrand",
                "是否顶级品牌",
                "IIF(brand in ('Apple', 'Nike'), 1, 0)"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "brand", "isTopBrand"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size(), "计算字段场景行数须等于基线");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> exp = expected.get(i);
            Map<String, Object> act = rows.get(i);
            assertEquals(String.valueOf(exp.get("product_id")),
                    String.valueOf(act.get("productId")),
                    "行 " + i + " productId");
            assertEquals(toInt(exp.get("is_top")), toInt(act.get("isTopBrand")),
                    "行 " + i + " isTopBrand (brand=" + act.get("brand") + ")");
        }
    }

    @Test
    @Order(11)
    @DisplayName("DSL calculatedFields: IIF(brand not in (...), 1, 0) 取反集")
    void calcField_iifNotInExpression() {
        String baseline = "SELECT product_id, "
                + "CASE WHEN brand NOT IN ('Apple','Nike') THEN 1 ELSE 0 END AS is_other "
                + "FROM dim_product ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef(
                "isOtherBrand",
                "是否非头部品牌",
                "IIF(brand not in ('Apple', 'Nike'), 1, 0)"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "brand", "isOtherBrand"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(toInt(expected.get(i).get("is_other")),
                    toInt(rows.get(i).get("isOtherBrand")),
                    "行 " + i + " isOtherBrand (brand=" + rows.get(i).get("brand") + ")");
        }
    }

    @Test
    @Order(12)
    @DisplayName("DSL calculatedFields: 字符串 NOT IN 返回正确分组计数")
    void calcField_groupingWithNotIn() {
        // 统计非头部品牌 (brand not in Apple/Nike) 产品数
        String baseline = "SELECT COUNT(*) AS c FROM dim_product "
                + "WHERE brand NOT IN ('Apple','Nike')";
        long expectedCount = Long.parseLong(String.valueOf(executeQuery(baseline).get(0).get("c")));
        log.info("非头部品牌基线数量: {}", expectedCount);
        assertTrue(expectedCount > 0, "基础数据应含至少一条非头部品牌产品（Adidas）");

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef(
                "otherBrandFlag",
                "非头部品牌标记",
                "IIF(brand not in ('Apple', 'Nike'), 1, 0)"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "otherBrandFlag"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        long actualCount = rows.stream()
                .filter(r -> toInt(r.get("otherBrandFlag")) != null && toInt(r.get("otherBrandFlag")) == 1)
                .count();
        assertEquals(expectedCount, actualCount,
                "计算字段 flag=1 的数量必须等于 SQL NOT IN 基线");
    }

    @Test
    @Order(20)
    @DisplayName("QM formula 共享编译链路：CalculatedFieldService.compileExpression 接受 `brand in (...)` ")
    void qmFormulaCompileEntrypointAcceptsIn() {
        // QM 的 columnGroups.items[].formula 在 QueryModelLoaderImpl 里被转成
        // CalculatedFieldDef.expression，后续走 CalculatedFieldService.compileExpression。
        // 所以这里对编译入口直接断言就等价于 QM formula 支持 IN。
        Object compiled = com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService
                .compileExpression("brand in ('Apple', 'Nike')");
        assertNotNull(compiled, "QM formula 的 IN 表达式必须编译通过");

        Object notInCompiled = com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService
                .compileExpression("status not in ('cancelled', 'returned')");
        assertNotNull(notInCompiled);

        Object iifWrapped = com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService
                .compileExpression("IIF(brand in ('Apple', 'Nike'), 1, 0)");
        assertNotNull(iifWrapped);
    }

    // ================================================================
    // SQL 三值逻辑 + OR 组合（NULL 语义与标准 SQL 对齐）
    // ================================================================

    /**
     * 当 IN 列表含 NULL 时，SQL 三值逻辑：
     * - {@code x IN (value, NULL)}：若 x 命中 value 返回 true；否则 UNKNOWN
     * - {@code x NOT IN (value, NULL)}：恒为 UNKNOWN（即使 x != value，因为有 NULL 存在）
     *
     * <p>在 {@code CASE WHEN ... THEN 1 ELSE 0 END} 里，UNKNOWN 落入 ELSE 分支 → 返回 0。
     * 所以 {@code IIF(brand NOT IN ('Sony', NULL), 1, 0)} 在任何 brand 值下都返回 0。
     * <p>计算字段层与原生 SQL 输出逐行一致是验收标准。
     */
    @Test
    @Order(40)
    @DisplayName("SQL 三值逻辑: `NOT IN (v, NULL)` 在所有行返回 0（UNKNOWN→ELSE）")
    void calcField_notInWithNullFollowsThreeValuedLogic() {
        String baseline = "SELECT product_id, "
                + "CASE WHEN brand NOT IN ('Sony', NULL) THEN 1 ELSE 0 END AS flag "
                + "FROM dim_product ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef(
                "flag",
                "NOT IN NULL flag",
                "IIF(brand not in ('Sony', null), 1, 0)"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "flag"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();
        assertEquals(expected.size(), rows.size());

        // 所有行 flag 都应为 0（三值逻辑 UNKNOWN → CASE 的 ELSE）
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(toInt(expected.get(i).get("flag")), toInt(rows.get(i).get("flag")),
                    "行 " + i + " flag 必须与原生 SQL 一致（期望全 0，SQL NOT IN NULL 三值逻辑）");
            assertEquals(Integer.valueOf(0), toInt(rows.get(i).get("flag")),
                    "行 " + i + " flag 应为 0 —— SQL NOT IN 含 NULL 总返回 UNKNOWN");
        }
    }

    /**
     * `IN (value, NULL)` 情况：命中 value 的行返回 1，否则返回 0（UNKNOWN → ELSE）。
     */
    @Test
    @Order(41)
    @DisplayName("SQL 三值逻辑: `IN (v, NULL)` 命中返回 1，未命中 UNKNOWN→0")
    void calcField_inWithNullFollowsThreeValuedLogic() {
        String baseline = "SELECT product_id, "
                + "CASE WHEN brand IN ('Apple', NULL) THEN 1 ELSE 0 END AS flag "
                + "FROM dim_product ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef(
                "flag",
                "IN NULL flag",
                "IIF(brand in ('Apple', null), 1, 0)"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "brand", "flag"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();
        assertEquals(expected.size(), rows.size());

        for (int i = 0; i < rows.size(); i++) {
            assertEquals(toInt(expected.get(i).get("flag")), toInt(rows.get(i).get("flag")),
                    "行 " + i + " flag 必须与原生 SQL 一致 (brand=" + rows.get(i).get("brand") + ")");
        }
    }

    /**
     * OR 组合：`brand in ('Apple') || unitPrice > 10000` slice 过滤，
     * 与原生 SQL `brand IN ('Apple') OR unit_price > 10000` 行集一致。
     */
    @Test
    @Order(50)
    @DisplayName("slice $expr: IN 与 OR 组合 `brand in ('Apple') || unitPrice > 10000`")
    void sliceExpr_inCombinedWithOr() {
        String baseline = "SELECT product_id FROM dim_product "
                + "WHERE brand IN ('Apple') OR unit_price > 10000 ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");
        req.setColumns(Arrays.asList("productId", "brand", "unitPrice"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("brand in ('Apple') || unitPrice > 10000");
        req.setSlice(Collections.singletonList(slice));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size(), "OR 组合行数须等于基线");
        for (Map<String, Object> row : rows) {
            Object brand = row.get("brand");
            BigDecimal price = toBigDecimal(row.get("unitPrice"));
            boolean brandHit = "Apple".equals(brand);
            boolean priceHit = price != null && price.compareTo(new BigDecimal("10000")) > 0;
            assertTrue(brandHit || priceHit,
                    "每行必须至少命中 OR 两侧之一: brand=" + brand + " price=" + price);
        }
    }

    /**
     * 多 IN 链式：`brand in ('Apple') && categoryId in ('CAT001','CAT002') && unitPrice > 0`
     */
    @Test
    @Order(51)
    @DisplayName("slice $expr: 多 IN 链式 && 组合")
    void sliceExpr_multipleInChained() {
        String baseline = "SELECT product_id FROM dim_product "
                + "WHERE brand IN ('Apple') AND category_id IN ('CAT001', 'CAT002') AND unit_price > 0 "
                + "ORDER BY product_id";
        List<Map<String, Object>> expected = executeQuery(baseline);

        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");
        req.setColumns(Arrays.asList("productId", "brand", "categoryId", "unitPrice"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("brand in ('Apple') && categoryId in ('CAT001', 'CAT002') && unitPrice > 0");
        req.setSlice(Collections.singletonList(slice));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("productId");
        order.setDir("ASC");
        req.setOrderBy(Collections.singletonList(order));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(req, 2000));
        List<Map<String, Object>> rows = result.getItems();

        assertEquals(expected.size(), rows.size(), "多 IN 链式行数须等于基线");
    }

    @Test
    @Order(60)
    @DisplayName("DSL calculatedFields: 空列表在编译期被拒绝")
    void calcField_emptyListRejected() {
        DbQueryRequestDef req = new DbQueryRequestDef();
        req.setQueryModel("DimProductQueryModel");

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef(
                "bad",
                "空列表",
                "brand in ()"
        ));
        req.setCalculatedFields(calcFields);
        req.setColumns(Arrays.asList("productId", "bad"));

        Throwable ex = assertThrows(Throwable.class,
                () -> queryFacade.queryModelData(
                        PagingRequest.buildPagingRequest(req, 10)),
                "空 IN 列表必须在编译期被拒绝");

        Throwable cause = ex;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "预期 IllegalArgumentException 在 cause 链里，实际: " + ex);
        assertTrue(cause.getMessage() != null && cause.getMessage().contains("IN"),
                "错误信息应提及 IN: " + cause.getMessage());
    }

    // ================================================================
    // helpers
    // ================================================================

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        return new BigDecimal(v.toString());
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }
}
