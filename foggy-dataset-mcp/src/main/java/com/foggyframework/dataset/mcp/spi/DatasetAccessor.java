package com.foggyframework.dataset.mcp.spi;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryResponse;

import java.util.Map;

/**
 * 数据集访问接口
 *
 * 抽象数据访问层，支持两种实现模式：
 * - Remote: 通过 HTTP 调用远程 foggy-dataset-model 服务（暂不实现）
 * - Local: 直接调用本地 SemanticService（服务集成模式）
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
public interface DatasetAccessor {

    /**
     * 获取元数据
     *
     * @param traceId       追踪ID
     * @param authorization 授权头（可选）
     * @return 元数据响应
     */
    RX<SemanticMetadataResponse> getMetadata(String traceId, String authorization);

    /**
     * 获取模型描述（字段详情）
     *
     * @param model         模型名称
     * @param format        输出格式：json | markdown
     * @param traceId       追踪ID
     * @param authorization 授权头（可选）
     * @return 模型描述响应
     */
    RX<SemanticMetadataResponse> describeModel(String model, String format, String traceId, String authorization);

    /**
     * 执行模型查询
     *
     * @param model         模型名称
     * @param payload       查询参数（columns, slice, groupBy, orderBy, limit 等）
     * @param mode          查询模式：execute | validate
     * @param traceId       追踪ID
     * @param authorization 授权头（可选）
     * @return 查询结果
     */
    RX<SemanticQueryResponse> queryModel(String model, Map<String, Object> payload, String mode,
                      String traceId, String authorization);

    /**
     * 获取访问模式名称
     *
     * @return 模式名称（remote 或 local）
     */
    String getAccessMode();
}
