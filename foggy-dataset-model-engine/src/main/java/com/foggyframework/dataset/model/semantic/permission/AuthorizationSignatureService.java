package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes a stable cache identity from the final engine permission snapshot.
 */
@Component
public class AuthorizationSignatureService {

    private static final String VERSION = "authorization-signature-v1";

    public Optional<AuthorizationSignature> compute(ModelResultContext context) {
        if (context == null || context.getPermissionDecision() == null
                || context.getQueryModel() == null) {
            return Optional.empty();
        }
        PermissionDecision decision = context.getPermissionDecision();
        Instant now = Instant.now();
        if (!decision.isAllow() || decision.isExpired(now)) {
            return Optional.empty();
        }
        CatalogIdentity catalog = context.getCatalogIdentity();
        if (catalog == null || catalog.generation() == null
                || context.getCanonicalModelName() == null
                || !context.isBindingIdentityComplete()) {
            return Optional.empty();
        }
        // Protected shared caches require both a policy version and a bounded
        // validity window. Otherwise the permission can still execute, but it
        // is deliberately non-cacheable.
        if (!decision.isPublicDecision()
                && (decision.getPolicyVersion() == null || decision.getExpiresAt() == null)) {
            return Optional.empty();
        }

        CanonicalEncoder encoder = new CanonicalEncoder();
        encoder.add("version", VERSION);
        encoder.add("identity", decision.isPublicDecision() ? "PUBLIC" : "PROTECTED");
        encoder.add("action", context.getPermissionAction());
        encoder.add("resource", permissionResource(context));
        encoder.add("namespace", CatalogIdentity.canonicalNamespace(context.getNamespace()));
        encoder.add("catalogGeneration", catalog.generation().value());
        encoder.add("sourceRevision", catalog.sourceRevision() == null
                ? null : catalog.sourceRevision().value());
        encoder.add("canonicalModel", context.getCanonicalModelName());
        encoder.add("decisionId", decision.getDecisionId());
        encoder.add("policyVersion", decision.getPolicyVersion());
        encoder.add("providerFingerprint", decision.getProviderFingerprint());
        encoder.add("expiresAt", decision.getExpiresAt());
        encoder.add("attributes", decision.getAttributes());
        encoder.add("rowPredicates", encodePredicates(decision.getRowPredicates()));
        encoder.add("fieldAccess", sortedValues(context.getFieldAccess()));
        encoder.add("deniedColumns", encodeDeniedColumns(context.getDeniedColumns()));
        encoder.add("systemSlice", encodeConditions(context.getSystemSlice()));
        encoder.add("scopeKey", scopeKey(context));
        if (!encoder.cacheable()) {
            return Optional.empty();
        }

        String prefix = decision.isPublicDecision() ? "PUBLIC:" : "AUTH:";
        return Optional.of(new AuthorizationSignature(
                prefix + sha256(encoder.value()),
                decision.isPublicDecision(),
                decision.getExpiresAt()
        ));
    }

    private String permissionResource(ModelResultContext context) {
        String canonical = context.getCanonicalModelName();
        int separator = canonical == null ? -1 : canonical.indexOf('#');
        return separator > 0 ? canonical.substring(0, separator) : canonical;
    }

    private Object scopeKey(ModelResultContext context) {
        if (context.getExtData() == null) {
            return null;
        }
        Object value = context.getExtData().get("preAggregationScopeKey");
        return value != null ? value : context.getExtData().get("authorizationScopeKey");
    }

    private Object encodePredicates(List<PermissionPredicate> predicates) {
        List<String> values = new ArrayList<>();
        for (PermissionPredicate predicate : predicates) {
            CanonicalEncoder encoder = new CanonicalEncoder();
            encoder.add("origin", predicate.getOrigin());
            encoder.add("binding", predicate.getBinding());
            encoder.add("field", predicate.getField());
            encoder.add("operator", predicate.getOperator());
            encoder.add("valueType", predicate.getValueType());
            encoder.add("value", predicate.getValue());
            encoder.add("referencedFields", sortedValues(predicate.getReferencedFields()));
            encoder.add("proofStatus", predicate.getProofStatus());
            if (!encoder.cacheable()) {
                return UnsupportedCanonicalValue.INSTANCE;
            }
            values.add(encoder.value());
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private Object encodeDeniedColumns(List<DeniedPhysicalColumn> columns) {
        if (columns == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (DeniedPhysicalColumn column : columns) {
            if (column == null) {
                values.add("null");
                continue;
            }
            CanonicalEncoder encoder = new CanonicalEncoder();
            encoder.add("schema", column.getSchema());
            encoder.add("table", column.getTable());
            encoder.add("column", column.getColumn());
            values.add(encoder.value());
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private Object encodeConditions(List<? extends CondRequestDef> conditions) {
        if (conditions == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (CondRequestDef condition : conditions) {
            Optional<String> encoded = encodeCondition(condition);
            if (encoded.isEmpty()) {
                return UnsupportedCanonicalValue.INSTANCE;
            }
            values.add(encoded.get());
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private Optional<String> encodeCondition(CondRequestDef condition) {
        if (condition == null) {
            return Optional.of("null");
        }
        CanonicalEncoder encoder = new CanonicalEncoder();
        if (condition._isLogicalGroup()) {
            List<String> children = new ArrayList<>();
            for (CondRequestDef child : condition._getGroupChildren()) {
                Optional<String> encoded = encodeCondition(child);
                if (encoded.isEmpty()) {
                    return Optional.empty();
                }
                children.add(encoded.get());
            }
            children.sort(Comparator.naturalOrder());
            encoder.add("link", condition._getGroupLink());
            encoder.add("children", children);
        } else {
            encoder.add("field", condition.getField());
            encoder.add("op", condition.getOp());
            encoder.add("value", condition.getValue());
            encoder.add("maxDepth", condition.getMaxDepth());
            encoder.add("expr", condition.getExpr());
        }
        return encoder.cacheable() ? Optional.of(encoder.value()) : Optional.empty();
    }

    private Object sortedValues(Collection<?> values) {
        if (values == null) {
            return null;
        }
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            CanonicalEncoder encoder = new CanonicalEncoder();
            encoder.add("value", value);
            if (!encoder.cacheable()) {
                return UnsupportedCanonicalValue.INSTANCE;
            }
            encoded.add(encoder.value());
        }
        encoded.sort(Comparator.naturalOrder());
        return encoded;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static final class CanonicalEncoder {
        private final StringBuilder value = new StringBuilder();
        private boolean cacheable = true;

        private void add(String label, Object item) {
            append(label);
            encode(item);
        }

        private void encode(Object item) {
            if (item == null) {
                append("null");
            } else if (item == UnsupportedCanonicalValue.INSTANCE) {
                cacheable = false;
            } else if (item instanceof CharSequence || item instanceof Number
                    || item instanceof Boolean || item instanceof Enum<?>
                    || item instanceof TemporalAccessor) {
                append(item.getClass().getName());
                append(String.valueOf(item));
            } else if (item instanceof Map<?, ?> map) {
                List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
                entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
                append("map");
                append(String.valueOf(entries.size()));
                for (Map.Entry<?, ?> entry : entries) {
                    encode(String.valueOf(entry.getKey()));
                    encode(entry.getValue());
                }
            } else if (item instanceof Collection<?> collection) {
                append("collection");
                append(String.valueOf(collection.size()));
                for (Object child : collection) {
                    encode(child);
                }
            } else if (item.getClass().isArray()) {
                int length = Array.getLength(item);
                append("array");
                append(String.valueOf(length));
                for (int index = 0; index < length; index++) {
                    encode(Array.get(item, index));
                }
            } else {
                cacheable = false;
            }
        }

        private void append(String raw) {
            String safe = raw == null ? "" : raw;
            byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
            value.append(bytes.length).append(':').append(safe);
        }

        private boolean cacheable() {
            return cacheable;
        }

        private String value() {
            return value.toString();
        }
    }

    private enum UnsupportedCanonicalValue {
        INSTANCE
    }
}
