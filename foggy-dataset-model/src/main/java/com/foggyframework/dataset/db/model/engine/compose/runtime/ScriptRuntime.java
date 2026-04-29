package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryFactory;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ExpressionWhitelistValidator;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ScriptSourceScanner;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.SecurityParamGuard;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.support.FsscriptImpl;
import com.foggyframework.fsscript.utils.ExpUtils;

import java.util.*;
import java.util.function.Function;

/**
 * M7 Compose Query script runtime — parse + evaluate fsscript with a
 * sandboxed visible surface ({@code from}, {@code dsl}, {@code Query}).
 *
 * <p><b>Sandbox strategy (Java — simpler than Python).</b>
 * Java {@link DefaultExpEvaluator} does NOT pre-inject any builtins when
 * constructed with {@code appCtx=null}. The default visible surface is
 * empty (plus script-defined variables). Danger is not "pre-injected
 * builtins" (Python has 17) but rather "{@code appCtx}-based Spring
 * beans". Passing {@code appCtx=null} blocks all {@code @FsscriptExp}
 * bean injection.</p>
 *
 * <p>{@link #ALLOWED_SCRIPT_GLOBALS} is {@code Set.of("from", "dsl", "Query")}
 * — exactly 3 items. Tests hard-assert this equals the evaluator's
 * actual visible surface after injection.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.runtime.script_runtime}.</p>
 *
 * @since 8.2.0.beta
 */
public final class ScriptRuntime {

    private ScriptRuntime() { /* utility */ }

    /**
     * Exact set of global names injected into the script evaluator.
     * Tests hard-assert this equals the evaluator's actual visible surface.
     */
    public static final Set<String> ALLOWED_SCRIPT_GLOBALS = Set.of("from", "dsl", "Query");

    /**
     * Execute a Compose Query fsscript.
     *
     * @param script          the fsscript source code
     * @param ctx             compose query context
     * @param semanticService the semantic service for compile + execute
     * @param dialect         SQL dialect (null defaults to "mysql")
     * @return a {@link ScriptResult} with value / sql / params / warnings
     * @throws IllegalArgumentException if ctx or semanticService is null
     */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect) {
        return runScript(script, ctx, semanticService, dialect, false);
    }

    /**
     * Execute a Compose Query fsscript with an optional preview mode.
     *
     * @param script          the fsscript source code
     * @param ctx             compose query context
     * @param semanticService the semantic service for compile + execute
     * @param dialect         SQL dialect (null defaults to "mysql")
     * @param previewMode     if true, QueryPlans returned are evaluated to SQL instead of fetching data
     * @return a {@link ScriptResult} with value / sql / params / warnings
     * @throws IllegalArgumentException if ctx or semanticService is null
     */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            boolean previewMode) {
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        if (semanticService == null) throw new IllegalArgumentException("semanticService must not be null");

        String effectiveDialect = dialect == null ? "mysql" : dialect;
        ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                .ctx(ctx)
                .semanticService(semanticService)
                .dialect(effectiveDialect)
                .build();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(bundle);
        try {
            // 0. Layer A pre-execution source scan
            ScriptSourceScanner.scan(script);

            // 1. Create closure definition space
            SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
            FsscriptClosureDefinition def = space.newFsscriptClosureDefinition();

            // 2. Compile script using ComposeQueryDialect to allow `from(...)` as a function
            com.foggyframework.fsscript.parser.spi.Parser parser = com.foggyframework.fsscript.utils.ExpUtils.getParser();
            Exp exp = parser.compileEl(def, script, com.foggyframework.fsscript.parser.ComposeQueryDialect.INSTANCE);
            if (exp == null) {
                return ScriptResult.builder().build();
            }
            Fsscript fsscript = new FsscriptImpl(def, exp);

            // 3. Create evaluator with appCtx=null (sandbox: blocks @FsscriptExp beans)
            ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(null,
                    def.newFoggyClosure());

            // 4. Inject the two allowed globals: from and dsl (both aliases for Dsl.from)
            //    fsscript engine calls Function<Object[], Object>.apply(evalArgs)
            //    where evalArgs is the Object[] of evaluated argument expressions.
            //    For from({model:'X', columns:['id']}), evalArgs = [ Map{model=X, columns=[id]} ]
            Function<Object[], Object> fromFunction = rawArgs -> {
                if (rawArgs == null || rawArgs.length == 0) {
                    throw new IllegalArgumentException(
                            "from() / dsl() requires exactly 1 argument (options map)");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) rawArgs[0];

                // Layer A: block security-sensitive parameters
                SecurityParamGuard.validate(args, "script-eval");

                Dsl.FromOptions.Builder builder = Dsl.FromOptions.builder();
                if (args.containsKey("model")) {
                    builder.model((String) args.get("model"));
                }
                if (args.containsKey("source")) {
                    builder.source((QueryPlan) args.get("source"));
                }
                if (args.containsKey("columns")) {
                    @SuppressWarnings("unchecked")
                    List<Object> rawColumns = (List<Object>) args.get("columns");
                    // G5 Phase 1 (F4): Normalize {field, agg?, as?} Map entries to
                    // their canonical string form. F1-F3 strings and PlanColumnRef /
                    // other PlanExpression types pass through unchanged.
                    List<Object> columns = com.foggyframework.dataset.db.model.engine.compose.plan
                            .ColumnObjectNormalizer.normalizeColumns(rawColumns);
                    // Layer B: validate column expressions (String + PlanExpression heterogeneous).
                    ExpressionWhitelistValidator.validateColumns(columns, "script-eval");
                    builder.columns(columns);
                }
                if (args.containsKey("slice")) {
                    @SuppressWarnings("unchecked")
                    List<Object> slice = (List<Object>) args.get("slice");
                    // Layer B: validate slice values for injection
                    ExpressionWhitelistValidator.validateSlice(slice, "script-eval");
                    builder.slice(slice);
                }
                if (args.containsKey("groupBy")) {
                    @SuppressWarnings("unchecked")
                    List<String> groupBy = (List<String>) args.get("groupBy");
                    builder.groupBy(groupBy);
                }
                if (args.containsKey("orderBy")) {
                    @SuppressWarnings("unchecked")
                    List<String> orderBy = (List<String>) args.get("orderBy");
                    builder.orderBy(orderBy);
                }
                if (args.containsKey("limit")) {
                    builder.limit(((Number) args.get("limit")).intValue());
                }
                if (args.containsKey("start")) {
                    builder.start(((Number) args.get("start")).intValue());
                }
                if (args.containsKey("distinct")) {
                    builder.distinct(Boolean.TRUE.equals(args.get("distinct")));
                }
                return Dsl.from(builder.build());
            };
            evaluator.setVar("from", fromFunction);
            evaluator.setVar("dsl", fromFunction);  // dsl is an alias for from

            // 4a. Inject the new OO entry point: Query.from("ModelName")
            evaluator.setVar("Query", QueryFactory.INSTANCE);

            // 4b. Inject the read-only 'params' surface from ctx
            Map<String, Object> ctxParams = ctx.params();
            if (ctxParams != null && !ctxParams.isEmpty()) {
                evaluator.setVar("params", Collections.unmodifiableMap(ctxParams));
            } else {
                evaluator.setVar("params", Collections.emptyMap());
            }

            // 5. Execute
            Object result = fsscript.evalResult(evaluator);

            // 5.5 If result is a Map containing 'plans', auto-execute them
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapResult = (Map<String, Object>) result;
                if (mapResult.containsKey("plans")) {
                    Object plansObj = mapResult.get("plans");
                    if (plansObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> plans = (Map<String, Object>) plansObj;
                        Map<String, Object> executedPlans = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : plans.entrySet()) {
                            Object planObj = entry.getValue();
                            if (planObj instanceof QueryPlan) {
                                QueryPlan qp = (QueryPlan) planObj;
                                executedPlans.put(entry.getKey(), previewMode ? qp.toSql() : qp.execute());
                            } else {
                                executedPlans.put(entry.getKey(), planObj);
                            }
                        }
                        mapResult.put("plans", executedPlans);
                    } else if (plansObj instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> plans = (java.util.List<Object>) plansObj;
                        java.util.List<Object> executedPlans = new java.util.ArrayList<>();
                        for (Object planObj : plans) {
                            if (planObj instanceof QueryPlan) {
                                QueryPlan qp = (QueryPlan) planObj;
                                executedPlans.add(previewMode ? qp.toSql() : qp.execute());
                            } else {
                                executedPlans.add(planObj);
                            }
                        }
                        mapResult.put("plans", executedPlans);
                    } else if (plansObj instanceof QueryPlan) {
                        QueryPlan qp = (QueryPlan) plansObj;
                        mapResult.put("plans", previewMode ? qp.toSql() : qp.execute());
                    }
                }
            } else if (result instanceof QueryPlan) {
                // Backward compatibility: If the script returns a raw QueryPlan instead of calling execute()
                QueryPlan qp = (QueryPlan) result;
                if (previewMode) {
                    result = qp.toSql();
                } else {
                    result = qp.execute();
                }
            }

            // 6. Build ScriptResult
            return ScriptResult.builder()
                    .value(result)
                    .build();

        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    /**
     * Result of a {@link #runScript} invocation.
     */
    public static final class ScriptResult {

        private final Object value;
        private final String sql;
        private final List<Object> params;
        private final List<String> warnings;

        private ScriptResult(Builder b) {
            this.value = b.value;
            this.sql = b.sql;
            this.params = b.params;
            this.warnings = b.warnings == null ? List.of() : List.copyOf(b.warnings);
        }

        public Object value() { return value; }
        public String sql() { return sql; }
        public List<Object> params() { return params; }
        public List<String> warnings() { return warnings; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Object value;
            private String sql;
            private List<Object> params;
            private List<String> warnings;

            public Builder value(Object v) { this.value = v; return this; }
            public Builder sql(String v) { this.sql = v; return this; }
            public Builder params(List<Object> v) { this.params = v; return this; }
            public Builder warnings(List<String> v) { this.warnings = v; return this; }

            public ScriptResult build() { return new ScriptResult(this); }
        }

        @Override
        public String toString() {
            return "ScriptResult{value=" + value
                    + ", sql=" + sql
                    + ", params=" + params
                    + ", warnings=" + warnings + '}';
        }
    }
}
