package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsConsoleQuestionSkillBundleTest {

    private static final String ROOT =
            "fap/analytics-question-answering/";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void revisionSixDeliversCurrentModelFunctionsAndRequiredReferences()
            throws Exception {
        JsonNode metadata = json.readTree(resource("skill-metadata.json"));
        JsonNode delivery = json.readTree(resource("function-schema-delivery.json"));
        Set<String> delivered = java.util.stream.StreamSupport.stream(
                        delivery.path("functions").spliterator(), false)
                .map(item -> item.path("functionRef").asText())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> published = FapAnalyticsFunctionCatalog.descriptors().stream()
                .map(descriptor -> descriptor.projection().functionRef())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(metadata.path("revision").asInt()).isEqualTo(6);
        assertThat(metadata.path("name").asText())
                .isEqualTo("analytics-question-answering");
        assertThat(delivered).containsExactlyInAnyOrder(
                "foggy.analytics.model-dependencies.list@v2",
                "foggy.analytics.semantic-models.describe@v2",
                "foggy.analytics.semantic-queries.execute@v2",
                "foggy.analytics.query-model.run@v2",
                "foggy.analytics.compose.run@v1");
        assertThat(published).containsAll(delivered);
        assertThat(resource("SKILL.md"))
                .contains(
                        "validate",
                        "query-model.run@v2",
                        "compose.run@v1",
                        "effectiveDelivery=ON_DEMAND",
                        "describe_business_function",
                        "INPUT",
                        "complete machine-readable contract",
                        "CatalogResolution",
                        "count(orderId) as orderCount",
                        "nullFirst",
                        "$expr",
                        "maxDepth",
                        "DSL_CTE_STAGE_REFERENCE_INVALID",
                        "parentShare",
                        "baselineRatio",
                        ".join(",
                        ".union(")
                .doesNotContain(
                        "expectedModel" + "Revision",
                        "model" + "Revision");
        assertThat(resource("references/query-model-dsl.md"))
                .contains(
                        "calculatedFields",
                        "timeWindow",
                        "pivot",
                        "nullFirst",
                        "$expr",
                        "maxDepth",
                        "DSL_CTE",
                        "DSL_CTE_STAGE_REFERENCE_INVALID",
                        "count(orderId) as orderCount",
                        "parentShare",
                        "baselineRatio")
                .contains("top three orders by amount", "omit `groupBy`");
        assertThat(resource("references/compose-script.md"))
                .contains(
                        "return { plans:",
                        ".join(",
                        ".union(",
                        "Time-window composition",
                        "snake_case");
    }

    private static String resource(String relativePath) throws Exception {
        try (InputStream stream = AnalyticsConsoleQuestionSkillBundleTest.class
                .getClassLoader().getResourceAsStream(ROOT + relativePath)) {
            assertThat(stream).as(ROOT + relativePath).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
