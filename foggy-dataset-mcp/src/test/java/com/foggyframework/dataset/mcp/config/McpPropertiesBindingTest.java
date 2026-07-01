package com.foggyframework.dataset.mcp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpProperties configuration binding")
class McpPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("should bind namespace model-list from foggy.mcp.semantic.namespaces")
    void namespaceModelList_shouldBindFromConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "foggy.mcp.semantic.model-list[0]=FactSalesQueryModel",
                        "foggy.mcp.semantic.namespaces.salesdrop.model-list[0]=SalesDropDailyQueryModel",
                        "foggy.mcp.semantic.namespaces.salesdrop.model-list[1]=SalesDropWeeklyQueryModel"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    McpProperties properties = context.getBean(McpProperties.class);
                    assertThat(properties.getSemantic().getModelList())
                            .containsExactly("FactSalesQueryModel");
                    assertThat(properties.getSemantic().getNamespaces())
                            .containsKey("salesdrop");
                    assertThat(properties.getSemantic().getNamespaces().get("salesdrop").getModelList())
                            .containsExactly("SalesDropDailyQueryModel", "SalesDropWeeklyQueryModel");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpProperties.class)
    static class TestConfig {
    }
}
