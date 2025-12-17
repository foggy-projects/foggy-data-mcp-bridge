package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.service.McpToolCallbackFactory;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.ToolCallCollector;
import com.foggyframework.dataset.mcp.tools.McpTool;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.*;

/**
 * 基于 Spring AI 的测试执行器
 *
 * <p>使用 Spring AI 的 Tool Calling 机制执行 MCP 工具测试。
 * 工具定义从业务代码的 schemas/ 目录加载，与业务代码保持一致。
 *
 * <p>参考文档：
 * <a href="https://docs.spring.io/spring-ai/reference/api/tools.html">Spring AI Tool Calling</a>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
public class SpringAiTestExecutor {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        你是一个数据分析助手，能够帮助用户查询和分析电商数据。

        ## 使用说明

        1. 先使用 dataset_get_metadata 工具获取可用模型列表
        2. 使用 dataset_describe_model_internal 工具了解模型的字段定义(可选)
        3. 使用 dataset_query_model_v2 工具执行数据查询

        ## 注意事项

        - 列名使用 $ 分隔维度层级，如 product$caption 表示商品名称
        - 使用 groupBy 时，columns 中的非聚合列必须在 groupBy 中
        - 数值型度量可以直接使用，如 salesAmount, quantity
        - 今天日期: {current_date}

        请根据用户的问题，选择合适的工具来获取数据并回答问题。
        """;

    private final McpToolDispatcher mcpToolDispatcher;
    private final McpToolCallbackFactory toolCallbackFactory;
    private final String availableModels;
    private final ResultValidator resultValidator;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final String traceId;
    private final String authorization;

    /**
     * 创建执行器（使用业务代码的工具定义）
     *
     * @param mcpToolDispatcher   MCP 工具分发器
     * @param toolCallbackFactory 工具回调工厂
     * @param availableModels     可用模型描述
     * @param chatModel           Spring AI ChatModel
     * @param traceId             追踪ID
     * @param authorization       授权令牌
     */
    public SpringAiTestExecutor(
            McpToolDispatcher mcpToolDispatcher,
            McpToolCallbackFactory toolCallbackFactory,
            String availableModels,
            ChatModel chatModel,
            String traceId,
            String authorization) {
        this.mcpToolDispatcher = mcpToolDispatcher;
        this.toolCallbackFactory = toolCallbackFactory;
        this.availableModels = availableModels;
        this.resultValidator = new ResultValidator();
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
        this.traceId = traceId;
        this.authorization = authorization;
    }

    /**
     * 执行测试用例（使用 Spring AI Tool Calling）
     *
     * <p>通过 ChatClient.tools() 方法将 MCP 工具注册到 AI 模型，
     * AI 会自动选择并调用合适的工具。工具定义来自业务代码的 schemas/ 目录。
     *
     * <p>使用 {@link ToolCallCollector} 收集工具调用的详细信息，
     * 包括工具名称、参数、结果、耗时等。
     */
    public AiTestResult executeTest(EcommerceTestCase testCase) {
        if (chatModel == null) {
            return AiTestResult.builder()
                    .testCaseId(testCase.getId())
                    .provider("spring-ai")
                    .modelName("not-configured")
                    .success(false)
                    .errorMessage("ChatModel not available")
                    .build();
        }

        Instant startTime = Instant.now();
        log.info("Executing test [{}] with Spring AI Tool Calling", testCase.getId());
        log.info("ChatModel class: {}", chatModel.getClass().getName());

        // 创建工具调用收集器
        ToolCallCollector collector = new ToolCallCollector(testCase.getId());

        try {
            // 构建系统提示
            String systemPrompt = buildSystemPrompt();

            // 获取所有 MCP 工具并转换为 ToolCallback（带收集器）
            List<McpTool> mcpTools = mcpToolDispatcher.getDataAnalysisTools();
            ToolCallback[] toolCallbacks = toolCallbackFactory.createToolCallbacks(
                    mcpTools, traceId, authorization, collector);

            log.info("Registered {} tools from business code:", toolCallbacks.length);
            for (ToolCallback tc : toolCallbacks) {
                log.info("  - {} : {}", tc.getToolDefinition().name(),
                        truncate(tc.getToolDefinition().description(), 80));
            }

            // 发送用户问题
            String userQuestion = testCase.getQuestion();
            log.info("User question: {}", userQuestion);

            // 使用 Spring AI Tool Calling - 注册业务代码的工具
            ChatClient chatClient = ChatClient.create(chatModel);
            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userQuestion)
                    .toolCallbacks(toolCallbacks)  // 使用业务代码的工具定义
                    .call()
                    .content();

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            log.info("AI Response: {}", aiResponse);

            // 从收集器获取工具调用信息
            List<ToolCallCollector.ToolCallRecord> toolCalls = collector.getToolCalls();
            log.info("Tool calls collected: {}", collector.getSummary());

            // 验证结果
            ResultValidator.ValidationResult validation = resultValidator.validateFromAiResponse(
                    testCase, aiResponse, toolCalls);

             return AiTestResult.builder()
                    .testCaseId(testCase.getId())
                    .provider("spring-ai")
                    .modelName(getModelName())
                    .success(validation.isPassed())
                    .question(testCase.getQuestion())
                    .aiResponse(aiResponse)
                    .toolCallRecords(toolCalls)
                    .validationResult(validation)
                    .durationMs(durationMs)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Test execution failed: {}", e.getMessage(), e);
            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            // 即使失败也记录已收集的工具调用
            List<ToolCallCollector.ToolCallRecord> toolCalls = collector.getToolCalls();
            if (!toolCalls.isEmpty()) {
                log.info("Tool calls before failure: {}", collector.getSummary());
            }

            return AiTestResult.builder()
                    .testCaseId(testCase.getId())
                    .provider("spring-ai")
                    .modelName(getModelName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .toolCallRecords(toolCalls)
                    .durationMs(durationMs)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        }
    }

    /**
     * 直接执行工具（不通过 AI）
     *
     * <p>用于验证工具本身的正确性，绕过 AI 直接调用工具函数。
     */
    public AiTestResult executeToolDirectly(EcommerceTestCase testCase) {
        Instant startTime = Instant.now();
        log.info("Executing tool directly for test [{}]", testCase.getId());

        try {
            String toolName = testCase.getExpectedTool();
            Map<String, Object> arguments = buildToolArguments(testCase);

            // 使用 McpToolDispatcher 执行工具
            Object result = mcpToolDispatcher.executeTool(toolName, arguments, traceId, authorization);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            // 手动构建工具调用记录
            ToolCallCollector.ToolCallRecord record = ToolCallCollector.ToolCallRecord.builder()
                    .toolName(toolName)
                    .springToolName(toolName.replace(".", "_"))
                    .arguments(arguments)
                    .result(result)
                    .success(true)
                    .durationMs(durationMs)
                    .timestamp(Instant.now())
                    .sequence(0)
                    .build();

            ResultValidator.ValidationResult validation = resultValidator.validate(testCase, result);

            return AiTestResult.builder()
                    .testCaseId(testCase.getId())
                    .provider("direct")
                    .modelName("tool-execution")
                    .success(validation.isPassed())
                    .question(testCase.getQuestion())
                    .toolCallRecords(List.of(record))
                    .toolResult(result)
                    .validationResult(validation)
                    .durationMs(durationMs)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Direct tool execution failed: {}", e.getMessage(), e);
            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            return AiTestResult.builder()
                    .testCaseId(testCase.getId())
                    .provider("direct")
                    .modelName("tool-execution")
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
        }
    }

    private String getModelName() {
        // 尝试从 ChatModel 获取模型名称
        try {
            var field = chatModel.getClass().getDeclaredField("defaultOptions");
            field.setAccessible(true);
            var options = field.get(chatModel);
            if (options != null) {
                var modelField = options.getClass().getDeclaredMethod("getModel");
                Object model = modelField.invoke(options);
                if (model != null) {
                    return model.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract model name: {}", e.getMessage());
        }
        return "unknown";
    }

    private String buildSystemPrompt() {
        return SYSTEM_PROMPT_TEMPLATE
//                .replace("{available_models}", availableModels)
                .replace("{current_date}", java.time.LocalDate.now().toString());
    }

    private Map<String, Object> buildToolArguments(EcommerceTestCase testCase) {
        Map<String, Object> args = new HashMap<>();

        switch (testCase.getExpectedTool()) {
            case "dataset.get_metadata" -> {
                // 无参数
            }
            case "dataset.describe_model_internal" -> {
                args.put("model", testCase.getTargetModel());
            }
            case "dataset.query_model_v2" -> {
                args.put("model", testCase.getTargetModel());
                args.put("mode", "execute");

                Map<String, Object> payload = new HashMap<>();
                // 从 expected 推断 columns
                if (testCase.getExpected() != null &&
                        testCase.getExpected().getRequiredColumns() != null &&
                        !testCase.getExpected().getRequiredColumns().isEmpty()) {
                    payload.put("columns", testCase.getExpected().getRequiredColumns());
                } else {
                    // 默认列 - 使用实际存在的列名
                    payload.put("columns", List.of("product$caption", "salesAmount"));
                }
                payload.put("limit", 20);
                args.put("payload", payload);
            }
        }

        return args;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /**
     * AI 测试结果
     */
    @Data
    @Builder
    public static class AiTestResult {
        private String testCaseId;
        private String provider;
        private String modelName;
        private boolean success;
        private String errorMessage;
        private String question;
        private String aiResponse;
        private Object toolResult;
        /**
         * 完整的工具调用记录列表
         */
        private List<ToolCallCollector.ToolCallRecord> toolCallRecords;
        private ResultValidator.ValidationResult validationResult;
        private long durationMs;
        private Instant startTime;
        private Instant endTime;

        public String getSummary() {
            if (success) {
                return String.format("✓ [%s] %s/%s - %dms",
                        testCaseId, provider, modelName, durationMs);
            } else {
                String error = errorMessage != null ? errorMessage :
                        (validationResult != null ? String.join("; ", validationResult.getErrors()) : "unknown");
                return String.format("✗ [%s] %s/%s - %s",
                        testCaseId, provider, modelName, error);
            }
        }

        /**
         * 获取工具调用摘要
         */
        public String getToolCallSummary() {
            if (toolCallRecords == null || toolCallRecords.isEmpty()) {
                return "No tool calls";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Tool calls: %d", toolCallRecords.size()));

            for (ToolCallCollector.ToolCallRecord record : toolCallRecords) {
                sb.append("\n  ").append(record.getSequence()).append(". ")
                        .append(record.getToolName())
                        .append(" - ")
                        .append(record.isSuccess() ? "OK" : "FAILED")
                        .append(" (").append(record.getDurationMs()).append("ms)");

                if (!record.isSuccess() && record.getError() != null) {
                    sb.append("\n     Error: ").append(record.getError());
                }
            }

            return sb.toString();
        }

        /**
         * 获取调用的所有工具名称
         */
        public List<String> getCalledToolNames() {
            if (toolCallRecords == null) {
                return List.of();
            }
            return toolCallRecords.stream()
                    .map(ToolCallCollector.ToolCallRecord::getToolName)
                    .toList();
        }
    }
}
