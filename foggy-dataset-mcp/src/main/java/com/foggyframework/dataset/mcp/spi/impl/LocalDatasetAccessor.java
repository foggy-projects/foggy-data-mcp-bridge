package com.foggyframework.dataset.mcp.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import com.foggyframework.dataset.model.semantic.support.QueryInputWarnings;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
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
public class LocalDatasetAccessor implements DatasetAccessor {

    private static final String DENIED_COLUMNS_KEY = "deniedColumns";
    private static final String SYSTEM_SLICE_KEY = "systemSlice";
    private final SemanticServiceResolver semanticServiceResolver;
    private final McpProperties mcpProperties;
    private final DatasetProperties datasetProperties;
    private final SemanticQueryPayloadMapper queryPayloadMapper;

    public LocalDatasetAccessor(SemanticServiceResolver semanticServiceResolver, McpProperties mcpProperties) {
        this(semanticServiceResolver, mcpProperties, new DatasetProperties());
    }

    public LocalDatasetAccessor(
            SemanticServiceResolver semanticServiceResolver,
            McpProperties mcpProperties,
            DatasetProperties datasetProperties
    ) {
        this(semanticServiceResolver, mcpProperties, datasetProperties,
                new SemanticQueryPayloadMapper(new ObjectMapper()));
    }

    public LocalDatasetAccessor(
            SemanticServiceResolver semanticServiceResolver,
            McpProperties mcpProperties,
            DatasetProperties datasetProperties,
            SemanticQueryPayloadMapper queryPayloadMapper
    ) {
        this.semanticServiceResolver = semanticServiceResolver;
        this.mcpProperties = mcpProperties;
        this.datasetProperties = datasetProperties;
        this.queryPayloadMapper = queryPayloadMapper;
    }

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
        String effectiveNamespace = resolveNamespace(namespace);
        log.debug("[Local] Fetching metadata, traceId={}, namespace={}", traceId, effectiveNamespace);

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();

            McpProperties.SemanticConfig semanticConfig = mcpProperties.getSemantic();
            List<String> availableModels = selectMetadataModels(semanticConfig, namespace, effectiveNamespace, traceId);

            if (availableModels == null || availableModels.isEmpty()) {
                log.warn("[Local] No models available, traceId={}", traceId);
                return RX.failB("未找到可用的数据模型，请检查 foggy.mcp.semantic.model-list 配置或 QM 文件");
            }

            request.setQmModels(availableModels);
            request.setTolerateModelLoadErrors(true);

            // 应用字段级别配置
            // metadata.force-levels 会覆盖用户请求
            // metadata.default-levels 作为默认值
            McpProperties.LevelConfig metadataLevelConfig = semanticConfig.getMetadata();
            List<Integer> levels = metadataLevelConfig.apply(null); // 无用户指定，使用配置
            request.setLevels(levels);

            log.debug("[Local] Fetching metadata for models: {}, levels: {}, traceId={}, namespace={}",
                    availableModels, levels, traceId, effectiveNamespace);

            // 使用版本解析器获取元数据（传递 namespace）
            SemanticRequestContext ctx = buildMetadataContext(effectiveNamespace, authorization, options);
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, "markdown", ctx);

            log.debug("[Local] Metadata fetched successfully, traceId={}", traceId);
            return RX.success(response);

        } catch (Exception e) {
            log.error("[Local] Failed to fetch metadata: {}, traceId={}", e.getMessage(), traceId, e);
            return RX.failB("获取元数据失败: " + e.getMessage());
        }
    }

    private List<String> selectMetadataModels(
            McpProperties.SemanticConfig semanticConfig,
            String requestNamespace,
            String effectiveNamespace,
            String traceId
    ) {
        McpProperties.NamespaceSemanticConfig namespaceConfig = namespaceConfig(semanticConfig, effectiveNamespace);
        if (namespaceConfig != null) {
            List<String> namespaceModels = namespaceConfig.getModelList() != null
                    ? namespaceConfig.getModelList()
                    : List.of();
            log.debug("[Local] Using namespace model-list: namespace={}, models={}, traceId={}",
                    effectiveNamespace, namespaceModels.size(), traceId);
            return namespaceModels;
        }

        boolean explicitNamespace = !isBlank(requestNamespace);
        if (explicitNamespace) {
            Boolean useAllModels = semanticConfig.getUseAllModels();
            if (Boolean.FALSE.equals(useAllModels)) {
                log.debug("[Local] Model discovery explicitly disabled for namespace={}, traceId={}",
                        effectiveNamespace, traceId);
                return List.of();
            }
            List<String> visibleModels = semanticServiceResolver.getAllModelNames(effectiveNamespace);
            log.debug("[Local] Dynamic namespace model discovery: namespace={}, found {} models, traceId={}",
                    effectiveNamespace, visibleModels.size(), traceId);
            return visibleModels;
        }

        Boolean useAllModels = semanticConfig.getUseAllModels();
        if (Boolean.FALSE.equals(useAllModels)) {
            log.debug("[Local] Model discovery explicitly disabled, traceId={}", traceId);
            return List.of();
        }
        if (Boolean.TRUE.equals(useAllModels)) {
            List<String> discoveredModels = discoverModels(effectiveNamespace);
            log.debug("[Local] Dynamic model discovery (forced): found {} models, traceId={}",
                    discoveredModels.size(), traceId);
            return discoveredModels;
        }

        List<String> configuredModels = semanticConfig.getModelList();
        if (configuredModels == null || configuredModels.isEmpty()) {
            List<String> discoveredModels = discoverModels(effectiveNamespace);
            log.debug("[Local] Dynamic model discovery (auto): found {} models, traceId={}",
                    discoveredModels.size(), traceId);
            return discoveredModels;
        }

        log.debug("[Local] Using configured model-list for default namespace path: {} models, traceId={}",
                configuredModels.size(), traceId);
        return configuredModels;
    }

    private List<String> discoverModels(String namespace) {
        return isBlank(namespace)
                ? semanticServiceResolver.getAllModelNames()
                : semanticServiceResolver.getAllModelNames(namespace);
    }

    private static McpProperties.NamespaceSemanticConfig namespaceConfig(
            McpProperties.SemanticConfig semanticConfig,
            String namespace
    ) {
        if (semanticConfig == null || semanticConfig.getNamespaces() == null || namespace == null || namespace.isBlank()) {
            return null;
        }
        return semanticConfig.getNamespaces().get(namespace.trim());
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
        String effectiveNamespace = resolveNamespace(namespace);
        log.debug("[Local] Describing model: {}, format={}, traceId={}, namespace={}",
                model, format, traceId, effectiveNamespace);

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
                    model, levels, traceId, effectiveNamespace);

            String outputFormat = format != null ? format : "json";
            // 使用版本解析器获取元数据（传递 namespace）
            SemanticRequestContext ctx = buildMetadataContext(effectiveNamespace, authorization, options);
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
        String effectiveNamespace = resolveNamespace(namespace);
        log.debug("[Local] Querying model: {}, mode={}, traceId={}, namespace={}",
                model, mode, traceId, effectiveNamespace);

        try {
            SemanticQueryRequest request = queryPayloadMapper.toQueryRequest(
                    payload,
                    datasetProperties.getQuery().getUnknownPropertyPolicy());
            Map<String, Object> hints = request.getHints() == null
                    ? new HashMap<>()
                    : new HashMap<>(request.getHints());
            hints.put("fromMcp", true);
            if (isDslCtePayload(request)) {
                hints.put("dslCteCompileToDsl", true);
            }
            request.setHints(hints);
            String queryMode = mode != null ? mode : "execute";

            // 构建请求上下文（namespace + 安全信息）
            SemanticRequestContext ctx = buildQueryContext(effectiveNamespace, authorization, options);

            // 使用版本解析器执行查询
            SemanticQueryResponse response = semanticServiceResolver.queryModel(model, request, queryMode, ctx);
            QueryInputWarnings.attach(response, request);

            log.debug("[Local] Query executed: model={}, items={}, traceId={}",
                    model, response.getItems() != null ? response.getItems().size() : 0, traceId);
            return RX.success(response);

        } catch (QueryInputValidationException e) {
            log.warn("[Local] Query input rejected: model={}, code={}, traceId={}",
                    model, e.getCode(), traceId);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", e.getCode());
            error.put("violations", e.getViolations());
            return RX.<SemanticQueryResponse>builder()
                    .code(400)
                    .message(e.getMessage())
                    .error(error)
                    .build();
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
            Object columnsValue = map.get("columns");
            if (columnsValue instanceof List<?> columns) {
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

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveNamespace(String namespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, namespace);
    }

    private static boolean isDslCtePayload(SemanticQueryRequest request) {
        return request != null
                && request.getRoute() != null
                && "DSL_CTE".equalsIgnoreCase(request.getRoute().trim())
                && request.getExecutablePlan() != null;
    }

    /**
     * 保留字段名（不作为简写格式的 key）
     */
    private static final Set<String> RESERVED_SLICE_KEYS = Set.of(
            "$or", "$and", "field", "op", "value", "maxDepth", "$expr"
    );

}
