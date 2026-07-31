package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceDeleteRequest(
        String expectedCandidateRevision,
        List<String> paths
) {
}
