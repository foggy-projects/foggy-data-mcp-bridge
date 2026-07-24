package com.foggyframework.dataset.model.semantic.util;

import com.foggyframework.dataset.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitize raw executor error messages so they never leak physical schema.
 *
 * <p>Background (BUG-007 v1.3): when a query slips past
 * {@code SchemaAwareFieldValidationStep} and the database rejects the SQL,
 * the raw {@code DataAccessException} message can include physical table
 * aliases ({@code t}, {@code j1}, {@code dp} …) and physical column names
 * ({@code move_name}, {@code account_move_line.move_name} …).  This breaks
 * the governance boundary established by {@code fieldAccess} /
 * {@code deniedColumns} / {@link PhysicalColumnMapping}: the blacklist can
 * hide columns from results but the error channel can still disclose them.</p>
 *
 * <p>This class provides pure, side-effect-free helpers that rewrite raw
 * error text into QM vocabulary:</p>
 * <ul>
 *   <li>{@code <alias>.<qm_token>}  →  {@code <qm_token>}
 *       — alias stripped, token with {@code $} preserved.</li>
 *   <li>{@code <alias>.<phys_col>}  →  {@code <qm_field>}
 *       — translated via the model's {@link PhysicalColumnMapping}.</li>
 *   <li>{@code <alias>.<phys_col>}  →  {@code <phys_col>}
 *       — if no mapping is available, the alias is still stripped.</li>
 *   <li>Double-quoted identifiers ({@code "alias.col"} / {@code "col"}) are
 *       rewritten with the same rules.</li>
 *   <li>PostgreSQL {@code HINT: Perhaps you meant to reference the column
 *       "X".} is rewritten to {@code Did you mean 'X'?} so the suggestion
 *       survives but the DB-specific marker does not.</li>
 *   <li>Any remaining {@code HINT:} line is stripped.</li>
 *   <li>The model name is prepended when provided, for upstream audit /
 *       LLM routing.</li>
 * </ul>
 *
 * <p>Mirrors the Python sanitizer
 * {@code foggy.dataset_model.semantic.error_sanitizer.sanitize_engine_error}
 * so MCP error surfaces stay engine-agnostic.</p>
 *
 * @since 8.2.1 (BUG-007 v1.3)
 */
public final class QueryErrorSanitizer {

    private QueryErrorSanitizer() {}

    /** Matches {@code <alias>.<column>} with word-boundary lookarounds. */
    private static final Pattern ALIAS_COL_RE = Pattern.compile(
            "(?<![A-Za-z0-9_$])([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_$][A-Za-z0-9_$]*)(?![A-Za-z0-9_$])"
    );

    /** Matches double-quoted identifiers used by PostgreSQL for physical names. */
    private static final Pattern DQUOTED_RE = Pattern.compile("\"([^\"]+)\"");

    /** Matches the canonical PostgreSQL HINT column suggestion line. */
    private static final Pattern HINT_COLUMN_RE = Pattern.compile(
            "(?im)^\\s*HINT:\\s*Perhaps you meant to reference the column\\s+\"([^\"]+)\"\\.?\\s*$"
    );

    /** Fallback: any remaining {@code HINT:} line is DB-specific and stripped. */
    private static final Pattern HINT_ANY_RE = Pattern.compile("(?im)^\\s*HINT:[^\\n]*$");

    /** Collapse extra blank lines introduced by HINT removal. */
    private static final Pattern BLANK_LINES_RE = Pattern.compile("\\n{2,}");

    /**
     * Sanitize a raw executor / DB error message.
     *
     * @param rawMessage raw error text (may be null or empty — returns "")
     * @param queryModel query model for {@link PhysicalColumnMapping} lookup
     *                   (nullable — no translation, only alias stripping
     *                   and HINT rewriting will run)
     * @return sanitized, QM-vocabulary error message suitable to forward
     *         to upstream callers without leaking physical schema.
     */
    public static String sanitize(String rawMessage, QueryModel queryModel) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }
        PhysicalColumnMapping mapping = queryModel != null ? queryModel.getPhysicalColumnMapping() : null;
        String modelName = queryModel != null ? queryModel.getName() : null;
        return sanitize(rawMessage, modelName, mapping);
    }

    /**
     * Sanitize a raw executor / DB error message.
     *
     * <p>Lower-level variant accepting the model name and mapping directly,
     * for call sites where the {@link QueryModel} is not yet loaded.</p>
     *
     * @param rawMessage raw error text (may be null or empty — returns "")
     * @param modelName  query model name (for prefixing); may be null
     * @param mapping    physical-column mapping (nullable — still strips aliases)
     * @return sanitized error message
     */
    public static String sanitize(String rawMessage, String modelName, PhysicalColumnMapping mapping) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }

        // 1. Translate unquoted <alias>.<col> references
        String msg = translateReferences(rawMessage, mapping);

        // 2. Translate double-quoted identifiers ("alias.col" / "col")
        Matcher dq = DQUOTED_RE.matcher(msg);
        StringBuilder dqOut = new StringBuilder();
        while (dq.find()) {
            String inner = dq.group(1);
            String replacement;
            if (inner.contains(".")) {
                String[] parts = inner.split("\\.", 2);
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
                        && parts[0].matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    replacement = "\"" + translateRef(parts[0], parts[1], mapping) + "\"";
                } else {
                    replacement = dq.group(0);
                }
            } else if (inner.contains("$")) {
                replacement = dq.group(0);
            } else if (mapping != null && inner.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                String qm = lookupQmField(mapping, inner, null);
                replacement = qm != null ? "\"" + qm + "\"" : dq.group(0);
            } else {
                replacement = dq.group(0);
            }
            dq.appendReplacement(dqOut, Matcher.quoteReplacement(replacement));
        }
        dq.appendTail(dqOut);
        msg = dqOut.toString();

        // 3. Rewrite ``HINT: Perhaps you meant to reference the column "X".``
        //    to QM-level ``Did you mean 'X'?`` — quoted identifiers inside
        //    the HINT have already been translated in step 2.
        msg = HINT_COLUMN_RE.matcher(msg).replaceAll("Did you mean '$1'?");

        // 4. Drop any remaining HINT: lines entirely.
        msg = HINT_ANY_RE.matcher(msg).replaceAll("");

        // Collapse extra blank lines introduced by HINT removal and trim.
        msg = BLANK_LINES_RE.matcher(msg).replaceAll("\n").trim();

        // 5. Prepend model context so upstream callers / LLMs can route /
        //    audit without needing access to SQL.
        if (modelName != null && !modelName.isEmpty() && !msg.contains(modelName)) {
            msg = "[" + modelName + "] " + msg;
        }

        return msg;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String translateReferences(String input, PhysicalColumnMapping mapping) {
        Matcher m = ALIAS_COL_RE.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String alias = m.group(1);
            String col = m.group(2);
            m.appendReplacement(out, Matcher.quoteReplacement(translateRef(alias, col, mapping)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String translateRef(String alias, String col, PhysicalColumnMapping mapping) {
        // QM-style token (contains $): strip alias, keep token
        if (col.indexOf('$') >= 0) {
            return col;
        }
        // Try QM translation via mapping
        if (mapping != null) {
            String qm = lookupQmField(mapping, col, alias);
            if (qm == null) {
                qm = lookupQmField(mapping, col, null);
            }
            if (qm != null) {
                return qm;
            }
        }
        // No mapping — still strip the alias, keep the column text
        return col;
    }

    /**
     * Look up a QM field for a physical column.  ``tableHint`` narrows the
     * search when provided; otherwise the first QM field mapping to the
     * column (across any table) is returned.
     */
    private static String lookupQmField(PhysicalColumnMapping mapping, String column, String tableHint) {
        if (mapping == null || column == null) {
            return null;
        }
        if (tableHint != null && !tableHint.isEmpty()) {
            List<String> qm = mapping.getQmFieldNames(tableHint, column);
            if (qm != null && !qm.isEmpty()) {
                return qm.get(0);
            }
        }
        // Fall back to scanning known tables — the mapping does not expose
        // a direct column-only lookup, so we search via physical tables.
        for (String table : mapping.getAllPhysicalTables()) {
            List<String> qm = mapping.getQmFieldNames(table, column);
            if (qm != null && !qm.isEmpty()) {
                return qm.get(0);
            }
        }
        return null;
    }
}
