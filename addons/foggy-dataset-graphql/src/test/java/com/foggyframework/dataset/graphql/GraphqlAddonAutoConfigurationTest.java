package com.foggyframework.dataset.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.graphql.controller.GraphqlEndpointController;
import com.foggyframework.dataset.graphql.converter.GraphqlToDslConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GraphqlAddonAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GraphqlAddonAutoConfiguration.class))
            .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void backsOffWhenAddonIsDisabled() {
        webContextRunner
                .withPropertyValues("foggy.dataset.graphql.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GraphqlToDslConverter.class);
                    assertThat(context).doesNotHaveBean(GraphqlEndpointController.class);
                });
    }

    @Test
    void createsConverterAndControllerExactlyOnceWhenPrerequisitesAreReady() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GraphqlToDslConverter.class);
            assertThat(context).hasSingleBean(GraphqlEndpointController.class);
        });
    }

    @Test
    void customBeansBackOffDefaults() {
        GraphqlToDslConverter converter = new GraphqlToDslConverter();
        GraphqlEndpointController controller = mock(GraphqlEndpointController.class);

        webContextRunner
                .withBean(GraphqlToDslConverter.class, () -> converter)
                .withBean(GraphqlEndpointController.class, () -> controller)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(GraphqlToDslConverter.class).isSameAs(converter);
                    assertThat(context).getBean(GraphqlEndpointController.class).isSameAs(controller);
                });
    }

    @Test
    void converterSwitchAlsoPreventsControllerAssembly() {
        webContextRunner
                .withPropertyValues("foggy.dataset.graphql.converter.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GraphqlToDslConverter.class);
                    assertThat(context).doesNotHaveBean(GraphqlEndpointController.class);
                });
    }

    @Test
    void missingServletDependencyIsSafe() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.web"))
                .withConfiguration(AutoConfigurations.of(GraphqlAddonAutoConfiguration.class))
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GraphqlToDslConverter.class);
                    assertThat(context).doesNotHaveBean(GraphqlEndpointController.class);
                });
    }
}
