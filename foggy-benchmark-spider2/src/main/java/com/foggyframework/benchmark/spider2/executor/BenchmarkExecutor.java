package com.foggyframework.benchmark.spider2.executor;

import com.foggyframework.benchmark.spider2.config.Spider2Properties;
import com.foggyframework.benchmark.spider2.model.BenchmarkResult;
import com.foggyframework.benchmark.spider2.model.Spider2TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基准测试执行器
 *
 * 执行单个测试用例的 AI 查询
 */
@Slf4j
@Component
public class BenchmarkExecutor {

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired
    private Spider2Properties properties;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String modelName;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    /**
     * 执行单个测试用例
     */
    public BenchmarkResult execute(Spider2TestCase testCase, String systemPrompt) {
        if (chatModel == null) {
            return BenchmarkResult.failure(testCase, "none", "none",
                    "ChatModel not configured", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            // 构建提示词
            String userMessage = buildUserMessage(testCase);

            // 调用 AI
            Prompt prompt = new Prompt(userMessage);
            ChatResponse response = chatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();
            long duration = System.currentTimeMillis() - startTime;

            // 提取 token 使用量
            BenchmarkResult.TokenUsage tokenUsage = null;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                tokenUsage = BenchmarkResult.TokenUsage.builder()
                        .promptTokens((int) usage.getPromptTokens())
                        .completionTokens((int) usage.getCompletionTokens())
                        .totalTokens((int) usage.getTotalTokens())
                        .build();
            }

            BenchmarkResult result = BenchmarkResult.success(
                    testCase,
                    getProvider(),
                    modelName,
                    aiResponse,
                    new ArrayList<>(),
                    null,
                    duration
            );
            result.setTokenUsage(tokenUsage);

            log.info("{}", result.getSummary());
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Test failed: {} - {}", testCase.getInstanceId(), e.getMessage());

            return BenchmarkResult.failure(testCase, getProvider(), modelName,
                    e.getMessage(), duration);
        }
    }

    /**
     * 批量执行测试用例
     */
    public List<BenchmarkResult> executeBatch(List<Spider2TestCase> testCases, String systemPrompt) {
        List<BenchmarkResult> results = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            Spider2TestCase testCase = testCases.get(i);
            log.info("Executing test {}/{}: {}", i + 1, testCases.size(), testCase.getInstanceId());

            BenchmarkResult result = execute(testCase, systemPrompt);
            results.add(result);
        }

        return results;
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(Spider2TestCase testCase) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下问题查询数据：\n\n");
        sb.append("数据库：").append(testCase.getDatabase()).append("\n");
        sb.append("问题：").append(testCase.getQuestion()).append("\n");

        if (testCase.getExternalKnowledge() != null && !testCase.getExternalKnowledge().isEmpty()) {
            sb.append("\n附加信息：").append(testCase.getExternalKnowledge()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取 AI 提供商名称
     */
    private String getProvider() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "openai";
        }
        if (baseUrl.contains("dashscope.aliyuncs.com")) {
            return "aliyun";
        }
        if (baseUrl.contains("deepseek.com")) {
            return "deepseek";
        }
        if (baseUrl.contains("localhost:11434")) {
            return "ollama";
        }
        return "custom";
    }

    /**
     * 检查 AI 模型是否可用
     */
    public boolean isAiModelAvailable() {
        return chatModel != null;
    }
}
