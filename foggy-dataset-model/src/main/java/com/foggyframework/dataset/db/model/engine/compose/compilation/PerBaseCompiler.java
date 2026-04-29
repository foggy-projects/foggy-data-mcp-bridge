package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.CteUnit;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-BaseModelPlan SQL compilation (M6 · 6.1).
 *
 * <p>Delegates to {@link SemanticQueryServiceV3#generateSql} — the v1.3
 * public entry-point that already runs the {@code beforeQuery} pipeline
 * (physical-column permission, field-access whitelist, system-slice
 * injection) and returns {@code (sql, params)} without executing. Binding's
 * {@code fieldAccess} / {@code systemSlice} / {@code deniedColumns} are
 * injected into {@link SemanticRequestContext} so v1.3 column + slice
 * governance applies automatically.</p>
 *
 * <p>Derived plans do NOT re-enter this function —
 * {@link ComposePlanner} lowers them via
 * {@code SELECT ... FROM (<source_sql>) AS <alias>} string templating;
 * only {@link BaseModelPlan} instances reach the v1.3 engine.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.per_base.compile_base_model}.
 * Python's new public method {@code build_query_with_governance} maps to
 * the existing Java {@link SemanticQueryServiceV3#generateSql} — both run
 * {@code getModel → applyGovernance → validate → buildSql} internally. No
 * new Java public method was needed (decision recorded in progress.md
 * 2026-04-22).</p>
 *
 * @since 8.2.0.beta
 */
final class PerBaseCompiler {

    private PerBaseCompiler() { /* utility */ }

    /**
     * Compile one {@link BaseModelPlan} against its {@link ModelBinding}.
     *
     * @param plan             plan whose {@code .model()} keys the binding lookup
     * @param binding          authority binding injected into the per-base request
     * @param semanticService  v1.3 service offering {@code generateSql}
     * @param namespace        namespace forwarded to semantic service;
     *                         typically {@code ComposeQueryContext.namespace()}
     * @param alias            alias for the resulting {@link CteUnit}
     *                         (caller owns numbering)
     * @param governanceCache  optional cache; {@code (model, identityOf(binding))}
     *                         → cached {@link SqlGenerationResult}. Skips
     *                         re-running v1.3 governance for self-join /
     *                         self-union cases within a single compile pass.
     * @return {@link CteUnit} carrying {@code alias}, {@code sql}, {@code params}
     *         and the plan's declared {@code columns} as
     *         {@code selectColumns}.
     * @throws ComposeCompileException
     *         - {@link ComposeCompileErrorCodes#MISSING_BINDING}
     *           (phase={@code plan-lower}) when the v1.3 service reports the
     *           QM is not registered (message prefix-based recognition).
     *         - {@link ComposeCompileErrorCodes#PER_BASE_COMPILE_FAILED}
     *           (phase={@code compile}) when v1.3 rejects the request or
     *           raises during SQL build. Original exception preserved on
     *           {@code cause}.
     */
    static CteUnit compileBaseModel(
            BaseModelPlan plan,
            ModelBinding binding,
            SemanticQueryServiceV3 semanticService,
            String namespace,
            String alias,
            Map<String, SqlGenerationResult> governanceCache) {

        String cacheKey = plan.model() + ":" + System.identityHashCode(binding);
        SqlGenerationResult buildResult = null;
        if (governanceCache != null) {
            buildResult = governanceCache.get(cacheKey);
        }

        if (buildResult == null) {
            SemanticQueryRequest request = buildRequest(plan);
            SemanticRequestContext reqContext = buildContext(namespace, binding);
            try {
                buildResult = semanticService.generateSql(plan.model(), request, reqContext);
            } catch (RuntimeException ex) {
                if (isModelNotFound(ex)) {
                    throw new ComposeCompileException(
                            ComposeCompileErrorCodes.MISSING_BINDING,
                            ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                            "QM '" + plan.model() + "' not registered with semantic service; "
                                    + "cannot compile BaseModelPlan (ensure the model was registered "
                                    + "before compilePlanToSql)",
                            ex);
                }
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED,
                        ComposeCompileErrorCodes.PHASE_COMPILE,
                        "v1.3 engine build failed for model '" + plan.model() + "': "
                                + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                        ex);
            }

            if (governanceCache != null) {
                governanceCache.put(cacheKey, buildResult);
            }
        }

        List<Object> params = new ArrayList<>();
        if (buildResult.getParams() != null) {
            params.addAll(buildResult.getParams());
        }

        return new CteUnit(
                alias,
                buildResult.getSql(),
                params,
                ComposePlanner.extractStringCols(plan.columns()));
    }

    /** Recognise the "Model not found" branch of v1.3 / semantic-service
     *  exceptions so we can reclassify it as {@link ComposeCompileErrorCodes#MISSING_BINDING}
     *  at the {@code plan-lower} phase. Matches both the Java
     *  {@code "模型不存在"} and the Python-aligned
     *  {@code "Model not found"} phrasings. */
    private static boolean isModelNotFound(Throwable ex) {
        String msg = ex.getMessage();
        return msg != null
                && (msg.contains("Model not found") || msg.contains("模型不存在"));
    }

    // ------------------------------------------------------------------
    // Request / context assembly
    // ------------------------------------------------------------------

    /** Assemble the {@link SemanticQueryRequest} from a {@link BaseModelPlan}.
     *  Plan-level slice dicts map to {@link SemanticQueryRequest.SliceItem};
     *  plain-string {@code groupBy} / {@code orderBy} map to the richer
     *  v1.3 item types. */
    @SuppressWarnings("unchecked")
    private static SemanticQueryRequest buildRequest(BaseModelPlan plan) {
        SemanticQueryRequest req = new SemanticQueryRequest();
        req.setColumns(ComposePlanner.extractStringCols(plan.columns()));

        if (!plan.slice().isEmpty()) {
            List<SemanticQueryRequest.SliceItem> slice = new ArrayList<>(plan.slice().size());
            for (Object raw : plan.slice()) {
                slice.add(toSliceItem(raw));
            }
            req.setSlice(slice);
        }

        if (!plan.groupBy().isEmpty()) {
            List<SemanticQueryRequest.GroupByItem> gb = new ArrayList<>(plan.groupBy().size());
            for (String field : plan.groupBy()) {
                gb.add(new SemanticQueryRequest.GroupByItem(field, null));
            }
            req.setGroupBy(gb);
        }

        if (!plan.orderBy().isEmpty()) {
            List<SemanticQueryRequest.OrderItem> ob = new ArrayList<>(plan.orderBy().size());
            for (String entry : plan.orderBy()) {
                ob.add(toOrderItem(entry));
            }
            req.setOrderBy(ob);
        }

        req.setStart(plan.start() == null ? 0 : plan.start());
        req.setLimit(plan.limit());
        req.setDistinct(plan.distinct());
        return req;
    }

    /** Plan-level slice → v1.3 {@link SemanticQueryRequest.SliceItem}.
     *  Accepted shapes are documented on {@link SliceShape#parse(Object)}. */
    private static SemanticQueryRequest.SliceItem toSliceItem(Object raw) {
        SliceShape s = SliceShape.parse(raw);
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(s.field);
        item.setOp(s.op);
        item.setValue(s.value);
        return item;
    }

    /** Order-by entry accepts {@code "name"} / {@code "name:desc"}. */
    private static SemanticQueryRequest.OrderItem toOrderItem(String entry) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        if (entry.contains(":")) {
            int idx = entry.indexOf(':');
            String name = entry.substring(0, idx).trim();
            String direction = entry.substring(idx + 1).trim().toLowerCase(java.util.Locale.ROOT);
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                direction = "asc";
            }
            item.setField(name);
            item.setDir(direction);
        } else {
            item.setField(entry);
            item.setDir("asc");
        }
        return item;
    }

    /** Build a {@link SemanticRequestContext} that carries the binding's
     *  three-field governance. A {@code null} namespace falls back to
     *  whatever {@link SemanticRequestContext#empty()} does at the host
     *  level. */
    private static SemanticRequestContext buildContext(String namespace, ModelBinding binding) {
        Set<String> fieldAccess = binding.fieldAccess() == null
                ? null
                : new HashSet<>(binding.fieldAccess());
        return SemanticRequestContext.of(
                namespace,
                /* securityContext */ null,
                fieldAccess,
                new ArrayList<>(binding.deniedColumns()),
                binding.systemSlice().isEmpty() ? null : new ArrayList<>(binding.systemSlice()));
    }
}
