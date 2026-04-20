package com.foggyframework.dataset.db.model.semantic.member.permission;

import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.fsscript.exp.FsscriptFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 TM 维度级 + QM 维度级成员权限合并为运行时 effective permission。
 * <p>
 * 合并规则（按需求文档）：
 * <ul>
 *   <li>visibleColumns：QM 覆盖 TM（后者覆盖前者，不做并集）</li>
 *   <li>forcedSlice：同字段 QM 完全替换 TM，不同字段合并保留</li>
 *   <li>forcedOrderBy：同字段 QM 覆盖 TM</li>
 *   <li>hierarchyEnabled：QM 覆盖 TM</li>
 *   <li>allowedHierarchyOps：QM 覆盖 TM</li>
 *   <li>queryBuilder：TM 和 QM 都保留，按 TM → QM 顺序执行</li>
 * </ul>
 */
public class SyntheticMemberPermissionResolver {

    /**
     * 合并 TM + QM 成员权限。
     *
     * @param tmPermission TM 维度级配置（可为 null）
     * @param qmPermission QM 维度级配置（可为 null）
     * @return 合并后的 effective permission，如果两边都为空则返回空 effective
     */
    public SyntheticMemberEffectivePermission resolve(MemberPermissionDef tmPermission,
                                                       QmMemberPermissionDef qmPermission) {
        SyntheticMemberEffectivePermission effective = new SyntheticMemberEffectivePermission();

        MemberPermissionPatchDef tmPatch = tmPermission != null ? tmPermission.getPatch() : null;
        MemberPermissionPatchDef qmPatch = qmPermission != null ? qmPermission.getPatch() : null;
        FsscriptFunction tmQb = tmPermission != null ? tmPermission.getQueryBuilder() : null;
        FsscriptFunction qmQb = qmPermission != null ? qmPermission.getQueryBuilder() : null;

        // --- patch 合并 ---
        effective.setVisibleColumns(mergeVisibleColumns(tmPatch, qmPatch));
        effective.setForcedSlice(mergeForcedSlice(tmPatch, qmPatch));
        effective.setForcedOrderBy(mergeForcedOrderBy(tmPatch, qmPatch));
        effective.setHierarchyEnabled(mergeHierarchyEnabled(tmPatch, qmPatch));
        effective.setAllowedHierarchyOps(mergeAllowedHierarchyOps(tmPatch, qmPatch));

        // --- queryBuilder 收集（TM → QM 顺序） ---
        List<FsscriptFunction> builders = new ArrayList<>();
        if (tmQb != null) {
            builders.add(tmQb);
        }
        if (qmQb != null) {
            builders.add(qmQb);
        }
        effective.setQueryBuilders(builders.isEmpty() ? null : builders);

        return effective;
    }

    // ==================== patch 合并子方法 ====================

    /**
     * visibleColumns：QM 覆盖 TM，不做并集。
     */
    private List<String> mergeVisibleColumns(MemberPermissionPatchDef tmPatch,
                                              MemberPermissionPatchDef qmPatch) {
        List<String> qmCols = qmPatch != null ? qmPatch.getVisibleColumns() : null;
        if (qmCols != null && !qmCols.isEmpty()) {
            return qmCols;
        }
        return tmPatch != null ? tmPatch.getVisibleColumns() : null;
    }

    /**
     * forcedSlice：同字段 QM 完全替换 TM，不同字段合并保留。
     */
    private List<MemberPermissionSliceDef> mergeForcedSlice(MemberPermissionPatchDef tmPatch,
                                                             MemberPermissionPatchDef qmPatch) {
        List<MemberPermissionSliceDef> tmSlice = tmPatch != null ? tmPatch.getForcedSlice() : null;
        List<MemberPermissionSliceDef> qmSlice = qmPatch != null ? qmPatch.getForcedSlice() : null;

        if (isEmpty(tmSlice) && isEmpty(qmSlice)) {
            return null;
        }
        if (isEmpty(tmSlice)) {
            return qmSlice;
        }
        if (isEmpty(qmSlice)) {
            return tmSlice;
        }

        // 以 field 为 key，QM 完全替换 TM 同字段项
        Map<String, MemberPermissionSliceDef> merged = new LinkedHashMap<>();
        for (MemberPermissionSliceDef s : tmSlice) {
            if (s.getField() != null) {
                merged.put(s.getField(), s);
            }
        }
        for (MemberPermissionSliceDef s : qmSlice) {
            if (s.getField() != null) {
                merged.put(s.getField(), s); // QM 替换 TM
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * forcedOrderBy：同字段 QM 覆盖 TM。
     */
    private List<OrderRequestDef> mergeForcedOrderBy(MemberPermissionPatchDef tmPatch,
                                                      MemberPermissionPatchDef qmPatch) {
        List<OrderRequestDef> tmOrder = tmPatch != null ? tmPatch.getForcedOrderBy() : null;
        List<OrderRequestDef> qmOrder = qmPatch != null ? qmPatch.getForcedOrderBy() : null;

        if (isEmpty(tmOrder) && isEmpty(qmOrder)) {
            return null;
        }
        if (isEmpty(tmOrder)) {
            return qmOrder;
        }
        if (isEmpty(qmOrder)) {
            return tmOrder;
        }

        Map<String, OrderRequestDef> merged = new LinkedHashMap<>();
        for (OrderRequestDef o : tmOrder) {
            if (o.getField() != null) {
                merged.put(o.getField(), o);
            }
        }
        for (OrderRequestDef o : qmOrder) {
            if (o.getField() != null) {
                merged.put(o.getField(), o);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * hierarchyEnabled：QM 覆盖 TM。
     */
    private Boolean mergeHierarchyEnabled(MemberPermissionPatchDef tmPatch,
                                           MemberPermissionPatchDef qmPatch) {
        Boolean qmVal = qmPatch != null ? qmPatch.getHierarchyEnabled() : null;
        if (qmVal != null) {
            return qmVal;
        }
        return tmPatch != null ? tmPatch.getHierarchyEnabled() : null;
    }

    /**
     * allowedHierarchyOps：QM 覆盖 TM。
     */
    private List<String> mergeAllowedHierarchyOps(MemberPermissionPatchDef tmPatch,
                                                   MemberPermissionPatchDef qmPatch) {
        List<String> qmOps = qmPatch != null ? qmPatch.getAllowedHierarchyOps() : null;
        if (qmOps != null && !qmOps.isEmpty()) {
            return qmOps;
        }
        return tmPatch != null ? tmPatch.getAllowedHierarchyOps() : null;
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
