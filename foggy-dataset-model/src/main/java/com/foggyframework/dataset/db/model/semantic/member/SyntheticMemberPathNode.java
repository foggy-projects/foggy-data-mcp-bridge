package com.foggyframework.dataset.db.model.semantic.member;

import java.util.List;

/**
 * synthetic member-QM 的维度路径节点。
 *
 * <p>root 节点的 {@code relativePath} 为空字符串；其直接子节点从各自有效名称开始，
 * 祖先路径之间用 {@code $} 拼接。</p>
 */
public record SyntheticMemberPathNode(
        String relativePath,
        String effectiveName,
        boolean root,
        boolean hierarchy,
        List<SyntheticMemberFieldSchema> fields,
        List<SyntheticMemberPathNode> children
) {
}
