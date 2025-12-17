package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import com.foggyframework.dataset.mcp.service.ToolConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;

/**
 * MCP 工具接口
 *
 * 所有 MCP 工具必须实现此接口。
 * 工具描述和参数Schema从配置文件加载（application.yml + classpath资源）。
 */
public interface McpTool {

    /**
     * 获取工具名称
     * 例如：dataset.get_metadata, dataset_nl.query
     */
    String getName();

    /**
     * 获取工具分类
     *
     * 返回此工具所属的分类集合，用于按用户角色过滤工具
     * 一个工具可以属于多个分类
     *
     * @return 工具分类集合
     */
    Set<ToolCategory> getCategories();

    /**
     * 执行工具（同步）
     *
     * @param arguments     工具参数
     * @param traceId       追踪ID
     * @param authorization 授权令牌（从请求头传递）
     * @return 执行结果
     */
    Object execute(Map<String, Object> arguments, String traceId, String authorization);

    /**
     * 获取工具描述（完整版本）
     *
     * 默认返回null，由McpToolDispatcher从ToolConfigLoader获取
     * 子类可以覆盖此方法提供硬编码的描述（用于配置未覆盖的工具）
     */
    default String getDescription() {
        return null;
    }

    /**
     * 获取输入参数 JSON Schema
     *
     * 默认返回null，由McpToolDispatcher从ToolConfigLoader获取
     * 子类可以覆盖此方法提供硬编码的Schema（用于配置未覆盖的工具）
     */
    default Map<String, Object> getInputSchema() {
        return null;
    }

    /**
     * 是否支持流式执行
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 执行工具（带进度流）
     *
     * 默认实现为不支持流式，子类可以覆盖
     *
     * @param arguments     工具参数
     * @param traceId       追踪ID
     * @param authorization 授权令牌
     * @return 进度事件流
     */
    default Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, String traceId, String authorization) {
        return Flux.create(sink -> {
            try {
                Object result = execute(arguments, traceId, authorization);
                sink.next(ProgressEvent.complete(result));
                sink.complete();
            } catch (Exception e) {
                sink.next(ProgressEvent.error("EXECUTION_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }
}
