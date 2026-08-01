package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceRollbackRequest(
        String releasePackageId,
        String expectedCandidateRevision,
        String publicationAttemptId
) {
}
