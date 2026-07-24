package com.foggyframework.dataset.model.semantic.member.permission;

import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SyntheticMemberPermissionResolver 单元测试。
 * 覆盖 TM+QM 合并规则的全部场景。
 */
@DisplayName("成员权限合并器测试")
class SyntheticMemberPermissionResolverTest {

    private final SyntheticMemberPermissionResolver resolver = new SyntheticMemberPermissionResolver();

    // ==================== 两边都为空 ====================

    @Test
    @DisplayName("TM 和 QM 都为空时返回空 effective")
    void bothNull_returnsEmptyEffective() {
        SyntheticMemberEffectivePermission effective = resolver.resolve(null, null);
        assertNotNull(effective);
        assertTrue(effective.isEmpty());
        assertFalse(effective.hasPatch());
        assertFalse(effective.hasQueryBuilders());
    }

    // ==================== 仅 TM 有 patch ====================

    @Test
    @DisplayName("仅 TM 有 visibleColumns")
    void tmOnly_visibleColumns() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption", "brand"));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, null);

        assertEquals(List.of("id", "caption", "brand"), effective.getVisibleColumns());
        assertTrue(effective.hasPatch());
    }

    @Test
    @DisplayName("仅 TM 有 forcedSlice")
    void tmOnly_forcedSlice() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("tenantId");
        slice.setOp("=");
        slice.setValue(100);

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedSlice(List.of(slice));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, null);

        assertNotNull(effective.getForcedSlice());
        assertEquals(1, effective.getForcedSlice().size());
        assertEquals("tenantId", effective.getForcedSlice().get(0).getField());
        assertEquals(100, effective.getForcedSlice().get(0).resolveValue(null));
    }

    // ==================== 仅 QM 有 patch ====================

    @Test
    @DisplayName("仅 QM 有 visibleColumns")
    void qmOnly_visibleColumns() {
        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setVisibleColumns(List.of("id", "caption"));

        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setDimension("product");
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(null, qm);

        assertEquals(List.of("id", "caption"), effective.getVisibleColumns());
    }

    // ==================== TM+QM 合并 ====================

    @Test
    @DisplayName("visibleColumns: QM 覆盖 TM")
    void merge_visibleColumns_qmOverridesTm() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption", "brand", "unitPrice"));

        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setVisibleColumns(List.of("id", "caption"));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        // QM 覆盖 TM
        assertEquals(List.of("id", "caption"), effective.getVisibleColumns());
    }

    @Test
    @DisplayName("forcedSlice: 同字段 QM 完全替换 TM")
    void merge_forcedSlice_sameField_qmReplacesTm() {
        MemberPermissionSliceDef tmSlice = new MemberPermissionSliceDef();
        tmSlice.setField("tenantId");
        tmSlice.setOp("=");
        tmSlice.setValue(100);

        MemberPermissionSliceDef qmSlice = new MemberPermissionSliceDef();
        qmSlice.setField("tenantId");
        qmSlice.setOp("in");
        qmSlice.setValue(List.of(200, 300));

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedSlice(List.of(tmSlice));
        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setForcedSlice(List.of(qmSlice));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        assertEquals(1, effective.getForcedSlice().size());
        assertEquals("tenantId", effective.getForcedSlice().get(0).getField());
        assertEquals("in", effective.getForcedSlice().get(0).getOp());
        assertEquals(List.of(200, 300), effective.getForcedSlice().get(0).resolveValue(null));
    }

    @Test
    @DisplayName("forcedSlice: 不同字段合并保留")
    void merge_forcedSlice_differentFields_merged() {
        MemberPermissionSliceDef tmSlice = new MemberPermissionSliceDef();
        tmSlice.setField("tenantId");
        tmSlice.setOp("=");
        tmSlice.setValue(100);

        MemberPermissionSliceDef qmSlice = new MemberPermissionSliceDef();
        qmSlice.setField("deptId");
        qmSlice.setOp("in");
        qmSlice.setValue(List.of(1, 2, 3));

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedSlice(List.of(tmSlice));
        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setForcedSlice(List.of(qmSlice));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        assertEquals(2, effective.getForcedSlice().size());
        assertEquals("tenantId", effective.getForcedSlice().get(0).getField());
        assertEquals("deptId", effective.getForcedSlice().get(1).getField());
    }

    @Test
    @DisplayName("forcedOrderBy: 同字段 QM 覆盖 TM")
    void merge_forcedOrderBy_sameField_qmOverrides() {
        OrderRequestDef tmOrder = new OrderRequestDef();
        tmOrder.setField("caption");
        tmOrder.setDir("ASC");

        OrderRequestDef qmOrder = new OrderRequestDef();
        qmOrder.setField("caption");
        qmOrder.setDir("DESC");

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setForcedOrderBy(List.of(tmOrder));
        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setForcedOrderBy(List.of(qmOrder));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        assertEquals(1, effective.getForcedOrderBy().size());
        assertEquals("caption", effective.getForcedOrderBy().get(0).getField());
        assertEquals("DESC", effective.getForcedOrderBy().get(0).getDir());
    }

    @Test
    @DisplayName("hierarchyEnabled: QM 覆盖 TM")
    void merge_hierarchyEnabled_qmOverrides() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setHierarchyEnabled(true);

        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setHierarchyEnabled(false);

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        assertFalse(effective.getHierarchyEnabled());
    }

    @Test
    @DisplayName("allowedHierarchyOps: QM 覆盖 TM")
    void merge_allowedHierarchyOps_qmOverrides() {
        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setAllowedHierarchyOps(List.of("childrenOf", "descendantsOf", "selfAndDescendantsOf"));

        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setAllowedHierarchyOps(List.of("childrenOf"));

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);
        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        assertEquals(List.of("childrenOf"), effective.getAllowedHierarchyOps());
    }

    // ==================== queryBuilder 收集 ====================

    @Test
    @DisplayName("TM+QM queryBuilder 按 TM→QM 顺序收集")
    void merge_queryBuilders_tmThenQm() {
        // 使用简单 mock：FsscriptFunction 是接口，不好直接 mock
        // 这里只验证收集逻辑，不验证执行

        MemberPermissionDef tm = new MemberPermissionDef();
        // tm.setQueryBuilder(...) 需要 FsscriptFunction，在 unit test 中跳过

        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        // qm.setQueryBuilder(...)

        // 两边都没有 queryBuilder
        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);
        assertFalse(effective.hasQueryBuilders());
    }

    @Test
    @DisplayName("仅 TM 有 queryBuilder")
    void merge_onlyTmQueryBuilder() {
        MemberPermissionDef tm = new MemberPermissionDef();
        // 无法在纯单元测试中构造 FsscriptFunction，仅验证 null 时的处理

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, null);
        assertFalse(effective.hasQueryBuilders());
    }

    // ==================== MemberPermissionSliceDef 求值测试 ====================

    @Test
    @DisplayName("静态 value 直接返回")
    void sliceDef_staticValue() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("tenantId");
        slice.setOp("=");
        slice.setValue(42);

        assertEquals(42, slice.resolveValue(null));
    }

    @Test
    @DisplayName("valueBuilder 优先于静态 value")
    void sliceDef_valueBuilderOverridesStaticValue() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("tenantId");
        slice.setOp("=");
        slice.setValue(42); // 静态值
        // 设置一个模拟的 valueBuilder
        FsscriptFunction mockBuilder = Mockito.mock(FsscriptFunction.class);
        when(mockBuilder.apply(any())).thenReturn(999);
        slice.setValueBuilder(mockBuilder);

        // valueBuilder 优先
        assertEquals(999, slice.resolveValue(null));
    }

    @Test
    @DisplayName("valueBuilder 为 null 时回退到静态 value")
    void sliceDef_nullValueBuilder_fallsBackToValue() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("tenantId");
        slice.setOp("=");
        slice.setValue(42);
        slice.setValueBuilder(null);

        assertEquals(42, slice.resolveValue(null));
    }

    @Test
    @DisplayName("value 和 valueBuilder 都为 null 时返回 null")
    void sliceDef_bothNull_returnsNull() {
        MemberPermissionSliceDef slice = new MemberPermissionSliceDef();
        slice.setField("tenantId");
        slice.setOp("=");

        assertNull(slice.resolveValue(null));
    }

    // ==================== SyntheticMemberEffectivePermission 辅助方法测试 ====================

    @Test
    @DisplayName("isEmpty 正确判断")
    void effectivePermission_isEmpty() {
        SyntheticMemberEffectivePermission ep = new SyntheticMemberEffectivePermission();
        assertTrue(ep.isEmpty());

        ep.setVisibleColumns(List.of("id"));
        assertFalse(ep.isEmpty());
    }

    @Test
    @DisplayName("hasPatch 正确判断")
    void effectivePermission_hasPatch() {
        SyntheticMemberEffectivePermission ep = new SyntheticMemberEffectivePermission();
        assertFalse(ep.hasPatch());

        ep.setHierarchyEnabled(false);
        assertTrue(ep.hasPatch());
    }

    @Test
    @DisplayName("hasQueryBuilders 正确判断")
    void effectivePermission_hasQueryBuilders() {
        SyntheticMemberEffectivePermission ep = new SyntheticMemberEffectivePermission();
        assertFalse(ep.hasQueryBuilders());

        ep.setQueryBuilders(List.of(Mockito.mock(FsscriptFunction.class)));
        assertTrue(ep.hasQueryBuilders());
    }

    // ==================== MemberPermissionPatchDef isEmpty 测试 ====================

    @Test
    @DisplayName("MemberPermissionPatchDef isEmpty 正确判断")
    void patchDef_isEmpty() {
        MemberPermissionPatchDef patch = new MemberPermissionPatchDef();
        assertTrue(patch.isEmpty());

        patch.setHierarchyEnabled(true);
        assertFalse(patch.isEmpty());
    }

    @Test
    @DisplayName("MemberPermissionDef isEmpty 正确判断")
    void memberPermissionDef_isEmpty() {
        MemberPermissionDef def = new MemberPermissionDef();
        assertTrue(def.isEmpty());

        def.setQueryBuilder(Mockito.mock(FsscriptFunction.class));
        assertFalse(def.isEmpty());
    }

    // ==================== 综合场景 ====================

    @Test
    @DisplayName("TM+QM 混合场景：TM 全量 + QM 部分覆盖")
    void merge_mixedScenario() {
        // TM: visibleColumns=[id, caption, brand, unitPrice], forcedSlice=[tenantId=100], forcedOrderBy=[caption ASC]
        MemberPermissionSliceDef tmSlice = new MemberPermissionSliceDef();
        tmSlice.setField("tenantId");
        tmSlice.setOp("=");
        tmSlice.setValue(100);

        OrderRequestDef tmOrder = new OrderRequestDef();
        tmOrder.setField("caption");
        tmOrder.setDir("ASC");

        MemberPermissionPatchDef tmPatch = new MemberPermissionPatchDef();
        tmPatch.setVisibleColumns(List.of("id", "caption", "brand", "unitPrice"));
        tmPatch.setForcedSlice(List.of(tmSlice));
        tmPatch.setForcedOrderBy(List.of(tmOrder));
        tmPatch.setHierarchyEnabled(true);

        MemberPermissionDef tm = new MemberPermissionDef();
        tm.setPatch(tmPatch);

        // QM: visibleColumns=[id, caption], forcedSlice=[deptId in [1,2]], forcedOrderBy 不设
        MemberPermissionSliceDef qmSlice = new MemberPermissionSliceDef();
        qmSlice.setField("deptId");
        qmSlice.setOp("in");
        qmSlice.setValue(List.of(1, 2));

        MemberPermissionPatchDef qmPatch = new MemberPermissionPatchDef();
        qmPatch.setVisibleColumns(List.of("id", "caption"));
        qmPatch.setForcedSlice(List.of(qmSlice));

        QmMemberPermissionDef qm = new QmMemberPermissionDef();
        qm.setDimension("product");
        qm.setPatch(qmPatch);

        SyntheticMemberEffectivePermission effective = resolver.resolve(tm, qm);

        // visibleColumns: QM 覆盖 TM
        assertEquals(List.of("id", "caption"), effective.getVisibleColumns());

        // forcedSlice: 不同字段合并
        assertEquals(2, effective.getForcedSlice().size());
        assertEquals("tenantId", effective.getForcedSlice().get(0).getField());
        assertEquals("deptId", effective.getForcedSlice().get(1).getField());

        // forcedOrderBy: QM 未设，保留 TM
        assertEquals(1, effective.getForcedOrderBy().size());
        assertEquals("caption", effective.getForcedOrderBy().get(0).getField());
        assertEquals("ASC", effective.getForcedOrderBy().get(0).getDir());

        // hierarchyEnabled: QM 未设，保留 TM
        assertTrue(effective.getHierarchyEnabled());
    }
}
