package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;

import java.util.*;

/**
 * Request-local admission and observation of the governance actually used by Pivot dependencies.
 */
public final class PivotCacheProofSession {
    private final AuthorizationSignatureService signatures;
    private final Map<String, String> admitted = new LinkedHashMap<>();
    private boolean valid = true;
    private int observed;
    private boolean executionObserved;

    public PivotCacheProofSession(AuthorizationSignatureService signatures) {
        this.signatures = Objects.requireNonNull(signatures);
    }

    /** Snapshot only user conditions, before any governance extension mutates the request. */
    public static List<String> conditions(ModelResultContext context) {
        var request = context.getRequest().getParam();
        List<String> result = new ArrayList<>();
        if (!collect(request.getSlice(), "where", result)
                || !collect(request.getHaving(), "having", result)) return null;
        return result;
    }

    private static boolean collect(
            List<? extends CondRequestDef> conditions, String kind, List<String> result) {
        if (conditions == null) return true;
        for (var condition : conditions) {
            var encoded =
                    AuthorizationSignatureService.canonicalFingerprint(conditionValue(condition));
            if (encoded.isEmpty()) return false;
            result.add(kind + ":" + encoded.get());
        }
        return true;
    }

    private static Object conditionValue(CondRequestDef condition) {
        if (condition == null) return null;
        if (condition._isLogicalGroup())
            return Arrays.asList(
                    condition._getGroupLink(),
                    condition._getGroupChildren().stream()
                            .map(PivotCacheProofSession::conditionValue)
                            .toList());
        return Arrays.asList(
                condition.getField(),
                condition.getOp(),
                condition.getValue(),
                condition.getMaxDepth(),
                condition.getExpr());
    }

    public Optional<String> admit(ModelResultContext context, List<String> original, String scope) {
        var key = shape(context);
        var proof = proof(context, original, scope);
        if (key.isEmpty() || proof.isEmpty()) {
            valid = false;
            return Optional.empty();
        }
        String previous = admitted.putIfAbsent(key.get(), proof.get());
        if (previous != null && !previous.equals(proof.get())) valid = false;
        return valid ? proof : Optional.empty();
    }

    public void observe(ModelResultContext context, List<String> original, String scope) {
        observed++;
        var key = shape(context);
        var proof = proof(context, original, scope);
        if (key.isEmpty() || proof.isEmpty() || !proof.get().equals(admitted.get(key.get())))
            valid = false;
    }

    public void markExecuted() {
        executionObserved = true;
    }

    public boolean canStore() {
        return valid && executionObserved && observed > 0;
    }

    private Optional<String> proof(
            ModelResultContext context, List<String> original, String scope) {
        if (original == null || scope == null) return Optional.empty();
        var signature = signatures.compute(context);
        var current = conditions(context);
        if (signature.isEmpty() || !signature.get().publicIdentity() || current == null)
            return Optional.empty();
        List<String> added = new ArrayList<>(current), removed = new ArrayList<>(original);
        for (String item : original) if (added.remove(item)) removed.remove(item);
        Collections.sort(added);
        Collections.sort(removed);
        return AuthorizationSignatureService.canonicalFingerprint(
                List.of(signature.get().value(), scope, added, removed));
    }

    private Optional<String> shape(ModelResultContext context) {
        var request = context.getRequest().getParam();
        List<Object> groups = new ArrayList<>(), orders = new ArrayList<>();
        if (request.getGroupBy() != null)
            for (var item : request.getGroupBy())
                groups.add(Arrays.asList(item.getField(), item.getAgg()));
        if (request.getOrderBy() != null)
            for (var item : request.getOrderBy())
                orders.add(Arrays.asList(item.getField(), item.getDir(), item.isNullLast()));
        return AuthorizationSignatureService.canonicalFingerprint(
                Arrays.asList(
                        context.getNamespace(),
                        context.getCanonicalModelName(),
                        request.getColumns(),
                        groups,
                        orders));
    }
}
