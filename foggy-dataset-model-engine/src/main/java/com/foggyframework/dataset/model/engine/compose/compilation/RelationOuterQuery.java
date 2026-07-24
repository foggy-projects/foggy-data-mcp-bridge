package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * S7d · Immutable result of building an outer query over a
 * {@link com.foggyframework.dataset.model.engine.compose.relation.CompiledRelation}.
 *
 * <p>Contains the final SQL, flattened params (inner relation +
 * filter), the output schema (subset of the relation's schema
 * limited to selected columns), and metadata carried from the
 * inner relation.</p>
 *
 * @since 8.5.0.beta (S7d)
 */
public final class RelationOuterQuery {

    private final String sql;
    private final List<Object> params;
    private final OutputSchema outputSchema;
    private final String datasourceId;       // nullable
    private final String dialect;

    private RelationOuterQuery(Builder b) {
        if (b.sql == null || b.sql.isEmpty()) {
            throw new IllegalArgumentException(
                    "RelationOuterQuery.sql must be non-empty");
        }
        if (b.outputSchema == null) {
            throw new IllegalArgumentException(
                    "RelationOuterQuery.outputSchema must not be null");
        }
        if (b.dialect == null || b.dialect.isEmpty()) {
            throw new IllegalArgumentException(
                    "RelationOuterQuery.dialect must be non-empty");
        }
        this.sql = b.sql;
        this.params = b.params != null
                ? Collections.unmodifiableList(new ArrayList<>(b.params))
                : Collections.emptyList();
        this.outputSchema = b.outputSchema;
        this.datasourceId = b.datasourceId;
        this.dialect = b.dialect;
    }

    /** Final wrapped SQL (outer SELECT over the relation). */
    public String sql() { return sql; }

    /** Flattened params: inner relation params followed by filter params. */
    public List<Object> params() { return params; }

    /** Output schema — subset of the inner relation schema. */
    public OutputSchema outputSchema() { return outputSchema; }

    /** Datasource identity, carried from the inner relation. */
    public String datasourceId() { return datasourceId; }

    /** Dialect, carried from the inner relation. */
    public String dialect() { return dialect; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sql;
        private List<Object> params;
        private OutputSchema outputSchema;
        private String datasourceId;
        private String dialect;

        public Builder sql(String v) { this.sql = v; return this; }
        public Builder params(List<Object> v) { this.params = v; return this; }
        public Builder outputSchema(OutputSchema v) { this.outputSchema = v; return this; }
        public Builder datasourceId(String v) { this.datasourceId = v; return this; }
        public Builder dialect(String v) { this.dialect = v; return this; }

        public RelationOuterQuery build() { return new RelationOuterQuery(this); }
    }

    @Override
    public String toString() {
        return "RelationOuterQuery{dialect=" + dialect
                + ", datasourceId=" + datasourceId
                + ", params=" + params.size()
                + ", schema=" + outputSchema.size() + " cols}";
    }
}
