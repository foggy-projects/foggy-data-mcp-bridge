package com.foggyframework.mcp.launcher;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = McpLauncherApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.lazy-initialization=false",
                "spring.datasource.url="
                        + "jdbc:sqlite:file:analytics-console-smoke?mode=memory&cache=shared",
                "foggy.data-viewer.enabled=false",
                "foggy.mcp.audit.enabled=false",
                "foggy.analytics-console.fap.enabled=false",
                "foggy.analytics-console.catalog-path="
                        + "${java.io.tmpdir}/foggy-analytics-console-smoke-${random.uuid}/catalog.json",
                "foggy.analytics-console.function-trace-path="
                        + "${java.io.tmpdir}/foggy-analytics-console-smoke-${random.uuid}/function-traces"
        })
@ActiveProfiles({"lite", "analytics-console"})
@AutoConfigureMockMvc
class AnalyticsConsoleDirectQuestionSmokeTest {

    @Autowired
    private AnalyticsSemanticFunctionOperations semantic;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesEmbeddedConsoleAndAnalyticsRoutesOnlyAfterProfileOptIn() throws Exception {
        mockMvc.perform(get("/analytics-console/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/analytics-console/index.html"));
        mockMvc.perform(get("/analytics-console/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/analytics-console/api/v1/session"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/analytics/api/v1/capabilities"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/analytics-console/api/v1/agent/question-profiles"))
                .andExpect(status().isNotFound());
    }

    @Test
    void describesTheCurrentConfiguredQuestionModelThroughTheAnalyticsLane() {
        AnalyticsSemanticModelDescription description = semantic.describeModel(
                new AnalyticsSemanticModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        new AnalyticsFunctionAuthority("console", "local-dev-only"),
                        new AnalyticsFunctionRequestContext(
                                "question-smoke", "question-smoke")),
                new AnalyticsFunctionContext("question-smoke", "question-smoke"));

        assertThat(description.content()).contains(
                "FactOrderQueryModel",
                "amount");

        var result = semantic.executeQuery(
                new AnalyticsSemanticQueryFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        new AnalyticsSemanticQuery(
                                List.of("amount"),
                                List.of(),
                                List.of(),
                                List.of(),
                                0,
                                5,
                                true,
                                false),
                        new AnalyticsFunctionAuthority("console", "local-dev-only"),
                        new AnalyticsFunctionRequestContext(
                                "question-query-smoke", "question-query-smoke")),
                new AnalyticsFunctionContext(
                        "question-query-smoke", "question-query-smoke"));

        assertThat(result.columns())
                .extracting(column -> column.name())
                .contains("amount");
        assertThat(result.rows()).allSatisfy(row ->
                assertThat(row).containsOnlyKeys("amount"));
    }
}
