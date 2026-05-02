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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S7d/S7e/S7f · Build an outer query over a {@link CompiledRelation}.
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
 *   <li>S7f window expressions over windowable columns when
 *       {@code supportsOuterWindow=true}</li>
 *   <li>GROUP BY groupable columns</li>
 *   <li>ORDER BY orderable columns</li>
 *   <li>WHERE filter on readable columns</li>
 *   <li>LIMIT / OFFSET pagination</li>
 * </ul>
 *
 * <h3>Not allowed</h3>
 * <ul>
 *   <li>Relation join / union</li>
 *   <li>Arbitrary raw OVER(...) — only parsed structured subset</li>
 * </ul>
 *
 * @since 8.5.0.beta (S7d, S7e, S7f)
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
                relation.capabilities().supportsOuterAggregate(),
                relation.capabilities().supportsOuterWindow());

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
            boolean supportsOuterAggregate,
            boolean supportsOuterWindow) {
        List<SelectItem> items = new ArrayList<>(columns.size());
        for (String col : columns) {
            // S7f: detect window expressions
            if (WINDOW_PATTERN.matcher(col).find()) {
                if (!supportsOuterWindow) {
                    throw new ComposeCompileException(
                            ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                            ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                            "Outer window is not supported: '"
                                    + col + "'. supportsOuterWindow=false.");
                }
                WindowSelectSpec wSpec = WindowSelectParser.parse(col);
                validateWindowSelect(wSpec, schema);
                items.add(new SelectItem(col, false, null, null,
                        wSpec.outputAlias(), false, wSpec));
                continue;
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

    // ---- S7f window validation ----

    private static void validateWindowSelect(
            WindowSelectSpec wSpec, OutputSchema schema) {
        // Validate input column
        if (wSpec.inputColumn() != null && !"*".equals(wSpec.inputColumn())) {
            if (!schema.contains(wSpec.inputColumn())) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window input column '" + wSpec.inputColumn()
                                + "' does not exist. Available: "
                                + schema.nameSet());
            }
            ColumnSpec inputSpec = schema.get(wSpec.inputColumn());
            if (inputSpec != null && !isWindowable(inputSpec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_WINDOWABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window input column '" + wSpec.inputColumn()
                                + "' is not windowable. referencePolicy="
                                + inputSpec.referencePolicy());
            }
        }
        // Validate partition columns — must be groupable
        for (String partCol : wSpec.partitionBy()) {
            if (!schema.contains(partCol)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window PARTITION BY column '" + partCol
                                + "' does not exist. Available: "
                                + schema.nameSet());
            }
            ColumnSpec partSpec = schema.get(partCol);
            if (partSpec != null && !isGroupable(partSpec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window PARTITION BY column '" + partCol
                                + "' is not groupable. referencePolicy="
                                + partSpec.referencePolicy());
            }
        }
        // Validate order columns — must be orderable
        for (String ordClause : wSpec.orderBy()) {
            String baseName = WindowSelectParser.extractOrderByBase(ordClause);
            if (!schema.contains(baseName)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window ORDER BY column '" + baseName
                                + "' does not exist. Available: "
                                + schema.nameSet());
            }
            ColumnSpec ordSpec = schema.get(baseName);
            if (ordSpec != null && !isOrderable(ordSpec)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.RELATION_COLUMN_NOT_ORDERABLE,
                        ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                        "Window ORDER BY column '" + baseName
                                + "' is not orderable. referencePolicy="
                                + ordSpec.referencePolicy());
            }
        }
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

    /** S7f: column can be used as a window function input. */
    static boolean isWindowable(ColumnSpec col) {
        Set<String> rp = col.referencePolicy();
        return rp == null || rp.contains(ReferencePolicy.WINDOWABLE);
    }

    private static OutputSchema buildOutputSchema(
            List<SelectItem> selectItems, OutputSchema innerSchema) {
        List<ColumnSpec> outCols = new ArrayList<>(selectItems.size());
        for (SelectItem item : selectItems) {
            if (item.windowSpec() != null) {
                outCols.add(windowColumnSpec(item.windowSpec()));
            } else if (item.aggregate()) {
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

    /** S7f: build ColumnSpec for a window function output column. */
    private static ColumnSpec windowColumnSpec(WindowSelectSpec wSpec) {
        // Build expression string
        StringBuilder expr = new StringBuilder(wSpec.function());
        expr.append("(");
        if (wSpec.inputColumn() != null) {
            expr.append(wSpec.inputColumn());
        }
        expr.append(") OVER (...)");

        // Build valueMeaning
        StringBuilder meaning = new StringBuilder(wSpec.function().toLowerCase(Locale.ROOT));
        if (wSpec.inputColumn() != null && !"*".equals(wSpec.inputColumn())) {
            meaning.append(" of ").append(wSpec.inputColumn());
        }
        if (!wSpec.partitionBy().isEmpty()) {
            meaning.append(" partitioned by ").append(String.join(", ", wSpec.partitionBy()));
        }
        if (!wSpec.orderBy().isEmpty()) {
            meaning.append(" ordered by ").append(String.join(", ", wSpec.orderBy()));
        }
        if (wSpec.frame() != null) {
            meaning.append(" ").append(wSpec.frame().toLowerCase(Locale.ROOT));
        }

        // Build lineage: input + partition + order columns
        Set<String> lineage = new LinkedHashSet<>();
        if (wSpec.inputColumn() != null && !"*".equals(wSpec.inputColumn())) {
            lineage.add(wSpec.inputColumn());
        }
        lineage.addAll(wSpec.partitionBy());
        for (String ordClause : wSpec.orderBy()) {
            lineage.add(WindowSelectParser.extractOrderByBase(ordClause));
        }

        return ColumnSpec.builder()
                .name(wSpec.outputAlias())
                .expression(expr.toString())
                .hasExplicitAlias(true)
                .semanticKind(SemanticKind.WINDOW_CALC)
                .valueMeaning(meaning.toString())
                .lineage(lineage)
                .referencePolicy(Set.of(ReferencePolicy.READABLE, ReferencePolicy.ORDERABLE))
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
        // S7f: window expression rendering
        if (item.windowSpec() != null) {
            return renderWindowSelectItem(item.windowSpec(), alias, dialect);
        }
        if (!item.aggregate()) {
            return alias + "." + quoteIdentifier(item.outputName(), dialect);
        }
        String input = "*".equals(item.inputName())
                ? "*"
                : alias + "." + quoteIdentifier(item.inputName(), dialect);
        return item.function() + "(" + input + ") AS "
                + quoteIdentifier(item.outputName(), dialect);
    }

    /** S7f: render a window function expression as SQL. */
    private static String renderWindowSelectItem(
            WindowSelectSpec wSpec, String alias, String dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append(wSpec.function()).append("(");
        if (wSpec.inputColumn() != null) {
            if ("*".equals(wSpec.inputColumn())) {
                sb.append("*");
            } else {
                sb.append(alias).append(".")
                        .append(quoteIdentifier(wSpec.inputColumn(), dialect));
            }
        }
        sb.append(") OVER (");
        boolean hasClause = false;
        if (!wSpec.partitionBy().isEmpty()) {
            sb.append("PARTITION BY ");
            for (int i = 0; i < wSpec.partitionBy().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(alias).append(".")
                        .append(quoteIdentifier(wSpec.partitionBy().get(i), dialect));
            }
            hasClause = true;
        }
        if (!wSpec.orderBy().isEmpty()) {
            if (hasClause) sb.append(" ");
            sb.append("ORDER BY ");
            for (int i = 0; i < wSpec.orderBy().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(qualifyOrderByClause(
                        wSpec.orderBy().get(i), alias, dialect));
            }
            hasClause = true;
        }
        if (wSpec.frame() != null) {
            if (hasClause) sb.append(" ");
            sb.append(wSpec.frame());
        }
        sb.append(") AS ").append(quoteIdentifier(wSpec.outputAlias(), dialect));
        return sb.toString();
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
                    alias != null && !alias.isBlank(), null);
        }
        if (AGGREGATE_PATTERN.matcher(raw).find()) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                    ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                    "Unsupported outer aggregate expression: '" + raw
                            + "'. Use FUNC(column) or FUNC(column) AS alias.");
        }
        String outputName = extractColumnName(raw);
        return new SelectItem(raw, false, null, outputName, outputName, false, null);
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
            boolean hasExplicitAlias,
            WindowSelectSpec windowSpec) {
    }
}
