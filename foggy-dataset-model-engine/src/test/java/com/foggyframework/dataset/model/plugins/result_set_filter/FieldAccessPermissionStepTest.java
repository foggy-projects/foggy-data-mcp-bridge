package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldAccessPermissionStep 单元测试
 * <p>
 * 验证 beforeQuery 阶段的运行时列权限校验逻辑，包括：
 * columns、calculatedFields、slice、orderBy、groupBy 的字段白名单检查，
 * 以及维度后缀剥离和表达式依赖提取。
 * </p>
 *
 * @since 8.2.0
 */
@DisplayName("FieldAccessPermissionStep 列权限校验测试")
class FieldAccessPermissionStepTest {

    private FieldAccessPermissionStep step;

    @BeforeEach
    void setUp() {
        step = new FieldAccessPermissionStep();
    }

    /**
     * 构建带 fieldAccess 的 ModelResultContext
     */
    private ModelResultContext buildCtx(List<String> columns, Set<String> fieldAccess) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(columns);
        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(fieldAccess);
        return ctx;
    }

    // ==============================================
    // fieldAccess 为 null — 不做限制
    // ==============================================

    @Test
    @DisplayName("fieldAccess 为 null 时不做任何限制，返回 CONTINUE")
    void fieldAccess_null_noRestriction() {
        ModelResultContext ctx = buildCtx(List.of("amount", "orderId", "secret"), null);
        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "fieldAccess 为 null 时应返回 CONTINUE (0)");
    }

    // ==============================================
    // columns 白名单通过
    // ==============================================

    @Test
    @DisplayName("columns 中所有字段均在白名单内 — 通过")
    void fieldAccess_allowedColumn_passes() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("amount", "orderId"));
        ModelResultContext ctx = buildCtx(List.of("amount", "orderId"), allowed);
        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "白名单内的字段应通过校验");
    }

    // ==============================================
    // columns 白名单拒绝
    // ==============================================

    @Test
    @DisplayName("columns 中存在不在白名单的字段 — 抛出异常")
    void fieldAccess_deniedColumn_throws() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        ModelResultContext ctx = buildCtx(List.of("amount", "secret"), allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"),
                "异常消息应包含被拒绝的字段名 'secret'，实际: " + ex.getMessage());
    }

    // ==============================================
    // calculatedFields 依赖全部在白名单
    // ==============================================

    @Test
    @DisplayName("calculatedFields 表达式依赖的源字段全部在白名单 — 通过")
    void fieldAccess_calculatedField_allDepsAllowed() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "b", "total"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("total");
        cf.setExpression("a + b");
        request.setCalculatedFields(List.of(cf));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "依赖字段全部在白名单时应通过");
    }

    @Test
    @DisplayName("calculatedFields 输出别名不在白名单 — 抛出异常")
    void fieldAccess_calculatedField_aliasDenied() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "b"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("total");
        cf.setExpression("a + b");
        request.setCalculatedFields(List.of(cf));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("total"),
                "异常消息应包含被拒绝的计算字段别名 'total'，实际: " + ex.getMessage());
    }

    // ==============================================
    // calculatedFields 依赖被拒绝
    // ==============================================

    @Test
    @DisplayName("calculatedFields 表达式依赖的源字段不在白名单 — 抛出异常")
    void fieldAccess_calculatedField_depDenied() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "total"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("total");
        cf.setExpression("a + b");
        request.setCalculatedFields(List.of(cf));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("b"),
                "异常消息应包含被拒绝的依赖字段 'b'，实际: " + ex.getMessage());
    }

    // ==============================================
    // calculatedFields 聚合表达式依赖被拒绝
    // ==============================================

    @Test
    @DisplayName("calculatedFields 聚合表达式依赖的源字段不在白名单 — 抛出异常")
    void fieldAccess_aggregateExpr_depDenied() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "total"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("total");
        cf.setExpression("SUM(a + b)");
        request.setCalculatedFields(List.of(cf));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("b"),
                "异常消息应包含被拒绝的依赖字段 'b'，实际: " + ex.getMessage());
    }

    // ==============================================
    // slice 字段被拒绝
    // ==============================================

    @Test
    @DisplayName("slice 中字段不在白名单 — 抛出异常")
    void fieldAccess_slice_deniedField() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());
        request.setSlice(List.of(new SliceRequestDef("secret", "=", "value")));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"),
                "异常消息应包含被拒绝的字段名 'secret'，实际: " + ex.getMessage());
    }

    // ==============================================
    // orderBy 字段被拒绝
    // ==============================================

    @Test
    @DisplayName("orderBy 中字段不在白名单 — 抛出异常")
    void fieldAccess_orderBy_deniedField() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        OrderRequestDef order = new OrderRequestDef();
        order.setField("secret");
        order.setDir("ASC");
        request.setOrderBy(List.of(order));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"),
                "异常消息应包含被拒绝的字段名 'secret'，实际: " + ex.getMessage());
    }

    // ==============================================
    // groupBy 字段被拒绝
    // ==============================================

    @Test
    @DisplayName("groupBy 中字段不在白名单 — 抛出异常")
    void fieldAccess_groupBy_deniedField() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        GroupRequestDef group = new GroupRequestDef();
        group.setField("secret");
        request.setGroupBy(List.of(group));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"),
                "异常消息应包含被拒绝的字段名 'secret'，实际: " + ex.getMessage());
    }

    // ==============================================
    // 维度后缀剥离
    // ==============================================

    @Test
    @DisplayName("columns 带维度后缀 $id — 剥离后缀匹配白名单，通过")
    void fieldAccess_dimensionSuffix() {
        Set<String> allowed = new LinkedHashSet<>(List.of("salesDate"));
        ModelResultContext ctx = buildCtx(List.of("salesDate$id"), allowed);
        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "维度后缀 $id 应被剥离，基础字段 salesDate 在白名单中应通过");
    }

    // ==============================================
    // 安全边界测试
    // ==============================================

    @Test
    @DisplayName("fieldAccess 为空集合（非 null）— 拒绝所有字段")
    void fieldAccess_emptySet_rejectsAllFields() {
        Set<String> empty = Set.of();
        ModelResultContext ctx = buildCtx(List.of("amount"), empty);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("amount"));
    }

    @Test
    @DisplayName("columns 中内联表达式 — 提取依赖后校验")
    void fieldAccess_inlineExpression_extractsDeps() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("amount", "bonus"));
        ModelResultContext ctx = buildCtx(List.of("amount + bonus as total"), allowed);
        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "表达式依赖字段全部在白名单时应通过");
    }

    @Test
    @DisplayName("columns 中内联表达式依赖被拒 — 抛出异常")
    void fieldAccess_inlineExpression_deniedDep() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        ModelResultContext ctx = buildCtx(List.of("amount + secret as total"), allowed);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"),
                "应包含被拒绝的依赖字段 'secret': " + ex.getMessage());
    }

    @Test
    @DisplayName("orderBy 中表达式 — 提取依赖后校验拒绝")
    void fieldAccess_orderByExpression_deniedDep() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        OrderRequestDef order = new OrderRequestDef();
        order.setField("amount + secret");
        order.setDir("ASC");
        request.setOrderBy(List.of(order));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("secret"));
    }

    @Test
    @DisplayName("维度后缀 $caption — 剥离后匹配白名单")
    void fieldAccess_dimensionSuffix_caption() {
        Set<String> allowed = new LinkedHashSet<>(List.of("product"));
        ModelResultContext ctx = buildCtx(List.of("product$caption"), allowed);
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("维度后缀 $property — 剥离后匹配白名单")
    void fieldAccess_dimensionSuffix_property() {
        Set<String> allowed = new LinkedHashSet<>(List.of("product"));
        ModelResultContext ctx = buildCtx(List.of("product$brand"), allowed);
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("null columns 列表 — 不抛异常")
    void fieldAccess_nullColumns_passes() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(null);
        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(Set.of("amount"));
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("空 columns 列表 — 不抛异常")
    void fieldAccess_emptyColumns_passes() {
        ModelResultContext ctx = buildCtx(List.of(), Set.of("amount"));
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("$ 在字段名开头 — 不剥离，按原名校验")
    void fieldAccess_dollarAtStart_notStripped() {
        // $system 这样的字段名，$ 在 index 0，stripDimensionSuffix 不剥离
        Set<String> allowed = new LinkedHashSet<>(List.of("$system"));
        ModelResultContext ctx = buildCtx(List.of("$system"), allowed);
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("$ 在字段名开头且不在白名单 — 拒绝")
    void fieldAccess_dollarAtStart_denied() {
        Set<String> allowed = new LinkedHashSet<>(List.of("amount"));
        ModelResultContext ctx = buildCtx(List.of("$system"), allowed);
        assertThrows(RuntimeException.class, () -> step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("fail-closed — 无法解析的表达式被拒绝")
    void fieldAccess_failClosed_unparseable() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("amount", "bad"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("bad");
        cf.setExpression("amount ++ !! invalid syntax [[[");
        request.setCalculatedFields(List.of(cf));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("fail-closed"),
                "异常消息应包含 fail-closed: " + ex.getMessage());
    }

    @Test
    @DisplayName("多个 clause 同时校验 — slice + orderBy + groupBy 全部检查")
    void fieldAccess_multiClause_allChecked() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("orderId", "amount"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of("orderId", "amount"));
        request.setSlice(List.of(new SliceRequestDef("orderId", "=", 1)));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("amount");
        order.setDir("ASC");
        request.setOrderBy(List.of(order));

        GroupRequestDef group = new GroupRequestDef();
        group.setField("orderId");
        request.setGroupBy(List.of(group));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        assertEquals(0, step.beforeQuery(ctx), "全部 clause 的字段都在白名单时应通过");
    }

    @Test
    @DisplayName("多个 clause 中有一个被拒 — 即使 columns 通过也应失败")
    void fieldAccess_multiClause_oneRejected() {
        Set<String> allowed = new LinkedHashSet<>(List.of("orderId"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of("orderId")); // columns 通过

        request.setSlice(List.of(new SliceRequestDef("secret", "=", "x"))); // slice 不通过

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        assertThrows(RuntimeException.class, () -> step.beforeQuery(ctx));
    }

    // ==============================================
    // 传递依赖测试（计算字段引用其他计算字段）
    // ==============================================

    @Test
    @DisplayName("传递依赖 — 计算字段 d=c+e，c=a+b，基础字段全部在白名单时通过")
    void fieldAccess_transitiveCalcField_allBaseDepsAllowed() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "b", "c", "d", "e"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf1 = new CalculatedFieldDef();
        cf1.setName("c");
        cf1.setExpression("a + b");

        CalculatedFieldDef cf2 = new CalculatedFieldDef();
        cf2.setName("d");
        cf2.setExpression("c + e"); // c 是另一个计算字段

        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf1, cf2)));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        // d → {c, e} → c → {a, b} → 传递展开后基础字段 = {a, b, e}，全部在白名单
        assertEquals(0, step.beforeQuery(ctx), "传递依赖展开后基础字段全部在白名单应通过");
    }

    @Test
    @DisplayName("传递依赖 — 计算字段 d=c+e，c=a+b，基础字段 b 不在白名单时拒绝")
    void fieldAccess_transitiveCalcField_baseDep_denied() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "c", "d", "e")); // 缺 b
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf1 = new CalculatedFieldDef();
        cf1.setName("c");
        cf1.setExpression("a + b");

        CalculatedFieldDef cf2 = new CalculatedFieldDef();
        cf2.setName("d");
        cf2.setExpression("c + e");

        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf1, cf2)));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        // d → {c, e} → c → {a, b} → b 不在白名单 → 拒绝
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("b"),
                "应包含被拒绝的基础字段 'b': " + ex.getMessage());
    }

    @Test
    @DisplayName("传递依赖 — 三层嵌套 f=d+g，d=c+e，c=a+b 全部在白名单时通过")
    void fieldAccess_deepTransitive_passes() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "b", "c", "d", "e", "f", "g"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf1 = new CalculatedFieldDef();
        cf1.setName("c");
        cf1.setExpression("a + b");

        CalculatedFieldDef cf2 = new CalculatedFieldDef();
        cf2.setName("d");
        cf2.setExpression("c + e");

        CalculatedFieldDef cf3 = new CalculatedFieldDef();
        cf3.setName("f");
        cf3.setExpression("d + g");

        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf1, cf2, cf3)));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        assertEquals(0, step.beforeQuery(ctx), "三层传递依赖全部在白名单应通过");
    }

    @Test
    @DisplayName("orderBy 引用计算字段别名 — 传递展开后校验基础字段")
    void fieldAccess_orderByCalcAlias_transitiveCheck() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("a", "b", "total"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("total");
        cf.setExpression("a + b");
        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf)));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("total"); // 引用计算字段别名
        order.setDir("DESC");
        request.setOrderBy(List.of(order));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        assertEquals(0, step.beforeQuery(ctx), "orderBy 引用计算字段别名应传递展开后校验通过");
    }

    // ==============================================
    // 跨表维度属性字段测试
    // ==============================================

    @Test
    @DisplayName("表达式引用维度属性字段 product$categoryName — 剥离后缀匹配白名单 product")
    void fieldAccess_expressionWithDimensionProperty_passes() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("salesAmount", "product", "adjusted"));
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("adjusted");
        cf.setExpression("salesAmount * product$discountRate");
        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf)));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        // product$discountRate 剥离为 product，在白名单中 → 通过
        assertEquals(0, step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("表达式引用维度属性字段 — 维度基础名不在白名单时拒绝")
    void fieldAccess_expressionWithDimensionProperty_denied() {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList("salesAmount", "adjusted")); // 没有 product
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of());

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("adjusted");
        cf.setExpression("salesAmount * product$discountRate");
        request.setCalculatedFields(new java.util.ArrayList<>(List.of(cf)));

        PagingRequest<DbQueryRequestDef> pagingRequest = PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(allowed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> step.beforeQuery(ctx));
        assertTrue(ex.getMessage().contains("product"),
                "应包含被拒绝的维度基础名 'product': " + ex.getMessage());
    }
}
