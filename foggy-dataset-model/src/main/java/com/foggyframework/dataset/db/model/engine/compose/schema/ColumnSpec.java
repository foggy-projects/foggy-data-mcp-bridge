package com.foggyframework.dataset.db.model.engine.compose.schema;

import java.util.Objects;

/**
 * One column in an {@link OutputSchema}.
 *
 * <p>M4 operates on <b>declared</b> schemas: what the user wrote in
 * {@code columns}. Types are intentionally left unset here
 * ({@link #dataType} is reserved for M5/M6 when QM type info + authority
 * binding become available).</p>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #name} — Output name. After alias resolution this is what
 *       the next plan layer references (e.g. {@code "totalAmount"} not
 *       {@code "SUM(amount) AS totalAmount"}).</li>
 *   <li>{@link #expression} — The full expression text before alias
 *       stripping. Preserved so M6 can lower the expression to SQL without
 *       re-parsing the alias.</li>
 *   <li>{@link #sourceModel} — QM name that originally produced this
 *       column ({@link com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan})
 *       or {@code null} when the column flows through a derived / union /
 *       join and source attribution is lost. Informational only in M4.</li>
 *   <li>{@link #dataType} — Reserved for M5/M6 type inference; always
 *       {@code null} from M4 derivation.</li>
 *   <li>{@link #hasExplicitAlias} — {@code true} iff the user wrote
 *       {@code ... AS <alias>}. Used only for error-message disambiguation;
 *       does not change behaviour.</li>
 * </ul>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.output_schema.ColumnSpec}
 * (Python {@code @dataclass(frozen=True)}).</p>
 *
 * @since 8.2.0.beta
 */
public final class ColumnSpec {

    private final String name;
    private final String expression;
    private final String sourceModel;   // nullable
    private final String dataType;      // nullable — reserved for M5/M6
    private final boolean hasExplicitAlias;

    private ColumnSpec(Builder b) {
        if (b.name == null || b.name.isEmpty()) {
            throw new IllegalArgumentException(
                    "ColumnSpec.name must be a non-empty string, got: " + b.name);
        }
        if (b.expression == null || b.expression.isEmpty()) {
            throw new IllegalArgumentException(
                    "ColumnSpec.expression must be a non-empty string, got: " + b.expression);
        }
        this.name = b.name;
        this.expression = b.expression;
        this.sourceModel = b.sourceModel;
        this.dataType = b.dataType;
        this.hasExplicitAlias = b.hasExplicitAlias;
    }

    public String name() { return name; }
    public String expression() { return expression; }
    public String sourceModel() { return sourceModel; }
    public String dataType() { return dataType; }
    public boolean hasExplicitAlias() { return hasExplicitAlias; }

    public static Builder builder() { return new Builder(); }

    /** Convenience: minimal {@code name + expression} ColumnSpec. */
    public static ColumnSpec of(String name, String expression) {
        return builder().name(name).expression(expression).build();
    }

    public static final class Builder {
        private String name;
        private String expression;
        private String sourceModel;
        private String dataType;
        private boolean hasExplicitAlias;

        public Builder name(String v) { this.name = v; return this; }
        public Builder expression(String v) { this.expression = v; return this; }
        public Builder sourceModel(String v) { this.sourceModel = v; return this; }
        public Builder dataType(String v) { this.dataType = v; return this; }
        public Builder hasExplicitAlias(boolean v) { this.hasExplicitAlias = v; return this; }

        public ColumnSpec build() { return new ColumnSpec(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnSpec)) return false;
        ColumnSpec c = (ColumnSpec) o;
        return hasExplicitAlias == c.hasExplicitAlias
                && Objects.equals(name, c.name)
                && Objects.equals(expression, c.expression)
                && Objects.equals(sourceModel, c.sourceModel)
                && Objects.equals(dataType, c.dataType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, expression, sourceModel, dataType, hasExplicitAlias);
    }

    @Override
    public String toString() {
        return "ColumnSpec{name=" + name
                + ", expression=" + expression
                + ", sourceModel=" + sourceModel
                + ", dataType=" + dataType
                + ", hasExplicitAlias=" + hasExplicitAlias
                + '}';
    }
}
