package com.foggyframework.dataset.db.model.engine.compose.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered, duplicate-free list of {@link ColumnSpec}.
 *
 * <p>Duplicate output names are rejected at construction — the spec
 * requires {@link com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan}
 * to resolve column-name conflicts via explicit alias, so any duplicate
 * surviving into an {@code OutputSchema} is a derivation bug the caller
 * must fix (usually by writing {@code ... AS <x>}).</p>
 *
 * <p>Iteration order follows construction order. Positional lookup is
 * {@code O(1)} via {@link #columns()}; name lookup is {@code O(n)} via
 * {@link #get(String)} or {@code O(1)} via {@link #indexOf(String)}
 * (cached map).</p>
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

    private final List<ColumnSpec> columns;            // unmodifiable
    private final Map<String, Integer> indexByName;    // cached for O(1) lookup

    private OutputSchema(List<ColumnSpec> columns) {
        // Validate each entry + detect duplicates.
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnSpec c = columns.get(i);
            if (c == null) {
                throw new IllegalArgumentException(
                        "OutputSchema.columns[" + i + "] must be a ColumnSpec, got null");
            }
            Integer prev = index.putIfAbsent(c.name(), i);
            if (prev != null) {
                throw new IllegalArgumentException(
                        "OutputSchema contains duplicate output column '"
                                + c.name() + "' (first at index " + prev
                                + ", again at index " + i + ")");
            }
        }
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.indexByName = Collections.unmodifiableMap(index);
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
     *  plan validation. */
    public List<String> names() {
        List<String> out = new ArrayList<>(columns.size());
        for (ColumnSpec c : columns) {
            out.add(c.name());
        }
        return Collections.unmodifiableList(out);
    }

    /** Immutable set of output names. No iteration-order guarantee — mirrors
     *  Python {@code frozenset} semantics. */
    public Set<String> nameSet() {
        // indexByName is already wrapped via Collections.unmodifiableMap, so
        // its keySet() is an unmodifiable O(1) view. No allocation per call.
        return indexByName.keySet();
    }

    /** Return the {@link ColumnSpec} with the given output name, or
     *  {@code null} when absent. */
    public ColumnSpec get(String name) {
        Integer i = indexByName.get(name);
        return i == null ? null : columns.get(i);
    }

    /** Positional index of {@code name}; throws {@link java.util.NoSuchElementException}
     *  when absent. */
    public int indexOf(String name) {
        Integer i = indexByName.get(name);
        if (i == null) {
            throw new java.util.NoSuchElementException(
                    "OutputSchema has no column named '" + name + "'");
        }
        return i;
    }

    public boolean contains(String name) {
        return indexByName.containsKey(name);
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
