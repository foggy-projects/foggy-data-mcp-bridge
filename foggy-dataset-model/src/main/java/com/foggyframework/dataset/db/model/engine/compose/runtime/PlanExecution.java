package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.authority.BaseModelPlanCollector;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlExecutionPort;
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
        return executePlan(plan, ctx, semanticService, dialect, false);
    }

    public static List<Map<String, Object>> executePlan(
            QueryPlan plan,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect,
            boolean normalizePlan) {
        return executePlan(
                plan, ctx, semanticService, semanticService, dialect, normalizePlan);
    }

    /** Execute using independently supplied planning and raw-SQL capabilities. */
    public static List<Map<String, Object>> executePlan(
            QueryPlan plan,
            ComposeQueryContext ctx,
            ComposeSemanticPlanningPort planningPort,
            ComposeSqlExecutionPort executionPort,
            String dialect) {
        return executePlan(plan, ctx, planningPort, executionPort, dialect, false);
    }

    /** Execute using independently supplied planning and raw-SQL capabilities. */
    public static List<Map<String, Object>> executePlan(
            QueryPlan plan,
            ComposeQueryContext ctx,
            ComposeSemanticPlanningPort planningPort,
            ComposeSqlExecutionPort executionPort,
            String dialect,
            boolean normalizePlan) {
        ComposedSql composed = ComposeSqlCompiler.compilePlanToSql(plan, ctx,
                ComposeSqlCompiler.CompileOptions.builder()
                        .planningPort(planningPort)
                        .dialect(dialect)
                        .normalizePlan(normalizePlan)
                        .build());
        String routeModel = pickRouteModel(plan);
        try {
            return executionPort.executeComposeSql(
                    composed.getSql(), composed.getParams(), routeModel);
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
