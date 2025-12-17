package com.foggyframework.dataset.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MCP Data Model Service - Java Implementation
 *
 * 基于 Spring AI 实现的 MCP 数据模型服务
 *
 * 功能：
 * - MCP 协议支持（JSON-RPC 2.0）
 * - 自然语言数据查询
 * - 图表生成和导出
 * - 流式响应（SSE/WebSocket）
 */
@SpringBootApplication
@EnableAsync
public class McpDataModelApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpDataModelApplication.class, args);
    }
}
