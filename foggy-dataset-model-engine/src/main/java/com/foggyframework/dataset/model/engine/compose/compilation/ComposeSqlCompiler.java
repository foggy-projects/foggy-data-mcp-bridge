package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.authority.AuthorityResolutionPipeline;
import com.foggyframework.dataset.model.engine.compose.authority.DatasourceIdCollector;
import com.foggyframework.dataset.model.engine.compose.authority.ModelInfoProvider;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.normalization.PlanNormalizePipeline;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;

import java.util.Map;
import java.util.Optional;

/**
 * Compose Query SQL compiler — single public entry for M6.
 *
 * <p>Two caller patterns are supported:
 * <ol>
 *   <li><b>One-shot</b> — caller has no bindings yet; M6 internally calls
 *       {@link AuthorityResolutionPipeline#resolve(QueryPlan, ComposeQueryContext, ModelInfoProvider)}.</li>
 *   <li><b>Two-step</b> — caller already resolved bindings (e.g. for
 *       multi-dialect snapshot or when the M7 script runner owns the
 *       binding lifecycle). Supplying {@code bindings} bypasses the
 *       internal resolve.</li>
 * </ol></p>
 *
 * <p><b>Caching note (r3 Q2).</b> M6 intentionally does NOT cache the
 * resolved bindings. Callers that invoke compile multiple times on the
 * same plan (e.g. snapshot across dialects) should resolve once externally
 * and pass the same bindings on each call.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.compiler.compile_plan_to_sql}.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeSqlCompiler {

    private ComposeSqlCompiler() { /* utility */ }

    /**
     * Compile a {@link QueryPlan} tree to dialect-aware SQL + bind params.
     *
     * @param plan    root of the plan tree
     * @param context the {@link ComposeQueryContext} carrying the principal
     *                and the authority resolver; required even when
     *                bindings are pre-supplied (mirrors Python contract)
     * @param opts    compile options (semantic service, optional bindings,
     *                optional model-info provider, dialect)
     * @return a {@link ComposedSql} with the final SQL + merged parameter
     *         list (positional {@code ?} placeholders; the caller's
     *         executor translates to dialect-specific placeholders)
     *
     * @throws ComposeCompileException with one of the 4 codes defined in
     *         {@link ComposeCompileErrorCodes}
     * @throws com.foggyframework.dataset.model.engine.compose.security.AuthorityResolutionException
     *         propagated verbatim from the M5 authority-resolution
     *         pipeline when the internal-resolve path is taken
     */
    public static ComposedSql compilePlanToSql(
            QueryPlan plan,
            ComposeQueryContext context,
            CompileOptions opts) {

        if (plan == null) {
            throw new IllegalArgumentException("compilePlanToSql: plan must not be null");
        }
        if (opts == null) {
            throw new IllegalArgumentException("compilePlanToSql: opts must not be null");
        }
        if (opts.planningPort == null) {
            throw new IllegalArgumentException("CompileOptions.semanticService is required");
        }
        QueryPlan effectivePlan = opts.normalizePlan
                ? PlanNormalizePipeline.defaults().normalize(plan).normalizedPlan()
                : plan;
        QueryPlan.validatePlanSliceValues(effectivePlan);

        Map<String, ModelBinding> bindings = opts.bindings;
        if (bindings == null) {
            if (context == null) {
                throw new IllegalArgumentException(
                        "compilePlanToSql: context is required on the internal-resolve path; "
                                + "either pass a ComposeQueryContext or pre-supply bindings via opts");
            }
            bindings = AuthorityResolutionPipeline.resolve(effectivePlan, context, opts.modelInfoProvider);
        }

        // F-7 datasource identity resolution
        Map<String, Optional<String>> datasourceIds = opts.datasourceIds;
        if (datasourceIds == null && opts.modelInfoProvider != null) {
            String ns = context == null ? null : context.namespace();
            datasourceIds = DatasourceIdCollector.collect(
                    effectivePlan, opts.modelInfoProvider, ns != null ? ns : "");
        }

        String namespace = context == null ? null : context.namespace();
        return ComposePlanner.compileToComposedSql(
                effectivePlan, bindings, opts.planningPort, namespace,
                semanticRequestContext(context), opts.dialect,
                datasourceIds);
    }

    static SemanticRequestContext semanticRequestContext(ComposeQueryContext context) {
        if (context == null) {
            return SemanticRequestContext.empty()
                    .withPermissionAction(PermissionAction.EXECUTE);
        }
        Principal principal = context.principal();
        ModelResultContext.SecurityContext securityContext =
                ModelResultContext.SecurityContext.builder()
                        .authorization(principal.authorizationHint())
                        .userId(principal.userId())
                        .roles(principal.roles())
                        .tenantId(principal.tenantId())
                        .deptId(principal.deptId())
                        .build();
        return SemanticRequestContext.of(context.namespace(), securityContext)
                .withPermissionAction(PermissionAction.EXECUTE);
    }

    /** Convenience overload — one-shot path with defaults. Equivalent to
     *  {@code compilePlanToSql(plan, context, opts)} where {@code opts}
     *  carries only {@code semanticService} and a default MySQL-5.7 dialect. */
    public static ComposedSql compilePlanToSql(
            QueryPlan plan,
            ComposeQueryContext context,
            SemanticQueryServiceV3 semanticService) {
        return compilePlanToSql(plan, context,
                CompileOptions.builder().semanticService(semanticService).build());
    }

    /** Narrow-port convenience overload for callers that do not need the
     * legacy semantic service surface. */
    public static ComposedSql compilePlanToSql(
            QueryPlan plan,
            ComposeQueryContext context,
            ComposeSemanticPlanningPort planningPort) {
        return compilePlanToSql(plan, context,
                CompileOptions.builder().planningPort(planningPort).build());
    }

    // ------------------------------------------------------------------
    // CompileOptions (Builder mirrors Python kw-only params)
    // ------------------------------------------------------------------

    /** Immutable bag of compile-time options passed to
     *  {@link #compilePlanToSql(QueryPlan, ComposeQueryContext, CompileOptions)}.
     *  Shape mirrors Python's kw-only parameters:
     *  {@code semantic_service / bindings / model_info_provider / datasource_ids / dialect / normalize_plan}. */
    public static final class CompileOptions {

        /** Compatibility reference retained for existing callers. */
        private final SemanticQueryServiceV3 semanticService;

        /** Required narrow planning capability used by the compiler core. */
        private final ComposeSemanticPlanningPort planningPort;

        /** Optional — when non-null, the internal authority-resolve path
         *  is skipped (two-step pattern). */
        private final Map<String, ModelBinding> bindings;

        /** Optional — forwarded to {@link AuthorityResolutionPipeline} when
         *  {@link #bindings} is null. Ignored otherwise. */
        private final ModelInfoProvider modelInfoProvider;

        /** F-7 · Optional pre-resolved datasource identity map.
         *  When non-null the compiler uses this directly; when null and
         *  {@link #modelInfoProvider} is non-null, the compiler
         *  auto-collects via {@link DatasourceIdCollector}. */
        private final Map<String, Optional<String>> datasourceIds;

        /** Default {@code "mysql"} is conservative MySQL-5.7-compat; pass
         *  {@code "mysql8"} to enable CTE emission on modern MySQL. */
        private final String dialect;

        /** Optional plan normalization before authority resolution and SQL lower.
         *  Default false preserves the historical compiler path. */
        private final boolean normalizePlan;

        private CompileOptions(Builder b) {
            this.semanticService = b.semanticService;
            this.planningPort = b.planningPort != null
                    ? b.planningPort
                    : b.semanticService == null
                            ? null
                            : SemanticQueryServiceV3.composePlanningPort(b.semanticService);
            this.bindings = b.bindings;
            this.modelInfoProvider = b.modelInfoProvider;
            this.datasourceIds = b.datasourceIds;
            this.dialect = b.dialect == null ? "mysql" : b.dialect;
            this.normalizePlan = b.normalizePlan;
        }

        public SemanticQueryServiceV3 semanticService() { return semanticService; }
        public ComposeSemanticPlanningPort planningPort() { return planningPort; }
        public Map<String, ModelBinding> bindings() { return bindings; }
        public ModelInfoProvider modelInfoProvider() { return modelInfoProvider; }
        public Map<String, Optional<String>> datasourceIds() { return datasourceIds; }
        public String dialect() { return dialect; }
        public boolean normalizePlan() { return normalizePlan; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private SemanticQueryServiceV3 semanticService;
            private ComposeSemanticPlanningPort planningPort;
            private Map<String, ModelBinding> bindings;
            private ModelInfoProvider modelInfoProvider;
            private Map<String, Optional<String>> datasourceIds;
            private String dialect;
            private boolean normalizePlan;

            public Builder semanticService(SemanticQueryServiceV3 v) {
                this.semanticService = v;
                this.planningPort = v == null ? null : SemanticQueryServiceV3.composePlanningPort(v);
                return this;
            }

            public Builder planningPort(ComposeSemanticPlanningPort v) {
                this.planningPort = v;
                return this;
            }

            public Builder bindings(Map<String, ModelBinding> v) {
                this.bindings = v;
                return this;
            }

            public Builder modelInfoProvider(ModelInfoProvider v) {
                this.modelInfoProvider = v;
                return this;
            }

            public Builder datasourceIds(Map<String, Optional<String>> v) {
                this.datasourceIds = v;
                return this;
            }

            public Builder dialect(String v) {
                this.dialect = v;
                return this;
            }

            public Builder normalizePlan(boolean v) {
                this.normalizePlan = v;
                return this;
            }

            public CompileOptions build() { return new CompileOptions(this); }
        }
    }
}
