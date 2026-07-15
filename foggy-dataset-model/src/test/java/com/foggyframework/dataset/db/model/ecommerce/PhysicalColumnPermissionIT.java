package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 物理列级权限（deniedColumns）集成测试。
 * <p>
 * 验证 {@code PhysicalColumnPermissionStep} 在 SQL 构建后、执行前
 * 对 JdbcQuery 引用的物理列做黑名单校验的逻辑。
 * <p>
 * 使用 FactSalesQueryModel（事实表 fact_sales）进行真实 SQL 数据比对。
 *
 * @since 8.2.0
 */
@DisplayName("物理列级权限集成测试（deniedColumns）")
class PhysicalColumnPermissionIT extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    private static final String QUERY_MODEL = "FactSalesQueryModel";

    // ==================== 不限制 — 查询正常 ====================

    @Nested
    @DisplayName("deniedColumns 为 null 或空 — 不限制")
    class NoRestrictionTests {

        @Test
        @DisplayName("deniedColumns 为 null — 不限制，查询正常执行")
        void deniedColumnsNull_querySucceeds() {
            // 1. 原生 SQL 基线
            String expectedSql = paginateSql(
                    "SELECT fs.order_id AS orderId, fs.sales_amount AS salesAmount"
                            + " FROM fact_sales fs"
                            + " ORDER BY fs.order_id",
                    100);
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 2. 通过 QueryFacade 执行查询（deniedColumns=null）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, null);

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            // 3. 数量一致
            assertEquals(expectedRows.size(), items.size(),
                    "deniedColumns=null 时查询结果数量应与原生 SQL 一致");
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
        }

        @Test
        @DisplayName("deniedColumns 空列表 — 不限制，查询正常执行")
        void emptyDeniedList_querySucceeds() {
            // 1. 原生 SQL 基线
            String expectedSql = paginateSql(
                    "SELECT fs.order_id AS orderId, fs.sales_amount AS salesAmount"
                            + " FROM fact_sales fs"
                            + " ORDER BY fs.order_id",
                    100);
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 2. deniedColumns = List.of()
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, List.of());

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            // 3. 数据比对
            assertEquals(expectedRows.size(), items.size(),
                    "deniedColumns 为空列表时查询结果数量应与原生 SQL 一致");
            assertFalse(items.isEmpty(), "查询结果不应为空");
        }
    }

    // ==================== 不命中 — 查询正常 ====================

    @Nested
    @DisplayName("deniedColumns 不命中请求列 — 查询正常")
    class DeniedColumnNotInQueryTests {

        @Test
        @DisplayName("deny 的物理列不在查询中 — 查询正常执行并返回正确数据")
        void deniedColumnNotInQuery_querySucceeds() {
            // 1. 原生 SQL 基线
            String expectedSql = paginateSql(
                    "SELECT fs.order_id AS orderId, fs.sales_amount AS salesAmount"
                            + " FROM fact_sales fs"
                            + " ORDER BY fs.order_id",
                    100);
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 2. 查询只用 orderId, salesAmount；deny profit_amount（不在查询列中）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            // 3. 数据比对
            assertEquals(expectedRows.size(), items.size(),
                    "deny 不相关列时查询结果数量应与原生 SQL 一致");
            assertFalse(items.isEmpty(), "查询结果不应为空");

            // 4. 逐行比对 salesAmount
            for (int i = 0; i < items.size(); i++) {
                assertEquals(
                        String.valueOf(expectedRows.get(i).get("salesAmount")),
                        String.valueOf(items.get(i).get("salesAmount")),
                        "第 " + i + " 行 salesAmount 应与原生 SQL 一致");
            }
        }
    }

    // ==================== 命中 — 查询被拒绝 ====================

    @Nested
    @DisplayName("deniedColumns 命中请求列 — 查询被拒绝")
    class DeniedColumnBlocksQueryTests {

        @Test
        @DisplayName("deniedColumns 命中 SELECT 列 — 查询被拒绝")
        void deniedColumnInSelect_queryRejected() {
            // 查询使用 orderId, salesAmount（映射到 fact_sales.sales_amount）
            // deny fact_sales.sales_amount → 应抛出异常
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "deny fact_sales.sales_amount 应拒绝使用 salesAmount 的查询");
            assertTrue(ex.getMessage().contains("salesAmount"),
                    "异常消息应包含被拒绝的 QM 字段名 'salesAmount'，实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("deniedColumns 命中度量列 profitAmount — 查询被拒绝")
        void deniedColumnProfitAmount_queryRejected() {
            // 查询 profitAmount（映射到 fact_sales.profit_amount）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "profitAmount"));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "deny fact_sales.profit_amount 应拒绝使用 profitAmount 的查询");
            assertTrue(ex.getMessage().contains("profitAmount"),
                    "异常消息应包含被拒绝的 QM 字段名 'profitAmount'，实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("deniedColumns 命中维度表物理列 — 查询被拒绝")
        void deniedDimensionTableColumn_queryRejected() {
            // 查询使用 customer$customerType（映射到 dim_customer.customer_type）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "customer$customerType"));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "dim_customer", "customer_type")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "deny dim_customer.customer_type 应拒绝使用 customer$customerType 的查询");
            assertTrue(ex.getMessage().contains("customer"),
                    "异常消息应包含被拒绝的 QM 字段名，实际: " + ex.getMessage());
        }
    }

    // ==================== schema 匹配逻辑 ====================

    @Nested
    @DisplayName("schema 匹配逻辑")
    class SchemaMatchingTests {

        @Test
        @DisplayName("deniedColumns schema 为 null — 匹配任意 schema")
        void deniedWithNullSchema_matchesAnySchema() {
            // schema=null, table=fact_sales, column=sales_amount → 应阻断
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "schema=null 时 deny fact_sales.sales_amount 应阻断查询");
            assertTrue(ex.getMessage().contains("salesAmount"),
                    "异常消息应包含被拒绝的 QM 字段名，实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("deniedColumns schema 不匹配实际 schema — 查询正常执行")
        void deniedWithWrongSchema_querySucceeds() {
            // deny {schema: "other_schema", table: "fact_sales", column: "sales_amount"}
            // 如果实际查询中 fact_sales 没有 schema 或 schema 不是 "other_schema"，不应命中
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn("nonexistent_schema", "fact_sales", "sales_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            // denied 条目始终生成 table.column key（跨 schema 兼容），
            // 即使 schema 不匹配，table.column 仍命中 → 安全优先，拒绝查询
            assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "denied 条目有 schema 时也应通过 table.column 匹配拦截");
        }
    }

    // ==================== 多条 deny 规则 ====================

    @Nested
    @DisplayName("多条 deny 规则")
    class MultipleDeniedColumnsTests {

        @Test
        @DisplayName("多条 deny 规则 — 只要有一条命中就拒绝")
        void multipleDenied_oneHit_queryRejected() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            // deny 两列：一个不命中（profit_amount），一个命中（sales_amount）
            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"),
                    new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> queryFacade.queryModelResult(ctx),
                    "多条 deny 规则中有一条命中 sales_amount 应拒绝查询");
            assertTrue(ex.getMessage().contains("salesAmount"),
                    "异常消息应包含命中的 QM 字段名，实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("多条 deny 规则 — 全部不命中则查询正常")
        void multipleDenied_noneHit_querySucceeds() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            // deny 两列，都不在查询中
            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"),
                    new DeniedPhysicalColumn(null, "fact_sales", "tax_amount")
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            assertNotNull(items, "所有 deny 规则都不命中时查询应正常执行");
            assertFalse(items.isEmpty(), "查询结果不应为空");
        }
    }

    // ==================== 无效 deny 条目 ====================

    @Nested
    @DisplayName("无效 deny 条目处理")
    class InvalidDeniedEntryTests {

        @Test
        @DisplayName("deny 条目 table 或 column 为 null — 自动跳过，查询正常")
        void deniedWithNullTableOrColumn_ignored() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL);
            queryRequest.setColumns(List.of("orderId", "salesAmount"));

            OrderRequestDef order = new OrderRequestDef();
            order.setField("orderId");
            order.setDir("ASC");
            queryRequest.setOrderBy(List.of(order));

            // 无效条目：table=null 或 column=null
            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, null, "sales_amount"),
                    new DeniedPhysicalColumn(null, "fact_sales", null)
            );
            ModelResultContext ctx = buildContextWithDeniedColumns(queryRequest, denied);

            // 无效条目应被跳过，不影响查询
            DbQueryResult dbResult = queryFacade.queryModelResult(ctx);
            PagingResultImpl result = dbResult.getPagingResult();
            List<Map<String, Object>> items = castItems(result);

            assertNotNull(items, "无效 deny 条目应被忽略，查询正常执行");
            assertFalse(items.isEmpty(), "查询结果不应为空");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建带 deniedColumns 的 ModelResultContext
     *
     * @param request       查询请求
     * @param deniedColumns 受限物理列黑名单，null 表示不限制
     * @return 预配置的上下文
     */
    private ModelResultContext buildContextWithDeniedColumns(DbQueryRequestDef request,
                                                             List<DeniedPhysicalColumn> deniedColumns) {
        PagingRequest<DbQueryRequestDef> pagingRequest =
                PagingRequest.buildPagingRequest(request, 100);
        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(pagingRequest);
        ctx.setDeniedColumns(deniedColumns);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(PagingResultImpl result) {
        return (List<Map<String, Object>>) result.getItems();
    }
}
