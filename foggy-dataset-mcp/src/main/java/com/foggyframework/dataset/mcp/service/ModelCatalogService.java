package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.LegacySemanticModelCatalogReadAdapter;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.DbQueryDimension;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the normalized model catalog used by both MCP list_models and host-facing HTTP APIs.
 */
@Slf4j
@Service
public class ModelCatalogService {

    private static final int MAX_CATALOG_VIEW_ATTEMPTS = 3;

    private final SemanticServiceResolver semanticServiceResolver;
    private final SemanticModelCatalogReadPort modelCatalogReadPort;
    private final boolean sharedCatalogAuthority;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    @Autowired
    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            ObjectMapper objectMapper,
            McpProperties mcpProperties,
            SemanticModelCatalogService semanticModelCatalogService
    ) {
        this.semanticServiceResolver = semanticServiceResolver;
        this.modelCatalogReadPort = semanticModelCatalogService;
        this.sharedCatalogAuthority = true;
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    /**
     * Compatibility constructor retained for callers compiled against the old
     * Spring wiring shape. New Spring wiring uses the shared catalog authority
     * and does not inject a model loader into this addon service.
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            QueryModelLoader queryModelLoader,
            ObjectMapper objectMapper,
            McpProperties mcpProperties,
            SemanticModelCatalogService semanticModelCatalogService
    ) {
        this.semanticServiceResolver = semanticServiceResolver;
        this.modelCatalogReadPort = semanticModelCatalogService != null
                ? semanticModelCatalogService
                : new LegacySemanticModelCatalogReadAdapter(queryModelLoader);
        this.sharedCatalogAuthority = semanticModelCatalogService != null;
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    /** Compatibility constructor for callers without the shared catalog authority. */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            QueryModelLoader queryModelLoader,
            ObjectMapper objectMapper,
            McpProperties mcpProperties
    ) {
        this(semanticServiceResolver, queryModelLoader, objectMapper,
                mcpProperties, null);
    }

    /** Compatibility constructor for callers without the shared catalog authority. */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            QueryModelLoader queryModelLoader,
            ObjectMapper objectMapper
    ) {
        this(semanticServiceResolver, queryModelLoader, objectMapper,
                new McpProperties(), null);
    }

    public Map<String, Object> buildCatalogResponse(Map<String, Object> options, String namespace, String authorization) {
        Map<String, Object> safeOptions = options != null ? options : Collections.emptyMap();
        Map<String, Object> catalog = buildCatalog(safeOptions, namespace, authorization);
        String format = stringOr(safeOptions.get("format"), "json");

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("format", format);
        if ("markdown".equalsIgnoreCase(format)) {
            dataMap.put("content", renderCatalogMarkdown(catalog));
        } else if ("all".equalsIgnoreCase(format)) {
            dataMap.put("content", renderCatalogMarkdown(catalog));
            dataMap.put("data", catalog);
        } else {
            dataMap.put("content", toJson(catalog));
            dataMap.put("data", catalog);
        }
        return dataMap;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildCatalog(Map<String, Object> options, String namespace, String authorization) {
        Map<String, Object> safeOptions = options != null ? options : Collections.emptyMap();
        if (!sharedCatalogAuthority) {
            return buildCatalogOnce(safeOptions, namespace, authorization, null);
        }

        NamespaceCatalogView namespaceView = modelCatalogReadPort
                .namespaceCatalogView(namespace);
        if (namespaceView.identity() == null) {
            return buildCatalogOnce(
                    safeOptions, namespace, authorization, namespaceView);
        }

        // Metadata is produced through the semantic service and may observe a
        // concurrent publication. The post-read turns the whole catalog build
        // into a bounded seqlock: return one generation or fail closed.
        for (int attempt = 1; attempt <= MAX_CATALOG_VIEW_ATTEMPTS; attempt++) {
            Map<String, Object> catalog = buildCatalogOnce(
                    safeOptions, namespace, authorization, namespaceView);
            NamespaceCatalogView observedAfterBuild = modelCatalogReadPort
                    .namespaceCatalogView(namespace);
            if (namespaceView.identity().equals(observedAfterBuild.identity())) {
                return catalog;
            }
            if (observedAfterBuild.identity() == null) {
                throw new IllegalStateException(
                        "CATALOG_AUTHORITY_IDENTITY_LOST: namespace='"
                                + namespaceView.identity().namespace() + "'");
            }
            namespaceView = observedAfterBuild;
        }

        throw new IllegalStateException(
                "CATALOG_VIEW_STALE_RETRY_EXHAUSTED: namespace='"
                        + namespaceView.identity().namespace() + "'");
    }

    private Map<String, Object> buildCatalogOnce(
            Map<String, Object> safeOptions,
            String namespace,
            String authorization,
            NamespaceCatalogView namespaceView
    ) {
        ModelNameSelection selection = selectModelNames(safeOptions, namespace);
        List<String> modelNames = resolveModelNames(selection, namespace, namespaceView);
        int fieldLimit = Math.max(0, intOr(safeOptions.get("fieldLimit"), 10));
        CatalogData catalogData = buildCatalogData(
                modelNames, namespace, authorization, safeOptions, fieldLimit, namespaceView);

        if (shouldFallbackToDynamicDiscovery(selection, namespace, catalogData)) {
            List<String> dynamicModelNames = resolveModelNames(
                    new ModelNameSelection(null, ModelNameSource.DYNAMIC),
                    namespace,
                    namespaceView);
            if (!dynamicModelNames.isEmpty() && !dynamicModelNames.equals(modelNames)) {
                CatalogData fallbackData = buildCatalogData(
                        dynamicModelNames, namespace, authorization, safeOptions,
                        fieldLimit, namespaceView);
                if (!fallbackData.visibleModels().isEmpty()) {
                    log.info("Configured MCP model-list resolved no models for namespace {}; using dynamic model discovery",
                            namespace);
                    catalogData = fallbackData;
                }
            }
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("models", catalogData.visibleModels());
        catalog.put("count", catalogData.visibleModels().size());
        catalog.put("recommendedNext", "dataset.describe_model_internal");
        catalog.put("items", catalogData.items());
        return catalog;
    }

    @SuppressWarnings("unchecked")
    private CatalogData buildCatalogData(
            List<String> modelNames,
            String namespace,
            String authorization,
            Map<String, Object> safeOptions,
            int fieldLimit,
            NamespaceCatalogView namespaceView
    ) {
        Map<String, Object> metadata = fetchCatalogMetadata(modelNames, namespace, authorization, safeOptions);
        Map<String, Object> fields = metadata != null && metadata.get("fields") instanceof Map<?, ?>
                ? (Map<String, Object>) metadata.get("fields")
                : Collections.emptyMap();
        Map<String, Object> modelsInfo = metadata != null && metadata.get("models") instanceof Map<?, ?>
                ? (Map<String, Object>) metadata.get("models")
                : Collections.emptyMap();

        List<String> visibleModels = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String modelName : modelNames) {
            try {
                QueryModel qm = namespaceView == null
                        ? modelCatalogReadPort.resolveModel(
                                null, modelName, namespace)
                        : SemanticModelCatalogReadPort.resolveModelFromView(
                                namespaceView, modelName);
                if (qm == null) {
                    continue;
                }
                String caption = qm.getCaption() != null ? qm.getCaption() : "";
                Map<String, Object> modelInfo = modelsInfo.get(modelName) instanceof Map<?, ?>
                        ? (Map<String, Object>) modelsInfo.get(modelName)
                        : Collections.emptyMap();
                List<String> preview = fieldPreview(fields, modelName, fieldLimit);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("model", modelName);
                item.put("caption", stringOr(modelInfo.get("name"), caption.isEmpty() ? modelName : caption));
                String shortAlias = namespaceView == null
                        ? qm.getShortAlias()
                        : SemanticModelCatalogReadPort.resolveAliasFromView(
                                namespaceView, modelName);
                if (shortAlias != null && !shortAlias.isBlank()) {
                    item.put("shortAlias", shortAlias);
                }
                item.put("description", stringOr(modelDescription(qm), stringOr(modelInfo.get("purpose"), "")));
                String itemNamespace = inferNamespace(modelName);
                if (itemNamespace != null) {
                    item.put("namespace", itemNamespace);
                }
                List<String> physicalTables = physicalTables(qm);
                if (!physicalTables.isEmpty()) {
                    item.put("physicalTables", physicalTables);
                }
                if (fieldLimit > 0) {
                    String primaryTimeField = primaryTimeField(qm);
                    if (primaryTimeField != null && !primaryTimeField.isBlank()) {
                        item.put("primaryTimeField", primaryTimeField);
                    }
                    item.put("fieldPreview", preview);
                    item.put("fieldCount", fieldCount(fields, modelName));
                }
                visibleModels.add(modelName);
                items.add(item);
            } catch (Exception e) {
                log.warn("Failed to load model {} for list_models catalog: {}", modelName, e.getMessage());
            }
        }

        return new CatalogData(visibleModels, items);
    }

    private ModelNameSelection selectModelNames(Map<String, Object> options, String namespace) {
        List<String> modelNames = optionalStringList(options.get("modelNames"));
        if (modelNames == null) {
            modelNames = optionalStringList(options.get("models"));
        }
        if (modelNames != null) {
            return new ModelNameSelection(modelNames, ModelNameSource.REQUEST);
        }

        McpProperties.SemanticConfig semantic = mcpProperties != null ? mcpProperties.getSemantic() : null;
        if (semantic == null) {
            return new ModelNameSelection(null, ModelNameSource.DYNAMIC);
        }

        McpProperties.NamespaceSemanticConfig namespaceConfig = namespaceConfig(semantic, namespace);
        if (namespaceConfig != null) {
            return new ModelNameSelection(
                    namespaceConfig.getModelList() != null ? namespaceConfig.getModelList() : Collections.emptyList(),
                    ModelNameSource.NAMESPACE_CONFIGURED
            );
        }

        Boolean useAllModels = semantic.getUseAllModels();
        if (Boolean.TRUE.equals(useAllModels)) {
            return new ModelNameSelection(null, ModelNameSource.DYNAMIC);
        }
        if (Boolean.FALSE.equals(useAllModels)) {
            return new ModelNameSelection(Collections.emptyList(), ModelNameSource.DISABLED);
        }
        List<String> configuredModels = semantic.getModelList();
        if (configuredModels == null || configuredModels.isEmpty()) {
            return new ModelNameSelection(null, ModelNameSource.DYNAMIC);
        }
        return new ModelNameSelection(configuredModels, ModelNameSource.GLOBAL_CONFIGURED);
    }

    private static McpProperties.NamespaceSemanticConfig namespaceConfig(
            McpProperties.SemanticConfig semantic,
            String namespace
    ) {
        if (semantic.getNamespaces() == null || isBlank(namespace)) {
            return null;
        }
        return semantic.getNamespaces().get(namespace.trim());
    }

    private List<String> resolveModelNames(
            ModelNameSelection selection,
            String namespace,
            NamespaceCatalogView namespaceView
    ) {
        List<String> modelNames = selection.modelNames();
        if (modelNames == null) {
            if (namespaceView != null) {
                modelNames = namespaceView.modelNames();
            } else if (isBlank(namespace)) {
                modelNames = semanticServiceResolver.getAllModelNames();
            } else {
                modelNames = semanticServiceResolver.getAllModelNames(namespace);
            }
        }
        return dedupe(modelNames);
    }

    private static boolean shouldFallbackToDynamicDiscovery(
            ModelNameSelection selection,
            String namespace,
            CatalogData catalogData
    ) {
        return selection.source() == ModelNameSource.GLOBAL_CONFIGURED
                && !isBlank(namespace)
                && catalogData.visibleModels().isEmpty();
    }

    private Map<String, Object> fetchCatalogMetadata(
            List<String> modelNames,
            String namespace,
            String authorization,
            Map<String, Object> options
    ) {
        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(modelNames);
            SemanticRequestContext metadataContext = SemanticRequestContext.of(
                    namespace,
                    toSecurityContext(authorization),
                    optionalStringSet(options.get("visibleFields")),
                    extractDeniedColumns(options),
                    null
            );
            SemanticMetadataResponse response = semanticServiceResolver.getMetadata(request, "json", metadataContext);
            return response != null ? response.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to build metadata-backed list_models catalog: {}", e.getMessage());
            return null;
        }
    }

    private static ModelResultContext.SecurityContext toSecurityContext(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return ModelResultContext.SecurityContext.fromAuthorization(authorization);
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldPreview(Map<String, Object> fields, String modelName, int fieldLimit) {
        List<String> result = new ArrayList<>();
        int limit = Math.max(0, fieldLimit);
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (result.size() >= limit) {
                break;
            }
            if (!(entry.getValue() instanceof Map<?, ?> fieldInfo)) {
                continue;
            }
            Object models = fieldInfo.get("models");
            if (models instanceof Map<?, ?> modelMap && modelMap.containsKey(modelName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<DeniedPhysicalColumn> extractDeniedColumns(Map<String, Object> options) {
        Object value = options.get("deniedColumns");
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

    private static int fieldCount(Map<String, Object> fields, String modelName) {
        int count = 0;
        for (Object value : fields.values()) {
            if (!(value instanceof Map<?, ?> fieldInfo)) {
                continue;
            }
            Object models = fieldInfo.get("models");
            if (models instanceof Map<?, ?> modelMap && modelMap.containsKey(modelName)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> physicalTables(QueryModel qm) {
        List<String> result = new ArrayList<>();
        if (qm.getJdbcModel() != null && qm.getJdbcModel().getTableName() != null) {
            result.add(qm.getJdbcModel().getTableName());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static String renderCatalogMarkdown(Map<String, Object> catalog) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Model Catalog\n\n");
        Object itemsValue = catalog.get("items");
        if (!(itemsValue instanceof List<?> items) || items.isEmpty()) {
            markdown.append("No data models available.");
            return markdown.toString();
        }
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> item)) {
                continue;
            }
            String model = stringValue(item.get("model"));
            String caption = stringOr(item.get("caption"), model);
            markdown.append("- **").append(caption).append("** (`").append(model).append("`)\n");
            String description = stringValue(item.get("description"));
            if (description != null && !description.isBlank()) {
                markdown.append("  - Description: ").append(sanitizeMarkdownLine(description)).append("\n");
            }
            String shortAlias = stringValue(item.get("shortAlias"));
            if (shortAlias != null && !shortAlias.isBlank()) {
                markdown.append("  - Short alias: ").append(sanitizeMarkdownLine(shortAlias)).append("\n");
            }
        }
        return markdown.toString();
    }

    private String toJson(Map<String, Object> catalog) {
        try {
            return objectMapper.writeValueAsString(catalog);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list_models catalog as JSON: {}", e.getMessage());
            return catalog.toString();
        }
    }

    private static String inferNamespace(String modelName) {
        if (modelName != null && modelName.contains(":")) {
            return modelName.substring(0, modelName.indexOf(':'));
        }
        if (modelName != null && modelName.startsWith("Odoo")) {
            return "odoo";
        }
        return null;
    }

    private static String modelDescription(QueryModel qm) {
        if (qm.getAi() != null && qm.getAi().getPrompt() != null && !qm.getAi().getPrompt().isBlank()) {
            return qm.getAi().getPrompt();
        }
        return qm.getDescription();
    }

    private static String primaryTimeField(QueryModel qm) {
        if (qm.getQueryDimensions() == null) {
            return "";
        }
        for (DbQueryDimension dim : qm.getQueryDimensions()) {
            if (dim.getDimension() != null && "business_date".equals(dim.getDimension().getTimeRole())) {
                return dim.getDimension().getEffectiveName() + "$id";
            }
        }
        for (DbQueryDimension dim : qm.getQueryDimensions()) {
            if (dim.getDimension() != null
                    && dim.getDimension().getTimeRole() != null
                    && dim.getDimension().getTimeRole().contains("date")) {
                return dim.getDimension().getEffectiveName() + "$id";
            }
        }
        return "";
    }

    private static String stringOr(Object value, String fallback) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> optionalStringList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = stringValue(item);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    private static List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                seen.add(value);
            }
        }
        return List.copyOf(seen);
    }

    private static Set<String> optionalStringSet(Object value) {
        List<String> list = optionalStringList(value);
        return list == null ? null : new LinkedHashSet<>(list);
    }

    private static String sanitizeMarkdownLine(String value) {
        return value.replace("\n", " ").replace("\r", " ").replace("|", "｜");
    }

    private enum ModelNameSource {
        REQUEST,
        NAMESPACE_CONFIGURED,
        GLOBAL_CONFIGURED,
        DYNAMIC,
        DISABLED
    }

    private record ModelNameSelection(List<String> modelNames, ModelNameSource source) {
    }

    private record CatalogData(List<String> visibleModels, List<Map<String, Object>> items) {
    }
}
