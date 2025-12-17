package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP JSON-RPC 2.0 Error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpError {

    // Standard JSON-RPC 2.0 Error Codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    // Custom Error Codes
    public static final int TOOL_NOT_FOUND = -32001;
    public static final int TOOL_EXECUTION_ERROR = -32002;
    public static final int QUERY_FAILED = -32003;
    public static final int AUTHENTICATION_ERROR = -32004;
    public static final int RATE_LIMIT_ERROR = -32005;

    private int code;
    private String message;
    private Object data;
}
