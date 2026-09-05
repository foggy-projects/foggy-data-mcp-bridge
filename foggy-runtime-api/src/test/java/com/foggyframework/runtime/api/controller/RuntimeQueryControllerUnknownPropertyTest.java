package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.UnknownQueryPropertyPolicy;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeQueryControllerUnknownPropertyTest {

    @Test
    void malformedJsonReturnsBadRequestBeforeQueryExecution() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":[\"amount\"]"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("error").path("code").asText())
                .isEqualTo("INVALID_DSL_SYNTAX");
        verifyNoInteractions(queryService);
    }

    @Test
    void jsonNullBodyShouldNotReachQueryExecution() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("success").asBoolean()).isFalse();
        assertThat(envelope.path("error").path("code").asText()).isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(queryService);
    }

    @Test
    void validateAndExecuteUseTheSameWarningSemantics() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        when(queryService.queryModel(eq("OrderModel"), any(), eq("validate"), any()))
                .thenReturn(new SemanticQueryResponse());
        DatasetProperties datasetProperties = new DatasetProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(datasetProperties);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload": {"groupBy": [{"field": "orderDate", "grain": "month"}]}}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode warning = objectMapper.readTree(json)
                .path("data").path("queryInputWarnings").get(0);
        assertThat(warning.path("code").asText()).isEqualTo("UNKNOWN_QUERY_PROPERTY_IGNORED");
        assertThat(warning.path("path").asText()).isEqualTo("$.groupBy[0].grain");
        assertThat(warning.path("message").asText()).contains("query results may differ");
        assertThat(warning.path("normalizedFragment").path("field").asText())
                .isEqualTo("orderDate");
        assertThat(warning.path("docsRef").asText()).isEqualTo("query-dsl/group-by");
        verify(queryService).queryModel(eq("OrderModel"), any(), eq("validate"), any());
    }

    @Test
    void strictModeReturnsStructuredBadRequestBeforeQueryExecution() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getQuery().setUnknownPropertyPolicy(UnknownQueryPropertyPolicy.STRICT);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(datasetProperties);
        RuntimeApiResponseFactory responses = new RuntimeApiResponseFactory(
                new FoggyRuntimeApiProperties());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                responses,
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "groupBy": [{"field": "orderDate", "grain": "month"}],
                                    "orderBy": [{"field": "amount", "descending": true}]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("success").asBoolean()).isFalse();
        assertThat(envelope.path("error").path("code").asText())
                .isEqualTo("UNKNOWN_QUERY_PROPERTY");
        JsonNode violations = envelope.path("diagnostics").path("attributes").path("violations");
        assertThat(violations).hasSize(2);
        assertThat(violations.get(0).path("path").asText())
                .isEqualTo("$.groupBy[0].grain");
        assertThat(violations.get(0).path("normalizedFragment").path("field").asText())
                .isEqualTo("orderDate");
        assertThat(violations.get(0).path("docsRef").asText())
                .isEqualTo("query-dsl/group-by");
        assertThat(violations.get(1).path("path").asText())
                .isEqualTo("$.orderBy[0].descending");
        verifyNoInteractions(queryService);
    }

    @Test
    void protectedPropertyFailsClosedEvenInIgnoreMode() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getQuery().setUnknownPropertyPolicy(UnknownQueryPropertyPolicy.IGNORE);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(datasetProperties);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload": {"groupBy": [{"field": "region", "rute": "bypass"}]}}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("error").path("code").asText())
                .isEqualTo("PROTECTED_QUERY_PROPERTY");
        assertThat(envelope.path("diagnostics").path("attributes").path("violations")
                .get(0).path("path").asText())
                .isEqualTo("$.groupBy[0].rute");
        verifyNoInteractions(queryService);
    }

    @Test
    void strictModeRejectsAllDuplicateJsonPropertiesBeforeBinding() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getQuery().setUnknownPropertyPolicy(UnknownQueryPropertyPolicy.STRICT);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(datasetProperties);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "groupBy": [{"field": "createdAt", "field": "paidAt"}],
                                    "limit": 10,
                                    "limit": 20
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("error").path("code").asText())
                .isEqualTo("DUPLICATE_QUERY_PROPERTY");
        JsonNode violations = envelope.path("diagnostics").path("attributes").path("violations");
        assertThat(violations).hasSize(2);
        assertThat(violations.get(0).path("path").asText())
                .isEqualTo("$.groupBy[0].field");
        assertThat(violations.get(1).path("path").asText())
                .isEqualTo("$.limit");
        verifyNoInteractions(queryService);
    }

    @Test
    void warnModeReportsDuplicateAndUsesJacksonLastOccurrence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        when(queryService.queryModel(eq("OrderModel"), any(), eq("execute"), any()))
                .thenReturn(new SemanticQueryResponse());
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getQuery().setUnknownPropertyPolicy(UnknownQueryPropertyPolicy.WARN);
        @SuppressWarnings("unchecked")
        ObjectProvider<DatasetProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(datasetProperties);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeQueryController(
                new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                queryService,
                objectMapper,
                provider)).build();

        String json = mockMvc.perform(post("/api/v1/query/OrderModel/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload": {"columns": ["amount"], "limit": 10, "limit": 20}}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.path("data").path("queryInputWarnings")).hasSize(1);
        assertThat(envelope.path("data").path("queryInputWarnings").get(0).path("code").asText())
                .isEqualTo("DUPLICATE_QUERY_PROPERTY");
        ArgumentCaptor<SemanticQueryRequest> request =
                ArgumentCaptor.forClass(SemanticQueryRequest.class);
        verify(queryService).queryModel(eq("OrderModel"), request.capture(), eq("execute"), any());
        assertThat(request.getValue().getLimit()).isEqualTo(20);
    }
}
