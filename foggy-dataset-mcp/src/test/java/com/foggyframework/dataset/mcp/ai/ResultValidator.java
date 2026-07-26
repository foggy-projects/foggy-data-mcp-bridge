package com.foggyframework.dataset.mcp.ai;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试结果验证器
 *
 * 验证 AI 工具调用结果是否符合预期
 */
@Slf4j
public class ResultValidator {

    /**
     * 验证结果
     */
    @Data
    @Builder
    public static class ValidationResult {
        private boolean passed;
        private String testCaseId;
        private List<String> passedRules;
        private List<String> failedRules;
        private List<String> errors;
        private Map<String, Object> details;

        public static ValidationResult success(String testCaseId) {
            return ValidationResult.builder()
                    .passed(true)
                    .testCaseId(testCaseId)
                    .passedRules(new ArrayList<>())
                    .failedRules(new ArrayList<>())
                    .errors(new ArrayList<>())
                    .details(new HashMap<>())
                    .build();
        }

        public static ValidationResult failure(String testCaseId, String error) {
            return ValidationResult.builder()
                    .passed(false)
                    .testCaseId(testCaseId)
                    .passedRules(new ArrayList<>())
                    .failedRules(new ArrayList<>())
                    .errors(Collections.singletonList(error))
                    .details(new HashMap<>())
                    .build();
        }

        public void addPassedRule(String rule) {
            if (passedRules == null) passedRules = new ArrayList<>();
            passedRules.add(rule);
        }

        public void addFailedRule(String rule) {
            if (failedRules == null) failedRules = new ArrayList<>();
            failedRules.add(rule);
            this.passed = false;
        }

        public void addError(String error) {
            if (errors == null) errors = new ArrayList<>();
            errors.add(error);
            this.passed = false;
        }

        public String getSummary() {
            if (passed) {
                return String.format("PASSED [%s]: %d rules passed",
                        testCaseId, passedRules.size());
            } else {
                return String.format("FAILED [%s]: %d passed, %d failed, errors: %s",
                        testCaseId, passedRules.size(), failedRules.size(),
                        String.join("; ", errors));
            }
        }
    }

    /**
     * 验证查询结果
     *
     * @param testCase     测试用例
     * @param actualResult 实际查询结果
     * @return 验证结果
     */
    @SuppressWarnings("unchecked")
    public ValidationResult validate(EcommerceTestCase testCase, Object actualResult) {
        ValidationResult result = ValidationResult.success(testCase.getId());
        EcommerceTestCase.ExpectedResult expected = testCase.getExpected();

        if (expected == null) {
            result.addPassedRule("No validation required (expected is null)");
            return result;
        }

        // 解包 RX 响应
        Object unwrappedResult = unwrapRxResponse(actualResult);

        // 检查结果是否为错误响应
        if (unwrappedResult instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) unwrappedResult;
            if (Boolean.TRUE.equals(resultMap.get("error"))) {
                result.addError("Query returned error: " + resultMap.get("message"));
                return result;
            }
        }

        // 如果只需要验证成功（不报错）
        if (expected.isSuccessOnly()) {
            result.addPassedRule("Success-only validation passed");
            return result;
        }

        // 提取 items 列表
        List<Map<String, Object>> items = extractItems(unwrappedResult);
        if (items == null) {
            result.addError("Cannot extract items from result");
            return result;
        }

        result.getDetails().put("actualRowCount", items.size());

        // 验证必需列
        if (expected.getRequiredColumns() != null && !expected.getRequiredColumns().isEmpty()) {
            validateRequiredColumns(expected.getRequiredColumns(), items, result);
        }

        // 验证禁止列
        if (expected.getForbiddenColumns() != null && !expected.getForbiddenColumns().isEmpty()) {
            validateForbiddenColumns(expected.getForbiddenColumns(), items, result);
        }

        // 验证行数
        validateRowCount(expected, items, result);

        // 验证自定义规则
        if (expected.getRules() != null) {
            for (EcommerceTestCase.ValidationRule rule : expected.getRules()) {
                validateRule(rule, items, result);
            }
        }

        return result;
    }

    /**
     * 解包 RX 响应
     */
    @SuppressWarnings("unchecked")
    private Object unwrapRxResponse(Object result) {
        if (result == null) {
            return null;
        }

        // 处理 RX 包装
        if (result instanceof RX<?> rx) {
            if (rx._isSuccess()) {
                return rx.getData();
            } else {
                // 返回错误信息作为 Map
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", true);
                errorMap.put("message", rx.getMsg());
                return errorMap;
            }
        }

        return result;
    }

    /**
     * 从结果中提取 items 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Object result) {
        if (result == null) {
            return null;
        }

        // 处理 SemanticQueryResponse 对象
        if (result instanceof SemanticQueryResponse sqr) {
            return sqr.getItems();
        }

        // 处理 SemanticMetadataResponse 对象
        if (result instanceof SemanticMetadataResponse smr) {
            // SemanticMetadataResponse 的数据在 data Map 中
            Map<String, Object> data = smr.getData();
            if (data != null) {
                // 检查 models 字段
                if (data.containsKey("models")) {
                    Object models = data.get("models");
                    if (models instanceof Map) {
                        // models 是 Map<modelName, modelInfo>
                        Map<String, Object> modelsMap = (Map<String, Object>) models;
                        return modelsMap.entrySet().stream()
                                .map(entry -> {
                                    Map<String, Object> item = new HashMap<>();
                                    item.put("name", entry.getKey());
                                    if (entry.getValue() instanceof Map) {
                                        item.putAll((Map<String, Object>) entry.getValue());
                                    }
                                    return item;
                                })
                                .collect(Collectors.toList());
                    } else if (models instanceof List) {
                        return (List<Map<String, Object>>) models;
                    }
                }
                // 尝试返回整个 data 作为单条记录
                return Collections.singletonList(data);
            }
            return Collections.emptyList();
        }

        // 如果结果是 Map
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;

            // 检查 RX 包装：data 字段
            if (map.containsKey("data")) {
                Object data = map.get("data");
                if (data instanceof Map) {
                    map = (Map<String, Object>) data;
                } else if (data instanceof SemanticQueryResponse sqr) {
                    return sqr.getItems();
                } else if (data instanceof SemanticMetadataResponse smr) {
                    return extractItems(smr);
                }
            }

            // 检查 items 字段
            if (map.containsKey("items")) {
                Object items = map.get("items");
                if (items instanceof List) {
                    return (List<Map<String, Object>>) items;
                }
            }

            // 检查 models 字段（元数据响应）
            if (map.containsKey("models")) {
                Object models = map.get("models");
                if (models instanceof Map) {
                    // models 是 Map<modelName, modelInfo>
                    Map<String, Object> modelsMap = (Map<String, Object>) models;
                    return modelsMap.entrySet().stream()
                            .map(entry -> {
                                Map<String, Object> item = new HashMap<>();
                                item.put("name", entry.getKey());
                                if (entry.getValue() instanceof Map) {
                                    item.putAll((Map<String, Object>) entry.getValue());
                                }
                                return item;
                            })
                            .collect(Collectors.toList());
                } else if (models instanceof List) {
                    return (List<Map<String, Object>>) models;
                }
            }

            // 如果是单个结果，包装为列表
            return Collections.singletonList(map);
        }

        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }

        return null;
    }

    /**
     * 验证必需列是否存在
     */
    private void validateRequiredColumns(List<String> requiredColumns,
                                         List<Map<String, Object>> items,
                                         ValidationResult result) {
        if (items.isEmpty()) {
            result.addFailedRule("Cannot validate columns: result is empty");
            return;
        }

        Set<String> actualColumns = items.get(0).keySet();
        for (String column : requiredColumns) {
            // 支持列名的模糊匹配（例如 product$caption 可以匹配 product_caption）
            boolean found = actualColumns.stream()
                    .anyMatch(col -> columnsMatch(column, col));

            if (found) {
                result.addPassedRule("Required column exists: " + column);
            } else {
                result.addFailedRule("Required column missing: " + column +
                        " (actual columns: " + actualColumns + ")");
            }
        }
    }

    /**
     * 验证禁止列不存在
     */
    private void validateForbiddenColumns(List<String> forbiddenColumns,
                                          List<Map<String, Object>> items,
                                          ValidationResult result) {
        if (items.isEmpty()) {
            result.addPassedRule("Forbidden columns check skipped: result is empty");
            return;
        }

        Set<String> actualColumns = items.get(0).keySet();
        for (String column : forbiddenColumns) {
            boolean found = actualColumns.stream()
                    .anyMatch(col -> columnsMatch(column, col));

            if (!found) {
                result.addPassedRule("Forbidden column absent: " + column);
            } else {
                result.addFailedRule("Forbidden column present: " + column);
            }
        }
    }

    /**
     * 验证行数
     */
    private void validateRowCount(EcommerceTestCase.ExpectedResult expected,
                                  List<Map<String, Object>> items,
                                  ValidationResult result) {
        int actualCount = items.size();

        if (expected.getExactRows() != null) {
            if (actualCount == expected.getExactRows()) {
                result.addPassedRule("Exact row count matched: " + actualCount);
            } else {
                result.addFailedRule("Row count mismatch: expected " +
                        expected.getExactRows() + ", got " + actualCount);
            }
            return;
        }

        if (expected.getMinRows() != null && actualCount < expected.getMinRows()) {
            result.addFailedRule("Row count below minimum: expected >= " +
                    expected.getMinRows() + ", got " + actualCount);
        } else if (expected.getMinRows() != null) {
            result.addPassedRule("Min row count satisfied: " + actualCount + " >= " + expected.getMinRows());
        }

        if (expected.getMaxRows() != null && actualCount > expected.getMaxRows()) {
            result.addFailedRule("Row count above maximum: expected <= " +
                    expected.getMaxRows() + ", got " + actualCount);
        } else if (expected.getMaxRows() != null) {
            result.addPassedRule("Max row count satisfied: " + actualCount + " <= " + expected.getMaxRows());
        }
    }

    /**
     * 验证单个规则
     */
    @SuppressWarnings("unchecked")
    private void validateRule(EcommerceTestCase.ValidationRule rule,
                              List<Map<String, Object>> items,
                              ValidationResult result) {
        String ruleName = rule.getType().name();

        try {
            switch (rule.getType()) {
                case NOT_EMPTY -> {
                    if (!items.isEmpty()) {
                        result.addPassedRule(ruleName + ": result is not empty");
                    } else {
                        result.addFailedRule(ruleName + ": result is empty");
                    }
                }

                case ROW_COUNT -> {
                    int expected = ((Number) rule.getValue()).intValue();
                    if (items.size() == expected) {
                        result.addPassedRule(ruleName + ": " + items.size() + " == " + expected);
                    } else {
                        result.addFailedRule(ruleName + ": " + items.size() + " != " + expected);
                    }
                }

                case ROW_COUNT_RANGE -> {
                    Map<String, Object> params = rule.getParams();
                    int min = params.containsKey("min") ? ((Number) params.get("min")).intValue() : 0;
                    int max = params.containsKey("max") ? ((Number) params.get("max")).intValue() : Integer.MAX_VALUE;

                    if (items.size() >= min && items.size() <= max) {
                        result.addPassedRule(ruleName + ": " + min + " <= " + items.size() + " <= " + max);
                    } else {
                        result.addFailedRule(ruleName + ": " + items.size() + " not in [" + min + ", " + max + "]");
                    }
                }

                case COLUMN_EXISTS -> {
                    String column = rule.getColumn();
                    if (!items.isEmpty()) {
                        boolean exists = items.get(0).keySet().stream()
                                .anyMatch(col -> columnsMatch(column, col));
                        if (exists) {
                            result.addPassedRule(ruleName + ": column " + column + " exists");
                        } else {
                            result.addFailedRule(ruleName + ": column " + column + " not found");
                        }
                    }
                }

                case VALUE_CONTAINS -> {
                    String column = rule.getColumn();
                    Object expectedValue = rule.getValue();
                    String columnKey = findMatchingColumn(items, column);

                    if (columnKey != null) {
                        boolean found = items.stream()
                                .anyMatch(item -> {
                                    Object val = item.get(columnKey);
                                    return val != null && val.toString().contains(expectedValue.toString());
                                });

                        if (found) {
                            result.addPassedRule(ruleName + ": " + column + " contains '" + expectedValue + "'");
                        } else {
                            result.addFailedRule(ruleName + ": " + column + " does not contain '" + expectedValue + "'");
                        }
                    } else {
                        result.addFailedRule(ruleName + ": column " + column + " not found");
                    }
                }

                case VALUE_IN_RANGE -> {
                    String column = rule.getColumn();
                    Map<String, Object> params = rule.getParams();
                    String columnKey = findMatchingColumn(items, column);

                    if (columnKey != null && params != null) {
                        Double min = params.containsKey("min") ? ((Number) params.get("min")).doubleValue() : null;
                        Double max = params.containsKey("max") ? ((Number) params.get("max")).doubleValue() : null;

                        boolean allInRange = items.stream().allMatch(item -> {
                            Object val = item.get(columnKey);
                            if (val instanceof Number) {
                                double numVal = ((Number) val).doubleValue();
                                return (min == null || numVal >= min) && (max == null || numVal <= max);
                            }
                            return false;
                        });

                        if (allInRange) {
                            result.addPassedRule(ruleName + ": all " + column + " values in range");
                        } else {
                            result.addFailedRule(ruleName + ": some " + column + " values out of range");
                        }
                    }
                }

                case ORDER_BY -> {
                    String column = rule.getColumn();
                    Map<String, Object> params = rule.getParams();
                    String direction = params != null ? (String) params.get("direction") : "ASC";
                    String columnKey = findMatchingColumn(items, column);

                    if (columnKey != null && items.size() > 1) {
                        boolean isOrdered = checkOrdering(items, columnKey, "DESC".equalsIgnoreCase(direction));
                        if (isOrdered) {
                            result.addPassedRule(ruleName + ": " + column + " is ordered " + direction);
                        } else {
                            result.addFailedRule(ruleName + ": " + column + " is not ordered " + direction);
                        }
                    } else {
                        result.addPassedRule(ruleName + ": ordering check skipped (not enough rows)");
                    }
                }

                case CONTAINS_MODEL -> {
                    String modelName = (String) rule.getValue();
                    boolean found = items.stream()
                            .anyMatch(item -> {
                                Object name = item.get("name");
                                return name != null && name.toString().equals(modelName);
                            });

                    if (found) {
                        result.addPassedRule(ruleName + ": model " + modelName + " found");
                    } else {
                        result.addFailedRule(ruleName + ": model " + modelName + " not found");
                    }
                }

                default -> result.addPassedRule(ruleName + ": rule type not implemented (skipped)");
            }
        } catch (Exception e) {
            result.addError("Error validating rule " + ruleName + ": " + e.getMessage());
        }
    }

    /**
     * 检查列名是否匹配（支持模糊匹配）
     */
    private boolean columnsMatch(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        // 替换 $ 为 _ 进行比较
        String normalizedExpected = normalizeColumnText(expected);
        String normalizedActual = normalizeColumnText(actual);
        return normalizedExpected.equalsIgnoreCase(normalizedActual);
    }

    private String normalizeColumnText(String value) {
        return value.replace("$", "_").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 查找匹配的列名
     */
    private String findMatchingColumn(List<Map<String, Object>> items, String column) {
        if (items.isEmpty()) {
            return null;
        }
        return items.get(0).keySet().stream()
                .filter(col -> columnsMatch(column, col))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查排序是否正确
     */
    @SuppressWarnings("unchecked")
    private boolean checkOrdering(List<Map<String, Object>> items, String column, boolean descending) {
        for (int i = 1; i < items.size(); i++) {
            Object prev = items.get(i - 1).get(column);
            Object curr = items.get(i).get(column);

            if (prev == null || curr == null) {
                continue;
            }

            int cmp;
            if (prev instanceof Number && curr instanceof Number) {
                cmp = Double.compare(
                        ((Number) prev).doubleValue(),
                        ((Number) curr).doubleValue()
                );
            } else {
                cmp = prev.toString().compareTo(curr.toString());
            }

            if (descending && cmp < 0) {
                return false;
            } else if (!descending && cmp > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 AI 响应验证结果
     *
     * <p>用于 Spring AI Tool Calling 模式，AI 响应是工具执行后的最终结果。
     *
     * @param testCase    测试用例
     * @param aiResponse  AI 响应内容
     * @param toolCalls   工具调用记录列表
     * @return 验证结果
     */
    public ValidationResult validateFromAiResponse(EcommerceTestCase testCase,
                                                    String aiResponse,
                                                    List<ToolCallCollector.ToolCallRecord> toolCalls) {
        ValidationResult result = ValidationResult.success(testCase.getId());
        EcommerceTestCase.ExpectedResult expected = testCase.getExpected();

        // 基本检查：AI 是否返回了响应
        if (aiResponse == null || aiResponse.isEmpty()) {
            result.addError("AI returned empty response");
            return result;
        }

        // 检查是否有错误响应
        if (aiResponse.contains("\"error\"") && aiResponse.contains("true")) {
            result.addError("AI response contains error");
            return result;
        }

        // 验证工具调用
        if (toolCalls != null && !toolCalls.isEmpty()) {
            result.getDetails().put("toolCallCount", toolCalls.size());
            result.getDetails().put("toolNames", toolCalls.stream()
                    .map(ToolCallCollector.ToolCallRecord::getToolName)
                    .toList());

            // 区分 JSON 解析错误和业务执行错误
            long jsonParseErrorCount = toolCalls.stream()
                    .filter(t -> !t.isSuccess() && t.getError() != null && t.getError().contains("JSON_PARSE_ERROR"))
                    .count();
            long executionErrorCount = toolCalls.stream()
                    .filter(t -> !t.isSuccess() && (t.getError() == null || !t.getError().contains("JSON_PARSE_ERROR")))
                    .count();
            long successCount = toolCalls.stream().filter(ToolCallCollector.ToolCallRecord::isSuccess).count();

            // JSON 解析错误单独记录（通常是 AI 生成参数的问题，不算严重失败）
            if (jsonParseErrorCount > 0) {
                result.getDetails().put("jsonParseErrors", jsonParseErrorCount);
                // JSON 解析错误作为警告，不直接判定失败
                // 因为 AI 可能会重试并最终成功
                for (ToolCallCollector.ToolCallRecord call : toolCalls) {
                    if (!call.isSuccess() && call.getError() != null && call.getError().contains("JSON_PARSE_ERROR")) {
                        log.debug("JSON parse error (AI may retry): {} - {}", call.getToolName(), call.getError());
                    }
                }
            }

            // 检查是否有真正的业务执行错误
            if (executionErrorCount > 0) {
                result.addFailedRule("Tool execution errors: " + executionErrorCount + " out of " + toolCalls.size());
                for (ToolCallCollector.ToolCallRecord call : toolCalls) {
                    if (!call.isSuccess() && (call.getError() == null || !call.getError().contains("JSON_PARSE_ERROR"))) {
                        result.addError("Tool " + call.getToolName() + " failed: " + call.getError());
                    }
                }
            }

            // 只要有成功的工具调用就算通过（AI 可能会重试解决 JSON 问题）
            if (successCount > 0) {
                result.addPassedRule(successCount + " tool calls succeeded" +
                        (jsonParseErrorCount > 0 ? " (with " + jsonParseErrorCount + " JSON parse retries)" : ""));
            } else if (executionErrorCount == 0 && jsonParseErrorCount > 0) {
                // 只有 JSON 解析错误，没有成功也没有业务错误
                result.addFailedRule("All " + jsonParseErrorCount + " tool calls failed due to JSON parse errors");
            }

            // 验证期望的工具是否被调用
            String expectedTool = testCase.getExpectedTool();
            if (expectedTool != null && !expectedTool.isEmpty()) {
                boolean expectedToolCalled = toolCalls.stream()
                        .anyMatch(t -> toolMatches(expectedTool, t));
                if (expectedToolCalled) {
                    result.addPassedRule("Expected tool was called: " + expectedTool);
                } else {
                    result.addFailedRule("Expected tool was not called: " + expectedTool +
                            " (called: " + toolCalls.stream().map(ToolCallCollector.ToolCallRecord::getToolName).toList() + ")");
                }
            }
            validateTargetModel(testCase, toolCalls, result);
        } else {
            result.getDetails().put("toolCallCount", 0);
            // 如果期望有工具调用但没有
            if (testCase.getExpectedTool() != null && !testCase.getExpectedTool().isEmpty()) {
                result.addFailedRule("No tool calls recorded, but expected: " + testCase.getExpectedTool());
            }
        }

        // 如果没有期望验证，只要有响应就算通过
        if (expected == null) {
            result.addPassedRule("No validation required, AI responded successfully");
            return result;
        }

        validateToolArgumentRules(expected.getToolArgumentRules(), toolCalls, result);

        // 如果只需要验证成功
        if (expected.isSuccessOnly()) {
            result.addPassedRule("Success-only validation passed");
            return result;
        }

        // 尝试从 AI 响应中提取数据进行验证
        try {
            // 检查必需的关键词/内容
            if (expected.getRequiredColumns() != null && !expected.getRequiredColumns().isEmpty()) {
                // 从工具调用结果中构建列名到 title 的映射
                Map<String, String> columnTitleMap = buildColumnTitleMap(toolCalls);

                for (String column : expected.getRequiredColumns()) {
                    // 检查列名是否在响应中出现（支持多种匹配策略）
                    String normalizedColumn = column.replace("$", "");
                    String columnTitle = columnTitleMap.get(column);

                    boolean found = aiResponse.contains(column)
                            || aiResponse.contains(normalizedColumn)
                            || (columnTitle != null && aiResponse.contains(columnTitle))
                            || (columnTitle != null && containsTitleKeywords(aiResponse, columnTitle))
                            || toolCallsContainColumnReference(toolCalls, column);

                    if (found) {
                        String matchInfo = columnTitle != null
                                ? column + " (or title: " + columnTitle + ")"
                                : column;
                        result.addPassedRule("Required content found: " + matchInfo);
                    } else {
                        String expectedInfo = columnTitle != null
                                ? column + " (expected title: " + columnTitle + ")"
                                : column;
                        result.addFailedRule("Required content not found: " + expectedInfo);
                    }
                }
            }

            // 检查自定义规则
            if (expected.getRules() != null) {
                for (EcommerceTestCase.ValidationRule rule : expected.getRules()) {
                    validateRuleFromAiResponse(rule, aiResponse, result);
                }
            }

            // 如果没有任何具体验证项但有响应，默认通过
            if (result.getPassedRules().isEmpty() && result.getFailedRules().isEmpty()) {
                result.addPassedRule("AI response received (no specific validation rules)");
            }

        } catch (Exception e) {
            result.addError("Error validating AI response: " + e.getMessage());
        }

        return result;
    }

    private boolean toolMatches(String expectedTool, ToolCallCollector.ToolCallRecord call) {
        if (call.getToolName().equals(expectedTool)
                || call.getSpringToolName().equals(expectedTool.replace(".", "_"))) {
            return true;
        }
        return "dataset.query_model".equals(expectedTool)
                && (isChartExportTool(call.getToolName())
                || isChartExportSpringTool(call.getSpringToolName()));
    }

    private boolean isChartExportTool(String toolName) {
        return "dataset.export_with_xchart".equals(toolName)
                || "dataset.export_with_echarts".equals(toolName);
    }

    private boolean isChartExportSpringTool(String toolName) {
        return "dataset_export_with_xchart".equals(toolName)
                || "dataset_export_with_echarts".equals(toolName);
    }

    private void validateTargetModel(EcommerceTestCase testCase,
                                     List<ToolCallCollector.ToolCallRecord> toolCalls,
                                     ValidationResult result) {
        String targetModel = testCase.getTargetModel();
        if (targetModel == null || targetModel.isBlank()
                || !"dataset.query_model".equals(testCase.getExpectedTool())
                || toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        List<String> observedModels = toolCalls.stream()
                .filter(ToolCallCollector.ToolCallRecord::isSuccess)
                .filter(call -> toolMatches(testCase.getExpectedTool(), call))
                .map(call -> argumentModel(call.getArguments()))
                .filter(model -> model != null && !model.isBlank())
                .distinct()
                .toList();
        if (observedModels.contains(targetModel)) {
            result.addPassedRule("Target query model used: " + targetModel);
        } else {
            result.addFailedRule("Target query model not used: " + targetModel
                    + " (observed: " + observedModels + ")");
        }
    }

    private String argumentModel(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        return stringValue(firstNonNull(
                firstNonNull(arguments.get("model"), arguments.get("modelName")),
                firstNonNull(arguments.get("queryModel"),
                        firstNonNull(arguments.get("queryModelName"),
                                firstNonNull(arguments.get("qm"), arguments.get("qmCode"))))));
    }

    private void validateToolArgumentRules(List<EcommerceTestCase.ToolArgumentRule> rules,
                                           List<ToolCallCollector.ToolCallRecord> toolCalls,
                                           ValidationResult result) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (EcommerceTestCase.ToolArgumentRule rule : rules) {
            String ruleName = "TOOL_ARGUMENT:" + rule.getPath() + ":" + rule.getField();
            List<ToolCallCollector.ToolCallRecord> candidateCalls = matchingSuccessfulToolCalls(toolCalls, rule);
            if (candidateCalls.isEmpty()) {
                result.addFailedRule(ruleName + ": no successful matching tool call found for " + rule.getTool());
                continue;
            }
            boolean found = candidateCalls.stream()
                    .anyMatch(call -> toolCallPayloadPathContains(call, rule));
            if (rule.isMustExist() == found) {
                result.addPassedRule(ruleName + ": " + (rule.isMustExist() ? "found" : "absent as expected"));
            } else if (rule.isMustExist()) {
                result.addFailedRule(ruleName + ": expected field " + rule.getField()
                        + operatorLabel(rule.getOperator()) + valueLabel(rule.getValue()) + " in " + rule.getPath()
                        + ". " + describeToolArgumentPaths(candidateCalls, rule));
            } else {
                result.addFailedRule(ruleName + ": forbidden field " + rule.getField()
                        + operatorLabel(rule.getOperator()) + valueLabel(rule.getValue()) + " found in " + rule.getPath()
                        + ". " + describeToolArgumentPaths(candidateCalls, rule));
            }
        }
    }

    private List<ToolCallCollector.ToolCallRecord> matchingSuccessfulToolCalls(
            List<ToolCallCollector.ToolCallRecord> toolCalls,
            EcommerceTestCase.ToolArgumentRule rule) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .filter(ToolCallCollector.ToolCallRecord::isSuccess)
                .filter(call -> rule.getTool() == null || rule.getTool().isBlank() || toolMatches(rule.getTool(), call))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private boolean toolCallPayloadPathContains(ToolCallCollector.ToolCallRecord call,
                                                EcommerceTestCase.ToolArgumentRule rule) {
        if (call.getArguments() == null || rule.getPath() == null || rule.getPath().isBlank()) {
            return false;
        }
        Object node = toolCallPayloadPathNode(call, rule.getPath());
        if (conditionTreeContains(node, rule.getField(), rule.getOperator(), rule.getValue())) {
            return true;
        }
        Object normalizedNode = toolCallNormalizedPayloadPathNode(call, rule.getPath());
        return conditionTreeContains(normalizedNode, rule.getField(), rule.getOperator(), rule.getValue());
    }

    @SuppressWarnings("unchecked")
    private Object toolCallPayloadPathNode(ToolCallCollector.ToolCallRecord call, String path) {
        if (call.getArguments() == null || path == null || path.isBlank()) {
            return null;
        }
        Object payload = call.getArguments().get("payload");
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return null;
        }
        return ((Map<String, Object>) payloadMap).get(path);
    }

    @SuppressWarnings("unchecked")
    private Object toolCallNormalizedPayloadPathNode(ToolCallCollector.ToolCallRecord call, String path) {
        Object result = call.getResult();
        if (result instanceof RX rx) {
            result = rx.getData();
        }
        return findNormalizedPathNode(result, path);
    }

    @SuppressWarnings("unchecked")
    private Object findNormalizedPathNode(Object node, String path) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> typedMap = new LinkedHashMap<>();
        map.forEach((key, value) -> typedMap.put(String.valueOf(key), value));

        Object debug = typedMap.get("debug");
        if (debug instanceof Map<?, ?> debugMap) {
            Object normalized = debugMap.get("normalized");
            if (normalized instanceof Map<?, ?> normalizedMap && normalizedMap.containsKey(path)) {
                return ((Map<String, Object>) normalizedMap).get(path);
            }
        }

        Object normalized = typedMap.get("normalized");
        if (normalized instanceof Map<?, ?> normalizedMap && normalizedMap.containsKey(path)) {
            return ((Map<String, Object>) normalizedMap).get(path);
        }

        Object data = typedMap.get("data");
        Object nested = findNormalizedPathNode(data, path);
        if (nested != null) {
            return nested;
        }
        return findNormalizedPathNode(typedMap.get("result"), path);
    }

    private boolean conditionTreeContains(Object node,
                                          String expectedField,
                                          String expectedOperator,
                                          Object expectedValue) {
        if (node == null) {
            return false;
        }
        Set<String> expectedValues = scalarValues(expectedValue);
        if (!expectedValues.isEmpty()) {
            Set<String> observedValues = new LinkedHashSet<>();
            collectMatchingConditionValues(node, expectedField, expectedOperator, observedValues);
            return observedValues.containsAll(expectedValues);
        }
        if (node instanceof String text) {
            return (expectedOperator == null || expectedOperator.isBlank())
                    && stringContainsColumnReference(text, expectedField);
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (conditionTreeContains(item, expectedField, expectedOperator, null)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof Map<?, ?> map) {
            String field = stringValue(firstNonNull(
                    firstNonNull(map.get("field"), map.get("column")),
                    firstNonNull(map.get("name"), map.get("expr"))));
            String operator = stringValue(firstNonNull(
                    firstNonNull(map.get("op"), map.get("operator")),
                    map.get("direction")));
            if (field != null && stringContainsColumnReference(field, expectedField)
                    && operatorMatches(expectedOperator, operator)) {
                return true;
            }
            for (Object value : map.values()) {
                if (conditionTreeContains(value, expectedField, expectedOperator, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void collectMatchingConditionValues(Object node,
                                                String expectedField,
                                                String expectedOperator,
                                                Set<String> values) {
        if (node == null) {
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectMatchingConditionValues(item, expectedField, expectedOperator, values);
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            String field = stringValue(firstNonNull(
                    firstNonNull(map.get("field"), map.get("column")),
                    firstNonNull(map.get("name"), map.get("expr"))));
            String operator = stringValue(firstNonNull(
                    firstNonNull(map.get("op"), map.get("operator")),
                    map.get("direction")));
            if (field != null && stringContainsColumnReference(field, expectedField)
                    && operatorMatches(expectedOperator, operator)) {
                values.addAll(scalarValues(firstNonNull(map.get("value"), map.get("values"))));
            }
            for (Object value : map.values()) {
                collectMatchingConditionValues(value, expectedField, expectedOperator, values);
            }
        }
    }

    private Set<String> scalarValues(Object value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectScalarValues(value, values);
        return values;
    }

    private void collectScalarValues(Object value, Set<String> values) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectScalarValues(item, values);
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object item : array) {
                collectScalarValues(item, values);
            }
            return;
        }
        values.add(String.valueOf(value));
    }

    private boolean operatorMatches(String expectedOperator, String actualOperator) {
        if (expectedOperator == null || expectedOperator.isBlank()) {
            return true;
        }
        return normalizeOperator(expectedOperator).equals(normalizeOperator(actualOperator));
    }

    private String normalizeOperator(String operator) {
        return operator == null ? "" : operator.trim().toLowerCase(Locale.ROOT);
    }

    private String operatorLabel(String operator) {
        return operator == null || operator.isBlank() ? "" : " " + operator;
    }

    private String valueLabel(Object value) {
        Set<String> values = scalarValues(value);
        return values.isEmpty() ? "" : " value=" + values;
    }

    @SuppressWarnings("unchecked")
    private String describeToolArgumentPaths(List<ToolCallCollector.ToolCallRecord> candidateCalls,
                                             EcommerceTestCase.ToolArgumentRule rule) {
        List<String> callDescriptions = new ArrayList<>();
        for (int i = 0; i < candidateCalls.size(); i++) {
            ToolCallCollector.ToolCallRecord call = candidateCalls.get(i);
            Object payload = call.getArguments() == null ? null : call.getArguments().get("payload");
            if (!(payload instanceof Map<?, ?> payloadMap)) {
                callDescriptions.add("#" + i + " payload=<missing>");
                continue;
            }
            Map<String, Object> typedPayload = (Map<String, Object>) payloadMap;
            List<String> pathDescriptions = new ArrayList<>();
            for (String path : reviewPayloadPaths(typedPayload, rule.getPath())) {
                List<String> labels = new ArrayList<>();
                collectArgumentNodeLabels(typedPayload.get(path), labels, 8);
                if (!labels.isEmpty()) {
                    pathDescriptions.add(path + "=[" + String.join(", ", labels) + "]");
                }
            }
            callDescriptions.add("#" + i + " " + String.join("; ", pathDescriptions));
        }
        return "observed payload paths: " + String.join(" | ", callDescriptions);
    }

    private List<String> reviewPayloadPaths(Map<String, Object> payload, String expectedPath) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (expectedPath != null && !expectedPath.isBlank()) {
            paths.add(expectedPath);
        }
        paths.addAll(List.of("slice", "having", "groupBy", "orderBy", "columns"));
        paths.addAll(payload.keySet());
        return paths.stream().filter(payload::containsKey).toList();
    }

    private void collectArgumentNodeLabels(Object node, List<String> labels, int limit) {
        if (node == null || labels.size() >= limit) {
            return;
        }
        if (node instanceof String text) {
            labels.add(text);
            return;
        }
        if (node instanceof Number || node instanceof Boolean) {
            labels.add(String.valueOf(node));
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectArgumentNodeLabels(item, labels, limit);
                if (labels.size() >= limit) {
                    return;
                }
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            String field = stringValue(firstNonNull(
                    firstNonNull(map.get("field"), map.get("column")),
                    firstNonNull(map.get("name"), map.get("expr"))));
            String operator = stringValue(firstNonNull(
                    firstNonNull(map.get("op"), map.get("operator")),
                    map.get("direction")));
            if (field != null) {
                labels.add(field + operatorLabel(operator));
                return;
            }
            for (Object value : map.values()) {
                collectArgumentNodeLabels(value, labels, limit);
                if (labels.size() >= limit) {
                    return;
                }
            }
        }
    }

    private boolean toolCallsContainColumnReference(List<ToolCallCollector.ToolCallRecord> toolCalls, String column) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return false;
        }

        for (ToolCallCollector.ToolCallRecord call : toolCalls) {
            if (!call.isSuccess()) {
                continue;
            }
            if (resultContainsColumn(call.getResult(), column)
                    || objectContainsColumnReference(call.getArguments(), column)) {
                return true;
            }
        }
        return false;
    }

    private boolean resultContainsColumn(Object result, String column) {
        Object unwrapped = unwrapRxResponse(result);
        List<Map<String, Object>> items = extractItems(unwrapped);
        if (items != null && !items.isEmpty() && findMatchingColumn(items, column) != null) {
            return true;
        }
        return objectContainsColumnReference(unwrapped, column);
    }

    @SuppressWarnings("unchecked")
    private boolean objectContainsColumnReference(Object value, String column) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return stringContainsColumnReference(text, column);
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (objectContainsColumnReference(entry.getKey(), column)
                        || objectContainsColumnReference(entry.getValue(), column)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (objectContainsColumnReference(item, column)) {
                    return true;
                }
            }
            return false;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (objectContainsColumnReference(java.lang.reflect.Array.get(value, i), column)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean stringContainsColumnReference(String text, String column) {
        if (columnsMatch(column, text)) {
            return true;
        }
        String normalizedText = normalizeColumnText(text);
        String normalizedColumn = normalizeColumnText(column);
        String pattern = "(^|[^a-z0-9_$])" + java.util.regex.Pattern.quote(normalizedColumn) + "([^a-z0-9_$]|$)";
        return java.util.regex.Pattern.compile(pattern).matcher(normalizedText).find();
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从 AI 响应验证单个规则
     */
    private void validateRuleFromAiResponse(EcommerceTestCase.ValidationRule rule,
                                            String aiResponse,
                                            ValidationResult result) {
        String ruleName = rule.getType().name();

        try {
            switch (rule.getType()) {
                case NOT_EMPTY -> {
                    if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                        result.addPassedRule(ruleName + ": AI response is not empty");
                    } else {
                        result.addFailedRule(ruleName + ": AI response is empty");
                    }
                }

                case CONTAINS_MODEL -> {
                    String modelName = (String) rule.getValue();
                    if (aiResponse.contains(modelName)) {
                        result.addPassedRule(ruleName + ": model " + modelName + " found in response");
                    } else {
                        result.addFailedRule(ruleName + ": model " + modelName + " not found in response");
                    }
                }

                case VALUE_CONTAINS -> {
                    Object expectedValue = rule.getValue();
                    if (aiResponse.contains(expectedValue.toString())) {
                        result.addPassedRule(ruleName + ": response contains '" + expectedValue + "'");
                    } else {
                        result.addFailedRule(ruleName + ": response does not contain '" + expectedValue + "'");
                    }
                }

                default -> result.addPassedRule(ruleName + ": rule type not applicable for AI response (skipped)");
            }
        } catch (Exception e) {
            result.addError("Error validating rule " + ruleName + " from AI response: " + e.getMessage());
        }
    }

    /**
     * 从工具调用结果中构建列名到 title 的映射
     *
     * <p>从 SemanticQueryResponse 的 schema.columns 中提取列名(name)和标题(title)的映射，
     * 用于在验证 AI 响应时，将期望的列名（如 product$caption）映射到实际的中文标题（如 商品名称）。
     *
     * @param toolCalls 工具调用记录列表
     * @return 列名到标题的映射
     */
    private Map<String, String> buildColumnTitleMap(List<ToolCallCollector.ToolCallRecord> toolCalls) {
        Map<String, String> columnTitleMap = new HashMap<>();

        if (toolCalls == null || toolCalls.isEmpty()) {
            return columnTitleMap;
        }

        for (ToolCallCollector.ToolCallRecord call : toolCalls) {
            if (!call.isSuccess() || call.getResult() == null) {
                continue;
            }

            Object result = call.getResult();
            if(result instanceof RX) {
                Object data = ((RX) result).getData();
                // 处理 SemanticQueryResponse
                if (data instanceof SemanticQueryResponse sqr) {
                    extractColumnTitles(sqr, columnTitleMap);
                }
                // 处理 Map 形式的结果（可能是序列化后的）
                else if (data instanceof Map) {
                    extractColumnTitlesFromMap((Map<String, Object>) result, columnTitleMap);
                }
            }
        }

        return columnTitleMap;
    }

    /**
     * 从 SemanticQueryResponse 中提取列标题
     */
    private void extractColumnTitles(SemanticQueryResponse response, Map<String, String> columnTitleMap) {
        if (response.getSchema() == null || response.getSchema().getColumns() == null) {
            return;
        }

        for (SemanticQueryResponse.SchemaInfo.ColumnDef column : response.getSchema().getColumns()) {
            if (column.getName() != null && column.getTitle() != null) {
                columnTitleMap.put(column.getName(), column.getTitle());
            }
        }
    }

    /**
     * 从 Map 形式的结果中提取列标题
     */
    @SuppressWarnings("unchecked")
    private void extractColumnTitlesFromMap(Map<String, Object> resultMap, Map<String, String> columnTitleMap) {
        // 检查 schema 字段
        Object schema = resultMap.get("schema");
        if (schema instanceof Map) {
            Map<String, Object> schemaMap = (Map<String, Object>) schema;
            Object columns = schemaMap.get("columns");
            if (columns instanceof List) {
                for (Object colObj : (List<?>) columns) {
                    if (colObj instanceof Map) {
                        Map<String, Object> colMap = (Map<String, Object>) colObj;
                        String name = (String) colMap.get("name");
                        String title = (String) colMap.get("title");
                        if (name != null && title != null) {
                            columnTitleMap.put(name, title);
                        }
                    }
                }
            }
        }

        // 也检查 data 字段中是否有嵌套的 schema
        if (resultMap.containsKey("data")) {
            Object data = resultMap.get("data");
            if (data instanceof Map) {
                extractColumnTitlesFromMap((Map<String, Object>) data, columnTitleMap);
            }
        }
    }

    /**
     * 检查 AI 响应中是否包含标题的关键词
     *
     * <p>用于处理 AI 可能对列标题进行改写的情况。
     * 例如：标题"销售金额"可能在 AI 响应中显示为"总销售额"、"销售额"等。
     *
     * <p>策略：将中文标题按字符拆分为2字词组，检查是否至少有一个词组在响应中出现。
     * 对于长度小于4的标题，直接进行包含检查。
     *
     * @param aiResponse  AI 响应内容
     * @param columnTitle 列标题
     * @return 是否包含关键词
     */
    private boolean containsTitleKeywords(String aiResponse, String columnTitle) {
        if (columnTitle == null || columnTitle.isEmpty()) {
            return false;
        }

        // 短标题直接检查包含
        if (columnTitle.length() < 4) {
            return aiResponse.contains(columnTitle);
        }

        // 对于中文标题，提取2字词组进行匹配
        // 例如："销售金额" -> ["销售", "售金", "金额"]
        // 只要 AI 响应包含其中的核心词（如"销售"或"金额"），就认为匹配
        for (int i = 0; i <= columnTitle.length() - 2; i++) {
            String biGram = columnTitle.substring(i, i + 2);
            // 跳过包含常见停用词的组合
            if (isCommonWord(biGram)) {
                continue;
            }
            if (aiResponse.contains(biGram)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否为常见停用词组合
     */
    private boolean isCommonWord(String word) {
        // 常见的无意义或过于通用的词组
        Set<String> commonWords = Set.of(
                "的", "了", "是", "在", "有", "和", "与", "或",
                "ID", "id", "Id"
        );
        return commonWords.contains(word);
    }
}
