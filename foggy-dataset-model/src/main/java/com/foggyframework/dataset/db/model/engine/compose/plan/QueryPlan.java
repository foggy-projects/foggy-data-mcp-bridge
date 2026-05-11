package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.db.model.engine.compose.runtime.PlanExecution;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

import java.util.*;

/**
 * Base type for every Compose Query plan node (8.2.0.beta M2).
 *
 * <p>Concrete subclasses ({@link BaseModelPlan}, {@link DerivedQueryPlan},
 * {@link UnionPlan}, {@link JoinPlan}) are {@code final} and built through
 * explicit Builders. They are pure immutable descriptors — no execution
 * state, no datasource handle, no authority binding. Execution lands in M7
 * (the script runner threading a {@link ComposeQueryContext} through
 * {@link #execute(ComposeQueryContext)}); SQL rendering lands in M6.</p>
 *
 * <p><b>Layer-C whitelist (M9 sandbox scaffold §Layer C)</b>. The public
 * surface of any {@code QueryPlan} subclass is exactly these five methods:
 * <ul>
 *   <li>{@link #query(QueryOptions)}</li>
 *   <li>{@link #union(QueryPlan)} / {@link #union(QueryPlan, boolean)}</li>
 *   <li>{@link #join(QueryPlan, JoinType, List)} and string/raw overloads</li>
 *   <li>{@link #execute()} / {@link #execute(ComposeQueryContext)}</li>
 *   <li>{@link #toSql()}</li>
 * </ul>
 * Iteration, raw-SQL escape hatches, and memory filters must NOT appear.
 * {@link #baseModelPlans()} is package-private so the M5 authority-resolver
 * pipeline can walk the tree without exposing it to the sandbox.</p>
 *
 * <p>Cross-repo invariant: shape mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.QueryPlan}. When a rule
 * differs, Python is the source of truth — see execution prompt.</p>
 *
 * @since 8.2.0.beta
 */
public abstract class QueryPlan implements PropertyHolder, PropertyFunction {

    private final Set<String> composeSourceAliases = new LinkedHashSet<>();

    /** Package-private constructor — subclasses are restricted to this
     *  package so the Layer-C whitelist cannot be bypassed by external
     *  subclassing. */
    QueryPlan() {
    }

    final void addComposeSourceAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        composeSourceAliases.add(alias);
    }

    final Set<String> composeSourceAliases() {
        return Collections.unmodifiableSet(composeSourceAliases);
    }

    // ------------------------------------------------------------------
    // PropertyHolder: dynamic field access (sales.partnerId → PlanColumnRef)
    // ------------------------------------------------------------------

    @Override
    public Object getProperty(String name) {
        // Don't intercept Java-internal or builder properties
        if (name == null || name.startsWith("_") || name.equals("class")) {
            return PropertyHolder.NO_MATCH;
        }
        return new PlanColumnRef(this, name);
    }

    // ------------------------------------------------------------------
    // PropertyFunction: method dispatch (sales.select(...), sales.leftJoin(...))
    // ------------------------------------------------------------------

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "where" -> {
                @SuppressWarnings("unchecked")
                List<Object> slice = args != null && args.length > 0 ? (List<Object>) args[0] : List.of();
                yield fluentWhere(slice);
            }
            case "groupBy" -> fluentGroupBy(args);
            case "select" -> fluentSelect(args);
            case "orderBy" -> fluentOrderBy(args);
            case "limit" -> fluentLimit(args);
            case "offset" -> fluentOffset(args);
            case "query" -> query(queryOptionsFromArgs(args));
            case "leftJoin" -> leftJoin((QueryPlan) args[0]);
            case "innerJoin" -> innerJoin((QueryPlan) args[0]);
            case "rightJoin" -> rightJoin((QueryPlan) args[0]);
            case "fullJoin" -> fullJoin((QueryPlan) args[0]);
            // Legacy union/join methods (still used)
            case "union" -> {
                if (args.length > 1) {
                    // union(other, {all: true}) — second arg may be a Map with "all" key
                    boolean all = false;
                    if (args[1] instanceof Boolean b) {
                        all = b;
                    } else if (args[1] instanceof Map<?, ?> m) {
                        all = Boolean.TRUE.equals(m.get("all"));
                    }
                    yield union((QueryPlan) args[0], all);
                }
                yield union((QueryPlan) args[0]);
            }
            case "join" -> {
                @SuppressWarnings("unchecked")
                List<?> on = (List<?>) args[2];
                yield join((QueryPlan) args[0], (String) args[1], on);
            }
            case "execute" -> execute();
            case "toSql" -> toSql();
            case "and" -> {
                if (this instanceof JoinPlan jp) {
                    yield jp.and((PlanColumnRef) args[0], (PlanColumnRef) args[1]);
                }
                throw new IllegalArgumentException(".and() is only available on JoinPlan");
            }
            default -> throw new IllegalArgumentException(
                    "QueryPlan does not support method: " + methodName);
        };
    }

    // ------------------------------------------------------------------
    // Fluent builder methods (8.2.0.beta OO API)
    // ------------------------------------------------------------------

    // ---- Window Function Builders (Global/Plan level) ----

    public WindowColumnBuilder rowNumber() { return new WindowColumnBuilder("ROW_NUMBER", null, java.util.List.of()); }
    public WindowColumnBuilder rank() { return new WindowColumnBuilder("RANK", null, java.util.List.of()); }
    public WindowColumnBuilder denseRank() { return new WindowColumnBuilder("DENSE_RANK", null, java.util.List.of()); }

    /** Filter on current stage output columns. */
    public final DerivedQueryPlan fluentWhere(List<Object> slice) {
        return DerivedQueryPlan.builder()
                .source(this)
                .slice(slice)
                .build();
    }

    /** Group by field references. */
    public final DerivedQueryPlan fluentGroupBy(Object... fields) {
        List<String> resolved = new ArrayList<>();
        if (fields != null) {
            for (Object f : fields) {
                if (f instanceof PlanColumnRef ref) {
                    resolved.add(ref.name());
                } else if (f instanceof ProjectedColumn pc) {
                    resolved.add(pc.alias());
                } else if (f instanceof String s) {
                    resolved.add(s);
                } else {
                    resolved.add(String.valueOf(f));
                }
            }
        }
        return DerivedQueryPlan.builder()
                .source(this)
                .groupBy(resolved)
                .build();
    }

    /**
     * Project columns and create a new relation stage.
     * Supports PlanColumnRef, AggregateColumn, WindowColumn, ProjectedColumn, and String.
     * Throws on duplicate aliases (fail-fast disambiguation).
     */
    public final DerivedQueryPlan fluentSelect(Object... args) {
        List<Object> columns = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();
        if (args != null) {
            for (Object arg : args) {
                String expr;
                String alias;
                if (arg instanceof ProjectedColumn pc) {
                    alias = pc.alias();
                } else if (arg instanceof PlanColumnRef ref) {
                    alias = ref.name();
                } else if (arg instanceof AggregateColumn agg) {
                    alias = agg.toColumnExpr();
                } else if (arg instanceof WindowColumn win) {
                    alias = win.toColumnExpr();
                } else if (arg instanceof String s) {
                    String upper = s.toUpperCase();
                    int asIdx = upper.lastIndexOf(" AS ");
                    alias = asIdx >= 0 ? s.substring(asIdx + 4).trim() : s;
                } else {
                    throw new IllegalArgumentException(
                            "Invalid select argument type: " + (arg == null ? "null" : arg.getClass().getSimpleName()));
                }
                if (seenAliases.contains(alias)) {
                    throw new IllegalArgumentException(
                            "Column '" + alias + "' is ambiguous. Please use .as('new_name') to disambiguate.");
                }
                seenAliases.add(alias);
                columns.add(arg); // Add the object directly
            }
        }
        return DerivedQueryPlan.builder()
                .source(this)
                .columns(columns)
                .build();
    }

    /** Order by string aliases. */
    public final DerivedQueryPlan fluentOrderBy(Object... fields) {
        List<String> resolved = new ArrayList<>();
        if (fields != null) {
            for (Object f : fields) {
                resolved.add(String.valueOf(f));
            }
        }
        return DerivedQueryPlan.builder()
                .source(this)
                .orderBy(resolved)
                .build();
    }

    /** Limit result rows. */
    public final DerivedQueryPlan fluentLimit(Object... args) {
        int n = args != null && args.length > 0 ? ((Number) args[0]).intValue() : 0;
        return DerivedQueryPlan.builder()
                .source(this)
                .limit(n)
                .build();
    }

    /** Offset (skip) rows. */
    public final DerivedQueryPlan fluentOffset(Object... args) {
        int n = args != null && args.length > 0 ? ((Number) args[0]).intValue() : 0;
        return DerivedQueryPlan.builder()
                .source(this)
                .start(n)
                .build();
    }

    // ---- Fluent Join factories ----

    public final ComposeJoinBuilder leftJoin(QueryPlan other) {
        return new ComposeJoinBuilder(this, other, JoinType.LEFT);
    }

    public final ComposeJoinBuilder innerJoin(QueryPlan other) {
        return new ComposeJoinBuilder(this, other, JoinType.INNER);
    }

    public final ComposeJoinBuilder rightJoin(QueryPlan other) {
        return new ComposeJoinBuilder(this, other, JoinType.RIGHT);
    }

    public final ComposeJoinBuilder fullJoin(QueryPlan other) {
        return new ComposeJoinBuilder(this, other, JoinType.FULL);
    }

    // ------------------------------------------------------------------
    // Layer-C public surface — exactly 5 methods.
    // ------------------------------------------------------------------

    /**
     * Build a {@link DerivedQueryPlan} whose {@code source} is this plan.
     * Equivalent to {@code Dsl.from(FromOptions.builder().source(this).columns(...).build())}
     * — same validation rules apply.
     */
    public final DerivedQueryPlan query(QueryOptions opts) {
        if (opts == null) {
            throw new IllegalArgumentException(
                    "QueryPlan.query(opts): opts must not be null");
        }
        return DerivedQueryPlan.builder()
                .source(this)
                .columns(opts.columns())
                .slice(opts.slice())
                .groupBy(opts.groupBy())
                .orderBy(opts.orderBy())
                .limit(opts.limit())
                .start(opts.start())
                .distinct(opts.distinct())
                .build();
    }

    /**
     * Build a {@code UNION} (distinct) of this plan with {@code other}.
     * Use {@link #union(QueryPlan, boolean)} with {@code all=true} for
     * {@code UNION ALL}.
     */
    public final UnionPlan union(QueryPlan other) {
        return union(other, false);
    }

    /**
     * Build a union of this plan with {@code other}.
     *
     * <p>M2 enforces structural rules only: {@code other} must be a
     * {@link QueryPlan} instance. Column-count parity and cross-datasource
     * rejection are deferred to M4 (schema derivation) and M6 (compiler).</p>
     */
    public final UnionPlan union(QueryPlan other, boolean all) {
        requirePlan(other, "union.other");
        return UnionPlan.builder().left(this).right(other).all(all).build();
    }

    /**
     * Build a join of this plan with {@code other}. See
     * {@link #join(QueryPlan, JoinType, List)} for details.
     * String overload — case-insensitive, normalised via
     * {@link JoinType#fromString(String)}.
     */
    public final JoinPlan join(QueryPlan other, String type, List<?> on) {
        return join(other, JoinType.fromString(type), on);
    }

    /**
     * Build a join of this plan with {@code other}.
     *
     * <p>{@code on} entries may be {@link JoinOn} instances or
     * {@link Map}-shaped dicts ({@code {"left":..., "op":..., "right":...}}) —
     * the latter get coerced via {@link JoinOn#fromMap(Map)}. Empty
     * {@code on} is rejected; cross joins are NOT in M2 scope.</p>
     */
    public final JoinPlan join(QueryPlan other, JoinType type, List<?> on) {
        requirePlan(other, "join.other");
        if (type == null) {
            throw new IllegalArgumentException(
                    "join.type must not be null");
        }
        if (on == null || on.isEmpty()) {
            throw new IllegalArgumentException(
                    "JoinPlan.on must be non-empty; cross joins are not "
                            + "supported in 8.2.0.beta M2");
        }
        List<JoinOn> coerced = PlanQualifiedFieldResolver.normalizeJoinOn(
                this, other, coerceJoinOnList(on));
        return JoinPlan.builder()
                .left(this)
                .right(other)
                .type(type)
                .on(coerced)
                .build();
    }

    /**
     * Execute this plan via the ambient {@link
     * com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeBundle}.
     *
     * <p>M7 wiring: reads the bundle from
     * {@link com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeHolder#currentBundle()},
     * then delegates to
     * {@link com.foggyframework.dataset.db.model.engine.compose.runtime.PlanExecution#executePlan}.</p>
     *
     * @return rows as a list of column-name→value maps
     * @throws RuntimeException if no ambient bundle is set
     * @since 8.2.0.beta M7
     */
    public final List<Map<String, Object>> execute() {
        return execute(null);
    }

    /**
     * Execute this plan, optionally overriding the context from the
     * ambient bundle.
     *
     * @param explicitCtx optional override context; when null, the
     *                    bundle's ctx is used
     * @return rows as a list of column-name→value maps
     * @throws RuntimeException if no ambient bundle is set
     * @since 8.2.0.beta M7
     */
    public final List<Map<String, Object>> execute(ComposeQueryContext explicitCtx) {
        ComposeRuntimeBundle bundle = ComposeRuntimeHolder.currentBundle();
        if (bundle == null) {
            throw new RuntimeException(
                    "QueryPlan.execute requires an ambient ComposeRuntimeBundle; "
                            + "call from inside ScriptRuntime.runScript(), or wrap manually via "
                            + "ComposeRuntimeHolder.setBundle(...). Host misconfiguration "
                            + "(semanticService / dialect not bound) cannot be surfaced as "
                            + "ComposeCompileException — that family is reserved for compile-phase failures.");
        }
        ComposeQueryContext effectiveCtx = explicitCtx != null ? explicitCtx : bundle.ctx();
        return PlanExecution.executePlan(
                this, effectiveCtx, bundle.semanticService(), bundle.dialect());
    }

    /**
     * Compile this plan to SQL via the ambient
     * {@link ComposeRuntimeBundle}.
     *
     * <p><b>Note:</b> even when overriding the context via
     * {@link #toSql(ComposeQueryContext)} or
     * {@link #toSql(ComposeQueryContext, String)}, an ambient
     * {@link ComposeRuntimeBundle} is still required to provide the
     * {@link SemanticQueryServiceV3} for SQL generation. Call from inside
     * {@code ScriptRuntime.runScript()}, or wrap manually via
     * {@code ComposeRuntimeHolder.setBundle(...)}.</p>
     *
     * @return compiled SQL + params
     * @throws RuntimeException if no ambient bundle or semanticService is set
     * @since 8.2.0.beta M7
     */
    public final ComposedSql toSql() {
        return toSql(null, null);
    }

    /**
     * Compile this plan to SQL with an explicit context override.
     *
     * <p>An ambient {@link ComposeRuntimeBundle} is still required
     * to provide the {@link SemanticQueryServiceV3}.</p>
     *
     * @param explicitCtx optional override context; when null, the
     *                    bundle's ctx is used
     * @return compiled SQL + params
     * @since 8.2.0.beta M7
     */
    public final ComposedSql toSql(ComposeQueryContext explicitCtx) {
        return toSql(explicitCtx, null);
    }

    /**
     * Compile this plan to SQL with explicit context and dialect overrides.
     *
     * <p>An ambient {@link ComposeRuntimeBundle} is still required
     * to provide the {@link SemanticQueryServiceV3}.</p>
     *
     * @param explicitCtx optional override context; when null, the
     *                    bundle's ctx is used
     * @param dialect     optional override dialect; when null, the
     *                    bundle's dialect is used
     * @return compiled SQL + params
     * @since 8.2.0.beta M7
     */
    public final ComposedSql toSql(
            ComposeQueryContext explicitCtx, String dialect) {
        ComposeRuntimeBundle bundle = ComposeRuntimeHolder.currentBundle();
        if (bundle == null && explicitCtx == null) {
            throw new RuntimeException(
                    "QueryPlan.toSql requires either an explicit ctx or an ambient ComposeRuntimeBundle");
        }
        ComposeQueryContext effectiveCtx = explicitCtx != null ? explicitCtx :
                bundle.ctx();
        SemanticQueryServiceV3 effectiveSvc =
                bundle != null ? bundle.semanticService() : null;
        String effectiveDialect = dialect != null ? dialect :
                (bundle != null ? bundle.dialect() : "mysql");
        if (effectiveSvc == null) {
            throw new RuntimeException(
                    "QueryPlan.toSql: semanticService unbound — an ambient "
                            + "ComposeRuntimeBundle is required even when explicitCtx is "
                            + "provided (call from inside ScriptRuntime.runScript, or wrap "
                            + "manually via ComposeRuntimeHolder.setBundle)");
        }
        return ComposeSqlCompiler.compilePlanToSql(this, effectiveCtx,
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(effectiveSvc)
                        .dialect(effectiveDialect)
                        .build());
    }

    // ------------------------------------------------------------------
    // Tree-walk helper — used by the M5 authority-resolver pipeline.
    // ------------------------------------------------------------------

    /**
     * Return the leaf {@link BaseModelPlan} nodes reachable from this node,
     * in left-to-right preorder.
     *
     * <p>Public so the M5 authority-resolver pipeline
     * ({@code compose.authority.BaseModelPlanCollector}) can walk the tree
     * from a sibling package. Layer-C enforcement for the JS sandbox is
     * handled at the reflective-allowlist layer in M9; Java package
     * visibility is not how we defend the sandbox boundary. Mirrors Python
     * {@code QueryPlan.base_model_plans()} which is also public.</p>
     */
    public abstract List<BaseModelPlan> baseModelPlans();

    /**
     * <b>G5 Phase 2 (F5)</b> · Return all plans visible from this plan node
     * for F5 plan-qualified column reference validation per G5 spec §5.1.
     *
     * <p>The returned set includes {@code this} plus every plan
     * transitively reachable through structural children:
     * <ul>
     *   <li>{@code BaseModelPlan} — leaf, returns {@code {this}}</li>
     *   <li>{@code DerivedQueryPlan} — {@code {this} ∪ source.collectVisiblePlans()}</li>
     *   <li>{@code JoinPlan} — {@code {this} ∪ left.collectVisiblePlans() ∪ right.collectVisiblePlans()}</li>
     *   <li>{@code UnionPlan} — same as join (both branches)</li>
     * </ul>
     *
     * <p><b>Identity-keyed</b> (G5 spec §5.1 warning): the returned set uses
     * object identity, NOT {@code equals}. Same model name referenced via
     * two distinct {@code dsl()} calls produces two distinct plan instances
     * that are NOT interchangeable. Implementations MUST use
     * {@link #identityPlanSet()} to construct the result.</p>
     *
     * @return identity-keyed {@code Set<QueryPlan>} containing {@code this}
     *         and all transitively-reachable plan nodes
     * @since 8.3.0.beta
     */
    public abstract Set<QueryPlan> collectVisiblePlans();

    // ------------------------------------------------------------------
    // Shared static helpers — package-private so the subclasses can reuse
    // them without duplicating validation logic.
    // ------------------------------------------------------------------

    static void requirePlan(Object value, String fieldName) {
        if (!(value instanceof QueryPlan)) {
            throw new IllegalArgumentException(
                    fieldName + " must be a QueryPlan instance, got: "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
        }
    }

    static void validateColumns(List<String> columns, String fieldName) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must be non-empty");
        }
        for (int i = 0; i < columns.size(); i++) {
            String c = columns.get(i);
            if (c == null || c.isEmpty()) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] must be a non-empty string, got: " + c);
            }
        }
    }

    /** Validate a heterogeneous {@code columns} list. Each element must be a
     *  non-empty {@link String} or any {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression}.
     *  Empty / null lists are allowed (intermediate fluent stages produce them
     *  before {@code .select(...)} is called). */
    static void validateColumnElements(List<?> columns, String fieldName) {
        if (columns == null || columns.isEmpty()) return;
        for (int i = 0; i < columns.size(); i++) {
            Object c = columns.get(i);
            if (c == null) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] must not be null");
            }
            if (c instanceof String s) {
                if (s.isEmpty()) {
                    throw new IllegalArgumentException(
                            fieldName + "[" + i + "] string must not be empty");
                }
                continue;
            }
            if (c instanceof com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression) {
                continue;
            }
            throw new IllegalArgumentException(
                    fieldName + "[" + i + "] must be String or PlanExpression, got: "
                            + c.getClass().getName());
        }
    }

    static void validateStringList(List<String> values, String fieldName) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (v == null || v.isEmpty()) {
                throw new IllegalArgumentException(
                        fieldName + "[" + i + "] must be a non-empty string, got: " + v);
            }
        }
    }

    static void validatePagination(Integer limit, Integer start, String owner) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException(
                    owner + ".limit must be non-negative or null; got: " + limit);
        }
        if (start != null && start < 0) {
            throw new IllegalArgumentException(
                    owner + ".start must be non-negative or null; got: " + start);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<JoinOn> coerceJoinOnList(List<?> raw) {
        List<JoinOn> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object entry = raw.get(i);
            if (entry instanceof JoinOn) {
                out.add((JoinOn) entry);
            } else if (entry instanceof Map) {
                try {
                    out.add(JoinOn.fromMap((Map<String, ?>) entry));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                            "JoinPlan.on[" + i + "]: " + ex.getMessage(), ex);
                }
            } else {
                throw new IllegalArgumentException(
                        "JoinPlan.on[" + i + "] must be a JoinOn or Map, got: "
                                + (entry == null ? "null" : entry.getClass().getSimpleName()));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static QueryOptions queryOptionsFromArgs(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(
                    "QueryPlan.query(opts): opts must be an object");
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        QueryOptions.Builder builder = QueryOptions.builder();
        Object columns = map.get("columns");
        builder.columns(columns instanceof List<?> list ? list : List.of());
        if (map.containsKey("slice") && map.get("slice") instanceof List<?> list) {
            builder.slice((List<Object>) list);
        }
        if (map.containsKey("groupBy") && map.get("groupBy") instanceof List<?> list) {
            builder.groupBy((List<String>) list);
        }
        if (map.containsKey("orderBy") && map.get("orderBy") instanceof List<?> list) {
            builder.orderBy((List<String>) list);
        }
        if (map.get("limit") instanceof Number n) {
            builder.limit(n.intValue());
        }
        if (map.get("start") instanceof Number n) {
            builder.start(n.intValue());
        } else if (map.get("offset") instanceof Number n) {
            builder.start(n.intValue());
        }
        if (map.containsKey("distinct")) {
            builder.distinct(Boolean.TRUE.equals(map.get("distinct")));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------
    // G5 Phase 2 (F5) shared helpers — used by BaseModelPlan / DerivedQueryPlan
    // build-time visibility + flag-gating validation.
    // ------------------------------------------------------------------

    /**
     * <b>G5 Phase 2 (F5) / G10 PR4</b> · Construct an identity-keyed
     * {@code Set<QueryPlan>} suitable for {@link #collectVisiblePlans()}
     * results and other plan-tree walks that need {@code ==} membership
     * (spec §5.1 same-model multi-instance disambiguation). Uses
     * {@link IdentityHashMap} so {@code contains} / {@code add} compare
     * by object reference, not {@code equals}.
     *
     * <p>Public so the compile-time tree walker in
     * {@code ComposePlanner.runPlanAwarePermissionCheck} (cross-package)
     * uses the same idiom as plan-build-time visibility checks.</p>
     */
    public static Set<QueryPlan> identityPlanSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * <b>G5 Phase 2 (F5) / G10 PR4</b> · Extract the {@link PlanColumnRef}
     * (if any) from a column entry, peeling off {@link AggregateColumn} /
     * {@link WindowColumn} / {@link ProjectedColumn} wrappers (including
     * the nested {@code ProjectedColumn(AggregateColumn(PlanColumnRef))}
     * shape produced by F5 {@code {plan, field, agg, as}}). Returns
     * {@code null} for F1-F4 strings or any plan-expression that does not
     * transitively wrap a {@code PlanColumnRef}.
     *
     * <p>Public so {@code ComposePlanAwarePermissionValidator} (PR4
     * package {@code engine.compose.security}) can route by the same
     * shape-extraction rule used at plan build time. Both call sites must
     * agree — drift would make F5 visibility checks and PR4 plan-routed
     * permission checks see different "plan" anchors for the same
     * column.</p>
     */
    public static PlanColumnRef extractPlanRef(Object column) {
        if (column instanceof PlanColumnRef ref) {
            return ref;
        }
        if (column instanceof AggregateColumn agg) {
            return agg.ref();
        }
        if (column instanceof WindowColumn win) {
            return win.ref();
        }
        if (column instanceof ProjectedColumn proj) {
            PlanExpression inner = proj.expr();
            if (inner instanceof PlanColumnRef ref) return ref;
            if (inner instanceof AggregateColumn agg) return agg.ref();
            if (inner instanceof WindowColumn win) return win.ref();
        }
        return null;
    }

    /**
     * <b>G5 Phase 2 (F5)</b> · Validate F5 plan-qualified columns at plan
     * build time per G5 spec §5.1 (visibility / lineage rule).
     *
     * <p>For any column whose {@link #extractPlanRef} returns non-null, the
     * referenced plan must be in {@code visiblePlans} (the build-time lineage
     * of the plan being constructed). Identity comparison via the
     * {@link #identityPlanSet() identity-keyed} set — same model name
     * referenced via two distinct {@code dsl()} calls produces two distinct
     * plan instances that are NOT interchangeable. Failure:
     * {@code COLUMN_PLAN_NOT_VISIBLE}.</p>
     *
     * <p><b>Why no G10 flag check here</b> · F5 plan-qualified SQL emission is
     * gated by {@code ComposePlanner.compilePlanColumnRef} (G10 PR3): under
     * {@code g10Enabled() == false} the compiler falls back to bare column
     * name. For single-base / self-reference cases that fallback is correct
     * (one source, no ambiguity); for multi-base / join cases the schema
     * derivation already throws {@code JOIN_OUTPUT_COLUMN_CONFLICT} (legacy
     * behaviour) before SQL emission — there is no silent-wrong-SQL window
     * to guard against at the build stage. The chained API (existing
     * {@code myBase.amount.sum().as("total")} path) and the F5 Map syntax
     * produce indistinguishable {@link PlanExpression} graphs, so a flag-gate
     * here would break the chained API too.</p>
     *
     * @param columns      heterogeneous column list (may contain F1-F4 strings
     *                     and F5 plan-expression objects mixed)
     * @param visiblePlans identity-keyed lineage set — typically
     *                     {@code source.collectVisiblePlans()} for a derived
     *                     plan, or empty for a base plan (which has no
     *                     children and itself does not yet exist during build)
     * @param fieldName    error-message prefix (e.g. {@code "DerivedQueryPlan.columns"})
     */
    static void validateF5PlanVisibility(
            List<?> columns, Set<QueryPlan> visiblePlans, String fieldName) {
        if (columns == null || columns.isEmpty()) return;
        for (int i = 0; i < columns.size(); i++) {
            PlanColumnRef ref = extractPlanRef(columns.get(i));
            if (ref == null) continue;  // F1-F4 string or non-F5 PlanExpression
            // PlanColumnRef created via Query.col("name") (factory shape) has
            // plan == null — these are "free" column references, equivalent
            // in semantics to F4 string columns. They are NOT F5 plan-qualified
            // references and are out of scope for visibility validation.
            if (ref.plan() == null) continue;
            if (!visiblePlans.contains(ref.plan())) {
                throw new IllegalArgumentException(
                        "COLUMN_PLAN_NOT_VISIBLE: " + fieldName + "[" + i + "] references "
                        + "plan ('" + ref.plan().getClass().getSimpleName() + "', field '"
                        + ref.name() + "') that is NOT in the visibility lineage of this "
                        + "plan. Per G5 spec §5.1, plan references are matched by object "
                        + "identity; same model name referenced via two distinct dsl() "
                        + "calls yields two distinct plan objects that are NOT "
                        + "interchangeable.");
            }
        }
    }
}
