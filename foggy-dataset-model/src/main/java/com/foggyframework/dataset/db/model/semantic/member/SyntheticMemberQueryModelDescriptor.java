package com.foggyframework.dataset.db.model.semantic.member;

/**
 * QM + fieldName -> synthetic member-QM 的解析结果。
 */
public record SyntheticMemberQueryModelDescriptor(
        String namespace,
        String sourceModelName,
        String syntheticModelName,
        String dimensionFieldBase,
        String matchedFieldName,
        String matchedNodePath,
        boolean hierarchyPathNode,
        SyntheticMemberQueryModelSchema schema
) {
    public String cacheKey() {
        return SyntheticMemberQueryModelResolver.buildCacheKey(namespace, sourceModelName, dimensionFieldBase);
    }
}
