package com.foggyframework.dataset.model.semantic.member.permission;

import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import lombok.Data;

import java.util.List;

/**
 * 成员权限 patch 子结构。
 * <p>request 级生效：visibleColumns / forcedSlice / forcedOrderBy / hierarchyEnabled / allowedHierarchyOps。
 */
@Data
public class MemberPermissionPatchDef {

    /** 允许返回的列白名单 */
    private List<String> visibleColumns;

    /** 强制过滤条件 */
    private List<MemberPermissionSliceDef> forcedSlice;

    /** 强制排序 */
    private List<OrderRequestDef> forcedOrderBy;

    /** 是否允许层级操作（null 表示不限制） */
    private Boolean hierarchyEnabled;

    /** 允许的 hierarchy operator 白名单（null 表示不限制） */
    private List<String> allowedHierarchyOps;

    public boolean isEmpty() {
        return (visibleColumns == null || visibleColumns.isEmpty())
                && (forcedSlice == null || forcedSlice.isEmpty())
                && (forcedOrderBy == null || forcedOrderBy.isEmpty())
                && hierarchyEnabled == null
                && (allowedHierarchyOps == null || allowedHierarchyOps.isEmpty());
    }
}
