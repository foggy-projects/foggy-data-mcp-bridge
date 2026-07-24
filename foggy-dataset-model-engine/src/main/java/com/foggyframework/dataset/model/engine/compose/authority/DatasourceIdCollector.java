package com.foggyframework.dataset.model.engine.compose.authority;

import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Datasource identity resolution for cross-datasource detection (F-7).
 *
 * <p>Collects per-model datasource identities from a
 * {@link ModelInfoProvider} so the compose compiler can reject
 * union / join plans whose leaf models span multiple datasources
 * at compile time.</p>
 *
 * <p>This class is intentionally separate from
 * {@link AuthorityResolutionPipeline} to keep the
 * {@code resolve(...) → Map<String, ModelBinding>} return-type
 * contract frozen — existing callers that unpack bindings are
 * not disturbed.</p>
 *
 * <p>The compile entry point
 * ({@link com.foggyframework.dataset.model.engine.compose.compilation.ComposeSqlCompiler})
 * calls {@link #collect(QueryPlan, ModelInfoProvider, String)}
 * independently when a {@code modelInfoProvider} is available.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.authority.datasource_ids}.</p>
 *
 * @since 8.5.0.beta
 */
public final class DatasourceIdCollector {

    private static final Logger LOG = Logger.getLogger(DatasourceIdCollector.class.getName());

    private DatasourceIdCollector() { /* utility */ }

    /**
     * Walk {@code plan}, call {@code provider.getDatasourceId} for each
     * unique {@link BaseModelPlan#model()}, and return a
     * {@code {modelName → datasourceId}} map.
     *
     * @param plan     root of the {@code QueryPlan} tree
     * @param provider host-supplied provider; when {@code null}, a
     *                 {@link NullModelInfoProvider} is used (every model
     *                 maps to {@code Optional.empty()})
     * @param namespace active namespace forwarded to the provider
     * @return unmodifiable map keyed by QM model name. Values are
     *         datasource-id optionals ({@code Optional.empty()} = unknown)
     */
    public static Map<String, Optional<String>> collect(
            QueryPlan plan,
            ModelInfoProvider provider,
            String namespace) {

        ModelInfoProvider effectiveProvider =
                provider != null ? provider : new NullModelInfoProvider();

        List<BaseModelPlan> basePlans = BaseModelPlanCollector.collect(plan);
        Map<String, Optional<String>> result = new LinkedHashMap<>();

        for (BaseModelPlan bp : basePlans) {
            if (!result.containsKey(bp.model())) {
                result.put(bp.model(),
                        safeGetDatasourceId(effectiveProvider, bp.model(), namespace));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Call {@code provider.getDatasourceId}; catch and coerce errors to
     * {@code Optional.empty()} (permissive fallback).
     *
     * <p>Providers that predate F-7 may not override
     * {@code getDatasourceId} at all — the default method returns
     * {@code Optional.empty()} which is the correct fallback. The
     * catch-all here handles misbehaving providers that throw.</p>
     */
    private static Optional<String> safeGetDatasourceId(
            ModelInfoProvider provider, String modelName, String namespace) {
        try {
            return provider.getDatasourceId(modelName, namespace);
        } catch (Exception ex) {
            // Misbehaving provider — fail open (permissive) but log the
            // traceback so the host integration issue is visible.
            LOG.log(Level.WARNING,
                    "ModelInfoProvider " + provider.getClass().getSimpleName()
                            + " raised exception on getDatasourceId("
                            + modelName + ", " + namespace + ")",
                    ex);
            return Optional.empty();
        }
    }
}
