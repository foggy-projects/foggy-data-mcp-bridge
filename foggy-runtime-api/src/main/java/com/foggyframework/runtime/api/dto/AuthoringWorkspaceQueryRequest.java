package com.foggyframework.runtime.api.dto;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

public record AuthoringWorkspaceQueryRequest(
        String candidateRevision,
        SemanticQueryRequest request
) {
}
