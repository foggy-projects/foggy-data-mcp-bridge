package com.foggyframework.dataset.mcp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Import;

/**
 * Optional natural-language query assembly.
 *
 * <p>The semantic MCP tools do not require an AI provider. Keep the query expert and its
 * natural-language facade out of the application context unless Spring AI has supplied both a
 * {@link ChatModel} and the corresponding {@link ChatClient.Builder}.
 */
@AutoConfiguration(
        after = DatasetMcpAutoConfiguration.class,
        afterName = {
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration"
        }
)
@ConditionalOnBean({ChatModel.class, ChatClient.Builder.class})
@Import({
        com.foggyframework.dataset.mcp.service.QueryExpertService.class,
        com.foggyframework.dataset.mcp.tools.NaturalLanguageQueryTool.class
})
public class DatasetMcpAiAutoConfiguration {
}
