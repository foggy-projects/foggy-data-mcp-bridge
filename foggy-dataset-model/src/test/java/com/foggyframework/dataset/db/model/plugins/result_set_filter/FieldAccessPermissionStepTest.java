package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.*;
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

        int result = step.beforeQuery(ctx);
        assertEquals(0, result, "依赖字段全部在白名单时应通过");
    }

    // ==============================================
    // calculatedFields 依赖被拒绝
    // ==============================================

    @Test
    @DisplayName("calculatedFields 表达式依赖的源字段不在白名单 — 抛出异常")
    void fieldAccess_calculatedField_depDenied() {
        Set<String> allowed = new LinkedHashSet<>(List.of("a"));
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
        Set<String> allowed = new LinkedHashSet<>(List.of("a"));
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
}
