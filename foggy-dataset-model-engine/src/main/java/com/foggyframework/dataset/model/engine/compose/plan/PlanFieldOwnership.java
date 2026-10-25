package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import java.util.List;
import java.util.function.Function;

/** Proves field ownership through actual relation outputs; UNION is an opaque boundary. */
public final class PlanFieldOwnership {
    private PlanFieldOwnership() { }

    public static String visibleField(QueryPlan source, QueryPlan owner, String name,
                                      Function<QueryPlan, List<String>> outputs) {
        if (owner == null || owner == source) {
            List<String> names = outputs.apply(source);
            // Legacy metadata-less ports may leave a wildcard base unbound. Explicit
            // projections always require proof; production planning binds authoritative outputs.
            return names.contains(name) || source instanceof BaseModelPlan base
                    && base.columns().isEmpty() && names.isEmpty() ? name : null;
        }
        if (source instanceof DerivedQueryPlan derived) {
            String input = visibleField(derived.source(), owner, name, outputs);
            if (derived.columns().isEmpty()) return input;
            String found = null;
            boolean ambiguous = false;
            String preferred = input != null ? input : name;
            for (Object column : derived.columns()) {
                var parts = AliasExtractor.extract(column instanceof PlanColumnRef direct
                        ? direct.toColumnExpr() : String.valueOf(column));
                PlanColumnRef ref = QueryPlan.extractPlanRef(column);
                boolean plainRef = column instanceof PlanColumnRef
                        || column instanceof ProjectedColumn projected && projected.expr() instanceof PlanColumnRef;
                // An explicit typed projection can disambiguate a right-side duplicate.
                // Retain that producer proof across its new output alias.
                boolean ownsProjection = plainRef && ref != null && containsOwner(derived.source(), ref.plan())
                        && ref.name().equals(visibleField(ref.plan(), owner, name, outputs));
                boolean passesInput = !plainRef && input != null && input.equals(parts.expression());
                if (ownsProjection || passesInput) {
                    // Preserve the existing identity projection even when the user also
                    // emits renamed copies. Only multiple renamed-only routes are ambiguous.
                    if (parts.outputName().equals(preferred)) return preferred;
                    if (found != null && !found.equals(parts.outputName())) ambiguous = true;
                    found = parts.outputName();
                }
            }
            return ambiguous ? null : found;
        }
        if (source instanceof JoinPlan join) {
            String left = visibleField(join.left(), owner, name, outputs);
            String right = visibleField(join.right(), owner, name, outputs);
            if (left != null && right != null) return null;
            if (left != null) return left;
            if (right != null && !outputs.apply(join.left()).contains(right)) return right;
        }
        return null;
    }

    /** Explicit typed JOIN references may disambiguate duplicate right-side outputs. */
    public static String visibleFieldForReference(QueryPlan source, QueryPlan owner, String name,
                                                 Function<QueryPlan, List<String>> outputs) {
        if (source instanceof JoinPlan join && owner != source && owner != null) {
            String left = visibleFieldForReference(join.left(), owner, name, outputs);
            String right = visibleFieldForReference(join.right(), owner, name, outputs);
            return left != null && right != null ? null : left != null ? left : right;
        }
        return visibleField(source, owner, name, outputs);
    }

    private static boolean containsOwner(QueryPlan source, QueryPlan owner) {
        if (source == owner) return true;
        if (source instanceof DerivedQueryPlan derived) return containsOwner(derived.source(), owner);
        if (source instanceof JoinPlan join) return containsOwner(join.left(), owner) || containsOwner(join.right(), owner);
        return false;
    }
}
