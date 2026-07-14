package io.foggytest.launcher;

import com.foggyframework.dataset.db.model.semantic.controller.SemanticServiceV3TestController;
import com.foggyframework.dataset.mcp.controller.DevToolsController;
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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

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
                        + "com.foggyframework.dataset.db.model.mongo.MongoModelAutoConfiguration,"
                        + "com.foggyframework.dataset.vector.DataSetVectorAutoConfiguration,"
                        + "com.foggyframework.dataset.db.model.vector.VectorModelAutoConfiguration,"
                        + "com.foggyframework.dataset.db.model.cache.config.QueryCacheAutoConfiguration,"
                        + "com.foggyframework.dataset.db.model.cache.config.QueryCacheEvictionAutoConfiguration,"
                        + "com.foggyframework.dataset.db.model.cache.config.QueryCacheWebAutoConfiguration,"
                        + "com.foggyframework.dataset.graphql.GraphqlAddonAutoConfiguration,"
                        + "com.foggyframework.dataset.db.model.preagg.config.PreAggAutoConfiguration,"
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
                "spring.datasource.url=jdbc:sqlite:file:/tmp/foggy-launcher-smoke-enabled.db",
                "foggy.auth.token=",
                "foggy.runtime-api.enabled=false",
                "foggy.data-viewer.enabled=false",
                "foggy.mcp.audit.enabled=false",
                "foggy.dataset.datasource.allow-global-fallback-for-namespace=false",
                "foggy.demo.enabled=true",
                "foggy.test.enabled=true",
                "foggy.dev-tools.enabled=true",
                "foggy.chart.storage.local.base-dir=${java.io.tmpdir}/foggy-launcher-smoke-enabled"
        })
@AutoConfigureMockMvc
@ActiveProfiles("lite")
class LauncherExplicitTestRoutesSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explicitOptInCreatesControllersAndMapsTheirRoutes() throws Exception {
        assertThat(applicationContext.getBeansOfType(DevToolsController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SemanticServiceV3TestController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SavedQueryTestController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(DemoSecurityIdentityResolver.class)).hasSize(1);

        mockMvc.perform(get("/dev/tables"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/test/identity").header("Authorization", "Bearer analyst"))
                .andExpect(status().isOk());

        RequestMappingHandlerMapping handlerMapping = applicationContext.getBean(
                "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Set<String> mappedPatterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());
        assertThat(mappedPatterns).contains("/semantic/v3/test/metadata/{model}");
    }
}
