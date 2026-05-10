package com.foggyframework.dataset.db.model.semantic.controller;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.semantic.service.NativeComposeQueryService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.semantic.support.SemanticQueryPayloadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

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
}
