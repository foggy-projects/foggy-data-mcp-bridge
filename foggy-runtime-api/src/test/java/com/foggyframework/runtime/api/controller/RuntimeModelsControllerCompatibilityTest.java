package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceBindingGenerationSummary;
import com.foggyframework.runtime.api.dto.ModelRefreshFailure;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureDiagnostic;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeModelOperationException;
import com.foggyframework.runtime.api.service.RuntimeModelOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level compatibility regression for the additive 9.3.3 lifecycle fields.
 */
class RuntimeModelsControllerCompatibilityTest {

    private static final String REFRESH_PATH = "/api/v1/models/refresh";
    private static final String SECRET = "controller-secret-933";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuntimeModelOperations operations;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        operations = mock(RuntimeModelOperations.class);
        RuntimeApiResponseFactory responses = new RuntimeApiResponseFactory(
                new FoggyRuntimeApiProperties());
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RuntimeModelsController(responses, operations)).build();
    }

    @Test
    void additiveSuccessPayloadMustRemainReadableByFrozenLegacyConsumer()
            throws Exception {
        ModelRefreshResponse response = new ModelRefreshResponse(
                "sales",
                "models",
                List.of("legacy-query-cache"),
                List.of("OrderModel"),
                1,
                0,
                List.of(),
                List.of("legacy warning"),
                "catalog-before-opaque",
                "catalog-after-opaque",
                "source-revision-opaque",
                List.of(new DatasourceBindingGenerationSummary(
                        "sales-primary", "jdbc", "binding-generation-opaque")),
                1,
                0,
                12L,
                RuntimeCatalogState.ACTIVE
        );
        when(operations.refreshModels(any(), eq("sales"))).thenReturn(response);

        String json = mockMvc.perform(post(REFRESH_PATH)
                        .header("X-NS", "sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"models\":[\"OrderModel\"]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(json);
        LegacyRefreshPayload legacy = objectMapper.treeToValue(
                body.path("data"), LegacyRefreshPayload.class);

        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("engine").asText()).isEqualTo("java");
        assertThat(body.path("runtimeApiVersion").asText())
                .isEqualTo("foggy-runtime-api/v1");
        assertThat(legacy).isEqualTo(new LegacyRefreshPayload(
                "sales",
                "models",
                List.of("legacy-query-cache"),
                List.of("OrderModel"),
                1,
                0,
                List.of(),
                List.of("legacy warning")
        ));
        assertThat(body.path("data").path("beforeCatalogGeneration").asText())
                .isEqualTo("catalog-before-opaque");
        assertThat(body.path("data").path("afterCatalogGeneration").asText())
                .isEqualTo("catalog-after-opaque");
        assertThat(body.path("data").path("affectedBindingGenerations").get(0)
                .path("generation").asText()).isEqualTo("binding-generation-opaque");
        assertThat(body.path("data").path("catalogState").asText())
                .isEqualTo("ACTIVE");
    }

    @Test
    void additiveFailurePayloadMustKeepLegacyErrorAndSanitizeLifecycleContext()
            throws Exception {
        RuntimeLifecycleFailureContext lifecycle =
                new RuntimeLifecycleFailureContext(
                        "sales",
                        "catalog-before-opaque",
                        null,
                        "source-revision-opaque",
                        RuntimeCatalogState.ACTIVE_OLD_PRESERVED,
                        List.of(),
                        List.of("OrderModel"),
                        List.of(new RuntimeLifecycleFailureDiagnostic(
                                "OrderModel",
                                "candidate-build",
                                "jdbc:mysql://user:" + SECRET
                                        + "@db.invalid:3306/private failed",
                                "fix the model and retry"
                        ))
                );
        RuntimeModelOperationException failure =
                new RuntimeModelOperationException(
                        "MODEL_REFRESH_FAILED",
                        "models.refresh",
                        "password=" + SECRET + " refresh failed",
                        "OrderModel",
                        "fix the model and retry",
                        false,
                        RuntimeDiagnostics.empty(),
                        RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                        lifecycle
                );
        when(operations.refreshModels(any(), eq("sales"))).thenThrow(failure);

        String json = mockMvc.perform(post(REFRESH_PATH)
                        .header("X-NS", "sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"models\":[\"OrderModel\"]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(json);
        LegacyRuntimeError legacy = objectMapper.treeToValue(
                body.path("error"), LegacyRuntimeError.class);

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(legacy.code()).isEqualTo("MODEL_REFRESH_FAILED");
        assertThat(legacy.phase()).isEqualTo("models.refresh");
        assertThat(legacy.model()).isEqualTo("OrderModel");
        assertThat(legacy.safeToAutoRepair()).isFalse();
        assertThat(body.path("error").path("lifecycleCode").asText())
                .isEqualTo("CATALOG_BUILD_FAILED");
        assertThat(body.path("error").path("lifecycle")
                .path("beforeCatalogGeneration").asText())
                .isEqualTo("catalog-before-opaque");
        assertThat(body.path("error").path("lifecycle")
                .path("afterCatalogGeneration").isNull()).isTrue();
        assertThat(json).doesNotContain(SECRET, "jdbc:mysql://", "db.invalid");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LegacyRefreshPayload(
            String namespace,
            String scope,
            List<String> clearedCaches,
            List<String> refreshedModels,
            int loadedCount,
            int failedCount,
            List<ModelRefreshFailure> failures,
            List<String> warnings
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LegacyRuntimeError(
            String code,
            String phase,
            String message,
            String model,
            String field,
            String path,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
    }
}
