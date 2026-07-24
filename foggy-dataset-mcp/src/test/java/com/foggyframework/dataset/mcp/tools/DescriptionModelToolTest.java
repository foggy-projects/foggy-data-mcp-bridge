package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DescriptionModelTool 单元测试")
@ExtendWith(MockitoExtension.class)
class DescriptionModelToolTest {

    @Mock
    private DatasetAccessor datasetAccessor;

    private DescriptionModelTool descriptionModelTool;

    @BeforeEach
    void setUp() {
        descriptionModelTool = new DescriptionModelTool(datasetAccessor);
    }

    @Test
    @DisplayName("内部程序化调用显式传 format=json 时，必须按 JSON 分支透传（保留 Odoo 列权限/字段映射链路）")
    void shouldHonorExplicitJsonFormatFromInternalCallers() {
        SemanticMetadataResponse response = new SemanticMetadataResponse();
        response.setData(Map.of("models", List.of()));
        when(datasetAccessor.describeModel(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(RX.success(response));

        // 模拟 Odoo Pro Gateway 模式下的 column_governance / field_mapping_registry
        // 这类内部程序化消费方——它们知道 format 参数存在，并显式传 json。
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("model", "OdooHrEmployeeQueryModel");
        arguments.put("format", "json");
        arguments.put("deniedColumns", List.of(
                Map.of("table", "hr_employee", "columns", List.of("gender", "marital"))
        ));
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId("trace-desc")
                .authorization("Bearer token")
                .namespace("odoo")
                .build();

        Object result = descriptionModelTool.execute(arguments, context);

        assertNotNull(result);
        assertInstanceOf(RX.class, result);
        // 内部调用方显式请求 json，必须按 json 透传，不得被 AI Chat 契约覆盖
        verify(datasetAccessor).describeModel(
                eq("OdooHrEmployeeQueryModel"),
                eq("json"),
                eq("trace-desc"),
                eq("Bearer token"),
                eq("odoo"),
                same(arguments)
        );
    }

    @Test
    @DisplayName("未显式传 format 时默认为 markdown（AI Chat 路径：LLM 不知道 format 参数）")
    void shouldDefaultToMarkdownForAiChatPath() {
        SemanticMetadataResponse response = new SemanticMetadataResponse();
        response.setData(Map.of("models", List.of()));
        when(datasetAccessor.describeModel(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(RX.success(response));

        // LLM 视角：schema 不暴露 format 参数，因此 LLM 的 tool call 里不会有 format。
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("model", "OdooHrEmployeeQueryModel");
        ToolExecutionContext context = ToolExecutionContext.builder()
                .traceId("trace-default")
                .authorization("Bearer token")
                .namespace("odoo")
                .build();

        Object result = descriptionModelTool.execute(arguments, context);

        assertNotNull(result);
        assertInstanceOf(RX.class, result);
        verify(datasetAccessor).describeModel(
                eq("OdooHrEmployeeQueryModel"),
                eq("markdown"),
                eq("trace-default"),
                eq("Bearer token"),
                eq("odoo"),
                same(arguments)
        );
    }

    @Test
    @DisplayName("LLM 可见 schema 不应包含 format 字段，避免 LLM 切换格式")
    void llmVisibleSchemaShouldNotExposeFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/schemas/describe_model_internal_schema.json")) {
            assertNotNull(in, "describe_model_internal_schema.json should be on classpath");
            JsonNode root = mapper.readTree(in);
            JsonNode properties = root.path("properties");
            assertTrue(properties.has("model"), "Schema should declare required 'model' parameter");
            assertFalse(properties.has("format"),
                    "AI Chat contract: describe_model_internal must NOT expose format to the LLM");
            JsonNode required = root.path("required");
            assertTrue(required.isArray() && required.toString().contains("model"),
                    "'model' must remain required");
        }
    }

    @Test
    @DisplayName("LLM 可见 get_metadata schema 也不应包含 format 字段")
    void llmVisibleGetMetadataSchemaShouldNotExposeFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/schemas/get_metadata_schema.json")) {
            assertNotNull(in, "get_metadata_schema.json should be on classpath");
            JsonNode root = mapper.readTree(in);
            JsonNode properties = root.path("properties");
            assertFalse(properties.has("format"),
                    "AI Chat contract: get_metadata must NOT expose format to the LLM");
        }
    }

    @Test
    @DisplayName("缺少 model 时应返回错误")
    void missingModelShouldReturnError() {
        Object result = descriptionModelTool.execute(Map.of(), ToolExecutionContext.of("trace-missing", null));

        assertNotNull(result);
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result;
        assertEquals(Boolean.TRUE, error.get("error"));
        assertEquals("缺少必要参数: model", error.get("message"));
    }

    @Test
    @DisplayName("服务错误应原样返回")
    void accessorErrorShouldBeReturned() {
        when(datasetAccessor.describeModel(anyString(), anyString(), anyString(), any(), any(), anyMap()))
                .thenReturn(RX.failB("metadata failed"));

        Object result = descriptionModelTool.execute(
                Map.of("model", "AnyModel"),
                ToolExecutionContext.of("trace-error", null)
        );

        assertNotNull(result);
        assertInstanceOf(RX.class, result);
        @SuppressWarnings("unchecked")
        RX<SemanticMetadataResponse> rx = (RX<SemanticMetadataResponse>) result;
        assertNotEquals(200, rx.getCode());
        verify(datasetAccessor).describeModel(
                eq("AnyModel"),
                eq("markdown"),
                eq("trace-error"),
                isNull(),
                isNull(),
                eq(Map.of("model", "AnyModel"))
        );
    }
}
