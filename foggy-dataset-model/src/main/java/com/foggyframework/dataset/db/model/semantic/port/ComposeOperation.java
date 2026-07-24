package com.foggyframework.dataset.db.model.semantic.port;

/**
 * Host-facing Compose operation. This is an endpoint operation, not a query
 * execution phase; query execution continues to use the shared
 * {@code QueryExecutionPhase} contract.
 */
public enum ComposeOperation {
    VALIDATE,
    PREVIEW,
    EXECUTE
}
