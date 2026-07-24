package com.foggyframework.dataset.db.model.semantic.port;

import java.util.List;

/** Identity values supplied by a host adapter for one Compose invocation. */
public record ComposeCaller(
        String userId,
        String tenantId,
        List<String> roles,
        String deptId,
        String authorizationHint,
        String policySnapshotId
) {
    public ComposeCaller {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("ComposeCaller.userId must be non-blank");
        }
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
