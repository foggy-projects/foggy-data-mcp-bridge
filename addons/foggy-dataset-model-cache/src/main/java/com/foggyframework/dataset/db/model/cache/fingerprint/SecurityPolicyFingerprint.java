package com.foggyframework.dataset.db.model.cache.fingerprint;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Hash-only representation of every security input that may affect a query.
 * Raw tokens, attributes and policy values are never exposed in cache keys or
 * debug output.
 */
public record SecurityPolicyFingerprint(
        String fieldAccessHash,
        String deniedColumnsHash,
        String systemSliceHash,
        String securityContextHash,
        String combinedHash) {

    public static Optional<SecurityPolicyFingerprint> from(ModelResultContext context) {
        if (context == null || !hasExplicitIdentity(context.getSecurityContext())) {
            return Optional.empty();
        }

        Optional<String> fieldAccess = StableCanonicalEncoder.encode(context.getFieldAccess());
        Optional<String> deniedColumns = encodeDeniedColumns(context.getDeniedColumns());
        Optional<String> systemSlice = encodeSystemSlice(context.getSystemSlice());
        Optional<String> securityContext = encodeSecurityContext(context.getSecurityContext());
        if (fieldAccess.isEmpty() || deniedColumns.isEmpty()
                || systemSlice.isEmpty() || securityContext.isEmpty()) {
            return Optional.empty();
        }

        String fieldAccessHash = StableCanonicalEncoder.sha256(fieldAccess.get());
        String deniedColumnsHash = StableCanonicalEncoder.sha256(deniedColumns.get());
        String systemSliceHash = StableCanonicalEncoder.sha256(systemSlice.get());
        String securityContextHash = StableCanonicalEncoder.sha256(securityContext.get());
        String combined = StableCanonicalEncoder.segment("fieldAccess", fieldAccessHash)
                + StableCanonicalEncoder.segment("deniedColumns", deniedColumnsHash)
                + StableCanonicalEncoder.segment("systemSlice", systemSliceHash)
                + StableCanonicalEncoder.segment("securityContext", securityContextHash);

        return Optional.of(new SecurityPolicyFingerprint(
                fieldAccessHash,
                deniedColumnsHash,
                systemSliceHash,
                securityContextHash,
                StableCanonicalEncoder.sha256(combined)));
    }

    /**
     * Until the security SPI exposes a dedicated anonymous/PUBLIC marker, an
     * allocated but empty context is still an unknown identity and must not be
     * treated as shared public scope.
     */
    private static boolean hasExplicitIdentity(ModelResultContext.SecurityContext securityContext) {
        if (securityContext == null) {
            return false;
        }
        if (hasText(securityContext.getAuthorization())
                || hasText(securityContext.getUserId())
                || hasText(securityContext.getTenantId())
                || hasText(securityContext.getDeptId())) {
            return true;
        }
        if (securityContext.getRoles() != null
                && securityContext.getRoles().stream().anyMatch(SecurityPolicyFingerprint::hasText)) {
            return true;
        }
        return securityContext.getAttributes() != null
                && !securityContext.getAttributes().isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Optional<String> encodeDeniedColumns(List<DeniedPhysicalColumn> deniedColumns) {
        if (deniedColumns == null) {
            return StableCanonicalEncoder.encode(null);
        }
        List<String> entries = new ArrayList<>(deniedColumns.size());
        for (DeniedPhysicalColumn denied : deniedColumns) {
            if (denied == null) {
                entries.add(StableCanonicalEncoder.segment("denied", "null"));
                continue;
            }
            Optional<String> schema = StableCanonicalEncoder.encode(denied.getSchema());
            Optional<String> table = StableCanonicalEncoder.encode(denied.getTable());
            Optional<String> column = StableCanonicalEncoder.encode(denied.getColumn());
            if (schema.isEmpty() || table.isEmpty() || column.isEmpty()) {
                return Optional.empty();
            }
            entries.add(StableCanonicalEncoder.segment("schema", schema.get())
                    + StableCanonicalEncoder.segment("table", table.get())
                    + StableCanonicalEncoder.segment("column", column.get()));
        }
        entries.sort(Comparator.naturalOrder());
        return StableCanonicalEncoder.encode(entries);
    }

    private static Optional<String> encodeSystemSlice(List<SliceRequestDef> systemSlice) {
        if (systemSlice == null) {
            return StableCanonicalEncoder.encode(null);
        }
        List<String> entries = new ArrayList<>(systemSlice.size());
        for (SliceRequestDef slice : systemSlice) {
            Optional<String> encoded = encodeCondition(slice);
            if (encoded.isEmpty()) {
                return Optional.empty();
            }
            entries.add(encoded.get());
        }
        // Top-level system slices are combined with AND, so order is not semantic.
        entries.sort(Comparator.naturalOrder());
        return StableCanonicalEncoder.encode(entries);
    }

    private static Optional<String> encodeCondition(CondRequestDef condition) {
        if (condition == null) {
            return StableCanonicalEncoder.encode(null);
        }
        if (condition._isLogicalGroup()) {
            List<String> children = new ArrayList<>();
            for (CondRequestDef child : condition._getGroupChildren()) {
                Optional<String> encoded = encodeCondition(child);
                if (encoded.isEmpty()) {
                    return Optional.empty();
                }
                children.add(encoded.get());
            }
            // AND/OR operands are commutative for the policy identity.
            children.sort(Comparator.naturalOrder());
            Optional<String> encodedChildren = StableCanonicalEncoder.encode(children);
            return encodedChildren.map(value -> StableCanonicalEncoder.segment(
                    "group-" + condition._getGroupLink(), value));
        }

        Optional<String> field = StableCanonicalEncoder.encode(condition.getField());
        Optional<String> op = StableCanonicalEncoder.encode(condition.getOp());
        Optional<String> value = StableCanonicalEncoder.encode(condition.getValue());
        Optional<String> maxDepth = StableCanonicalEncoder.encode(condition.getMaxDepth());
        Optional<String> expression = StableCanonicalEncoder.encode(condition.getExpr());
        if (field.isEmpty() || op.isEmpty() || value.isEmpty()
                || maxDepth.isEmpty() || expression.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(StableCanonicalEncoder.segment("field", field.get())
                + StableCanonicalEncoder.segment("op", op.get())
                + StableCanonicalEncoder.segment("value", value.get())
                + StableCanonicalEncoder.segment("maxDepth", maxDepth.get())
                + StableCanonicalEncoder.segment("expression", expression.get()));
    }

    private static Optional<String> encodeSecurityContext(ModelResultContext.SecurityContext securityContext) {
        if (securityContext == null) {
            return StableCanonicalEncoder.encode(null);
        }

        // Hash the bearer value before it joins any canonical material. The raw
        // token therefore cannot appear in a Redis key, local key or debug log.
        String authorizationHash = null;
        if (securityContext.getAuthorization() != null) {
            Optional<String> authorization = StableCanonicalEncoder.encode(securityContext.getAuthorization());
            if (authorization.isEmpty()) {
                return Optional.empty();
            }
            authorizationHash = StableCanonicalEncoder.sha256(authorization.get());
        }

        List<String> roles = securityContext.getRoles() == null
                ? null
                : new ArrayList<>(securityContext.getRoles());
        if (roles != null) {
            roles.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        }

        Optional<String> auth = StableCanonicalEncoder.encode(authorizationHash);
        Optional<String> user = StableCanonicalEncoder.encode(securityContext.getUserId());
        Optional<String> encodedRoles = StableCanonicalEncoder.encode(roles);
        Optional<String> tenant = StableCanonicalEncoder.encode(securityContext.getTenantId());
        Optional<String> department = StableCanonicalEncoder.encode(securityContext.getDeptId());
        Optional<String> attributes = StableCanonicalEncoder.encode(securityContext.getAttributes());
        if (auth.isEmpty() || user.isEmpty() || encodedRoles.isEmpty()
                || tenant.isEmpty() || department.isEmpty() || attributes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(StableCanonicalEncoder.segment("authorizationHash", auth.get())
                + StableCanonicalEncoder.segment("user", user.get())
                + StableCanonicalEncoder.segment("roles", encodedRoles.get())
                + StableCanonicalEncoder.segment("tenant", tenant.get())
                + StableCanonicalEncoder.segment("department", department.get())
                + StableCanonicalEncoder.segment("attributes", attributes.get()));
    }
}
