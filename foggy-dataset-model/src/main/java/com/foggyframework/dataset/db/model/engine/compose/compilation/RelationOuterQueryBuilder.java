package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.relation.CompiledRelation;
import com.foggyframework.dataset.db.model.engine.compose.relation.CteItem;
import com.foggyframework.dataset.db.model.engine.compose.relation.ReferencePolicy;
import com.foggyframework.dataset.db.model.engine.compose.relation.RelationWrapStrategy;
import com.foggyframework.dataset.db.model.engine.compose.relation.SemanticKind;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S7d/S7e · Build an outer query over a {@link CompiledRelation}.
 *
 * <p>Validates all column references against the relation's
 * {@link OutputSchema} and {@link ReferencePolicy}, generates
 * wrapped SQL using the appropriate strategy (inline subquery
 * or hoisted CTE), and enforces relation fail-closed invariants.</p>
 *
 * <h3>Allowed operations</h3>
 * <ul>
 *   <li>SELECT readable columns</li>
 *   <li>S7e aggregate expressions over aggregatable columns when
 *       {@code supportsOuterAggregate=true}</li>
 *   <li>GROUP BY groupable columns</li>
 *   <li>ORDER BY orderable columns</li>
 *   <li>WHERE filter on readable columns</li>
 *   <li>LIMIT / OFFSET pagination</li>
 * </ul>
 *
 * <h3>Not allowed</h3>
 * <ul>
 *   <li>Outer window (OVER(...))</li>
 *   <li>Relation join / union</li>
 * </ul>
 *
 * @since 8.5.0.beta (S7d, S7e)
 */
public final class RelationOuterQueryBuilder {

    private RelationOuterQueryBuilder() { /* utility */ }

    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "\\b(SUM|AVG|COUNT|MIN|MAX)\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern AGGREGATE_SELECT_PATTERN = Pattern.compile(
            "^\\s*(SUM|AVG|COUNT|MIN|MAX)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*|\\*)\\s*\\)"
                    + "\\s*(?:AS\\s+([A-Za-z_][A-Za-z0-9_$]*))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WINDOW_PATTERN = Pattern.compile(
            "\\bOVER\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * Build an outer query over the given compiled relation.
     *
     * @param relation the pre-compiled relation source
     * @param spec     outer query specification (columns, groupBy, orderBy,
     *                 filter, etc.)
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

        List<String> selectCols = resolveSelectColumns(spec, schema);
        List<SelectItem> selectItems = validateSelectColumns(
                selectCols, schema,
                relation.capabilities().supportsOuterAggregate());

        if (spec.groupBy() != null && !spec.groupBy().isEmpty()) {
            validateGroupByColumns(spec.groupBy(), schema);
        }
        if (spec.orderBy() != null && !spec.orderBy().isEmpty()) {
            validateOrderByColumns(spec.orderBy(), schema);
        }
        if (spec.filter() != null && !spec.filter().isBlank()) {
            validateFilterColumns(spec.filterColumns(), schema);
        }

        OutputSchema outputSchema = buildOutputSchema(selectItems, schema);

        String bodySql = relation.relationSql().bodySql();
        List<Object> innerParams = relation.params() != null
                ? relation.params()
                : Collections.emptyList();

        String sql;
        if (RelationWrapStrategy.HOISTED_CTE.equals(wrapStrategy)) {
            sql = buildHoistedCteSql(relation.relationSql().withItems(),
                    bodySql, alias, selectItems, spec, dialect);
        } else {
            sql = buildInlineSubquerySql(bodySql, alias, selectItems, spec,
                    dialect);
        }

        List<Object> allParams = new ArrayList<>(innerParams);
        if (spec.filterParams() != null) {
            allParams.addAll(spec.filterParams());
        }

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

    private static List<String> resolveSelectColumns(
            OuterQuerySpec spec, OutputSchema schema) {
        if (spec.selectColumns() != null && !spec.selectColumns().isEmpty()) {
            return spec.selectColumns();
        }
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

    private static List<SelectItem> validateSelectColumns(
            List<String> columns, OutputSchema schema,
            boolean supportsOuterAggregate) {
        List<SelectItem> items = new ArrayList<>(columns.size());
        for (String col : columns) {
            if (WINDOW_PATTERN.matcher(col).find()) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Outer window is not supported (S7e): '"
                                + col + "'. supportsOuterWindow=false.");
            }

            SelectItem item = parseSelectItem(col);
            items.add(item);
            if (item.aggregate()) {
                validateAggregateSelect(item, schema, supportsOuterAggregate);
                continue;
            }

            String baseName = item.outputName();
            if (!schema.contains(baseName)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Column '" + baseName + "' does not exist in the "
                                + "relation's output schema. Available columns: "
                                + schema.nameSet());
            }
            ColumnSpec spec = schema.get(baseName);
            if (spec != null && !isReadable(spec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Column '" + baseName + "' is not readable. "
                                + "referencePolicy=" + spec.referencePolicy());
            }
        }
        return items;
    }

    private static void validateAggregateSelect(
            SelectItem item, OutputSchema schema,
            boolean supportsOuterAggregate) {
        if (!supportsOuterAggregate) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Outer aggregate is not supported by this relation: '"
                            + item.raw() + "'. supportsOuterAggregate=false.");
        }
        if ("*".equals(item.inputName())) {
            if (!"COUNT".equals(item.function())) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Only COUNT(*) is supported for star aggregate: '"
                                + item.raw() + "'.");
            }
            return;
        }
        if (!schema.contains(item.inputName())) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Aggregate input column '" + item.inputName()
                            + "' does not exist in the relation's output schema. "
                            + "Available columns: " + schema.nameSet());
        }
        ColumnSpec spec = schema.get(item.inputName());
        if (spec != null && !isAggregatable(spec)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_COLUMN_NOT_AGGREGATABLE,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Aggregate input column '" + item.inputName()
                            + "' is not aggregatable. referencePolicy="
                            + spec.referencePolicy());
        }
    }

    private static void validateGroupByColumns(
            List<String> groupBy, OutputSchema schema) {
        for (String col : groupBy) {
            if (!schema.contains(col)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "GROUP BY column '" + col + "' does not exist in the "
                                + "relation's output schema. "
                                + "Available columns: " + schema.nameSet());
            }
            ColumnSpec spec = schema.get(col);
            if (spec != null && !isGroupable(spec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "GROUP BY column '" + col + "' is not groupable. "
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

    static boolean isReadable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.READABLE);
    }

    static boolean isOrderable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.ORDERABLE);
    }

    static boolean isGroupable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.GROUPABLE);
    }

    static boolean isAggregatable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.AGGREGATABLE);
    }

    private static OutputSchema buildOutputSchema(
            List<SelectItem> selectItems, OutputSchema innerSchema) {
        List<ColumnSpec> outCols = new ArrayList<>(selectItems.size());
        for (SelectItem item : selectItems) {
            if (item.aggregate()) {
                outCols.add(aggregateColumnSpec(item));
            } else {
                ColumnSpec inner = innerSchema.get(item.outputName());
                if (inner != null) {
                    outCols.add(inner);
                }
            }
        }
        return OutputSchema.of(outCols);
    }

    private static ColumnSpec aggregateColumnSpec(SelectItem item) {
        String expression = item.function() + "(" + item.inputName() + ")";
        return ColumnSpec.builder()
                .name(item.outputName())
                .expression(expression)
                .hasExplicitAlias(item.hasExplicitAlias())
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .valueMeaning(item.function().toLowerCase(Locale.ROOT)
                        + " of " + item.inputName())
                .lineage("*".equals(item.inputName()) ? Set.of() : Set.of(item.inputName()))
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();
    }

    private static String buildInlineSubquerySql(
            String bodySql, String alias,
            List<SelectItem> selectItems, OuterQuerySpec spec,
            String dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSelectClause(selectItems, alias, dialect));
        sb.append("\nFROM (").append(bodySql).append(") AS ").append(alias);
        appendWhereGroupOrderLimit(sb, spec, alias, dialect);
        return sb.toString();
    }

    private static String buildHoistedCteSql(
            List<CteItem> withItems, String bodySql, String alias,
            List<SelectItem> selectItems, OuterQuerySpec spec,
            String dialect) {
        StringBuilder sb = new StringBuilder();
        if (isSqlServerDialect(dialect)) {
            sb.append(";");
        }
        sb.append("WITH ");
        boolean first = true;
        if (withItems != null) {
            for (CteItem item : withItems) {
                if (!first) {
                    sb.append(",\n");
                }
                appendCteItem(sb, item.name(), item.sql());
                first = false;
            }
        }
        if (!first) {
            sb.append(",\n");
        }
        appendCteItem(sb, alias, bodySql);
        sb.append("\n");
        sb.append(buildSelectClause(selectItems, alias, dialect));
        sb.append("\nFROM ").append(alias);
        appendWhereGroupOrderLimit(sb, spec, alias, dialect);
        return sb.toString();
    }

    private static void appendCteItem(StringBuilder sb, String name, String sql) {
        sb.append(name).append(" AS (").append(sql).append(")");
    }

    private static String buildSelectClause(
            List<SelectItem> selectItems, String alias, String dialect) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < selectItems.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(renderSelectItem(selectItems.get(i), alias, dialect));
        }
        return sb.toString();
    }

    private static String renderSelectItem(
            SelectItem item, String alias, String dialect) {
        if (!item.aggregate()) {
            return alias + "." + quoteIdentifier(item.outputName(), dialect);
        }
        String input = "*".equals(item.inputName())
                ? "*"
                : alias + "." + quoteIdentifier(item.inputName(), dialect);
        return item.function() + "(" + input + ") AS "
                + quoteIdentifier(item.outputName(), dialect);
    }

    private static void appendWhereGroupOrderLimit(
            StringBuilder sb, OuterQuerySpec spec,
            String alias, String dialect) {
        if (spec.filter() != null && !spec.filter().isBlank()) {
            sb.append("\nWHERE ").append(spec.filter());
        }
        if (spec.groupBy() != null && !spec.groupBy().isEmpty()) {
            sb.append("\nGROUP BY ");
            for (int i = 0; i < spec.groupBy().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(alias).append(".")
                        .append(quoteIdentifier(spec.groupBy().get(i), dialect));
            }
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

    private static String extractColumnName(String col) {
        String trimmed = col.trim();
        int asIdx = trimmed.toUpperCase(Locale.ROOT).lastIndexOf(" AS ");
        if (asIdx > 0) {
            return trimmed.substring(0, asIdx).trim();
        }
        return trimmed;
    }

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

    private static SelectItem parseSelectItem(String raw) {
        Matcher m = AGGREGATE_SELECT_PATTERN.matcher(raw);
        if (m.matches()) {
            String function = m.group(1).toUpperCase(Locale.ROOT);
            String inputName = m.group(2);
            String alias = m.group(3);
            String outputName = alias != null && !alias.isBlank()
                    ? alias
                    : defaultAggregateAlias(function, inputName);
            return new SelectItem(raw, true, function, inputName, outputName,
                    alias != null && !alias.isBlank());
        }
        if (AGGREGATE_PATTERN.matcher(raw).find()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported outer aggregate expression: '" + raw
                            + "'. Use FUNC(column) or FUNC(column) AS alias.");
        }
        String outputName = extractColumnName(raw);
        return new SelectItem(raw, false, null, outputName, outputName, false);
    }

    private static String defaultAggregateAlias(String function, String inputName) {
        String fn = function.toLowerCase(Locale.ROOT);
        if ("*".equals(inputName)) {
            return fn;
        }
        return fn + "_" + inputName;
    }

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

    private record SelectItem(
            String raw,
            boolean aggregate,
            String function,
            String inputName,
            String outputName,
            boolean hasExplicitAlias) {
    }
}
