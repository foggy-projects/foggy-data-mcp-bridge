package com.foggyframework.dataset.mcp.audit;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * 工具调用审计日志
 *
 * <p>记录 MCP 工具调用的详细信息，用于追踪和分析 AI 工具使用情况。
 *
 * <h3>ID 层级说明：</h3>
 * <ul>
 *   <li>traceId: AI 会话级，一次完整 AI 执行的唯一标识，贯穿多次工具调用</li>
 *   <li>requestId: HTTP 请求级，单次 HTTP 请求的唯一标识</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Data
@Builder
@Document("#{@mcpProperties.audit.mongodb.collection}")
@CompoundIndexes({
        @CompoundIndex(name = "idx_trace_request", def = "{'traceId': 1, 'requestId': 1}"),
        @CompoundIndex(name = "idx_tool_time", def = "{'toolName': 1, 'timestamp': -1}")
})
public class ToolAuditLog {

    /**
     * 文档 ID（MongoDB 自动生成）
     */
    @Id
    private String id;

    /**
     * AI 会话追踪 ID
     * <p>一次完整 AI 执行的唯一标识，同一会话中多次工具调用共享此 ID
     */
    @Indexed
    private String traceId;

    /**
     * HTTP 请求 ID
     * <p>单次 HTTP 请求的唯一标识
     */
    @Indexed
    private String requestId;

    /**
     * 工具名称
     * <p>如 dataset.query_model_v2, dataset.export_with_chart
     */
    @Indexed
    private String toolName;

    /**
     * 调用参数
     * <p>工具调用时传入的参数（JSON 格式存储）
     */
    private Map<String, Object> arguments;

    /**
     * 用户授权信息
     * <p>根据配置可能进行脱敏处理
     */
    private String authorization;

    /**
     * 用户角色
     * <p>如 ADMIN, ANALYST, BUSINESS
     */
    private String userRole;

    /**
     * 调用时间戳
     */
    @Indexed
    private Instant timestamp;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 错误类型
     * <p>如 JSON_PARSE_ERROR, EXECUTION_ERROR
     */
    private String errorType;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 结果摘要
     * <p>执行结果的简要信息，如返回行数等
     */
    private String resultSummary;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 请求来源
     * <p>如 /mcp/analyst/rpc, /mcp/admin/rpc
     */
    private String requestPath;

    /**
     * 额外信息
     * <p>用于存储其他需要记录的信息
     */
    private Map<String, Object> extra;
}
