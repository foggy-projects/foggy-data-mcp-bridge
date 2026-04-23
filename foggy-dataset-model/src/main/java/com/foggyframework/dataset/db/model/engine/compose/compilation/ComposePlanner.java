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

    private static final Set<String> MYSQL_LEGACY_ALIASES = Set.of("mysql", "mysql57");
    private static final Set<String> MYSQL_MODERN_ALIASES = Set.of("mysql8");
    private static final Set<String> CTE_DIALECTS = Set.of(
            "postgres", "postgresql", "mssql", "sqlserver", "sqlite");

    /** Return {@code true} when the dialect supports {@code WITH cte_N AS (...)}
     *  syntax. {@code "mysql"} (bare) is treated as conservative MySQL 5.7 —
     *  callers on MySQL 8+ must pass {@code "mysql8"} to opt in to CTE
     *  emission. */
    static boolean dialectSupportsCte(String dialect) {
        String n = dialect.toLowerCase(Locale.ROOT);
        if (MYSQL_LEGACY_ALIASES.contains(n)) return false;
        if (MYSQL_MODERN_ALIASES.contains(n)) return true;
        return CTE_DIALECTS.contains(n);
    }

    /** Fail-closed: reject unknown dialect strings early so downstream
     *  snapshot drift is caught here rather than at a live query. */
    private static void assertDialect(String dialect) {
        String n = dialect.toLowerCase(Locale.ROOT);
        if (MYSQL_LEGACY_ALIASES.contains(n) || MYSQL_MODERN_ALIASES.contains(n)
                || CTE_DIALECTS.contains(n)) {
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
    // Compile state — carries dedup + alias counter through the recursion.
    // ------------------------------------------------------------------

    static final class CompileState {
        final Map<String, ModelBinding> bindings;
        final SemanticQueryServiceV3 semanticService;
        final String namespace;
        final String dialect;
        int aliasCounter = 0;

        /** MVP dedup: same {@code id(plan)} hit → reuse the CteUnit directly. */
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
            this.dialect = dialect;
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
        return wrapSingleUnit(unit, dialectSupportsCte(dialect));
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

            if (state.idCache.containsKey(plan)) {
                return state.idCache.get(plan);
            }
            List<Object> structuralKey = PlanHash.planHash(plan);
            if (state.hashCache.containsKey(structuralKey)) {
                CteUnit cached = state.hashCache.get(structuralKey);
                state.idCache.put(plan, cached);
                return cached;
            }

            CteUnit unit;
            if (plan instanceof BaseModelPlan) {
                unit = compileBase((BaseModelPlan) plan, state);
            } else if (plan instanceof DerivedQueryPlan) {
                unit = compileDerived((DerivedQueryPlan) plan, state);
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

    private static CteUnit compileDerived(DerivedQueryPlan plan, CompileState state) {
        Object inner = compileAny(plan.source(), state);
        CteUnit innerUnit;
        if (inner instanceof ComposedSql) {
            innerUnit = wrapComposedAsUnit((ComposedSql) inner, state);
        } else {
            innerUnit = (CteUnit) inner;
        }

        List<Object> outerParams = new ArrayList<>();
        String outerSql = renderOuterSelect(plan, innerUnit.getAlias(), innerUnit.getSql(), outerParams);

        List<Object> merged = new ArrayList<>();
        if (innerUnit.getParams() != null) merged.addAll(innerUnit.getParams());
        merged.addAll(outerParams);

        return new CteUnit(
                state.nextAlias(),
                outerSql,
                merged,
                new ArrayList<>(plan.columns()));
    }

    private static ComposedSql compileJoin(JoinPlan plan, CompileState state) {
        if (plan.type() == JoinType.FULL
                && "sqlite".equals(state.dialect.toLowerCase(Locale.ROOT))) {
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

        String onCondition = buildOnCondition(plan.on(), left.getAlias(), right.getAlias());
        String joinKeyword = sqlJoinKeyword(plan.type());
        boolean useCte = dialectSupportsCte(state.dialect);

        // Dedup anchor units: if both sides resolved to the same CteUnit
        // (via id-cache / hash-cache — typical for self-join on the same
        // plan instance), emit just one CTE.
        List<CteUnit> anchors = new ArrayList<>();
        anchors.add(left);
        if (!right.getAlias().equals(left.getAlias())) {
            anchors.add(right);
        }

        return assembleJoinSql(anchors, joinKeyword, onCondition,
                left.getAlias(), right.getAlias(), useCte);
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
    private static ComposedSql wrapSingleUnit(CteUnit unit, boolean useCte) {
        List<Object> params = new ArrayList<>();
        if (unit.getParams() != null) params.addAll(unit.getParams());
        StringBuilder sb = new StringBuilder();
        if (useCte) {
            sb.append("WITH ").append(unit.getAlias()).append(" AS (")
                    .append(unit.getSql()).append(")\n");
            sb.append(appendSelectColumns("SELECT ", unit.getAlias(), unit.getSelectColumns()))
                    .append("\n");
            sb.append("FROM ").append(unit.getAlias());
        } else {
            sb.append(appendSelectColumns("SELECT ", "t0", unit.getSelectColumns()))
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
            boolean useCte) {

        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        if (useCte) {
            sb.append("WITH ");
            for (int i = 0; i < anchors.size(); i++) {
                if (i > 0) sb.append(",\n");
                CteUnit u = anchors.get(i);
                sb.append(u.getAlias()).append(" AS (").append(u.getSql()).append(")");
                if (u.getParams() != null) params.addAll(u.getParams());
            }
            sb.append("\nSELECT *\nFROM ").append(leftAlias);
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

            sb.append("SELECT *\nFROM (").append(anchors.get(0).getSql()).append(") AS ")
                    .append(rename.get(anchors.get(0).getAlias()));
            if (anchors.get(0).getParams() != null) params.addAll(anchors.get(0).getParams());

            if (anchors.size() > 1) {
                sb.append("\n").append(joinKeyword).append(" JOIN (")
                        .append(anchors.get(1).getSql()).append(") AS ")
                        .append(rename.get(anchors.get(1).getAlias()))
                        .append(" ON ").append(renamedOn);
                if (anchors.get(1).getParams() != null) params.addAll(anchors.get(1).getParams());
            } else {
                sb.append("\nWHERE ").append(renamedOn);
            }
        }
        return new ComposedSql(sb.toString(), params);
    }

    private static String rewriteAliases(String sql, Map<String, String> rename) {
        String out = sql;
        for (Map.Entry<String, String> e : rename.entrySet()) {
            out = out.replace(e.getKey() + ".", e.getValue() + ".");
        }
        return out;
    }

    private static String appendSelectColumns(String prefix, String alias, List<String> cols) {
        if (cols == null || cols.isEmpty()) {
            return prefix + alias + ".*";
        }
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(alias).append(".").append(cols.get(i));
        }
        return sb.toString();
    }

    private static String buildOnCondition(List<JoinOn> onList, String leftAlias, String rightAlias) {
        List<String> frags = new ArrayList<>(onList.size());
        for (JoinOn o : onList) {
            frags.add(leftAlias + "." + o.left() + " " + o.op() + " "
                    + rightAlias + "." + o.right());
        }
        return String.join(" AND ", frags);
    }

    private static String sqlJoinKeyword(JoinType type) {
        switch (type) {
            case INNER: return "INNER";
            case LEFT:  return "LEFT";
            case RIGHT: return "RIGHT";
            case FULL:  return "FULL OUTER";
            default:    return "INNER";
        }
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
            DerivedQueryPlan plan, String innerAlias, String innerSql, List<Object> outerParams) {

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (plan.distinct()) sb.append("DISTINCT ");
        sb.append(plan.columns().isEmpty() ? "*" : String.join(", ", plan.columns()));
        sb.append("\nFROM (").append(innerSql).append(") AS ").append(innerAlias);

        if (!plan.slice().isEmpty()) {
            List<String> frags = new ArrayList<>();
            for (Object entry : plan.slice()) {
                frags.add(renderSliceEntry(entry, outerParams));
            }
            sb.append("\nWHERE ").append(String.join(" AND ", frags));
        }

        if (!plan.groupBy().isEmpty()) {
            sb.append("\nGROUP BY ").append(String.join(", ", plan.groupBy()));
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

    @SuppressWarnings("unchecked")
    private static String renderSliceEntry(Object entry, List<Object> outerParams) {
        if (!(entry instanceof Map)) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "Derived slice entries must be Map, got "
                            + (entry == null ? "null" : entry.getClass().getSimpleName()));
        }
        Map<String, Object> map = (Map<String, Object>) entry;
        String field;
        String op;
        Object value;
        if (map.containsKey("field")) {
            field = String.valueOf(map.get("field"));
            op = map.get("op") == null ? "=" : String.valueOf(map.get("op"));
            value = map.get("value");
        } else {
            if (map.size() != 1) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                        ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                        "Derived slice shortcut must have exactly 1 key, got " + map.keySet());
            }
            Map.Entry<String, Object> e = map.entrySet().iterator().next();
            field = e.getKey();
            op = "=";
            value = e.getValue();
        }
        outerParams.add(value);
        return field + " " + op + " ?";
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
