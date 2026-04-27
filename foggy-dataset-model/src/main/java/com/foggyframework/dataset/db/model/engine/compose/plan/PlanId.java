package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * G10 — Transient identity key for a {@link QueryPlan} node within a single
 * compile session.
 *
 * <p>{@code PlanId} carries plan provenance through the schema / compile / permission
 * pipeline so post-join disambiguation (G5 F5) and plan-routed permissions
 * can resolve a column back to its originating plan. PR1 (真零行为变化)
 * adds the type only — no downstream code consumes it yet.</p>
 *
 * <h3>Identity contract (G10 spec v2 §4.3)</h3>
 *
 * <ul>
 *   <li><b>{@code equals}</b> compares strictly by <i>referent identity</i>:
 *       {@code ref.get() == other.ref.get()}. <em>Never</em> uses
 *       {@code identityHash} for equality — {@code System.identityHashCode}
 *       can collide for distinct objects, and using it for equality would
 *       silently route two unrelated plans to the same alias / binding.</li>
 *   <li><b>{@code hashCode}</b> returns the cached {@code identityHash}
 *       (= {@code System.identityHashCode(plan)} captured at construction).
 *       Used only for hash-bucket allocation. Hash collisions are still
 *       resolved correctly because {@code equals} compares the referent.</li>
 *   <li><b>Transient semantics</b>: valid only within a single compile
 *       session. <em>Not serializable</em>, <em>not cross-request reusable</em>,
 *       <em>must not enter persistent hash / cache keys</em>. Backed by
 *       {@link WeakReference} so a long-lived {@code PlanId} cannot pin
 *       its plan tree in heap.</li>
 *   <li><b>GC behaviour</b>: once the referent is collected,
 *       {@link #resolve()} returns {@code null} and {@link #equals(Object)}
 *       (with a non-null other referent) returns {@code false}. Downstream
 *       code <em>must</em> handle {@code null} from {@code resolve()}
 *       fail-closed.</li>
 * </ul>
 *
 * @since 8.3.0.beta (G10 PR1 — types only, no downstream consumers yet)
 */
public final class PlanId {

    /** Captured at construction; cached for {@link #hashCode()}. */
    private final int identityHash;

    /**
     * Weak reference to the plan node. {@code WeakReference} avoids
     * pinning large plan trees in heap during long-running query
     * pipelines.
     */
    private final WeakReference<QueryPlan> ref;

    private PlanId(QueryPlan plan) {
        Objects.requireNonNull(plan, "PlanId.of(plan): plan must not be null");
        this.identityHash = System.identityHashCode(plan);
        this.ref = new WeakReference<>(plan);
    }

    /**
     * Construct a {@code PlanId} for the given plan.
     *
     * @param plan the plan whose identity to capture; must not be null
     * @return a new {@code PlanId}
     * @throws NullPointerException if {@code plan} is null
     */
    public static PlanId of(QueryPlan plan) {
        return new PlanId(plan);
    }

    /**
     * @return the referenced plan, or {@code null} if it has been
     *         garbage-collected since this {@code PlanId} was constructed
     */
    public QueryPlan resolve() {
        return ref.get();
    }

    /**
     * Strict referent-identity equality. Two {@code PlanId} are equal iff
     * both still hold the <i>same</i> plan object (by Java {@code ==}).
     * If either referent has been GC'd to {@code null}, equality is
     * {@code false} (except {@code this == other} short-circuit).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanId)) return false;
        QueryPlan a = this.ref.get();
        QueryPlan b = ((PlanId) o).ref.get();
        // Identity equality on the referents. Both being null still returns
        // false (false-rich): a GC'd PlanId has no usable identity, callers
        // must fail-closed rather than treat it as equivalent to another GC'd id.
        return a != null && a == b;
    }

    /**
     * @return cached {@code System.identityHashCode(plan)} from construction
     */
    @Override
    public int hashCode() {
        return identityHash;
    }

    @Override
    public String toString() {
        QueryPlan plan = ref.get();
        return "PlanId{hash=" + identityHash
                + ", referent=" + (plan == null ? "<gc>" : plan.getClass().getSimpleName())
                + '}';
    }
}
