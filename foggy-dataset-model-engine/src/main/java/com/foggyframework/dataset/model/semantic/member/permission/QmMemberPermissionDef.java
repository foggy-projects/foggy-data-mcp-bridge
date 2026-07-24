package com.foggyframework.dataset.model.semantic.member.permission;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

/**
 * QM 级成员权限配置。
 * <p>挂在 {@code QM.memberPermissions[]}。
 * <p>通过 dimension 字段定位要覆盖的根维度，patch 覆盖 TM 默认配置。
 */
@Data
public class QmMemberPermissionDef {

    /** 目标维度名（对应 TM 中的维度 name 或 QM 中暴露的维度字段基名） */
    private String dimension;

    /** request 级 patch，覆盖 TM.patch */
    private MemberPermissionPatchDef patch;

    /** SQL 级权限增强脚本，与 TM.queryBuilder 顺序执行 */
    private FsscriptFunction queryBuilder;
}
