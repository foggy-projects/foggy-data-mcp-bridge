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
        String expression,
        String relationAlias,
        String relationModel,
        String joinPath) {

    public static AggregateRelationDiagnostic pushed(String field, String op, String target, String expression) {
        return new AggregateRelationDiagnostic("pushed", null, field, op, target, expression, null, null, null);
    }

    public static AggregateRelationDiagnostic retained(String field, String op, String reasonCode) {
        return new AggregateRelationDiagnostic("retained", reasonCode, field, op, "outer", null, null, null, null);
    }

    public static AggregateRelationDiagnostic projectionRetained(String reasonCode) {
        return new AggregateRelationDiagnostic("retained", reasonCode, null, "projection", "projection", null, null, null, null);
    }

    public static AggregateRelationDiagnostic refused(String field, String op, String reasonCode) {
        return new AggregateRelationDiagnostic("refused", reasonCode, field, op, null, null, null, null, null);
    }

    public AggregateRelationDiagnostic withRelation(String relationAlias, String relationModel, String joinPath) {
        return new AggregateRelationDiagnostic(
                decision,
                reasonCode,
                field,
                op,
                target,
                expression,
                relationAlias,
                relationModel,
                joinPath);
    }
}
