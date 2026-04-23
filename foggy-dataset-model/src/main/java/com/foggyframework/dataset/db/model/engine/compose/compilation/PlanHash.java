package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Plan-subtree canonical hashing + MAX_PLAN_DEPTH DOS guard (M6 · 6.6).
 *
 * <p>Two features in one class because they share the same recursive plan
 * walker. {@link #planHash(QueryPlan)} returns an immutable, equality-stable
 * {@code List<Object>} that identifies a plan subtree structurally — two
 * plans that are semantically equivalent yield equal lists even when the
 * instances differ (Full-mode dedup from spec §6.6).</p>
 *
 * <p>MVP dedup (keyed by {@link System#identityHashCode} on the plan
 * reference) lives in {@code ComposePlanner._CompileState}; it runs before
 * {@link #planHash(QueryPlan)} and covers the typical "same instance
 * referenced twice" case for free. Full mode then catches the "two distinct
 * instances with identical shape" case.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.plan_hash} — the
 * {@code MAX_PLAN_DEPTH} constant, {@link #canonical(Object)} recursion
 * rules, {@link #planHash(QueryPlan)} tuple shapes, and
 * {@link #planDepth(QueryPlan)} are byte-for-byte aligned.</p>
 *
 * @since 8.2.0.beta
 */
public final class PlanHash {

    private PlanHash() { /* utility */ }

    /** Defense-in-depth cap on nested plan recursion.
     *
     *  <p>A real Compose Query rarely exceeds depth 3–5; depths above 32
     *  signal either script abuse or a generated-plan bug. The cap protects
     *  the M7 script runner from pathological DoS inputs without blocking
     *  any real-world query shape.</p>
     *
     *  <p>Enforced inside {@code ComposePlanner._compileAny} via
     *  {@code _CompileState.enterDepth()}; any recursion level greater than
     *  this value raises {@link ComposeCompileException} with
     *  {@link ComposeCompileErrorCodes#UNSUPPORTED_PLAN_SHAPE} at phase
     *  {@link ComposeCompileErrorCodes#PHASE_PLAN_LOWER}.</p> */
    public static final int MAX_PLAN_DEPTH = 32;

    // ------------------------------------------------------------------
    // Canonical — recursively convert Lists / Maps / arrays to a hashable,
    // order-stable shape so Full-mode dedup can compare subtrees.
    // ------------------------------------------------------------------

    /**
     * Recursively convert a value so the returned object has stable
     * {@code equals} / {@code hashCode} semantics usable as a map key.
     *
     * <p>Rules (mirrors Python {@code canonical()}):</p>
     * <ul>
     *   <li>{@link List} → {@code List.copyOf(...)} with each element
     *       recursively canonicalised — order preserved.</li>
     *   <li>{@link Map} → sorted entries as {@code List<List<Object>>}
     *       where each inner list is {@code List.of(key, canonical(value))};
     *       keys are compared by their {@code toString()} so
     *       {@code {"a":1,"b":2}} and {@code {"b":2,"a":1}} collapse.</li>
     *   <li>Anything else (String / primitive wrappers / null) is returned
     *       verbatim, trusting it is already hashable.</li>
     * </ul>
     */
    public static Object canonical(Object value) {
        if (value instanceof Map<?, ?>) {
            // sort by key toString, map value through canonical
            Map<?, ?> map = (Map<?, ?>) value;
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), canonical(e.getValue()));
            }
            List<List<Object>> pairs = new ArrayList<>(sorted.size());
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                pairs.add(List.of(e.getKey(), e.getValue() == null ? NULL_SENTINEL : e.getValue()));
            }
            return List.copyOf(pairs);
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Object> out = new ArrayList<>(list.size());
            for (Object v : list) {
                out.add(v == null ? NULL_SENTINEL : canonical(v));
            }
            return List.copyOf(out);
        }
        return value;
    }

    /** Sentinel used for {@code null} inside a {@code List.copyOf} path —
     *  {@code List.of} / {@code List.copyOf} reject nulls, so we normalise
     *  them once at canonicalisation time. */
    static final Object NULL_SENTINEL = new Object() {
        @Override public String toString() { return "<null>"; }
    };

    // ------------------------------------------------------------------
    // planHash — structural hash list per QueryPlan subclass.
    // ------------------------------------------------------------------

    /**
     * Canonical hash list for a plan subtree — stable across instances.
     *
     * <p>The returned list is an immutable {@code List<Object>} whose
     * {@link List#equals(Object)} / {@link List#hashCode()} recurse the
     * canonical form produced by {@link #canonical(Object)}. Callers may
     * use it as a {@code Map} key to dedup structurally equivalent plan
     * subtrees (Full-mode dedup).</p>
     *
     * <p>Unknown {@link QueryPlan} subclasses raise
     * {@link IllegalArgumentException} — fail-closed to avoid silent dedup
     * drift when a new plan type is introduced without updating this
     * module.</p>
     */
    public static List<Object> planHash(QueryPlan plan) {
        if (plan instanceof BaseModelPlan) {
            BaseModelPlan p = (BaseModelPlan) plan;
            return List.of(
                    "base",
                    p.model(),
                    canonical(p.columns()),
                    canonical(p.slice()),
                    canonical(p.groupBy()),
                    canonical(p.orderBy()),
                    boxNull(p.limit()),
                    boxNull(p.start()),
                    Boolean.valueOf(p.distinct()));
        }
        if (plan instanceof DerivedQueryPlan) {
            DerivedQueryPlan p = (DerivedQueryPlan) plan;
            return List.of(
                    "derived",
                    planHash(p.source()),
                    canonical(p.columns()),
                    canonical(p.slice()),
                    canonical(p.groupBy()),
                    canonical(p.orderBy()),
                    boxNull(p.limit()),
                    boxNull(p.start()),
                    Boolean.valueOf(p.distinct()));
        }
        if (plan instanceof UnionPlan) {
            UnionPlan p = (UnionPlan) plan;
            return List.of(
                    "union",
                    Boolean.valueOf(p.all()),
                    planHash(p.left()),
                    planHash(p.right()));
        }
        if (plan instanceof JoinPlan) {
            JoinPlan p = (JoinPlan) plan;
            List<List<String>> onEntries = new ArrayList<>(p.on().size());
            for (JoinOn o : p.on()) {
                onEntries.add(canonicalJoinOn(o));
            }
            return List.of(
                    "join",
                    p.type().token(),
                    planHash(p.left()),
                    planHash(p.right()),
                    List.copyOf(onEntries));
        }
        throw new IllegalArgumentException(
                "planHash received unsupported plan type "
                        + (plan == null ? "null" : plan.getClass().getName())
                        + "; extend PlanHash.planHash if a new QueryPlan subclass was added");
    }

    /** Canonical form of one {@link JoinOn} — {@code [left, op, right]}. */
    private static List<String> canonicalJoinOn(JoinOn o) {
        return List.of(o.left(), o.op(), o.right());
    }

    /** Box {@code null} into the {@link #NULL_SENTINEL}; otherwise pass
     *  through so {@code List.of(...)} (which rejects nulls) can accept
     *  the result. */
    private static Object boxNull(Object v) {
        return v == null ? NULL_SENTINEL : v;
    }

    // ------------------------------------------------------------------
    // planDepth — used by tests + ComposePlanner depth guard.
    // ------------------------------------------------------------------

    /**
     * Return the maximum nesting depth of {@code plan}.
     *
     * <p>A single {@link BaseModelPlan} is depth 1;
     * {@code DerivedQueryPlan(source=base)} is depth 2;
     * {@code UnionPlan(left=base, right=base)} is depth 2;
     * {@code DerivedQueryPlan(source=UnionPlan(...))} is depth 3; etc.</p>
     */
    public static int planDepth(QueryPlan plan) {
        if (plan instanceof BaseModelPlan) {
            return 1;
        }
        if (plan instanceof DerivedQueryPlan) {
            return 1 + planDepth(((DerivedQueryPlan) plan).source());
        }
        if (plan instanceof UnionPlan) {
            UnionPlan p = (UnionPlan) plan;
            return 1 + Math.max(planDepth(p.left()), planDepth(p.right()));
        }
        if (plan instanceof JoinPlan) {
            JoinPlan p = (JoinPlan) plan;
            return 1 + Math.max(planDepth(p.left()), planDepth(p.right()));
        }
        throw new IllegalArgumentException(
                "planDepth received unsupported plan type "
                        + (plan == null ? "null" : plan.getClass().getName()));
    }

    /** Comparator used by tests — exposed for golden-snapshot style debugging. */
    public static Comparator<Object> canonicalComparator() {
        return Comparator.comparing(o -> o == null ? "" : o.toString());
    }
}
