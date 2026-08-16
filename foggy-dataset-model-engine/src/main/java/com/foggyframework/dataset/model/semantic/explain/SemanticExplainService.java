package com.foggyframework.dataset.model.semantic.explain;

import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

public interface SemanticExplainService {

    SemanticExplainResponse explain(
            String queryModel,
            SemanticExplainRequest request,
            SemanticRequestContext context
    );
}
