package com.foggyframework.dataset.db.model.engine.compose.authority;

import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Walk a {@link QueryPlan} tree and return the unique {@link BaseModelPlan}
 * nodes by QM model name (first-occurrence wins).
 *
 * <p><b>Why collect by model name, not by {@code BaseModelPlan} identity.</b>
 * The same QM ({@code SaleOrderQM}) referenced twice in a script will
 * materialise as two distinct {@code BaseModelPlan} instances (different
 * columns / slice / limit etc. per call site). For authority resolution,
 * however, {@code (principal, namespace, model)} is the cache key — two
 * references to the same QM in the same script must produce a single
 * {@code AuthorityRequest.models[i]} entry and a single {@code ModelBinding}.</p>
 *
 * <p>Dedup rule:
 * <ul>
 *   <li>Walk the tree in left-to-right preorder
 *       ({@link QueryPlan#baseModelPlans()} already guarantees this).</li>
 *   <li>Keep the first {@code BaseModelPlan} encountered per {@code model}
 *       string.</li>
 *   <li>Later duplicates are discarded; they all consume the same binding.</li>
 * </ul></p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.collector.collect_base_models}.
 * Python raises {@code TypeError} on bad input; Java raises
 * {@link IllegalArgumentException} (more idiomatic); the two are semantically
 * equivalent for callers.</p>
 *
 * @since 8.2.0.beta
 */
public final class BaseModelPlanCollector {

    private BaseModelPlanCollector() { /* utility */ }

    /**
     * Return the unique {@link BaseModelPlan} nodes in {@code plan}, one per
     * {@code model} name, in first-occurrence order.
     *
     * @param plan root of a {@code QueryPlan} tree
     * @return ordered, duplicate-free (by {@code model}) unmodifiable list
     * @throws IllegalArgumentException when {@code plan} is {@code null} or
     *         not a {@link QueryPlan} instance (fail-closed: the authority
     *         pipeline refuses to bind unknown node shapes)
     * @throws IllegalStateException when {@code plan.baseModelPlans()} yields
     *         a non-{@code BaseModelPlan} element (defensive — should be
     *         impossible given the M2-frozen contract but still checked)
     */
    public static List<BaseModelPlan> collect(QueryPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException(
                    "collect(plan) requires a QueryPlan instance, got null");
        }

        Set<String> seenModels = new LinkedHashSet<>();
        List<BaseModelPlan> unique = new ArrayList<>();
        for (BaseModelPlan leaf : plan.baseModelPlans()) {
            if (leaf == null) {
                throw new IllegalStateException(
                        "QueryPlan.baseModelPlans() yielded a null element; "
                                + "refusing to proceed");
            }
            // baseModelPlans() returns List<BaseModelPlan> at compile time
            // (M2 contract) — defensive instanceof remains as a belt-and-
            // suspenders guard in case a subclass ever violates the contract
            // via unchecked casting.
            if (!(leaf instanceof BaseModelPlan)) {
                throw new IllegalStateException(
                        "QueryPlan.baseModelPlans() yielded non-BaseModelPlan "
                                + leaf.getClass().getSimpleName()
                                + "; refusing to proceed");
            }
            if (seenModels.add(leaf.model())) {
                unique.add(leaf);
            }
        }
        return Collections.unmodifiableList(unique);
    }
}
