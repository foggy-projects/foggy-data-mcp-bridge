package com.foggyframework.dataset.db.model.semantic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.db.model.spi.DbQueryDimension;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * MCP-free model catalog builder for native dataset REST APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticModelCatalogService {

    private final SemanticServiceV3 semanticServiceV3;
    private final QueryModelLoader queryModelLoader;
    private final SystemBundlesContext systemBundlesContext;
    private final ObjectMapper objectMapper;
    private final SemanticQueryPayloadMapper payloadMapper;

    private volatile List<String> cachedModelNames;

    public Map<String, Object> buildCatalogResponse(Map<String, Object> options, String namespace,
                                                    String authorization) {
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
            modelNames = getAllModelNames();
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
                    item.put("fieldPreview", fieldPreview(fields, modelName, fieldLimit));
                    item.put("fieldCount", fieldCount(fields, modelName));
                }
                visibleModels.add(modelName);
                items.add(item);
            } catch (Exception e) {
                log.warn("Failed to load model {} for native list_models catalog: {}", modelName, e.getMessage());
            }
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("models", visibleModels);
        catalog.put("count", visibleModels.size());
        catalog.put("recommendedNext", "dataset.describe_model_internal");
        catalog.put("items", items);
        return catalog;
    }

    public List<String> getAllModelNames() {
        List<String> cached = cachedModelNames;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedModelNames == null) {
                cachedModelNames = scanAllModelNames();
                log.info("Native dataset catalog scanned {} models: {}", cachedModelNames.size(), cachedModelNames);
            }
            return cachedModelNames;
        }
    }

    public void clearCachedModelNames() {
        cachedModelNames = null;
    }

    private Map<String, Object> fetchCatalogMetadata(List<String> modelNames, String namespace,
                                                     String authorization, Map<String, Object> options) {
        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(modelNames);
            SemanticRequestContext context = SemanticRequestContext.of(
                    namespace,
                    authorization != null && !authorization.isBlank()
                            ? com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext.SecurityContext.fromAuthorization(authorization)
                            : null,
                    payloadMapper.optionalStringSet(options.get("visibleFields")),
                    payloadMapper.extractDeniedColumns(options),
                    payloadMapper.extractSystemSlice(options)
            );
            SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", context);
            return response != null ? response.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to build metadata-backed native list_models catalog: {}", e.getMessage());
            return null;
        }
    }

    private List<String> scanAllModelNames() {
        LinkedHashSet<String> modelNames = new LinkedHashSet<>();
        try {
            systemBundlesContext.getBundleList().forEach(bundle -> {
                try {
                    BundleResource[] resources = bundle.findBundleResources("**/*.qm");
                    if (resources == null) {
                        return;
                    }
                    for (BundleResource resource : resources) {
                        try {
                            QueryModel qm = queryModelLoader.loadJdbcQueryModel(resource);
                            if (qm != null && qm.getName() != null && !qm.getName().isBlank()) {
                                modelNames.add(qm.getName());
                            }
                        } catch (Exception e) {
                            log.debug("Failed to load QM resource {}: {}",
                                    resource.getResource().getDescription(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to scan bundle {} for QM files: {}", bundle.getName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to scan native model catalog: {}", e.getMessage());
        }
        return List.copyOf(modelNames);
    }

    private String toJson(Map<String, Object> catalog) {
        try {
            return objectMapper.writeValueAsString(catalog);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize native list_models catalog as JSON: {}", e.getMessage());
            return catalog.toString();
        }
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

    private static String stringOr(Object value, String fallback) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String sanitizeMarkdownLine(String value) {
        return value.replace("\n", " ").replace("\r", " ").replace("|", "｜");
    }
}
