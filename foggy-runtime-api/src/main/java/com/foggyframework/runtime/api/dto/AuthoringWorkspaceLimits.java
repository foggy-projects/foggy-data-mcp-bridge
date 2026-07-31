package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceLimits(
        int maxActiveWorkspaces,
        int maxResourcesPerRevision,
        long maxResourceBytes,
        long maxRevisionBytes,
        int maxBatchOperations,
        int maxPathBytes
) {
}
