package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 题库执行报告的结构化摘要。
 */
final class AiTestReportSummary {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private AiTestReportSummary() {
    }

    static Map<String, Object> build(List<SpringAiTestExecutor.AiTestResult> results) {
        List<SpringAiTestExecutor.AiTestResult> safeResults = results == null ? List.of() : results;
        List<Map<String, Object>> cases = new ArrayList<>();
        List<Map<String, Object>> clarifyObservability = new ArrayList<>();
        Set<String> domains = new LinkedHashSet<>();
        Set<String> riskTypes = new LinkedHashSet<>();
        Set<String> ownerRules = new LinkedHashSet<>();
        Set<String> missingSlots = new LinkedHashSet<>();

        for (SpringAiTestExecutor.AiTestResult result : safeResults) {
            Map<String, Object> caseSummary = summarizeCase(result);
            cases.add(caseSummary);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> observations =
                    (List<Map<String, Object>>) caseSummary.get("clarifyObservability");
            clarifyObservability.addAll(observations);
            for (Map<String, Object> observation : observations) {
                addStringValues(domains, observation.get("domains"));
                addStringValues(riskTypes, observation.get("riskTypes"));
                addStringValues(ownerRules, observation.get("ownerRules"));
                addStringValues(missingSlots, observation.get("missingSlots"));
            }
        }

        long passed = safeResults.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("resultCount", safeResults.size());
        summary.put("passedCount", passed);
        summary.put("failedCount", safeResults.size() - passed);
        summary.put("models", summarizeModels(safeResults));
        summary.put("clarify", summarizeClarify(clarifyObservability, domains, riskTypes, ownerRules, missingSlots));
        summary.put("cases", cases);
        return summary;
    }

    static List<Map<String, Object>> clarifyObservability(SpringAiTestExecutor.AiTestResult result) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, Object>> observations = new ArrayList<>();
        if (result.getToolResult() != null) {
            extractClarifyObservation(result.getToolResult(), "toolResult", null, null)
                    .map(observation -> withResultContext(observation, result))
                    .ifPresent(observations::add);
        }
        if (result.getToolCallRecords() != null) {
            for (ToolCallCollector.ToolCallRecord record : result.getToolCallRecords()) {
                if (record.getResult() == null || record.getResult() == result.getToolResult()) {
                    continue;
                }
                String source = "toolCall#" + record.getSequence();
                extractClarifyObservation(record.getResult(), source, record.getToolName(), record.getSpringToolName())
                        .map(observation -> withResultContext(observation, result))
                        .ifPresent(observations::add);
            }
        }
        return List.copyOf(observations);
    }

    private static Map<String, Object> summarizeCase(SpringAiTestExecutor.AiTestResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("testCaseId", result.getTestCaseId());
        summary.put("provider", result.getProvider());
        summary.put("modelName", result.getModelName());
        summary.put("success", result.isSuccess());
        summary.put("durationMs", result.getDurationMs());
        summary.put("question", result.getQuestion());
        summary.put("errorMessage", result.getErrorMessage());
        summary.put("calledTools", result.getCalledToolNames());
        summary.put("clarifyObservability", clarifyObservability(result));
        if (result.getValidationResult() != null) {
            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("passed", result.getValidationResult().isPassed());
            validation.put("errors", result.getValidationResult().getErrors());
            validation.put("failedRules", result.getValidationResult().getFailedRules());
            summary.put("validation", validation);
        }
        return summary;
    }

    private static Map<String, Object> withResultContext(Map<String, Object> observation,
                                                         SpringAiTestExecutor.AiTestResult result) {
        Map<String, Object> contextual = new LinkedHashMap<>();
        contextual.put("testCaseId", result.getTestCaseId());
        contextual.put("provider", result.getProvider());
        contextual.put("modelName", result.getModelName());
        contextual.putAll(observation);
        return contextual;
    }

    private static List<Map<String, Object>> summarizeModels(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(
                        result -> result.getProvider() + "/" + result.getModelName(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<SpringAiTestExecutor.AiTestResult> modelResults = entry.getValue();
                    long passed = modelResults.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
                    double avgDuration = modelResults.stream()
                            .mapToLong(SpringAiTestExecutor.AiTestResult::getDurationMs)
                            .average()
                            .orElse(0);
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("model", entry.getKey());
                    summary.put("resultCount", modelResults.size());
                    summary.put("passedCount", passed);
                    summary.put("failedCount", modelResults.size() - passed);
                    summary.put("successRate", modelResults.isEmpty() ? 0 : passed * 100.0 / modelResults.size());
                    summary.put("avgDurationMs", avgDuration);
                    return summary;
                })
                .toList();
    }

    private static Map<String, Object> summarizeClarify(List<Map<String, Object>> observations,
                                                        Set<String> domains,
                                                        Set<String> riskTypes,
                                                        Set<String> ownerRules,
                                                        Set<String> missingSlots) {
        Set<String> clarifyCaseIds = new LinkedHashSet<>();
        for (Map<String, Object> observation : observations) {
            Object testCaseId = observation.get("testCaseId");
            if (testCaseId != null) {
                clarifyCaseIds.add(String.valueOf(testCaseId));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", clarifyCaseIds.size());
        summary.put("observationCount", observations.size());
        summary.put("domainCount", domains.size());
        summary.put("riskTypeCount", riskTypes.size());
        summary.put("ownerRuleCount", ownerRules.size());
        summary.put("missingSlotCount", missingSlots.size());
        summary.put("domains", List.copyOf(domains));
        summary.put("riskTypes", List.copyOf(riskTypes));
        summary.put("ownerRules", List.copyOf(ownerRules));
        summary.put("missingSlots", List.copyOf(missingSlots));
        summary.put("observations", observations);
        return summary;
    }

    private static Optional<Map<String, Object>> extractClarifyObservation(Object rawResult, String source,
                                                                           String toolName, String springToolName) {
        return normalizeResult(rawResult)
                .flatMap(AiTestReportSummary::findClarifyPayload)
                .map(payload -> {
                    Map<String, Object> detail = asMap(payload.get("detail")).orElse(payload);
                    List<Map<String, Object>> templateMatches = templateMatches(detail.get("clarify_template_matches"));
                    Map<String, Object> observation = new LinkedHashMap<>();
                    observation.put("source", source);
                    observation.put("toolName", toolName);
                    observation.put("springToolName", springToolName);
                    observation.put("type", stringValue(payload.get("type")));
                    observation.put("code", stringValue(payload.get("code")));
                    observation.put("terminalRoute", stringValue(detail.get("terminal_route")));
                    observation.put("domains", matchValues(templateMatches, "domain"));
                    observation.put("riskTypes", matchValues(templateMatches, "riskType"));
                    observation.put("ownerRules", matchValues(templateMatches, "ownerRule"));
                    observation.put("missingSlots", stringValues(firstNonNull(
                            detail.get("clarify_missing_slots"), payload.get("missing"))));
                    observation.put("templateMatches", templateMatches);
                    return observation;
                });
    }

    private static Optional<Map<String, Object>> findClarifyPayload(Map<String, Object> payload) {
        if (hasClarifySignals(payload)) {
            return Optional.of(payload);
        }
        Object data = payload.get("data");
        if (data != null) {
            Optional<Map<String, Object>> nested = normalizeResult(data).flatMap(AiTestReportSummary::findClarifyPayload);
            if (nested.isPresent()) {
                return nested;
            }
        }
        Object result = payload.get("result");
        if (result != null) {
            return normalizeResult(result).flatMap(AiTestReportSummary::findClarifyPayload);
        }
        return Optional.empty();
    }

    private static boolean hasClarifySignals(Map<String, Object> payload) {
        Map<String, Object> detail = asMap(payload.get("detail")).orElse(Map.of());
        return "clarify".equals(payload.get("type"))
                || "ROUTING_TERMINAL_CLARIFY".equals(payload.get("code"))
                || "CLARIFY".equals(detail.get("terminal_route"))
                || detail.containsKey("clarify_template_matches")
                || detail.containsKey("clarify_missing_slots");
    }

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> normalizeResult(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
            return Optional.of(normalized);
        }
        try {
            return Optional.of(OBJECT_MAPPER.convertValue(value, MAP_TYPE));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Map<String, Object>> asMap(Object value) {
        return normalizeResult(value);
    }

    private static List<Map<String, Object>> templateMatches(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object item : list) {
            asMap(item).ifPresent(matches::add);
        }
        return List.copyOf(matches);
    }

    private static List<String> matchValues(List<Map<String, Object>> matches, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> match : matches) {
            Object value = match.get(fieldName);
            if (value != null && !String.valueOf(value).isBlank()) {
                values.add(String.valueOf(value));
            }
        }
        return List.copyOf(values);
    }

    private static List<String> stringValues(Object value) {
        if (value instanceof List<?> list) {
            Set<String> values = new LinkedHashSet<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    values.add(String.valueOf(item));
                }
            }
            return List.copyOf(values);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    private static void addStringValues(Set<String> target, Object value) {
        target.addAll(stringValues(value));
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
