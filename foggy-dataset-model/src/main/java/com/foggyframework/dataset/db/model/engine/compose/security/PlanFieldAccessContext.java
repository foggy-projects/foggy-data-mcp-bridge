package com.foggyframework.dataset.db.model.engine.compose.security;

import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <b>G10 PR4</b> · Per-{@link QueryPlan} permission view used by
 * {@code ComposePlanAwarePermissionValidator} to route a column reference
 * back to the producing plan's {@link ModelBinding#fieldAccess()}.
 *
 * <p>Two parallel maps, both identity-keyed by {@link QueryPlan}:
 * <ul>
 *   <li>{@link #planBindings} — the full {@link ModelBinding}
 *       (covers {@code fieldAccess} / {@code deniedColumns} /
 *       {@code systemSlice}; PR4 only consults {@code fieldAccess}, but
 *       carrying the binding lets PR5 / future passes evolve without
 *       another constructor change).</li>
 *   <li>{@link #fieldAccessSets} — pre-cached
 *       {@code Set<String>} view of each plan's fieldAccess (or
 *       {@code null} when the binding has none). Lookups by plan land in
 *       O(1) via {@link IdentityHashMap}.</li>
 * </ul>
 *
 * <p><b>Identity vs. equality.</b> Same rationale as
 * {@code ComposePlanner.CompileState.planAliasMap}: two structurally-equal
 * plan instances should map to distinct bindings (each represents a
 * separate compile-time entity), and the validator must resolve via the
 * actual referent identity that {@link com.foggyframework.dataset.db.model.engine.compose.plan.PlanColumnRef#plan()}
 * returns.</p>
 *
 * <p><b>Lifecycle.</b> Constructed once per {@code compilePlanToSql}
 * invocation by walking the plan tree and pairing each
 * {@link com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan}
 * with its {@link ModelBinding} via the model name. Discarded after the
 * SQL is emitted; not persisted.</p>
 *
 * @since 8.3.0.beta
 */
public final class PlanFieldAccessContext {

    /** {@code QueryPlan} → its full {@link ModelBinding}. Identity-keyed. */
    private final Map<QueryPlan, ModelBinding> planBindings;

    /** Cached {@code Set<String>} view of each plan's {@code fieldAccess},
     *  or absent when that plan's binding declares none. */
    private final Map<QueryPlan, Set<String>> fieldAccessSets;

    private PlanFieldAccessContext(Builder b) {
        Map<QueryPlan, ModelBinding> bindings = new IdentityHashMap<>(b.planBindings);
        Map<QueryPlan, Set<String>> sets = new IdentityHashMap<>(b.planBindings.size());
        for (Map.Entry<QueryPlan, ModelBinding> e : bindings.entrySet()) {
            List<String> fa = e.getValue() == null ? null : e.getValue().fieldAccess();
            if (fa != null) {
                sets.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(fa)));
            }
        }
        this.planBindings = Collections.unmodifiableMap(bindings);
        this.fieldAccessSets = Collections.unmodifiableMap(sets);
    }

    /**
     * Resolve the {@code fieldAccess} whitelist for {@code plan}.
     *
     * @return the plan's whitelist, or {@code null} when either
     *         (a) the plan is not registered in this context (caller
     *         must fail-closed via {@code COLUMN_PLAN_NOT_BOUND}), or
     *         (b) the plan is registered but its binding declares no
     *         {@code fieldAccess} (no whitelist → unrestricted access
     *         for that plan; caller treats as "allow"). The two cases
     *         are distinguished by {@link #containsPlan(QueryPlan)}.
     */
    public Set<String> resolveFieldAccess(QueryPlan plan) {
        if (plan == null) return null;
        return fieldAccessSets.get(plan);
    }

    /** Whether {@code plan} has a registered binding (regardless of
     *  whether that binding declares a {@code fieldAccess} list). */
    public boolean containsPlan(QueryPlan plan) {
        return plan != null && planBindings.containsKey(plan);
    }

    /** The full {@link ModelBinding} for {@code plan}, or {@code null}
     *  when unregistered. PR4 only reads {@code fieldAccess}; future
     *  passes may consume {@code deniedColumns} / {@code systemSlice}. */
    public ModelBinding bindingOf(QueryPlan plan) {
        return plan == null ? null : planBindings.get(plan);
    }

    public int size() { return planBindings.size(); }
    public boolean isEmpty() { return planBindings.isEmpty(); }

    public static Builder builder() { return new Builder(); }

    /** Convenience: empty context. Validators receiving this must fail
     *  closed on every {@code PlanColumnRef} since no plan is bound. */
    public static PlanFieldAccessContext empty() {
        return builder().build();
    }

    public static final class Builder {
        private final Map<QueryPlan, ModelBinding> planBindings = new IdentityHashMap<>();

        public Builder bind(QueryPlan plan, ModelBinding binding) {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(binding, "binding");
            this.planBindings.put(plan, binding);
            return this;
        }

        public PlanFieldAccessContext build() {
            return new PlanFieldAccessContext(this);
        }
    }
}
