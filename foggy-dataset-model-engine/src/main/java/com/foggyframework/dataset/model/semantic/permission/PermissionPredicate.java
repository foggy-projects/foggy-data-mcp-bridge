package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Typed, provenance-aware row permission obligation.
 */
public final class PermissionPredicate {

    public enum Origin {
        TM_BASE_PERMISSION,
        QM_MODEL_PERMISSION,
        LEGACY_ACCESS
    }

    public enum ProofStatus {
        PROVABLE,
        UNPROVABLE
    }

    private final Origin origin;
    private final String binding;
    private final String field;
    private final String operator;
    private final String valueType;
    private final Object value;
    private final Set<String> referencedFields;
    private final ProofStatus proofStatus;

    public PermissionPredicate(
            Origin origin,
            String binding,
            String field,
            String operator,
            String valueType,
            Object value,
            Set<String> referencedFields,
            ProofStatus proofStatus
    ) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.binding = normalize(binding);
        this.field = requireText(field, "field");
        this.operator = normalizeOperator(operator);
        this.valueType = requireText(valueType, "valueType");
        this.value = freezeValue(value);
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (referencedFields != null) {
            referencedFields.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .forEach(fields::add);
        }
        fields.add(this.field);
        this.referencedFields = Set.copyOf(fields);
        this.proofStatus = Objects.requireNonNull(proofStatus, "proofStatus");
        validateValue();
    }

    public static PermissionPredicate provable(
            Origin origin,
            String binding,
            String field,
            String operator,
            Object value
    ) {
        return new PermissionPredicate(origin, binding, field, operator,
                inferValueType(value), value, Set.of(field), ProofStatus.PROVABLE);
    }

    public static PermissionPredicate unprovable(String field, String description) {
        return new PermissionPredicate(Origin.LEGACY_ACCESS, null, field, "custom",
                "CUSTOM", description, Set.of(field), ProofStatus.UNPROVABLE);
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getBinding() {
        return binding;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public String getValueType() {
        return valueType;
    }

    public Object getValue() {
        return value;
    }

    public Set<String> getReferencedFields() {
        return referencedFields;
    }

    public ProofStatus getProofStatus() {
        return proofStatus;
    }

    public boolean isProvable() {
        return proofStatus == ProofStatus.PROVABLE;
    }

    /**
     * Converts a provable obligation into the existing trusted system-slice
     * representation. Empty IN is represented as an explicit contradiction.
     */
    public SliceRequestDef toSlice() {
        if (!isProvable()) {
            throw new IllegalStateException("Unprovable permission predicate cannot be converted to a slice");
        }
        if ("in".equals(operator) && value instanceof Collection<?> collection && collection.isEmpty()) {
            CondRequestDef isNull = new CondRequestDef(field, "is null", null);
            CondRequestDef isNotNull = new CondRequestDef(field, "is not null", null);
            return SliceRequestDef.and(List.of(isNull, isNotNull));
        }
        if (value == null && "=".equals(operator)) {
            return new SliceRequestDef(field, "is null", null);
        }
        if (value == null && ("!=".equals(operator) || "<>".equals(operator))) {
            return new SliceRequestDef(field, "is not null", null);
        }
        return new SliceRequestDef(field, operator, value);
    }

    private void validateValue() {
        if (("in".equals(operator) || "not in".equals(operator))
                && !(value instanceof Collection<?>)) {
            throw new IllegalArgumentException(operator + " permission predicate requires a collection value");
        }
        if (("between".equals(operator) || "[)".equals(operator) || "[]".equals(operator))
                && (!(value instanceof Collection<?> collection) || collection.size() != 2)) {
            throw new IllegalArgumentException(operator + " permission predicate requires exactly two values");
        }
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return Collections.unmodifiableList(new ArrayList<>(collection));
        }
        return value;
    }

    private static String inferValueType(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Collection<?> collection) {
            Object first = collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
            return "LIST<" + (first == null ? "UNKNOWN" : first.getClass().getSimpleName()) + ">";
        }
        return value.getClass().getSimpleName();
    }

    private static String normalizeOperator(String operator) {
        String normalized = requireText(operator, "operator").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "eq" -> "=";
            case "ne", "neq" -> "!=";
            case "gt" -> ">";
            case "gte", "ge" -> ">=";
            case "lt" -> "<";
            case "lte", "le" -> "<=";
            case "notin", "not_in" -> "not in";
            default -> normalized;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
