package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.List;
import java.util.Objects;

/**
 * Debug-only SQL preview returned by {@link QueryPlan#toSql()}.
 *
 * <p><b>Not</b> a stable cross-system protocol. Format may change freely
 * across minor versions; callers should use it only for logging, EXPLAIN
 * tooling, or interactive development.</p>
 *
 * <p>M2 does not actually produce any SQL — {@link QueryPlan#toSql()}
 * always raises {@link UnsupportedInM2Exception}. This carrier type exists
 * so the return shape is stable the day the M6 SQL compiler wires it.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.SqlPreview}.</p>
 *
 * @since 8.2.0.beta
 */
public final class SqlPreview {

    private final String sql;
    private final List<Object> params;

    private SqlPreview(Builder b) {
        this.sql = b.sql == null ? "" : b.sql;
        this.params = b.params == null ? List.of() : List.copyOf(b.params);
    }

    /** Rendered SQL text. Parameter placeholders are left in-place — no
     *  string interpolation of values. */
    public String sql() { return sql; }

    /** Parameter values in the order they appear in {@link #sql()}. */
    public List<Object> params() { return params; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sql;
        private List<Object> params;

        public Builder sql(String v) { this.sql = v; return this; }
        public Builder params(List<Object> v) { this.params = v; return this; }

        public SqlPreview build() { return new SqlPreview(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqlPreview)) return false;
        SqlPreview p = (SqlPreview) o;
        return Objects.equals(sql, p.sql) && Objects.equals(params, p.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sql, params);
    }

    @Override
    public String toString() {
        return "SqlPreview{sql=" + sql + ", paramsSize=" + params.size() + '}';
    }
}
