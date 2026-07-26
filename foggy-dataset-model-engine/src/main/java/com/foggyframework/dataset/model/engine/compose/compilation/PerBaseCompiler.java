package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.model.engine.compose.CteUnit;
import com.foggyframework.dataset.model.engine.compose.ComposeOrderByNormalizer;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.port.ComposeSqlGeneration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-BaseModelPlan SQL compilation (M6 · 6.1).
 *
 * <p>Delegates to {@link ComposeSemanticPlanningPort#generateComposeSql} —
 * the governed entry-point that already runs the {@code beforeQuery} pipeline
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
 * the existing Java semantic SQL generation path — both run
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
     * @param planningPort     narrow governed SQL planning port
     * @param namespace        namespace forwarded to semantic service;
     *                         typically {@code ComposeQueryContext.namespace()}
     * @param alias            alias for the resulting {@link CteUnit}
     *                         (caller owns numbering)
     * @param governanceCache  optional cache; {@code (model, identityOf(binding),
     *                         planHash)} → cached {@link ComposeSqlGeneration}.
     *                         Skips re-running v1.3 governance for identical
     *                         base query shapes within a single compile pass.
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
    static List<CteUnit> compileBaseModel(
            BaseModelPlan plan,
            ModelBinding binding,
            ComposeSemanticPlanningPort planningPort,
            String namespace,
            String alias,
            Map<String, ComposeSqlGeneration> governanceCache) {
        return compileBaseModel(
                plan,
                binding,
                planningPort,
                namespace,
                null,
                alias,
                governanceCache
        );
    }

    static List<CteUnit> compileBaseModel(
            BaseModelPlan plan,
            ModelBinding binding,
            ComposeSemanticPlanningPort planningPort,
            String namespace,
            SemanticRequestContext baseRequestContext,
            String alias,
            Map<String, ComposeSqlGeneration> governanceCache) {

        String cacheKey = plan.model() + ":" + System.identityHashCode(binding)
                + ":" + PlanHash.planHash(plan);
        ComposeSqlGeneration buildResult = null;
        if (governanceCache != null) {
            buildResult = governanceCache.get(cacheKey);
        }

        if (buildResult == null) {
            SemanticQueryRequest request = buildRequest(plan);
            SemanticRequestContext reqContext = buildContext(
                    namespace, baseRequestContext, binding);
            try {
                buildResult = planningPort.generateComposeSql(plan.model(), request, reqContext);
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

            if (buildResult == null) {
                throw new ComposeCompileException(
                        ComposeCompileErrorCodes.PER_BASE_COMPILE_FAILED,
                        ComposeCompileErrorCodes.PHASE_COMPILE,
                        "v1.3 engine build failed for model '" + plan.model()
                                + "': semantic planning port returned null");
            }

            if (governanceCache != null) {
                governanceCache.put(cacheKey, buildResult);
            }
        }

        List<String> selectColumns = ComposePlanner.extractStringCols(plan.columns());

        // ── CTE Wrapping: return flattened sibling CteUnits when structured stages exist ──
        if (buildResult.hasCteStages()) {
            List<CteUnit> units = new ArrayList<>();
            for (ComposeSqlGeneration.CteStage stage : buildResult.cteStages()) {
                // Prerequisite CTE: alias is scoped to the base plan (e.g., cte_0_stage1)
                String stageAlias = alias + "_" + stage.alias();
                List<Object> stageParams = new ArrayList<>();
                if (stage.params() != null) stageParams.addAll(stage.params());
                units.add(new CteUnit(stageAlias, stage.sql(), stageParams, List.of()));
            }
            // Outer SELECT: replace the original stage alias references with the scoped alias
            String outerSql = buildResult.sql();
            for (ComposeSqlGeneration.CteStage stage : buildResult.cteStages()) {
                String scopedAlias = alias + "_" + stage.alias();
                // Replace references: FROM stage1 → FROM cte_0_stage1, stage1."col" → cte_0_stage1."col"
                // Using regex \b to prevent partial substring matches (e.g. percent_stage1)
                outerSql = outerSql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(stage.alias() + "."), 
                        java.util.regex.Matcher.quoteReplacement(scopedAlias + "."));
                outerSql = outerSql.replaceAll("(?i)\\bFROM\\s+" + java.util.regex.Pattern.quote(stage.alias()) + "\\b", 
                        "FROM " + java.util.regex.Matcher.quoteReplacement(scopedAlias));
            }
            List<Object> outerParams = new ArrayList<>();
            outerParams.addAll(buildResult.params());
            units.add(new CteUnit(alias, outerSql, outerParams, selectColumns));
            return units;
        }

        // ── Legacy single-pass: return single CteUnit ──
        List<Object> params = new ArrayList<>();
        params.addAll(buildResult.params());

        return List.of(new CteUnit(
                alias,
                buildResult.sql(),
                params,
                selectColumns));
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

        if (!plan.having().isEmpty()) {
            List<SemanticQueryRequest.SliceItem> having = new ArrayList<>(plan.having().size());
            for (Object raw : plan.having()) {
                having.add(toSliceItem(raw));
            }
            req.setHaving(having);
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

        if (!plan.calculatedFields().isEmpty()) {
            req.setCalculatedFields(new ArrayList<>(plan.calculatedFields()));
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

    /** Order-by entry accepts the same shorthand strings as query_model. */
    private static SemanticQueryRequest.OrderItem toOrderItem(String entry) {
        return ComposeOrderByNormalizer.toOrderItem(entry);
    }

    /** Build a {@link SemanticRequestContext} that carries the binding's
     *  three-field governance. A {@code null} namespace falls back to
     *  whatever {@link SemanticRequestContext#empty()} does at the host
     *  level. */
    private static SemanticRequestContext buildContext(
            String namespace,
            SemanticRequestContext baseRequestContext,
            ModelBinding binding
    ) {
        Set<String> fieldAccess = binding.fieldAccess() == null
                ? null
                : new HashSet<>(binding.fieldAccess());
        SemanticRequestContext base = baseRequestContext != null
                ? baseRequestContext
                : SemanticRequestContext.ofNamespace(namespace);
        return base.withGovernance(
                fieldAccess,
                new ArrayList<>(binding.deniedColumns()),
                binding.systemSlice().isEmpty() ? null : new ArrayList<>(binding.systemSlice()));
    }
}
