package com.foggyframework.dataset.model.candidate;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;

import java.util.List;

/** Candidate response plus the exact revision and isolation evidence. */
public record CandidateQueryResult(
        SemanticQueryResponse response,
        CandidateQueryIdentity identity,
        String phase,
        List<String> diagnostics
) {
    public CandidateQueryResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
