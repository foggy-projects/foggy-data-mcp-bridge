package com.foggyframework.dataset.db.model.semantic.member;

/**
 * synthetic member-QM 的字段定义。
 *
 * <p>{@code name} 是对外暴露字段名，{@code sourceRef} 是回指到原 QM 的来源字段。</p>
 */
public record SyntheticMemberFieldSchema(
        String name,
        String sourceRef,
        String nodePath,
        SyntheticMemberFieldKind kind,
        boolean reserved,
        boolean hierarchyScoped
) {
}
