package com.foggyframework.dataset.db.model.engine.compose.schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Column-alias extraction for Compose Query {@code columns[*]} strings.
 *
 * <p>Grammar (very small subset, case-insensitive keyword):
 * <pre>
 *     &lt;column_spec&gt;   ::= &lt;expression&gt; (&lt;ws&gt;+ "AS" &lt;ws&gt;+ &lt;identifier&gt;)?
 *     &lt;identifier&gt;    ::= &lt;letter|underscore&gt; (&lt;letter|digit|underscore|$&gt;)*
 * </pre>
 *
 * <p>Examples:
 * <pre>
 *     "orderId"                         → output = "orderId"           · expr = "orderId"
 *     "customer$id"                     → output = "customer$id"       · expr = "customer$id"
 *     "customer$id AS customerId"       → output = "customerId"        · expr = "customer$id"
 *     "SUM(amount)"                     → output = "SUM(amount)"       · expr = "SUM(amount)"
 *     "SUM(amount) AS totalAmount"      → output = "totalAmount"       · expr = "SUM(amount)"
 *     "SUM(IIF(isOverdue==1,x,0)) AS y" → output = "y"                 · expr = "SUM(IIF(...))"
 *     "  foo   AS   bar  "              → output = "bar"               · expr = "foo"
 * </pre>
 *
 * <p>Design notes:
 * <ol>
 *   <li>{@code AS} matching is case-insensitive and requires whitespace on
 *       both sides — this avoids false-positives inside string literals and
 *       inside identifiers ({@code "ASSETS"}).</li>
 *   <li>We match {@code AS} at the <b>last</b> occurrence at the top level —
 *       this way a legal expression like {@code "CAST(x AS INT) AS y"}
 *       (currently not supported in fsscript but could be in future) still
 *       yields the outermost alias {@code y}.</li>
 *   <li>We validate the alias as a simple identifier. Anything weirder
 *       (spaces, operators, dots) in the alias slot is treated as "not an
 *       alias" and the whole string becomes the expression. This is the
 *       safe failure mode.</li>
 * </ol>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.alias.extract_column_alias}.</p>
 *
 * @since 8.2.0.beta
 */
public final class AliasExtractor {

    private AliasExtractor() { /* utility */ }

    /** Case-insensitive {@code AS} surrounded by whitespace; we pick the LAST match. */
    private static final Pattern AS_PATTERN = Pattern.compile("\\s+AS\\s+", Pattern.CASE_INSENSITIVE);

    /** Alias side must be a plain identifier (letter/digit/underscore/{@code $}). */
    private static final Pattern ALIAS_IDENT = Pattern.compile("\\A[A-Za-z_][A-Za-z0-9_$]*\\z");

    /**
     * Split a {@code columns[*]} entry into its expression and output-name parts.
     *
     * @param columnSpec a non-empty string from {@code plan.columns}.
     * @return the decomposition
     * @throws IllegalArgumentException when {@code columnSpec} is null, empty,
     *         whitespace-only, or has an alias but no preceding expression.
     */
    public static ColumnAliasParts extract(String columnSpec) {
        if (columnSpec == null) {
            throw new IllegalArgumentException(
                    "column_spec must be non-null string");
        }
        String stripped = columnSpec.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(
                    "column_spec must be a non-empty (non-whitespace) string");
        }

        // Find all AS split points; pick the last one.
        Matcher matcher = AS_PATTERN.matcher(stripped);
        int lastStart = -1;
        int lastEnd = -1;
        while (matcher.find()) {
            lastStart = matcher.start();
            lastEnd = matcher.end();
        }

        if (lastStart < 0) {
            return new ColumnAliasParts(stripped, stripped, false);
        }

        String candidateAlias = stripped.substring(lastEnd).strip();
        if (!ALIAS_IDENT.matcher(candidateAlias).matches()) {
            // Not a legal identifier — treat the entire input as the expression.
            return new ColumnAliasParts(stripped, stripped, false);
        }

        String expression = stripped.substring(0, lastStart).strip();
        if (expression.isEmpty()) {
            // "AS name" with no preceding expression is not a legal spec.
            throw new IllegalArgumentException(
                    "column_spec '" + columnSpec + "' has an alias but no expression");
        }

        return new ColumnAliasParts(expression, candidateAlias, true);
    }
}
