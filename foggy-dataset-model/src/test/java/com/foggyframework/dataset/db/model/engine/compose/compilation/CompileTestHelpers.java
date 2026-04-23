package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityResolutionPipeline;
import com.foggyframework.dataset.db.model.engine.compose.authority.NullModelInfoProvider;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test fakes for M6 compilation tests.
 *
 * <p>The real {@code SemanticQueryServiceV3Impl} requires a Spring context
 * + QueryModelLoader + JdbcModelQueryEngine. M6 unit tests don't need any
 * of that — they only care about the contract between
 * {@link com.foggyframework.dataset.db.model.engine.compose.compilation.PerBaseCompiler}
 * and {@code generateSql(...)}. This helper offers:
 *
 * <ul>
 *   <li>{@link FakeSemanticService} — returns a canned
 *       {@link SqlGenerationResult} per invocation, captures the
 *       {@code (model, request, context)} call args so tests can assert
 *       the injected fieldAccess / deniedColumns / systemSlice.</li>
 *   <li>{@link #principal()} / {@link #context()} — minimal builders so
 *       tests don't repeat boilerplate.</li>
 * </ul>
 */
final class CompileTestHelpers {

    private CompileTestHelpers() { /* utility */ }

    static Principal principal() {
        return Principal.builder()
                .userId("test-user")
                .tenantId("test-ns")
                .roles(List.of("analyst"))
                .build();
    }

    static ComposeQueryContext context(AuthorityResolver resolver) {
        return ComposeQueryContext.builder()
                .principal(principal())
                .namespace("test-ns")
                .traceId("trace-1")
                .authorityResolver(resolver)
                .build();
    }

    /** Authority resolver that returns the pre-built binding map. */
    static AuthorityResolver resolverFor(Map<String, ModelBinding> bindings) {
        return new AuthorityResolver() {
            @Override
            public AuthorityResolution resolve(AuthorityRequest request) {
                // Return only the bindings for the requested models.
                Map<String, ModelBinding> matching = new LinkedHashMap<>();
                for (String name : request.modelNames()) {
                    ModelBinding b = bindings.get(name);
                    if (b != null) {
                        matching.put(name, b);
                    }
                }
                return AuthorityResolution.builder().bindings(matching).build();
            }
        };
    }

    /** Convenience: build a binding with only the non-empty required defaults. */
    static ModelBinding emptyBinding() {
        return ModelBinding.builder().build();
    }

    /** Canned SQL + params returned by {@link FakeSemanticService}. */
    static final class CannedResult {
        final String sql;
        final List<Object> params;

        CannedResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /** A captured invocation of {@link FakeSemanticService#generateSql}. */
    static final class CapturedInvocation {
        final String model;
        final SemanticQueryRequest request;
        final SemanticRequestContext context;

        CapturedInvocation(String model, SemanticQueryRequest request, SemanticRequestContext context) {
            this.model = model;
            this.request = request;
            this.context = context;
        }
    }

    /** Test fake for {@link SemanticQueryServiceV3}. */
    static final class FakeSemanticService implements SemanticQueryServiceV3 {

        private final Map<String, CannedResult> canned = new HashMap<>();
        final List<CapturedInvocation> invocations = new ArrayList<>();
        private RuntimeException throwOnNext;

        FakeSemanticService stub(String model, String sql, Object... params) {
            canned.put(model, new CannedResult(sql, new ArrayList<>(Arrays.asList(params))));
            return this;
        }

        FakeSemanticService throwNext(RuntimeException ex) {
            this.throwOnNext = ex;
            return this;
        }

        @Override
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                               SemanticRequestContext context) {
            invocations.add(new CapturedInvocation(model, request, context));
            if (throwOnNext != null) {
                RuntimeException toThrow = throwOnNext;
                throwOnNext = null;
                throw toThrow;
            }
            CannedResult c = canned.get(model);
            if (c == null) {
                throw new RuntimeException("Model not found: " + model);
            }
            return new SqlGenerationResult(c.sql, c.params, null);
        }

        // ---- unused methods required by the interface ----

        @Override
        public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse queryModel(
                String model,
                SemanticQueryRequest request,
                String mode,
                SemanticRequestContext context) {
            throw new UnsupportedOperationException("FakeSemanticService.queryModel not used in M6 tests");
        }

        @Override
        public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse validateQuery(
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context) {
            throw new UnsupportedOperationException("FakeSemanticService.validateQuery not used in M6 tests");
        }
    }

    /** Build a simple BaseModelPlan with model + columns (no slice/groupBy/orderBy). */
    static BaseModelPlan base(String model, String... columns) {
        return BaseModelPlan.builder()
                .model(model)
                .columns(List.of(columns))
                .build();
    }

    /** Wrapper around {@link AuthorityResolutionPipeline} for tests that
     *  prefer a direct Map<String, ModelBinding>. */
    static Map<String, ModelBinding> resolve(QueryPlan plan, Map<String, ModelBinding> bindings) {
        return AuthorityResolutionPipeline.resolve(
                plan, context(resolverFor(bindings)), new NullModelInfoProvider());
    }
}
