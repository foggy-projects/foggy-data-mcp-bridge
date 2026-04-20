package com.foggyframework.dataset.db.model.semantic.member.permission;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelDimensionImpl;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryColumnImpl;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.SyntheticMemberInternalPatchStep;
import com.foggyframework.dataset.db.model.spi.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SyntheticMemberInternalPatchStep 单元测试。
 * 使用 Mockito mock QueryModelLoader 和 QueryModel，验证 patch 注入逻辑。
 */
@DisplayName("内部成员权限 Patch 注入测试")
class SyntheticMemberInternalPatchStepTest {

    private SyntheticMemberInternalPatchStep step;
    private QueryModelLoader mockLoader;

    @BeforeEach
    void setUp() throws Exception {
        step = new SyntheticMemberInternalPatchStep();
        mockLoader = Mockito.mock(QueryModelLoader.class);

        // 反射注入 mock queryModelLoader
        var field = SyntheticMemberInternalPatchStep.class.getDeclaredField("queryModelLoader");
        field.setAccessible(true);
        field.set(step, mockLoader);
    }

    // ==================== 基础跳过场景 ====================

    @Test
    @DisplayName("ctx 为 null 时直接跳过")
    void nullCtx_skip() {
        assertEquals(0, step.beforeQuery(null));
    }

    @Test
    @DisplayName("非 synthetic member-QM 时跳过")
    void nonSyntheticModel_skip() {
        ModelResultContext ctx = buildCtx("NormalQueryModel", List.of("id"));
        assertEquals(0, step.beforeQuery(ctx));
        // extData 中不应有权限对象
        assertNull(ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY));
    }

    @Test
    @DisplayName("无权限配置时跳过")
    void noPermission_skip() {
        // 源模型无权限配置
        QueryModel sourceModel = buildSourceModel("product", null, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption"));
        assertEquals(0, step.beforeQuery(ctx));
    }

    // ==================== visibleColumns 注入 ====================

    @Test
    @DisplayName("TM visibleColumns 限制请求列")
    void visibleColumns_restrictsRequestColumns() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求 [id, caption, brand]，但 visibleColumns 只允许 [id, caption]
        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption", "brand"));
        step.beforeQuery(ctx);

        DbQueryRequestDef req = ctx.getRequest().getParam();
        assertEquals(List.of("id", "caption"), req.getColumns());
    }

    @Test
    @DisplayName("visibleColumns 与请求列求交为空时抛异常")
    void visibleColumns_intersectionEmpty_throws() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求 [brand, unitPrice]，与 visibleColumns [id, caption] 无交集
        ModelResultContext ctx = buildCtx("TestQM#product", List.of("brand", "unitPrice"));
        assertThrows(Exception.class, () -> step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("请求未指定列时，visibleColumns 直接设为默认列")
    void visibleColumns_noRequestColumns_setsDefault() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求未指定 columns
        ModelResultContext ctx = buildCtx("TestQM#product", null);
        step.beforeQuery(ctx);

        assertEquals(List.of("id", "caption"), ctx.getRequest().getParam().getColumns());
    }

    // ==================== forcedSlice 注入 ====================

    @Test
    @DisplayName("forcedSlice 静态值注入到请求")
    void forcedSlice_staticValue_merged() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("id");
        slice.setOp("=");
        slice.setValue(42);

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedSlice(List.of(slice));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求已有 slice
        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption"));
        ctx.getRequest().getParam().setSlice(new ArrayList<>(List.of(
                new SliceRequestDef("caption", "like", "Apple%")
        )));

        step.beforeQuery(ctx);

        List<SliceRequestDef> mergedSlice = ctx.getRequest().getParam().getSlice();
        assertEquals(2, mergedSlice.size());
        // forcedSlice 在前
        assertEquals("id", mergedSlice.get(0).getField());
        assertEquals(42, mergedSlice.get(0).getValue());
        // 原始请求 slice 在后
        assertEquals("caption", mergedSlice.get(1).getField());
    }

    @Test
    @DisplayName("forcedSlice 字段不在 schema 中时跳过")
    void forcedSlice_fieldNotInSchema_skipped() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("nonExistentField");
        slice.setOp("=");
        slice.setValue(1);

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedSlice(List.of(slice));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id"));
        step.beforeQuery(ctx);

        // 非法字段被跳过，不应注入
        List<SliceRequestDef> resultSlice = ctx.getRequest().getParam().getSlice();
        assertTrue(resultSlice == null || resultSlice.isEmpty());
    }

    // ==================== forcedOrderBy 注入 ====================

    @Test
    @DisplayName("forcedOrderBy 与请求排序合并")
    void forcedOrderBy_merged() {
        OrderRequestDef forcedOrder = new OrderRequestDef();
        forcedOrder.setField("caption");
        forcedOrder.setDir("DESC");

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedOrderBy(List.of(forcedOrder));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求有 orderBy: id ASC
        OrderRequestDef requestOrder = new OrderRequestDef();
        requestOrder.setField("id");
        requestOrder.setDir("ASC");

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption"));
        ctx.getRequest().getParam().setOrderBy(new ArrayList<>(List.of(requestOrder)));

        step.beforeQuery(ctx);

        List<OrderRequestDef> mergedOrderBy = ctx.getRequest().getParam().getOrderBy();
        assertEquals(2, mergedOrderBy.size());
        assertEquals("id", mergedOrderBy.get(0).getField()); // 请求排序保留
        assertEquals("caption", mergedOrderBy.get(1).getField()); // forced 追加
        assertEquals("DESC", mergedOrderBy.get(1).getDir());
    }

    @Test
    @DisplayName("forcedOrderBy 同字段替换请求排序")
    void forcedOrderBy_sameField_replaces() {
        OrderRequestDef forcedOrder = new OrderRequestDef();
        forcedOrder.setField("caption");
        forcedOrder.setDir("DESC");

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedOrderBy(List.of(forcedOrder));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        // 请求也有 caption ASC
        OrderRequestDef requestOrder = new OrderRequestDef();
        requestOrder.setField("caption");
        requestOrder.setDir("ASC");

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption"));
        ctx.getRequest().getParam().setOrderBy(new ArrayList<>(List.of(requestOrder)));

        step.beforeQuery(ctx);

        List<OrderRequestDef> mergedOrderBy = ctx.getRequest().getParam().getOrderBy();
        assertEquals(1, mergedOrderBy.size());
        assertEquals("caption", mergedOrderBy.get(0).getField());
        assertEquals("DESC", mergedOrderBy.get(0).getDir()); // forced 覆盖
    }

    // ==================== hierarchy 校验 ====================

    @Test
    @DisplayName("hierarchyEnabled=false 时层级操作被拦截")
    void hierarchyDisabled_rejectsHierarchyOp() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setHierarchyEnabled(false);

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id"));
        ctx.getRequest().getParam().setSlice(new ArrayList<>(List.of(
                new SliceRequestDef("id", "childrenOf", 1)
        )));

        assertThrows(Exception.class, () -> step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("allowedHierarchyOps 白名单外的操作被拦截")
    void allowedHierarchyOps_rejectsDisallowed() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setAllowedHierarchyOps(List.of("childrenOf"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id"));
        ctx.getRequest().getParam().setSlice(new ArrayList<>(List.of(
                new SliceRequestDef("id", "descendantsOf", 1)
        )));

        assertThrows(Exception.class, () -> step.beforeQuery(ctx));
    }

    @Test
    @DisplayName("allowedHierarchyOps 白名单内的操作通过")
    void allowedHierarchyOps_allowsPermitted() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setAllowedHierarchyOps(List.of("childrenOf", "descendantsOf"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id"));
        ctx.getRequest().getParam().setSlice(new ArrayList<>(List.of(
                new SliceRequestDef("id", "childrenOf", 1)
        )));

        // 不应抛异常
        assertDoesNotThrow(() -> step.beforeQuery(ctx));
    }

    // ==================== TM+QM 合并场景 ====================

    @Test
    @DisplayName("QM visibleColumns 覆盖 TM，forcedSlice 合并")
    void tmAndQm_merged() {
        // TM: visibleColumns=[id, caption, brand], forcedSlice=[id = 1]
        MemberPermissionSliceDef tmSlice = new MemberPermissionSliceDef();
        tmSlice.setField("id");
        tmSlice.setOp("=");
        tmSlice.setValue(1);

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption", "brand"));
        tmPatch.setForcedSlice(List.of(tmSlice));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        // QM: visibleColumns=[id, caption], forcedSlice=[caption like 'A%']
        MemberPermissionSliceDef qmSlice = new MemberPermissionSliceDef();
        qmSlice.setField("caption");
        qmSlice.setOp("like");
        qmSlice.setValue("A%");

        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setVisibleColumns(List.of("id", "caption"));
        qmPatch.setForcedSlice(List.of(qmSlice));

        QmMemberPermissionDef qmPerm = new QmMemberPermissionDef();
        qmPerm.setDimension("product");
        qmPerm.setPatch(qmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, List.of(qmPerm));
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id", "caption", "brand"));
        step.beforeQuery(ctx);

        DbQueryRequestDef req = ctx.getRequest().getParam();

        // visibleColumns: QM [id, caption] 覆盖 TM
        assertEquals(List.of("id", "caption"), req.getColumns());

        // forcedSlice: 两个不同字段合并
        assertEquals(2, req.getSlice().size());
    }

    // ==================== effective permission 存储 ====================

    @Test
    @DisplayName("effective permission 存储到 extData 供后续步骤使用")
    void effectivePermission_storedInExtData() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption"));

        MemberPermissionDef tmPerm = new MemberPermissionDef();
        tmPerm.setPatch(tmPatch);

        QueryModel sourceModel = buildSourceModel("product", tmPerm, null);
        when(mockLoader.getJdbcQueryModel(eq("TestQM"), any())).thenReturn(sourceModel);

        ModelResultContext ctx = buildCtx("TestQM#product", List.of("id"));
        step.beforeQuery(ctx);

        Object stored = ctx.getExtData().get(SyntheticMemberInternalPatchStep.EFFECTIVE_PERMISSION_KEY);
        assertNotNull(stored);
        assertInstanceOf(SyntheticMemberEffectivePermission.class, stored);
    }

    // ==================== 辅助方法 ====================

    private ModelResultContext buildCtx(String modelName, List<String> columns) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(modelName);
        queryRequest.setColumns(columns != null ? new ArrayList<>(columns) : null);

        // 构建 mock QueryModel（synthetic member-QM 的运行时模型）
        QueryModel mockModel = Mockito.mock(QueryModel.class);
        when(mockModel.getName()).thenReturn(modelName);

        // schema 字段：id, caption, brand, unitPrice
        List<DbQueryColumn> queryColumns = new ArrayList<>();
        for (String field : List.of("id", "caption", "brand", "unitPrice")) {
            DbQueryColumn col = Mockito.mock(DbQueryColumn.class);
            when(col.getName()).thenReturn(field);
            queryColumns.add(col);
        }
        when(mockModel.getJdbcQueryColumns()).thenReturn(queryColumns);

        ModelResultContext ctx = new ModelResultContext(
                PagingRequest.buildPagingRequest(queryRequest, 20), null
        );
        ctx.setQueryModel(mockModel);
        return ctx;
    }

    /**
     * 构建一个 mock 的源 QM，包含 TM 维度权限和 QM 成员权限。
     */
    private QueryModel buildSourceModel(String dimName,
                                         MemberPermissionDef tmPermission,
                                         List<QmMemberPermissionDef> qmPermissions) {
        // 构建 TM 维度
        DbModelDimensionImpl dimension = new DbModelDimensionImpl();
        dimension.setName(dimName);
        dimension.setAlias(dimName);
        dimension.setMemberPermission(tmPermission);

        // 构建 TableModel
        DbTableModelImpl tableModel = new DbTableModelImpl();
        tableModel.setDimensions(List.of(dimension));

        // 构建 QueryModel
        QueryModel mockModel = Mockito.mock(QueryModel.class);
        when(mockModel.getJdbcModel()).thenReturn(tableModel);

        // QueryModelSupport 用于存储 QM 级别的 memberPermissions
        QueryModelSupport mockSupport = Mockito.mock(QueryModelSupport.class);
        when(mockSupport.getMemberPermissions()).thenReturn(qmPermissions);
        when(mockModel.getDecorate(QueryModelSupport.class)).thenReturn(mockSupport);

        return mockModel;
    }
}
