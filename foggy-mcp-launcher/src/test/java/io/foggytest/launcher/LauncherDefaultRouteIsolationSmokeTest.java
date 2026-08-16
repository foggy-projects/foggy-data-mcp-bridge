package io.foggytest.launcher;

import com.foggyframework.dataset.model.semantic.controller.SemanticServiceV3TestController;
import com.foggyframework.dataset.mcp.controller.ChartImageController;
import com.foggyframework.dataset.mcp.controller.DevToolsController;
import com.foggyframework.dataset.mcp.service.NamespaceToolPolicyService;
import com.foggyframework.dataset.mcp.tools.ExplainQueryTool;
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
                "spring.ai.openai.api-key=test-key",
                "spring.ai.openai.base-url=http://127.0.0.1:9",
                "spring.ai.model.embedding=none",
                "spring.ai.model.image=none",
                "spring.ai.model.audio.transcription=none",
                "spring.ai.model.audio.speech=none",
                "spring.ai.model.moderation=none",
                "spring.datasource.url=jdbc:sqlite:file:/tmp/foggy-launcher-smoke-default.db",
                "foggy.auth.token=",
                "foggy.runtime-api.enabled=false",
                "foggy.data-viewer.enabled=false",
                "foggy.mcp.audit.enabled=false",
                "foggy.dataset.datasource.allow-global-fallback-for-namespace=false",
                "foggy.demo.enabled=true",
                "foggy.test.enabled=false",
                "foggy.dev-tools.enabled=false",
                "foggy.chart.storage.local.directory=${java.io.tmpdir}/foggy-launcher-smoke-default"
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

        mockMvc.perform(get("/dev/tables"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/semantic/v3/test/metadata/FactOrderQueryModel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/test/identity"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/charts/stats"))
                .andExpect(status().isOk());
    }
}
