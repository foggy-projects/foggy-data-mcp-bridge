package com.foggyframework.dataset.db.model.semantic.util;

import java.util.*;

/**
 * Case-insensitive canonical field name resolver.
 *
 * <p>Resolves user-supplied field names that differ only by case from
 * the canonical (schema-defined) field name. Example:
 * {@code aroverdueamount} resolves to canonical {@code arOverdueAmount}.
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Only case-only variants are resolved.  {@code ar_overdue_amount}
 *       is NOT treated as equivalent to {@code arOverdueAmount} (that
 *       would be camelCase/snake_case conversion).</li>
 *   <li>If the schema contains two fields that differ only by case (e.g.
 *       {@code amount} and {@code Amount}), referencing {@code AMOUNT}
 *       throws {@link CaseInsensitiveFieldAmbiguousException} — fail-closed.</li>
 *   <li>If no match exists (exact or case-insensitive), the input is returned
 *       unchanged so downstream unknown-field handling surfaces its own error.</li>
 * </ul>
 *
 * <h3>Feature flag</h3>
 * <p>Enabled by default. Disable via system property
 * {@code foggy.dataset.case-insensitive-field-resolve=false} or environment
 * variable {@code FOGGY_DATASET_CASE_INSENSITIVE_FIELD_RESOLVE=false}.</p>
 *
 * @since 8.3.0
 */
public class CaseInsensitiveFieldResolver {

    private static final String SYS_PROP = "foggy.dataset.case-insensitive-field-resolve";
    private static final String ENV_VAR = "FOGGY_DATASET_CASE_INSENSITIVE_FIELD_RESOLVE";

    private final Set<String> canonical;
    private final Map<String, List<String>> lowerIndex;

    /**
     * Construct a resolver from the canonical (schema-defined) field names.
     *
     * @param canonicalFields all valid field names from the model schema
     */
    public CaseInsensitiveFieldResolver(Set<String> canonicalFields) {
        this.canonical = Set.copyOf(canonicalFields);
        this.lowerIndex = new HashMap<>();
        for (String name : canonicalFields) {
            lowerIndex.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(name);
        }
    }

    /**
     * Resolve a field name to its canonical casing.
     *
     * <ol>
     *   <li>Exact match → return as-is (short-circuit).</li>
     *   <li>Unique case-insensitive match → return canonical name.</li>
     *   <li>Multiple case-insensitive matches → throw ambiguity error.</li>
     *   <li>No match → return input unchanged.</li>
     * </ol>
     *
     * @param fieldName the user-supplied field name
     * @return the resolved canonical field name
     * @throws CaseInsensitiveFieldAmbiguousException when multiple candidates exist
     */
    public String resolve(String fieldName) {
        if (canonical.contains(fieldName)) {
            return fieldName;
        }
        List<String> candidates = lowerIndex.get(fieldName.toLowerCase(Locale.ROOT));
        if (candidates == null || candidates.isEmpty()) {
            return fieldName; // no match
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        throw new CaseInsensitiveFieldAmbiguousException(fieldName, candidates);
    }

    /**
     * Like {@link #resolve(String)} but returns {@code null} when no match exists
     * (instead of returning the input unchanged).
     */
    public String resolveOrNull(String fieldName) {
        if (canonical.contains(fieldName)) {
            return fieldName;
        }
        List<String> candidates = lowerIndex.get(fieldName.toLowerCase(Locale.ROOT));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        throw new CaseInsensitiveFieldAmbiguousException(fieldName, candidates);
    }

    // ---- Feature flag ----

    /**
     * Return whether case-insensitive field resolution is enabled.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>System property {@code foggy.dataset.case-insensitive-field-resolve}</li>
     *   <li>Environment variable {@code FOGGY_DATASET_CASE_INSENSITIVE_FIELD_RESOLVE}</li>
     *   <li>Default {@code true}</li>
     * </ol>
     */
    public static boolean isEnabled() {
        // System property
        String sysProp = System.getProperty(SYS_PROP);
        if (sysProp != null && !sysProp.isBlank()) {
            return !isFalsy(sysProp);
        }
        // Environment variable
        String env = System.getenv(ENV_VAR);
        if (env != null && !env.isBlank()) {
            return !isFalsy(env);
        }
        // Default
        return true;
    }

    private static boolean isFalsy(String value) {
        String s = value.strip().toLowerCase(Locale.ROOT);
        return "false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s);
    }

    // ---- Ambiguity exception ----

    /**
     * Thrown when a field reference matches multiple canonical names
     * that differ only by case.
     */
    public static class CaseInsensitiveFieldAmbiguousException extends RuntimeException {

        public static final String ERROR_CODE = "CASE_INSENSITIVE_FIELD_AMBIGUOUS";

        private final String field;
        private final List<String> candidates;

        public CaseInsensitiveFieldAmbiguousException(String field, List<String> candidates) {
            super("Field '" + field + "' is ambiguous — matches multiple canonical "
                    + "fields that differ only by case: " + candidates);
            this.field = field;
            this.candidates = List.copyOf(candidates);
        }

        public String getField() {
            return field;
        }

        public List<String> getCandidates() {
            return candidates;
        }

        public String getErrorCode() {
            return ERROR_CODE;
        }
    }
}
