package com.foggyframework.mcp.launcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpLauncherAnalyticsConsoleProfileConfigurationTest {

    @Test
    void enablesIndependentAnalyticsRuntimeAndConsoleWithDirectQuestionScope() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-analytics-console.yml"));
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();

        assertEquals("true", properties.getProperty("foggy.analytics.runtime-api.enabled"));
        assertEquals("true", properties.getProperty("foggy.analytics-console.enabled"));
        assertEquals("orders", properties.getProperty(
                "foggy.analytics-console.question-profiles[0].id"));
        assertEquals("default", properties.getProperty(
                "foggy.analytics-console.question-profiles[0].namespace"));
        assertEquals("FactOrderQueryModel", properties.getProperty(
                "foggy.analytics-console.question-profiles[0].model-name"));
        assertEquals("${ANALYTICS_CONSOLE_FAP_ENABLED:false}", properties.getProperty(
                "foggy.analytics-console.fap.enabled"));
    }
}
