package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 业务人员 MCP JSON-RPC 2.0 Controller
 *
 * 提供简化的 MCP 协议端点，仅返回自然语言查询工具
 * 适用于：业务人员、普通用户、不需要了解技术细节的用户
 *
 * 可用工具：
 * - 自然语言查询工具（NaturalLanguageQueryTool）
 *
 * 端点：
 * - POST /mcp/business/rpc - 同步 JSON-RPC 调用
 * - POST /mcp/business/stream - 流式 SSE 响应
 *
 * HTTP Header 说明：
 * - X-Trace-Id: AI 会话追踪 ID，一次完整 AI 执行的唯一标识，贯穿多次工具调用
 * - X-Request-Id: HTTP 请求 ID，单次请求的唯一标识
 */
@Slf4j
@RestController
@RequestMapping("/mcp/business")
@RequiredArgsConstructor
public class BusinessMcpController {

    private final McpService mcpService;
    private final McpToolDispatcher toolDispatcher;

    /**
     * 用户角色固定为 BUSINESS
     */
    private static final UserRole USER_ROLE = UserRole.BUSINESS;

    /**
     * 标准 MCP JSON-RPC 端点（同步）
     *
     * Claude Desktop IDE 可使用此端点
     */
    @PostMapping(value = "/rpc", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpResponse> handleRpc(
            @RequestBody McpRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        // traceId: AI 会话级，如果没有则生成新的
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        // requestId: HTTP 请求级，每次请求都生成新的
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        log.info("Business MCP RPC request received: method={}, id={}, traceId={}, requestId={}",
                request.getMethod(), request.getId(), traceId, requestId);

        try {
            // 处理 MCP 内置方法
            if (request.getMethod() != null) {
                switch (request.getMethod()) {
                    case "initialize":
                        return ResponseEntity.ok(mcpService.handleInitialize(request, USER_ROLE));
                    case "tools/list":
                        return ResponseEntity.ok(mcpService.handleToolsList(request, USER_ROLE));
                    case "tools/call":
                        return ResponseEntity.ok(mcpService.handleToolsCall(request, USER_ROLE, traceId, requestId, authorization));
                    case "ping":
                        return ResponseEntity.ok(mcpService.handlePing(request));
                    default:
                        // 尝试作为工具调用处理
                        if (request.getMethod().startsWith("dataset")) {
                            return ResponseEntity.ok(mcpService.handleDirectToolCall(request, USER_ROLE, traceId, requestId, authorization));
                        }
                        return ResponseEntity.ok(McpResponse.error(
                                request.getId(),
                                McpError.METHOD_NOT_FOUND,
                                "Method not found: " + request.getMethod()
                        ));
                }
            }

            return ResponseEntity.ok(McpResponse.error(
                    request.getId(),
                    McpError.INVALID_REQUEST,
                    "Missing method field"
            ));

        } catch (Exception e) {
            log.error("Business MCP RPC error: method={}, error={}", request.getMethod(), e.getMessage(), e);
            return ResponseEntity.ok(McpResponse.error(
                    request.getId(),
                    McpError.INTERNAL_ERROR,
                    e.getMessage()
            ));
        }
    }

    /**
     * 流式 SSE 端点
     *
     * 用于 Web 前端获取实时进度
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> handleStream(
            @RequestBody McpRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        // traceId: AI 会话级
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        // requestId: HTTP 请求级
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        log.info("Business MCP Stream request received: method={}, id={}, traceId={}, requestId={}",
                request.getMethod(), request.getId(), traceId, requestId);

        final String finalTraceId = traceId;
        return toolDispatcher.executeWithProgress(request, traceId, authorization)
                .map(event -> ServerSentEvent.<Object>builder()
                        .id(event.getId())
                        .event(event.getEventType())
                        .data(event.getData())
                        .build())
                .doOnComplete(() -> log.info("Business MCP Stream completed: traceId={}", finalTraceId))
                .doOnError(e -> log.error("Business MCP Stream error: traceId={}, error={}", finalTraceId, e.getMessage()));
    }
}
