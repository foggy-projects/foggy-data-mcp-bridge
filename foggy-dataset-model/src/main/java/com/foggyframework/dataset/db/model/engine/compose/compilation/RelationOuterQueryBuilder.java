package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.relation.CompiledRelation;
import com.foggyframework.dataset.db.model.engine.compose.relation.ReferencePolicy;
import com.foggyframework.dataset.db.model.engine.compose.relation.RelationWrapStrategy;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;

import java.util.*;
import java.util.regex.Pattern;

/**
 * S7d · Build a read-only outer query over a {@link CompiledRelation}.
 *
 * <p>Validates all column references against the relation's
 * {@link OutputSchema} and {@link ReferencePolicy}, generates
 * wrapped SQL using the appropriate strategy (inline subquery
 * or hoisted CTE), and enforces the S7c fail-closed invariants.</p>
 *
 * <h3>Allowed operations</h3>
 * <ul>
 *   <li>SELECT readable columns</li>
 *   <li>ORDER BY orderable columns</li>
 *   <li>WHERE filter on readable columns</li>
 *   <li>LIMIT / OFFSET pagination</li>
 * </ul>
 *
 * <h3>Not allowed</h3>
 * <ul>
 *   <li>Outer aggregate (SUM, AVG, COUNT, MIN, MAX)</li>
 *   <li>Outer window (OVER(...))</li>
 *   <li>Relation join / union</li>
 * </ul>
 *
 * @since 8.5.0.beta (S7d)
 */
public final class RelationOuterQueryBuilder {

    private RelationOuterQueryBuilder() { /* utility */ }

    // Aggregate function pattern — matches SUM(...), COUNT(*), etc.
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "\\b(SUM|AVG|COUNT|MIN|MAX)\\s*\\(", Pattern.CASE_INSENSITIVE);

    // Window function pattern — matches ... OVER(...)
    private static final Pattern WINDOW_PATTERN = Pattern.compile(
            "\\bOVER\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * Build a read-only outer query over the given compiled relation.
     *
     * @param relation the pre-compiled relation source
     * @param spec     outer query specification (columns, orderBy, filter, etc.)
     * @return an immutable {@link RelationOuterQuery}
     * @throws ComposeCompileException on validation failure
     */
    public static RelationOuterQuery buildOuterQuery(
            CompiledRelation relation,
            OuterQuerySpec spec) {

        if (relation == null) {
            throw new IllegalArgumentException(
                    "buildOuterQuery: relation must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException(
                    "buildOuterQuery: spec must not be null");
        }

        String wrapStrategy = relation.capabilities().relationWrapStrategy();
        if (RelationWrapStrategy.FAIL_CLOSED.equals(wrapStrategy)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Cannot build outer query: the relation's wrap strategy "
                            + "is FAIL_CLOSED for dialect '"
                            + relation.dialect() + "'.");
        }

        OutputSchema schema = relation.outputSchema();
        String alias = relation.alias();
        String dialect = relation.dialect();

        // ---- 1. Resolve select columns ----
        List<String> selectCols = resolveSelectColumns(spec, schema);

        // ---- 2. Validate select columns ----
        validateSelectColumns(selectCols, schema);

        // ---- 3. Validate orderBy columns ----
        List<String> orderByCols = spec.orderBy();
        if (orderByCols != null && !orderByCols.isEmpty()) {
            validateOrderByColumns(orderByCols, schema);
        }

        // ---- 4. Validate filter columns ----
        if (spec.filter() != null && !spec.filter().isBlank()) {
            validateFilterColumns(spec.filterColumns(), schema);
        }

        // ---- 5. Build output schema (subset) ----
        OutputSchema outputSchema = buildOutputSchema(selectCols, schema);

        // ---- 6. Generate SQL ----
        String bodySql = relation.relationSql().bodySql();
        List<Object> innerParams = relation.params() != null
                ? relation.params()
                : Collections.emptyList();

        String sql;
        if (RelationWrapStrategy.HOISTED_CTE.equals(wrapStrategy)) {
            sql = buildHoistedCteSql(bodySql, alias, selectCols, spec,
                    dialect);
        } else {
            // INLINE_SUBQUERY or NATIVE_CTE (treated as inline for now)
            sql = buildInlineSubquerySql(bodySql, alias, selectCols, spec,
                    dialect);
        }

        // ---- 7. Flatten params ----
        List<Object> allParams = new ArrayList<>(innerParams);
        if (spec.filterParams() != null) {
            allParams.addAll(spec.filterParams());
        }

        // ---- 8. Post-compilation FROM (WITH safety ----
        if (ComposeRelationCompiler.containsFromWith(sql)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Outer query SQL for dialect '" + dialect
                            + "' contains forbidden 'FROM (WITH' pattern.");
        }

        return RelationOuterQuery.builder()
                .sql(sql)
                .params(allParams)
                .outputSchema(outputSchema)
                .datasourceId(relation.datasourceId())
                .dialect(dialect)
                .build();
    }

    // ------------------------------------------------------------------
    // Column resolution
    // ------------------------------------------------------------------

    /**
     * Resolve select columns: if spec provides explicit columns, use them;
     * otherwise select all readable columns from the schema.
     */
    private static List<String> resolveSelectColumns(
            OuterQuerySpec spec, OutputSchema schema) {
        if (spec.selectColumns() != null && !spec.selectColumns().isEmpty()) {
            return spec.selectColumns();
        }
        // Select all readable columns
        List<String> readable = new ArrayList<>();
        for (ColumnSpec col : schema.columns()) {
            if (isReadable(col)) {
                readable.add(col.name());
            }
        }
        if (readable.isEmpty()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "No readable columns found in the relation's output schema.");
        }
        return readable;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private static void validateSelectColumns(
            List<String> columns, OutputSchema schema) {
        for (String col : columns) {
            // Check for outer aggregate
            if (AGGREGATE_PATTERN.matcher(col).find()) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Outer aggregate is not supported (S7d read-only): '"
                                + col + "'. supportsOuterAggregate=false.");
            }
            // Check for outer window
            if (WINDOW_PATTERN.matcher(col).find()) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Outer window is not supported (S7d read-only): '"
                                + col + "'. supportsOuterWindow=false.");
            }
            // Check column exists
            String baseName = extractColumnName(col);
            if (!schema.contains(baseName)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Column '" + baseName + "' does not exist in the "
                                + "relation's output schema. Available columns: "
                                + schema.nameSet());
            }
            // Check readable
            ColumnSpec spec = schema.get(baseName);
            if (spec != null && !isReadable(spec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Column '" + baseName + "' is not readable. "
                                + "referencePolicy=" + spec.referencePolicy());
            }
        }
    }

    private static void validateOrderByColumns(
            List<String> orderBy, OutputSchema schema) {
        for (String clause : orderBy) {
            String baseName = extractOrderByColumnName(clause);
            if (!schema.contains(baseName)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "ORDER BY column '" + baseName + "' does not exist "
                                + "in the relation's output schema. "
                                + "Available columns: " + schema.nameSet());
            }
            ColumnSpec spec = schema.get(baseName);
            if (spec != null && !isOrderable(spec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_ORDERABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Column '" + baseName + "' is not orderable. "
                                + "referencePolicy=" + spec.referencePolicy());
            }
        }
    }

    private static void validateFilterColumns(
            Set<String> filterColumns, OutputSchema schema) {
        if (filterColumns == null || filterColumns.isEmpty()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Filter expression must declare referenced columns via "
                            + "OuterQuerySpec.filterColumns so relation "
                            + "referencePolicy can be validated.");
        }
        for (String col : filterColumns) {
            if (!schema.contains(col)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Filter column '" + col + "' does not exist in the "
                                + "relation's output schema. "
                                + "Available columns: " + schema.nameSet());
            }
            ColumnSpec spec = schema.get(col);
            if (spec != null && !isReadable(spec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Filter column '" + col + "' is not readable. "
                                + "referencePolicy=" + spec.referencePolicy());
            }
        }
    }

    // ------------------------------------------------------------------
    // Reference policy checks
    // ------------------------------------------------------------------

    /**
     * A column is readable if its referencePolicy is null (non-enriched
     * schema → backward-compatible default) or contains
     * {@link ReferencePolicy#READABLE}.
     */
    static boolean isReadable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.READABLE);
    }

    /**
     * A column is orderable if its referencePolicy is null (backward-
     * compatible default) or contains {@link ReferencePolicy#ORDERABLE}.
     */
    static boolean isOrderable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.ORDERABLE);
    }

    // ------------------------------------------------------------------
    // Output schema
    // ------------------------------------------------------------------

    private static OutputSchema buildOutputSchema(
            List<String> selectCols, OutputSchema innerSchema) {
        List<ColumnSpec> outCols = new ArrayList<>(selectCols.size());
        for (String name : selectCols) {
            ColumnSpec inner = innerSchema.get(name);
            if (inner != null) {
                outCols.add(inner);
            }
        }
        return OutputSchema.of(outCols);
    }

    // ------------------------------------------------------------------
    // SQL generation
    // ------------------------------------------------------------------

    private static String buildInlineSubquerySql(
            String bodySql, String alias,
            List<String> selectCols, OuterQuerySpec spec,
            String dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSelectClause(selectCols, alias, dialect));
        sb.append("\nFROM (").append(bodySql).append(") AS ").append(alias);
        appendWhereOrderLimit(sb, spec, alias, dialect);
        return sb.toString();
    }

    private static String buildHoistedCteSql(
            String bodySql, String alias,
            List<String> selectCols, OuterQuerySpec spec,
            String dialect) {
        StringBuilder sb = new StringBuilder();
        if (isSqlServerDialect(dialect)) {
            sb.append(";");
        }
        sb.append("WITH ").append(alias).append(" AS (")
                .append(bodySql).append(")\n");
        sb.append(buildSelectClause(selectCols, alias, dialect));
        sb.append("\nFROM ").append(alias);
        appendWhereOrderLimit(sb, spec, alias, dialect);
        return sb.toString();
    }

    private static String buildSelectClause(
            List<String> selectCols, String alias, String dialect) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < selectCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(alias).append(".")
                    .append(quoteIdentifier(selectCols.get(i), dialect));
        }
        return sb.toString();
    }

    private static void appendWhereOrderLimit(
            StringBuilder sb, OuterQuerySpec spec,
            String alias, String dialect) {
        if (spec.filter() != null && !spec.filter().isBlank()) {
            sb.append("\nWHERE ").append(spec.filter());
        }
        if (spec.orderBy() != null && !spec.orderBy().isEmpty()) {
            sb.append("\nORDER BY ");
            for (int i = 0; i < spec.orderBy().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(qualifyOrderByClause(spec.orderBy().get(i),
                        alias, dialect));
            }
        }
        if (spec.limit() != null) {
            sb.append("\nLIMIT ").append(spec.limit());
        }
        if (spec.offset() != null) {
            sb.append("\nOFFSET ").append(spec.offset());
        }
    }

    // ------------------------------------------------------------------
    // String helpers
    // ------------------------------------------------------------------

    /**
     * Extract the base column name from a select column expression.
     * Strips trailing " AS alias" if present.
     */
    private static String extractColumnName(String col) {
        String trimmed = col.trim();
        int asIdx = trimmed.toUpperCase(Locale.ROOT).lastIndexOf(" AS ");
        if (asIdx > 0) {
            return trimmed.substring(0, asIdx).trim();
        }
        return trimmed;
    }

    /**
     * Extract the column name from an ORDER BY clause.
     * Strips trailing " ASC" / " DESC" if present.
     */
    private static String extractOrderByColumnName(String clause) {
        String trimmed = clause.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" ASC")) {
            return trimmed.substring(0, trimmed.length() - 4).trim();
        }
        if (upper.endsWith(" DESC")) {
            return trimmed.substring(0, trimmed.length() - 5).trim();
        }
        return trimmed;
    }

    /**
     * Qualify an ORDER BY clause with the alias prefix.
     * E.g. "storeName DESC" → "rel_0.`storeName` DESC".
     */
    private static String qualifyOrderByClause(
            String clause, String alias, String dialect) {
        String trimmed = clause.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String suffix = "";
        String baseName;
        if (upper.endsWith(" ASC")) {
            suffix = " ASC";
            baseName = trimmed.substring(0, trimmed.length() - 4).trim();
        } else if (upper.endsWith(" DESC")) {
            suffix = " DESC";
            baseName = trimmed.substring(0, trimmed.length() - 5).trim();
        } else {
            baseName = trimmed;
        }
        return alias + "." + quoteIdentifier(baseName, dialect) + suffix;
    }

    /**
     * Quote an identifier for the given dialect.
     * MySQL uses backticks; SQL Server uses brackets; others use double-quotes.
     */
    private static String quoteIdentifier(String name, String dialect) {
        String dl = dialect == null ? "mysql" : dialect.toLowerCase(Locale.ROOT);
        if (dl.startsWith("mysql")) {
            return "`" + name + "`";
        }
        if (isSqlServerDialect(dl)) {
            return "[" + name + "]";
        }
        return "\"" + name + "\"";
    }

    private static boolean isSqlServerDialect(String dialect) {
        String dl = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return "mssql".equals(dl) || "sqlserver".equals(dl);
    }
}
