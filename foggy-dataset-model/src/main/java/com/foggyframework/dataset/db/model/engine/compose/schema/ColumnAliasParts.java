package com.foggyframework.dataset.db.model.engine.compose.schema;

import java.util.Objects;

/**
 * Result of {@link AliasExtractor#extract(String)} — the decomposition of a
 * {@code columns[*]} entry into its expression and output-name parts.
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.alias.ColumnAliasParts}
 * (Python {@code NamedTuple}).</p>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code expression} — the expression portion with outer whitespace
 *       stripped. Equals the original (stripped) input when no alias was
 *       found.</li>
 *   <li>{@code outputName} — the alias when present; otherwise the stripped
 *       expression text. This is what downstream plans reference the
 *       column by.</li>
 *   <li>{@code hasAlias} — {@code true} iff the input contained an
 *       {@code AS <identifier>} suffix.</li>
 * </ul>
 *
 * @since 8.2.0.beta
 */
public final class ColumnAliasParts {

    private final String expression;
    private final String outputName;
    private final boolean hasAlias;

    public ColumnAliasParts(String expression, String outputName, boolean hasAlias) {
        this.expression = expression;
        this.outputName = outputName;
        this.hasAlias = hasAlias;
    }

    public String expression() { return expression; }
    public String outputName() { return outputName; }
    public boolean hasAlias() { return hasAlias; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnAliasParts)) return false;
        ColumnAliasParts that = (ColumnAliasParts) o;
        return hasAlias == that.hasAlias
                && Objects.equals(expression, that.expression)
                && Objects.equals(outputName, that.outputName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, outputName, hasAlias);
    }

    @Override
    public String toString() {
        return "ColumnAliasParts{expression=" + expression
                + ", outputName=" + outputName
                + ", hasAlias=" + hasAlias + '}';
    }
}
