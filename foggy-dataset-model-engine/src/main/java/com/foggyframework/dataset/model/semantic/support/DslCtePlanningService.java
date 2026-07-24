package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single planning pass for DSL_CTE validation and bridge selection.
 */
public final class DslCtePlanningService {

    private DslCtePlanningService() {
    }

    public enum Kind {
        SIMPLE_DSL,
        RESULT_STAGE_WINDOW,
        RESULT_STAGE_METRIC_RATIO,
        CROSS_MODEL_FUNNEL_MONEY_ATTRIBUTION,
        CROSS_MODEL_FUNNEL_TIME_ATTRIBUTION,
        CROSS_MODEL_FUNNEL_SOURCE_RATE,
        CROSS_MODEL_JOIN_ALIGN,
        UNSUPPORTED
    }

    public static DslCtePlan plan(String fallbackModel, SemanticQueryRequest request) {
        Object executablePlan = request == null ? null : request.getExecutablePlan();
        return plan(fallbackModel, executablePlan);
    }

    public static DslCtePlan plan(String fallbackModel, Object executablePlan) {
        Map<String, Object> validation = DslCtePlanValidator.validate(executablePlan);

        DslCteDslRequestMapper.BridgeResult bridge =
                DslCteDslRequestMapper.toDslRequest(fallbackModel, executablePlan);
        if (bridge.ready()) {
            return new DslCtePlan(Kind.SIMPLE_DSL, bridge.status(), validation, bridge,
                    null, null, null, null, null, null, List.of());
        }

        DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge =
                DslCteDslRequestMapper.toResultStageWindowBridge(fallbackModel, executablePlan);
        if (resultStageBridge.ready()) {
            return new DslCtePlan(Kind.RESULT_STAGE_WINDOW, resultStageBridge.status(), validation, bridge,
                    resultStageBridge, null, null, null, null, null, List.of());
        }

        DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge =
                DslCteDslRequestMapper.toResultStageMetricRatioBridge(fallbackModel, executablePlan);
        if (metricRatioBridge.ready()) {
            return new DslCtePlan(Kind.RESULT_STAGE_METRIC_RATIO, metricRatioBridge.status(), validation, bridge,
                    resultStageBridge, metricRatioBridge, null, null, null, null, List.of());
        }

        DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge =
                DslCteDslRequestMapper.toCrossModelFunnelMoneyAttributionBridge(fallbackModel, executablePlan);
        if (moneyAttributionBridge.ready()) {
            return new DslCtePlan(Kind.CROSS_MODEL_FUNNEL_MONEY_ATTRIBUTION, moneyAttributionBridge.status(),
                    validation, bridge, resultStageBridge, metricRatioBridge, moneyAttributionBridge,
                    null, null, null, List.of());
        }
        if (moneyAttributionBridge.relevant()) {
            return new DslCtePlan(Kind.UNSUPPORTED, moneyAttributionBridge.status(), validation, bridge,
                    resultStageBridge, metricRatioBridge, moneyAttributionBridge, null, null, null,
                    safeList(moneyAttributionBridge.unsupported()));
        }

        DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge =
                DslCteDslRequestMapper.toCrossModelFunnelTimeAttributionBridge(fallbackModel, executablePlan);
        if (timeAttributionBridge.ready()) {
            return new DslCtePlan(Kind.CROSS_MODEL_FUNNEL_TIME_ATTRIBUTION, timeAttributionBridge.status(),
                    validation, bridge, resultStageBridge, metricRatioBridge, moneyAttributionBridge,
                    timeAttributionBridge, null, null, List.of());
        }
        if (timeAttributionBridge.relevant()) {
            return new DslCtePlan(Kind.UNSUPPORTED, timeAttributionBridge.status(), validation, bridge,
                    resultStageBridge, metricRatioBridge, moneyAttributionBridge, timeAttributionBridge, null, null,
                    safeList(timeAttributionBridge.unsupported()));
        }

        DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge =
                DslCteDslRequestMapper.toCrossModelFunnelSourceRateBridge(fallbackModel, executablePlan);
        if (funnelSourceRateBridge.ready()) {
            return new DslCtePlan(Kind.CROSS_MODEL_FUNNEL_SOURCE_RATE, funnelSourceRateBridge.status(),
                    validation, bridge, resultStageBridge, metricRatioBridge, moneyAttributionBridge,
                    timeAttributionBridge, funnelSourceRateBridge, null, List.of());
        }

        DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge =
                DslCteDslRequestMapper.toCrossModelJoinAlignBridge(fallbackModel, executablePlan);
        if (joinAlignBridge.ready()) {
            return new DslCtePlan(Kind.CROSS_MODEL_JOIN_ALIGN, joinAlignBridge.status(), validation, bridge,
                    resultStageBridge, metricRatioBridge, moneyAttributionBridge, timeAttributionBridge,
                    funnelSourceRateBridge, joinAlignBridge, List.of());
        }

        return new DslCtePlan(Kind.UNSUPPORTED, bridge.status(), validation, bridge, resultStageBridge,
                metricRatioBridge, moneyAttributionBridge, timeAttributionBridge, funnelSourceRateBridge,
                joinAlignBridge, combinedUnsupported(bridge, resultStageBridge, metricRatioBridge,
                moneyAttributionBridge, timeAttributionBridge, funnelSourceRateBridge, joinAlignBridge));
    }

    public record DslCtePlan(
            Kind kind,
            String status,
            Map<String, Object> validation,
            DslCteDslRequestMapper.BridgeResult simpleBridge,
            DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
            DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge,
            DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge,
            DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge,
            DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge,
            DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge,
            List<String> unsupported) {

        public DslCtePlan {
            validation = validation == null ? Map.of() : new LinkedHashMap<>(validation);
            unsupported = safeList(unsupported);
        }

        public boolean ready() {
            return kind != Kind.UNSUPPORTED && DslCteDslRequestMapper.STATUS_READY.equals(status);
        }

        public Map<String, Object> validationEvidence() {
            Map<String, Object> evidence = new LinkedHashMap<>(validation);
            evidence.put("dsl_bridge_status", status);
            switch (kind) {
                case SIMPLE_DSL -> {
                    evidence.put("dsl_bridge_model", simpleBridge.model());
                    evidence.put("dsl_request", simpleBridge.request());
                }
                case RESULT_STAGE_WINDOW -> {
                    evidence.put("dsl_bridge_model", resultStageBridge.model());
                    evidence.put("dsl_request", resultStageBridge.baseRequest());
                    evidence.put("dsl_result_stage_window", resultStageBridge.summary());
                }
                case RESULT_STAGE_METRIC_RATIO -> {
                    evidence.put("dsl_bridge_model", metricRatioBridge.model());
                    evidence.put("dsl_request", metricRatioBridge.baseRequest());
                    evidence.put("dsl_result_stage_metric_ratio", metricRatioBridge.summary());
                }
                case CROSS_MODEL_FUNNEL_MONEY_ATTRIBUTION -> appendMoneyAttributionEvidence(evidence);
                case CROSS_MODEL_FUNNEL_TIME_ATTRIBUTION -> appendTimeAttributionEvidence(evidence);
                case CROSS_MODEL_FUNNEL_SOURCE_RATE -> appendSourceRateEvidence(evidence);
                case CROSS_MODEL_JOIN_ALIGN -> appendJoinAlignEvidence(evidence);
                case UNSUPPORTED -> evidence.put("dsl_bridge_unsupported", unsupported);
            }
            return evidence;
        }

        private void appendMoneyAttributionEvidence(Map<String, Object> evidence) {
            Map<String, Object> models = new LinkedHashMap<>();
            if (moneyAttributionBridge.denominatorModel() != null) {
                models.put("denominator", moneyAttributionBridge.denominatorModel());
            }
            models.put("left", moneyAttributionBridge.leftModel());
            models.put("right", moneyAttributionBridge.rightModel());
            evidence.put("dsl_bridge_models", models);
            if (moneyAttributionBridge.denominatorRequest() != null) {
                evidence.put("dsl_denominator_request", moneyAttributionBridge.denominatorRequest());
            }
            evidence.put("dsl_left_request", moneyAttributionBridge.leftRequest());
            evidence.put("dsl_right_request", moneyAttributionBridge.rightRequest());
            evidence.put("dsl_cross_model_funnel_money_attribution", moneyAttributionBridge.summary());
        }

        private void appendTimeAttributionEvidence(Map<String, Object> evidence) {
            Map<String, Object> models = new LinkedHashMap<>();
            models.put("denominator", timeAttributionBridge.denominatorModel());
            models.put("left", timeAttributionBridge.leftModel());
            models.put("right", timeAttributionBridge.rightModel());
            evidence.put("dsl_bridge_models", models);
            evidence.put("dsl_denominator_request", timeAttributionBridge.denominatorRequest());
            evidence.put("dsl_left_request", timeAttributionBridge.leftRequest());
            evidence.put("dsl_right_request", timeAttributionBridge.rightRequest());
            evidence.put("dsl_cross_model_funnel_time_attribution", timeAttributionBridge.summary());
        }

        private void appendSourceRateEvidence(Map<String, Object> evidence) {
            Map<String, Object> models = new LinkedHashMap<>();
            models.put("denominator", funnelSourceRateBridge.denominatorModel());
            models.put("left", funnelSourceRateBridge.leftModel());
            models.put("right", funnelSourceRateBridge.rightModel());
            evidence.put("dsl_bridge_models", models);
            evidence.put("dsl_denominator_request", funnelSourceRateBridge.denominatorRequest());
            evidence.put("dsl_left_request", funnelSourceRateBridge.leftRequest());
            evidence.put("dsl_right_request", funnelSourceRateBridge.rightRequest());
            evidence.put("dsl_cross_model_funnel_source_rate", funnelSourceRateBridge.summary());
        }

        private void appendJoinAlignEvidence(Map<String, Object> evidence) {
            Map<String, Object> models = new LinkedHashMap<>();
            models.put("left", joinAlignBridge.leftModel());
            models.put("right", joinAlignBridge.rightModel());
            evidence.put("dsl_bridge_models", models);
            evidence.put("dsl_left_request", joinAlignBridge.leftRequest());
            evidence.put("dsl_right_request", joinAlignBridge.rightRequest());
            evidence.put("dsl_cross_model_join_align", joinAlignBridge.summary());
        }
    }

    private static List<String> combinedUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                    DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
                                                    DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge,
                                                    DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge,
                                                    DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge,
                                                    DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge,
                                                    DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge) {
        Set<String> unsupported = new LinkedHashSet<>();
        addAll(unsupported, bridge == null ? null : bridge.unsupported());
        addAll(unsupported, resultStageBridge == null ? null : resultStageBridge.unsupported());
        addAll(unsupported, metricRatioBridge == null ? null : metricRatioBridge.unsupported());
        addAll(unsupported, moneyAttributionBridge == null ? null : moneyAttributionBridge.unsupported());
        addAll(unsupported, timeAttributionBridge == null ? null : timeAttributionBridge.unsupported());
        addAll(unsupported, funnelSourceRateBridge == null ? null : funnelSourceRateBridge.unsupported());
        addAll(unsupported, joinAlignBridge == null ? null : joinAlignBridge.unsupported());
        return List.copyOf(unsupported);
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
