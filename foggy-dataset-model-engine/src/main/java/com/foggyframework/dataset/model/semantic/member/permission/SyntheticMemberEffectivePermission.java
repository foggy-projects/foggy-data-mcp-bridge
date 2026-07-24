package com.foggyframework.dataset.model.semantic.member.permission;

import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

import java.util.List;

/**
 * TM + QM 合并后的运行时成员权限对象。
 * <p>由 {@link SyntheticMemberPermissionResolver} 生成，供 InternalPatchStep 和 QueryBuilderStep 消费。
 */
@Data
public class SyntheticMemberEffectivePermission {

    /** 合并后的可见列白名单（QM 覆盖 TM） */
    private List<String> visibleColumns;

    /** 合并后的强制过滤（同字段 QM 完全替换 TM，不同字段保留） */
    private List<MemberPermissionSliceDef> forcedSlice;

    /** 合并后的强制排序（同字段 QM 覆盖 TM） */
    private List<OrderRequestDef> forcedOrderBy;

    /** 层级操作开关 */
    private Boolean hierarchyEnabled;

    /** 允许的 hierarchy operator 白名单 */
    private List<String> allowedHierarchyOps;

    /** TM → QM 顺序收集的 queryBuilder 列表 */
    private List<FsscriptFunction> queryBuilders;

    public boolean isEmpty() {
        return (visibleColumns == null || visibleColumns.isEmpty())
                && (forcedSlice == null || forcedSlice.isEmpty())
                && (forcedOrderBy == null || forcedOrderBy.isEmpty())
                && hierarchyEnabled == null
                && (allowedHierarchyOps == null || allowedHierarchyOps.isEmpty())
                && (queryBuilders == null || queryBuilders.isEmpty());
    }

    public boolean hasPatch() {
        return (visibleColumns != null && !visibleColumns.isEmpty())
                || (forcedSlice != null && !forcedSlice.isEmpty())
                || (forcedOrderBy != null && !forcedOrderBy.isEmpty())
                || hierarchyEnabled != null
                || (allowedHierarchyOps != null && !allowedHierarchyOps.isEmpty());
    }

    public boolean hasQueryBuilders() {
        return queryBuilders != null && !queryBuilders.isEmpty();
    }
}
