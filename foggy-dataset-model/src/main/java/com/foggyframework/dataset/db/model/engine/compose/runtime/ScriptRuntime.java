package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityException;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityPolicy;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityRegistry;
import com.foggyframework.dataset.db.model.engine.compose.capability.FunctionDescriptor;
import com.foggyframework.dataset.db.model.engine.compose.capability.MethodDescriptor;
import com.foggyframework.dataset.db.model.engine.compose.capability.ObjectFacadeDescriptor;
import com.foggyframework.dataset.db.model.engine.compose.capability.ObjectFacadeProxy;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.db.model.engine.compose.plan.PlanAliasSupport;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryFactory;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ExpressionWhitelistValidator;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ScriptSourceScanner;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.SecurityParamGuard;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlExecutionPort;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.support.FsscriptImpl;
import com.foggyframework.fsscript.utils.ExpUtils;

import java.util.*;
import java.util.function.Function;

/**
 * M7 Compose Query script runtime — parse + evaluate fsscript with a
 * sandboxed visible surface ({@code from}, {@code dsl}, {@code Query}, {@code subquery}).
 *
 * <p><b>Sandbox strategy (Java — simpler than Python).</b>
 * Java {@link DefaultExpEvaluator} does NOT pre-inject any builtins when
 * constructed with {@code appCtx=null}. The default visible surface is
 * empty (plus script-defined variables). Danger is not "pre-injected
 * builtins" (Python has 17) but rather "{@code appCtx}-based Spring
 * beans". Passing {@code appCtx=null} blocks all {@code @FsscriptExp}
 * bean injection.</p>
 *
 * <p>{@link #ALLOWED_SCRIPT_GLOBALS} is {@code Set.of("from", "dsl", "Query", "subquery")}
 * — exactly 4 items. Tests hard-assert this equals the evaluator's
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
    public static final Set<String> ALLOWED_SCRIPT_GLOBALS = Set.of("from", "dsl", "Query", "subquery");

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
        return runScript(script, ctx, semanticService, dialect, previewMode, null, null);
    }

    /**
     * Execute a Compose Query fsscript with capability injection.
     *
     * <p>Capabilities are only injected when both registry and policy are
     * explicit. Existing overloads keep the default visible surface unchanged.</p>
     */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy) {
        return runScript(script, ctx, semanticService, dialect, false, capabilityRegistry, capabilityPolicy);
    }

    /**
     * Execute a Compose Query fsscript with capability injection and suspension support.
     *
     * @since 8.5.0 (P2.5)
     */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            boolean previewMode,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy,
            SuspensionManager suspensionManager) {
        return runScript(script, ctx, semanticService, dialect, previewMode,
                capabilityRegistry, capabilityPolicy, suspensionManager, false);
    }

    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            boolean previewMode,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy,
            SuspensionManager suspensionManager,
            boolean normalizePlan) {
        if (semanticService == null) {
            throw new IllegalArgumentException("semanticService must not be null");
        }
        return runScript(script, ctx,
                SemanticQueryServiceV3.composePlanningPort(semanticService),
                SemanticQueryServiceV3.composeExecutionPort(semanticService), dialect,
                previewMode, capabilityRegistry, capabilityPolicy, suspensionManager, normalizePlan);
    }

    /** Execute with independently supplied semantic planning and raw-SQL ports. */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            ComposeSemanticPlanningPort planningPort,
            ComposeSqlExecutionPort executionPort,
            String dialect,
            boolean previewMode,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy,
            SuspensionManager suspensionManager,
            boolean normalizePlan) {
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        if (planningPort == null) throw new IllegalArgumentException("semanticService must not be null");
        if (executionPort == null) throw new IllegalArgumentException("semanticService must not be null");

        // P2.5: set up run context and manager
        ScriptRunContext runCtx = new ScriptRunContext();
        ScriptRunContextHolder.Token runToken = null;
        if (suspensionManager != null) {
            suspensionManager.registerRun(runCtx);
            runToken = ScriptRunContextHolder.set(runCtx);
            ComposePause.CURRENT_MANAGER.set(suspensionManager);
        }

        try {
            return doRunScript(script, ctx, planningPort, executionPort, dialect,
                    previewMode, capabilityRegistry, capabilityPolicy, suspensionManager, normalizePlan);
        } finally {
            if (suspensionManager != null) {
                ComposePause.CURRENT_MANAGER.remove();
                ScriptRunContextHolder.pop(runToken);
                // Complete or abort depending on state
                if (!runCtx.isTerminal()) {
                    try {
                        suspensionManager.completeRun(runCtx.getRunId());
                    } catch (Exception ignored) {
                        // run may already have been cleaned up
                    }
                }
            }
        }
    }

    /**
     * Execute a Compose Query fsscript with optional preview mode and capability injection.
     */
    public static ScriptResult runScript(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            boolean previewMode,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy) {
        return runScript(script, ctx, semanticService, dialect,
                previewMode, capabilityRegistry, capabilityPolicy, null);
    }

    private static ScriptResult doRunScript(
            String script,
            ComposeQueryContext ctx,
            ComposeSemanticPlanningPort planningPort,
            ComposeSqlExecutionPort executionPort,
            String dialect,
            boolean previewMode,
            CapabilityRegistry capabilityRegistry,
            CapabilityPolicy capabilityPolicy,
            SuspensionManager suspensionManager,
            boolean normalizePlan) {
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        if (planningPort == null) throw new IllegalArgumentException("semanticService must not be null");
        if (executionPort == null) throw new IllegalArgumentException("semanticService must not be null");

        String effectiveDialect = dialect == null ? "mysql" : dialect;
        ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                .ctx(ctx)
                .planningPort(planningPort)
                .executionPort(executionPort)
                .dialect(effectiveDialect)
                .normalizePlan(normalizePlan)
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
            ExpEvaluator evaluator = new AliasBindingExpEvaluator(def.newFoggyClosure());

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
                if (args.containsKey("having")) {
                    @SuppressWarnings("unchecked")
                    List<Object> having = (List<Object>) args.get("having");
                    ExpressionWhitelistValidator.validateSlice(having, "script-eval");
                    builder.having(having);
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
                if (args.containsKey("calculatedFields")) {
                    @SuppressWarnings("unchecked")
                    List<Object> calculatedFields = (List<Object>) args.get("calculatedFields");
                    builder.calculatedFields(toCalculatedFields(calculatedFields));
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
            evaluator.setVar("subquery", (Function<Object[], Object>) rawArgs -> {
                if (rawArgs == null || rawArgs.length == 0 || rawArgs.length > 2) {
                    throw new IllegalArgumentException("subquery(plan[, field]) requires 1 or 2 arguments");
                }
                QueryPlan plan = (QueryPlan) rawArgs[0];
                String field = rawArgs.length == 2 && rawArgs[1] != null
                        ? String.valueOf(rawArgs[1])
                        : null;
                return Dsl.subquery(plan, field);
            });

            // 4a. Inject the new OO entry point: Query.from("ModelName")
            evaluator.setVar("Query", QueryFactory.INSTANCE);

            // 4b. Inject the read-only 'params' surface from ctx
            Map<String, Object> ctxParams = ctx.params();
            if (ctxParams != null && !ctxParams.isEmpty()) {
                evaluator.setVar("params", Collections.unmodifiableMap(ctxParams));
            } else {
                evaluator.setVar("params", Collections.emptyMap());
            }

            injectCapabilities(evaluator, capabilityRegistry, capabilityPolicy);

            // P2.5: Inject optional runtime.pause when policy allows
            if (capabilityPolicy != null && capabilityPolicy.isScriptPauseAllowed()
                    && suspensionManager != null) {
                java.util.Map<String, Object> runtimeObj = new java.util.LinkedHashMap<>();
                runtimeObj.put("pause", (java.util.function.Function<Object[], Object>) rawArgs -> {
                    if (rawArgs == null || rawArgs.length != 1 || !(rawArgs[0] instanceof Map)) {
                        throw new IllegalArgumentException("runtime.pause must be called with an options object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> opts = (Map<String, Object>) rawArgs[0];
                    Object reason = opts.get("reason");
                    if (!(reason instanceof String) || ((String) reason).isEmpty()) {
                        throw new IllegalArgumentException("runtime.pause requires 'reason'");
                    }
                    Object timeoutMs = opts.get("timeout_ms");
                    if (!(timeoutMs instanceof Number)) {
                        throw new IllegalArgumentException("runtime.pause requires 'timeout_ms'");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> summary = opts.containsKey("summary")
                            ? (Map<String, Object>) opts.get("summary") : Map.of();
                    return ComposePause.pause((String) reason, summary,
                            ((Number) timeoutMs).intValue());
                });
                evaluator.setVar("runtime", Collections.unmodifiableMap(runtimeObj));
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

    private static final class AliasBindingExpEvaluator extends DefaultExpEvaluator {

        private AliasBindingExpEvaluator(FsscriptClosure closure) {
            super(null, closure);
        }

        @Override
        public Object setVar(String name, Object value) {
            Object result = super.setVar(name, value);
            if (value instanceof QueryPlan plan) {
                PlanAliasSupport.bindAlias(plan, name);
            }
            return result;
        }
    }

    private static void injectCapabilities(
            ExpEvaluator evaluator,
            CapabilityRegistry registry,
            CapabilityPolicy policy) {
        if (registry == null || policy == null || registry.isEmpty()) {
            return;
        }

        for (String name : policy.getAllowedFunctions()) {
            if (!registry.hasFunction(name)) {
                continue;
            }
            CapabilityRegistry.FunctionEntry entry = registry.getFunction(name);
            FunctionDescriptor descriptor = entry.getDescriptor();
            if (!"pure_runtime".equals(descriptor.getKind())
                    || !descriptor.getAllowedIn().contains("compose_runtime")
                    || entry.getHandler() == null) {
                continue;
            }
            evaluator.setVar(name, (Function<Object[], Object>) rawArgs -> {
                Object result = entry.getHandler().handle(toNamedArgs(descriptor, rawArgs));
                ensureSafeCapabilityReturn(result, "Function '" + name + "'");
                return result;
            });
        }

        for (String objectName : policy.getAllowedObjects().keySet()) {
            if (!registry.hasObject(objectName) || !policy.isObjectAllowed(objectName)) {
                continue;
            }
            CapabilityRegistry.ObjectEntry entry = registry.getObject(objectName);
            ObjectFacadeDescriptor descriptor = entry.getDescriptor();
            ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, entry.getTarget(), policy);
            Map<String, Object> methodWrappers = new LinkedHashMap<>();
            for (MethodDescriptor method : descriptor.getMethods()) {
                if (!policy.isMethodAllowed(objectName, method.getName())) {
                    continue;
                }
                methodWrappers.put(method.getName(), (Function<Object[], Object>) rawArgs -> {
                    Object[] args = rawArgs == null ? new Object[0] : rawArgs;
                    Object result = proxy.invoke(method.getName(), args);
                    ensureSafeCapabilityReturn(result,
                            "Method '" + method.getName() + "' on object '" + objectName + "'");
                    return result;
                });
            }
            evaluator.setVar(objectName, Collections.unmodifiableMap(methodWrappers));
        }
    }

    private static Map<String, Object> toNamedArgs(FunctionDescriptor descriptor, Object[] rawArgs) {
        Object[] args = rawArgs == null ? new Object[0] : rawArgs;
        List<Map<String, Object>> schema = descriptor.getArgsSchema();
        if (args.length > schema.size()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + descriptor.getName() + "' received too many arguments.");
        }
        Map<String, Object> named = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object argName = schema.get(i).get("name");
            if (!(argName instanceof String name) || name.isEmpty()) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + descriptor.getName() + "' has invalid argsSchema[" + i + "].name.");
            }
            named.put(name, args[i]);
        }
        return Collections.unmodifiableMap(named);
    }

    private static void ensureSafeCapabilityReturn(Object result, String label) {
        if (!ObjectFacadeProxy.isSafeReturnValue(result)) {
            throw new CapabilityException.ReturnTypeDenied(
                    label + " returned a value of disallowed type.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<CalculatedFieldDef> toCalculatedFields(List<Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<CalculatedFieldDef> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof CalculatedFieldDef def) {
                out.add(def);
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                out.add(toCalculatedField((Map<String, Object>) map));
                continue;
            }
            throw new IllegalArgumentException("calculatedFields entries must be objects");
        }
        return out;
    }

    private static CalculatedFieldDef toCalculatedField(Map<String, Object> map) {
        CalculatedFieldDef def = new CalculatedFieldDef();
        def.setName((String) map.get("name"));
        def.setCaption((String) map.get("caption"));
        def.setExpression((String) map.get("expression"));
        def.setDescription((String) map.get("description"));
        def.setAgg((String) map.get("agg"));
        return def;
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
