package io.foggytest.launcher;

import com.foggyframework.analytics.console.api.AnalyticsConsoleController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.dataset.model.semantic.controller.SemanticServiceV3TestController;
import com.foggyframework.dataset.mcp.controller.ChartImageController;
import com.foggyframework.dataset.mcp.controller.DevToolsController;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.NamespaceToolPolicyService;
import com.foggyframework.dataset.mcp.service.QueryExpertService;
import com.foggyframework.dataset.mcp.tools.ExplainQueryTool;
import com.foggyframework.dataset.mcp.tools.NaturalLanguageQueryTool;
import com.foggyframework.mcp.launcher.DemoSecurityIdentityResolver;
import com.foggyframework.mcp.launcher.McpLauncherApplication;
import com.foggyframework.mcp.launcher.SavedQueryTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = McpLauncherApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.lazy-initialization=false",
                "spring.main.allow-bean-definition-overriding=false",
                "spring.autoconfigure.exclude="
                        + "com.foggyframework.dataset.mongo.DataSetMongoAutoConfiguration,"
                        + "com.foggyframework.dataset.model.mongo.MongoModelAutoConfiguration,"
                        + "com.foggyframework.dataset.vector.DataSetVectorAutoConfiguration,"
                        + "com.foggyframework.dataset.model.vector.VectorModelAutoConfiguration,"
                        + "com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration,"
                        + "com.foggyframework.dataset.model.cache.config.QueryCacheEvictionAutoConfiguration,"
                        + "com.foggyframework.dataset.model.cache.config.QueryCacheWebAutoConfiguration,"
                        + "com.foggyframework.dataset.graphql.GraphqlAddonAutoConfiguration,"
                        + "com.foggyframework.dataset.model.preagg.config.PreAggAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
                "spring.ai.openai.api-key=",
                "spring.datasource.url="
                        + "jdbc:sqlite:file:foggy-launcher-smoke-default-${random.uuid}"
                        + "?mode=memory&cache=shared",
                "foggy.auth.token=",
                "foggy.runtime-api.enabled=false",
                "foggy.data-viewer.enabled=false",
                "foggy.mcp.audit.enabled=false",
                "foggy.dataset.datasource.allow-global-fallback-for-namespace=false",
                "foggy.demo.enabled=true",
                "foggy.test.enabled=false",
                "foggy.dev-tools.enabled=false",
                "foggy.chart.storage.local.directory="
                        + "${java.io.tmpdir}/foggy-launcher-smoke-default-${random.uuid}"
        })
@AutoConfigureMockMvc
@ActiveProfiles("lite")
class LauncherDefaultRouteIsolationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAndDevelopmentControllersAreAbsentAndUnmappedByDefault() throws Exception {
        assertThat(applicationContext.getBeansOfType(DevToolsController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SemanticServiceV3TestController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SavedQueryTestController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(DemoSecurityIdentityResolver.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ChartImageController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(NamespaceToolPolicyService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ExplainQueryTool.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(QueryExpertService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NaturalLanguageQueryTool.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AnalyticsConsoleController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AnalyticsCapabilitiesController.class)).isEmpty();

        McpToolDispatcher toolDispatcher = applicationContext.getBean(McpToolDispatcher.class);
        assertThat(toolDispatcher.hasTool("dataset.explain_query")).isTrue();
        assertThat(toolDispatcher.hasTool("dataset_nl.query")).isFalse();

        mockMvc.perform(get("/dev/tables"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/semantic/v3/test/metadata/FactOrderQueryModel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/test/identity"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/charts/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/analytics-console/"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/analytics-console/index.html"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/analytics-console/api/v1/session"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/analytics/api/v1/capabilities"))
                .andExpect(status().isNotFound());
    }
}
