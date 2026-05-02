package com.foggyframework.dataset.db.model.engine.compose.relation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One CTE item inside a {@link RelationSql}.
 *
 * <p>Represents a {@code name AS (sql)} clause within a top-level
 * {@code WITH} block. The SQL must not itself contain a top-level
 * {@code WITH} unless marked {@link #recursive}.</p>
 *
 * <p>Immutable. Use {@link #builder()} to construct.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class CteItem {

    private final String name;
    private final String sql;
    private final List<Object> params;
    private final boolean recursive;

    private CteItem(Builder b) {
        if (b.name == null || b.name.isEmpty()) {
            throw new IllegalArgumentException("CteItem.name must be non-empty");
        }
        if (b.sql == null || b.sql.isEmpty()) {
            throw new IllegalArgumentException("CteItem.sql must be non-empty");
        }
        this.name = b.name;
        this.sql = b.sql;
        this.params = b.params == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(b.params);
        this.recursive = b.recursive;
    }

    public String name() { return name; }
    public String sql() { return sql; }
    public List<Object> params() { return params; }
    public boolean recursive() { return recursive; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String sql;
        private List<Object> params;
        private boolean recursive;

        public Builder name(String v) { this.name = v; return this; }
        public Builder sql(String v) { this.sql = v; return this; }
        public Builder params(List<Object> v) { this.params = v; return this; }
        public Builder recursive(boolean v) { this.recursive = v; return this; }
        public CteItem build() { return new CteItem(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CteItem c)) return false;
        return recursive == c.recursive
                && Objects.equals(name, c.name)
                && Objects.equals(sql, c.sql)
                && Objects.equals(params, c.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sql, params, recursive);
    }

    @Override
    public String toString() {
        return "CteItem{name=" + name + ", recursive=" + recursive
                + ", params=" + params.size() + "}";
    }
}
