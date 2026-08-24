package com.foggyframework.analytics.function.fap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FapAnalyticsFunctionCatalogTest {

    private static final Set<String> PUBLICATION_FIELDS = Set.of(
            "functionRef",
            "name",
            "displayName",
            "description",
            "searchText",
            "tags",
            "inputSchema",
            "outputSchema",
            "examples",
            "schemaDigest",
            "projectionDigest");

    @Test
    void publishesEverySynchronousSdkOperationAsReadOnlyWithoutLifecycleFields() {
        List<FapAnalyticsFunctionDescriptor> descriptors =
                FapAnalyticsFunctionCatalog.descriptors();

        assertThat(descriptors).hasSize(10);
        assertThat(descriptors)
                .extracting(FapAnalyticsFunctionDescriptor::operation)
                .containsExactlyInAnyOrderElementsOf(AnalyticsFunctionOperations.FAP_V1);
        assertThat(descriptors)
                .extracting(FapAnalyticsFunctionDescriptor::sideEffect)
                .containsOnly(FapAnalyticsFunctionDescriptor.SideEffect.READ_ONLY);
        assertThat(descriptors)
                .extracting(FapAnalyticsFunctionDescriptor::confirmation)
                .containsOnly(FapAnalyticsFunctionDescriptor.Confirmation.NOT_REQUIRED);

        for (FapAnalyticsFunctionDescriptor descriptor : descriptors) {
            Map<String, Object> publication = descriptor.projection().publicationValue();
            assertThat(publication.keySet()).containsExactlyInAnyOrderElementsOf(
                    PUBLICATION_FIELDS);
            assertNoLifecycleKeys(publication);
        }
    }

    @Test
    void renderSchemaRequiresExactRevisionButNeverAcceptsCallerAuthority() {
        FapAnalyticsFunctionDescriptor descriptor = FapAnalyticsFunctionCatalog
                .findByFunctionRef(FapAnalyticsFunctionRefs.REPORTS_PREVIEW)
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>)
                descriptor.projection().inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>)
                descriptor.projection().inputSchema().get("required");

        assertThat(properties.keySet()).containsExactlyInAnyOrder(
                "bundleRef",
                "artifactRef",
                "expectedBundleRevision",
                "parameters",
                "timezone",
                "locale");
        assertThat(required).contains("expectedBundleRevision");
        assertThat(properties).doesNotContainKeys(
                "authority", "securityContext", "filters", "rawSql");
    }

    @Test
    void semanticQuerySchemaExposesOnlyTheGovernedQuestionSubset() {
        FapAnalyticsFunctionDescriptor descriptor = FapAnalyticsFunctionCatalog
                .findByFunctionRef(FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE)
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>)
                descriptor.projection().inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) properties.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> queryProperties = (Map<String, Object>)
                query.get("properties");

        assertThat(properties.keySet()).containsExactlyInAnyOrder(
                "namespace", "modelName", "expectedModelRevision", "query");
        assertThat(queryProperties.keySet()).containsExactlyInAnyOrder(
                "columns", "filters", "groupBy", "orderBy", "start", "limit",
                "returnTotal", "distinct");
        assertThat(queryProperties).doesNotContainKeys(
                "rawSql", "compose", "script", "calculatedFields", "hints",
                "extData", "authority", "securityContext");
        assertThat(query.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void canonicalDigestMatchesTheFapCrossLanguageFixture() {
        String digest = FapCanonicalDigests.json(Map.of(
                "askInvocationRef", "ask.focused",
                "binding", Map.of(
                        "workerIdentityRef",
                        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "runtimeExecutionId", "execution.focused",
                        "runtimeTaskId", "task.focused"),
                "functionInvocationId", "function-invocation.focused",
                "functionRef", "tms.order.getSummary@v1",
                "arguments", Map.of("orderIdentifier", "运单-1")));

        assertThat(digest).isEqualTo(
                "sha256:fdd62da6a189cb4a035c9a05c923a08e23901058ff556b9f43ee5db203cc7e84");
    }

    @Test
    void descriptorDigestsAreOrderIndependentAndTamperEvident() {
        FapAnalyticsFunctionDescriptor.Projection source =
                FapAnalyticsFunctionCatalog.descriptors().get(0).projection();
        Map<String, Object> reversedInput = new LinkedHashMap<>();
        source.inputSchema().entrySet().stream()
                .sorted(Map.Entry.<String, Object>comparingByKey().reversed())
                .forEach(entry -> reversedInput.put(entry.getKey(), entry.getValue()));

        FapAnalyticsFunctionDescriptor.Projection rebuilt =
                FapAnalyticsFunctionDescriptor.Projection.create(
                        source.functionRef(),
                        source.name(),
                        source.displayName(),
                        source.description(),
                        source.searchText(),
                        source.tags(),
                        reversedInput,
                        source.outputSchema(),
                        source.examples());

        assertThat(rebuilt.schemaDigest()).isEqualTo(source.schemaDigest());
        assertThat(rebuilt.projectionDigest()).isEqualTo(source.projectionDigest());
        assertThatThrownBy(() -> new FapAnalyticsFunctionDescriptor.Projection(
                source.functionRef(),
                source.name(),
                source.displayName(),
                source.description(),
                source.searchText(),
                source.tags(),
                source.inputSchema(),
                source.outputSchema(),
                source.examples(),
                "sha256:" + "0".repeat(64),
                source.projectionDigest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaDigest");
    }

    @Test
    void registryUsesExactFapRefsWithoutAliases() {
        assertThat(FapAnalyticsFunctionRefs.operation(
                FapAnalyticsFunctionRefs.DASHBOARDS_RENDER))
                .isEqualTo(AnalyticsFunctionOperations.DASHBOARDS_RENDER);
        assertThat(FapAnalyticsFunctionRefs.functionRef(
                AnalyticsFunctionOperations.DASHBOARDS_RENDER))
                .isEqualTo(FapAnalyticsFunctionRefs.DASHBOARDS_RENDER);
        assertThat(FapAnalyticsFunctionRefs.operation("analytics.dashboard.render"))
                .isNull();
        assertThat(FapAnalyticsFunctionCatalog.findByFunctionRef("unknown.fn@v1"))
                .isEmpty();
    }

    @Test
    void descriptorDigestsAreFrozenForFapPublication() {
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry(
                        FapAnalyticsFunctionRefs.ARTIFACTS_DESCRIBE,
                        List.of(
                                "sha256:dfa8f1ef4c627f33b2a9a4bf4ead3d96db95887df35b06e53d2a3667a831d782",
                                "sha256:204afedf7c4cde8415fec7b15531beefdec19d59d8c5a6d1d00b4fd3edb67048")),
                Map.entry(
                        FapAnalyticsFunctionRefs.BUNDLES_DESCRIBE,
                        List.of(
                                "sha256:6ac0356c337b0598da5471c936211f850a614910c72ce8bf88635b913f97f522",
                                "sha256:2e39f757a9c2bbe12e1377bff65023cfa71e0e0972e9d02cd4c6daf63d191d5c")),
                Map.entry(
                        FapAnalyticsFunctionRefs.BUNDLES_LIST,
                        List.of(
                                "sha256:65775d773a35d49e4cf0c1816ee120cb00c7ea7cfa8bc11cf904ab84c814bf79",
                                "sha256:1375993cc238c2bd7dc1856e5569159231430dc4494c03a1c11c7d56b10eef78")),
                Map.entry(
                        FapAnalyticsFunctionRefs.BUNDLES_VALIDATE,
                        List.of(
                                "sha256:2d268d843104a1f50faa64665462885ca1ecc4e7c1ecd8de0d7fd740559a4df2",
                                "sha256:3e8cf8c620119991b7ca318e86437b82c50680151560b06273e8e77a63819853")),
                Map.entry(
                        FapAnalyticsFunctionRefs.CAPABILITIES,
                        List.of(
                                "sha256:cd52ceb005f2b9884c1d65f24b70eae2d00d397adcb4afe95012d76a1c8de2d6",
                                "sha256:afd1253ef0f2b495e8bc13a56cac4b76226a0a93d06c72b4ee066019a0243cfe")),
                Map.entry(
                        FapAnalyticsFunctionRefs.DASHBOARDS_PREVIEW,
                        List.of(
                                "sha256:1b20a58858caf3bd4a2fef2f6da2556c3f672478a521607a22246e5cf895322f",
                                "sha256:55fe1a75604dae7e65f1cabcc577d00ef1d909ffa16b6c94e35db03696467ef7")),
                Map.entry(
                        FapAnalyticsFunctionRefs.DASHBOARDS_RENDER,
                        List.of(
                                "sha256:d96f65b27da251358d004daf60b57caf602943af74d98b83ffcfec51d2debcf1",
                                "sha256:2e64a966f9258aff27382276a77044443158a6576d9cc96a4c7f3bbe86dc9248")),
                Map.entry(
                        FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                        List.of(
                                "sha256:539ba44bdcf6a18575e35c64d0beede46208a66b53a4d6450404df9cb42187f4",
                                "sha256:683f91b7fc140a6c9bf664b4c8d2e0fca56be9d361723d54ca893442818ff74d")),
                Map.entry(
                        FapAnalyticsFunctionRefs.SEMANTIC_MODELS_DESCRIBE,
                        List.of(
                                "sha256:cc4271d6b131642a89ab95f5b49db8092f40fc51cbcca3ec879019f2dc733e1d",
                                "sha256:055082cb704b273bf88a3af0891857cb5bf26d67275fc47a7430b1e9f7973d87")),
                Map.entry(
                        FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE,
                        List.of(
                                "sha256:70ee5f284208b3efaafd10077d4ea5fdc9b8c257fdc9971b375b3bfed4fc32be",
                                "sha256:3b53bded6e0a2ed689dcbd479d6776449aaa0dfc7de9301c952889e7f0d5d98c")));

        Map<String, List<String>> actual = FapAnalyticsFunctionCatalog.descriptors()
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        descriptor -> descriptor.projection().functionRef(),
                        descriptor -> List.of(
                                descriptor.projection().schemaDigest(),
                                descriptor.projection().projectionDigest())));

        assertThat(actual).isEqualTo(expected);
    }

    private static void assertNoLifecycleKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString().toLowerCase();
                assertThat(key).isNotIn(
                        "authority",
                        "owner",
                        "acl",
                        "runtimeexecutionid",
                        "runtimetaskid",
                        "conversationref",
                        "attemptid",
                        "credential",
                        "callbackbindingref");
                assertNoLifecycleKeys(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            list.forEach(FapAnalyticsFunctionCatalogTest::assertNoLifecycleKeys);
        }
    }
}
