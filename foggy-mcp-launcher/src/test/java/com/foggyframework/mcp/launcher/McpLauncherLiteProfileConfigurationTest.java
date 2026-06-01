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
        assertEquals("ServiceTicketQueryModel", properties.getProperty("foggy.mcp.semantic.model-list[3]"));
    }

    @Test
    void liteOrderFixtureExposesShipDateForFieldComparisonCoverage() throws IOException {
        String liteSchema = classpathText("db/lite-demo-schema.sql");
        String liteData = classpathText("db/lite-demo-data.sql");
        String orderModel = classpathText("foggy/templates/ecommerce/model/FactOrderModel.tm");
        String orderQueryModel = classpathText("foggy/templates/ecommerce/query/FactOrderQueryModel.qm");

        assertTrue(liteSchema.contains("ship_date"), "lite fact_order schema should expose ship_date");
        assertTrue(liteData.contains("ORD-LITE-0006"), "lite fixture should include a predicate-governance anomaly row");
        assertTrue(liteData.contains("'ORD-LITE-0006', 20240104, NULL"), "lite fixture should include a customer-null branch");
        assertTrue(orderModel.contains("column: 'ship_date'"), "FactOrderModel should map ship_date");
        assertTrue(orderQueryModel.contains("{ ref: fo.shipDate }"), "FactOrderQueryModel should expose shipDate");
    }

    @Test
    void liteOrderFixtureExposesSalesTeamForOrderSemanticCoverage() throws IOException {
        String liteSchema = classpathText("db/lite-demo-schema.sql");
        String liteData = classpathText("db/lite-demo-data.sql");
        String orderModel = classpathText("foggy/templates/ecommerce/model/FactOrderModel.tm");
        String orderQueryModel = classpathText("foggy/templates/ecommerce/query/FactOrderQueryModel.qm");

        assertTrue(liteSchema.contains("CREATE TABLE dim_sales_team"), "lite schema should define sales team dimension");
        assertTrue(liteSchema.contains("sales_team_key  INTEGER"), "lite fact_order schema should link sales team");
        assertTrue(liteData.contains("INSERT INTO dim_sales_team"), "lite fixture should seed sales teams");
        assertTrue(liteData.contains("East Direct Sales Team"), "lite fixture should include governed sales team captions");
        assertTrue(orderModel.contains("name: 'salesTeam'"), "FactOrderModel should map salesTeam dimension");
        assertTrue(orderQueryModel.contains("{ ref: fo.salesTeam }"), "FactOrderQueryModel should expose salesTeam");
        assertTrue(orderQueryModel.contains("{ ref: fo.salesTeam$managerName }"), "FactOrderQueryModel should expose sales team owner");
    }

    @Test
    void liteServiceTicketFixtureExposesSlaCoverageModel() throws IOException {
        String liteSchema = classpathText("db/lite-demo-schema.sql");
        String liteData = classpathText("db/lite-demo-data.sql");
        String ticketModel = classpathText("foggy/templates/ecommerce/model/ServiceTicketModel.tm");
        String ticketQueryModel = classpathText("foggy/templates/ecommerce/query/ServiceTicketQueryModel.qm");

        assertTrue(liteSchema.contains("CREATE TABLE dim_team"), "lite schema should define service team dimension");
        assertTrue(liteSchema.contains("CREATE TABLE service_ticket"), "lite schema should define service ticket fact");
        assertTrue(liteSchema.contains("first_response_at"), "lite service ticket schema should expose first response time");
        assertTrue(liteData.contains("INSERT INTO service_ticket"), "lite fixture should seed service tickets");
        assertTrue(liteData.contains("SLA-LITE-003"), "lite fixture should include an unresponded service ticket row");
        assertTrue(liteData.contains("North Support Team"), "lite fixture should include governed service team captions");
        assertTrue(ticketModel.contains("name: 'team'"), "ServiceTicketModel should map team dimension");
        assertTrue(ticketModel.contains("column: 'first_response_at'"), "ServiceTicketModel should map first_response_at");
        assertTrue(ticketQueryModel.contains("name: 'ServiceTicketQueryModel'"), "ServiceTicketQueryModel should be available");
        assertTrue(ticketQueryModel.contains("{ ref: st.team }"), "ServiceTicketQueryModel should expose service team");
        assertTrue(ticketQueryModel.contains("{ ref: st.firstResponseAt }"), "ServiceTicketQueryModel should expose firstResponseAt");
        assertTrue(ticketQueryModel.contains("{ ref: st.ticketCount }"), "ServiceTicketQueryModel should expose ticket denominator");
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
