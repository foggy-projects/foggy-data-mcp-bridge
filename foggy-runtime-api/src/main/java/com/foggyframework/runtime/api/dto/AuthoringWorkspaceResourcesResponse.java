package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceResourcesResponse(
        String workspaceId,
        String candidateRevision,
        List<AuthoringWorkspaceResource> resources
) {
    public AuthoringWorkspaceResourcesResponse {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
