package com.foggyframework.dataset.db.model.engine.compose.plan;

/**
 * Explicit subquery slice value wrapper.
 *
 * <p>{@code subquery(plan, field)} is accepted as a base or derived
 * {@code IN}/{@code NOT IN} slice value. It does not add anti-join semantics;
 * lowering renders the referenced plan as a scalar SQL subquery.</p>
 */
public final class PlanSubquery {

    private final QueryPlan plan;
    private final String field;

    public PlanSubquery(QueryPlan plan, String field) {
        QueryPlan.requirePlan(plan, "PlanSubquery.plan");
        if (field != null && field.trim().isEmpty()) {
            throw new IllegalArgumentException("PlanSubquery.field must be non-empty when provided");
        }
        this.plan = plan;
        this.field = field == null ? null : field.trim();
    }

    public QueryPlan plan() { return plan; }
    public String field() { return field; }
}
