package com.foggyframework.runtime.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RuntimeLifecycleSafetyContractTest {

    private static final String JDBC_URL =
            "jdbc:mysql://runtime_user:top-secret-731@db-internal.invalid:3306/private_catalog_731";
    private static final String ABSOLUTE_PATH =
            "/srv/foggy/private-tenant-731/models/BrokenOrder.qm";
    private static final String CREDENTIAL =
            "password=top-secret-731;apiKey=private-api-key-731";
    private static final String STACK_TRACE =
            "java.lang.IllegalStateException: top-secret-731\n"
                    + "\tat com.acme.private731.RefreshAuthority.publish(RefreshAuthority.java:91)";

    @Test
    void bindingSummariesRejectBlankIdentityAndEveryLifecycleListIsSorted() {
        assertAll(
                () -> assertThatThrownBy(() -> binding(null, "backend-a", "generation-a"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> binding(" ", "backend-a", "generation-a"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> binding("binding-a", null, "generation-a"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> binding("binding-a", "\t", "generation-a"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> binding("binding-a", "backend-a", null))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> binding("binding-a", "backend-a", "\n"))
                        .isInstanceOf(IllegalArgumentException.class)
        );

        List<DatasourceBindingGenerationSummary> unsorted = List.of(
                binding("binding-z", "backend-a", "generation-z"),
                binding("binding-a", "backend-z", "generation-az"),
                binding("binding-a", "backend-a", "generation-aa")
        );
        List<String> expectedOrder = List.of(
                "binding-a/backend-a",
                "binding-a/backend-z",
                "binding-z/backend-a"
        );

        ModelRefreshResponse refresh = new ModelRefreshResponse(
                "sales", "models", List.of(), List.of("OrderModel"),
                1, 0, List.of(), List.of(),
                "before", "after", "source", unsorted,
                1, 0, 1L, RuntimeCatalogState.ACTIVE
        );
        ModelValidateResponse validation = new ModelValidateResponse(
                true, "sales", ".", 0, 0, 0, 0, 1L,
                List.of(), List.of(), "before", "before", "source",
                unsorted, RuntimeCatalogState.ACTIVE
        );
        RuntimeLifecycleFailureContext failure = new RuntimeLifecycleFailureContext(
                "sales", "before", null, "source",
                RuntimeCatalogState.ACTIVE_OLD_PRESERVED,
                unsorted, List.of(), List.of()
        );

        assertAll(
                () -> assertThat(bindingOrder(refresh.affectedBindingGenerations()))
                        .containsExactlyElementsOf(expectedOrder),
                () -> assertThat(bindingOrder(validation.affectedBindingGenerations()))
                        .containsExactlyElementsOf(expectedOrder),
                () -> assertThat(bindingOrder(failure.affectedBindingGenerations()))
                        .containsExactlyElementsOf(expectedOrder)
        );
    }

    @Test
    void failureContextDeduplicatesSortsAndBoundsTargetsAndDiagnostics() {
        List<String> failedTargets = new ArrayList<>(IntStream.range(0, 105)
                .mapToObj(index -> "Model-%03d".formatted(index))
                .toList());
        failedTargets.add("Model-050");
        failedTargets.add(" Model-001 ");
        Collections.reverse(failedTargets);

        String longMessage = "x".repeat(700);
        List<RuntimeLifecycleFailureDiagnostic> diagnostics = IntStream.range(0, 55)
                .mapToObj(index -> new RuntimeLifecycleFailureDiagnostic(
                        "Model-%03d".formatted(index),
                        "candidate-build",
                        index + ":" + longMessage,
                        "Fix the logical model and retry."
                ))
                .toList();

        RuntimeLifecycleFailureContext context = new RuntimeLifecycleFailureContext(
                " sales ",
                "before-generation",
                null,
                "source-revision",
                RuntimeCatalogState.ACTIVE_OLD_PRESERVED,
                null,
                failedTargets,
                diagnostics
        );

        assertAll(
                () -> assertThat(context.namespace()).isEqualTo("sales"),
                () -> assertThat(context.affectedBindingGenerations()).isEmpty(),
                () -> assertThat(context.failedTargets())
                        .hasSize(100)
                        .isSorted()
                        .doesNotHaveDuplicates(),
                () -> assertThat(context.diagnostics()).hasSize(50),
                () -> assertThat(context.diagnostics())
                        .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                                .isNotBlank()
                                .hasSizeLessThanOrEqualTo(512))
        );
    }

    @Test
    void lifecycleAndEnvelopeDiagnosticsCannotLeakDatasourcePathCredentialOrStack()
            throws Exception {
        String unsafeMessage = String.join(" | ",
                JDBC_URL, ABSOLUTE_PATH, CREDENTIAL, STACK_TRACE);
        RuntimeLifecycleFailureDiagnostic diagnostic =
                new RuntimeLifecycleFailureDiagnostic(
                        JDBC_URL,
                        "candidate-build",
                        unsafeMessage,
                        "Inspect " + ABSOLUTE_PATH + " using " + CREDENTIAL
                );
        RuntimeLifecycleFailureContext lifecycle = new RuntimeLifecycleFailureContext(
                "sales",
                "before-generation",
                null,
                "source-revision",
                RuntimeCatalogState.STALE_ADMISSION_BLOCKED,
                List.of(binding(JDBC_URL, ABSOLUTE_PATH, CREDENTIAL)),
                List.of(JDBC_URL, ABSOLUTE_PATH, "OrderModel"),
                List.of(diagnostic)
        );
        RuntimeDiagnostics envelopeDiagnostics = new RuntimeDiagnostics(
                "select * from private_catalog_731 -- " + CREDENTIAL,
                Map.of("datasource", JDBC_URL, "path", ABSOLUTE_PATH),
                List.of(STACK_TRACE),
                nestedUnsafeAttributes(unsafeMessage)
        );

        RuntimeApiResponseFactory responseFactory = new RuntimeApiResponseFactory(
                new FoggyRuntimeApiProperties());
        RuntimeEnvelope<Object> envelope = responseFactory.fail(
                "MODEL_REFRESH_FAILED",
                "models.refresh",
                unsafeMessage,
                null,
                null,
                ABSOLUTE_PATH,
                "Retry without " + CREDENTIAL,
                false,
                envelopeDiagnostics,
                RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                lifecycle
        );

        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String lifecycleJson = mapper.writeValueAsString(envelope.error().lifecycle());
        String envelopeJson = mapper.writeValueAsString(envelope);

        assertAll(
                () -> assertSanitized(lifecycleJson),
                () -> assertSanitized(envelopeJson),
                () -> assertThat(envelopeJson).contains("OrderModel")
        );
    }

    private static Map<String, Object> nestedUnsafeAttributes(String unsafeMessage) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("jdbc", JDBC_URL);
        nested.put("absolutePath", ABSOLUTE_PATH);
        nested.put("credential", CREDENTIAL);
        nested.put("stack", STACK_TRACE);
        nested.put("messages", List.of(unsafeMessage, Map.of("again", CREDENTIAL)));
        nested.put(JDBC_URL, "hostile JDBC key");
        nested.put(ABSOLUTE_PATH, "hostile path key");
        nested.put(CREDENTIAL, "hostile credential key");
        return nested;
    }

    private static void assertSanitized(String json) {
        String normalized = json.toLowerCase(Locale.ROOT);
        assertAll(
                () -> assertThat(normalized).doesNotContain("jdbc:mysql:"),
                () -> assertThat(normalized).doesNotContain("runtime_user"),
                () -> assertThat(normalized).doesNotContain("top-secret-731"),
                () -> assertThat(normalized).doesNotContain("private-api-key-731"),
                () -> assertThat(normalized).doesNotContain("db-internal.invalid"),
                () -> assertThat(normalized).doesNotContain("private_catalog_731"),
                () -> assertThat(normalized).doesNotContain("/srv/foggy/private-tenant-731"),
                () -> assertThat(normalized).doesNotContain("com.acme.private731"),
                () -> assertThat(normalized).doesNotContain("refreshauthority.java:91")
        );
    }

    private static DatasourceBindingGenerationSummary binding(
            String bindingKey,
            String backendId,
            String generation
    ) {
        return new DatasourceBindingGenerationSummary(bindingKey, backendId, generation);
    }

    private static List<String> bindingOrder(
            List<DatasourceBindingGenerationSummary> bindings
    ) {
        return bindings.stream()
                .map(binding -> binding.bindingKey() + "/" + binding.backendId())
                .toList();
    }
}
