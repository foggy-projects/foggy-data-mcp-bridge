package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base type for every Compose Query plan node (8.2.0.beta M2).
 *
 * <p>Concrete subclasses ({@link BaseModelPlan}, {@link DerivedQueryPlan},
 * {@link UnionPlan}, {@link JoinPlan}) are {@code final} and built through
 * explicit Builders. They are pure immutable descriptors — no execution
 * state, no datasource handle, no authority binding. Execution lands in M7
 * (the script runner threading a {@link ComposeQueryContext} through
 * {@link #execute(ComposeQueryContext)}); SQL rendering lands in M6.</p>
 *
 * <p><b>Layer-C whitelist (M9 sandbox scaffold §Layer C)</b>. The public
 * surface of any {@code QueryPlan} subclass is exactly these five methods:
 * <ul>
 *   <li>{@link #query(QueryOptions)}</li>
 *   <li>{@link #union(QueryPlan)} / {@link #union(QueryPlan, boolean)}</li>
 *   <li>{@link #join(QueryPlan, JoinType, List)} and string/raw overloads</li>
 *   <li>{@link #execute()} / {@link #execute(ComposeQueryContext)}</li>
 *   <li>{@link #toSql()}</li>
 * </ul>
 * Iteration, raw-SQL escape hatches, and memory filters must NOT appear.
 * {@link #baseModelPlans()} is package-private so the M5 authority-resolver
 * pipeline can walk the tree without exposing it to the sandbox.</p>
 *
 * <p>Cross-repo invariant: shape mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.QueryPlan}. When a rule
 * differs, Python is the source of truth — see execution prompt.</p>
 *
 * @since 8.2.0.beta
 */
public abstract class QueryPlan {

    /** Package-private constructor — subclasses are restricted to this
     *  package so the Layer-C whitelist cannot be bypassed by external
     *  subclassing. */
    QueryPlan() {
    }

    // ------------------------------------------------------------------
    // Layer-C public surface — exactly 5 methods.
    // ------------------------------------------------------------------

    /**
     * Build a {@link DerivedQueryPlan} whose {@code source} is this plan.
     * Equivalent to {@code Dsl.from(FromOptions.builder().source(this).columns(...).build())}
     * — same validation rules apply.
     */
    public final DerivedQueryPlan query(QueryOptions opts) {
        if (opts == null) {
            throw new IllegalArgumentException(
                    "QueryPlan.query(opts): opts must not be null");
        }
        return DerivedQueryPlan.builder()
                .source(this)
                .columns(opts.columns())
                .slice(opts.slice())
                .groupBy(opts.groupBy())
                .orderBy(opts.orderBy())
                .limit(opts.limit())
                .start(opts.start())
                .distinct(opts.distinct())
                .build();
    }

    /**
     * Build a {@code UNION} (distinct) of this plan with {@code other}.
     * Use {@link #union(QueryPlan, boolean)} with {@code all=true} for
     * {@code UNION ALL}.
     */
    public final UnionPlan union(QueryPlan other) {
        return union(other, false);
    }

    /**
     * Build a union of this plan with {@code other}.
     *
     * <p>M2 enforces structural rules only: {@code other} must be a
     * {@link QueryPlan} instance. Column-count parity and cross-datasource
     * rejection are deferred to M4 (schema derivation) and M6 (compiler).</p>
     */
    public final UnionPlan union(QueryPlan other, boolean all) {
        requirePlan(other, "union.other");
        return UnionPlan.builder().left(this).right(other).all(all).build();
    }

    /**
     * Build a join of this plan with {@code other}. See
     * {@link #join(QueryPlan, JoinType, List)} for details.
     * String overload — case-insensitive, normalised via
     * {@link JoinType#fromString(String)}.
     */
    public final JoinPlan join(QueryPlan other, String type, List<?> on) {
        return join(other, JoinType.fromString(type), on);
    }

    /**
     * Build a join of this plan with {@code other}.
     *
     * <p>{@code on} entries may be {@link JoinOn} instances or
     * {@link Map}-shaped dicts ({@code {"left":..., "op":..., "right":...}}) —
     * the latter get coerced via {@link JoinOn#fromMap(Map)}. Empty
     * {@code on} is rejected; cross joins are NOT in M2 scope.</p>
     */
    public final JoinPlan join(QueryPlan other, JoinType type, List<?> on) {
        requirePlan(other, "join.other");
        if (type == null) {
            throw new IllegalArgumentException(
                    "join.type must not be null");
        }
        if (on == null || on.isEmpty()) {
            throw new IllegalArgumentException(
                    "JoinPlan.on must be non-empty; cross joins are not "
                            + "supported in 8.2.0.beta M2");
        }
        List<JoinOn> coerced = coerceJoinOnList(on);
        return JoinPlan.builder()
                .left(this)
                .right(other)
                .type(type)
                .on(coerced)
                .build();
    }

    /**
     * Placeholder — wired in M7 (script runner ties the plan to execution
     * via the compiler produced in M6).
     *
     * @throws UnsupportedInM2Exception always in 8.2.0.beta M2
     */
    public final Object execute() {
        return execute(null);
    }

    /**
     * Placeholder — wired in M7.
     *
     * @throws UnsupportedInM2Exception always in 8.2.0.beta M2
     */
    public final Object execute(ComposeQueryContext context) {
        throw new UnsupportedInM2Exception(
                "QueryPlan.execute() is not implemented in 8.2.0.beta M2. "
                        + "Wiring arrives in M6 (SQL compile) + M7 (script runner).");
    }

    /**
     * Placeholder — wired in M6 (SQL compile + dialect rendering).
     *
     * @throws UnsupportedInM2Exception always in 8.2.0.beta M2
     */
    public final SqlPreview toSql() {
        throw new UnsupportedInM2Exception(
                "QueryPlan.toSql() is not implemented in 8.2.0.beta M2. "
                        + "Arrives in M6 together with the SQL compiler.");
    }

    // ------------------------------------------------------------------
    // Tree-walk helper — package-private (not in Layer-C public surface).
    // The authority-resolution pipeline (M5) batch-resolves bindings using
    // the leaf BaseModelPlan set returned here, in left-to-right preorder.
    // ------------------------------------------------------------------

    /**
     * Return the leaf {@link BaseModelPlan} nodes reachable from this node,
     * in left-to-right preorder.
     *
     * <p>Package-private on purpose — neither scripts nor the sandbox need
     * to walk the plan tree themselves; the M5 pipeline does it on their
     * behalf. Exposed in the package so tests and the resolver can call it
     * without going through reflection.</p>
     */
    abstract List<BaseModelPlan> baseModelPlans();

    // ------------------------------------------------------------------
    // Shared static helpers — package-private so the subclasses can reuse
    // them without duplicating validation logic.
    // ------------------------------------------------------------------

    static void requirePlan(Object value, String fieldName) {
        if (!(value instanceof QueryPlan)) {
            throw new IllegalArgumentException(
                    fieldName + " must be a QueryPlan instance, got: "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
        }
    }

    static void validateColumns(List<String> columns, String fieldName) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must be non-empty");
        }
        for (int i = 0; i < columns.size(); i++) {
            String c = columns.get(i);
            if (c == null || c.isEmpty()) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] must be a non-empty string, got: " + c);
            }
        }
    }

    static void validateStringList(List<String> values, String fieldName) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (v == null || v.isEmpty()) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] must be a non-empty string, got: " + v);
            }
        }
    }

    static void validatePagination(Integer limit, Integer start, String owner) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException(
                    owner + ".limit must be non-negative or null; got: " + limit);
        }
        if (start != null && start < 0) {
            throw new IllegalArgumentException(
                    owner + ".start must be non-negative or null; got: " + start);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<JoinOn> coerceJoinOnList(List<?> raw) {
        List<JoinOn> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object entry = raw.get(i);
            if (entry instanceof JoinOn) {
                out.add((JoinOn) entry);
            } else if (entry instanceof Map) {
                try {
                    out.add(JoinOn.fromMap((Map<String, ?>) entry));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                            "JoinPlan.on[" + i + "]: " + ex.getMessage(), ex);
                }
            } else {
                throw new IllegalArgumentException(
                        "JoinPlan.on[" + i + "] must be a JoinOn or Map, got: "
                                + (entry == null ? "null" : entry.getClass().getSimpleName()));
            }
        }
        return out;
    }
}
