package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.spi.DbQueryDimension;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
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

    private final SemanticServiceResolver semanticServiceResolver;
    private final QueryModelLoader queryModelLoader;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    @Autowired
    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            QueryModelLoader queryModelLoader,
            ObjectMapper objectMapper,
            McpProperties mcpProperties
    ) {
        this.semanticServiceResolver = semanticServiceResolver;
        this.queryModelLoader = queryModelLoader;
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    public ModelCatalogService(
            SemanticServiceResolver semanticServiceResolver,
            QueryModelLoader queryModelLoader,
            ObjectMapper objectMapper
    ) {
        this(semanticServiceResolver, queryModelLoader, objectMapper, new McpProperties());
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
        List<String> modelNames = optionalStringList(safeOptions.get("modelNames"));
        if (modelNames == null) {
            modelNames = optionalStringList(safeOptions.get("models"));
        }
        if (modelNames == null) {
            modelNames = configuredCatalogModelNames();
        }
        if (modelNames == null) {
            modelNames = semanticServiceResolver.getAllModelNames();
        }
        modelNames = dedupe(modelNames);

        int fieldLimit = Math.max(0, intOr(safeOptions.get("fieldLimit"), 10));
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
                QueryModel qm = queryModelLoader.getJdbcQueryModel(modelName, namespace);
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
                if (qm.getShortAlias() != null && !qm.getShortAlias().isBlank()) {
                    item.put("shortAlias", qm.getShortAlias());
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

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("models", visibleModels);
        catalog.put("count", visibleModels.size());
        catalog.put("recommendedNext", "dataset.describe_model_internal");
        catalog.put("items", items);
        return catalog;
    }

    private List<String> configuredCatalogModelNames() {
        McpProperties.SemanticConfig semantic = mcpProperties.getSemantic();
        if (semantic == null) {
            return null;
        }
        Boolean useAllModels = semantic.getUseAllModels();
        if (Boolean.TRUE.equals(useAllModels)) {
            return null;
        }
        if (Boolean.FALSE.equals(useAllModels)) {
            return Collections.emptyList();
        }
        List<String> configuredModels = semantic.getModelList();
        if (configuredModels == null || configuredModels.isEmpty()) {
            return null;
        }
        return configuredModels;
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
}
