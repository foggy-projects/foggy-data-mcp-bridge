package com.foggyframework.dataset.model.semantic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SemanticServiceV3TestControllerIsolationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withBean(SemanticServiceV3.class, () -> mock(SemanticServiceV3.class))
            .withBean(SemanticQueryServiceV3.class, () -> mock(SemanticQueryServiceV3.class))
            .withBean(DatasetProperties.class, DatasetProperties::new)
            .withBean(SemanticQueryPayloadMapper.class,
                    () -> new SemanticQueryPayloadMapper(new ObjectMapper()))
            .withUserConfiguration(TestControllerConfiguration.class);

    @Test
    void shouldBeDisabledAndUnmappedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SemanticServiceV3TestController.class);
            MockMvcBuilders.webAppContextSetup(context)
                    .build()
                    .perform(get("/semantic/v3/test/metadata/TestModel"))
                    .andExpect(status().isNotFound());
        });
    }

    @Test
    void shouldRequireExplicitTestEnablement() {
        contextRunner
                .withPropertyValues("foggy.test.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SemanticServiceV3TestController.class);
                    MockMvcBuilders.webAppContextSetup(context)
                            .build()
                            .perform(get("/semantic/v3/test/metadata/TestModel"))
                            .andExpect(status().isOk());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SemanticServiceV3TestController.class)
    static class TestControllerConfiguration {
    }
}
