package com.foggyframework.dataset.model.candidate;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

/** One request-local candidate catalog and execution lifetime. */
public interface CandidateQuerySession extends AutoCloseable {

    CandidateQueryIdentity identity();

    CandidateQueryResult validate(
            String model,
            SemanticQueryRequest request,
            SemanticRequestContext context
    );

    CandidateQueryResult execute(
            String model,
            SemanticQueryRequest request,
            SemanticRequestContext context
    );

    @Override
    void close();
}
