package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.util.Optional;

/**
 * Narrow semantic planning capability required by the Compose compiler.
 *
 * <p>Authority bindings remain request-scoped and are assembled by Compose;
 * this port only runs governed per-model SQL generation and optional physical
 * field-expression resolution.</p>
 *
 * @since 9.4.0
 */
public interface ComposeSemanticPlanningPort {

    ComposeSqlGeneration generateComposeSql(
            String model,
            SemanticQueryRequest request,
            SemanticRequestContext context);

    default Optional<String> resolveFieldSqlExpression(
            String model, String field, String namespace) {
        return Optional.empty();
    }

    /**
     * Whether an empty field resolution is authoritative and must fail closed.
     */
    default boolean supportsFieldSqlResolution() {
        try {
            return getClass()
                    .getMethod("resolveFieldSqlExpression", String.class, String.class, String.class)
                    .getDeclaringClass() != ComposeSemanticPlanningPort.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
