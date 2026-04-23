package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityResolutionPipeline;
import com.foggyframework.dataset.db.model.engine.compose.authority.ModelInfoProvider;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.Map;

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
     * @throws com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException
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
        if (opts.semanticService == null) {
            throw new IllegalArgumentException("CompileOptions.semanticService is required");
        }

        Map<String, ModelBinding> bindings = opts.bindings;
        if (bindings == null) {
            if (context == null) {
                throw new IllegalArgumentException(
                        "compilePlanToSql: context is required on the internal-resolve path; "
                                + "either pass a ComposeQueryContext or pre-supply bindings via opts");
            }
            bindings = AuthorityResolutionPipeline.resolve(plan, context, opts.modelInfoProvider);
        }

        String namespace = context == null ? null : context.namespace();
        return ComposePlanner.compileToComposedSql(
                plan, bindings, opts.semanticService, namespace, opts.dialect);
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

    // ------------------------------------------------------------------
    // CompileOptions (Builder mirrors Python kw-only params)
    // ------------------------------------------------------------------

    /** Immutable bag of compile-time options passed to
     *  {@link #compilePlanToSql(QueryPlan, ComposeQueryContext, CompileOptions)}.
     *  Shape mirrors Python's kw-only parameters:
     *  {@code semantic_service / bindings / model_info_provider / dialect}. */
    public static final class CompileOptions {

        /** Required — the v1.3 semantic-query service that owns the
         *  per-model SQL build path. */
        private final SemanticQueryServiceV3 semanticService;

        /** Optional — when non-null, the internal authority-resolve path
         *  is skipped (two-step pattern). */
        private final Map<String, ModelBinding> bindings;

        /** Optional — forwarded to {@link AuthorityResolutionPipeline} when
         *  {@link #bindings} is null. Ignored otherwise. */
        private final ModelInfoProvider modelInfoProvider;

        /** Default {@code "mysql"} is conservative MySQL-5.7-compat; pass
         *  {@code "mysql8"} to enable CTE emission on modern MySQL. */
        private final String dialect;

        private CompileOptions(Builder b) {
            this.semanticService = b.semanticService;
            this.bindings = b.bindings;
            this.modelInfoProvider = b.modelInfoProvider;
            this.dialect = b.dialect == null ? "mysql" : b.dialect;
        }

        public SemanticQueryServiceV3 semanticService() { return semanticService; }
        public Map<String, ModelBinding> bindings() { return bindings; }
        public ModelInfoProvider modelInfoProvider() { return modelInfoProvider; }
        public String dialect() { return dialect; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private SemanticQueryServiceV3 semanticService;
            private Map<String, ModelBinding> bindings;
            private ModelInfoProvider modelInfoProvider;
            private String dialect;

            public Builder semanticService(SemanticQueryServiceV3 v) {
                this.semanticService = v;
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

            public Builder dialect(String v) {
                this.dialect = v;
                return this;
            }

            public CompileOptions build() { return new CompileOptions(this); }
        }
    }
}
