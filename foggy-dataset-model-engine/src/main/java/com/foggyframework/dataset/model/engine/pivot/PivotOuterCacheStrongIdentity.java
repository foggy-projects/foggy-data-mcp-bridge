package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Cache-safe projection of one atomic catalog resolution.
 *
 * <p>The canonical form is length framed and versioned before hashing. It uses
 * logical lifecycle identities only; connection targets and credentials are
 * deliberately outside this type.</p>
 */
record PivotOuterCacheStrongIdentity(
        CatalogIdentity catalogIdentity,
        String canonicalModelName,
        List<DatasourceBindingIdentity> dependencyBindings
) {

    static final String STATUS_COMPLETE = "complete";
    static final String STATUS_MISSING = "missing";
    static final String STATUS_INCOMPLETE = "incomplete";
    static final String STATUS_CONFLICT = "conflict";

    static final String REFUSAL_MISSING = "lifecycle_identity_missing";
    static final String REFUSAL_INCOMPLETE = "lifecycle_identity_incomplete";
    static final String REFUSAL_CONFLICT = "lifecycle_identity_conflict";

    PivotOuterCacheStrongIdentity {
        dependencyBindings = List.copyOf(dependencyBindings);
    }

    static Assessment assess(CatalogResolution<QueryModel> resolution,
                             String expectedNamespace) {
        if (resolution == null) {
            return Assessment.refused(STATUS_MISSING, REFUSAL_MISSING);
        }
        int observedBindingCount = resolution.dependencyBindings() == null
                ? 0
                : resolution.dependencyBindings().size();

        CatalogIdentity identity = resolution.catalogIdentity();
        if (identity == null
                || !CatalogIdentity.canonicalNamespace(expectedNamespace).equals(identity.namespace())) {
            return Assessment.refused(STATUS_CONFLICT, REFUSAL_CONFLICT, observedBindingCount);
        }

        QueryModel model = resolution.model();
        String modelName;
        try {
            modelName = model == null ? null : model.getName();
        } catch (RuntimeException e) {
            return Assessment.refused(STATUS_CONFLICT, REFUSAL_CONFLICT, observedBindingCount);
        }
        if (resolution.canonicalName() == null
                || resolution.canonicalName().isBlank()
                || modelName == null
                || modelName.isBlank()
                || !resolution.canonicalName().equals(modelName)) {
            return Assessment.refused(STATUS_CONFLICT, REFUSAL_CONFLICT, observedBindingCount);
        }

        Map<String, DatasourceBindingIdentity> bindings = resolution.dependencyBindings();
        if (!resolution.bindingIdentityComplete() || bindings == null) {
            return Assessment.refused(STATUS_INCOMPLETE, REFUSAL_INCOMPLETE, observedBindingCount);
        }
        if (model instanceof JdbcQueryModel && bindings.isEmpty()) {
            return Assessment.refused(STATUS_INCOMPLETE, REFUSAL_INCOMPLETE, observedBindingCount);
        }

        List<DatasourceBindingIdentity> sorted = new ArrayList<>();
        for (Map.Entry<String, DatasourceBindingIdentity> entry : bindings.entrySet()) {
            DatasourceBindingIdentity binding = entry.getValue();
            if (entry.getKey() == null
                    || binding == null
                    || !entry.getKey().equals(binding.bindingKey())) {
                return Assessment.refused(STATUS_CONFLICT, REFUSAL_CONFLICT, observedBindingCount);
            }
            sorted.add(binding);
        }
        sorted.sort(DatasourceBindingIdentity::compareTo);
        return Assessment.complete(new PivotOuterCacheStrongIdentity(
                identity, resolution.canonicalName(), sorted));
    }

    String canonicalValue() {
        StringBuilder encoded = new StringBuilder("pivot-outer-identity-v2");
        append(encoded, "namespace", catalogIdentity.namespace());
        append(encoded, "catalogGeneration", catalogIdentity.generation().value());
        append(encoded, "sourceRevision", catalogIdentity.sourceRevision().value());
        append(encoded, "canonicalModel", canonicalModelName);
        append(encoded, "bindingCount", String.valueOf(dependencyBindings.size()));
        for (DatasourceBindingIdentity binding : dependencyBindings) {
            append(encoded, "bindingKey", binding.bindingKey());
            append(encoded, "backendId", binding.backendId());
            append(encoded, "bindingGeneration", binding.generation().value());
        }
        return encoded.toString();
    }

    String identityHash() {
        return sha256(canonicalValue());
    }

    int bindingCount() {
        return dependencyBindings.size();
    }

    @Override
    public String toString() {
        return "PivotOuterCacheStrongIdentity[identityHash=" + identityHash()
                + ", bindingCount=" + bindingCount() + "]";
    }

    private static void append(StringBuilder encoded, String label, String value) {
        appendFramed(encoded, label);
        appendFramed(encoded, value == null ? "" : value);
    }

    private static void appendFramed(StringBuilder encoded, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        encoded.append(bytes.length).append(':').append(value);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    record Assessment(PivotOuterCacheStrongIdentity identity,
                      String status,
                      String refusalReason,
                      int bindingCount) {

        static Assessment complete(PivotOuterCacheStrongIdentity identity) {
            return new Assessment(identity, STATUS_COMPLETE, null, identity.bindingCount());
        }

        static Assessment refused(String status, String refusalReason) {
            return refused(status, refusalReason, 0);
        }

        static Assessment refused(String status, String refusalReason, int bindingCount) {
            return new Assessment(null, status, refusalReason, Math.max(0, bindingCount));
        }

        boolean cacheable() {
            return identity != null && refusalReason == null;
        }

        /**
         * An incomplete binding projection is unsafe for cache reuse but still
         * pins the model/catalog generation to prevent a multi-phase hybrid.
         */
        boolean pinnable() {
            return STATUS_COMPLETE.equals(status) || STATUS_INCOMPLETE.equals(status);
        }

        String identityHash() {
            return identity == null ? null : identity.identityHash();
        }

    }
}
