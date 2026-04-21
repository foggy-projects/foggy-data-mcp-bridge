package com.foggyframework.dataset.db.model.engine.compose.security;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One (model, tables) pair the resolver needs to bind authority for.
 *
 * <p>Always carried inside {@link AuthorityRequest#models()}. Even for a
 * single-model resolution the request ships a size-1 list, so this class
 * is always a batch-element not a standalone payload.</p>
 *
 * @since 8.2.0.beta
 */
public final class ModelQuery {

    private final String model;
    private final List<String> tables;

    private ModelQuery(Builder b) {
        if (b.model == null || b.model.isEmpty()) {
            throw new IllegalArgumentException(
                    "ModelQuery.model must be non-blank");
        }
        if (b.tables == null) {
            throw new IllegalArgumentException(
                    "ModelQuery.tables must not be null; use empty list for no tables");
        }
        this.model = b.model;
        this.tables = Collections.unmodifiableList(List.copyOf(b.tables));
    }

    /** QM model name (e.g. {@code "SaleOrderQM"}). */
    public String model() { return model; }

    /** Physical tables derived from the model's {@code JoinGraph};
     *  unmodifiable, never null, may be empty. */
    public List<String> tables() { return tables; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String model;
        private List<String> tables;

        public Builder model(String v) { this.model = v; return this; }
        public Builder tables(List<String> v) { this.tables = v; return this; }

        public ModelQuery build() { return new ModelQuery(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelQuery)) return false;
        ModelQuery m = (ModelQuery) o;
        return Objects.equals(model, m.model) && Objects.equals(tables, m.tables);
    }

    @Override
    public int hashCode() { return Objects.hash(model, tables); }

    @Override
    public String toString() {
        return "ModelQuery{model=" + model + ", tables=" + tables + '}';
    }
}
