package com.foggyframework.dataset.db.model.impl.model;

/**
 * Lightweight planning evidence for aggregate relation filter handling.
 */
public record AggregateRelationDiagnostic(
        String decision,
        String reasonCode,
        String field,
        String op,
        String target,
        String expression) {

    public static AggregateRelationDiagnostic pushed(String field, String op, String target, String expression) {
        return new AggregateRelationDiagnostic("pushed", null, field, op, target, expression);
    }

    public static AggregateRelationDiagnostic retained(String field, String op, String reasonCode) {
        return new AggregateRelationDiagnostic("retained", reasonCode, field, op, "outer", null);
    }

    public static AggregateRelationDiagnostic projectionRetained(String reasonCode) {
        return new AggregateRelationDiagnostic("retained", reasonCode, null, "projection", "projection", null);
    }

    public static AggregateRelationDiagnostic refused(String field, String op, String reasonCode) {
        return new AggregateRelationDiagnostic("refused", reasonCode, field, op, null, null);
    }
}
