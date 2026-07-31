package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceResource(
        String path,
        String type,
        long size,
        String sha256,
        String content
) {
}
