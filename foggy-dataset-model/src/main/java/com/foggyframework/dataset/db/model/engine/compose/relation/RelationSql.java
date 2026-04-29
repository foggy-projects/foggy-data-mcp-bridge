package com.foggyframework.dataset.db.model.engine.compose.relation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured SQL representation for a {@link CompiledRelation}.
 *
 * <p>Instead of a raw SQL string, the relation's SQL is decomposed into
 * structured parts so the outer compiler can safely hoist CTE items,
 * rewrite aliases, and validate params order without string parsing.</p>
 *
 * <p>Immutable. Use {@link #builder()} to construct.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class RelationSql {

    private final List<CteItem> withItems;
    private final String bodySql;
    private final List<Object> bodyParams;
    private final String preferredAlias;

    private RelationSql(Builder b) {
        if (b.bodySql == null || b.bodySql.isEmpty()) {
            throw new IllegalArgumentException("RelationSql.bodySql must be non-empty");
        }
        if (b.preferredAlias == null || b.preferredAlias.isEmpty()) {
            throw new IllegalArgumentException("RelationSql.preferredAlias must be non-empty");
        }
        this.withItems = b.withItems == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.withItems));
        this.bodySql = b.bodySql;
        this.bodyParams = b.bodyParams == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.bodyParams));
        this.preferredAlias = b.preferredAlias;
    }

    /** CTE items that precede the body SQL in the WITH block. */
    public List<CteItem> withItems() { return withItems; }

    /** The body SELECT statement. May reference withItems by name. */
    public String bodySql() { return bodySql; }

    /** Bind params for the body SQL only (excludes withItems params). */
    public List<Object> bodyParams() { return bodyParams; }

    /** Preferred alias when this relation is used as a CTE or subquery source. */
    public String preferredAlias() { return preferredAlias; }

    /** Whether this relation contains any CTE items. */
    public boolean containsWithItems() { return !withItems.isEmpty(); }

    /**
     * Flatten all params in render order: withItem[0].params + withItem[1].params + ... + bodyParams.
     *
     * <p>This is the canonical parameter order for SQL rendering. Any
     * snapshot or parity check must validate params in this order.</p>
     */
    public List<Object> flattenParams() {
        List<Object> result = new ArrayList<>();
        for (CteItem item : withItems) {
            result.addAll(item.params());
        }
        result.addAll(bodyParams);
        return Collections.unmodifiableList(result);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<CteItem> withItems;
        private String bodySql;
        private List<Object> bodyParams;
        private String preferredAlias;

        public Builder withItems(List<CteItem> v) { this.withItems = v; return this; }
        public Builder bodySql(String v) { this.bodySql = v; return this; }
        public Builder bodyParams(List<Object> v) { this.bodyParams = v; return this; }
        public Builder preferredAlias(String v) { this.preferredAlias = v; return this; }
        public RelationSql build() { return new RelationSql(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationSql r)) return false;
        return Objects.equals(withItems, r.withItems)
                && Objects.equals(bodySql, r.bodySql)
                && Objects.equals(bodyParams, r.bodyParams)
                && Objects.equals(preferredAlias, r.preferredAlias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(withItems, bodySql, bodyParams, preferredAlias);
    }

    @Override
    public String toString() {
        return "RelationSql{preferredAlias=" + preferredAlias
                + ", withItems=" + withItems.size()
                + ", bodyParams=" + bodyParams.size() + "}";
    }
}
