package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionCatalog;
import com.foggyframework.analytics.function.fap.FapAnalyticsQuestionFunctionCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsConsoleQuestionSkillBundleTest {

    private static final String ROOT =
            "fap/analytics-question-answering/";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void revisionSevenDeliversCurrentModelFunctionsAndRequiredReferences()
            throws Exception {
        JsonNode metadata = json.readTree(resource("skill-metadata.json"));
        JsonNode delivery = json.readTree(resource("function-schema-delivery.json"));
        JsonNode publication = json.readTree(resource("host-publication-manifest.json"));
        Set<String> delivered = java.util.stream.StreamSupport.stream(
                        delivery.path("functions").spliterator(), false)
                .map(item -> item.path("functionRef").asText())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> published = FapAnalyticsFunctionCatalog.descriptors().stream()
                .map(descriptor -> descriptor.projection().functionRef())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(metadata.path("revision").asInt()).isEqualTo(7);
        assertThat(metadata.path("name").asText())
                .isEqualTo("analytics-question-answering");
        assertThat(delivered).containsExactlyInAnyOrder(
                "foggy.analytics.model-dependencies.list@v3",
                "foggy.analytics.semantic-models.describe@v2",
                "foggy.analytics.semantic-queries.execute@v2",
                "foggy.analytics.query-model.run@v2",
                "foggy.analytics.compose.run@v1");
        assertThat(published).containsAll(delivered);
        assertThat(publication.path("contractVersion").asText())
                .isEqualTo("foggy.analytics.question-host-publication.v1");
        assertThat(publication.path("publicationMode").asText())
                .isEqualTo("HOST_MANAGED_EXPLICIT");
        assertThat(publication.path("launcherStartupMutationAllowed").asBoolean())
                .isFalse();
        assertThat(publication.path("skill").path("name").asText())
                .isEqualTo(metadata.path("name").asText());
        assertThat(publication.path("skill").path("revision").asInt())
                .isEqualTo(metadata.path("revision").asInt());

        Map<String, DigestPair> expectedPublications =
                FapAnalyticsQuestionFunctionCatalog.descriptors().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                descriptor -> descriptor.projection().functionRef(),
                                descriptor -> new DigestPair(
                                        descriptor.projection().schemaDigest(),
                                        descriptor.projection().projectionDigest())));
        assertThat(publication.path("functions").size()).isEqualTo(5);
        java.util.stream.StreamSupport.stream(
                publication.path("functions").spliterator(), false)
                .forEach(item -> {
                    DigestPair expected =
                            expectedPublications.get(item.path("functionRef").asText());
                    assertThat(expected).isNotNull();
                    assertThat(item.path("schemaDigest").asText())
                            .isEqualTo(expected.schemaDigest());
                    assertThat(item.path("projectionDigest").asText())
                            .isEqualTo(expected.projectionDigest());
                });
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

    private record DigestPair(
            String schemaDigest,
            String projectionDigest) {
    }
}
