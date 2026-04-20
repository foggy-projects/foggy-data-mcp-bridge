package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("应透传 deniedColumns 等治理参数")
    void shouldPassGovernanceArguments() {
        SemanticMetadataResponse response = new SemanticMetadataResponse();
        response.setData(Map.of("models", List.of()));
        when(datasetAccessor.describeModel(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(RX.success(response));

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
