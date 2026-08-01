package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspacePromotionRequest(
        String releasePackageId,
        String expectedCandidateRevision,
        String expectedBaseBundleRevision,
        String expectedBaseNamespaceSourceRevision
) {
}
