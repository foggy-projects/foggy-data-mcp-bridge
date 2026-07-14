package com.foggyframework.dataset.db.model.semantic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.spi.DbQueryDimension;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.NamespaceScope;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP-free model catalog builder for native dataset REST APIs.
 */
@Slf4j
@Service
public class SemanticModelCatalogService {

    private static final int MAX_CATALOG_BUILD_ATTEMPTS = 3;

    private final SemanticServiceV3 semanticServiceV3;
    private final QueryModelLoader queryModelLoader;
    private final SystemBundlesContext systemBundlesContext;
    private final ObjectMapper objectMapper;
    private final SemanticQueryPayloadMapper payloadMapper;
    private final CatalogSnapshotStore catalogSnapshotStore;
    private final CatalogRefreshCoordinator catalogRefreshCoordinator;

    /**
     * Immutable namespace catalog projection. A non-null identity means every
     * name, alias and model in the view was pinned from that exact snapshot.
     */
    public record NamespaceCatalogView(
            CatalogIdentity identity,
            List<String> modelNames,
            Map<String, String> aliasesByModel,
            Map<String, QueryModel> queryModels,
            Map<String, CatalogResolution<QueryModel>> resolutionsByModel
    ) {
        public NamespaceCatalogView {
            modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
            aliasesByModel = immutableLinkedMap(aliasesByModel);
            queryModels = immutableLinkedMap(queryModels);
            resolutionsByModel = immutableLinkedMap(resolutionsByModel);
            if (identity != null) {
                LinkedHashSet<String> names = new LinkedHashSet<>(modelNames);
                if (!aliasesByModel.keySet().equals(names)
                        || !queryModels.keySet().equals(names)
                        || !resolutionsByModel.keySet().equals(names)) {
                    throw new IllegalArgumentException(
                            "tracked namespace view maps must exactly match modelNames");
                }
                for (Map.Entry<String, CatalogResolution<QueryModel>> entry
                        : resolutionsByModel.entrySet()) {
                    String modelName = entry.getKey();
                    CatalogResolution<QueryModel> resolution = entry.getValue();
                    if (!identity.equals(resolution.catalogIdentity())
                            || !modelName.equals(resolution.canonicalName())
                            || resolution.model() != queryModels.get(modelName)) {
                        throw new IllegalArgumentException(
                                "tracked namespace resolution does not match view: "
                                        + modelName);
                    }
                }
            } else if (!resolutionsByModel.isEmpty()) {
                throw new IllegalArgumentException(
                        "legacy namespace view must not expose tracked resolutions");
            }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SemanticModelCatalogService(
            SemanticServiceV3 semanticServiceV3,
            QueryModelLoader queryModelLoader,
            SystemBundlesContext systemBundlesContext,
            ObjectMapper objectMapper,
            SemanticQueryPayloadMapper payloadMapper,
            CatalogSnapshotStore catalogSnapshotStore,
            CatalogRefreshCoordinator catalogRefreshCoordinator
    ) {
        this.semanticServiceV3 = semanticServiceV3;
        this.queryModelLoader = queryModelLoader;
        this.systemBundlesContext = systemBundlesContext;
        this.objectMapper = objectMapper;
        this.payloadMapper = payloadMapper;
        this.catalogSnapshotStore = catalogSnapshotStore;
        this.catalogRefreshCoordinator = catalogRefreshCoordinator;
    }

    /** Compatibility constructor for callers that do not own refresh wiring. */
    public SemanticModelCatalogService(
            SemanticServiceV3 semanticServiceV3,
            QueryModelLoader queryModelLoader,
            SystemBundlesContext systemBundlesContext,
            ObjectMapper objectMapper,
            SemanticQueryPayloadMapper payloadMapper,
            CatalogSnapshotStore catalogSnapshotStore
    ) {
        this(semanticServiceV3, queryModelLoader, systemBundlesContext,
                objectMapper, payloadMapper, catalogSnapshotStore, null);
    }

    /** Compatibility constructor for non-Spring callers. */
    public SemanticModelCatalogService(
            SemanticServiceV3 semanticServiceV3,
            QueryModelLoader queryModelLoader,
            SystemBundlesContext systemBundlesContext,
            ObjectMapper objectMapper,
            SemanticQueryPayloadMapper payloadMapper
    ) {
        this(semanticServiceV3, queryModelLoader, systemBundlesContext, objectMapper, payloadMapper,
                queryModelLoader instanceof QueryModelLoaderImpl loader
                        ? loader.getCatalogSnapshotStore()
                        : null,
                null);
    }

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

    public Map<String, Object> buildCatalog(Map<String, Object> options, String namespace, String authorization) {
        Map<String, Object> safeOptions = options != null ? options : Collections.emptyMap();
        String canonicalNamespace = CatalogIdentity.canonicalNamespace(namespace);
        for (int attempt = 1; attempt <= MAX_CATALOG_BUILD_ATTEMPTS; attempt++) {
            NamespaceCatalogView catalogView = namespaceCatalogView(canonicalNamespace);
            if (catalogView.identity() != null
                    && !isCurrentCatalogIdentity(
                    canonicalNamespace, catalogView.identity())) {
                continue;
            }
            Map<String, Object> catalog = buildCatalog(
                    safeOptions, canonicalNamespace, authorization, catalogView);
            if (catalogView.identity() == null
                    || isCurrentCatalogIdentity(
                    canonicalNamespace, catalogView.identity())) {
                return catalog;
            }
        }
        throw new IllegalStateException(
                "CATALOG_BUILD_STALE_RETRY_EXHAUSTED: namespace='"
                        + canonicalNamespace + "'");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCatalog(
            Map<String, Object> safeOptions,
            String namespace,
            String authorization,
            NamespaceCatalogView catalogView
    ) {
        List<String> requestedModelNames = optionalStringList(safeOptions.get("modelNames"));
        if (requestedModelNames == null) {
            requestedModelNames = optionalStringList(safeOptions.get("models"));
        }
        List<String> modelNames = selectModelNames(catalogView, requestedModelNames);

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
                QueryModel qm = catalogView.queryModels().get(modelName);
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
                String shortAlias = catalogView.aliasesByModel().get(modelName);
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

    private boolean isCurrentCatalogIdentity(
            String namespace,
            CatalogIdentity expected
    ) {
        return catalogSnapshotStore.readCurrent(namespace)
                .map(CatalogSnapshot::identity)
                .filter(expected::equals)
                .isPresent();
    }

    public List<String> getAllModelNames() {
        return namespaceCatalogView(NamespaceContext.getNamespace()).modelNames();
    }

    public List<String> getAllModelNames(String namespace) {
        return namespaceCatalogView(namespace).modelNames();
    }

    /**
     * Returns one immutable namespace catalog view. Lifecycle-aware production
     * loaders materialize every discovered model, then pin names, aliases and
     * model objects from one final snapshot. Legacy/custom loaders remain
     * uncached and expose a null identity.
     */
    public NamespaceCatalogView namespaceCatalogView(String namespace) {
        String canonicalNamespace = CatalogIdentity.canonicalNamespace(namespace);
        if (catalogSnapshotStore != null
                && catalogRefreshCoordinator != null
                && queryModelLoader instanceof QueryModelLoaderImpl) {
            return lifecycleNamespaceCatalogView(canonicalNamespace);
        }
        return scanNamespaceCatalogView(canonicalNamespace);
    }

    public void clearCachedModelNames() {
        // Compatibility no-op. Discovery is part of the immutable catalog and
        // production invalidation must go through the scoped lifecycle authority.
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
            if (e instanceof CatalogAdmissionBlockedException blocked) {
                throw blocked;
            }
            log.warn("Failed to build metadata-backed native list_models catalog: {}", e.getMessage());
            return null;
        }
    }

    private NamespaceCatalogView lifecycleNamespaceCatalogView(String namespace) {
        CatalogSnapshot active = catalogSnapshotStore.readCurrent(namespace)
                .orElse(null);
        if (active != null && isCompleteNamespaceSnapshot(active)) {
            return namespaceView(active);
        }

        catalogRefreshCoordinator.refresh(CatalogRefreshRequest.namespace(
                namespace, CatalogRefreshTrigger.EXPLICIT_RECOVERY));
        CatalogSnapshot recovered = catalogSnapshotStore.readCurrent(namespace)
                .orElseThrow(() -> new IllegalStateException(
                        "CATALOG_RECOVERY_PUBLISHED_SNAPSHOT_ABSENT: namespace='"
                                + namespace + "'"));
        if (!isCompleteNamespaceSnapshot(recovered)) {
            throw new IllegalStateException(
                    "CATALOG_RECOVERY_PUBLISHED_INCOMPLETE_SNAPSHOT: namespace='"
                            + namespace + "'");
        }
        return namespaceView(recovered);
    }

    private NamespaceCatalogView namespaceView(CatalogSnapshot snapshot) {
        List<String> snapshotNames = List.copyOf(snapshot.discoveredQueryModelNames());
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        LinkedHashMap<String, QueryModel> models = new LinkedHashMap<>();
        LinkedHashMap<String, CatalogResolution<QueryModel>> resolutions =
                new LinkedHashMap<>();
        for (String modelName : snapshotNames) {
            var provenance = snapshot.queryModelProvenance(modelName)
                    .orElseThrow(() -> new IllegalStateException(
                            "CATALOG_QUERY_PROVENANCE_ABSENT: " + modelName));
            QueryModel model = snapshot.resolveQueryModel(modelName)
                    .orElseThrow(() -> new IllegalStateException(
                            "complete catalog snapshot lost query model " + modelName));
            aliases.put(modelName, snapshot.canonicalToAlias().get(modelName));
            models.put(modelName, model);
            resolutions.put(modelName, new CatalogResolution<>(
                    modelName,
                    model,
                    snapshot.identity(),
                    provenance.datasourceBindings(),
                    provenance.bindingIdentityComplete()));
        }
        return new NamespaceCatalogView(
                snapshot.identity(), snapshotNames, aliases, models, resolutions);
    }

    private boolean isCompleteNamespaceSnapshot(CatalogSnapshot snapshot) {
        LinkedHashSet<String> materialized = new LinkedHashSet<>(
                snapshot.queryModels().keySet());
        materialized.addAll(snapshot.syntheticQueryModels().keySet());
        return snapshot.discoveredQueryModelNames().equals(materialized);
    }

    private NamespaceCatalogView scanNamespaceCatalogView(String namespace) {
        LinkedHashMap<String, QueryModel> models = new LinkedHashMap<>();
        try (NamespaceScope ignored = NamespaceContext.open(namespace)) {
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
                                    models.putIfAbsent(qm.getName(), qm);
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
        }

        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        models.forEach((modelName, model) -> {
            String alias = model.getShortAlias();
            if (alias != null && !alias.isBlank()) {
                aliases.put(modelName, alias);
            }
        });
        log.info("Native legacy catalog scanned {} models for namespace={}: {}",
                models.size(), namespace, models.keySet());
        return new NamespaceCatalogView(
                null, List.copyOf(models.keySet()), aliases, models, Map.of());
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

    private static List<String> selectModelNames(
            NamespaceCatalogView catalogView,
            List<String> requestedNames
    ) {
        if (requestedNames == null) {
            return catalogView.modelNames();
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String requested : requestedNames) {
            if (catalogView.queryModels().containsKey(requested)) {
                selected.add(requested);
                continue;
            }
            catalogView.aliasesByModel().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(requested))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(selected::add);
        }
        return List.copyOf(selected);
    }

    private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
