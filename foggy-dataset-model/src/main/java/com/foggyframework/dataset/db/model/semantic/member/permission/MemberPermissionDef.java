package com.foggyframework.dataset.db.model.semantic.member.permission;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

/**
 * TM 维度级成员权限配置。
 * <p>挂在 {@code TM.dimensions[].memberPermission}。
 * <p>包含 patch（request 级改写）+ queryBuilder（SQL 级增强）两部分。
 */
@Data
public class MemberPermissionDef {

    /** request 级 patch：列裁剪、强制过滤、强制排序、层级开关 */
    private MemberPermissionPatchDef patch;

    /** SQL 级权限增强脚本。签名：queryBuilder(context) */
    private FsscriptFunction queryBuilder;

    public boolean isEmpty() {
        return (patch == null || patch.isEmpty()) && queryBuilder == null;
    }
}
