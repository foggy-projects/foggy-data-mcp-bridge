package com.foggyframework.dataset.model.port;

/**
 * Composite advanced query port used by orchestrators that require both
 * context-aware execution and governed managed-relation execution.
 *
 * @since 9.3.5
 */
public interface AdvancedQueryExecutionPort
        extends InternalQueryExecutionPort, ManagedRelationExecutionPort {
}
