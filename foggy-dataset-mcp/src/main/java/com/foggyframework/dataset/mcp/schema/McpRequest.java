package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP JSON-RPC 2.0 Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpRequest {

    @JsonProperty("jsonrpc")
    @Builder.Default
    private String jsonrpc = "2.0";

    private String id;

    private String method;

    private Map<String, Object> params;
}
