package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceRecoverRequest(
        String expectedCandidateRevision,
        String publicationAttemptId
) {
}
