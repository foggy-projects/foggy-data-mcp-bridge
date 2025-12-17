package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP JSON-RPC 2.0 Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    @JsonProperty("jsonrpc")
    @Builder.Default
    private String jsonrpc = "2.0";

    private String id;

    private Object result;

    private McpError error;

    /**
     * 创建成功响应
     */
    public static McpResponse success(String id, Object result) {
        return McpResponse.builder()
                .id(id)
                .result(result)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static McpResponse error(String id, int code, String message) {
        return McpResponse.builder()
                .id(id)
                .error(McpError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * 创建错误响应（带详情）
     */
    public static McpResponse error(String id, int code, String message, Object data) {
        return McpResponse.builder()
                .id(id)
                .error(McpError.builder()
                        .code(code)
                        .message(message)
                        .data(data)
                        .build())
                .build();
    }
}
