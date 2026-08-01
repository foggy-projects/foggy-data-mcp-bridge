package com.foggyframework.runtime.api.dto;

import java.util.List;

/** Portable, text-only immutable model release package. */
public record AuthoringReleasePackage(
        String formatVersion,
        String packageId,
        String sourceRuntimeApiVersion,
        String sourceNamespace,
        String sourceBundle,
        String candidateRevision,
        String baseBundleRevision,
        String baseNamespaceSourceRevision,
        String exportedAt,
        AuthoringWorkspaceInfo.ValidationEvidence validation,
        List<Dependency> dependencies,
        List<Resource> resources
) {
    public AuthoringReleasePackage {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public record Dependency(
            String bundle,
            String sourceType,
            String sourceIdentity,
            String artifactRevision
    ) {
    }

    public record Resource(
            String path,
            String type,
            long size,
            String sha256,
            String content
    ) {
    }
}
