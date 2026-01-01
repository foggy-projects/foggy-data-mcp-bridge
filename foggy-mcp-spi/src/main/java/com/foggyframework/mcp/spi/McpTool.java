package com.foggyframework.mcp.spi;

import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;

/**
 * MCP 工具接口
 * <p>
 * 所有 MCP 工具必须实现此接口。
 * 工具描述和参数Schema从配置文件加载（application.yml + classpath资源）。
 * <p>
 * 此接口定义在 SPI 模块中，允许 addon 模块实现自定义工具，
 * 无需依赖完整的 foggy-dataset-mcp 模块。
 */
public interface McpTool {

    /**
     * 获取工具名称
     * <p>
     * 例如：dataset.get_metadata, dataset_nl.query, dataset.open_in_viewer
     */
    String getName();

    /**
     * 获取工具分类
     * <p>
     * 返回此工具所属的分类集合，用于按用户角色过滤工具
     * 一个工具可以属于多个分类
     *
     * @return 工具分类集合
     */
    Set<ToolCategory> getCategories();

    /**
     * 执行工具（同步）
     *
     * @param arguments 工具参数
     * @param context   执行上下文（包含traceId、authorization等）
     * @return 执行结果
     */
    Object execute(Map<String, Object> arguments, ToolExecutionContext context);

    /**
     * 获取工具描述（完整版本）
     * <p>
     * 默认返回null，由McpToolDispatcher从ToolConfigLoader获取
     * 子类可以覆盖此方法提供硬编码的描述（用于配置未覆盖的工具）
     */
    default String getDescription() {
        return null;
    }

    /**
     * 获取输入参数 JSON Schema
     * <p>
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
     * <p>
     * 默认实现为不支持流式，子类可以覆盖
     *
     * @param arguments 工具参数
     * @param context   执行上下文
     * @return 进度事件流
     */
    default Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, ToolExecutionContext context) {
        return Flux.create(sink -> {
            try {
                Object result = execute(arguments, context);
                sink.next(ProgressEvent.complete(result));
                sink.complete();
            } catch (Exception e) {
                sink.next(ProgressEvent.error("EXECUTION_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }
}
