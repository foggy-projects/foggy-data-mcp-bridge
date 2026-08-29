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

    /** Pure CTE alias for CTE placement; null for derived placement. */
    private final String cteAlias;

    /** Unquoted CTE output columns produced from the structured domain plan. */
    private final List<String> cteColumnAliases;

    /** CTE body without the {@code alias(columns) AS (...)} wrapper. */
    private final String cteBody;

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
