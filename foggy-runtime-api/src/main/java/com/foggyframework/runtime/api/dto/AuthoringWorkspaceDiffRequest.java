package com.foggyframework.runtime.api.dto;

public record AuthoringWorkspaceDiffRequest(
        String candidateRevision,
        Boolean includeContent
) {
}
