package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.permission.FieldPermissionRuleDef;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 运行时列权限（FieldAccess）集成测试。
 * <p>
 * 验证 {@code FieldAccessPermissionStep} 在 beforeQuery 阶段对
 * columns / slice / orderBy / groupBy 的白名单校验逻辑，
 * 以及 fieldAccess 为 null 时的向后兼容性。
 * <p>
 * 使用 FactSalesQueryModel 进行真实 SQL 数据比对。
 */
@DisplayName("运行时列权限集成测试（FieldAccess）")
class FieldAccessPermissionIT extends EcommerceTestSupport {

    @Resource
    private AdvancedQueryFacade queryFacade;

    private static final String QUERY_MODEL = "FactSalesQueryModel";

    // ==================== 允许访问的字段 — 真实数据比对 ====================

    @Nested
    @DisplayName("白名单允许的字段查询")
    class AllowedFieldTests {

        @Test
        @DisplayName("fieldAccess 包含请求列时查询成功，结果与原生 SQL 一致")
        void queryWithFieldAccess_onlyAllowedColumnsReturned() {
            // 1. 原生 SQL 基线
            String expectedSql = paginateSql(
                    "SELECT fs.order_id AS orderId, fs.sales_amount AS salesAmount"
                            + " FROM fact_sales fs"
                            + " ORDER BY fs.order_id",
                    100);
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 2. 通过 AdvancedQueryFacade 执行查询（带 fieldAccess 白名单）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            // 显式排序以保证跨数据库一致性（PostgreSQL 不保证无序查询行顺序）
            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId", "salesAmount", "store"));

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            // 3. 数量一致
            assertEquals(expectedRows.size(), items.size(),
                    "fieldAccess 白名单允许的查询结果数量应与原生 SQL 一致");
            assertFalse(items.isEmpty(), "查询结果不应为空");

            // 4. 逐行比对 orderId
            List<String> expectedOrderIds = expectedRows.stream()
                    .map(r -> String.valueOf(r.get("orderId")))
                    .collect(Collectors.toList());
            List<String> actualOrderIds = items.stream()
                    .map(r -> String.valueOf(r.get("orderId")))
                    .collect(Collectors.toList());
            assertEquals(expectedOrderIds, actualOrderIds,
                    "orderId 列表应与原生 SQL 完全一致");

            // 5. 逐行比对 salesAmount
            for (int i = 0; i < items.size(); i++) {
                assertEquals(
                        String.valueOf(expectedRows.get(i).get("salesAmount")),
                        String.valueOf(items.get(i).get("salesAmount")),
                        "第 " + i + " 行 salesAmount 应与原生 SQL 一致");
            }
        }
    }

    // ==================== 被拒绝的字段 — 异常校验 ====================

    @Nested
    @DisplayName("白名单拒绝的字段")
    class DeniedFieldTests {

        @Test
        @DisplayName("columns 包含白名单外字段时抛出异常")
        void queryWithFieldAccess_deniedColumn_rejected() {
            // fieldAccess 只允许 orderId，不允许 salesAmount
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "请求白名单外的字段 salesAmount 应抛出异常");
            assertTrue(ex.getMessage().contains("salesAmount"),
                    "异常消息应包含被拒绝的字段名 'salesAmount'，实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("slice 引用白名单外字段时抛出异常")
        void queryWithFieldAccess_sliceOnDeniedField_rejected() {
            // fieldAccess 允许 orderId 和 salesAmount，但不允许 store
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId"));

            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("store$id");
            slice.setOp("=");
            slice.setValue(1);
            queryRequest.setSlice(List.of(slice));

            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId", "salesAmount"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "slice 引用白名单外字段 store 应抛出异常");
            assertTrue(ex.getMessage().contains("store"),
                    "异常消息应包含被拒绝的字段名 'store'，实际: " + ex.getMessage());
        }
    }

    // ==================== 向后兼容 — fieldAccess 为 null ====================

    @Nested
    @DisplayName("向后兼容（fieldAccess=null）")
    class BackwardCompatibleTests {

        @Test
        @DisplayName("fieldAccess 为 null 时不限制任何字段，查询正常执行")
        void queryWithFieldAccess_noFieldAccess_unrestricted() {
            // 1. 原生 SQL 基线（包含维度字段和度量）
            String expectedSql = paginateSql(
                    "SELECT fs.order_id AS orderId, fs.sales_amount AS salesAmount"
                            + " FROM fact_sales fs"
                            + " ORDER BY fs.order_id",
                    100);
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 2. 通过 AdvancedQueryFacade 执行查询（fieldAccess=null，不限制）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            // 显式排序以保证跨数据库一致性
            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest, null);

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            // 3. 数量一致
            assertEquals(expectedRows.size(), items.size(),
                    "fieldAccess=null 时查询结果数量应与原生 SQL 一致");
            assertFalse(items.isEmpty(), "查询结果不应为空");

            // 4. 逐行比对
            for (int i = 0; i < items.size(); i++) {
                assertEquals(
                        String.valueOf(expectedRows.get(i).get("orderId")),
                        String.valueOf(items.get(i).get("orderId")),
                        "第 " + i + " 行 orderId 应一致");
                assertEquals(
                        String.valueOf(expectedRows.get(i).get("salesAmount")),
                        String.valueOf(items.get(i).get("salesAmount")),
                        "第 " + i + " 行 salesAmount 应一致");
            }
        }
    }

    // ==================== 计算字段全链路测试 ====================

    @Nested
    @DisplayName("计算字段列权限全链路")
    class CalculatedFieldTests {

        @Test
        @DisplayName("计算字段依赖源字段全部在白名单 — 查询成功")
        void queryWithFieldAccess_calculatedFieldAllowed() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId"));

            // 添加计算字段：salesAmount * 2 （依赖 salesAmount）
            CalculatedFieldDef cf = new CalculatedFieldDef();
            cf.setName("doubleAmount");
            cf.setExpression("salesAmount * 2");
            queryRequest.setCalculatedFields(new java.util.ArrayList<>(List.of(cf)));

            // fieldAccess 包含输出别名和依赖源字段 — 应通过
            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId", "salesAmount", "doubleAmount"));

            // 不抛异常即通过
            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            assertNotNull(dbResult);
            PagingResultImpl result = dbResult.getPagingResult();
            assertNotNull(result);
            assertFalse(castItems(result).isEmpty(), "查询结果不应为空");
        }

        @Test
        @DisplayName("计算字段依赖源字段不在白名单 — 拒绝")
        void queryWithFieldAccess_calculatedFieldDenied() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId"));

            // 添加计算字段：salesAmount * 2 （依赖 salesAmount）
            CalculatedFieldDef cf = new CalculatedFieldDef();
            cf.setName("doubleAmount");
            cf.setExpression("salesAmount * 2");
            queryRequest.setCalculatedFields(new java.util.ArrayList<>(List.of(cf)));

            // fieldAccess 包含输出别名，但不包含依赖源字段 salesAmount — 应拒绝
            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId", "doubleAmount"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "计算字段依赖的 salesAmount 不在白名单应抛异常");
            assertTrue(ex.getMessage().contains("salesAmount"),
                    "异常消息应包含被拒字段 'salesAmount': " + ex.getMessage());
        }
    }

    // ==================== 模型权限与 deniedColumns 组合 ====================

    @Nested
    @DisplayName("模型字段权限与 deniedColumns 组合")
    class ModelPermissionAndDeniedColumnsTests {

        @Test
        @DisplayName("TM/QM 允许字段但 deniedColumns 命中时仍拒绝")
        void modelFieldPermissionsAllowedButDeniedColumnsRejected() {
            JdbcQueryModel queryModel = getQueryModel(QUERY_MODEL);
            QueryModelSupport qm = queryModel.getDecorate(QueryModelSupport.class);
            TableModelSupport tm = queryModel.getJdbcModel().getDecorate(TableModelSupport.class);
            assertNotNull(qm, "测试模型应支持 QM fieldPermissions");
            assertNotNull(tm, "测试模型应支持 TM fieldPermissions");

            FieldPermissionsDef originalQmPermissions = qm.getFieldPermissions();
            FieldPermissionsDef originalTmPermissions = tm.getFieldPermissions();

            try {
                tm.setFieldPermissions(permissions(false,
                        List.of(rule(null, "orderId", "salesAmount")), null));
                qm.setFieldPermissions(permissions(false,
                        List.of(rule(null, "orderId", "salesAmount")), null));

                ModelResultContext allowedContext = buildContextWithPermissions(
                        salesAmountRequest(),
                        null,
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")));
                DbQueryResult allowedResult = queryFacade.queryModelResult(allowedContext);
                assertFalse(castItems(allowedResult.getPagingResult()).isEmpty(),
                        "TM/QM 允许且 deniedColumns 未命中时查询应成功");

                ModelResultContext deniedContext = buildContextWithPermissions(
                        salesAmountRequest(),
                        null,
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")));

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> queryFacade.queryModelResult(deniedContext),
                        "即使 TM/QM 允许 salesAmount，deniedColumns 命中物理列后仍应拒绝");
                assertTrue(ex.getMessage().contains("salesAmount"),
                        "异常消息应包含被 deniedColumns 收窄的 QM 字段 salesAmount，实际: " + ex.getMessage());
            } finally {
                qm.setFieldPermissions(originalQmPermissions);
                tm.setFieldPermissions(originalTmPermissions);
            }
        }
    }

    // ==================== 空集合边界测试 ====================

    @Nested
    @DisplayName("边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("fieldAccess 空集合 — 拒绝所有查询")
        void queryWithFieldAccess_emptySet_rejectsAll() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId"));

            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest, Set.of());

            assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "空 fieldAccess 集合应拒绝所有查询");
        }

        @Test
        @DisplayName("orderBy 引用无权限字段 — 拒绝")
        void queryWithFieldAccess_orderByDenied() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("salesAmount");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            // fieldAccess 只有 orderId，不包含 salesAmount
            ModelResultContext ctx = buildContextWithFieldAccess(queryRequest,
                    Set.of("orderId"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "orderBy 引用无权限字段 salesAmount 应拒绝");
            assertTrue(ex.getMessage().contains("salesAmount"));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建带 fieldAccess 的 ModelResultContext
     *
     * @param request     查询请求
     * @param fieldAccess 字段白名单，null 表示不限制
     * @return 预配置的上下文
     */
    private ModelResultContext buildContextWithFieldAccess(DbQueryRequestDef request,
                                                          Set<String> fieldAccess) {
        PagingRequest<DbQueryRequestDef> pagingRequest =
                PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setFieldAccess(fieldAccess);
        return ctx;
    }

    private ModelResultContext buildContextWithPermissions(DbQueryRequestDef request,
                                                           Set<String> fieldAccess,
                                                           List<DeniedPhysicalColumn> deniedColumns) {
        ModelResultContext ctx = buildContextWithFieldAccess(request, fieldAccess);
        ctx.setDeniedColumns(deniedColumns);
        return ctx;
    }

    private DbQueryRequestDef salesAmountRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL);
        queryRequest.setColumns(List.of("orderId", "salesAmount"));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setDir("ASC");
        queryRequest.setOrderBy(List.of(order));
        return queryRequest;
    }

    private FieldPermissionsDef permissions(Boolean defaultVisible,
                                            List<FieldPermissionRuleDef> visibleFields,
                                            List<FieldPermissionRuleDef> hiddenFields) {
        FieldPermissionsDef def = new FieldPermissionsDef();
        def.setDefaultVisible(defaultVisible);
        def.setVisibleFields(visibleFields);
        def.setHiddenFields(hiddenFields);
        return def;
    }

    private FieldPermissionRuleDef rule(Map<String, Object> when, String... fields) {
        FieldPermissionRuleDef rule = new FieldPermissionRuleDef();
        rule.setWhen(when);
        rule.setFields(List.of(fields));
        return rule;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(PagingResultImpl result) {
        return (List<Map<String, Object>>) result.getItems();
    }
}
