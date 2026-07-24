package com.foggyframework.dataset.model.semantic.port;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

/**
 * Narrow semantic-query execution boundary for internal engines.
 *
 * <p>Callers that only need to execute a governed semantic request should
 * depend on this port instead of the full semantic service surface.</p>
 */
@FunctionalInterface
public interface SemanticQueryExecutionPort {

    SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                     SemanticRequestContext context);
}
