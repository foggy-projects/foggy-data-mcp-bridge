package com.foggyframework.mcp.launcher;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalSpringAiEnvironmentPostProcessorTest {

    private static final List<String> MODEL_SELECTORS = List.of(
            "spring.ai.model.audio.speech",
            "spring.ai.model.audio.transcription",
            "spring.ai.model.chat",
            "spring.ai.model.embedding",
            "spring.ai.model.image",
            "spring.ai.model.moderation"
    );

    private final OptionalSpringAiEnvironmentPostProcessor processor =
            new OptionalSpringAiEnvironmentPostProcessor();

    @Test
    void defaultsUnconfiguredOpenAiModelsToNone() {
        MockEnvironment environment = new MockEnvironment();

        process(environment);

        assertThat(environment.getPropertySources()
                .contains(OptionalSpringAiEnvironmentPostProcessor.DEFAULTS_PROPERTY_SOURCE)).isTrue();
        assertThat(MODEL_SELECTORS)
                .allSatisfy(selector -> assertThat(environment.getProperty(selector)).isEqualTo("none"));
    }

    @Test
    void preservesSpringAiDefaultsWhenCommonOpenAiKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "test-key");

        process(environment);

        assertThat(environment.getPropertySources()
                .contains(OptionalSpringAiEnvironmentPostProcessor.DEFAULTS_PROPERTY_SOURCE)).isFalse();
        assertThat(MODEL_SELECTORS)
                .allSatisfy(selector -> assertThat(environment.getProperty(selector)).isNull());
    }

    @Test
    void enablesOnlyTheSpecificallyConfiguredModelAndPreservesExplicitSelectors() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.chat.api-key", "chat-key")
                .withProperty("spring.ai.model.embedding", "custom-embedding");

        process(environment);

        assertThat(environment.getProperty("spring.ai.model.chat")).isNull();
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("custom-embedding");
        assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
    }

    private void process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
    }
}
