package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.engine.compose.plan.PlanId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
 *   <li>{@link #planProvenance} <b>(G10 PR1)</b> — Plan-level identity of the
 *       node that produced this column, captured as a {@link PlanId}
 *       (transient weak reference; see {@link PlanId} javadoc). {@code null}
 *       in PR1 — no producer sets it yet. Filled in by G10 PR2 (flag-gated
 *       SchemaDerivation refactor) so post-join disambiguation (G5 F5) and
 *       plan-routed permissions can resolve a column back to its plan.</li>
 *   <li>{@link #isAmbiguous} <b>(G10 PR1)</b> — {@code true} when this column
 *       name occurs in multiple side schemas of a join (the same name appears
 *       on both {@code left} and {@code right}). {@code false} in PR1 — no
 *       producer sets it yet. Filled in by G10 PR2.</li>
 * </ul>
 *
 * <p><b>G10 PR1 真零行为变化保证</b>: the new fields default to
 * {@code null}/{@code false} and are <em>not</em> read by any compiler /
 * validator / lookup path in PR1. They also do not participate in
 * {@link #equals}/{@link #hashCode} — the existing equality contract
 * (name + expression + sourceModel + dataType + hasExplicitAlias) is
 * preserved bitwise. PR2 (when fields actually get set) will revisit
 * whether to include {@code planProvenance} in equality.</p>
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
    // G10 PR1 — types only, no producer sets these yet
    private final PlanId planProvenance;  // nullable — PR2 fills in (flag-gated)
    private final boolean isAmbiguous;     // PR2 sets to true on join overlap (flag-gated)
    // S7a POC — semantic metadata, excluded from equals/hashCode
    private final String semanticKind;         // nullable — S7a POC
    private final String valueMeaning;         // nullable — S7a POC
    private final Set<String> lineage;         // nullable — S7a POC (unmodifiable)
    private final Set<String> referencePolicy; // nullable — S7a POC (unmodifiable)

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
        this.planProvenance = b.planProvenance;
        this.isAmbiguous = b.isAmbiguous;
        this.semanticKind = b.semanticKind;
        this.valueMeaning = b.valueMeaning;
        this.lineage = b.lineage == null ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(b.lineage));
        this.referencePolicy = b.referencePolicy == null ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(b.referencePolicy));
    }

    public String name() { return name; }
    public String expression() { return expression; }
    public String sourceModel() { return sourceModel; }
    public String dataType() { return dataType; }
    public boolean hasExplicitAlias() { return hasExplicitAlias; }

    /**
     * G10 PR1 — plan-level provenance (PlanId of the producing plan node).
     *
     * <p>{@code null} in PR1 (no producer sets it). Once G10 PR2 lands,
     * SchemaDerivation will populate this for join-merged columns so
     * downstream code can route a column back to its origin plan.</p>
     */
    public PlanId planProvenance() { return planProvenance; }

    /**
     * G10 PR1 — {@code true} when this column name overlaps with another
     * side of a join (same name on both {@code left} and {@code right}).
     *
     * <p>{@code false} in PR1 (no producer sets it). Once G10 PR2 lands,
     * SchemaDerivation will mark overlapping join columns ambiguous instead
     * of throwing {@code JOIN_OUTPUT_COLUMN_CONFLICT}; downstream consumers
     * (G10 PR3 / PR4) handle the disambiguation.</p>
     */
    public boolean isAmbiguous() { return isAmbiguous; }

    /** S7a POC — semantic classification (e.g. "base_field", "time_window_derived"). */
    public String semanticKind() { return semanticKind; }
    /** S7a POC — human/LLM-readable meaning of this column's value. */
    public String valueMeaning() { return valueMeaning; }
    /** S7a POC — upstream field(s) this column derives from. */
    public Set<String> lineage() { return lineage; }
    /** S7a POC — capability set (readable, groupable, aggregatable, etc.). */
    public Set<String> referencePolicy() { return referencePolicy; }

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
        private PlanId planProvenance;
        private boolean isAmbiguous;
        private String semanticKind;
        private String valueMeaning;
        private Set<String> lineage;
        private Set<String> referencePolicy;

        public Builder name(String v) { this.name = v; return this; }
        public Builder expression(String v) { this.expression = v; return this; }
        public Builder sourceModel(String v) { this.sourceModel = v; return this; }
        public Builder dataType(String v) { this.dataType = v; return this; }
        public Builder hasExplicitAlias(boolean v) { this.hasExplicitAlias = v; return this; }

        /** G10 PR1 — see {@link ColumnSpec#planProvenance()}. */
        public Builder planProvenance(PlanId v) { this.planProvenance = v; return this; }

        /** G10 PR1 — see {@link ColumnSpec#isAmbiguous()}. */
        public Builder isAmbiguous(boolean v) { this.isAmbiguous = v; return this; }

        /** S7a POC — see {@link ColumnSpec#semanticKind()}. */
        public Builder semanticKind(String v) { this.semanticKind = v; return this; }
        /** S7a POC — see {@link ColumnSpec#valueMeaning()}. */
        public Builder valueMeaning(String v) { this.valueMeaning = v; return this; }
        /** S7a POC — see {@link ColumnSpec#lineage()}. */
        public Builder lineage(Set<String> v) { this.lineage = v; return this; }
        /** S7a POC — see {@link ColumnSpec#referencePolicy()}. */
        public Builder referencePolicy(Set<String> v) { this.referencePolicy = v; return this; }

        public ColumnSpec build() { return new ColumnSpec(this); }
    }

    /**
     * G10 PR1 真零行为保证：equality unchanged from M4 era. The new
     * {@code planProvenance} / {@code isAmbiguous} fields are
     * <em>excluded</em> from equality so existing tests / compare paths
     * see no behavior shift. PR2 will revisit when fields actually carry
     * meaningful values.
     */
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
                + ", planProvenance=" + planProvenance
                + ", isAmbiguous=" + isAmbiguous
                + ", semanticKind=" + semanticKind
                + ", valueMeaning=" + valueMeaning
                + ", lineage=" + lineage
                + ", referencePolicy=" + referencePolicy
                + '}';
    }
}
