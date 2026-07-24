package com.foggyframework.dataset.model.semantic.controller;

import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.NativeComposeQueryService;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NativeDatasetController namespace 默认值测试")
class NativeDatasetControllerTest {

    @Mock
    private SemanticQueryServiceV3 semanticQueryServiceV3;
    @Mock
    private SemanticServiceV3 semanticServiceV3;
    @Mock
    private NativeComposeQueryService nativeComposeQueryService;
    @Mock
    private SemanticModelCatalogService catalogService;
    @Mock
    private SemanticQueryPayloadMapper payloadMapper;

    private NativeDatasetController controller;

    @BeforeEach
    void setUp() {
        DatasetProperties datasetProperties = new DatasetProperties();
        datasetProperties.getRequest().setDefaultNamespace("tms-ai");
        controller = new NativeDatasetController(
                semanticQueryServiceV3,
                semanticServiceV3,
                nativeComposeQueryService,
                catalogService,
                payloadMapper,
                datasetProperties
        );
    }

    @Test
    @DisplayName("未传 X-NS 时使用 request.defaultNamespace")
    void missingNamespaceShouldUseRequestDefaultNamespace() {
        when(catalogService.buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token")))
                .thenReturn(Map.of("models", java.util.List.of()));

        controller.models("Bearer token", null);

        verify(catalogService).buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token"));
    }

    @Test
    @DisplayName("显式 X-NS 优先于 request.defaultNamespace")
    void explicitNamespaceShouldWin() {
        when(catalogService.buildCatalogResponse(anyMap(), eq("tms-biz"), eq("Bearer token")))
                .thenReturn(Map.of("models", java.util.List.of()));

        controller.models("Bearer token", " tms-biz ");

        verify(catalogService).buildCatalogResponse(anyMap(), eq("tms-biz"), eq("Bearer token"));
    }

    @Test
    @DisplayName("list_models 未传 namespace 时使用 request.defaultNamespace")
    void listModelsMissingNamespaceShouldUseRequestDefaultNamespace() {
        when(catalogService.buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token")))
                .thenReturn(Map.of("models", java.util.List.of()));

        controller.listModels(Map.of(), "Bearer token", null);

        verify(catalogService).buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token"));
    }

    @Test
    @DisplayName("query 使用 body.namespace")
    void queryShouldUseBodyNamespace() {
        when(payloadMapper.toQueryRequest(anyMap())).thenReturn(new SemanticQueryRequest());

        controller.query(Map.of("model", "Order", "payload", Map.of(), "namespace", " tms-biz "),
                "Bearer token", null);

        ArgumentCaptor<SemanticRequestContext> captor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticQueryServiceV3).queryModel(eq("Order"), any(SemanticQueryRequest.class), eq("execute"),
                captor.capture());
        assertEquals("tms-biz", captor.getValue().getNamespace());
    }

    @Test
    @DisplayName("query 中 X-NS 优先于 body.namespace")
    void queryHeaderNamespaceShouldWinOverBodyNamespace() {
        when(payloadMapper.toQueryRequest(anyMap())).thenReturn(new SemanticQueryRequest());

        controller.query(Map.of("model", "Order", "payload", Map.of(), "namespace", "tms-biz"),
                "Bearer token", " tms-ai ");

        ArgumentCaptor<SemanticRequestContext> captor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticQueryServiceV3).queryModel(eq("Order"), any(SemanticQueryRequest.class), eq("execute"),
                captor.capture());
        assertEquals("tms-ai", captor.getValue().getNamespace());
    }

    @Test
    @DisplayName("compose 使用 body.namespace")
    void composeShouldUseBodyNamespace() {
        when(nativeComposeQueryService.execute(anyMap(), eq("tms-biz"), eq("Bearer token"), anyMap()))
                .thenReturn(Map.of("status", "success"));

        controller.compose(Map.of("script", "return {}", "namespace", " tms-biz "),
                "Bearer token", null, Map.of());

        verify(nativeComposeQueryService).execute(anyMap(), eq("tms-biz"), eq("Bearer token"), anyMap());
    }

    @Test
    @DisplayName("list_models 中 X-NS 优先于 body.namespace")
    void listModelsHeaderNamespaceShouldWinOverBodyNamespace() {
        when(catalogService.buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token")))
                .thenReturn(Map.of("models", java.util.List.of()));

        controller.listModels(Map.of("namespace", "tms-biz"), "Bearer token", " tms-ai ");

        verify(catalogService).buildCatalogResponse(anyMap(), eq("tms-ai"), eq("Bearer token"));
    }

    @Test
    @DisplayName("describe_model_internal 使用 body.namespace")
    void describeModelShouldUseBodyNamespace() {
        when(semanticServiceV3.getMetadata(any(SemanticMetadataRequest.class), eq("json"),
                any(SemanticRequestContext.class))).thenReturn(new SemanticMetadataResponse());

        controller.describeModel(Map.of("model", "Order", "namespace", " tms-biz "),
                "Bearer token", null);

        ArgumentCaptor<SemanticRequestContext> captor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceV3).getMetadata(any(SemanticMetadataRequest.class), eq("json"), captor.capture());
        assertEquals("tms-biz", captor.getValue().getNamespace());
    }
}
