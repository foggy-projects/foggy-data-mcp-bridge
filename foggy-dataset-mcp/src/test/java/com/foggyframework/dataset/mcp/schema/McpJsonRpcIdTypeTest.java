package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MCP JSON-RPC id 类型测试")
class McpJsonRpcIdTypeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("数字 id 应按数字回写到响应")
    void numericId_shouldRemainNumericInResponse() throws Exception {
        McpRequest request = objectMapper.readValue("""
                {
                  "jsonrpc": "2.0",
                  "id": 0,
                  "method": "initialize",
                  "params": {}
                }
                """, McpRequest.class);

        assertInstanceOf(Integer.class, request.getId());
        McpResponse response = McpResponse.success(request.getId(), Map.of("ok", true));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("id").isNumber());
        assertEquals(0, json.get("id").intValue());
    }

    @Test
    @DisplayName("字符串 id 仍应按字符串回写到响应")
    void stringId_shouldRemainStringInResponse() throws Exception {
        McpRequest request = objectMapper.readValue("""
                {
                  "jsonrpc": "2.0",
                  "id": "client-1",
                  "method": "initialize",
                  "params": {}
                }
                """, McpRequest.class);

        assertEquals("client-1", request.getId());
        McpResponse response = McpResponse.error(request.getId(), -32600, "Invalid request");
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("id").isTextual());
        assertEquals("client-1", json.get("id").textValue());
    }
}
