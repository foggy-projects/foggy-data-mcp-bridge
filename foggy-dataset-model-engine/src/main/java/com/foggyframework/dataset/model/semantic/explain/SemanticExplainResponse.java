package com.foggyframework.dataset.model.semantic.explain;

import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

import java.util.List;
import java.util.Map;

/**
 * Versioned public contract for semantic definition and recompilation evidence.
 */
public record SemanticExplainResponse(
        String schemaVersion,
        Basis basis,
        DefinitionTrace definitionTrace,
        CompilationTrace compilationTrace,
        SecurityTrace securityTrace,
        MaterializationTrace materializationTrace,
        ExecutionTrace executionTrace,
        SqlTrace sqlTrace,
        List<Limitation> limitations
) {
    public static final String SCHEMA_VERSION = "foggy-semantic-explain/v1";

    public enum Basis {
        DEFINITION,
        RECOMPILED,
        EXECUTED_TRACE
    }

    public enum Confidence {
        EXACT,
        DECLARED,
        OBSERVED,
        OPAQUE
    }

    public enum ConditionOrigin {
        USER_SLICE,
        SYSTEM_SLICE,
        MODEL_PERMISSION,
        MEMBER_PERMISSION,
        ACCESS_BUILDER,
        COMPOSE_PROPAGATION,
        UNKNOWN
    }

    public enum StageStatus {
        EVALUATED,
        NOT_EVALUATED,
        NOT_APPLICABLE
    }

    public record DefinitionTrace(
            String queryModel,
            ModelTrace model,
            List<FieldTrace> fields,
            List<JoinTrace> joins
    ) {
    }

    public record ModelTrace(
            String name,
            String kind,
            String namespace,
            String revision,
            String source,
            List<ModelDependency> dependencies
    ) {
    }

    public record ModelDependency(String kind, String name) {
    }

    public record FieldTrace(
            String queryField,
            String tableModel,
            String tableField,
            String fieldType,
            String dataType,
            String aggregationFormula,
            List<String> references,
            List<LineageEdge> lineage
    ) {
    }

    public record LineageEdge(
            String from,
            String to,
            String relation,
            String column,
            String expression,
            Confidence confidence,
            String reasonCode
    ) {
    }

    public record JoinTrace(
            String fromModel,
            String toModel,
            String joinType,
            String foreignKey,
            Confidence confidence,
            String reasonCode
    ) {
    }

    public record CompilationTrace(
            SemanticQueryRequest originalDsl,
            DbQueryRequestDef normalizedDsl,
            List<ConditionTrace> normalizedConditions,
            List<FieldResolution> fieldResolution,
            QueryStageTrace stagePlan,
            List<ExplainTraceCollector.Event> events
    ) {
    }

    public record FieldResolution(
            String requested,
            String resolved,
            String tableModel,
            String tableField,
            Confidence confidence
    ) {
    }

    public record QueryStageTrace(
            String version,
            String dialect,
            String renderStrategy,
            String returnTotalStrategy,
            String preAggOptimizationPolicy,
            List<StageTrace> stages,
            List<String> fallbacks,
            List<String> unsupported
    ) {
    }

    public record StageTrace(
            String id,
            String type,
            String sqlAlias,
            List<String> inputAliases,
            List<String> outputAliases,
            List<String> filterAliases,
            List<String> orderAliases,
            boolean requiresSqlBoundary,
            int parameterCount
    ) {
    }

    public record SecurityTrace(
            StageStatus status,
            String policyId,
            String policyVersion,
            boolean publicDecision,
            String effect,
            List<ConditionTrace> conditions,
            List<String> affectedFields,
            boolean affectsFieldVisibility,
            boolean affectsJoin,
            boolean affectsPreAggregation
    ) {
    }

    public record ConditionTrace(
            String path,
            String field,
            String operator,
            String valueType,
            String redactedValue,
            ConditionOrigin origin,
            Confidence confidence
    ) {
    }

    public record MaterializationTrace(
            List<MaterializationDefinition> definitions,
            StageStatus status,
            String route,
            String preAggregation,
            String decision,
            String reasonCode
    ) {
    }

    public record MaterializationDefinition(
            String tableModel,
            String name,
            String physicalRelation,
            String buildMode,
            List<String> dimensions,
            Map<String, String> granularities,
            Map<String, String> measures,
            Confidence confidence
    ) {
    }

    public record ExecutionTrace(
            StageStatus l1Cache,
            StageStatus l2Cache,
            StageStatus jdbc,
            String route
    ) {
    }

    public record SqlTrace(
            boolean exposed,
            String logicalSql,
            String finalPhysicalSql,
            String sourceOfTruth,
            List<ParameterTrace> parameters,
            Confidence confidence,
            String reasonCode
    ) {
    }

    public record ParameterTrace(
            int index,
            String valueType,
            String redactedValue,
            ConditionOrigin origin,
            Confidence confidence
    ) {
    }

    public record Limitation(String code, String message, Confidence confidence) {
    }
}
