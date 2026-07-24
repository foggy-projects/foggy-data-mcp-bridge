package com.foggyframework.dataset.model.semantic.member;

import java.util.List;
import java.util.Map;

/**
 * synthetic member-QM 的 schema 视图。
 */
public record SyntheticMemberQueryModelSchema(
        String sourceModelName,
        String syntheticModelName,
        String dimensionFieldBase,
        SyntheticMemberPathNode root,
        List<SyntheticMemberFieldSchema> fields,
        Map<String, SyntheticMemberPathNode> nodeIndex,
        Map<String, SyntheticMemberFieldSchema> fieldIndex,
        boolean hierarchySupported
) {
}
