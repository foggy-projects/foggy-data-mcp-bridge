package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered list of {@link ColumnSpec}.
 *
 * <h3>Duplicate-name handling</h3>
 *
 * <p>The duplicate-name policy depends on the
 * {@link ComposeFeatureFlags#g10Enabled() G10 feature flag}:</p>
 *
 * <ul>
 *   <li><b>Flag OFF (legacy)</b> — duplicate output names are rejected at
 *       construction. {@code JoinPlan} must resolve column-name conflicts
 *       via explicit alias; any duplicate surviving into an
 *       {@code OutputSchema} is a derivation bug.</li>
 *   <li><b>Flag ON (G10)</b> — duplicate names are <em>allowed</em> when
 *       <em>every</em> column carrying that name has
 *       {@link ColumnSpec#isAmbiguous()} = {@code true}. Such duplicates are
 *       produced by {@code SchemaDerivation.deriveJoin} when both join
 *       sides emit the same name. Each ambiguous occurrence must record a
 *       distinct {@link ColumnSpec#planProvenance()} — pure duplicates
 *       (same {@code planProvenance}) remain rejected as a structural bug.
 *       Non-ambiguous duplicates (any column lacking the
 *       {@code isAmbiguous} flag) are still rejected; they would indicate
 *       the user wrote two aliases that landed on the same name, which is
 *       a {@code DUPLICATE_OUTPUT_COLUMN} error from upstream
 *       derivation.</li>
 * </ul>
 *
 * <h3>Lookup API</h3>
 *
 * <p>Iteration order follows construction order. Positional lookup is
 * {@code O(1)} via {@link #columns()}.</p>
 *
 * <p>Name lookup surface (G10 PR2):
 * <ul>
 *   <li>{@link #get(String)} — <b>fail-fast on ambiguity.</b> Returns the
 *       single column for non-ambiguous names; throws
 *       {@code OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP} when the name resolves to
 *       multiple ambiguous columns. Returns {@code null} when absent.
 *       Suitable for callers that already verified non-ambiguity (most
 *       single-base / derived-single-source paths).</li>
 *   <li>{@link #requireUnique(String)} — same fail-fast semantics, but
 *       throws {@link java.util.NoSuchElementException} on absent. Use
 *       this when the caller logically expects a unique hit.</li>
 *   <li>{@link #getAll(String)} — returns every {@link ColumnSpec} with
 *       this name (single-element list for non-ambiguous, multi-element
 *       for ambiguous, empty for absent). Use when business logic must
 *       inspect every plan that contributed the name.</li>
 *   <li>{@link #isAmbiguous(String)} — boolean predicate; {@code true}
 *       iff the name resolves to two or more columns.</li>
 *   <li>{@link #indexOf(String)} — fail-fast on ambiguity, throws
 *       {@link java.util.NoSuchElementException} on absent.</li>
 * </ul>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.output_schema.OutputSchema}
 * (Python {@code @dataclass(frozen=True)}).</p>
 *
 * @since 8.2.0.beta
 */
public final class OutputSchema {

    /** Canonical empty schema — callable via {@link #empty()}. */
    private static final OutputSchema EMPTY = new OutputSchema(List.of());

    private final List<ColumnSpec> columns;                    // unmodifiable
    /** Cached name → list of indices into {@code columns}. Always
     *  iteration-order; single-element list for non-ambiguous names. */
    private final Map<String, List<Integer>> indicesByName;    // unmodifiable

    private OutputSchema(List<ColumnSpec> columns) {
        boolean g10 = ComposeFeatureFlags.g10Enabled();
        // LinkedHashMap preserves first-occurrence order for deterministic iteration.
        Map<String, List<Integer>> index = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            indexOne(columns, i, index, g10);
        }
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        Map<String, List<Integer>> frozen = new LinkedHashMap<>(index.size());
        for (Map.Entry<String, List<Integer>> e : index.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.indicesByName = Collections.unmodifiableMap(frozen);
    }

    /**
     * Validate column at {@code i} and append its index to the running
     * {@code index} bucket. Centralises the duplicate-name policy so
     * the constructor body stays a flat loop.
     */
    private static void indexOne(List<ColumnSpec> columns, int i,
                                 Map<String, List<Integer>> index, boolean g10) {
        ColumnSpec c = columns.get(i);
        if (c == null) {
            throw new IllegalArgumentException(
                    "OutputSchema.columns[" + i + "] must be a ColumnSpec, got null");
        }
        List<Integer> bucket = index.get(c.name());
        if (bucket == null) {
            List<Integer> first = new ArrayList<>(1);
            first.add(i);
            index.put(c.name(), first);
            return;
        }
        int firstIndex = bucket.get(0);
        ColumnSpec firstSpec = columns.get(firstIndex);
        DuplicateOutcome outcome = classifyDuplicate(firstSpec, c, g10);
        if (outcome != DuplicateOutcome.ACCEPT_AMBIGUOUS) {
            throw new IllegalArgumentException(
                    duplicateMessage(outcome, c.name(), firstSpec, c, firstIndex, i));
        }
        bucket.add(i);
    }

    /** Classification of a same-name duplicate against the active flag policy. */
    private enum DuplicateOutcome {
        REJECT_LEGACY,        // flag=false: any duplicate forbidden
        REJECT_MIXED_FLAG,    // flag=true:  not every occurrence has isAmbiguous=true
        REJECT_PURE_DUPLICATE,// flag=true:  same planProvenance — plan-tree construction bug
        ACCEPT_AMBIGUOUS      // flag=true:  legitimate join-overlap pair
    }

    private static DuplicateOutcome classifyDuplicate(
            ColumnSpec firstSpec, ColumnSpec c, boolean g10) {
        if (!g10) {
            return DuplicateOutcome.REJECT_LEGACY;
        }
        if (!firstSpec.isAmbiguous() || !c.isAmbiguous()) {
            return DuplicateOutcome.REJECT_MIXED_FLAG;
        }
        if (Objects.equals(firstSpec.planProvenance(), c.planProvenance())) {
            return DuplicateOutcome.REJECT_PURE_DUPLICATE;
        }
        return DuplicateOutcome.ACCEPT_AMBIGUOUS;
    }

    private static String duplicateMessage(DuplicateOutcome outcome, String name,
                                           ColumnSpec firstSpec, ColumnSpec c,
                                           int firstIndex, int i) {
        String prefix = "OutputSchema contains duplicate output column '" + name
                + "' (first at index " + firstIndex + ", again at index " + i + ")";
        switch (outcome) {
            case REJECT_LEGACY:
                return prefix;
            case REJECT_MIXED_FLAG:
                return prefix + ". G10 allows duplicates only when every occurrence "
                        + "has isAmbiguous=true; [firstAmbiguous="
                        + firstSpec.isAmbiguous()
                        + ", currentAmbiguous=" + c.isAmbiguous() + "]";
            case REJECT_PURE_DUPLICATE:
                return "OutputSchema rejects pure duplicate ambiguous column '"
                        + name + "' — both occurrences carry the same planProvenance, "
                        + "which indicates a plan-tree construction bug rather than "
                        + "a join overlap (first at index " + firstIndex
                        + ", again at index " + i + ")";
            default:
                throw new IllegalStateException("unreachable: " + outcome);
        }
    }

    // ------------------------------------------------------------------
    // Construction helpers
    // ------------------------------------------------------------------

    public static OutputSchema empty() { return EMPTY; }

    /**
     * Construct from a list of {@link ColumnSpec}. The list is defensively
     * copied; subsequent mutation of the caller's list does not affect
     * the returned schema.
     */
    public static OutputSchema of(List<ColumnSpec> columns) {
        if (columns == null) {
            throw new IllegalArgumentException(
                    "OutputSchema.of: columns must not be null; use OutputSchema.empty() for the empty schema");
        }
        if (columns.isEmpty()) {
            return EMPTY;
        }
        return new OutputSchema(columns);
    }

    // ------------------------------------------------------------------
    // Read accessors
    // ------------------------------------------------------------------

    /** Ordered, unmodifiable view of the columns. */
    public List<ColumnSpec> columns() { return columns; }

    public int size() { return columns.size(); }
    public boolean isEmpty() { return columns.isEmpty(); }

    /** Ordered list of output names — primary lookup surface for downstream
     *  plan validation. Ambiguous names appear once per occurrence. */
    public List<String> names() {
        List<String> out = new ArrayList<>(columns.size());
        for (ColumnSpec c : columns) {
            out.add(c.name());
        }
        return Collections.unmodifiableList(out);
    }

    /** Immutable set of distinct output names. No iteration-order guarantee —
     *  mirrors Python {@code frozenset} semantics. Ambiguous names appear
     *  exactly once. */
    public Set<String> nameSet() {
        return indicesByName.keySet();
    }

    /**
     * Single-column lookup by name.
     *
     * <p>Returns {@code null} when absent. <b>Throws</b>
     * {@link ComposeSchemaException} with code
     * {@code OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP} when the name resolves to
     * multiple ambiguous columns — callers that may encounter ambiguity
     * should use {@link #getAll(String)} or {@link #requireUnique(String)}
     * for explicit semantics.</p>
     */
    public ColumnSpec get(String name) {
        Integer i = uniqueIndexOrNull(name);
        return i == null ? null : columns.get(i);
    }

    /**
     * <b>G10 PR2</b> · Return every {@link ColumnSpec} carrying
     * {@code name}. Returns an empty list when absent, a single-element
     * list for non-ambiguous names, or a multi-element list for ambiguous
     * (join-overlap) names. The returned list is unmodifiable and
     * preserves construction order.
     */
    public List<ColumnSpec> getAll(String name) {
        List<Integer> bucket = indicesByName.get(name);
        if (bucket == null) {
            return Collections.emptyList();
        }
        List<ColumnSpec> out = new ArrayList<>(bucket.size());
        for (Integer i : bucket) {
            out.add(columns.get(i));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * <b>G10 PR2</b> · Return {@code true} iff {@code name} resolves to
     * two or more columns (only possible when the G10 flag is on and an
     * upstream join produced an overlap).
     */
    public boolean isAmbiguous(String name) {
        List<Integer> bucket = indicesByName.get(name);
        return bucket != null && bucket.size() > 1;
    }

    /**
     * <b>G10 PR2</b> · Same as {@link #get(String)} but throws
     * {@link java.util.NoSuchElementException} when absent. Use when the
     * caller logically expects a unique hit; the explicit name documents
     * intent.
     */
    public ColumnSpec requireUnique(String name) {
        return columns.get(requireUniqueIndex(name));
    }

    /**
     * Positional index of {@code name}. <b>Fails fast on ambiguity</b>
     * with {@code OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP}; throws
     * {@link java.util.NoSuchElementException} when absent.
     */
    public int indexOf(String name) {
        return requireUniqueIndex(name);
    }

    public boolean contains(String name) {
        return indicesByName.containsKey(name);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Bucket → unique index, or null when absent.
     *  Throws {@code OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP} on multi-element buckets. */
    private Integer uniqueIndexOrNull(String name) {
        List<Integer> bucket = indicesByName.get(name);
        if (bucket == null) {
            return null;
        }
        if (bucket.size() == 1) {
            return bucket.get(0);
        }
        throw ambiguousLookup(name, bucket);
    }

    /** Same as {@link #uniqueIndexOrNull(String)} but
     *  {@link java.util.NoSuchElementException} on absent. */
    private int requireUniqueIndex(String name) {
        Integer i = uniqueIndexOrNull(name);
        if (i == null) {
            throw new java.util.NoSuchElementException(
                    "OutputSchema has no column named '" + name + "'");
        }
        return i;
    }

    private ComposeSchemaException ambiguousLookup(String name, List<Integer> bucket) {
        // Build the candidate list with plan-provenance so the caller can
        // disambiguate via F5 ({plan: <handle>, field: <name>}).
        StringBuilder sb = new StringBuilder();
        sb.append("OutputSchema lookup of '").append(name)
                .append("' is ambiguous — ").append(bucket.size())
                .append(" candidate columns. Use a plan-qualified reference "
                        + "({plan: <handle>, field: '").append(name)
                .append("'}) or call OutputSchema.getAll(name) explicitly. Candidates: [");
        for (int j = 0; j < bucket.size(); j++) {
            if (j > 0) sb.append(", ");
            ColumnSpec c = columns.get(bucket.get(j));
            sb.append("{index=").append(bucket.get(j))
                    .append(", planProvenance=").append(c.planProvenance())
                    .append("}");
        }
        sb.append("]");
        return new ComposeSchemaException(
                ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
                sb.toString(),
                ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                /* path = */ null,
                /* offendingField = */ name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputSchema)) return false;
        OutputSchema s = (OutputSchema) o;
        return Objects.equals(columns, s.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns);
    }

    @Override
    public String toString() {
        return "OutputSchema" + names();
    }
}
