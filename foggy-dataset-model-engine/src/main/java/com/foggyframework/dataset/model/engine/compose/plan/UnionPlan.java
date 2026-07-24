package com.foggyframework.dataset.model.engine.compose.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set-union of two plans.
 *
 * <p>Both branches must come from the same data source — cross-datasource
 * union is rejected by the M6 compiler. M2 does not have datasource
 * information available, so that enforcement is deferred. Likewise,
 * column-count parity is an M4 responsibility.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.UnionPlan}.</p>
 *
 * @since 8.2.0.beta
 */
public final class UnionPlan extends QueryPlan {

    private final QueryPlan left;
    private final QueryPlan right;
    private final boolean all;

    private UnionPlan(Builder b) {
        requirePlan(b.left, "UnionPlan.left");
        requirePlan(b.right, "UnionPlan.right");
        this.left = b.left;
        this.right = b.right;
        this.all = b.all;
    }

    public QueryPlan left() { return left; }
    public QueryPlan right() { return right; }
    public boolean all() { return all; }

    @Override
    public List<BaseModelPlan> baseModelPlans() {
        List<BaseModelPlan> out = new ArrayList<>();
        out.addAll(left.baseModelPlans());
        out.addAll(right.baseModelPlans());
        return Collections.unmodifiableList(out);
    }

    @Override
    public Set<QueryPlan> collectVisiblePlans() {
        // Union = self ∪ left.visible ∪ right.visible (each branch's full subtree).
        Set<QueryPlan> set = identityPlanSet();
        set.add(this);
        set.addAll(left.collectVisiblePlans());
        set.addAll(right.collectVisiblePlans());
        return set;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private QueryPlan left;
        private QueryPlan right;
        private boolean all;

        public Builder left(QueryPlan v) { this.left = v; return this; }
        public Builder right(QueryPlan v) { this.right = v; return this; }
        public Builder all(boolean v) { this.all = v; return this; }

        public UnionPlan build() { return new UnionPlan(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnionPlan)) return false;
        UnionPlan p = (UnionPlan) o;
        return all == p.all
                && Objects.equals(left, p.left)
                && Objects.equals(right, p.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, all);
    }

    @Override
    public String toString() {
        return "UnionPlan{leftType=" + left.getClass().getSimpleName()
                + ", rightType=" + right.getClass().getSimpleName()
                + ", all=" + all + '}';
    }
}
