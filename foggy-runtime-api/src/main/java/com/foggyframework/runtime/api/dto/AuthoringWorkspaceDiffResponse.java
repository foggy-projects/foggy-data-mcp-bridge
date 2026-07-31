package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceDiffResponse(
        String workspaceId,
        String baseBundleRevision,
        String candidateRevision,
        List<ResourceChange> changes
) {
    public AuthoringWorkspaceDiffResponse {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public record ResourceChange(
            String path,
            String type,
            String changeType,
            String baseSha256,
            String candidateSha256,
            String baseContent,
            String candidateContent
    ) {
    }
}
