package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final McpToolDispatcher toolDispatcher;

    /**
     * 健康检查端点
     */
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ok");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "mcp-data-model-java");
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }

    /**
     * 就绪检查端点
     */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyCheck() {
        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("status", "ready");
        ready.put("timestamp", LocalDateTime.now().toString());
        ready.put("tools_count", toolDispatcher.getToolDefinitions().size());
        return ResponseEntity.ok(ready);
    }

    /**
     * 服务信息端点
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> serviceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "mcp-data-model-java");
        info.put("version", "1.0.0");
        info.put("description", "MCP Data Model Service - Java Implementation with Spring AI");
        info.put("protocol", "JSON-RPC 2.0");
        info.put("endpoints", Map.of(
                "mcp_rpc", "/mcp/rpc",
                "mcp_stream", "/mcp/stream",
                "health", "/healthz",
                "ready", "/readyz"
        ));
        info.put("tools", toolDispatcher.getToolDefinitions());
        return ResponseEntity.ok(info);
    }
}
