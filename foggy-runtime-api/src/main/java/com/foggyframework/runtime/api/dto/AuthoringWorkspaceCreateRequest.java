package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceCreateRequest(
        String namespace,
        String sourceBundle
) {
}
