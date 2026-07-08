package com.foggyframework.runtime.api.controller;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.ModelDescribeRequest;
import com.foggyframework.runtime.api.dto.ModelDescribeResponse;
import com.foggyframework.runtime.api.dto.ModelRefreshFailure;
import com.foggyframework.runtime.api.dto.ModelRefreshRequest;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.ModelValidateIssue;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.ModelValidateResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeModelsController {

    private final RuntimeApiResponseFactory responses;
    private final SemanticModelCatalogService catalogService;
    private final SemanticServiceV3 semanticServiceV3;
    private final SystemBundlesContext systemBundlesContext;
    private final QueryModelLoader queryModelLoader;
    private final TableModelLoaderManager tableModelLoaderManager;
    private final DatasetProperties datasetProperties;

    public RuntimeModelsController(
            RuntimeApiResponseFactory responses,
            SemanticModelCatalogService catalogService,
            SemanticServiceV3 semanticServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this.responses = responses;
        this.catalogService = catalogService;
        this.semanticServiceV3 = semanticServiceV3;
        this.systemBundlesContext = systemBundlesContext;
        this.queryModelLoader = queryModelLoader;
        this.tableModelLoaderManager = tableModelLoaderManager;
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
    }

    @GetMapping(RuntimeApiRoutes.V1.MODELS)
    public RuntimeEnvelope<Map<String, Object>> listModels(
            @RequestParam Map<String, String> query,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        Map<String, Object> options = new LinkedHashMap<>(query);
        String bodyNamespace = stringValue(options.remove("namespace"));
        Map<String, Object> response = catalogService.buildCatalogResponse(
                options,
                resolveNamespace(namespace, bodyNamespace),
                null
        );
        return responses.ok(response);
    }

    @PostMapping(RuntimeApiRoutes.V1.MODEL_DESCRIBE)
    public RuntimeEnvelope<ModelDescribeResponse> describeModel(
            @PathVariable String model,
            @RequestBody(required = false) ModelDescribeRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String normalizedModel = blankToNull(model);
        if (normalizedModel == null) {
            return fail("INVALID_REQUEST", "models.describe", "Missing required path variable: model",
                    model, "Provide a QM model name in the URL path.", false);
        }

        String format = stringOr(request != null ? request.format() : null, "json");
        SemanticMetadataRequest metadataRequest = new SemanticMetadataRequest();
        metadataRequest.setQmModels(List.of(normalizedModel));
        if (request != null) {
            metadataRequest.setFields(emptyToNull(request.fields()));
            metadataRequest.setLevels(emptyToNull(request.levels()));
            if (request.includeExamples() != null) {
                metadataRequest.setIncludeExamples(request.includeExamples());
            }
        }

        SemanticMetadataResponse metadata = semanticServiceV3.getMetadata(
                metadataRequest,
                format,
                SemanticRequestContext.ofNamespace(resolveNamespace(namespace, request != null ? request.namespace() : null))
        );

        if (metadata == null || isModelMissing(normalizedModel, metadata)) {
            return fail("MODEL_NOT_FOUND", "models.describe", "QM model was not found.",
                    normalizedModel, "Refresh or register the QM model, then retry.", false);
        }

        ModelDescribeResponse response = new ModelDescribeResponse(
                metadata.getFormat(),
                metadata.getContent(),
                metadata.getData()
        );
        return responses.ok(response);
    }

    @PostMapping(RuntimeApiRoutes.V1.MODELS_VALIDATE)
    public RuntimeEnvelope<ModelValidateResponse> validateModels(
            @RequestBody(required = false) ModelValidateRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String path = blankToNull(request != null ? request.path() : null);
        if (path == null) {
            return fail("INVALID_REQUEST", "models.validate", "Missing required body field: path",
                    null, "Provide a directory path containing TM/QM files.", false);
        }

        File pathFile = new File(path);
        if (!pathFile.exists() || !pathFile.isDirectory()) {
            return fail("INVALID_REQUEST", "models.validate", "Path must be an existing directory: " + path,
                    null, "Provide a directory path containing TM/QM files.", false);
        }

        ModelValidateResponse response;
        try {
            response = validateModelDirectory(
                    path,
                    resolveNamespace(namespace, request != null ? request.namespace() : null),
                    booleanOr(request != null ? request.watch() : null, false),
                    booleanOr(request != null ? request.clearExisting() : null, true),
                    booleanOr(request != null ? request.includeStackTrace() : null, false)
            );
        } catch (Exception e) {
            return fail("MODEL_VALIDATE_FAILED", "models.validate", e.getMessage(),
                    null, "Check the model directory and retry.", false);
        }

        if (!response.valid()) {
            return fail(
                    "MODEL_VALIDATE_FAILED",
                    "models.validate",
                    firstValidationMessage(response),
                    null,
                    "Inspect diagnostics.attributes.validation.errors and fix the TM/QM files.",
                    false,
                    diagnosticsForValidation(response)
            );
        }

        return responses.ok(response);
    }

    @PostMapping(RuntimeApiRoutes.V1.MODELS_REFRESH)
    public RuntimeEnvelope<ModelRefreshResponse> refreshModels(
            @RequestBody(required = false) ModelRefreshRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String effectiveNamespace = resolveNamespace(namespace, request != null ? request.namespace() : null);
        List<String> requestedModels = dedupe(request != null ? request.models() : null);
        List<String> warnings = new ArrayList<>();

        try {
            tableModelLoaderManager.clearByNamespace(effectiveNamespace);
            queryModelLoader.clearByNamespace(effectiveNamespace);
            clearCatalogCacheIfSupported();
        } catch (Exception e) {
            return fail("MODEL_REFRESH_FAILED", "models.refresh", e.getMessage(),
                    null, "Check model loader state and retry.", false);
        }

        List<String> modelsToLoad = requestedModels;
        String scope = requestedModels.isEmpty() ? "namespace" : "models";
        if (modelsToLoad.isEmpty()) {
            Map<String, Object> catalog = catalogService.buildCatalog(Map.of("fieldLimit", 0), effectiveNamespace, null);
            modelsToLoad = optionalStringList(catalog.get("models"));
            if (modelsToLoad.isEmpty()) {
                warnings.add("No QM models were discovered for refresh warmup.");
            }
        }

        List<String> refreshedModels = new ArrayList<>();
        List<ModelRefreshFailure> failures = new ArrayList<>();
        for (String modelName : modelsToLoad) {
            try {
                queryModelLoader.getJdbcQueryModel(modelName, effectiveNamespace);
                refreshedModels.add(modelName);
            } catch (Exception e) {
                failures.add(new ModelRefreshFailure(modelName, e.getMessage()));
            }
        }

        ModelRefreshResponse response = new ModelRefreshResponse(
                effectiveNamespace,
                scope,
                List.of("table-model", "query-model", "model-catalog"),
                refreshedModels,
                refreshedModels.size(),
                failures.size(),
                failures,
                warnings
        );
        if (!failures.isEmpty()) {
            return fail(
                    "MODEL_REFRESH_FAILED",
                    "models.refresh",
                    firstRefreshFailureMessage(response),
                    failures.get(0).model(),
                    "Inspect diagnostics.attributes.refresh.failures and fix or register the requested QM model.",
                    false,
                    diagnosticsForRefresh(response)
            );
        }
        return responses.ok(response);
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return fail(code, phase, message, model, suggestedNextAction, safeToAutoRepair, RuntimeDiagnostics.empty());
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics
    ) {
        return responses.fail(
                code,
                phase,
                message,
                model,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair,
                diagnostics
        );
    }

    private String resolveNamespace(String headerNamespace, String bodyNamespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
    }

    private void clearCatalogCacheIfSupported() {
        try {
            catalogService.getClass().getMethod("clearCachedModelNames").invoke(catalogService);
        } catch (NoSuchMethodException ignored) {
            // Older dataset-model builds do not expose an explicit catalog cache reset.
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear model catalog cache", e);
        }
    }

    private ModelValidateResponse validateModelDirectory(
            String path,
            String namespace,
            boolean watch,
            boolean clearExisting,
            boolean includeStackTrace
    ) {
        Instant startedAt = Instant.now();
        String bundleName = validationBundleName(namespace);
        boolean registered = false;
        try {
            if (clearExisting && systemBundlesContext.containBundle(bundleName)) {
                systemBundlesContext.removeBundle(bundleName);
            }
            Bundle bundle = findExistingBundleForPath(namespace, path);
            List<ModelValidateIssue> warnings = new ArrayList<>();
            if (bundle == null) {
                registered = systemBundlesContext.addExternalBundle(bundleName, namespace, path, watch);
                if (!registered) {
                    throw new IllegalStateException("Bundle registration failed: " + bundleName);
                }

                bundle = systemBundlesContext.getBundleByName(bundleName);
                if (bundle == null) {
                    throw new IllegalStateException("Registered bundle was not found: " + bundleName);
                }
            } else {
                warnings.add(warning(
                        "BUNDLE_ALREADY_REGISTERED",
                        "Validation reused existing bundle '" + bundle.getName()
                                + "' for the same namespace and path instead of registering a duplicate temporary bundle."
                ));
            }

            List<ModelValidateIssue> errors = new ArrayList<>();
            Set<String> failedTmNames = new HashSet<>();
            int totalFiles = 0;

            BundleResource[] tmResources = findBundleResources(bundle, "**/*.tm");
            totalFiles += tmResources.length;
            for (BundleResource tmResource : tmResources) {
                int beforeSize = errors.size();
                validateTmResource(tmResource, namespace, includeStackTrace, errors);
                if (errors.size() > beforeSize) {
                    failedTmNames.add(extractModelName(relativePath(tmResource)));
                }
            }

            BundleResource[] qmResources = findBundleResources(bundle, "**/*.qm");
            totalFiles += qmResources.length;
            for (BundleResource qmResource : qmResources) {
                int beforeSize = errors.size();
                validateQmResource(qmResource, includeStackTrace, errors);
                if (errors.size() > beforeSize && !failedTmNames.isEmpty()) {
                    markCascadingErrors(errors, beforeSize, failedTmNames);
                }
            }

            int cascadingErrors = (int) errors.stream()
                    .filter(issue -> "CASCADING".equals(issue.category()))
                    .count();
            return new ModelValidateResponse(
                    errors.isEmpty(),
                    namespace,
                    path,
                    totalFiles,
                    totalFiles - errors.size(),
                    errors.size(),
                    cascadingErrors,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    List.copyOf(errors),
                    List.copyOf(warnings)
            );
        } finally {
            if (registered) {
                try {
                    systemBundlesContext.removeBundle(bundleName);
                } catch (Exception ignored) {
                    // Validation must report model issues; cleanup failure should not mask that result.
                }
            }
        }
    }

    private Bundle findExistingBundleForPath(String namespace, String path) {
        List<Bundle> bundles = systemBundlesContext.getBundleList();
        if (bundles == null || bundles.isEmpty()) {
            return null;
        }

        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedPath = normalizeBundlePath(path);
        for (Bundle bundle : bundles) {
            if (bundle == null) {
                continue;
            }
            BundleDefinition definition = bundle.getDefinition();
            if (definition == null) {
                continue;
            }
            if (!normalizedNamespace.equals(normalizeNamespace(definition.getNamespace()))) {
                continue;
            }
            if (normalizedPath.equals(normalizeBundlePath(bundle.getRootPath()))) {
                return bundle;
            }
        }
        return null;
    }

    private void validateTmResource(
            BundleResource tmResource,
            String namespace,
            boolean includeStackTrace,
            List<ModelValidateIssue> errors
    ) {
        String file = relativePath(tmResource);
        try {
            tableModelLoaderManager.load(extractModelName(file), namespace);
        } catch (Exception e) {
            errors.add(issue(file, "TM", e, "MODEL", includeStackTrace));
        }
    }

    private void validateQmResource(
            BundleResource qmResource,
            boolean includeStackTrace,
            List<ModelValidateIssue> errors
    ) {
        String file = relativePath(qmResource);
        try {
            queryModelLoader.loadJdbcQueryModel(qmResource);
        } catch (Exception e) {
            errors.add(issue(file, "QM", e, "MODEL", includeStackTrace));
        }
    }

    private static BundleResource[] findBundleResources(Bundle bundle, String pattern) {
        BundleResource[] resources = bundle.findBundleResources(pattern);
        return resources != null ? resources : new BundleResource[0];
    }

    private static ModelValidateIssue issue(
            String file,
            String type,
            Exception e,
            String category,
            boolean includeStackTrace
    ) {
        return new ModelValidateIssue(
                file,
                type,
                null,
                null,
                "ERROR",
                e.getClass().getSimpleName(),
                e.getMessage(),
                null,
                category,
                includeStackTrace ? stackTrace(e) : null
        );
    }

    private static ModelValidateIssue warning(String code, String message) {
        return new ModelValidateIssue(
                null,
                "BUNDLE",
                null,
                null,
                "WARNING",
                code,
                message,
                null,
                "RUNTIME",
                null
        );
    }

    private static void markCascadingErrors(
            List<ModelValidateIssue> errors,
            int fromIndex,
            Set<String> failedTmNames
    ) {
        for (int i = fromIndex; i < errors.size(); i++) {
            ModelValidateIssue issue = errors.get(i);
            if (containsAny(issue.message(), failedTmNames)) {
                errors.set(i, new ModelValidateIssue(
                        issue.file(),
                        issue.type(),
                        issue.line(),
                        issue.column(),
                        issue.severity(),
                        issue.code(),
                        issue.message(),
                        issue.suggestion(),
                        "CASCADING",
                        issue.stackTrace()
                ));
            }
        }
    }

    private static RuntimeDiagnostics diagnosticsForValidation(ModelValidateResponse response) {
        List<String> warnings = response.warnings().stream()
                .map(ModelValidateIssue::message)
                .filter(message -> message != null && !message.isBlank())
                .toList();
        return new RuntimeDiagnostics(null, null, warnings, Map.of("validation", response));
    }

    private static RuntimeDiagnostics diagnosticsForRefresh(ModelRefreshResponse response) {
        return new RuntimeDiagnostics(null, null, response.warnings(), Map.of("refresh", response));
    }

    private static String firstValidationMessage(ModelValidateResponse response) {
        if (response.errors() != null && !response.errors().isEmpty()) {
            String message = response.errors().get(0).message();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return "Model validation failed.";
    }

    private static String firstRefreshFailureMessage(ModelRefreshResponse response) {
        if (response.failures() != null && !response.failures().isEmpty()) {
            ModelRefreshFailure failure = response.failures().get(0);
            if (failure.message() != null && !failure.message().isBlank()) {
                return failure.message();
            }
        }
        return "Model refresh failed.";
    }

    private static String validationBundleName(String namespace) {
        String normalized = blankToNull(namespace);
        return normalized != null ? "runtime-validation-" + normalized : "runtime-validation";
    }

    private static String normalizeNamespace(String namespace) {
        String normalized = blankToNull(namespace);
        return normalized != null ? normalized : "";
    }

    private static String normalizeBundlePath(String path) {
        String normalized = blankToNull(path);
        if (normalized == null) {
            return "";
        }
        try {
            return Paths.get(normalized).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException e) {
            return trimTrailingSlashes(normalized.replace('\\', '/'));
        }
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String relativePath(BundleResource resource) {
        try {
            String filename = resource.getResource().getFilename();
            if (filename != null && !filename.isBlank()) {
                return filename;
            }
        } catch (Exception ignored) {
        }
        try {
            File file = resource.getFile();
            String rootPath = resource.getBundle().getRootPath();
            if (rootPath != null && file != null) {
                Path root = Paths.get(rootPath);
                Path filePath = file.toPath();
                if (filePath.startsWith(root)) {
                    return root.relativize(filePath).toString().replace('\\', '/');
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String extractModelName(String fileName) {
        int lastSlash = fileName.lastIndexOf('/');
        int lastBackslash = fileName.lastIndexOf('\\');
        int lastSeparator = Math.max(lastSlash, lastBackslash);
        String name = lastSeparator >= 0 ? fileName.substring(lastSeparator + 1) : fileName;
        if (name.endsWith(".tm") || name.endsWith(".qm")) {
            return name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static boolean containsAny(String message, Set<String> candidates) {
        if (message == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (message.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String stackTrace(Exception e) {
        StringBuilder builder = new StringBuilder();
        builder.append(e.getClass().getName()).append(": ").append(e.getMessage()).append('\n');
        for (StackTraceElement element : e.getStackTrace()) {
            if (builder.length() > 1000) {
                break;
            }
            builder.append("  at ").append(element).append('\n');
        }
        return builder.toString();
    }

    private static boolean isModelMissing(String model, SemanticMetadataResponse metadata) {
        Map<String, Object> data = metadata.getData();
        if (data == null) {
            return false;
        }
        Object models = data.get("models");
        if (models instanceof Map<?, ?> modelMap) {
            return !modelMap.containsKey(model);
        }
        return false;
    }

    private static String stringOr(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> List<T> emptyToNull(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private static boolean booleanOr(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> optionalStringList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = blankToNull(stringValue(item));
            if (text != null) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
