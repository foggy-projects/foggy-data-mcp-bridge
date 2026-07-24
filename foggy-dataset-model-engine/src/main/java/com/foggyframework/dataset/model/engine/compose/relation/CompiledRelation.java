package com.foggyframework.dataset.model.engine.compose.relation;

import com.foggyframework.dataset.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compiled, stable relation — the formal S7a contract output.
 *
 * <p>Represents a QueryPlan compiled into a reusable SQL relation with
 * full metadata: SQL structure, params, output schema, dialect, capabilities,
 * datasource identity, and permission state.</p>
 *
 * <p>{@code CteUnit} remains an internal SQL assembly primitive. This class
 * is the formal stable relation contract, facing outer query / LLM / validator /
 * parity fixture consumers.</p>
 *
 * <p>Immutable. Use {@link #builder()} to construct.</p>
 *
 * @since 8.5.0.beta (S7a)
 */
public final class CompiledRelation {

    private final String alias;
    private final RelationSql relationSql;
    private final List<Object> params;
    private final OutputSchema outputSchema;
    private final String datasourceId;       // nullable
    private final String dialect;
    private final RelationCapabilities capabilities;
    private final PlanId sourcePlanId;       // nullable
    private final String permissionState;

    private CompiledRelation(Builder b) {
        if (b.alias == null || b.alias.isEmpty()) {
            throw new IllegalArgumentException("CompiledRelation.alias must be non-empty");
        }
        if (b.relationSql == null) {
            throw new IllegalArgumentException("CompiledRelation.relationSql must not be null");
        }
        if (b.outputSchema == null) {
            throw new IllegalArgumentException("CompiledRelation.outputSchema must not be null");
        }
        if (b.dialect == null || b.dialect.isEmpty()) {
            throw new IllegalArgumentException("CompiledRelation.dialect must be non-empty");
        }
        if (b.capabilities == null) {
            throw new IllegalArgumentException("CompiledRelation.capabilities must not be null");
        }
        this.alias = b.alias;
        this.relationSql = b.relationSql;
        this.params = b.params != null
                ? Collections.unmodifiableList(new ArrayList<>(b.params))
                : b.relationSql.flattenParams();
        this.outputSchema = b.outputSchema;
        this.datasourceId = b.datasourceId;
        this.dialect = b.dialect;
        this.capabilities = b.capabilities;
        this.sourcePlanId = b.sourcePlanId;
        this.permissionState = b.permissionState != null
                ? b.permissionState
                : RelationPermissionState.UNKNOWN;
    }

    public String alias() { return alias; }
    public RelationSql relationSql() { return relationSql; }
    public List<Object> params() { return params; }
    public OutputSchema outputSchema() { return outputSchema; }
    public String datasourceId() { return datasourceId; }
    public String dialect() { return dialect; }
    public RelationCapabilities capabilities() { return capabilities; }
    public PlanId sourcePlanId() { return sourcePlanId; }
    public String permissionState() { return permissionState; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String alias;
        private RelationSql relationSql;
        private List<Object> params;
        private OutputSchema outputSchema;
        private String datasourceId;
        private String dialect;
        private RelationCapabilities capabilities;
        private PlanId sourcePlanId;
        private String permissionState;

        public Builder alias(String v) { this.alias = v; return this; }
        public Builder relationSql(RelationSql v) { this.relationSql = v; return this; }
        public Builder params(List<Object> v) { this.params = v; return this; }
        public Builder outputSchema(OutputSchema v) { this.outputSchema = v; return this; }
        public Builder datasourceId(String v) { this.datasourceId = v; return this; }
        public Builder dialect(String v) { this.dialect = v; return this; }
        public Builder capabilities(RelationCapabilities v) { this.capabilities = v; return this; }
        public Builder sourcePlanId(PlanId v) { this.sourcePlanId = v; return this; }
        public Builder permissionState(String v) { this.permissionState = v; return this; }
        public CompiledRelation build() { return new CompiledRelation(this); }
    }

    @Override
    public String toString() {
        return "CompiledRelation{alias=" + alias
                + ", dialect=" + dialect
                + ", capabilities=" + capabilities
                + ", permissionState=" + permissionState
                + ", outputSchema=" + outputSchema + "}";
    }
}
