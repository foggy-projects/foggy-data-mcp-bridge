package com.foggyframework.mcp.launcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpLauncherLiteProfileConfigurationTest {

    @Test
    void liteProfileKeepsSingleInMemorySqliteConnectionAlive() {
        Properties properties = loadLiteProperties();

        assertEquals(
                "jdbc:sqlite:file:foggy_mcp_lite?mode=memory&cache=shared",
                properties.getProperty("spring.datasource.url"));
        assertEquals("FoggyMcpLiteSQLiteHikariCP", properties.getProperty("spring.datasource.hikari.pool-name"));
        assertEquals("1", properties.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals("1", properties.getProperty("spring.datasource.hikari.minimum-idle"));
        assertEquals("0", properties.getProperty("spring.datasource.hikari.idle-timeout"));
        assertEquals("0", properties.getProperty("spring.datasource.hikari.max-lifetime"));
        assertEquals("SELECT 1", properties.getProperty("spring.datasource.hikari.connection-test-query"));
    }

    private static Properties loadLiteProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-lite.yml"));
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
