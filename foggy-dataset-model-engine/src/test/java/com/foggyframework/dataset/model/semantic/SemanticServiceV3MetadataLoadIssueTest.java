package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V3 metadata 模型加载诊断")
class SemanticServiceV3MetadataLoadIssueTest extends EcommerceTestSupport {

    private static final String EXISTING_MODEL = "FactOrderQueryModel";
    private static final String MISSING_MODEL = "MissingConfiguredQueryModel";

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Test
    @DisplayName("JSON metadata 应保留可用模型并返回缺失配置模型诊断")
    @SuppressWarnings("unchecked")
    void jsonMetadataIncludesModelLoadErrorsWithoutDroppingAvailableModels() {
        SemanticMetadataRequest request = tolerantRequest(List.of(EXISTING_MODEL, MISSING_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());

        assertNotNull(response);
        assertEquals("json", response.getFormat());
        Map<String, Object> data = response.getData();
        assertNotNull(data);

        Map<String, Object> models = (Map<String, Object>) data.get("models");
        assertNotNull(models);
        assertTrue(models.containsKey(EXISTING_MODEL), "可用模型仍应出现在 metadata.models");

        List<Map<String, String>> modelErrors = (List<Map<String, String>>) data.get("modelErrors");
        assertNotNull(modelErrors, "缺失配置模型应进入 modelErrors");
        assertTrue(modelErrors.stream().anyMatch(error ->
                        MISSING_MODEL.equals(error.get("model"))
                                && error.get("message") != null
                                && error.get("message").contains(MISSING_MODEL)),
                "modelErrors 应包含缺失模型名称和加载错误");
    }

    @Test
    @DisplayName("Markdown metadata 应保留可用模型并返回缺失配置模型诊断")
    void markdownMetadataIncludesModelLoadDiagnosticsWithoutThrowing() {
        SemanticMetadataRequest request = tolerantRequest(List.of(EXISTING_MODEL, MISSING_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty());

        assertNotNull(response);
        assertEquals("markdown", response.getFormat());
        String content = response.getContent();
        assertNotNull(content);
        assertTrue(content.contains(EXISTING_MODEL), "可用模型仍应出现在 markdown 模型索引");
        assertTrue(content.contains("## 模型加载诊断"), "markdown 应包含模型加载诊断段");
        assertTrue(content.contains(MISSING_MODEL), "诊断段应包含缺失模型名称");
    }

    @Test
    @DisplayName("单个配置模型缺失时，容错 metadata 返回诊断而不是失败")
    void tolerantSingleModelMarkdownReturnsDiagnostic() {
        SemanticMetadataRequest request = tolerantRequest(List.of(MISSING_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty());

        assertNotNull(response);
        assertEquals("markdown", response.getFormat());
        assertTrue(response.getContent().contains("## 模型加载诊断"));
        assertTrue(response.getContent().contains(MISSING_MODEL));
    }

    @Test
    @DisplayName("显式单模型描述未开启容错时仍按原语义失败")
    void strictSingleModelMarkdownStillFails() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of(MISSING_MODEL));

        assertThrows(RuntimeException.class, () -> semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty()));
    }

    @Test
    @DisplayName("显式多模型 metadata 未开启容错时仍按原语义失败")
    void strictMultiModelMarkdownStillFails() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of(EXISTING_MODEL, MISSING_MODEL));

        assertThrows(RuntimeException.class, () -> semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty()));
    }

    private SemanticMetadataRequest tolerantRequest(List<String> models) {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(models);
        request.setTolerateModelLoadErrors(true);
        return request;
    }
}
