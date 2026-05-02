package com.foggyframework.dataset.db.model.engine.pivot.cascade;

/**
 * Structured error codes for PIVOT-91-C2 cascade Generate refusal paths.
 */
public enum PivotCascadeErrorCode {
    PIVOT_CASCADE_ORDER_BY_REQUIRED,
    PIVOT_CASCADE_SQL_REQUIRED,
    PIVOT_CASCADE_NON_ADDITIVE_REJECTED,
    PIVOT_CASCADE_CROSS_AXIS_REJECTED,
    PIVOT_CASCADE_TREE_REJECTED,
    PIVOT_CASCADE_SCOPE_UNSUPPORTED
}
