package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.authority.BaseModelPlanCollector;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.List;
import java.util.Map;

/**
 * Execute a {@link QueryPlan} tree end-to-end: compile → executeSql → rows.
 *
 * <p>This is the M7 bridge between the plan tree and the raw-SQL execution
 * primitive. Mirrors Python
 * {@code foggy.dataset_model.engine.compose.runtime.plan_execution}.</p>
 *
 * @since 8.2.0.beta
 */
public final class PlanExecution {

    private PlanExecution() { /* utility */ }

    /**
     * Compile {@code plan} to SQL via M6 {@link ComposeSqlCompiler} and
     * execute via {@link SemanticQueryServiceV3#executeSql}.
     *
     * @param plan            the root plan node
     * @param ctx             compose query context (principal + resolver)
     * @param semanticService the semantic service for compile + execute
     * @param dialect         SQL dialect string (e.g. "mysql", "sqlite")
     * @return rows as a list of column-name→value maps
     * @throws RuntimeException wrapping any execute-phase failure with
     *         message prefix "Plan execution failed at execute phase:"
     */
    public static List<Map<String, Object>> executePlan(
            QueryPlan plan,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect) {
        ComposedSql composed = ComposeSqlCompiler.compilePlanToSql(plan, ctx,
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(semanticService)
                        .dialect(dialect)
                        .build());
        String routeModel = pickRouteModel(plan);
        try {
            return semanticService.executeSql(composed.getSql(), composed.getParams(), routeModel);
        } catch (Exception exc) {
            throw new RuntimeException(
                    "Plan execution failed at execute phase: " + exc.getMessage(), exc);
        }
    }

    /**
     * Pick the first base model name for route-model resolution.
     * M7 uses this for single-datasource deployment; M8+ may enhance.
     */
    static String pickRouteModel(QueryPlan plan) {
        List<BaseModelPlan> bases = BaseModelPlanCollector.collect(plan);
        return bases.isEmpty() ? null : bases.get(0).model();
    }
}
