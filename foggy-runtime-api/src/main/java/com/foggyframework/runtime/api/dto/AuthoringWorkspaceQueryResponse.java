package com.foggyframework.runtime.api.dto;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;

import java.util.List;

public record AuthoringWorkspaceQueryResponse(
        String workspaceId,
        String sourceBundle,
        String namespace,
        String baseBundleRevision,
        String baseNamespaceSourceRevision,
        String candidateRevision,
        CatalogIdentity catalogIdentity,
        String phase,
        SemanticQueryResponse response,
        List<String> diagnostics
) {
    public AuthoringWorkspaceQueryResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
