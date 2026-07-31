package com.foggyframework.dataset.model.candidate;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

/** Immutable identity of one request-local candidate source view. */
public record CandidateQueryIdentity(
        String namespace,
        String sourceBundle,
        String baseSourceRevision,
        String candidateRevision,
        CatalogIdentity catalogIdentity
) {
}
