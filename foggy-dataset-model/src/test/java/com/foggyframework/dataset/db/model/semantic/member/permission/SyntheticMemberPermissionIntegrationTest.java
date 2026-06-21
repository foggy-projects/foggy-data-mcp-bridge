package com.foggyframework.dataset.db.model.semantic.member.permission;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.SyntheticMemberInternalPatchStep;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于真实 TM/QM 文件的内部成员权限集成测试。
 * <p>
 * 使用 FactSalesMemberPermModel.tm / FactSalesMemberPermQueryModel.qm。
 * TM product 维度配置了 memberPermission.patch: visibleColumns=[id,caption,productId,brand], forcedSlice=[brand='Apple'], forcedOrderBy=[caption ASC]
 * QM memberPermissions 覆盖: visibleColumns=[id,caption,brand], forcedSlice+=[id!=0], forcedOrderBy=[caption DESC]
 */
@DisplayName("内部成员权限集成测试（真实 TM/QM）")
class SyntheticMemberPermissionIntegrationTest extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    // ==================== Loader 解析验证 ====================

    @Nested
    @DisplayName("Loader 解析")
    class LoaderTests {

        @Test
        @DisplayName("TM loader 正确解析 dimensions[].memberPermission")
        void tmLoaderParsesMemberPermission() {
            QueryModel sourceModel = queryModelLoader.getJdbcQueryModel("FactSalesMemberPermQueryModel", null);
            assertNotNull(sourceModel);

            // 找到 product 维度
            DbDimension productDim = null;
            for (DbDimension dim : sourceModel.getJdbcModel().getDimensions()) {
                if ("product".equals(dim.getEffectiveName())) {
                    productDim = dim;
                    break;
                }
            }
            assertNotNull(productDim, "应找到 product 维度");
            assertInstanceOf(DbDimensionSupport.class, productDim);

            DbDimensionSupport productDimSupport = (DbDimensionSupport) productDim;
            MemberPermissionDef perm = productDimSupport.getMemberPermission();
            assertNotNull(perm, "product 维度应有 memberPermission");
            assertNotNull(perm.getPatch(), "memberPermission 应有 patch");

            MemberPermissionPatchDef patch = perm.getPatch();
            assertEquals(List.of("id", "caption", "productId", "brand"), patch.getVisibleColumns());
            assertNotNull(patch.getForcedSlice());
            assertEquals(1, patch.getForcedSlice().size());
            assertEquals("brand", patch.getForcedSlice().get(0).getField());
            assertEquals("=", patch.getForcedSlice().get(0).getOp());
            assertEquals("Apple", patch.getForcedSlice().get(0).resolveValue(null));
        }

        @Test
        @DisplayName("QM loader 正确解析 memberPermissions[]")
        void qmLoaderParsesMemberPermissions() {
            QueryModel sourceModel = queryModelLoader.getJdbcQueryModel("FactSalesMemberPermQueryModel", null);
            assertNotNull(sourceModel);

            QueryModelSupport qms = sourceModel.getDecorate(QueryModelSupport.class);
            assertNotNull(qms);
            assertNotNull(qms.getMemberPermissions(), "QM 应有 memberPermissions");
            assertEquals(1, qms.getMemberPermissions().size());

            QmMemberPermissionDef qmPerm = qms.getMemberPermissions().get(0);
            assertEquals("product", qmPerm.getDimension());
            assertNotNull(qmPerm.getPatch());
            assertEquals(List.of("id", "caption", "brand"), qmPerm.getPatch().getVisibleColumns());
        }

        @Test
        @DisplayName("store 维度无 memberPermission，不受影响")
        void storeDimensionHasNoPermission() {
            QueryModel sourceModel = queryModelLoader.getJdbcQueryModel("FactSalesMemberPermQueryModel", null);
            for (DbDimension dim : sourceModel.getJdbcModel().getDimensions()) {
                if ("store".equals(dim.getEffectiveName()) && dim instanceof DbDimensionSupport ds) {
                    assertNull(ds.getMemberPermission(), "store 维度不应有 memberPermission");
                }
            }
        }
    }

    // ==================== QueryFacade 链路验证 ====================

    @Nested
    @DisplayName("QueryFacade 链路")
    class QueryFacadeTests {

        @Test
        @DisplayName("synthetic member-QM 走 InternalPatchStep 后 forcedSlice 生效到 SQL")
        void forcedSliceAppliedToSql() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption", "brand"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            SqlGenerationResult sqlResult = queryFacade.buildSqlOnly(ctx);

            String sql = sqlResult.getSql();
            assertNotNull(sql, "应生成 SQL");

            // TM forcedSlice: brand = 'Apple'（合并后保留）
            // QM forcedSlice: id != 0（合并后追加）
            // 两个条件应体现在 SQL 的 WHERE 子句中
            assertTrue(sql.toLowerCase().contains("brand"),
                    "SQL 应包含 brand 条件（来自 TM forcedSlice）: " + sql);
        }

        @Test
        @DisplayName("visibleColumns 限制后，请求不可见字段被裁剪")
        void visibleColumnsRestrictsRequestColumns() {
            // QM 覆盖后 visibleColumns = [id, caption, brand]
            // 请求 unitPrice（不在 visibleColumns 内），应被裁剪
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(new ArrayList<>(List.of("id", "caption", "brand", "unitPrice")));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            SqlGenerationResult sqlResult = queryFacade.buildSqlOnly(ctx);

            // 生成的 SQL 中不应包含 unit_price 列
            String sql = sqlResult.getSql();
            assertNotNull(sql);
            assertFalse(sql.toLowerCase().contains("unit_price"),
                    "SQL 不应包含 unitPrice（被 visibleColumns 裁剪）: " + sql);
        }

        @Test
        @DisplayName("effective permission 存储到 ctx.extData")
        void effectivePermissionStoredInContext() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            queryFacade.buildSqlOnly(ctx);

            Object stored = ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);
            assertNotNull(stored, "effective permission 应存储到 extData");
            assertInstanceOf(SyntheticMemberEffectivePermission.class, stored);

            SyntheticMemberEffectivePermission ep = (SyntheticMemberEffectivePermission) stored;
            // 验证合并结果
            // visibleColumns: QM [id, caption, brand] 覆盖 TM [id, caption, productId, brand]
            assertEquals(List.of("id", "caption", "brand"), ep.getVisibleColumns());
            // forcedOrderBy: QM [caption DESC] 覆盖 TM [caption ASC]
            assertNotNull(ep.getForcedOrderBy());
            assertEquals(1, ep.getForcedOrderBy().size());
            assertEquals("DESC", ep.getForcedOrderBy().get(0).getDir());
        }

        @Test
        @DisplayName("无 memberPermission 的旧模型不受影响")
        void oldModelWithoutPermissionUnaffected() {
            // FactSalesNestedDimQueryModel 没有 memberPermission
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption", "brand", "unitPrice"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            SqlGenerationResult sqlResult = queryFacade.buildSqlOnly(ctx);

            // 没有权限限制，所有列应保留
            String sql = sqlResult.getSql();
            assertNotNull(sql);
            assertTrue(sql.toLowerCase().contains("unit_price"),
                    "无权限模型应保留 unitPrice 列: " + sql);

            // extData 中不应有 effective permission
            assertNull(ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY));
        }

        @Test
        @DisplayName("forcedSlice 可使用未声明为维度属性的内部物理字段")
        void hiddenPhysicalFieldForcedSliceApplied() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesHiddenMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption", "brand"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            SqlGenerationResult sqlResult = queryFacade.buildSqlOnly(ctx);

            String sql = sqlResult.getSql();
            assertNotNull(sql);
            assertTrue(sql.toLowerCase().contains("status"),
                    "SQL 应包含隐藏物理字段 status 的内部权限条件: " + sql);

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(queryRequest, 100)
            );
            List<Map<String, Object>> items = castItems(result);
            assertFalse(items.isEmpty(), "隐藏物理字段 forcedSlice 不应阻断成员查询");
            for (Map<String, Object> row : items) {
                assertEquals(List.of("id", "caption", "brand"), new ArrayList<>(row.keySet()),
                        "未请求内部字段时不应额外返回 status");
            }
        }
    }

    // ==================== 合并规则端到端验证 ====================

    @Nested
    @DisplayName("合并规则端到端")
    class MergeRuleE2ETests {

        @Test
        @DisplayName("TM+QM forcedSlice 不同字段合并（brand + id）")
        void forcedSliceMerge_differentFields() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            queryFacade.buildSqlOnly(ctx);

            SyntheticMemberEffectivePermission ep = (SyntheticMemberEffectivePermission)
                    ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);

            assertNotNull(ep.getForcedSlice());
            // TM: brand='Apple' + QM: id!=0 → 2 个条件
            assertEquals(2, ep.getForcedSlice().size());

            boolean hasBrand = ep.getForcedSlice().stream().anyMatch(s -> "brand".equals(s.getField()));
            boolean hasId = ep.getForcedSlice().stream().anyMatch(s -> "id".equals(s.getField()));
            assertTrue(hasBrand, "应保留 TM 的 brand 条件");
            assertTrue(hasId, "应保留 QM 的 id 条件");
        }

        @Test
        @DisplayName("TM+QM forcedOrderBy 同字段覆盖（caption: ASC→DESC）")
        void forcedOrderByMerge_sameField() {
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption"));

            ModelResultContext ctx = new ModelResultContext(
                    PagingRequest.buildPagingRequest(queryRequest, 20), null
            );
            queryFacade.buildSqlOnly(ctx);

            SyntheticMemberEffectivePermission ep = (SyntheticMemberEffectivePermission)
                    ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);

            assertNotNull(ep.getForcedOrderBy());
            assertEquals(1, ep.getForcedOrderBy().size());
            assertEquals("caption", ep.getForcedOrderBy().get(0).getField());
            assertEquals("DESC", ep.getForcedOrderBy().get(0).getDir()); // QM 覆盖 TM
        }
    }

    // ==================== 真实数据比对验证 ====================

    @Nested
    @DisplayName("真实数据比对")
    class RealDataTests {

        @Test
        @DisplayName("forcedSlice brand='Apple' 实际只查出 Apple 品牌")
        void forcedSlice_onlyAppleBrand_inResults() {
            // 原生 SQL 基线：只查 Apple 品牌且 id != 0
            String expectedSql = """
                    SELECT p.product_key AS id,
                           p.product_name AS caption,
                           p.brand AS brand
                      FROM dim_product_nested p
                     WHERE p.brand = 'Apple'
                       AND p.product_key != 0
                     ORDER BY p.product_name DESC
                    """;
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            // 通过 synthetic member-QM 查询（内部权限自动注入 brand='Apple' + id!=0）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption", "brand"));

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(queryRequest, 100)
            );
            List<Map<String, Object>> items = castItems(result);

            // 数量一致
            assertEquals(expectedRows.size(), items.size(),
                    "内部权限 forcedSlice 后查询结果数量应与原生 SQL 一致");
            assertFalse(items.isEmpty(), "Apple 品牌应有产品");

            // 每条记录的 brand 都是 Apple
            for (Map<String, Object> row : items) {
                assertEquals("Apple", row.get("brand"),
                        "所有返回的成员 brand 都应为 Apple（来自 TM forcedSlice）");
            }

            // ID 列表一致
            List<String> expectedIds = expectedRows.stream()
                    .map(r -> String.valueOf(r.get("id"))).collect(java.util.stream.Collectors.toList());
            List<String> actualIds = items.stream()
                    .map(r -> String.valueOf(r.get("id"))).collect(java.util.stream.Collectors.toList());
            assertEquals(expectedIds, actualIds, "成员 ID 列表应与原生 SQL 完全一致（含排序）");
        }

        @Test
        @DisplayName("visibleColumns 裁剪后返回列集合正确")
        void visibleColumns_returnedColumnsCorrect() {
            // QM 覆盖后 visibleColumns = [id, caption, brand]
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            // 请求全部允许列
            queryRequest.setColumns(List.of("id", "caption", "brand"));

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(queryRequest, 100)
            );
            List<Map<String, Object>> items = castItems(result);
            assertFalse(items.isEmpty());

            // 每条记录应只有 id、caption、brand 三列
            for (Map<String, Object> row : items) {
                assertTrue(row.containsKey("id"), "应包含 id");
                assertTrue(row.containsKey("caption"), "应包含 caption");
                assertTrue(row.containsKey("brand"), "应包含 brand");
                assertFalse(row.containsKey("unitPrice"), "不应包含 unitPrice（被 visibleColumns 裁剪）");
                assertFalse(row.containsKey("productId"), "不应包含 productId（QM 覆盖后移除）");
            }
        }

        @Test
        @DisplayName("forcedOrderBy caption DESC 实际排序与原生 SQL 一致")
        void forcedOrderBy_captionDesc_actuallyOrdered() {
            // 原生 SQL 基线：brand='Apple' AND id!=0，按 caption DESC 排序
            String expectedSql = """
                    SELECT p.product_key AS id,
                           p.product_name AS caption,
                           p.brand AS brand
                      FROM dim_product_nested p
                     WHERE p.brand = 'Apple'
                       AND p.product_key != 0
                     ORDER BY p.product_name DESC
                    """;
            List<Map<String, Object>> expectedRows = executeQuery(expectedSql);

            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesMemberPermQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption", "brand"));
            // 不指定 orderBy，应使用 forcedOrderBy: caption DESC

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(queryRequest, 100)
            );
            List<Map<String, Object>> items = castItems(result);
            assertFalse(items.isEmpty());
            assertEquals(expectedRows.size(), items.size(), "结果数量应一致");

            // 逐行比对：排序顺序应与原生 SQL ORDER BY product_name DESC 完全一致
            List<String> expectedCaptions = expectedRows.stream()
                    .map(r -> String.valueOf(r.get("caption")))
                    .collect(java.util.stream.Collectors.toList());
            List<String> actualCaptions = items.stream()
                    .map(r -> String.valueOf(r.get("caption")))
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(expectedCaptions, actualCaptions,
                    "forcedOrderBy caption DESC 排序应与原生 SQL 完全一致");
        }

        @Test
        @DisplayName("无权限模型查询不被 forcedSlice 限制")
        void noPermissionModel_notRestricted() {
            // 原生 SQL 基线：不含 brand='Apple' 条件
            String allProductsSql = """
                    SELECT COUNT(*) AS cnt FROM dim_product_nested
                    """;
            long totalCount = executeQueryForObject(allProductsSql, Long.class);

            String appleOnlySql = """
                    SELECT COUNT(*) AS cnt FROM dim_product_nested WHERE brand = 'Apple'
                    """;
            long appleCount = executeQueryForObject(appleOnlySql, Long.class);

            // 无权限模型应查出全部产品
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesNestedDimQueryModel#product");
            queryRequest.setColumns(List.of("id", "caption"));

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(queryRequest, 1000)
            );
            List<Map<String, Object>> items = castItems(result);

            assertEquals(totalCount, items.size(),
                    "无权限模型应查出全部产品（" + totalCount + "），而非仅 Apple（" + appleCount + "）");
            assertTrue(items.size() > appleCount,
                    "无权限模型的结果数应 > Apple 品牌数，证明不受 forcedSlice 限制");
        }
    }

    // ==================== 辅助方法 ====================

    private SliceRequestDef slice(String field, String op, Object value) {
        SliceRequestDef s = new SliceRequestDef();
        s.setField(field);
        s.setOp(op);
        s.setValue(value);
        return s;
    }

    private OrderRequestDef order(String field, String dir) {
        OrderRequestDef o = new OrderRequestDef();
        o.setField(field);
        o.setDir(dir);
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(PagingResultImpl result) {
        return (List<Map<String, Object>>) result.getItems();
    }
}
