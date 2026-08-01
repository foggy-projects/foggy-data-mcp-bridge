package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspacePublishRequest(
        String expectedCandidateRevision,
        String expectedBaseBundleRevision,
        String expectedBaseNamespaceSourceRevision
) {
}
