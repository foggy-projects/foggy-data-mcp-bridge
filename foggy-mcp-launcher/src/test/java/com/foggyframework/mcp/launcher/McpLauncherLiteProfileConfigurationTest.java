package com.foggyframework.mcp.launcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpLauncherLiteProfileConfigurationTest {

    @Test
    void liteProfileUsesPersistentSqliteFileForLongRunningMatrix() {
        Properties properties = loadLiteProperties();

        assertEquals(
                "jdbc:sqlite:${MCP_LITE_SQLITE_PATH:${java.io.tmpdir}/foggy_mcp_lite.db}",
                properties.getProperty("spring.datasource.url"));
        assertEquals("FoggyMcpLiteSQLiteHikariCP", properties.getProperty("spring.datasource.hikari.pool-name"));
        assertEquals("1", properties.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals("1", properties.getProperty("spring.datasource.hikari.minimum-idle"));
        assertEquals("0", properties.getProperty("spring.datasource.hikari.idle-timeout"));
        assertEquals("0", properties.getProperty("spring.datasource.hikari.max-lifetime"));
        assertEquals("SELECT 1", properties.getProperty("spring.datasource.hikari.connection-test-query"));
    }

    @Test
    void liteProfileExposesCoverageModelsForMediumMatrix() {
        Properties properties = loadLiteProperties();

        assertEquals("FactOrderQueryModel", properties.getProperty("foggy.mcp.semantic.model-list[0]"));
        assertEquals("CrmLead", properties.getProperty("foggy.mcp.semantic.model-list[1]"));
        assertEquals("CustomerOrderLifecycleQueryModel", properties.getProperty("foggy.mcp.semantic.model-list[2]"));
    }

    @Test
    void liteOrderFixtureExposesShipDateForFieldComparisonCoverage() throws IOException {
        String liteSchema = classpathText("db/lite-demo-schema.sql");
        String liteData = classpathText("db/lite-demo-data.sql");
        String orderModel = classpathText("foggy/templates/ecommerce/model/FactOrderModel.tm");
        String orderQueryModel = classpathText("foggy/templates/ecommerce/query/FactOrderQueryModel.qm");

        assertTrue(liteSchema.contains("ship_date"), "lite fact_order schema should expose ship_date");
        assertTrue(liteData.contains("ORD-LITE-0006"), "lite fixture should include a predicate-governance anomaly row");
        assertTrue(liteData.contains("NULL, 2, 1, 1, 1, 199.00"), "lite fixture should include a customer-null branch");
        assertTrue(orderModel.contains("column: 'ship_date'"), "FactOrderModel should map ship_date");
        assertTrue(orderQueryModel.contains("{ ref: fo.shipDate }"), "FactOrderQueryModel should expose shipDate");
    }

    private static Properties loadLiteProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-lite.yml"));
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    private static String classpathText(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
