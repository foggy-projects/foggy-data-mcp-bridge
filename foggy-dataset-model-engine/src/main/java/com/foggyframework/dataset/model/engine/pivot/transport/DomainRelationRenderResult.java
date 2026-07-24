package com.foggyframework.dataset.model.engine.pivot.transport;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class DomainRelationRenderResult {
    /**
     * The SQL fragment (e.g. CTE definition or derived table subquery).
     */
    private final String sqlFragment;

    /**
     * Flattened parameters.
     */
    private final List<Object> params;

    /**
     * The join predicate string (e.g. "base.col <=> _d.col").
     */
    private final String joinPredicate;

    /**
     * Where the engine should place this fragment.
     */
    private final DomainTransportPlacement placement;
}
