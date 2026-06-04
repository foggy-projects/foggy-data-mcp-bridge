package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;

import java.time.Instant;
import java.util.ArrayList;
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
        summary.put("caseComparison", summarizeCaseComparison(safeResults));
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
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("testCaseId", entry.getKey());
                    summary.put("question", firstQuestion(caseResults));
                    summary.put("resultCount", caseResults.size());
                    summary.put("passedCount", passed);
                    summary.put("failedCount", caseResults.size() - passed);
                    summary.put("consensus", consensus(caseResults.size(), passed));
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
        summary.put("warningCount", warnings(result).size());
        if (result.getValidationResult() != null) {
            summary.put("failedRules", result.getValidationResult().getFailedRules());
            summary.put("validationErrors", result.getValidationResult().getErrors());
        }
        return summary;
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
