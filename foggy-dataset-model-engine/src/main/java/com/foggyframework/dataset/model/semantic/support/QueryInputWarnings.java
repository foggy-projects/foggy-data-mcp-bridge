package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;

import java.util.List;

/** Propagates raw-input diagnostics without affecting query planning or legacy warnings. */
public final class QueryInputWarnings {

    private QueryInputWarnings() {
    }

    public static SemanticQueryResponse attach(
            SemanticQueryResponse response,
            SemanticQueryRequest request
    ) {
        if (response == null) {
            return null;
        }
        List<QueryInputWarning> warnings = request == null ? null : request.getQueryInputWarnings();
        response.setQueryInputWarnings(warnings == null || warnings.isEmpty()
                ? null
                : List.copyOf(warnings));
        return response;
    }
}
