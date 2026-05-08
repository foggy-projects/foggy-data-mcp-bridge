package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.db.model.engine.compose.ComposeOrderByNormalizer;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.CteUnit;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.plan.*;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.*;
import com.foggyframework.dataset.db.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnAliasParts;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import com.foggyframework.dataset.db.model.engine.compose.schema.SchemaDerivation;
import com.foggyframework.dataset.db.model.engine.compose.security.ComposePlanAwarePermissionValidator;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.engine.compose.security.PlanFieldAccessContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan-tree → {@link ComposedSql} lowering (M6 · 6.2 / 6.3 / 6.5 / 6.6).
 *
 * <p>Owns the recursive {@code _compileAny} dispatcher that walks a
 * {@link QueryPlan} tree, emitting either a {@link CteUnit} (for
 * base/derived — embeddable) or a {@link ComposedSql} (for union/join —
 * self-contained).</p>
 *
 * <p>Four responsibilities live here because they share the recursion:
 * <ul>
 *   <li>6.2 · {@link UnionPlan} compilation — native {@code UNION} / {@code UNION ALL}</li>
 *   <li>6.3 · {@link JoinPlan} compilation — self-assembled CTE / subquery</li>
 *   <li>6.5 · dialect-driven CTE-vs-subquery fallback
 *       ({@link #dialectSupportsCte(String)})</li>
 *   <li>6.6 · MVP id-based dedup + MAX_PLAN_DEPTH DoS guard
 *       ({@link CompileState})</li>
 * </ul></p>
 *
 * <p>Dialect-driven output:
 * <ul>
 *   <li>{@code "mysql8" / "postgres" / "postgresql" / "sqlite"}
 *       → {@code useCte=true} ({@code WITH cte_0 AS (...) SELECT * FROM cte_0})</li>
 *   <li>{@code "mysql" / "mysql57"} (MySQL 5.7 without CTE) and
 *       {@code "mssql" / "sqlserver"} (CTE cannot be nested under derived
 *       tables in SQL Server) → {@code useCte=false} (inline subqueries).</li>
 * </ul>
 * Note: {@code "mysql"} alone is the conservative 5.7-compat default;
 * callers on MySQL 8+ must pass {@code "mysql8"} explicitly.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.compose_planner}.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposePlanner {

    private ComposePlanner() { /* utility */ }

    // ------------------------------------------------------------------
    // Dialect helpers (6.5)
    // ------------------------------------------------------------------

    /** dialect alias (lower-case) → whether it supports {@code WITH cte_N AS (...)}.
     *  {@code "mysql"} (bare) is conservative MySQL 5.7 — callers on MySQL 8+
     *  must pass {@code "mysql8"} to opt in to CTE emission. Unknown aliases
     *  are rejected in {@link #assertDialect(String)}. */
    private static final Map<String, Boolean> DIALECT_CTE_SUPPORT = Map.of(
            "mysql",       Boolean.FALSE,
            "mysql57",     Boolean.FALSE,
            "mysql8",      Boolean.TRUE,
            "postgres",    Boolean.TRUE,
            "postgresql",  Boolean.TRUE,
            "mssql",       Boolean.FALSE,
            "sqlserver",   Boolean.FALSE,
            "sqlite",      Boolean.TRUE);

    /** Return {@code true} when the dialect supports {@code WITH cte_N AS (...)}
     *  syntax. See {@link #DIALECT_CTE_SUPPORT} for the fixed mapping. */
    static boolean dialectSupportsCte(String dialect) {
        Boolean flag = DIALECT_CTE_SUPPORT.get(dialect.toLowerCase(Locale.ROOT));
        return flag != null && flag;
    }

    /** Fail-closed: reject unknown dialect strings early so downstream
     *  snapshot drift is caught here rather than at a live query. */
    private static void assertDialect(String dialect) {
        if (DIALECT_CTE_SUPPORT.containsKey(dialect.toLowerCase(Locale.ROOT))) {
            return;
        }
        throw new ComposeCompileException(
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                "Unknown dialect '" + dialect + "'; supported: "
                        + "mysql / mysql57 / mysql8 / postgres(postgresql) / "
                        + "mssql(sqlserver) / sqlite");
    }

    // ------------------------------------------------------------------
    // Identifier quoting (dialect-aware)
    // ------------------------------------------------------------------

    /** Quote a bare identifier for the target dialect.
     *  MySQL uses backticks, PostgreSQL/MSSQL/SQLite use double-quotes.
     *  Only applied to identifiers that NEED quoting (contain $, space, etc.). */
    static String quoteIdent(String ident, String dialect) {
        if (ident == null) return ident;
        if ("mysql".equals(dialect) || "mysql57".equals(dialect) || "mysql8".equals(dialect)) {
            return "`" + ident + "`";
        }
        // postgres, postgresql, mssql, sqlserver, sqlite → ANSI double-quote
        return "\"" + ident + "\"";
    }

    private static String quoteIdentIfNeeded(String ident, String dialect) {
        return needsQuoting(ident, dialect) ? quoteIdent(ident, dialect) : ident;
    }

    /** Return true if a column string needs identifier quoting.
     *  Simple bare identifiers containing special chars need quoting.
     *  Expressions (containing spaces, parens, AS keyword) are left as-is.
     *  For PostgreSQL/MSSQL/SQLite: also quotes mixed-case identifiers
     *  since these dialects fold unquoted identifiers to lowercase. */
    private static boolean needsQuoting(String col) {
        return needsQuoting(col, null);
    }

    private static boolean needsQuoting(String col, String dialect) {
        if (col == null || col.isEmpty()) return false;
        // Skip expressions: contain space, parens
        if (col.contains(" ") || col.contains("(") || col.contains(")")) return false;
        // Always quote if contains $
        if (col.contains("$")) return true;
        // For non-MySQL dialects, quote if contains uppercase letters
        // (PostgreSQL folds unquoted identifiers to lowercase)
        if (dialect != null && !dialect.startsWith("mysql")) {
            for (int i = 0; i < col.length(); i++) {
                if (Character.isUpperCase(col.charAt(i))) return true;
            }
        }
        return false;
    }

    /** Quote a column expression if it's a bare identifier needing quoting.
     *  Handles "colName" and "colName AS alias" forms.
     *  For complex expressions (CASE WHEN, arithmetic), scans for bare
     *  identifiers within the expression and quotes those individually. */
    static String quoteColumnExpr(String col, String dialect) {
        if (col == null) return col;
        // Check for " AS " — handle base and alias separately
        int asIdx = col.toUpperCase(Locale.ROOT).indexOf(" AS ");
        if (asIdx > 0) {
            String base = col.substring(0, asIdx).trim();
            String alias = col.substring(asIdx + 4).trim();
            String quotedBase = quoteExpressionTokens(rewriteSafeDivision(base), dialect);
            String quotedAlias = needsQuoting(alias, dialect) ? quoteIdent(alias, dialect) : alias;
            return quotedBase + " AS " + quotedAlias;
        }
        // No AS — simple column or expression
        return quoteExpressionTokens(rewriteSafeDivision(col), dialect);
    }

    /** Compile an AST node or raw string into SQL.
     *
     *  <p>Backward-compatible 2-arg form. Equivalent to calling
     *  {@link #compileExpression(Object, String, Map)} with an empty alias
     *  map — {@link PlanColumnRef} renders as a bare column name, matching
     *  the legacy single-base-plan behaviour.</p> */
    public static String compileExpression(Object expr, String dialect) {
        return compileExpression(expr, dialect, Collections.emptyMap());
    }

    /** Compile an AST node or raw string into SQL with plan-aware alias
     *  resolution.
     *
     *  <p><b>G10 PR3</b> · Adds a {@code planAliasMap} parameter so
     *  {@link PlanColumnRef} references inside a plan tree compile to
     *  {@code <alias>.<column>} when the producing plan has been registered
     *  with an alias by {@link #compileBase} / {@link #compileDerived}.</p>
     *
     *  <p>When {@link ComposeFeatureFlags#g10Enabled()} is {@code false}
     *  the alias map is ignored and {@link PlanColumnRef} falls back to
     *  bare-name rendering — preserving M4 byte-for-byte output for all
     *  existing single-base / non-ambiguous compile paths.</p>
     *
     *  @param expr           AST node, raw string, or supported plan-tree
     *                        column form
     *  @param dialect        target SQL dialect (lowercased by caller)
     *  @param planAliasMap   identity-keyed mapping from {@link QueryPlan}
     *                        producer to its CTE / subquery alias; never
     *                        null (use {@link Collections#emptyMap()} when
     *                        not applicable) */
    public static String compileExpression(Object expr, String dialect,
                                           Map<QueryPlan, String> planAliasMap) {
        if (expr == null) return "NULL";
        if (expr instanceof String s) {
            return quoteColumnExpr(s, dialect);
        }
        if (expr instanceof ProjectedColumn pc) {
            String compiledExpr = compileExpression(pc.expr(), dialect, planAliasMap);
            if (pc.caption() != null && !pc.caption().isEmpty()) {
                return compiledExpr + "$" + pc.caption() + " AS " + quoteIdentIfNeeded(pc.alias(), dialect);
            }
            return compiledExpr + " AS " + quoteIdentIfNeeded(pc.alias(), dialect);
        }
        if (expr instanceof ColumnExpr col) {
            return needsQuoting(col.name(), dialect) ? quoteIdent(col.name(), dialect) : col.name();
        }
        if (expr instanceof LiteralExpr lit) {
            if (lit.value() == null) return "NULL";
            if (lit.value() instanceof Number) return lit.value().toString();
            if (lit.value() instanceof Boolean) return lit.value().toString().toUpperCase(Locale.ROOT);
            return "'" + lit.value().toString().replace("'", "''") + "'";
        }
        if (expr instanceof BinaryExpr bin) {
            String left = compileExpression(bin.left(), dialect, planAliasMap);
            String right = compileExpression(bin.right(), dialect, planAliasMap);
            if ("/".equals(bin.op().trim())) {
                right = "NULLIF(" + right + ", 0)";
            }
            return "(" + left + " " + bin.op() + " " + right + ")";
        }
        if (expr instanceof CaseWhenExpr caseWhen) {
            StringBuilder sb = new StringBuilder("CASE");
            for (CaseWhenExpr.WhenThen wt : caseWhen.whens()) {
                sb.append(" WHEN ").append(compileExpression(wt.condition(), dialect, planAliasMap))
                  .append(" THEN ").append(compileExpression(wt.result(), dialect, planAliasMap));
            }
            if (caseWhen.elseExpr() != null) {
                sb.append(" ELSE ").append(compileExpression(caseWhen.elseExpr(), dialect, planAliasMap));
            }
            sb.append(" END");
            return sb.toString();
        }
        if (expr instanceof AggregateColumn agg) {
            // agg.toColumnExpr() returns raw SQL string like "SUM(col)". For full AST we might need a FuncExpr.
            // For now, it returns a string with parens, which quoteColumnExpr skips.
            return quoteColumnExpr(agg.toColumnExpr(), dialect);
        }
        if (expr instanceof WindowColumn win) {
            StringBuilder sb = new StringBuilder(win.func().toUpperCase(Locale.ROOT)).append("(");
            if (win.ref() != null) {
                sb.append(compileExpression(win.ref(), dialect, planAliasMap));
            }
            if (!win.args().isEmpty()) {
                if (win.ref() != null) sb.append(", ");
                for (int i = 0; i < win.args().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(win.args().get(i));
                }
            }
            sb.append(") OVER (");

            OverClause over = win.over();
            boolean hasPartition = over.getPartitionBy() != null && !over.getPartitionBy().isEmpty();
            boolean hasOrder = over.getOrderBy() != null && !over.getOrderBy().isEmpty();
            boolean hasFrame = over.getWindowFrame() != null;

            if (hasPartition) {
                sb.append("PARTITION BY ");
                for (int i = 0; i < over.getPartitionBy().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(quoteColumnExpr(over.getPartitionBy().get(i), dialect));
                }
            }

            if (hasOrder) {
                if (hasPartition) sb.append(" ");
                sb.append("ORDER BY ");
                for (int i = 0; i < over.getOrderBy().size(); i++) {
                    if (i > 0) sb.append(", ");
                    String orderCol = over.getOrderBy().get(i);
                    boolean isDesc = orderCol.startsWith("-");
                    String baseCol = isDesc ? orderCol.substring(1) : orderCol;
                    sb.append(quoteColumnExpr(baseCol, dialect));
                    if (isDesc) {
                        sb.append(" DESC");
                    } else {
                        sb.append(" ASC");
                    }
                }
            }

            if (hasFrame) {
                if (hasPartition || hasOrder) sb.append(" ");
                sb.append(over.getWindowFrame().toSql());
            }

            sb.append(")");
            return sb.toString();
        }
        if (expr instanceof PlanColumnRef ref) {
            return compilePlanColumnRef(ref, dialect, planAliasMap);
        }
        // Fallback for unknown objects
        return quoteColumnExpr(expr.toString(), dialect);
    }

    /**
     * <b>G10 PR3</b> · Render a {@link PlanColumnRef}, routing through
     * {@code planAliasMap} when the G10 flag is enabled and the plan is
     * registered. Falls back to bare-name rendering otherwise — preserves
     * M4 byte-for-byte for single-base / non-ambiguous paths.
     *
     * <p>Cost note: in production with G10 flag off, {@link #compileBase} /
     * {@link #compileDerived} skip the {@code planAliasMap.put(...)} so the
     * map stays empty for the whole compile. The {@code isEmpty()}
     * short-circuit therefore avoids any {@code g10Enabled()} read on the
     * legacy hot path — the flag is consulted only once per
     * {@link CompileState} construction.</p>
     */
    private static String compilePlanColumnRef(PlanColumnRef ref, String dialect,
                                                Map<QueryPlan, String> planAliasMap) {
        String column = quoteIdentIfNeeded(ref.name(), dialect);
        if (planAliasMap.isEmpty() || ref.plan() == null) {
            return column;
        }
        // Reached only when callers populated the map. Internal callers
        // populate only when the G10 flag is on; external direct callers
        // (e.g. unit tests) supply maps explicitly — the flag check enforces
        // the documented "G10 off ⇒ bare name even with non-empty map" contract.
        if (!ComposeFeatureFlags.g10Enabled()) {
            return column;
        }
        String alias = planAliasMap.get(ref.plan());
        return alias == null ? column : alias + "." + column;
    }

    /** Render a heterogeneous {@code .columns()} list (raw strings, {@link ColumnExpr},
     *  {@link ProjectedColumn}, {@link PlanColumnRef}) as plain SQL-ish strings.
     *  Package-visible so {@link PerBaseCompiler} can share the same conversion.
     *
     *  <p>For {@link PlanColumnRef} the bare column name is emitted — these
     *  strings name the <i>output columns</i> of the surrounding CTE/subquery
     *  so the wrapper {@code SELECT col1, col2 FROM cte_N} resolves them
     *  positionally; the alias-qualified {@code cte_N.col} form is rendered
     *  one layer in by {@code renderOuterSelect}. {@code toString()} would
     *  emit {@code "FieldRef(...)"} which is meaningless SQL.</p> */
    static List<String> extractStringCols(List<Object> cols) {
        List<String> out = new ArrayList<>(cols.size());
        for (Object c : cols) {
            if (c instanceof String s) out.add(s);
            else if (c instanceof ColumnExpr ce) out.add(ce.name());
            else if (c instanceof ProjectedColumn pc) out.add(pc.toColumnExpr());
            else if (c instanceof PlanColumnRef ref) out.add(ref.name());
            else out.add(c.toString());
        }
        return out;
    }

    private static List<String> sourceOutputColumnsForDerived(QueryPlan source, CteUnit innerUnit) {
        List<String> cols = innerUnit.getSelectColumns();
        if (cols != null && !cols.isEmpty()) {
            List<String> out = new ArrayList<>(cols.size());
            for (String col : cols) {
                out.add(unquoteIdentifier(extractOutputColName(col)));
            }
            return out;
        }
        if (source instanceof UnionPlan) {
            return SchemaDerivation.derive(source).names();
        }
        return declaredOutputColumnsForPlan(source);
    }

    private static List<String> derivedOutputColumns(DerivedQueryPlan plan, List<String> sourceColumns) {
        if (plan.columns().isEmpty()) {
            return sourceColumns == null ? Collections.emptyList() : new ArrayList<>(sourceColumns);
        }
        List<String> out = new ArrayList<>(plan.columns().size());
        for (String col : extractStringCols(plan.columns())) {
            out.add(unquoteIdentifier(extractOutputColName(col)));
        }
        return out;
    }

    private static List<String> declaredOutputColumnsForPlan(QueryPlan plan) {
        if (plan instanceof BaseModelPlan base) {
            List<String> out = new ArrayList<>(base.columns().size());
            for (String col : extractStringCols(base.columns())) {
                out.add(unquoteIdentifier(extractOutputColName(col)));
            }
            return out;
        }
        if (plan instanceof DerivedQueryPlan derived) {
            if (derived.columns().isEmpty()) {
                return declaredOutputColumnsForPlan(derived.source());
            }
            List<String> out = new ArrayList<>(derived.columns().size());
            for (String col : extractStringCols(derived.columns())) {
                out.add(unquoteIdentifier(extractOutputColName(col)));
            }
            return out;
        }
        if (plan instanceof JoinPlan join) {
            List<String> left = declaredOutputColumnsForPlan(join.left());
            List<String> right = declaredOutputColumnsForPlan(join.right());
            Set<String> seen = new LinkedHashSet<>(left);
            List<String> out = new ArrayList<>(left);
            for (String col : right) {
                if (seen.add(col)) {
                    out.add(col);
                }
            }
            return out;
        }
        if (plan instanceof UnionPlan) {
            return SchemaDerivation.derive(plan).names();
        }
        return Collections.emptyList();
    }

    private static Iterable<Object> iterSliceEntries(Iterable<?> slice) {
        if (slice == null) return Collections.emptyList();
        List<Object> out = new ArrayList<>();
        for (Object entry : slice) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            if (map.size() == 1) {
                Map.Entry<?, ?> e = map.entrySet().iterator().next();
                Object key = e.getKey();
                if (SYMMETRIC_LOGICAL_OPS.contains(key)) {
                    Object val = e.getValue();
                    if (val instanceof Iterable<?> it) {
                        for (Object sub : iterSliceEntries(it)) {
                            out.add(sub);
                        }
                    }
                    continue;
                }
                if ("$not".equals(key)) {
                    Object val = e.getValue();
                    if (val instanceof Map) {
                        for (Object sub : iterSliceEntries(Collections.singletonList(val))) {
                            out.add(sub);
                        }
                    } else if (val instanceof Iterable<?> it) {
                        for (Object sub : iterSliceEntries(it)) {
                            out.add(sub);
                        }
                    }
                    continue;
                }
            }
            out.add(entry);
        }
        return out;
    }

    private static void validateDerivedOutputRefs(DerivedQueryPlan plan, List<String> sourceColumns) {
        if (sourceColumns == null || sourceColumns.isEmpty()) {
            return;
        }
        Set<String> sourceNames = new LinkedHashSet<>(sourceColumns);
        for (Object col : plan.columns()) {
            for (String ident : columnInputRefs(col)) {
                String unquoted = unquoteIdentifier(ident);
                if (!sourceNames.contains(unquoted)) {
                    throw new ComposeSchemaException(
                            ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                            "derived query references unknown field '" + unquoted
                                    + "' not present in source output schema",
                            ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                            "DerivedQueryPlan",
                            unquoted);
                }
            }
        }
        Set<String> orderByNames = new LinkedHashSet<>(sourceNames);
        for (Object col : plan.columns()) {
            orderByNames.add(extractOutputColName(String.valueOf(col)));
        }
        for (String entry : plan.orderBy()) {
            String fieldName = unquoteIdentifier(ComposeOrderByNormalizer.parse(entry).field());
            if (!orderByNames.contains(fieldName)) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                        "derived query order_by references unknown field '" + fieldName
                                + "' not present in source output schema or this derived query's output columns "
                                + "(available: " + orderByNames + ")",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        "DerivedQueryPlan",
                        fieldName);
            }
        }
        for (Object entry : iterSliceEntries(plan.slice())) {
            String fieldName = sliceFieldName(entry);
            if (fieldName != null) {
                String fieldStr = fieldName.trim();
                if (DOTTED_REF.matcher(fieldStr).matches()) {
                    String aliasPart = fieldStr.split("\\.")[0];
                    throw new ComposeSchemaException(
                            ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                            "derived query slice references unknown field '" + aliasPart
                                    + "' not present in source output schema (available: " + sourceNames + ")",
                            ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                            "DerivedQueryPlan",
                            aliasPart);
                }
                for (String ident : extractUnquotedIdentifiers(fieldStr)) {
                    String unquoted = unquoteIdentifier(ident);
                    if (!sourceNames.contains(unquoted)) {
                        throw new ComposeSchemaException(
                                ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD,
                                "derived query slice references unknown field '" + unquoted
                                        + "' not present in source output schema (available: " + sourceNames + ")",
                                ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                                "DerivedQueryPlan",
                                unquoted);
                    }
                }
            }
        }
    }

    private static List<String> columnInputRefs(Object col) {
        if (col instanceof String s) {
            String expr = extractBaseColName(s);
            if ("*".equals(expr.trim())) {
                return Collections.emptyList();
            }
            return extractUnquotedIdentifiers(expr);
        }
        if (col instanceof PlanExpression expr) {
            List<String> out = new ArrayList<>();
            collectExpressionInputRefs(expr, out);
            return out;
        }
        return extractUnquotedIdentifiers(extractBaseColName(String.valueOf(col)));
    }

    private static void collectExpressionInputRefs(PlanExpression expr, List<String> out) {
        if (expr == null) {
            return;
        }
        if (expr instanceof ColumnExpr col) {
            out.add(col.name());
            return;
        }
        if (expr instanceof PlanColumnRef ref) {
            out.add(ref.name());
            return;
        }
        if (expr instanceof ProjectedColumn projected) {
            collectExpressionInputRefs(projected.expr(), out);
            return;
        }
        if (expr instanceof BinaryExpr binary) {
            collectExpressionInputRefs(binary.left(), out);
            collectExpressionInputRefs(binary.right(), out);
            return;
        }
        if (expr instanceof CaseWhenExpr caseWhen) {
            for (CaseWhenExpr.WhenThen whenThen : caseWhen.whens()) {
                collectExpressionInputRefs(whenThen.condition(), out);
                collectExpressionInputRefs(whenThen.result(), out);
            }
            collectExpressionInputRefs(caseWhen.elseExpr(), out);
            return;
        }
        if (expr instanceof AggregateColumn aggregate) {
            collectExpressionInputRefs(aggregate.ref(), out);
            return;
        }
        if (expr instanceof WindowColumn window) {
            collectExpressionInputRefs(window.ref(), out);
            collectWindowClauseRefs(window, out);
            return;
        }
        if (expr instanceof RawExpr raw) {
            out.addAll(extractUnquotedIdentifiers(raw.expression()));
            return;
        }
        if (expr instanceof LiteralExpr) {
            return;
        }
        out.addAll(extractUnquotedIdentifiers(expr.toString()));
    }

    private static void collectWindowClauseRefs(WindowColumn window, List<String> out) {
        OverClause over = window.over();
        if (over == null) {
            return;
        }
        if (over.getPartitionBy() != null) {
            for (String entry : over.getPartitionBy()) {
                out.addAll(extractUnquotedIdentifiers(entry));
            }
        }
        if (over.getOrderBy() != null) {
            for (String entry : over.getOrderBy()) {
                String normalized = entry != null && entry.startsWith("-")
                        ? entry.substring(1)
                        : entry;
                out.addAll(extractUnquotedIdentifiers(normalized));
            }
        }
    }

    /** SQL keywords that should NOT be quoted when found as bare tokens */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "CASE", "WHEN", "THEN", "ELSE", "END", "IS", "NULL", "OR", "AND", "NOT",
            "BETWEEN", "IN", "LIKE", "ESCAPE", "TRUE", "FALSE", "DISTINCT",
            "ASC", "DESC", "NULLS", "FIRST", "LAST", "SELECT", "FROM", "WHERE",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON",
            "AS", "EXISTS", "CAST", "COALESCE", "IFNULL", "ISNULL",
            "NULLIF",
            "OVER", "PARTITION", "ROWS", "RANGE", "PRECEDING", "FOLLOWING", "UNBOUNDED", "CURRENT", "ROW",
            "SUM", "COUNT", "AVG", "MAX", "MIN"
    );

    /** Token-boundary splitter used by {@link #quoteExpressionTokens(String, String)}.
     *  Compiled once — {@code quoteExpressionTokens} is invoked per column per
     *  query. */
    private static final Pattern EXPR_TOKEN_PATTERN =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*|[^A-Za-z_$]+");

    private static final Pattern DOTTED_REF =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*\\.[A-Za-z_][A-Za-z0-9_$]*$");

    /**
     * Slice DSL operators whose value is a list of sub-conditions joined with
     * the operator's own semantics (OR / AND).  Kept as a constant so that
     * {@link #iterSliceEntries} (validation path) and {@link #renderSliceEntry}
     * (SQL render path) share the same authoritative source — adding a new
     * operator only requires updating these two sets.
     */
    private static final Set<String> SYMMETRIC_LOGICAL_OPS = Set.of("$or", "$and");

    /** All logical grouping operators including negation. */
    private static final Set<String> ALL_LOGICAL_OPS = Set.of("$or", "$and", "$not");

    private static List<String> extractUnquotedIdentifiers(String expr) {
        if (expr == null || expr.isBlank()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        Matcher m = EXPR_TOKEN_PATTERN.matcher(expr);
        while (m.find()) {
            String token = m.group();
            if (token.isEmpty()) continue;
            char first = token.charAt(0);
            if (!(first == '_' || (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
                continue;
            }
            String upper = token.toUpperCase(Locale.ROOT);
            if (SQL_KEYWORDS.contains(upper) || isNumericLiteral(token)) {
                continue;
            }
            char prev = m.start() > 0 ? expr.charAt(m.start() - 1) : '\0';
            char next = m.end() < expr.length() ? expr.charAt(m.end()) : '\0';
            if (prev == '.' || next == '.') {
                continue;
            }
            if (nextNonWhitespace(expr, m.end()) == '(') {
                continue;
            }
            out.add(token);
        }
        return out;
    }

    private static char nextNonWhitespace(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return '\0';
    }

    /** Quote bare identifiers within a SQL expression.
     *  Splits the expression into tokens and quotes those that look like
     *  column identifiers (contain uppercase, $, or __) while preserving
     *  SQL keywords, numbers, operators, and punctuation. */
    private static String quoteExpressionTokens(String expr, String dialect) {
        if (expr == null || expr.isEmpty()) return expr;
        // Simple identifier — fast path
        if (needsQuoting(expr, dialect)) {
            return quoteIdent(expr, dialect);
        }
        // No dialect-specific quoting needed — return as-is
        if (dialect == null || dialect.startsWith("mysql")) {
            // MySQL is case-insensitive for column names; only $ needs quoting
            // and we already handled simple identifiers above
            if (!expr.contains("$")) return expr;
        }

        // Tokenize and quote — split on boundaries between word and non-word chars.
        StringBuilder sb = new StringBuilder(expr.length() + 16);
        Matcher m = EXPR_TOKEN_PATTERN.matcher(expr);
        while (m.find()) {
            String token = m.group();
            if (token.isEmpty()) continue;
            char first = token.charAt(0);
            if (Character.isLetter(first) || first == '_' || first == '$') {
                // It's a word token — check if it's a keyword or literal
                String upper = token.toUpperCase(Locale.ROOT);
                if (SQL_KEYWORDS.contains(upper) || isNumericLiteral(token)) {
                    sb.append(token);
                } else if (needsQuoting(token, dialect)) {
                    sb.append(quoteIdent(token, dialect));
                } else {
                    sb.append(token);
                }
            } else {
                // Operators, spaces, parens, etc. — pass through
                sb.append(token);
            }
        }
        return sb.toString();
    }

    private static String rewriteSafeDivision(String expr) {
        if (expr == null || expr.indexOf('/') < 0) return expr;
        StringBuilder out = new StringBuilder(expr.length() + 24);
        int i = 0;
        while (i < expr.length()) {
            char ch = expr.charAt(i);
            if (ch == '\'') {
                int end = consumeSingleQuoted(expr, i);
                out.append(expr, i, end);
                i = end;
                continue;
            }
            if (ch == '"') {
                int end = consumeDoubleQuoted(expr, i);
                out.append(expr, i, end);
                i = end;
                continue;
            }
            if (ch == '`') {
                int end = consumeBacktickQuoted(expr, i);
                out.append(expr, i, end);
                i = end;
                continue;
            }
            if (ch != '/') {
                out.append(ch);
                i++;
                continue;
            }
            int rhsStart = skipWs(expr, i + 1);
            if (startsFunctionCall(expr, rhsStart, "NULLIF")) {
                out.append(ch);
                i++;
                continue;
            }
            int rhsEnd = consumeDivisionDenominator(expr, rhsStart);
            if (rhsEnd <= rhsStart) {
                out.append(ch);
                i++;
                continue;
            }
            out.append("/ NULLIF(")
                    .append(expr.substring(rhsStart, rhsEnd).trim())
                    .append(", 0)");
            i = rhsEnd;
        }
        return out.toString();
    }

    private static int skipWs(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean startsFunctionCall(String text, int start, String name) {
        int end = start + name.length();
        if (end > text.length()) return false;
        if (!text.regionMatches(true, start, name, 0, name.length())) return false;
        if (end < text.length()) {
            char ch = text.charAt(end);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') return false;
        }
        int paren = skipWs(text, end);
        return paren < text.length() && text.charAt(paren) == '(';
    }

    private static int consumeDivisionDenominator(String text, int start) {
        if (start >= text.length()) return start;
        if (text.charAt(start) == '+' || text.charAt(start) == '-') {
            start = skipWs(text, start + 1);
        }
        if (start >= text.length()) return start;
        char ch = text.charAt(start);
        if (ch == '(') return consumeBalancedParentheses(text, start);
        if (ch == '\'') return consumeSingleQuoted(text, start);
        if (ch == '"') return consumeDoubleQuoted(text, start);
        if (ch == '`') return consumeBacktickQuoted(text, start);
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.')) {
                break;
            }
            i++;
        }
        int callStart = skipWs(text, i);
        if (callStart < text.length() && text.charAt(callStart) == '(') {
            return consumeBalancedParentheses(text, callStart);
        }
        return i;
    }

    private static int consumeBalancedParentheses(String text, int start) {
        int depth = 0;
        int i = start;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                i = consumeSingleQuoted(text, i);
                continue;
            }
            if (ch == '"') {
                i = consumeDoubleQuoted(text, i);
                continue;
            }
            if (ch == '`') {
                i = consumeBacktickQuoted(text, i);
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) return i + 1;
            }
            i++;
        }
        return text.length();
    }

    private static int consumeSingleQuoted(String text, int start) {
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                i += 2;
                continue;
            }
            if (text.charAt(i) == '\'') return i + 1;
            i++;
        }
        return text.length();
    }

    private static int consumeDoubleQuoted(String text, int start) {
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                i += 2;
                continue;
            }
            if (text.charAt(i) == '"') return i + 1;
            i++;
        }
        return text.length();
    }

    private static int consumeBacktickQuoted(String text, int start) {
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '`' && i + 1 < text.length() && text.charAt(i + 1) == '`') {
                i += 2;
                continue;
            }
            if (text.charAt(i) == '`') return i + 1;
            i++;
        }
        return text.length();
    }

    private static boolean isNumericLiteral(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '.') return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Compile state — carries dedup + alias counter through the recursion.
    // ------------------------------------------------------------------

    static final class CompileState {
        final Map<String, ModelBinding> bindings;
        final SemanticQueryServiceV3 semanticService;
        final String namespace;
        /** Dialect lower-cased once at construction; downstream comparisons
         *  ({@code "sqlite"} SQLite full-outer guard, CTE detection) read this
         *  without re-normalising. */
        final String dialect;
        /** Pre-resolved {@code useCte} for the session's dialect — avoids a
         *  {@link Map#get} + lowercasing at every compile branch. */
        final boolean useCte;
        /** <b>G10 PR3</b> · Snapshot of {@link ComposeFeatureFlags#g10Enabled()}
         *  taken once at construction so per-{@link PlanColumnRef} compile
         *  doesn't re-read the volatile override / system property / env var
         *  for every column in the plan tree. */
        final boolean g10Enabled;
        /** F-7 · Per-model datasource identity map. {@code null} when no
         *  provider was supplied and no explicit datasourceIds were passed;
         *  the cross-datasource check is skipped entirely in that case. */
        final Map<String, Optional<String>> datasourceIds;
        int aliasCounter = 0;

        /** MVP fast path — same plan reference compiled twice in one session
         *  (common for self-join / derived-over-same-plan) short-circuits
         *  here without re-running the structural {@link PlanHash#planHash}
         *  walk. {@link #hashCache} catches the different-instance-same-shape
         *  case. Both caches are populated on miss to keep them in sync. */
        final IdentityHashMap<QueryPlan, CteUnit> idCache = new IdentityHashMap<>();
        /** Full-mode dedup: structurally equal plan subtrees share a CteUnit. */
        final Map<List<Object>, CteUnit> hashCache = new HashMap<>();
        /** {@code (model, identityOf(binding))} → cached
         *  {@link SqlGenerationResult}. Skips re-running v1.3 governance for
         *  self-join / self-union cases. */
        final Map<String, SqlGenerationResult> governanceCache = new HashMap<>();
        /**
         * <b>G10 PR3</b> · Plan-tree → CTE/subquery alias mapping.
         *
         * <p>Populated by {@link #compileBase} / {@link #compileDerived} as
         * each plan node receives its {@code cte_N} alias via
         * {@link #nextAlias()}. Read by {@link #compileExpression(Object, String, Map)}
         * when emitting {@link PlanColumnRef} so a plan-qualified ref like
         * {@code {plan: orderHandle, field: "name"}} compiles to
         * {@code cte_0.name} instead of an ambiguous bare {@code name}.</p>
         *
         * <p>Identity-keyed (uses {@link IdentityHashMap}) so two plans with
         * structurally-equal payloads but distinct object identities don't
         * collide on the same alias — that would re-introduce ambiguity.
         * Empty when {@link ComposeFeatureFlags#g10Enabled()} is false; the
         * legacy emit path then falls back to bare-name rendering.</p>
         */
        final IdentityHashMap<QueryPlan, String> planAliasMap = new IdentityHashMap<>();
        /**
         * CTE Wrapping (9.2.0+): prerequisite CTE units that must be emitted
         * BEFORE the main CTE/subquery in the final SQL assembly.
         * Populated when a BaseModelPlan uses two-stage CTE wrapping for window CFs.
         */
        final List<CteUnit> prerequisiteCtes = new ArrayList<>();
        int currentDepth = 0;

        CompileState(Map<String, ModelBinding> bindings, SemanticQueryServiceV3 semanticService,
                     String namespace, String dialect,
                     Map<String, Optional<String>> datasourceIds) {
            this.bindings = bindings;
            this.semanticService = semanticService;
            this.namespace = namespace;
            this.dialect = dialect.toLowerCase(Locale.ROOT);
            this.useCte = dialectSupportsCte(this.dialect);
            this.g10Enabled = ComposeFeatureFlags.g10Enabled();
            this.datasourceIds = datasourceIds;
        }

        String nextAlias() {
            String alias = "cte_" + aliasCounter;
            aliasCounter += 1;
            return alias;
        }

        int enterDepth() {
            currentDepth += 1;
            return currentDepth;
        }

        void exitDepth() {
            currentDepth -= 1;
        }
    }

    // ------------------------------------------------------------------
    // Public entry used by ComposeSqlCompiler.
    // ------------------------------------------------------------------

    /**
     * Walk {@code plan} and return a {@link ComposedSql} using dialect-aware
     * CTE / subquery assembly. Binding coverage is checked inline by
     * {@link #compileBase(BaseModelPlan, CompileState)} on a single tree
     * pass.
     */
    static ComposedSql compileToComposedSql(
            QueryPlan plan,
            Map<String, ModelBinding> bindings,
            SemanticQueryServiceV3 semanticService,
            String namespace,
            String dialect,
            Map<String, Optional<String>> datasourceIds) {

        assertDialect(dialect);
        CompileState state = new CompileState(bindings, semanticService, namespace, dialect,
                datasourceIds);
        // G10 PR4 · plan-aware permission validation. Runs only when the
        // G10 flag is on; under flag=off the legacy single-QM
        // FieldAccessPermissionStep continues to enforce flat-whitelist
        // semantics at @Order(-25) without any change.
        if (state.g10Enabled) {
            runPlanAwarePermissionCheck(plan, bindings);
        }
        Object result = compileAny(plan, state);
        if (result instanceof ComposedSql composed) {
            return prependPrerequisiteCtes(composed, state.prerequisiteCtes, state.useCte, state.dialect);
        }
        // Top-level CteUnit (base / derived) — wrap as a single-unit CTE
        // or inline subquery for dialect-consistent output.
        CteUnit unit = (CteUnit) result;
        return wrapSingleUnit(unit, state.useCte, state.dialect, state.prerequisiteCtes);
    }

    /**
     * <b>G10 PR4</b> · Walk the plan tree to build a
     * {@link PlanFieldAccessContext}, derive the root plan's
     * {@link OutputSchema}, then run
     * {@link ComposePlanAwarePermissionValidator#validate}.
     *
     * <p>Pure pre-compile sub-step: no SQL is emitted here, no
     * compile-state side effects beyond the validator's own throws.
     * Failure surfaces as {@code ComposeSchemaException} (validation
     * codes carry phase {@code "permission-validate"}).</p>
     */
    private static void runPlanAwarePermissionCheck(
            QueryPlan plan, Map<String, ModelBinding> bindings) {
        PlanFieldAccessContext.Builder ctxBuilder = PlanFieldAccessContext.builder();
        Set<QueryPlan> visited = QueryPlan.identityPlanSet();
        collectPlanBindings(plan, bindings, ctxBuilder, visited);
        PlanFieldAccessContext planCtx = ctxBuilder.build();
        OutputSchema schema = SchemaDerivation.derive(plan);
        ComposePlanAwarePermissionValidator.validate(plan, schema, planCtx);
    }

    /** Tree walk: every {@link BaseModelPlan} pairs with its model's
     *  {@link ModelBinding}; visited-set prevents quadratic walks on
     *  shared plan subtrees. */
    private static void collectPlanBindings(QueryPlan plan,
                                              Map<String, ModelBinding> bindings,
                                              PlanFieldAccessContext.Builder ctxBuilder,
                                              Set<QueryPlan> visited) {
        if (plan == null || !visited.add(plan)) {
            return;
        }
        if (plan instanceof BaseModelPlan b) {
            ModelBinding binding = bindings.get(b.model());
            if (binding != null) {
                ctxBuilder.bind(b, binding);
            }
            return;
        }
        if (plan instanceof DerivedQueryPlan d) {
            collectPlanBindings(d.source(), bindings, ctxBuilder, visited);
            return;
        }
        if (plan instanceof JoinPlan j) {
            collectPlanBindings(j.left(), bindings, ctxBuilder, visited);
            collectPlanBindings(j.right(), bindings, ctxBuilder, visited);
            return;
        }
        if (plan instanceof UnionPlan u) {
            collectPlanBindings(u.left(), bindings, ctxBuilder, visited);
            collectPlanBindings(u.right(), bindings, ctxBuilder, visited);
        }
    }

    // ------------------------------------------------------------------
    // Dispatcher — recursion + depth guard + dedup
    // ------------------------------------------------------------------

    private static Object compileAny(QueryPlan plan, CompileState state) {
        int depth = state.enterDepth();
        try {
            if (depth > PlanHash.MAX_PLAN_DEPTH) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "Plan depth " + depth + " exceeds MAX_PLAN_DEPTH="
                                + PlanHash.MAX_PLAN_DEPTH
                                + "; nested derivations beyond this depth are rejected "
                                + "as a DoS safeguard (Compose Query typical depth is 3-5).");
            }

            // Union/Join emit ComposedSql — not reusable as embedded CTE,
            // so skip the CteUnit dedup cache entirely.
            if (plan instanceof UnionPlan) {
                return compileUnion((UnionPlan) plan, state);
            }
            if (plan instanceof JoinPlan) {
                return compileJoin((JoinPlan) plan, state);
            }

            CteUnit idHit = state.idCache.get(plan);
            if (idHit != null) {
                return idHit;
            }
            // id-cache miss → fall back to structural hash (Full-mode dedup).
            List<Object> structuralKey = PlanHash.planHash(plan);
            CteUnit hashHit = state.hashCache.get(structuralKey);
            if (hashHit != null) {
                state.idCache.put(plan, hashHit);
                return hashHit;
            }

            CteUnit unit;
            if (plan instanceof BaseModelPlan) {
                unit = compileBase((BaseModelPlan) plan, state);
            } else if (plan instanceof DerivedQueryPlan) {
                Object derivedResult = compileDerived((DerivedQueryPlan) plan, state);
                // When a DerivedQueryPlan wraps a JoinPlan, compileDerived
                // returns ComposedSql (terminal); skip CteUnit caching.
                if (derivedResult instanceof ComposedSql) {
                    return derivedResult;
                }
                unit = (CteUnit) derivedResult;
            } else {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "Unknown QueryPlan subclass "
                                + (plan == null ? "null" : plan.getClass().getSimpleName())
                                + "; extend ComposePlanner.compileAny if a new plan type was added");
            }

            state.idCache.put(plan, unit);
            state.hashCache.put(structuralKey, unit);
            return unit;
        } finally {
            state.exitDepth();
        }
    }

    // ------------------------------------------------------------------
    // Per-shape compilers
    // ------------------------------------------------------------------

    private static CteUnit compileBase(BaseModelPlan plan, CompileState state) {
        ModelBinding binding = state.bindings.get(plan.model());
        if (binding == null) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.MISSING_BINDING,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "No ModelBinding provided for BaseModelPlan.model='"
                            + plan.model()
                            + "'. Ensure AuthorityResolutionPipeline.resolve was called "
                            + "on the same plan tree and its result is passed via bindings=...");
        }
        String alias = state.nextAlias();
        registerPlanAlias(state, plan, alias);
        List<CteUnit> units = PerBaseCompiler.compileBaseModel(
                plan, binding, state.semanticService, state.namespace, alias, state.governanceCache);

        if (units.size() == 1) {
            return units.get(0); // legacy single-unit path
        }
        // Multi-unit (CTE wrapping): register prerequisite CTEs, return the final outer unit
        for (int i = 0; i < units.size() - 1; i++) {
            state.prerequisiteCtes.add(units.get(i));
        }
        return units.get(units.size() - 1);
    }

    /** Register {@code plan → alias} when the G10 flag is on. Skipping the
     *  put when flag is off keeps {@link CompileState#planAliasMap} empty so
     *  {@link #compilePlanColumnRef} short-circuits without consulting the
     *  flag again per column. */
    private static void registerPlanAlias(CompileState state, QueryPlan plan, String alias) {
        if (state.g10Enabled) {
            state.planAliasMap.put(plan, alias);
        }
    }

    private static Object compileDerived(DerivedQueryPlan plan, CompileState state) {
        Object inner = compileAny(plan.source(), state);
        boolean innerWasJoin = inner instanceof ComposedSql;
        CteUnit innerUnit;
        if (innerWasJoin) {
            innerUnit = wrapComposedAsUnit((ComposedSql) inner, state);
        } else {
            innerUnit = (CteUnit) inner;
        }
        validateDerivedSliceNotSameStageAlias(plan, innerUnit.getSelectColumns());

        List<String> sourceColumns = sourceOutputColumnsForDerived(plan.source(), innerUnit);
        validateDerivedOutputRefs(plan, sourceColumns);

        List<Object> outerParams = new ArrayList<>();
        String outerSql = renderOuterSelect(plan, innerUnit.getAlias(), innerUnit.getSql(), outerParams, state);

        List<Object> merged = new ArrayList<>();
        if (innerUnit.getParams() != null) merged.addAll(innerUnit.getParams());
        merged.addAll(outerParams);

        // When the inner plan was a join (ComposedSql), the DerivedQueryPlan
        // wrapping it already produces the final, self-contained SQL.
        // Return it as ComposedSql so the top-level compileToComposedSql
        // short-circuits at line 178 and does NOT call wrapSingleUnit again,
        // which would add a spurious second outer SELECT layer.
        if (innerWasJoin) {
            return new ComposedSql(outerSql, merged);
        }
        String derivedAlias = state.nextAlias();
        registerPlanAlias(state, plan, derivedAlias);
        return new CteUnit(
                derivedAlias,
                outerSql,
                merged,
                derivedOutputColumns(plan, sourceColumns));
    }

    private static ComposedSql compileJoin(JoinPlan plan, CompileState state) {
        checkCrossDatasource(plan, state, "join");

        if (plan.type() == JoinType.FULL && "sqlite".equals(state.dialect)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "JoinPlan(type='full') is not supported on SQLite dialect; "
                            + "use inner/left/right or switch dialects.");
        }

        Object leftObj = compileAny(plan.left(), state);
        Object rightObj = compileAny(plan.right(), state);
        CteUnit left = leftObj instanceof ComposedSql
                ? wrapComposedAsUnit((ComposedSql) leftObj, state)
                : (CteUnit) leftObj;
        CteUnit right = rightObj instanceof ComposedSql
                ? wrapComposedAsUnit((ComposedSql) rightObj, state)
                : (CteUnit) rightObj;

        String onCondition = buildOnCondition(plan.on(), left.getAlias(), right.getAlias(), state.dialect);

        // Dedup anchor units: if both sides resolved to the same CteUnit
        // (via id-cache / hash-cache — typical for self-join on the same
        // plan instance), emit just one CTE.
        List<CteUnit> anchors = new ArrayList<>();
        anchors.add(left);
        if (!right.getAlias().equals(left.getAlias())) {
            anchors.add(right);
        }

        return assembleJoinSql(anchors, plan.type().sqlKeyword(), onCondition,
                left.getAlias(), right.getAlias(), state.useCte, state.dialect);
    }

    private static ComposedSql compileUnion(UnionPlan plan, CompileState state) {
        checkCrossDatasource(plan, state, "union");

        Object left = compileAny(plan.left(), state);
        Object right = compileAny(plan.right(), state);

        String leftSql = extractSql(left);
        String rightSql = extractSql(right);
        List<Object> leftParams = extractParams(left);
        List<Object> rightParams = extractParams(right);

        String keyword = plan.all() ? "UNION ALL" : "UNION";
        String sql = leftSql + "\n" + keyword + "\n" + rightSql;
        List<Object> params = new ArrayList<>();
        params.addAll(leftParams);
        params.addAll(rightParams);
        return new ComposedSql(sql, params);
    }

    // ------------------------------------------------------------------
    // F-7 · Cross-datasource guard
    // ------------------------------------------------------------------

    /**
     * Check whether the leaf models of a union/join plan span multiple
     * datasources and reject if they do.
     *
     * <p>Called at the top of {@link #compileUnion} and
     * {@link #compileJoin}, <b>before</b> any SQL is emitted for the
     * children — mirrors the Python
     * {@code _check_cross_datasource(plan, state, kind)} guard.</p>
     *
     * <p>Skip semantics: when {@code state.datasourceIds} is
     * {@code null}, the compiler was invoked without a
     * {@code modelInfoProvider} and without explicit datasource IDs.
     * In that case the guard is a no-op (backward-compatible fast path).
     * Unknown datasources ({@code Optional.empty()}) are also skipped
     * — only non-empty, differing identities trigger rejection.</p>
     */
    private static void checkCrossDatasource(
            QueryPlan plan, CompileState state, String planKind) {
        if (state.datasourceIds == null) {
            return;
        }

        Set<String> dsIds = new TreeSet<>();
        Set<String> models = new TreeSet<>();

        for (BaseModelPlan base : plan.baseModelPlans()) {
            models.add(base.model());
            Optional<String> ds = state.datasourceIds.getOrDefault(
                    base.model(), Optional.empty());
            if (ds != null) {
                ds.ifPresent(id -> {
                    if (id != null && !id.isBlank()) {
                        dsIds.add(id);
                    }
                });
            }
        }

        if (dsIds.size() > 1) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.CROSS_DATASOURCE_REJECTED,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    planKind + " operands span " + dsIds.size()
                            + " datasources " + dsIds
                            + "; models involved: " + models
                            + ". Cross-datasource composition is not supported; "
                            + "all operands must belong to the same datasource.");
        }
    }

    // ------------------------------------------------------------------
    // SQL assembly helpers — M6 does NOT go through CteComposer.
    //
    // Why: the Java {@link CteComposer} / {@link JoinSpec} pair only
    // supports single-column equality joins (leftKey = rightKey) and
    // its {@code compose(List, List, boolean)} signature has no
    // top-level select_columns parameter (Python has both). Python's
    // {@code JoinSpec} carries a raw on_condition string and its
    // {@code CteComposer.compose} accepts {@code select_columns=...},
    // so the Python composer is strictly richer than ours.
    //
    // To avoid mutating the shared v1.3 / 8.2 CteComposer infra (which
    // is reused by other call sites), M6 keeps full control of the
    // 2-branch CTE / subquery assembly here. The cross-repo drift is
    // documented at the class level on both {@code CteComposer} and
    // {@code JoinSpec}; if you ever align them with Python, this whole
    // block can be deleted in favour of CteComposer.compose(...).
    // ------------------------------------------------------------------

    /** Wrap a single CteUnit as either a one-clause CTE or an inline
     *  subquery SELECT — matches Python CteComposer behaviour for the
     *  single-unit, zero-joinSpecs case.
     *
     *  <p>When prerequisite CTEs are present (from CTE-wrapped window CFs),
     *  they are emitted as sibling WITH clauses before the main unit.
     *  If we're in subquery mode but prerequisites exist, we force CTE mode
     *  because nested WITH inside FROM(...) is illegal on MSSQL/MySQL5.7.</p>
     */
    private static ComposedSql wrapSingleUnit(CteUnit unit, boolean useCte, String dialect,
                                               List<CteUnit> prerequisiteCtes) {
        List<Object> params = new ArrayList<>();
        boolean hasPrereqs = prerequisiteCtes != null && !prerequisiteCtes.isEmpty();

        if (hasPrereqs && !useCte) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_COMPILE,
                    "Dialect '" + dialect + "' does not support CTEs, which are required to hoist complex window functions or multi-stage subqueries.");
        }

        // Force CTE mode if prerequisites exist — subquery mode can't host WITH clauses
        boolean effectiveUseCte = useCte || hasPrereqs;

        StringBuilder sb = new StringBuilder();
        if (effectiveUseCte) {
            sb.append("WITH ");
            // Emit prerequisite CTEs first (e.g., cte_0_stage1 AS (...))
            int cteIndex = 0;
            if (hasPrereqs) {
                for (CteUnit prereq : prerequisiteCtes) {
                    if (cteIndex > 0) sb.append(",\n");
                    sb.append(prereq.getAlias()).append(" AS (").append(prereq.getSql()).append(")");
                    if (prereq.getParams() != null) params.addAll(prereq.getParams());
                    cteIndex++;
                }
            }
            // Emit main unit CTE
            if (cteIndex > 0) sb.append(",\n");
            sb.append(unit.getAlias()).append(" AS (").append(unit.getSql()).append(")\n");
            if (unit.getParams() != null) params.addAll(unit.getParams());

            sb.append(appendSelectColumns("SELECT ", unit.getAlias(), unit.getSelectColumns(), dialect))
                    .append("\n");
            sb.append("FROM ").append(unit.getAlias());
        } else {
            if (unit.getParams() != null) params.addAll(unit.getParams());
            sb.append(appendSelectColumns("SELECT ", "t0", unit.getSelectColumns(), dialect))
                    .append("\n");
            sb.append("FROM (").append(unit.getSql()).append(") AS t0");
        }
        return new ComposedSql(sb.toString(), params);
    }

    /**
     * Prepend prerequisite CTEs to an already-assembled ComposedSql.
     *
     * <p>When a ComposedSql (from join/union) already has its own WITH clause,
     * we insert the prerequisite CTEs at the beginning of the WITH block.
     * When it has no WITH clause, we prepend a new WITH block.</p>
     */
    private static ComposedSql prependPrerequisiteCtes(ComposedSql composed,
                                                        List<CteUnit> prerequisiteCtes,
                                                        boolean useCte,
                                                        String dialect) {
        if (prerequisiteCtes == null || prerequisiteCtes.isEmpty()) {
            return composed;
        }

        if (!useCte) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED,
                    ComposeCompileErrorCodes.PHASE_COMPILE,
                    "Dialect '" + dialect + "' does not support CTEs, which are required to hoist complex window functions or multi-stage subqueries.");
        }

        List<Object> params = new ArrayList<>();
        StringBuilder prereqBlock = new StringBuilder();
        for (int i = 0; i < prerequisiteCtes.size(); i++) {
            if (i > 0) prereqBlock.append(",\n");
            CteUnit prereq = prerequisiteCtes.get(i);
            prereqBlock.append(prereq.getAlias()).append(" AS (").append(prereq.getSql()).append(")");
            if (prereq.getParams() != null) params.addAll(prereq.getParams());
        }

        String sql = composed.getSql();
        if (composed.getParams() != null) params.addAll(composed.getParams());

        // Check if the composed SQL already starts with WITH
        String trimmed = sql.stripLeading();
        if (trimmed.regionMatches(true, 0, "WITH ", 0, 5)) {
            // Insert prerequisite CTEs after "WITH "
            int withIdx = sql.indexOf(trimmed);
            String afterWith = sql.substring(withIdx + 5); // everything after "WITH "
            String newSql = sql.substring(0, withIdx) + "WITH " + prereqBlock + ",\n" + afterWith;
            return new ComposedSql(newSql, params);
        } else {
            // Wrap with a new WITH block — wrap the existing SQL in a subquery
            String newSql = "WITH " + prereqBlock + "\n" + sql;
            return new ComposedSql(newSql, params);
        }
    }

    /** Assemble join SQL for anchors + on-condition. Two anchors per join
     *  is the normal case; a single anchor (self-join after dedup) degrades
     *  to a single-CTE select with the on-condition as a where-clause
     *  over the shared alias. */
    private static ComposedSql assembleJoinSql(
            List<CteUnit> anchors,
            String joinKeyword,
            String onCondition,
            String leftAlias,
            String rightAlias,
            boolean useCte,
            String dialect) {

        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // Build explicit column list to avoid duplicate column names
        // from both sides of the join (e.g. the join key appears in both).
        CteUnit left = anchors.get(0);
        CteUnit right = anchors.size() > 1 ? anchors.get(1) : null;

        if (useCte) {
            sb.append("WITH ");
            for (int i = 0; i < anchors.size(); i++) {
                if (i > 0) sb.append(",\n");
                CteUnit u = anchors.get(i);
                sb.append(u.getAlias()).append(" AS (").append(u.getSql()).append(")");
                if (u.getParams() != null) params.addAll(u.getParams());
            }
            sb.append("\n");
            sb.append(buildJoinSelectClause(left, right, leftAlias, rightAlias, dialect));
            sb.append("\nFROM ").append(leftAlias);
            if (anchors.size() > 1) {
                sb.append("\n").append(joinKeyword).append(" JOIN ").append(rightAlias)
                        .append(" ON ").append(onCondition);
            } else {
                sb.append("\nWHERE ").append(onCondition);
            }
        } else {
            // subquery mode — rewrite aliases to t0/t1 and inline SQLs
            Map<String, String> rename = new HashMap<>();
            for (int i = 0; i < anchors.size(); i++) {
                rename.put(anchors.get(i).getAlias(), "t" + i);
            }
            String renamedOn = rewriteAliases(onCondition, rename);
            String newLeftAlias = rename.get(leftAlias);
            String newRightAlias = right != null ? rename.get(rightAlias) : null;

            sb.append(buildJoinSelectClause(left, right, newLeftAlias, newRightAlias, dialect));
            sb.append("\nFROM (").append(left.getSql()).append(") AS ")
                    .append(newLeftAlias);
            if (left.getParams() != null) params.addAll(left.getParams());

            if (anchors.size() > 1) {
                sb.append("\n").append(joinKeyword).append(" JOIN (")
                        .append(right.getSql()).append(") AS ")
                        .append(newRightAlias)
                        .append(" ON ").append(renamedOn);
                if (right.getParams() != null) params.addAll(right.getParams());
            } else {
                sb.append("\nWHERE ").append(renamedOn);
            }
        }
        return new ComposedSql(sb.toString(), params);
    }

    /**
     * Build a SELECT clause for a join that avoids duplicate column names.
     * Takes all columns from the left side, then adds right-side columns
     * that aren't already present (by base column name, ignoring aliases).
     * Falls back to {@code SELECT *} when selectColumns are unavailable.
     */
    private static String buildJoinSelectClause(CteUnit left, CteUnit right,
                                                 String leftAlias, String rightAlias,
                                                 String dialect) {
        List<String> leftCols = left.getSelectColumns();
        List<String> rightCols = right != null ? right.getSelectColumns() : null;

        // Fall back to SELECT * when column info is unavailable
        if (leftCols == null || leftCols.isEmpty()) {
            return "SELECT *";
        }

        // Collect left output column names (the alias after " AS ", or bare name)
        Set<String> leftOutputNames = new LinkedHashSet<>();
        for (String col : leftCols) {
            leftOutputNames.add(unquoteIdentifier(extractOutputColName(col)));
        }

        StringBuilder select = new StringBuilder("SELECT ");
        // All left columns, qualified with left alias
        for (int i = 0; i < leftCols.size(); i++) {
            if (i > 0) select.append(", ");
            select.append(outputColumnRef(leftAlias, leftCols.get(i), dialect));
        }

        // Right columns, excluding those whose output name matches a left column
        // e.g. right has bare "salesDate$month" → output name is "salesDate$month" → skip (dupe)
        //      right has "unitPrice AS unitPrice__prior" → output name is "unitPrice__prior" → include
        if (rightCols != null && !rightCols.isEmpty()) {
            for (String col : rightCols) {
                String outputName = unquoteIdentifier(extractOutputColName(col));
                if (!leftOutputNames.contains(outputName)) {
                    // The right subquery has already applied aliases, so its
                    // output columns are the alias names. Reference by output name.
                    select.append(", ").append(rightAlias).append(".").append(quoteColumnExpr(outputName, dialect));
                }
            }
        }
        return select.toString();
    }

    /** Extract the base column name — the part before " AS " if present. */
    private static String extractBaseColName(String col) {
        int asIdx = col.toUpperCase(Locale.ROOT).indexOf(" AS ");
        if (asIdx > 0) {
            return col.substring(0, asIdx).trim();
        }
        return col.trim();
    }

    /** Extract the output column name — the alias after " AS " if present,
     *  or the full column name if no alias. This is the name the column will
     *  have in the result set. */
    private static String extractOutputColName(String col) {
        int asIdx = col.toUpperCase(Locale.ROOT).indexOf(" AS ");
        if (asIdx > 0) {
            return col.substring(asIdx + 4).trim();
        }
        return col.trim();
    }

    private static String unquoteIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return identifier;
        }
        String trimmed = identifier.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    static boolean isSimpleOutputColumn(String col) {
        return col != null
                && !col.contains(" ")
                && !col.contains("(")
                && !col.contains(")");
    }

    private static String outputColumnRef(String alias, String col, String dialect) {
        String outputName = unquoteIdentifier(extractOutputColName(col));
        if (isSimpleOutputColumn(outputName)) {
            return alias + "." + quoteColumnExpr(outputName, dialect);
        }
        return quoteColumnExpr(col, dialect);
    }

    private static String rewriteAliases(String sql, Map<String, String> rename) {
        String out = sql;
        for (Map.Entry<String, String> e : rename.entrySet()) {
            out = out.replace(e.getKey() + ".", e.getValue() + ".");
        }
        return out;
    }

    private static String appendSelectColumns(String prefix, String alias, List<String> cols, String dialect) {
        if (cols == null || cols.isEmpty()) {
            return prefix + alias + ".*";
        }
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(outputColumnRef(alias, cols.get(i), dialect));
        }
        return sb.toString();
    }

    private static String buildOnCondition(List<JoinOn> onList, String leftAlias, String rightAlias, String dialect) {
        List<String> frags = new ArrayList<>(onList.size());
        for (JoinOn o : onList) {
            String left = needsQuoting(o.left(), dialect) ? quoteIdent(o.left(), dialect) : o.left();
            String right = needsQuoting(o.right(), dialect) ? quoteIdent(o.right(), dialect) : o.right();
            frags.add(leftAlias + "." + left + " " + o.op() + " "
                    + rightAlias + "." + right);
        }
        return String.join(" AND ", frags);
    }

    private static String extractSql(Object compiled) {
        if (compiled instanceof CteUnit) return ((CteUnit) compiled).getSql();
        if (compiled instanceof ComposedSql) return ((ComposedSql) compiled).getSql();
        throw new ComposeCompileException(
                ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                ComposeCompileErrorCodes.PHASE_COMPILE,
                "Unexpected compile result type: "
                        + (compiled == null ? "null" : compiled.getClass().getSimpleName()));
    }

    private static List<Object> extractParams(Object compiled) {
        List<Object> raw = null;
        if (compiled instanceof CteUnit) raw = ((CteUnit) compiled).getParams();
        if (compiled instanceof ComposedSql) raw = ((ComposedSql) compiled).getParams();
        return raw == null ? Collections.emptyList() : raw;
    }

    private static CteUnit wrapComposedAsUnit(ComposedSql composed, CompileState state) {
        List<Object> params = composed.getParams() == null
                ? new ArrayList<>()
                : new ArrayList<>(composed.getParams());
        return new CteUnit(state.nextAlias(), composed.getSql(), params, null);
    }

    // ------------------------------------------------------------------
    // Outer-select rendering (derived chain)
    // ------------------------------------------------------------------

    /**
     * Render the {@code SELECT ... FROM (innerSql) AS innerAlias [WHERE ... GROUP BY ... ORDER BY ...]}
     * outer wrapper for a {@link DerivedQueryPlan}.
     *
     * <p>Reads {@code state.dialect} for identifier quoting and (G10 PR3)
     * {@code state.planAliasMap} so {@link PlanColumnRef} columns inside
     * {@code plan.columns()} compile to alias-qualified SQL.</p>
     */
    private static String renderOuterSelect(
            DerivedQueryPlan plan, String innerAlias, String innerSql,
            List<Object> outerParams, CompileState state) {

        String dialect = state.dialect;
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (plan.distinct()) sb.append("DISTINCT ");
        if (plan.columns().isEmpty()) {
            sb.append("*");
        } else {
            List<String> quotedCols = new ArrayList<>(plan.columns().size());
            for (Object colObj : plan.columns()) {
                quotedCols.add(compileExpression(colObj, dialect, state.planAliasMap));
            }
            sb.append(String.join(", ", quotedCols));
        }
        sb.append("\nFROM (").append(innerSql).append(") AS ").append(innerAlias);

        if (!plan.slice().isEmpty()) {
            List<String> frags = new ArrayList<>();
            for (Object entry : plan.slice()) {
                String subSql = renderSliceEntry(entry, outerParams, dialect, innerAlias);
                if (subSql != null && !subSql.isEmpty()) {
                    frags.add(subSql);
                }
            }
            if (!frags.isEmpty()) {
                sb.append("\nWHERE ").append(String.join(" AND ", frags));
            }
        }

        if (!plan.groupBy().isEmpty()) {
            List<String> quotedGroupBy = new ArrayList<>(plan.groupBy().size());
            for (String g : plan.groupBy()) {
                quotedGroupBy.add(quoteColumnExpr(unquoteIdentifier(g), dialect));
            }
            sb.append("\nGROUP BY ").append(String.join(", ", quotedGroupBy));
        }

        if (!plan.orderBy().isEmpty()) {
            List<String> orderFrags = new ArrayList<>(plan.orderBy().size());
            for (String entry : plan.orderBy()) {
                orderFrags.add(renderOrderEntry(entry, dialect));
            }
            sb.append("\nORDER BY ").append(String.join(", ", orderFrags));
        }

        if (plan.limit() != null) {
            if (plan.start() != null) {
                sb.append("\nLIMIT ").append(plan.limit()).append(" OFFSET ").append(plan.start());
            } else {
                sb.append("\nLIMIT ").append(plan.limit());
            }
        } else if (plan.start() != null) {
            sb.append("\nOFFSET ").append(plan.start());
        }

        return sb.toString();
    }

    private static String renderSliceEntry(
            Object entry, List<Object> outerParams, String dialect, String innerAlias) {
        if (entry instanceof Map<?, ?> map && map.size() == 1) {
            Map.Entry<?, ?> e = map.entrySet().iterator().next();
            Object key = e.getKey();
            if (SYMMETRIC_LOGICAL_OPS.contains(key)) {
                Object val = e.getValue();
                if (!(val instanceof Iterable<?>)) {
                    throw new ComposeCompileException(
                            ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                            ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                            "Logical operator '" + key + "' requires a list.");
                }
                List<String> subFrags = new ArrayList<>();
                for (Object sub : (Iterable<?>) val) {
                    String subSql = renderSliceEntry(sub, outerParams, dialect, innerAlias);
                    if (subSql != null && !subSql.isEmpty()) {
                        subFrags.add(subSql);
                    }
                }
                if (subFrags.isEmpty()) return "";
                if (subFrags.size() == 1) return subFrags.get(0);
                String op = "$or".equals(key) ? " OR " : " AND ";
                return "(" + String.join(op, subFrags) + ")";
            }
            if ("$not".equals(key)) {
                Object val = e.getValue();
                Iterable<?> it = val instanceof Iterable<?> ? (Iterable<?>) val : Collections.singletonList(val);
                List<String> subFrags = new ArrayList<>();
                for (Object sub : it) {
                    String subSql = renderSliceEntry(sub, outerParams, dialect, innerAlias);
                    if (subSql != null && !subSql.isEmpty()) {
                        subFrags.add(subSql);
                    }
                }
                if (subFrags.isEmpty()) return "";
                return "NOT (" + String.join(" AND ", subFrags) + ")";
            }
        }
        SliceShape s = SliceShape.parse(entry);
        String fieldSql = renderDerivedSliceField(innerAlias, s.field, dialect);
        String op = normalizeSliceOp(s.op);
        if (s.hasFieldReferenceValue()) {
            String ref = s.fieldReferenceValue();
            return fieldSql + " " + op + " "
                    + renderDerivedSliceField(innerAlias, ref, dialect);
        }
        if (s.value instanceof Map<?, ?>) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "Derived slice object values are unsupported except {'$field': '<output_field>'}; "
                            + "raw expression objects such as {'$expr': ...} are not a public DSL feature.");
        }
        if ("IS NULL".equals(op) || "IS NOT NULL".equals(op)) {
            return fieldSql + " " + op;
        }
        if ("IN".equals(op) || "NOT IN".equals(op)) {
            if (!(s.value instanceof Collection<?> values) || values.isEmpty()) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "Derived slice operator '" + op + "' requires a non-empty collection value.");
            }
            if (values.stream().anyMatch(Map.class::isInstance)) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "Derived slice operator '" + op + "' does not support object values; "
                                + "raw expression objects such as {'$expr': ...} are not a public DSL feature.");
            }
            outerParams.addAll(values);
            return fieldSql + " " + op + " ("
                    + String.join(", ", Collections.nCopies(values.size(), "?"))
                    + ")";
        }
        outerParams.add(s.value);
        return fieldSql + " " + op + " ?";
    }

    private static String normalizeSliceOp(Object op) {
        return op == null
                ? "="
                : op.toString().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String renderDerivedSliceField(String innerAlias, String field, String dialect) {
        String unquoted = unquoteIdentifier(field);
        if (isSimpleOutputColumn(unquoted)) {
            return innerAlias + "." + quoteColumnExpr(unquoted, dialect);
        }
        return quoteColumnExpr(unquoted, dialect);
    }

    private static void validateDerivedSliceNotSameStageAlias(
            DerivedQueryPlan plan, List<String> sourceColumns) {
        if (plan.slice().isEmpty()) {
            return;
        }
        Set<String> sourceNames = new LinkedHashSet<>();
        if (sourceColumns != null) {
            for (String col : sourceColumns) {
                sourceNames.add(unquoteIdentifier(extractOutputColName(col)));
            }
        }
        Set<String> currentStageAliases = new LinkedHashSet<>();
        for (Object col : plan.columns()) {
            ColumnAliasParts parts = AliasExtractor.extract(extractStringCols(List.of(col)).get(0));
            if (parts.hasAlias() && !sourceNames.contains(parts.outputName())) {
                currentStageAliases.add(parts.outputName());
            }
        }
        if (currentStageAliases.isEmpty()) {
            return;
        }
        for (Object entry : iterSliceEntries(plan.slice())) {
            String fieldName = sliceFieldName(entry);
            if (fieldName != null && currentStageAliases.contains(fieldName)) {
                throw new ComposeSchemaException(
                        ComposeSchemaErrorCodes.DERIVED_QUERY_SAME_STAGE_ALIAS,
                        "field '" + fieldName + "' is created by this derived "
                                + "query's SELECT and cannot be filtered in the same "
                                + "stage; add another .query({ slice: "
                                + "[{field: '" + fieldName + "', ...}] }) stage",
                        ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE,
                        "DerivedQueryPlan",
                        fieldName);
            }
        }
    }

    private static String sliceFieldName(Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            return null;
        }
        Object canonical = map.get("field");
        if (canonical instanceof String s) {
            return s;
        }
        if (canonical != null || map.size() != 1) {
            return null;
        }
        Object key = map.keySet().iterator().next();
        return key instanceof String s ? s : null;
    }

    private static String renderOrderEntry(String entry, String dialect) {
        ComposeOrderByNormalizer.OrderSpec spec = ComposeOrderByNormalizer.parse(entry);
        return quoteColumnExpr(unquoteIdentifier(spec.field()), dialect) + " " + spec.dirUpper();
    }
}
