package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        List<Map<String, Object>> toolBusinessErrors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

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
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> caseToolBusinessErrors =
                    (List<Map<String, Object>>) caseSummary.get("toolBusinessErrors");
            toolBusinessErrors.addAll(caseToolBusinessErrors);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> caseWarnings =
                    (List<Map<String, Object>>) caseSummary.get("warnings");
            warnings.addAll(caseWarnings);
        }
        List<Map<String, Object>> caseComparison = summarizeCaseComparison(safeResults);
        warnings.addAll(queryPayloadShapeWarnings(caseComparison));

        long passed = safeResults.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("resultCount", safeResults.size());
        summary.put("passedCount", passed);
        summary.put("failedCount", safeResults.size() - passed);
        summary.put("models", summarizeModels(safeResults));
        summary.put("clarify", summarizeClarify(clarifyObservability, domains, riskTypes, ownerRules, missingSlots));
        summary.put("toolBusinessErrorCount", toolBusinessErrors.size());
        summary.put("toolBusinessErrorCaseCount", toolBusinessErrors.stream()
                .map(error -> stringValue(error.get("testCaseId")))
                .filter(testCaseId -> testCaseId != null && !testCaseId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size());
        summary.put("toolBusinessErrors", toolBusinessErrors);
        summary.put("warningCount", warnings.size());
        summary.put("warningCaseCount", warningCaseCountFromWarnings(warnings));
        summary.put("toolBusinessErrorWarningCount", toolBusinessErrors.size());
        summary.put("warningCategories", summarizeWarningCategories(warnings));
        summary.put("warnings", warnings);
        summary.put("failureCategories", summarizeFailureCategories(safeResults));
        summary.put("caseComparison", caseComparison);
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

    static List<Map<String, Object>> toolBusinessErrors(SpringAiTestExecutor.AiTestResult result) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, Object>> errors = new ArrayList<>();
        if (result.getToolResult() != null) {
            extractToolBusinessError(result.getToolResult(), "toolResult", null, null, null, null)
                    .map(error -> withResultContext(error, result))
                    .ifPresent(errors::add);
        }
        if (result.getToolCallRecords() != null) {
            for (ToolCallCollector.ToolCallRecord record : result.getToolCallRecords()) {
                if (record.getResult() == null || record.getResult() == result.getToolResult()) {
                    continue;
                }
                String source = "toolCall#" + record.getSequence();
                extractToolBusinessError(record.getResult(), source, record.getToolName(), record.getSpringToolName(),
                                record.getArguments(), record)
                        .map(error -> withResultContext(error, result))
                        .ifPresent(errors::add);
            }
        }
        return List.copyOf(errors);
    }

    private static Map<String, Object> summarizeCase(SpringAiTestExecutor.AiTestResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> toolBusinessErrors = toolBusinessErrors(result);
        List<Map<String, Object>> warnings = warnings(result, toolBusinessErrors);
        List<Map<String, Object>> queryPayloads = queryPayloads(result);
        summary.put("testCaseId", result.getTestCaseId());
        summary.put("provider", result.getProvider());
        summary.put("modelName", result.getModelName());
        summary.put("success", result.isSuccess());
        summary.put("durationMs", result.getDurationMs());
        summary.put("question", result.getQuestion());
        summary.put("errorMessage", result.getErrorMessage());
        summary.put("errorCategory", errorCategory(result));
        summary.put("calledTools", result.getCalledToolNames());
        summary.put("clarifyObservability", clarifyObservability(result));
        summary.put("toolBusinessErrorCount", toolBusinessErrors.size());
        summary.put("toolBusinessErrors", toolBusinessErrors);
        summary.put("queryPayloadCount", queryPayloads.size());
        summary.put("queryPayloads", queryPayloads);
        summary.put("warningCount", warnings.size());
        summary.put("warnings", warnings);
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
                    summary.put("failureCategories", summarizeFailureCategories(modelResults));
                    summary.put("clarifyCaseCount", clarifyCaseCount(modelResults));
                    summary.put("toolBusinessErrorCaseCount", toolBusinessErrorCaseCount(modelResults));
                    summary.put("toolBusinessErrorCount", toolBusinessErrorCount(modelResults));
                    summary.put("warningCaseCount", warningCaseCount(modelResults));
                    summary.put("warningCount", warningCount(modelResults));
                    summary.put("warningRate", modelResults.isEmpty()
                            ? 0
                            : warningCaseCount(modelResults) * 100.0 / modelResults.size());
                    summary.put("toolBusinessErrorWarningCount", toolBusinessErrorCount(modelResults));
                    return summary;
                })
                .toList();
    }

    private static List<Map<String, Object>> summarizeCaseComparison(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(
                        AiTestReportSummary::caseKey,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<SpringAiTestExecutor.AiTestResult> caseResults = entry.getValue();
                    long passed = caseResults.stream().filter(SpringAiTestExecutor.AiTestResult::isSuccess).count();
                    List<Map<String, Object>> queryPayloads = caseResults.stream()
                            .flatMap(result -> queryPayloads(result).stream())
                            .toList();
                    List<Map<String, Object>> queryPayloadShapeSignatures =
                            summarizeQueryPayloadShapeSignatures(queryPayloads);
                    List<Map<String, Object>> queryPayloadSemanticSignatures =
                            summarizeQueryPayloadSemanticSignatures(queryPayloads);
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("testCaseId", entry.getKey());
                    summary.put("question", firstQuestion(caseResults));
                    summary.put("resultCount", caseResults.size());
                    summary.put("passedCount", passed);
                    summary.put("failedCount", caseResults.size() - passed);
                    summary.put("consensus", consensus(caseResults.size(), passed));
                    summary.put("queryPayloadCount", queryPayloads.size());
                    summary.put("queryPayloadShapeConsensus",
                            queryPayloadShapeConsensus(queryPayloads.size(), queryPayloadShapeSignatures.size()));
                    summary.put("queryPayloadShapeSignatureCount", queryPayloadShapeSignatures.size());
                    summary.put("queryPayloadShapeSignatures", queryPayloadShapeSignatures);
                    summary.put("queryPayloadSemanticSignatureCount", queryPayloadSemanticSignatures.size());
                    summary.put("queryPayloadSemanticSignatures", queryPayloadSemanticSignatures);
                    summary.put("queryPayloadShapeDivergenceClass",
                            queryPayloadShapeDivergenceClass(queryPayloads.size(),
                                    queryPayloadShapeSignatures.size(),
                                    queryPayloadSemanticSignatures.size()));
                    summary.put("models", caseResults.stream()
                            .map(AiTestReportSummary::summarizeCaseModelResult)
                            .toList());
                    return summary;
                })
                .toList();
    }

    private static Map<String, Object> summarizeCaseModelResult(SpringAiTestExecutor.AiTestResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("model", result.getProvider() + "/" + result.getModelName());
        summary.put("provider", result.getProvider());
        summary.put("modelName", result.getModelName());
        summary.put("success", result.isSuccess());
        summary.put("durationMs", result.getDurationMs());
        summary.put("errorCategory", errorCategory(result));
        summary.put("errorMessage", result.getErrorMessage());
        summary.put("calledTools", result.getCalledToolNames());
        summary.put("clarifyObservationCount", clarifyObservability(result).size());
        summary.put("toolBusinessErrorCount", toolBusinessErrors(result).size());
        List<Map<String, Object>> queryPayloads = queryPayloads(result);
        summary.put("queryPayloadCount", queryPayloads.size());
        summary.put("queryPayloads", queryPayloads);
        summary.put("warningCount", warnings(result).size());
        if (result.getValidationResult() != null) {
            summary.put("failedRules", result.getValidationResult().getFailedRules());
            summary.put("validationErrors", result.getValidationResult().getErrors());
        }
        return summary;
    }

    private static List<Map<String, Object>> queryPayloads(SpringAiTestExecutor.AiTestResult result) {
        if (result == null || result.getToolCallRecords() == null || result.getToolCallRecords().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (ToolCallCollector.ToolCallRecord record : result.getToolCallRecords()) {
            if (!isQueryModelTool(record.getToolName(), record.getSpringToolName())) {
                continue;
            }
            queryPayload(record).map(payload -> withResultContext(payload, result)).ifPresent(payloads::add);
        }
        return List.copyOf(payloads);
    }

    private static Optional<Map<String, Object>> queryPayload(ToolCallCollector.ToolCallRecord record) {
        Map<String, Object> arguments = record.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> payload = queryPayloadBody(arguments).orElse(arguments);
        Map<String, Object> signatureShape = new LinkedHashMap<>();
        signatureShape.put("argumentModel", argumentValue(arguments,
                "model", "modelName", "queryModel", "queryModelName", "qm", "qmCode"));
        signatureShape.put("mode", stringValue(firstNonNull(arguments.get("mode"), payload.get("mode"))));
        signatureShape.put("columns", fieldValues(payload.get("columns")));
        signatureShape.put("slice", conditionValues(firstNonNull(
                firstNonNull(payload.get("slice"), payload.get("where")),
                firstNonNull(payload.get("filter"), payload.get("filters")))));
        signatureShape.put("having", conditionValues(payload.get("having")));
        signatureShape.put("groupBy", fieldValues(payload.get("groupBy")));
        signatureShape.put("orderBy", orderValues(payload.get("orderBy")));
        signatureShape.put("limit", compactValue(payload.get("limit")));
        signatureShape.put("offset", compactValue(payload.get("offset")));
        Map<String, Object> semanticShape = queryPayloadSemanticShape(signatureShape);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("source", "toolCall#" + record.getSequence());
        summary.put("toolName", record.getToolName());
        summary.put("springToolName", record.getSpringToolName());
        summary.put("sequence", record.getSequence());
        summary.put("argumentModel", signatureShape.get("argumentModel"));
        summary.put("mode", signatureShape.get("mode"));
        summary.put("columns", signatureShape.get("columns"));
        summary.put("sliceFields", conditionFields(signatureShape.get("slice")));
        summary.put("sliceOps", conditionOps(signatureShape.get("slice")));
        summary.put("sliceConditions", signatureShape.get("slice"));
        summary.put("havingFields", conditionFields(signatureShape.get("having")));
        summary.put("havingOps", conditionOps(signatureShape.get("having")));
        summary.put("havingConditions", signatureShape.get("having"));
        summary.put("groupBy", signatureShape.get("groupBy"));
        summary.put("orderBy", signatureShape.get("orderBy"));
        summary.put("limit", signatureShape.get("limit"));
        summary.put("offset", signatureShape.get("offset"));
        summary.put("signature", stableJson(signatureShape));
        summary.put("semanticSignature", stableJson(semanticShape));
        return Optional.of(summary);
    }

    private static Optional<Map<String, Object>> queryPayloadBody(Map<String, Object> arguments) {
        Object value = firstNonNull(
                firstNonNull(arguments.get("payload"), arguments.get("query")),
                firstNonNull(arguments.get("dsl"), arguments.get("body")));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String string) {
            try {
                return Optional.of(OBJECT_MAPPER.readValue(string, MAP_TYPE));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return normalizeResult(value);
    }

    private static List<Map<String, Object>> summarizeQueryPayloadShapeSignatures(
            List<Map<String, Object>> queryPayloads) {
        Map<String, Map<String, Object>> summariesBySignature = new LinkedHashMap<>();
        for (Map<String, Object> payload : queryPayloads) {
            String signature = stringValue(payload.get("signature"));
            if (signature == null || signature.isBlank()) {
                continue;
            }
            Map<String, Object> summary = summariesBySignature.computeIfAbsent(signature, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("signature", key);
                item.put("count", 0);
                item.put("models", new ArrayList<String>());
                item.put("sliceFields", payload.get("sliceFields"));
                item.put("havingFields", payload.get("havingFields"));
                item.put("groupBy", payload.get("groupBy"));
                item.put("orderBy", payload.get("orderBy"));
                item.put("limit", payload.get("limit"));
                return item;
            });
            summary.put("count", ((Integer) summary.get("count")) + 1);
            @SuppressWarnings("unchecked")
            List<String> models = (List<String>) summary.get("models");
            String model = stringValue(payload.get("provider")) + "/" + stringValue(payload.get("modelName"));
            if (!models.contains(model)) {
                models.add(model);
            }
        }
        return summariesBySignature.values().stream()
                .peek(item -> item.put("models", List.copyOf(castStringList(item.get("models")))))
                .toList();
    }

    private static List<Map<String, Object>> summarizeQueryPayloadSemanticSignatures(
            List<Map<String, Object>> queryPayloads) {
        Map<String, Map<String, Object>> summariesBySignature = new LinkedHashMap<>();
        for (Map<String, Object> payload : queryPayloads) {
            String signature = stringValue(payload.get("semanticSignature"));
            if (signature == null || signature.isBlank()) {
                continue;
            }
            Map<String, Object> summary = summariesBySignature.computeIfAbsent(signature, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("signature", key);
                item.put("count", 0);
                item.put("models", new ArrayList<String>());
                item.put("sliceFields", payload.get("sliceFields"));
                item.put("havingFields", payload.get("havingFields"));
                item.put("groupBy", normalizeGroupBy(payload.get("groupBy")));
                return item;
            });
            summary.put("count", ((Integer) summary.get("count")) + 1);
            @SuppressWarnings("unchecked")
            List<String> models = (List<String>) summary.get("models");
            String model = stringValue(payload.get("provider")) + "/" + stringValue(payload.get("modelName"));
            if (!models.contains(model)) {
                models.add(model);
            }
        }
        return summariesBySignature.values().stream()
                .peek(item -> item.put("models", List.copyOf(castStringList(item.get("models")))))
                .toList();
    }

    private static List<Map<String, Object>> queryPayloadShapeWarnings(List<Map<String, Object>> caseComparison) {
        if (caseComparison == null || caseComparison.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> comparison : caseComparison) {
            if (!"mixed".equals(comparison.get("queryPayloadShapeConsensus"))) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("testCaseId", comparison.get("testCaseId"));
            warning.put("provider", "comparison");
            warning.put("modelName", "cross-model");
            String divergenceClass = stringValue(comparison.get("queryPayloadShapeDivergenceClass"));
            boolean benign = "benign".equals(divergenceClass);
            warning.put("warningType", benign
                    ? "benign_query_payload_shape_divergence"
                    : "query_payload_shape_divergence");
            warning.put("severity", benign ? "info" : "warning");
            warning.put("source", "caseComparison");
            warning.put("queryPayloadShapeDivergenceClass", divergenceClass);
            warning.put("queryPayloadCount", comparison.get("queryPayloadCount"));
            warning.put("queryPayloadShapeSignatureCount", comparison.get("queryPayloadShapeSignatureCount"));
            warning.put("queryPayloadShapeSignatures", comparison.get("queryPayloadShapeSignatures"));
            warning.put("queryPayloadSemanticSignatureCount", comparison.get("queryPayloadSemanticSignatureCount"));
            warning.put("queryPayloadSemanticSignatures", comparison.get("queryPayloadSemanticSignatures"));
            warnings.add(warning);
        }
        return List.copyOf(warnings);
    }

    private static String queryPayloadShapeConsensus(int payloadCount, int signatureCount) {
        if (payloadCount == 0) {
            return "none";
        }
        if (signatureCount <= 1) {
            return "same";
        }
        return "mixed";
    }

    private static String queryPayloadShapeDivergenceClass(int payloadCount, int shapeSignatureCount,
                                                           int semanticSignatureCount) {
        if (payloadCount == 0) {
            return "none";
        }
        if (shapeSignatureCount <= 1) {
            return "same";
        }
        if (semanticSignatureCount <= 1) {
            return "benign";
        }
        return "semantic";
    }

    private static Map<String, Object> queryPayloadSemanticShape(Map<String, Object> signatureShape) {
        Map<String, Object> semanticShape = new LinkedHashMap<>();
        semanticShape.put("argumentModel", signatureShape.get("argumentModel"));
        semanticShape.put("slice", normalizeStringList(signatureShape.get("slice")));
        semanticShape.put("having", normalizeStringList(signatureShape.get("having")));
        semanticShape.put("groupBy", normalizeGroupBy(signatureShape.get("groupBy")));
        return semanticShape;
    }

    private static List<String> normalizeStringList(Object value) {
        return castStringList(value).stream()
                .sorted()
                .toList();
    }

    private static List<String> normalizeGroupBy(Object value) {
        List<String> fields = castStringList(value);
        Set<String> redundantIdPrefixes = fields.stream()
                .filter(field -> field.endsWith("$id"))
                .map(field -> field.substring(0, field.length() - 3))
                .filter(prefix -> fields.stream()
                        .anyMatch(field -> !field.endsWith("$id") && field.startsWith(prefix + "$")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return fields.stream()
                .filter(field -> !(field.endsWith("$id")
                        && redundantIdPrefixes.contains(field.substring(0, field.length() - 3))))
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> fieldValues(Object value) {
        return mapItems(value).stream()
                .map(AiTestReportSummary::fieldValue)
                .filter(field -> field != null && !field.isBlank())
                .distinct()
                .toList();
    }

    private static String fieldValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> map.put(String.valueOf(key), mapValue));
            return stringValue(firstNonNull(
                    firstNonNull(map.get("field"), map.get("name")),
                    firstNonNull(map.get("column"), firstNonNull(map.get("expr"), map.get("expression")))));
        }
        return stringValue(value);
    }

    private static List<String> conditionValues(Object value) {
        List<String> conditions = new ArrayList<>();
        collectConditionValues(value, conditions);
        return conditions.stream()
                .filter(condition -> condition != null && !condition.isBlank())
                .distinct()
                .toList();
    }

    private static void collectConditionValues(Object value, List<String> conditions) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectConditionValues(item, conditions);
            }
            return;
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> map.put(String.valueOf(key), mapValue));
            String compactOrCondition = compactOrEqualityCondition(map);
            if (compactOrCondition != null) {
                conditions.add(compactOrCondition);
                return;
            }
            if (hasConditionField(map)) {
                conditions.add(conditionValue(map));
                return;
            }
            for (Object child : map.values()) {
                collectConditionValues(child, conditions);
            }
            return;
        }
        conditions.add(stringValue(value));
    }

    private static String compactOrEqualityCondition(Map<String, Object> map) {
        Object orNode = firstNonNull(map.get("$or"), map.get("or"));
        if (orNode == null) {
            return null;
        }
        List<Map<String, Object>> children = mapItems(orNode).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(rawChild -> {
                    Map<String, Object> child = new LinkedHashMap<>();
                    rawChild.forEach((key, value) -> child.put(String.valueOf(key), value));
                    return child;
                })
                .toList();
        if (children.isEmpty() || children.size() != mapItems(orNode).size()) {
            return null;
        }

        List<String> fields = children.stream()
                .map(AiTestReportSummary::fieldValue)
                .filter(field -> field != null && !field.isBlank())
                .distinct()
                .toList();
        if (fields.size() != 1) {
            return null;
        }
        boolean allEquals = children.stream()
                .map(child -> stringValue(firstNonNull(child.get("op"), child.get("operator"))))
                .allMatch(op -> "=".equals(op) || "==".equals(op));
        if (!allEquals) {
            return null;
        }
        List<Object> values = children.stream()
                .map(child -> firstNonNull(child.get("value"), child.get("values")))
                .filter(item -> item != null && !compactValue(item).isBlank())
                .sorted(Comparator.comparing(AiTestReportSummary::compactValue))
                .toList();
        if (values.size() != children.size()) {
            return null;
        }
        return String.join("|", fields.get(0), "in", compactValue(values));
    }

    private static boolean hasConditionField(Map<String, Object> map) {
        return firstNonBlank(fieldValue(map)) != null;
    }

    private static String conditionValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> map.put(String.valueOf(key), mapValue));
            String field = fieldValue(map);
            String op = stringValue(firstNonNull(map.get("op"), map.get("operator")));
            Object conditionValue = firstNonNull(firstNonNull(map.get("value"), map.get("values")),
                    firstNonNull(map.get("from"), map.get("to")));
            return String.join("|",
                    firstNonBlank(field) == null ? "?" : field,
                    firstNonBlank(op) == null ? "?" : op,
                    compactValue(conditionValue));
        }
        return stringValue(value);
    }

    private static List<String> conditionFields(Object conditions) {
        return castStringList(conditions).stream()
                .map(condition -> condition.split("\\|", -1)[0])
                .filter(field -> !field.isBlank() && !"?".equals(field))
                .distinct()
                .toList();
    }

    private static List<String> conditionOps(Object conditions) {
        return castStringList(conditions).stream()
                .map(condition -> {
                    String[] parts = condition.split("\\|", -1);
                    return parts.length > 1 ? parts[1] : "";
                })
                .filter(op -> !op.isBlank() && !"?".equals(op))
                .distinct()
                .toList();
    }

    private static List<String> orderValues(Object value) {
        return mapItems(value).stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> rawMap) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        rawMap.forEach((key, mapValue) -> map.put(String.valueOf(key), mapValue));
                        String field = fieldValue(map);
                        String dir = stringValue(firstNonNull(map.get("dir"), map.get("direction")));
                        if (firstNonBlank(dir) == null) {
                            return field;
                        }
                        return "desc".equalsIgnoreCase(dir) ? "-" + field : field;
                    }
                    return stringValue(item);
                })
                .filter(order -> order != null && !order.isBlank())
                .distinct()
                .toList();
    }

    private static List<Object> mapItems(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    private static String compactValue(Object value) {
        if (value == null) {
            return "";
        }
        String rendered;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            rendered = stableJson(value);
        } else {
            rendered = String.valueOf(value);
        }
        return rendered.length() <= 120 ? rendered : rendered.substring(0, 117) + "...";
    }

    private static String stableJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private static String firstQuestion(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .map(SpringAiTestExecutor.AiTestResult::getQuestion)
                .filter(question -> question != null && !question.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String caseKey(SpringAiTestExecutor.AiTestResult result) {
        String testCaseId = result.getTestCaseId();
        return testCaseId == null || testCaseId.isBlank() ? "(unknown)" : testCaseId;
    }

    private static String consensus(int resultCount, long passed) {
        if (resultCount == 0) {
            return "empty";
        }
        if (passed == resultCount) {
            return "all_passed";
        }
        if (passed == 0) {
            return "all_failed";
        }
        return "mixed";
    }

    private static Map<String, Object> summarizeFailureCategories(List<SpringAiTestExecutor.AiTestResult> results) {
        Map<String, Long> counts = results.stream()
                .collect(Collectors.groupingBy(
                        AiTestReportSummary::errorCategory,
                        LinkedHashMap::new,
                        Collectors.counting()));
        return new LinkedHashMap<>(counts);
    }

    private static long clarifyCaseCount(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .filter(result -> !clarifyObservability(result).isEmpty())
                .map(SpringAiTestExecutor.AiTestResult::getTestCaseId)
                .distinct()
                .count();
    }

    private static long toolBusinessErrorCaseCount(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .filter(result -> !toolBusinessErrors(result).isEmpty())
                .map(SpringAiTestExecutor.AiTestResult::getTestCaseId)
                .distinct()
                .count();
    }

    private static long toolBusinessErrorCount(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .mapToLong(result -> toolBusinessErrors(result).size())
                .sum();
    }

    private static List<Map<String, Object>> warnings(SpringAiTestExecutor.AiTestResult result) {
        return warnings(result, toolBusinessErrors(result));
    }

    private static List<Map<String, Object>> warnings(SpringAiTestExecutor.AiTestResult result,
                                                      List<Map<String, Object>> toolBusinessErrors) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        warnings.addAll(warningsFromToolBusinessErrors(toolBusinessErrors));
        warnings.addAll(unknownModelProbeWarnings(toolBusinessErrors));
        warnings.addAll(toolCallAnomalyWarnings(result));
        warnings.addAll(modelDescribeRetryWarnings(result));
        return List.copyOf(warnings);
    }

    private static List<Map<String, Object>> warningsFromToolBusinessErrors(
            List<Map<String, Object>> toolBusinessErrors) {
        if (toolBusinessErrors == null || toolBusinessErrors.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> toolBusinessError : toolBusinessErrors) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("warningType", "tool_business_error");
            warning.put("severity", "warning");
            warning.putAll(toolBusinessError);
            warnings.add(warning);
        }
        return List.copyOf(warnings);
    }

    private static List<Map<String, Object>> unknownModelProbeWarnings(
            List<Map<String, Object>> toolBusinessErrors) {
        if (toolBusinessErrors == null || toolBusinessErrors.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> toolBusinessError : toolBusinessErrors) {
            if (!isDescribeModelTool(toolBusinessError)
                    || firstNonBlank(stringValue(toolBusinessError.get("argumentModel"))) == null) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("warningType", "unknown_model_probe");
            warning.put("severity", "warning");
            warning.putAll(toolBusinessError);
            warnings.add(warning);
        }
        return List.copyOf(warnings);
    }

    private static List<Map<String, Object>> toolCallAnomalyWarnings(SpringAiTestExecutor.AiTestResult result) {
        if (result.getToolCallRecords() == null || result.getToolCallRecords().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (ToolCallCollector.ToolCallRecord record : result.getToolCallRecords()) {
            if (!record.isSuccess()) {
                String warningType = isJsonParseError(record.getError())
                        ? "tool_result_parse_error"
                        : "tool_call_failure";
                warnings.add(toolCallWarning(result, record, warningType));
            } else if (record.getResult() == null) {
                warnings.add(toolCallWarning(result, record, "empty_tool_result"));
            }
        }
        return List.copyOf(warnings);
    }

    private static Map<String, Object> toolCallWarning(SpringAiTestExecutor.AiTestResult result,
                                                       ToolCallCollector.ToolCallRecord record,
                                                       String warningType) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("testCaseId", result.getTestCaseId());
        warning.put("provider", result.getProvider());
        warning.put("modelName", result.getModelName());
        warning.put("warningType", warningType);
        warning.put("severity", "warning");
        warning.put("source", "toolCall#" + record.getSequence());
        warning.put("toolName", record.getToolName());
        warning.put("springToolName", record.getSpringToolName());
        warning.put("sequence", record.getSequence());
        warning.put("durationMs", record.getDurationMs());
        warning.put("error", firstNonBlank(record.getError()));
        warning.put("argumentModel", argumentValue(record.getArguments(),
                "model", "modelName", "queryModel", "queryModelName", "qm", "qmCode"));
        return warning;
    }

    private static List<Map<String, Object>> modelDescribeRetryWarnings(SpringAiTestExecutor.AiTestResult result) {
        if (result.getToolCallRecords() == null || result.getToolCallRecords().isEmpty()) {
            return List.of();
        }
        Map<String, List<ToolCallCollector.ToolCallRecord>> recordsByModel = new LinkedHashMap<>();
        for (ToolCallCollector.ToolCallRecord record : result.getToolCallRecords()) {
            if (!isDescribeModelTool(record.getToolName(), record.getSpringToolName())) {
                continue;
            }
            String argumentModel = argumentValue(record.getArguments(),
                    "model", "modelName", "queryModel", "queryModelName", "qm", "qmCode");
            if (argumentModel == null || argumentModel.isBlank()) {
                continue;
            }
            String key = toolName(record.getToolName(), record.getSpringToolName()) + "|" + argumentModel;
            recordsByModel.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (List<ToolCallCollector.ToolCallRecord> records : recordsByModel.values()) {
            if (records.size() <= 1) {
                continue;
            }
            ToolCallCollector.ToolCallRecord first = records.get(0);
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("testCaseId", result.getTestCaseId());
            warning.put("provider", result.getProvider());
            warning.put("modelName", result.getModelName());
            warning.put("warningType", "model_describe_retry");
            warning.put("severity", "warning");
            warning.put("source", "toolCalls");
            warning.put("toolName", first.getToolName());
            warning.put("springToolName", first.getSpringToolName());
            warning.put("argumentModel", argumentValue(first.getArguments(),
                    "model", "modelName", "queryModel", "queryModelName", "qm", "qmCode"));
            warning.put("describeCallCount", records.size());
            warning.put("sequences", records.stream()
                    .map(ToolCallCollector.ToolCallRecord::getSequence)
                    .toList());
            warning.put("sources", records.stream()
                    .map(record -> "toolCall#" + record.getSequence())
                    .toList());
            warnings.add(warning);
        }
        return List.copyOf(warnings);
    }

    private static long warningCaseCount(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .filter(result -> !warnings(result).isEmpty())
                .map(SpringAiTestExecutor.AiTestResult::getTestCaseId)
                .distinct()
                .count();
    }

    private static long warningCount(List<SpringAiTestExecutor.AiTestResult> results) {
        return results.stream()
                .mapToLong(result -> warnings(result).size())
                .sum();
    }

    private static int warningCaseCountFromWarnings(List<Map<String, Object>> warnings) {
        return warnings.stream()
                .map(warning -> stringValue(warning.get("testCaseId")))
                .filter(testCaseId -> testCaseId != null && !testCaseId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size();
    }

    private static Map<String, Long> summarizeWarningCategories(List<Map<String, Object>> warnings) {
        return warnings.stream()
                .collect(Collectors.groupingBy(
                        warning -> Optional.ofNullable(stringValue(warning.get("warningType")))
                                .filter(type -> !type.isBlank())
                                .orElse("unknown"),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private static String errorCategory(SpringAiTestExecutor.AiTestResult result) {
        if (result == null) {
            return "unknown";
        }
        if (result.isSuccess()) {
            return "success";
        }
        String message = firstNonBlank(result.getErrorMessage());
        if (message != null) {
            String normalized = message.toLowerCase(Locale.ROOT);
            if (isDatabaseUnavailable(normalized)) {
                return "exception:database_unavailable";
            }
            if (normalized.contains("429") || normalized.contains("quota") || normalized.contains("cooldown")) {
                return "exception:provider_unavailable";
            }
            return "exception";
        }
        if (result.getValidationResult() != null && !result.getValidationResult().isPassed()) {
            String validationMessage = validationMessage(result.getValidationResult());
            if (validationMessage != null && isDatabaseUnavailable(validationMessage.toLowerCase(Locale.ROOT))) {
                return "validation_failed:database_unavailable";
            }
            return "validation_failed";
        }
        if (result.getToolCallRecords() == null || result.getToolCallRecords().isEmpty()) {
            return "no_tool_calls";
        }
        return "failed_without_detail";
    }

    private static boolean isDatabaseUnavailable(String normalizedMessage) {
        return normalizedMessage.contains("communications link failure")
                || normalizedMessage.contains("connection refused")
                || normalizedMessage.contains("unable to acquire jdbc connection")
                || normalizedMessage.contains("mysql");
    }

    private static String validationMessage(ResultValidator.ValidationResult validationResult) {
        List<String> messages = new ArrayList<>();
        if (validationResult.getErrors() != null) {
            messages.addAll(validationResult.getErrors());
        }
        if (validationResult.getFailedRules() != null) {
            messages.addAll(validationResult.getFailedRules());
        }
        return messages.stream()
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(null);
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

    private static Optional<Map<String, Object>> extractToolBusinessError(Object rawResult, String source,
                                                                          String toolName, String springToolName,
                                                                          Map<String, Object> arguments,
                                                                          ToolCallCollector.ToolCallRecord record) {
        return normalizeResult(rawResult)
                .flatMap(AiTestReportSummary::findToolBusinessErrorPayload)
                .map(payload -> {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("source", source);
                    error.put("toolName", toolName);
                    error.put("springToolName", springToolName);
                    error.put("sequence", record == null ? null : record.getSequence());
                    error.put("durationMs", record == null ? null : record.getDurationMs());
                    error.put("code", payload.get("code"));
                    error.put("exCode", stringValue(payload.get("exCode")));
                    error.put("message", stringValue(firstNonNull(
                            firstNonNull(payload.get("msg"), payload.get("message")), payload.get("error"))));
                    error.put("argumentModel", argumentValue(arguments,
                            "model", "modelName", "queryModel", "queryModelName", "qm", "qmCode"));
                    return error;
                });
    }

    private static Optional<Map<String, Object>> findToolBusinessErrorPayload(Map<String, Object> payload) {
        if (isToolBusinessErrorPayload(payload)) {
            return Optional.of(payload);
        }
        if (numericCode(payload.get("code")).isPresent()) {
            return Optional.empty();
        }
        Object data = payload.get("data");
        if (data != null) {
            Optional<Map<String, Object>> nested =
                    normalizeResult(data).flatMap(AiTestReportSummary::findToolBusinessErrorPayload);
            if (nested.isPresent()) {
                return nested;
            }
        }
        Object result = payload.get("result");
        if (result != null) {
            return normalizeResult(result).flatMap(AiTestReportSummary::findToolBusinessErrorPayload);
        }
        return Optional.empty();
    }

    private static boolean isToolBusinessErrorPayload(Map<String, Object> payload) {
        return numericCode(payload.get("code"))
                .map(code -> code != 200)
                .orElse(false);
    }

    private static Optional<Long> numericCode(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String string && string.matches("-?\\d+")) {
            return Optional.of(Long.parseLong(string));
        }
        return Optional.empty();
    }

    private static String argumentValue(Map<String, Object> arguments, String... names) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        for (String name : names) {
            Object value = arguments.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static boolean isDescribeModelTool(Map<String, Object> item) {
        return isDescribeModelTool(stringValue(item.get("toolName")), stringValue(item.get("springToolName")));
    }

    private static boolean isDescribeModelTool(String toolName, String springToolName) {
        return toolName(toolName, springToolName).contains("describe_model");
    }

    private static boolean isQueryModelTool(String toolName, String springToolName) {
        return toolName(toolName, springToolName).contains("query_model");
    }

    private static boolean isJsonParseError(String error) {
        return error != null && error.contains("JSON_PARSE_ERROR");
    }

    private static String toolName(String toolName, String springToolName) {
        String selected = firstNonBlank(toolName);
        if (selected == null) {
            selected = firstNonBlank(springToolName);
        }
        return selected == null ? "" : selected;
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

    private static List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .toList();
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

    private static String firstNonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
