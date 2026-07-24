package com.foggyframework.dataset.db.model.semantic.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Engine-neutral SQL generation result used by internal semantic consumers.
 */
public record SemanticSqlGeneration(String sql, List<Object> params) {

    public SemanticSqlGeneration {
        params = params == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(params));
    }
}
