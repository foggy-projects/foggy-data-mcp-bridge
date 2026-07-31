package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceSaveRequest(
        String expectedCandidateRevision,
        List<ResourceFile> files
) {
    public record ResourceFile(String path, String content) {
    }
}
