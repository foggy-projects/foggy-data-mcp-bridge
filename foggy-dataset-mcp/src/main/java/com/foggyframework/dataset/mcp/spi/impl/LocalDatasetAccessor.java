package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.semantic.domain.*;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.fsscript.fun.Iif;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 本地数据集访问实现
 *
 * <p>直接调用本地的 SemanticServiceV3 和 SemanticQueryServiceV3，
 * 适用于单体应用/服务集成部署场景。
 *
 * <h3>工作模式说明：</h3>
 * <p>当 {@code mcp.dataset.access-mode=local} 时使用此实现。
 * 相比 RemoteDatasetAccessor（通过 HTTP 调用），本地模式：
 * <ul>
 *   <li>性能更高：无网络开销</li>
 *   <li>部署更简单：单进程运行</li>
 *   <li>适合开发和测试环境</li>
 * </ul>
 *
 * <h3>字段级别控制：</h3>
 * <p>通过 {@link McpProperties.SemanticConfig} 配置控制返回字段的范围。
 * 每个字段在 .qm 模型定义中可以设置 {@code ai.level} 属性，
 * 默认为 1（核心字段）。通过配置 levels 可以过滤返回的字段。
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 * @see McpProperties.SemanticConfig
 * @see McpProperties.LevelConfig
 */
@Slf4j
@RequiredArgsConstructor
public class LocalDatasetAccessor implements DatasetAccessor {

    private final SemanticServiceResolver semanticServiceResolver;
    private final McpProperties mcpProperties;

    @Override
    public RX<SemanticMetadataResponse> getMetadata(String traceId, String authorization) {
        log.debug("[Local] Fetching metadata, traceId={}", traceId);

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();

            // 从配置获取可用模型列表
            // 这些模型由 mcp.semantic.model-list 配置指定
            McpProperties.SemanticConfig semanticConfig = mcpProperties.getSemantic();
            List<String> availableModels = semanticConfig.getModelList();

            if (availableModels == null || availableModels.isEmpty()) {
                log.warn("[Local] No models configured in mcp.semantic.model-list, traceId={}", traceId);
                return RX.failB("未配置可用的数据模型，请检查 mcp.semantic.model-list 配置");
            }

            request.setQmModels(availableModels);

            // 应用字段级别配置
            // metadata.force-levels 会覆盖用户请求
            // metadata.default-levels 作为默认值
            McpProperties.LevelConfig metadataLevelConfig = semanticConfig.getMetadata();
            List<Integer> levels = metadataLevelConfig.apply(null); // 无用户指定，使用配置
            request.setLevels(levels);

            log.debug("[Local] Fetching metadata for models: {}, levels: {}, traceId={}",
                    availableModels, levels, traceId);

            // 使用版本解析器获取元数据
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, "markdown");

            log.debug("[Local] Metadata fetched successfully, traceId={}", traceId);
            return RX.success(response);

        } catch (Exception e) {
            log.error("[Local] Failed to fetch metadata: {}, traceId={}", e.getMessage(), traceId, e);
            return RX.failB("获取元数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个模型的详细描述
     *
     * <p>用于 AI 了解模型的具体字段定义，包括：
     * <ul>
     *   <li>字段名称和中文说明</li>
     *   <li>字段类型（度量/维度）</li>
     *   <li>可选的示例值</li>
     * </ul>
     *
     * <p>字段返回范围由 {@code mcp.semantic.internal} 配置控制。
     *
     * @param model         模型名称
     * @param format        输出格式（json/text）
     * @param traceId       追踪ID
     * @param authorization 授权信息
     * @return 模型描述信息
     */
    @Override
    public RX<SemanticMetadataResponse> describeModel(String model, String format, String traceId, String authorization) {
        log.debug("[Local] Describing model: {}, format={}, traceId={}",
                model, format, traceId);

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(List.of(model));
            request.setIncludeExamples(true);

            // 应用字段级别配置
            // internal.force-levels 会覆盖用户请求
            // internal.default-levels 作为默认值
            McpProperties.SemanticConfig semanticConfig = mcpProperties.getSemantic();
            McpProperties.LevelConfig internalLevelConfig = semanticConfig.getInternal();
            List<Integer> levels = internalLevelConfig.apply(null);
            request.setLevels(levels);

            log.debug("[Local] Describing model: {}, levels: {}, traceId={}",
                    model, levels, traceId);

            String outputFormat = format != null ? format : "json";
            // 使用版本解析器获取元数据
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, outputFormat);

            log.debug("[Local] Model description fetched: {}, traceId={}", model, traceId);
            return RX.success(response);

        } catch (Exception e) {
            log.error("[Local] Failed to describe model {}: {}, traceId={}", model, e.getMessage(), traceId, e);
            return RX.failB("获取模型描述失败: " + e.getMessage());
        }
    }

    /**
     * 执行数据查询
     *
     * <p>根据查询参数执行数据查询，支持：
     * <ul>
     *   <li>列选择（columns）</li>
     *   <li>过滤条件（slice）</li>
     *   <li>分组聚合（groupBy）</li>
     *   <li>排序（orderBy）</li>
     *   <li>分页（limit/start/cursor）</li>
     * </ul>
     *
     * @param model         模型名称
     * @param payload       查询参数
     * @param mode          执行模式（execute/validate）
     * @param traceId       追踪ID
     * @param authorization 授权信息
     * @return 查询结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public RX<SemanticQueryResponse> queryModel(String model, Map<String, Object> payload, String mode,
                             String traceId, String authorization) {
        log.debug("[Local] Querying model: {}, mode={}, traceId={}",
                model, mode, traceId);

        try {
            SemanticQueryRequest request = buildQueryRequest(payload);
            String queryMode = mode != null ? mode : "execute";

            // 构建 SecurityContext 传递授权信息
            ModelResultContext.SecurityContext securityContext = null;
            if (authorization != null && !authorization.isEmpty()) {
                securityContext = ModelResultContext.SecurityContext.fromAuthorization(authorization);
                log.debug("[Local] SecurityContext created for authorization, traceId={}", traceId);
            }

            // 使用版本解析器执行查询（带 SecurityContext）
            SemanticQueryResponse response = semanticServiceResolver.queryModel(model, request, queryMode, securityContext);

            log.debug("[Local] Query executed: model={}, items={}, traceId={}",
                    model, response.getItems() != null ? response.getItems().size() : 0, traceId);
            return RX.success(response);

        } catch (Exception e) {
            log.error("[Local] Query failed: model={}, error={}, traceId={}", model, e.getMessage(), traceId, e);
            return RX.failB("查询执行失败: " + e.getMessage());
        }
    }

    @Override
    public String getAccessMode() {
        return "local";
    }

    /**
     * 从 Map 构建 SemanticQueryRequest
     */
    @SuppressWarnings("unchecked")
    private SemanticQueryRequest buildQueryRequest(Map<String, Object> payload) {
        SemanticQueryRequest request = new SemanticQueryRequest();

        if (payload == null) {
            return request;
        }

        // columns
        if (payload.containsKey("columns")) {
            request.setColumns((List<String>) payload.get("columns"));
        }

        // calculatedFields (计算字段)
        if (payload.containsKey("calculatedFields")) {
            Object calculatedFields = payload.get("calculatedFields");
            if (calculatedFields instanceof List) {
                List<Map<String, Object>> cfList = (List<Map<String, Object>>) calculatedFields;
                List<CalculatedFieldDef> calculatedFieldDefs = cfList.stream()
                        .map(this::convertToCalculatedFieldDef)
                        .toList();
                request.setCalculatedFields(calculatedFieldDefs);
            }
        }

        // slice (过滤条件) - 需要转换为 List<SliceItem>
        if (payload.containsKey("slice")) {
            Object slice = payload.get("slice");
            if (slice instanceof List) {
                // slice 是 List<Map<String, Object>> 格式
                List<Map<String, Object>> sliceList = (List<Map<String, Object>>) slice;
                List<SemanticQueryRequest.SliceItem> sliceItems = sliceList.stream()
                        .map(this::convertToSliceItem)
                        .toList();
                request.setSlice(sliceItems);
            }
        }

        // groupBy - 需要转换为 List<GroupByItem>
        if (payload.containsKey("groupBy")) {
            Object groupBy = payload.get("groupBy");
            if (groupBy instanceof List) {
                List<?> groupByList = (List<?>) groupBy;
                if (!groupByList.isEmpty()) {
                    if (groupByList.get(0) instanceof String) {
                        // 简化格式：List<String>
                        List<SemanticQueryRequest.GroupByItem> groupByItems = ((List<String>) groupBy).stream()
                                .map(name -> new SemanticQueryRequest.GroupByItem(name, null))
                                .toList();
                        request.setGroupBy(groupByItems);
                    } else if (groupByList.get(0) instanceof Map) {
                        // 完整格式：List<Map>
                        List<SemanticQueryRequest.GroupByItem> groupByItems = ((List<Map<String, Object>>) groupBy).stream()
                                .map(this::convertToGroupByItem)
                                .toList();
                        request.setGroupBy(groupByItems);
                    }
                }
            }
        }

        // orderBy - 需要转换为 List<OrderItem>
        if (payload.containsKey("orderBy")) {
            Object orderBy = payload.get("orderBy");
            if (orderBy instanceof List) {
                List<Map<String, Object>> orderByList = (List<Map<String, Object>>) orderBy;
                List<SemanticQueryRequest.OrderItem> orderItems = orderByList.stream()
                        .map(this::convertToOrderItem)
                        .toList();
                request.setOrderBy(orderItems);
            }
        }

        // limit
        if (payload.containsKey("limit")) {
            Object limit = payload.get("limit");
            if (limit instanceof Number) {
                request.setLimit(((Number) limit).intValue());
            }
        }

        // start (offset)
        if (payload.containsKey("start")) {
            Object start = payload.get("start");
            if (start instanceof Number) {
                request.setStart(((Number) start).intValue());
            }
        }

//        // cursor (分页游标)
//        if (payload.containsKey("cursor")) {
//            request.setCursor((String) payload.get("cursor"));
//        }
        if (payload.containsKey("returnTotal")) {
            request.setReturnTotal(Iif.check( payload.get("returnTotal")));
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private SemanticQueryRequest.SliceItem convertToSliceItem(Map<String, Object> map) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField((String) map.get("field"));
        item.setOp((String) map.getOrDefault("op", "eq"));
        item.setValue(map.get("value"));

        // 处理 link（逻辑连接符）
        if (map.containsKey("link")) {
            Object linkObj = map.get("link");
            if (linkObj instanceof Number) {
                item.setLink(((Number) linkObj).intValue());
            }
        }

        // 处理 children（嵌套条件组）
        if (map.containsKey("children")) {
            Object childrenObj = map.get("children");
            if (childrenObj instanceof List) {
                List<Map<String, Object>> childrenList = (List<Map<String, Object>>) childrenObj;
                List<SemanticQueryRequest.SliceItem> childrenItems = childrenList.stream()
                        .map(this::convertToSliceItem)
                        .toList();
                item.setChildren(childrenItems);
            }
        }

        return item;
    }

    private SemanticQueryRequest.GroupByItem convertToGroupByItem(Map<String, Object> map) {
        return new SemanticQueryRequest.GroupByItem(
                (String) map.get("field"),
                (String) map.get("agg")
        );
    }

    private SemanticQueryRequest.OrderItem convertToOrderItem(Map<String, Object> map) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        // 支持 name 或 column 作为字段名
        String name = (String) map.get("field");
        if (name == null) {
            name = (String) map.get("column");
        }
        item.setField(name);
        // 支持 dir 或 direction 作为排序方向
        String dir = (String) map.get("dir");
        if (dir == null) {
            dir = (String) map.getOrDefault("direction", "asc");
        }
        item.setDir(dir);
        return item;
    }

    private CalculatedFieldDef convertToCalculatedFieldDef(Map<String, Object> map) {
        CalculatedFieldDef def = new CalculatedFieldDef();
        def.setName((String) map.get("name"));
        def.setCaption((String) map.get("caption"));
        def.setExpression((String) map.get("expression"));
        def.setDescription((String) map.get("description"));
        return def;
    }
}
