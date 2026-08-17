package com.foggyframework.mcp.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Makes the Spring AI provider optional for the standalone launcher.
 *
 * <p>Spring AI's OpenAI starter selects every OpenAI model type when its selector is absent. The
 * launcher intentionally keeps the starter available, but supplies a low-priority {@code none}
 * default for each unconfigured model type when neither its model-specific API key nor the shared
 * OpenAI API key is configured. Explicit model selectors and configured keys retain Spring AI's
 * existing behavior.
 */
public final class OptionalSpringAiEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String DEFAULTS_PROPERTY_SOURCE = "foggyOptionalSpringAiDefaults";
    private static final String COMMON_OPENAI_API_KEY = "spring.ai.openai.api-key";
    private static final Map<String, String> MODEL_API_KEYS = Map.of(
            "spring.ai.model.audio.speech", "spring.ai.openai.audio.speech.api-key",
            "spring.ai.model.audio.transcription", "spring.ai.openai.audio.transcription.api-key",
            "spring.ai.model.chat", "spring.ai.openai.chat.api-key",
            "spring.ai.model.embedding", "spring.ai.openai.embedding.api-key",
            "spring.ai.model.image", "spring.ai.openai.image.api-key",
            "spring.ai.model.moderation", "spring.ai.openai.moderation.api-key"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean hasCommonApiKey = StringUtils.hasText(environment.getProperty(COMMON_OPENAI_API_KEY));
        Map<String, Object> defaults = new LinkedHashMap<>();

        MODEL_API_KEYS.forEach((modelSelector, modelApiKey) -> {
            if (environment.containsProperty(modelSelector)) {
                return;
            }
            boolean hasModelApiKey = StringUtils.hasText(environment.getProperty(modelApiKey));
            if (!hasCommonApiKey && !hasModelApiKey) {
                defaults.put(modelSelector, "none");
            }
        });

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(DEFAULTS_PROPERTY_SOURCE, defaults));
        }
    }

    @Override
    public int getOrder() {
        // Config data must already be available before deciding whether a provider was configured.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
