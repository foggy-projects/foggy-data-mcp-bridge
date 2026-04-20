package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.fsscript.fun.Iif;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 本地数据集访问实现
 *
 * <p>直接调用本地的 SemanticServiceV3 和 SemanticQueryServiceV3，
 * 适用于单体应用/服务集成部署场景。
 *
 * <h3>工作模式说明：</h3>
 * <p>当 {@code foggy.mcp.dataset.access-mode=local} 时使用此实现。
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

    private static final String DENIED_COLUMNS_KEY = "deniedColumns";
    private static final String SYSTEM_SLICE_KEY = "systemSlice";

    private final SemanticServiceResolver semanticServiceResolver;
    private final McpProperties mcpProperties;

    @Override
    public RX<SemanticMetadataResponse> getMetadata(String traceId, String authorization, String namespace) {
        return getMetadata(traceId, authorization, namespace, null);
    }

    @Override
    public RX<SemanticMetadataResponse> getMetadata(
            String traceId,
            String authorization,
            String namespace,
            Map<String, Object> options
    ) {
        log.debug("[Local] Fetching metadata, traceId={}, namespace={}", traceId, namespace);

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();

            // 从配置获取可用模型列表（三态逻辑）
            McpProperties.SemanticConfig semanticConfig = mcpProperties.getSemantic();
            List<String> availableModels;
            Boolean useAllModels = semanticConfig.getUseAllModels();

            if (Boolean.FALSE.equals(useAllModels)) {
                // 显式禁用：返回空列表
                log.debug("[Local] Model discovery explicitly disabled, traceId={}", traceId);
                return RX.failB("模型发现已禁用（useAllModels=false）");
            } else if (Boolean.TRUE.equals(useAllModels)) {
                // 强制动态发现
                availableModels = semanticServiceResolver.getAllModelNames();
                log.debug("[Local] Dynamic model discovery (forced): found {} models, traceId={}", availableModels.size(), traceId);
            } else {
                // null：根据 model-list 自动推断
                List<String> configuredModels = semanticConfig.getModelList();
                if (configuredModels == null || configuredModels.isEmpty()) {
                    // 未配置 model-list，使用动态发现
                    availableModels = semanticServiceResolver.getAllModelNames();
                    log.debug("[Local] Dynamic model discovery (auto): found {} models, traceId={}", availableModels.size(), traceId);
                } else {
                    // 使用静态配置
                    availableModels = configuredModels;
                    log.debug("[Local] Using configured model-list: {} models, traceId={}", availableModels.size(), traceId);
                }
            }

            if (availableModels == null || availableModels.isEmpty()) {
                log.warn("[Local] No models available, traceId={}", traceId);
                return RX.failB("未找到可用的数据模型，请检查 foggy.mcp.semantic.model-list 配置或 QM 文件");
            }

            request.setQmModels(availableModels);

            // 应用字段级别配置
            // metadata.force-levels 会覆盖用户请求
            // metadata.default-levels 作为默认值
            McpProperties.LevelConfig metadataLevelConfig = semanticConfig.getMetadata();
            List<Integer> levels = metadataLevelConfig.apply(null); // 无用户指定，使用配置
            request.setLevels(levels);

            log.debug("[Local] Fetching metadata for models: {}, levels: {}, traceId={}, namespace={}",
                    availableModels, levels, traceId, namespace);

            // 使用版本解析器获取元数据（传递 namespace）
            SemanticRequestContext ctx = buildMetadataContext(namespace, authorization, options);
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, "markdown", ctx);

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
    public RX<SemanticMetadataResponse> describeModel(String model, String format, String traceId,
                                                       String authorization, String namespace) {
        return describeModel(model, format, traceId, authorization, namespace, null);
    }

    @Override
    public RX<SemanticMetadataResponse> describeModel(
            String model,
            String format,
            String traceId,
            String authorization,
            String namespace,
            Map<String, Object> options
    ) {
        log.debug("[Local] Describing model: {}, format={}, traceId={}, namespace={}",
                model, format, traceId, namespace);

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

            log.debug("[Local] Describing model: {}, levels: {}, traceId={}, namespace={}",
                    model, levels, traceId, namespace);

            String outputFormat = format != null ? format : "json";
            // 使用版本解析器获取元数据（传递 namespace）
            SemanticRequestContext ctx = buildMetadataContext(namespace, authorization, options);
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, outputFormat, ctx);

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
                                                String traceId, String authorization, String namespace) {
        return queryModel(model, payload, mode, traceId, authorization, namespace, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RX<SemanticQueryResponse> queryModel(
            String model,
            Map<String, Object> payload,
            String mode,
            String traceId,
            String authorization,
            String namespace,
            Map<String, Object> options
    ) {
        log.debug("[Local] Querying model: {}, mode={}, traceId={}, namespace={}",
                model, mode, traceId, namespace);

        try {
            SemanticQueryRequest request = buildQueryRequest(payload);
            String queryMode = mode != null ? mode : "execute";

            // 构建请求上下文（namespace + 安全信息）
            SemanticRequestContext ctx = buildQueryContext(namespace, authorization, options);

            // 使用版本解析器执行查询
            SemanticQueryResponse response = semanticServiceResolver.queryModel(model, request, queryMode, ctx);

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

    private SemanticRequestContext buildMetadataContext(
            String namespace,
            String authorization,
            Map<String, Object> options
    ) {
        List<DeniedPhysicalColumn> deniedColumns = extractDeniedColumns(options);
        ModelResultContext.SecurityContext securityContext = toSecurityContext(authorization);
        if (securityContext == null && (deniedColumns == null || deniedColumns.isEmpty())) {
            return SemanticRequestContext.ofNamespace(namespace);
        }
        return SemanticRequestContext.of(namespace, securityContext, null, deniedColumns, null);
    }

    private SemanticRequestContext buildQueryContext(
            String namespace,
            String authorization,
            Map<String, Object> options
    ) {
        return SemanticRequestContext.of(
                namespace,
                toSecurityContext(authorization),
                null,
                extractDeniedColumns(options),
                extractSystemSlice(options)
        );
    }

    private ModelResultContext.SecurityContext toSecurityContext(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return ModelResultContext.SecurityContext.fromAuthorization(authorization);
    }

    @SuppressWarnings("unchecked")
    private List<DeniedPhysicalColumn> extractDeniedColumns(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        Object value = options.get(DENIED_COLUMNS_KEY);
        if (!(value instanceof List<?> deniedList) || deniedList.isEmpty()) {
            return null;
        }
        List<DeniedPhysicalColumn> result = new ArrayList<>();
        for (Object entry : deniedList) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object columnsObj = map.get("columns");
            if (columnsObj instanceof List<?> columns) {
                String schema = stringValue(map.get("schema"));
                String table = stringValue(map.get("table"));
                for (Object columnObj : columns) {
                    String column = stringValue(columnObj);
                    if (isBlank(table) || isBlank(column)) {
                        continue;
                    }
                    result.add(new DeniedPhysicalColumn(schema, table, column));
                }
                continue;
            }
            String table = stringValue(map.get("table"));
            String column = stringValue(map.get("column"));
            if (isBlank(table) || isBlank(column)) {
                continue;
            }
            result.add(new DeniedPhysicalColumn(stringValue(map.get("schema")), table, column));
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<SliceRequestDef> extractSystemSlice(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        Object value = options.get(SYSTEM_SLICE_KEY);
        if (!(value instanceof List<?> sliceList) || sliceList.isEmpty()) {
            return null;
        }
        List<SliceRequestDef> result = new ArrayList<>();
        for (Object entry : sliceList) {
            if (entry instanceof Map<?, ?> map) {
                result.add(convertToSliceRequestDef((Map<String, Object>) map));
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private SliceRequestDef convertToSliceRequestDef(Map<String, Object> map) {
        SliceRequestDef item = new SliceRequestDef();
        if (map.size() == 1) {
            String key = map.keySet().iterator().next();
            if ("$or".equals(key)) {
                item.setOr(convertGroupConditions((List<Object>) map.get(key)));
                return item;
            }
            if ("$and".equals(key)) {
                item.setAnd(convertGroupConditions((List<Object>) map.get(key)));
                return item;
            }
            if (!RESERVED_SLICE_KEYS.contains(key)) {
                item.setField(key);
                item.setOp("=");
                item.setValue(map.get(key));
                return item;
            }
        }

        item.setField(stringValue(map.get("field")));
        item.setOp(stringValue(map.getOrDefault("op", "=")));
        item.setValue(map.get("value"));
        if (map.containsKey("maxDepth") && map.get("maxDepth") instanceof Number maxDepth) {
            item.setMaxDepth(maxDepth.intValue());
        }
        if (map.containsKey("$expr")) {
            item.setExpr(stringValue(map.get("$expr")));
        }
        if (map.containsKey("$or")) {
            item.setOr(convertGroupConditions((List<Object>) map.get("$or")));
        }
        if (map.containsKey("$and")) {
            item.setAnd(convertGroupConditions((List<Object>) map.get("$and")));
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<CondRequestDef> convertGroupConditions(List<Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<CondRequestDef> result = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> map) {
                result.add(convertToSliceRequestDef((Map<String, Object>) map));
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
                List<?> orderByList = (List<?>) orderBy;
                List<SemanticQueryRequest.OrderItem> orderItems = new ArrayList<>();
                for (Object item : orderByList) {
                    if (item instanceof String) {
                        // 简写格式：字符串
                        orderItems.add(parseOrderByShorthand((String) item));
                    } else if (item instanceof Map) {
                        // 完整格式：Map
                        orderItems.add(convertToOrderItem((Map<String, Object>) item));
                    }
                }
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

        // distinct
        if (payload.containsKey("distinct")) {
            request.setDistinct(Iif.check(payload.get("distinct")));
        }

        // withSubtotals
        if (payload.containsKey("withSubtotals")) {
            request.setWithSubtotals(Iif.check(payload.get("withSubtotals")));
        }

        // 添加 MCP 来源标记（供 LargeResultTruncationStep 识别）
        Map<String, Object> hints = new HashMap<>();
        hints.put("fromMcp", true);
        request.setHints(hints);

        return request;
    }

    /**
     * 保留字段名（不作为简写格式的 key）
     */
    private static final Set<String> RESERVED_SLICE_KEYS = Set.of(
            "$or", "$and", "field", "op", "value", "maxDepth"
    );

    @SuppressWarnings("unchecked")
    private SemanticQueryRequest.SliceItem convertToSliceItem(Map<String, Object> map) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();

        // 判断是否为简写格式：map.size == 1 且 key 不是保留字
        if (map.size() == 1) {
            String key = map.keySet().iterator().next();

            // $or 逻辑组
            if ("$or".equals(key)) {
                Object orObj = map.get("$or");
                if (orObj instanceof List) {
                    List<Map<String, Object>> orList = (List<Map<String, Object>>) orObj;
                    List<SemanticQueryRequest.SliceItem> orItems = orList.stream()
                            .map(this::convertToSliceItem)
                            .toList();
                    item.setOr(orItems);
                }
                return item;
            }

            // $and 逻辑组
            if ("$and".equals(key)) {
                Object andObj = map.get("$and");
                if (andObj instanceof List) {
                    List<Map<String, Object>> andList = (List<Map<String, Object>>) andObj;
                    List<SemanticQueryRequest.SliceItem> andItems = andList.stream()
                            .map(this::convertToSliceItem)
                            .toList();
                    item.setAnd(andItems);
                }
                return item;
            }

            // 简写格式：{ "fieldName": value } → { field, op: "=", value }
            if (!RESERVED_SLICE_KEYS.contains(key)) {
                item.setField(key);
                item.setOp("=");
                item.setValue(map.get(key));
                return item;
            }
        }

        // 完整格式
        item.setField((String) map.get("field"));
        item.setOp((String) map.getOrDefault("op", "="));
        item.setValue(map.get("value"));

        // 处理 $or 条件组
        if (map.containsKey("$or")) {
            Object orObj = map.get("$or");
            if (orObj instanceof List) {
                List<Map<String, Object>> orList = (List<Map<String, Object>>) orObj;
                List<SemanticQueryRequest.SliceItem> orItems = orList.stream()
                        .map(this::convertToSliceItem)
                        .toList();
                item.setOr(orItems);
            }
        }

        // 处理 $and 条件组
        if (map.containsKey("$and")) {
            Object andObj = map.get("$and");
            if (andObj instanceof List) {
                List<Map<String, Object>> andList = (List<Map<String, Object>>) andObj;
                List<SemanticQueryRequest.SliceItem> andItems = andList.stream()
                        .map(this::convertToSliceItem)
                        .toList();
                item.setAnd(andItems);
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

    /**
     * 解析 orderBy 简写格式
     * <ul>
     *   <li>{@code "fieldName"} → asc（默认）</li>
     *   <li>{@code "fieldName asc"} → asc</li>
     *   <li>{@code "fieldName desc"} → desc</li>
     *   <li>{@code "-fieldName"} → desc（负号前缀）</li>
     * </ul>
     */
    private SemanticQueryRequest.OrderItem parseOrderByShorthand(String text) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        text = text.trim();

        // 检查负号前缀（降序）
        if (text.startsWith("-")) {
            item.setField(text.substring(1).trim());
            item.setDir("desc");
            return item;
        }

        // 检查空格分隔的格式："field asc" 或 "field desc"
        int spaceIndex = text.lastIndexOf(' ');
        if (spaceIndex > 0) {
            String fieldPart = text.substring(0, spaceIndex).trim();
            String dirPart = text.substring(spaceIndex + 1).trim().toLowerCase();

            if ("asc".equals(dirPart) || "desc".equals(dirPart)) {
                item.setField(fieldPart);
                item.setDir(dirPart);
                return item;
            }
        }

        // 默认：仅字段名，升序
        item.setField(text);
        item.setDir("asc");
        return item;
    }
}
