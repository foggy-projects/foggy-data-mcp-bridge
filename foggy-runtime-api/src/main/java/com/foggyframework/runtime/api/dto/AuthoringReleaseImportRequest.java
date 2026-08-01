package com.foggyframework.runtime.api.dto;

public record AuthoringReleaseImportRequest(
        String namespace,
        String targetBundle,
        AuthoringReleasePackage releasePackage
) {
}
