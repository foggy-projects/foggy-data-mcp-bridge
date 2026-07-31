package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceListResponse(
        List<AuthoringWorkspaceInfo> workspaces,
        List<String> warnings
) {
    public AuthoringWorkspaceListResponse {
        workspaces = workspaces == null ? List.of() : List.copyOf(workspaces);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
