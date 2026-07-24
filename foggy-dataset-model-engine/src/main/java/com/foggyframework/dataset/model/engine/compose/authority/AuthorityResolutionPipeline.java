package com.foggyframework.dataset.model.engine.compose.authority;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.engine.compose.security.ModelQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Top-level M5 entry point — walk a plan, batch-call the resolver, validate
 * the response, return per-model bindings.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>{@link BaseModelPlanCollector#collect(QueryPlan)} — walk the tree,
 *       dedup by {@code model}.</li>
 *   <li>Build one {@link ModelQuery} per unique model (tables via injected
 *       {@link ModelInfoProvider}, or {@code List.of()} fallback).</li>
 *   <li>Build one {@link AuthorityRequest} and call
 *       {@code context.authorityResolver().resolve(request)}.</li>
 *   <li>Validate: resolver must return an {@link AuthorityResolution};
 *       its key-set must equal the requested model set; each value must
 *       be a {@link ModelBinding}.</li>
 *   <li>Return {@code Map<model_name, ModelBinding>}. Downstream consumers
 *       (M6 SQL compiler, M7 script runner) look up bindings by QM model
 *       name.</li>
 * </ol>
 *
 * <h2>Fail-closed invariants</h2>
 * <ul>
 *   <li>Missing resolver ({@code context == null} or
 *       {@code context.authorityResolver() == null}) →
 *       {@link AuthorityErrorCodes#RESOLVER_NOT_AVAILABLE}. The
 *       {@link ComposeQueryContext} ctor already rejects null, but we
 *       double-check here in case a caller bypasses the context layer
 *       (Mockito-backed fakes, future integrations).</li>
 *   <li>Resolver-raised {@link AuthorityResolutionException} propagates
 *       verbatim — never swallowed or remapped.</li>
 *   <li>Resolver-raised unexpected exceptions become
 *       {@link AuthorityErrorCodes#UPSTREAM_FAILURE} with {@code cause}
 *       preserved (so the log chain survives).</li>
 *   <li>Non-{@link AuthorityResolution} return →
 *       {@link AuthorityErrorCodes#INVALID_RESPONSE}.</li>
 *   <li>Response with missing key →
 *       {@link AuthorityErrorCodes#MODEL_BINDING_MISSING} for the first
 *       absent model (deterministic: iterate in request order).</li>
 *   <li>Response with extra keys or non-{@link ModelBinding} value →
 *       {@link AuthorityErrorCodes#INVALID_RESPONSE}.</li>
 * </ul>
 *
 * <h2>Request-level caching</h2>
 * One call to {@link #resolve} performs <b>at most one</b> resolver
 * invocation — the collector deduplicates by model name before we build
 * the request. Per-session cross-request caching is NOT in M5 scope
 * (the requirements doc explicitly defers it; per-script dedup is the
 * minimum the batch protocol needs).
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.resolver.resolve_authority_for_plan}.
 * Python exposes a keyword-only {@code model_info_provider=None}; Java
 * uses a 2-arg overload (no provider) and a 3-arg overload.</p>
 *
 * @since 8.2.0.beta
 */
public final class AuthorityResolutionPipeline {

    private AuthorityResolutionPipeline() { /* utility */ }

    /**
     * Convenience overload — uses {@link NullModelInfoProvider} for table
     * lookup. Equivalent to Python's {@code resolve_authority_for_plan(plan,
     * context)} with default {@code model_info_provider=None}.
     */
    public static Map<String, ModelBinding> resolve(
            QueryPlan plan, ComposeQueryContext context) {
        return resolve(plan, context, null);
    }

    /**
     * Resolve per-model authority bindings for every {@link BaseModelPlan}
     * reachable from {@code plan}.
     *
     * @param plan root of the plan tree to bind authority for
     * @param context execution context with a valid
     *                {@link AuthorityResolver}
     * @param provider optional host-supplied lookup of
     *                 (model_name &rarr; physical tables); {@code null}
     *                 falls back to {@link NullModelInfoProvider}
     * @return unmodifiable map keyed by QM model name
     * @throws AuthorityResolutionException on any contract violation
     *         (see class javadoc for the fail-closed branches)
     */
    public static Map<String, ModelBinding> resolve(
            QueryPlan plan,
            ComposeQueryContext context,
            ModelInfoProvider provider) {

        // (1) Null-resolver / null-context guard. M1 ComposeQueryContext
        // already rejects null resolver in its ctor, but we re-check
        // here to defend against fakes that bypass the ctor.
        if (context == null || context.authorityResolver() == null) {
            throw new AuthorityResolutionException(
                    AuthorityErrorCodes.RESOLVER_NOT_AVAILABLE,
                    "ComposeQueryContext is missing an authorityResolver; "
                            + "fail-closed means we never resolve with a null resolver",
                    null,
                    AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        }

        // (2) Collect unique BaseModelPlans; if the plan somehow has no
        // models, return {} (not null) so callers can uniformly .get().
        List<BaseModelPlan> basePlans = BaseModelPlanCollector.collect(plan);
        if (basePlans.isEmpty()) {
            return Collections.emptyMap();
        }

        ModelInfoProvider effectiveProvider =
                provider != null ? provider : new NullModelInfoProvider();

        // (3) Build one ModelQuery per unique base plan.
        List<ModelQuery> mqs = new ArrayList<>(basePlans.size());
        for (BaseModelPlan bp : basePlans) {
            List<String> tables = safeGetTables(
                    effectiveProvider, bp.model(), context.namespace());
            mqs.add(ModelQuery.builder()
                    .model(bp.model())
                    .tables(tables)
                    .build());
        }

        // (4) Build request.
        AuthorityRequest request = AuthorityRequest.builder()
                .principal(context.principal())
                .namespace(context.namespace())
                .traceId(context.traceId())
                .models(mqs)
                .build();

        // (5) Call resolver; separate handling of AuthorityResolutionException
        //     (propagate verbatim) vs any other exception (wrap as UPSTREAM_FAILURE).
        AuthorityResolution resolution;
        try {
            resolution = context.authorityResolver().resolve(request);
        } catch (AuthorityResolutionException ex) {
            // Resolver spoke the structured protocol — propagate verbatim.
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthorityResolutionException(
                    AuthorityErrorCodes.UPSTREAM_FAILURE,
                    "AuthorityResolver.resolve raised an unexpected exception; "
                            + "see cause for details",
                    null,
                    AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE,
                    ex);
        }

        // (6) Validate return shape.
        if (resolution == null) {
            throw new AuthorityResolutionException(
                    AuthorityErrorCodes.INVALID_RESPONSE,
                    "AuthorityResolver.resolve must return an AuthorityResolution; got null",
                    null,
                    AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        }

        return validateBindingCoverage(resolution, request);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Call {@link ModelInfoProvider#getTablesForModel}; coerce
     *  {@link Optional#empty()} (and any {@code null} inside) to empty list. */
    private static List<String> safeGetTables(
            ModelInfoProvider provider, String modelName, String namespace) {
        Optional<List<String>> tables = provider.getTablesForModel(modelName, namespace);
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        List<String> inner = tables.get();
        return inner == null ? List.of() : List.copyOf(inner);
    }

    /**
     * Return {@code resolution.bindings()} iff its key-set exactly covers
     * the request's model-name set; otherwise raise
     * {@link AuthorityErrorCodes#MODEL_BINDING_MISSING} or
     * {@link AuthorityErrorCodes#INVALID_RESPONSE}.
     *
     * <p>Order of checks matters for deterministic error reporting:
     * <ol>
     *   <li>Every requested model must be present ({@code MODEL_BINDING_MISSING}
     *       on first absent, in request order).</li>
     *   <li>No extra keys ({@code INVALID_RESPONSE} — cheap to check).</li>
     *   <li>Every value must be a {@link ModelBinding} instance
     *       (belt-and-suspenders — {@code AuthorityResolution}'s ctor
     *       already checks this, but we don't assume the resolver used
     *       the real ctor; fakes can bypass via reflection).</li>
     * </ol></p>
     */
    private static Map<String, ModelBinding> validateBindingCoverage(
            AuthorityResolution resolution, AuthorityRequest request) {
        Map<String, ModelBinding> bindings = resolution.bindings();
        List<String> requestedNames = request.modelNames();

        // (1) Missing key — first in request order for determinism.
        for (String name : requestedNames) {
            if (!bindings.containsKey(name)) {
                throw new AuthorityResolutionException(
                        AuthorityErrorCodes.MODEL_BINDING_MISSING,
                        "AuthorityResolver returned a binding set that is missing model '"
                                + name + "'",
                        name,
                        AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
            }
        }

        // (2) Extra key — deterministic sort for error messages.
        TreeSet<String> extra = new TreeSet<>();
        for (String key : bindings.keySet()) {
            if (!requestedNames.contains(key)) {
                extra.add(key);
            }
        }
        if (!extra.isEmpty()) {
            throw new AuthorityResolutionException(
                    AuthorityErrorCodes.INVALID_RESPONSE,
                    "AuthorityResolver returned unexpected bindings for models "
                            + new ArrayList<>(extra)
                            + "; resolution must cover exactly the requested set",
                    null,
                    AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        }

        // (3) Value-type check (defensive — AuthorityResolution ctor
        // already rejects non-ModelBinding values, but reflection-backed
        // fakes can bypass that).
        for (Map.Entry<String, ModelBinding> e : bindings.entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof ModelBinding)) {
                throw new AuthorityResolutionException(
                        AuthorityErrorCodes.INVALID_RESPONSE,
                        "AuthorityResolver binding for '" + e.getKey()
                                + "' must be a ModelBinding; got "
                                + (v == null ? "null" : v.getClass().getSimpleName()),
                        e.getKey(),
                        AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
            }
        }

        // Return a defensive snapshot; preserve iteration order.
        return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
}
