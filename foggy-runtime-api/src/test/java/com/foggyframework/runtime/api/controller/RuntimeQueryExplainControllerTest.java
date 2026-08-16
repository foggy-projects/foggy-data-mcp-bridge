package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainRequest;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainService;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeQueryExplainControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SemanticExplainService explainService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        explainService = mock(SemanticExplainService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RuntimeApiResponseFactory responses = new RuntimeApiResponseFactory(
                new FoggyRuntimeApiProperties());
        mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryExplainController(
                responses, explainService, objectMapper, provider)).build();
    }

    @Test
    void definitionRequestDoesNotRequirePayload() throws Exception {
        when(explainService.explain(eq("OrderModel"), any(), any()))
                .thenReturn(response(SemanticExplainResponse.Basis.DEFINITION));

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/explain")
                        .header("X-NS", "sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[\"amount\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("success").asBoolean()).isTrue();
        assertThat(envelope.path("data").path("schemaVersion").asText())
                .isEqualTo("foggy-semantic-explain/v1");
        assertThat(envelope.path("data").path("basis").asText()).isEqualTo("DEFINITION");

        ArgumentCaptor<SemanticExplainRequest> request =
                ArgumentCaptor.forClass(SemanticExplainRequest.class);
        ArgumentCaptor<SemanticRequestContext> context =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(explainService).explain(eq("OrderModel"), request.capture(), context.capture());
        assertThat(request.getValue().getPayload()).isNull();
        assertThat(request.getValue().getFields()).containsExactly("amount");
        assertThat(context.getValue().getNamespace()).isEqualTo("sales");
    }

    @Test
    void recompiledRequestKeepsPayloadInsideIndependentExplainContract() throws Exception {
        when(explainService.explain(eq("OrderModel"), any(), any()))
                .thenReturn(response(SemanticExplainResponse.Basis.RECOMPILED));

        mockMvc.perform(post("/api/v1/query/OrderModel/explain")
                        .header("Authorization", "Bearer caller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {"columns": ["amount"], "limit": 1},
                                  "depth": "DETAILED",
                                  "includeSql": true,
                                  "includePhysicalNames": true
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<SemanticExplainRequest> request =
                ArgumentCaptor.forClass(SemanticExplainRequest.class);
        ArgumentCaptor<SemanticRequestContext> context =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(explainService).explain(eq("OrderModel"), request.capture(), context.capture());
        assertThat(request.getValue().getPayload().getColumns()).containsExactly("amount");
        assertThat(request.getValue().getDepth()).isEqualTo(SemanticExplainRequest.Depth.DETAILED);
        assertThat(request.getValue().isIncludeSql()).isTrue();
        assertThat(request.getValue().isIncludePhysicalNames()).isTrue();
        assertThat(context.getValue().getAuthorization()).isEqualTo("Bearer caller");
    }

    private SemanticExplainResponse response(SemanticExplainResponse.Basis basis) {
        return new SemanticExplainResponse(
                SemanticExplainResponse.SCHEMA_VERSION,
                basis,
                new SemanticExplainResponse.DefinitionTrace(
                        "OrderModel", null, List.of(), List.of()),
                new SemanticExplainResponse.CompilationTrace(
                        null, null, List.of(), List.of(), null, List.of()),
                null,
                null,
                null,
                null,
                List.of());
    }
}
