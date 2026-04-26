package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.CteUnit;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *   <li>{@code "mysql8" / "postgres" / "postgresql" / "mssql" / "sqlserver" / "sqlite"}
 *       → {@code useCte=true} ({@code WITH cte_0 AS (...) SELECT * FROM cte_0})</li>
 *   <li>{@code "mysql" / "mysql57"} (MySQL 5.7 without CTE) → {@code useCte=false}
 *       (inline subqueries).</li>
 * </ul>
 * Note: {@code "mysql"} alone is the conservative 5.7-compat default;
 * callers on MySQL 8+ must pass {@code "mysql8"} explicitly.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.compose_planner}.</p>
 *
 * @since 8.2.0.beta
 */
final class ComposePlanner {

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
            "mssql",       Boolean.TRUE,
            "sqlserver",   Boolean.TRUE,
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
            String quotedBase = quoteExpressionTokens(base, dialect);
            String quotedAlias = needsQuoting(alias, dialect) ? quoteIdent(alias, dialect) : alias;
            return quotedBase + " AS " + quotedAlias;
        }
        // No AS — simple column or expression
        return quoteExpressionTokens(col, dialect);
    }

    /** SQL keywords that should NOT be quoted when found as bare tokens */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "CASE", "WHEN", "THEN", "ELSE", "END", "IS", "NULL", "OR", "AND", "NOT",
            "BETWEEN", "IN", "LIKE", "ESCAPE", "TRUE", "FALSE", "DISTINCT",
            "ASC", "DESC", "NULLS", "FIRST", "LAST", "SELECT", "FROM", "WHERE",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON",
            "AS", "EXISTS", "CAST", "COALESCE", "IFNULL", "ISNULL"
    );

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

        // Tokenize and quote — use regex to split on boundaries between
        // word chars and non-word chars
        StringBuilder sb = new StringBuilder(expr.length() + 16);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "[A-Za-z_$][A-Za-z0-9_$]*|[^A-Za-z_$]+").matcher(expr);
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
        int currentDepth = 0;

        CompileState(Map<String, ModelBinding> bindings, SemanticQueryServiceV3 semanticService,
                     String namespace, String dialect) {
            this.bindings = bindings;
            this.semanticService = semanticService;
            this.namespace = namespace;
            this.dialect = dialect.toLowerCase(Locale.ROOT);
            this.useCte = dialectSupportsCte(this.dialect);
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
            String dialect) {

        assertDialect(dialect);
        CompileState state = new CompileState(bindings, semanticService, namespace, dialect);
        Object result = compileAny(plan, state);
        if (result instanceof ComposedSql) {
            return (ComposedSql) result;
        }
        // Top-level CteUnit (base / derived) — wrap as a single-unit CTE
        // or inline subquery for dialect-consistent output.
        CteUnit unit = (CteUnit) result;
        return wrapSingleUnit(unit, state.useCte, state.dialect);
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
        return PerBaseCompiler.compileBaseModel(
                plan, binding, state.semanticService, state.namespace, alias, state.governanceCache);
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

        List<Object> outerParams = new ArrayList<>();
        String outerSql = renderOuterSelect(plan, innerUnit.getAlias(), innerUnit.getSql(), outerParams, state.dialect);

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
        return new CteUnit(
                state.nextAlias(),
                outerSql,
                merged,
                new ArrayList<>(plan.columns()));
    }

    private static ComposedSql compileJoin(JoinPlan plan, CompileState state) {
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
        Object left = compileAny(plan.left(), state);
        Object right = compileAny(plan.right(), state);

        String leftSql = extractSql(left);
        String rightSql = extractSql(right);
        List<Object> leftParams = extractParams(left);
        List<Object> rightParams = extractParams(right);

        String keyword = plan.all() ? "UNION ALL" : "UNION";
        String sql = "(" + leftSql + ")\n" + keyword + "\n(" + rightSql + ")";
        List<Object> params = new ArrayList<>();
        params.addAll(leftParams);
        params.addAll(rightParams);
        return new ComposedSql(sql, params);
    }

    // ------------------------------------------------------------------
    // SQL assembly helpers — M6 does not go through CteComposer because
    // the Java CteComposer / JoinSpec pair only supports single-column
    // equality joins (leftKey = rightKey). Python's JoinSpec carries a
    // raw on_condition string so its CteComposer is richer; to avoid
    // touching shared v1.3 / 8.2 infra we own the 2-branch assembly here.
    // ------------------------------------------------------------------

    /** Wrap a single CteUnit as either a one-clause CTE or an inline
     *  subquery SELECT — matches Python CteComposer behaviour for the
     *  single-unit, zero-joinSpecs case. */
    private static ComposedSql wrapSingleUnit(CteUnit unit, boolean useCte, String dialect) {
        List<Object> params = new ArrayList<>();
        if (unit.getParams() != null) params.addAll(unit.getParams());
        StringBuilder sb = new StringBuilder();
        if (useCte) {
            sb.append("WITH ").append(unit.getAlias()).append(" AS (")
                    .append(unit.getSql()).append(")\n");
            sb.append(appendSelectColumns("SELECT ", unit.getAlias(), unit.getSelectColumns(), dialect))
                    .append("\n");
            sb.append("FROM ").append(unit.getAlias());
        } else {
            sb.append(appendSelectColumns("SELECT ", "t0", unit.getSelectColumns(), dialect))
                    .append("\n");
            sb.append("FROM (").append(unit.getSql()).append(") AS t0");
        }
        return new ComposedSql(sb.toString(), params);
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
            leftOutputNames.add(extractOutputColName(col));
        }

        StringBuilder select = new StringBuilder("SELECT ");
        // All left columns, qualified with left alias
        for (int i = 0; i < leftCols.size(); i++) {
            if (i > 0) select.append(", ");
            String colExpr = leftCols.get(i);
            select.append(leftAlias).append(".").append(quoteColumnExpr(colExpr, dialect));
        }

        // Right columns, excluding those whose output name matches a left column
        // e.g. right has bare "salesDate$month" → output name is "salesDate$month" → skip (dupe)
        //      right has "unitPrice AS unitPrice__prior" → output name is "unitPrice__prior" → include
        if (rightCols != null && !rightCols.isEmpty()) {
            for (String col : rightCols) {
                String outputName = extractOutputColName(col);
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
            String col = cols.get(i);
            if (col.contains(" ") || col.contains("(")) {
                sb.append(col);
            } else {
                sb.append(alias).append(".").append(quoteColumnExpr(col, dialect));
            }
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

    private static String renderOuterSelect(
            DerivedQueryPlan plan, String innerAlias, String innerSql, List<Object> outerParams, String dialect) {

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (plan.distinct()) sb.append("DISTINCT ");
        if (plan.columns().isEmpty()) {
            sb.append("*");
        } else {
            List<String> quotedCols = new ArrayList<>(plan.columns().size());
            for (String col : plan.columns()) {
                quotedCols.add(quoteColumnExpr(col, dialect));
            }
            sb.append(String.join(", ", quotedCols));
        }
        sb.append("\nFROM (").append(innerSql).append(") AS ").append(innerAlias);

        if (!plan.slice().isEmpty()) {
            List<String> frags = new ArrayList<>();
            for (Object entry : plan.slice()) {
                frags.add(renderSliceEntry(entry, outerParams));
            }
            sb.append("\nWHERE ").append(String.join(" AND ", frags));
        }

        if (!plan.groupBy().isEmpty()) {
            List<String> quotedGroupBy = new ArrayList<>(plan.groupBy().size());
            for (String g : plan.groupBy()) {
                quotedGroupBy.add(needsQuoting(g, dialect) ? quoteIdent(g, dialect) : g);
            }
            sb.append("\nGROUP BY ").append(String.join(", ", quotedGroupBy));
        }

        if (!plan.orderBy().isEmpty()) {
            List<String> orderFrags = new ArrayList<>(plan.orderBy().size());
            for (String entry : plan.orderBy()) {
                orderFrags.add(renderOrderEntry(entry));
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

    private static String renderSliceEntry(Object entry, List<Object> outerParams) {
        SliceShape s = SliceShape.parse(entry);
        outerParams.add(s.value);
        return s.field + " " + s.op + " ?";
    }

    private static String renderOrderEntry(String entry) {
        if (entry.contains(":")) {
            String[] parts = entry.split(":", 2);
            String name = parts[0].trim();
            String dir = parts[1].trim().toUpperCase(Locale.ROOT);
            if (!Arrays.asList("ASC", "DESC").contains(dir)) {
                dir = "ASC";
            }
            return name + " " + dir;
        }
        return entry;
    }
}
