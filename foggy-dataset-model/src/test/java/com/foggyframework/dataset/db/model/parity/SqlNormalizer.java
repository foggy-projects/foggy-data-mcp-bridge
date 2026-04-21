package com.foggyframework.dataset.db.model.parity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL canonical-form normalizer for Formula parity (M5 Step 5.1 — Java side).
 *
 * <p>Mirrors <code>tests/integration/_sql_normalizer.py</code> on the Python side so
 * both engines produce the same canonical string + parameter tuple when their
 * formula compilers run against the shared catalog at
 * <code>src/test/resources/parity/formula-parity-expressions.json</code>.  See
 * <code>docs/v1.4/formula-spec-v1/parity.md §2</code> for the authoritative rules.</p>
 *
 * <p>The Java side today renders calculated-field SQL with <b>inline literals</b> —
 * <code>CASE WHEN (status = 'posted') THEN amount ELSE 0 END</code> — while the
 * Python {@code FormulaCompiler} emits {@code ?} placeholders plus a
 * {@code bind_params} tuple.  {@link #toCanonical(String, List)} accepts both
 * forms and converges them:
 * <ul>
 *   <li>when {@code params} is {@code null} the SQL is treated as Java-form:
 *       string/number literals are extracted into {@code ?} placeholders in
 *       left-to-right order</li>
 *   <li>whitespace is collapsed, SQL keywords are upper-cased, and a flat
 *       redundant <code>((X))</code> pair is reduced to <code>(X)</code></li>
 * </ul>
 *
 * <p>If the two ports drift (regex / keyword table), the parity test surfaces
 * it.  Keep this class line-for-line behaviourally identical with its Python
 * twin — always change both together.</p>
 */
public final class SqlNormalizer {

    private SqlNormalizer() {
    }

    /** Keywords we canonicalize to upper-case.  Must match the Python twin. */
    private static final String[] KEYWORDS = {
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "AND", "OR", "NOT", "IN", "IS", "NULL", "BETWEEN",
            "COALESCE", "ABS", "ROUND", "CEILING", "CEIL", "FLOOR",
            "CAST", "AS", "DATE", "INTERVAL", "DATEADD", "DATEDIFF",
            "DATE_ADD", "DATE_SUB", "NOW", "GETDATE",
            "SUM", "COUNT", "AVG", "MAX", "MIN", "DISTINCT",
            "DAY", "MONTH", "YEAR", "TRUE", "FALSE"
    };

    private static final Pattern KEYWORD_RE;
    static {
        StringBuilder sb = new StringBuilder("\\b(");
        for (int i = 0; i < KEYWORDS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(KEYWORDS[i]));
        }
        sb.append(")\\b");
        KEYWORD_RE = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static final Pattern DOUBLE_PAREN_FLAT_RE =
            Pattern.compile("\\(\\s*\\(([^()]*)\\)\\s*\\)");

    /** Matches <code>'...'</code> with SQL-style {@code ''} escape for embedded quote. */
    private static final Pattern STRING_LITERAL_RE =
            Pattern.compile("'((?:[^']|'')*)'");

    /** Numeric literal outside a word-char context (so {@code foo123} is spared). */
    private static final Pattern NUMERIC_LITERAL_RE =
            Pattern.compile("(?<![A-Za-z_0-9.])(-?\\d+(?:\\.\\d+)?)(?![A-Za-z_0-9.])");

    /**
     * Canonical-form result from {@link #toCanonical(String, List)}.
     *
     * <p>{@code params} is never null — it's the empty list for "no literals".</p>
     */
    public static final class Canonical {
        public final String sql;
        public final List<Object> params;

        Canonical(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /**
     * Convert SQL + (optional) parameter list into the shared canonical form.
     *
     * @param sql    raw SQL string from the compiler under test
     * @param params Python-side input: {@code ?} placeholder values in left-to-right
     *               order.  Pass {@code null} for Java-side input (inline literals
     *               will be extracted).
     */
    public static Canonical toCanonical(String sql, List<Object> params) {
        List<Object> effectiveParams;
        String working = sql;
        if (params == null) {
            ExtractResult extracted = extractInlineLiterals(working);
            working = extracted.sql;
            effectiveParams = extracted.params;
        } else {
            effectiveParams = new ArrayList<>(params);
        }

        working = collapseWhitespace(working);
        working = upperKeywords(working);
        working = collapseRedundantParens(working);
        working = collapseWhitespace(working);
        return new Canonical(working, effectiveParams);
    }

    // ------------------------------------------------------------------ //
    // Internals
    // ------------------------------------------------------------------ //

    private static String collapseWhitespace(String sql) {
        String s = sql.replaceAll("[\\t\\r\\n]+", " ");
        s = s.replaceAll(" {2,}", " ");
        s = s.replaceAll("\\(\\s+", "(");
        s = s.replaceAll("\\s+\\)", ")");
        return s.trim();
    }

    private static String upperKeywords(String sql) {
        Matcher m = KEYWORD_RE.matcher(sql);
        StringBuffer buf = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(buf, Matcher.quoteReplacement(m.group(0).toUpperCase()));
        }
        m.appendTail(buf);
        return buf.toString();
    }

    private static String collapseRedundantParens(String sql) {
        String current = sql;
        while (true) {
            String next = DOUBLE_PAREN_FLAT_RE.matcher(current).replaceAll("($1)");
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
    }

    private static final class ExtractResult {
        final String sql;
        final List<Object> params;

        ExtractResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /** Re-introduce {@code ?} placeholders for inline literals, left-to-right. */
    private static ExtractResult extractInlineLiterals(String sql) {
        List<Object> params = new ArrayList<>();

        // Pass 1: string literals.  We replace each with a sentinel token so
        // the numeric pass can't eat a digit that lives inside a string.
        Matcher strM = STRING_LITERAL_RE.matcher(sql);
        StringBuffer buf = new StringBuffer();
        while (strM.find()) {
            String raw = strM.group(1).replace("''", "'");
            params.add(raw);
            strM.appendReplacement(buf, Matcher.quoteReplacement("\u0000p\u0000"));
        }
        strM.appendTail(buf);
        String pass1 = buf.toString();

        // Pass 2: numeric literals (integers / decimals; signed).
        Matcher numM = NUMERIC_LITERAL_RE.matcher(pass1);
        StringBuffer buf2 = new StringBuffer();
        while (numM.find()) {
            String raw = numM.group(1);
            Object value;
            if (raw.contains(".")) {
                value = Double.parseDouble(raw);
            } else {
                value = Long.parseLong(raw);
            }
            params.add(value);
            numM.appendReplacement(buf2, Matcher.quoteReplacement("\u0000p\u0000"));
        }
        numM.appendTail(buf2);

        return new ExtractResult(buf2.toString().replace("\u0000p\u0000", "?"), params);
    }
}
