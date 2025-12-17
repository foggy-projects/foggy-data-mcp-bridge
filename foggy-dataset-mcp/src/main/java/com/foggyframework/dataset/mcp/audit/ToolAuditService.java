package com.foggyframework.dataset.mcp.audit;

import com.foggyframework.dataset.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 工具调用审计日志服务
 *
 * <p>负责将工具调用信息异步写入 MongoDB。
 *
 * <p>启用条件：
 * <ul>
 *   <li>{@code mcp.audit.enabled=true}</li>
 *   <li>存在 {@link MongoTemplate} Bean（即配置了 MongoDB 数据源）</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.audit", name = "enabled", havingValue = "true")
@ConditionalOnBean(MongoTemplate.class)
public class ToolAuditService {

    private final MongoTemplate mongoTemplate;
    private final McpProperties mcpProperties;

    /**
     * 异步记录工具调用审计日志
     *
     * @param traceId       AI 会话追踪 ID
     * @param requestId     HTTP 请求 ID
     * @param toolName      工具名称
     * @param arguments     调用参数
     * @param authorization 用户授权信息
     * @param userRole      用户角色
     * @param durationMs    执行耗时（毫秒）
     * @param success       是否成功
     * @param errorType     错误类型（可选）
     * @param errorMessage  错误信息（可选）
     * @param resultSummary 结果摘要（可选）
     * @param clientIp      客户端 IP（可选）
     * @param requestPath   请求路径（可选）
     * @param extra         额外信息（可选）
     */
    @Async
    public void logToolCall(
            String traceId,
            String requestId,
            String toolName,
            Map<String, Object> arguments,
            String authorization,
            String userRole,
            Long durationMs,
            Boolean success,
            String errorType,
            String errorMessage,
            String resultSummary,
            String clientIp,
            String requestPath,
            Map<String, Object> extra
    ) {
        McpProperties.AuditConfig auditConfig = mcpProperties.getAudit();

        // 检查是否需要记录此工具
        if (!auditConfig.shouldAudit(toolName)) {
            return;
        }

        try {
            ToolAuditLog auditLog = ToolAuditLog.builder()
                    .traceId(traceId)
                    .requestId(requestId)
                    .toolName(toolName)
                    .arguments(arguments)
                    .authorization(auditConfig.maskAuthorizationValue(authorization))
                    .userRole(userRole)
                    .timestamp(Instant.now())
                    .durationMs(durationMs)
                    .success(success)
                    .errorType(errorType)
                    .errorMessage(errorMessage)
                    .resultSummary(resultSummary)
                    .clientIp(clientIp)
                    .requestPath(requestPath)
                    .extra(extra)
                    .build();

            String collectionName = auditConfig.getMongodb().getCollection();
            mongoTemplate.save(auditLog, collectionName);

            log.debug("[Audit] Tool call logged: traceId={}, tool={}, success={}",
                    traceId, toolName, success);

        } catch (Exception e) {
            // 审计日志写入失败不应影响主流程
            log.warn("[Audit] Failed to log tool call: traceId={}, tool={}, error={}",
                    traceId, toolName, e.getMessage());
        }
    }

    /**
     * 简化版日志记录（成功场景）
     */
    @Async
    public void logSuccess(
            String traceId,
            String requestId,
            String toolName,
            Map<String, Object> arguments,
            String authorization,
            String userRole,
            Long durationMs,
            String resultSummary
    ) {
        logToolCall(traceId, requestId, toolName, arguments, authorization, userRole,
                durationMs, true, null, null, resultSummary, null, null, null);
    }

    /**
     * 简化版日志记录（失败场景）
     */
    @Async
    public void logFailure(
            String traceId,
            String requestId,
            String toolName,
            Map<String, Object> arguments,
            String authorization,
            String userRole,
            Long durationMs,
            String errorType,
            String errorMessage
    ) {
        logToolCall(traceId, requestId, toolName, arguments, authorization, userRole,
                durationMs, false, errorType, errorMessage, null, null, null, null);
    }
}
