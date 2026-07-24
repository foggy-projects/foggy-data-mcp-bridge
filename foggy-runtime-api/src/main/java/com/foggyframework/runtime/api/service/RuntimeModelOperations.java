package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionBlockedException;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshDiagnostic;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshException;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshRequest;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshResult;
import com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshTrigger;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.validation.DefaultDetachedModelValidationFactory;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationSession;
import com.foggyframework.runtime.api.dto.DatasourceBindingGenerationSummary;
import com.foggyframework.runtime.api.dto.ModelDescribeRequest;
import com.foggyframework.runtime.api.dto.ModelDescribeResponse;
import com.foggyframework.runtime.api.dto.ModelRefreshRequest;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.ModelValidateIssue;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.ModelValidateResponse;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleErrorCode;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureContext;
import com.foggyframework.runtime.api.dto.RuntimeLifecycleFailureDiagnostic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
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
import java.util.TreeMap;
import java.util.TreeSet;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeModelOperations {

    private final SemanticModelCatalogService catalogService;
    private final SemanticServiceV3 semanticServiceV3;
    private final DetachedModelValidationFactory detachedModelValidationFactory;
    private final DatasetProperties datasetProperties;
    private final CatalogSnapshotStore catalogSnapshotStore;
    private final CatalogRefreshCoordinator catalogRefreshCoordinator;

    @Deprecated(since = "9.3.5", forRemoval = false)
    public RuntimeModelOperations(
            SemanticModelCatalogService catalogService,
            SemanticServiceV3 semanticServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this(
                catalogService,
                semanticServiceV3,
                systemBundlesContext,
                queryModelLoader,
                tableModelLoaderManager,
                datasetPropertiesProvider,
                null,
                null
        );
    }

    @Deprecated(since = "9.3.5", forRemoval = false)
    public RuntimeModelOperations(
            SemanticModelCatalogService catalogService,
            SemanticServiceV3 semanticServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider
    ) {
        this(
                catalogService,
                semanticServiceV3,
                systemBundlesContext,
                queryModelLoader,
                tableModelLoaderManager,
                datasetPropertiesProvider,
                catalogSnapshotStoreProvider,
                null
        );
    }

    @Deprecated(since = "9.3.5", forRemoval = false)
    public RuntimeModelOperations(
            SemanticModelCatalogService catalogService,
            SemanticServiceV3 semanticServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            TableModelLoaderManager tableModelLoaderManager,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider,
            ObjectProvider<CatalogRefreshCoordinator> catalogRefreshCoordinatorProvider
    ) {
        this(
                catalogService,
                semanticServiceV3,
                new DefaultDetachedModelValidationFactory(
                        systemBundlesContext,
                        tableModelLoaderManager,
                        queryModelLoader),
                datasetPropertiesProvider,
                catalogSnapshotStoreProvider,
                catalogRefreshCoordinatorProvider
        );
    }

    @Autowired
    public RuntimeModelOperations(
            SemanticModelCatalogService catalogService,
            SemanticServiceV3 semanticServiceV3,
            DetachedModelValidationFactory detachedModelValidationFactory,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider,
            ObjectProvider<CatalogRefreshCoordinator> catalogRefreshCoordinatorProvider
    ) {
        this.catalogService = catalogService;
        this.semanticServiceV3 = semanticServiceV3;
        this.detachedModelValidationFactory = detachedModelValidationFactory;
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
        this.catalogSnapshotStore = catalogSnapshotStoreProvider == null
                ? null
                : catalogSnapshotStoreProvider.getIfAvailable();
        this.catalogRefreshCoordinator = catalogRefreshCoordinatorProvider == null
                ? null
                : catalogRefreshCoordinatorProvider.getIfAvailable();
    }

    public Map<String, Object> listModels(Map<String, String> query, String namespace) {
        Map<String, Object> options = new LinkedHashMap<>(query);
        String bodyNamespace = stringValue(options.remove("namespace"));
        return catalogService.buildCatalogResponse(
                options,
                resolveNamespace(namespace, bodyNamespace),
                null
        );
    }

    public ModelDescribeResponse describeModel(
            String model,
            ModelDescribeRequest request,
            String namespace
    ) {
        String normalizedModel = blankToNull(model);
        if (normalizedModel == null) {
            throw failure("INVALID_REQUEST", "models.describe", "Missing required path variable: model",
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
            throw failure("MODEL_NOT_FOUND", "models.describe", "QM model was not found.",
                    normalizedModel, "Refresh or register the QM model, then retry.", false);
        }

        return new ModelDescribeResponse(
                metadata.getFormat(),
                metadata.getContent(),
                metadata.getData()
        );
    }

    public ModelValidateResponse validateModels(
            ModelValidateRequest request,
            String namespace
    ) {
        String path = blankToNull(request != null ? request.path() : null);
        if (path == null) {
            throw failure("INVALID_REQUEST", "models.validate", "Missing required body field: path",
                    null, "Provide a directory path containing TM/QM files.", false);
        }

        File pathFile = new File(path);
        if (!pathFile.exists() || !pathFile.isDirectory()) {
            throw failure("INVALID_REQUEST", "models.validate",
                    "Path must be an existing directory.",
                    null, "Provide a directory path containing TM/QM files.", false);
        }

        String effectiveNamespace = resolveNamespace(
                namespace, request != null ? request.namespace() : null);
        CatalogObservation before;
        try {
            before = observeCatalog(effectiveNamespace);
        } catch (RuntimeException observationFailure) {
            throw validationFailure(
                    effectiveNamespace,
                    CatalogObservation.absent(),
                    null,
                    "Detached model validation could not capture the live catalog state.");
        }
        ModelValidateResponse response;
        try {
            response = validateModelDirectory(
                    path,
                    effectiveNamespace,
                    booleanOr(request != null ? request.includeStackTrace() : null, false)
            );
        } catch (Exception e) {
            throw validationFailure(
                    effectiveNamespace,
                    before,
                    null,
                    "Detached model validation failed.");
        }

        if (!response.valid()) {
            throw validationFailure(
                    effectiveNamespace,
                    before,
                    response,
                    "Detached model validation rejected the candidate.");
        }

        return response;
    }

    public ModelRefreshResponse refreshModels(
            ModelRefreshRequest request,
            String namespace
    ) {
        String effectiveNamespace = resolveNamespace(
                namespace, request != null ? request.namespace() : null);
        List<String> requestedModels = dedupe(request != null ? request.models() : null);
        CatalogRefreshRequest refreshRequest = requestedModels.isEmpty()
                ? CatalogRefreshRequest.namespace(
                effectiveNamespace, CatalogRefreshTrigger.RUNTIME_API)
                : CatalogRefreshRequest.models(
                effectiveNamespace,
                requestedModels.stream()
                        .map(RuntimeModelOperations::runtimeModelKey)
                        .toList(),
                CatalogRefreshTrigger.RUNTIME_API);
        CatalogObservation before;
        try {
            before = observeCatalog(effectiveNamespace);
        } catch (RuntimeException observationFailure) {
            throw refreshFailure(
                    refreshRequest,
                    CatalogObservation.absent(),
                    RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                    "Catalog refresh could not capture the live catalog state.",
                    List.of());
        }

        if (catalogRefreshCoordinator == null) {
            throw refreshFailure(
                    refreshRequest,
                    before,
                    RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED,
                    "Catalog refresh authority is unavailable.",
                    List.of());
        }

        CatalogRefreshResult result;
        try {
            result = catalogRefreshCoordinator.refresh(refreshRequest);
        } catch (CatalogRefreshException failure) {
            CatalogObservation failureBefore = failure.beforeIdentity() == null
                    ? before
                    : new CatalogObservation(
                    failure.beforeIdentity().generation().value(),
                    failure.beforeIdentity().sourceRevision().value(),
                    runtimeCatalogState(failure.catalogState()));
            throw refreshFailure(refreshRequest, failureBefore,
                    lifecycleCode(failure.code()),
                    "Catalog refresh failed without publication.",
                    failure.diagnostics());
        } catch (RuntimeException failure) {
            throw refreshFailure(refreshRequest, before,
                    lifecycleCode(failure),
                    "Catalog refresh failed without publication.",
                    List.of());
        }

        List<String> refreshedModels = result.refreshedModels().stream()
                .map(CatalogModelKey::canonicalName)
                .distinct()
                .sorted()
                .toList();
        List<DatasourceBindingGenerationSummary> bindings =
                bindingSummaries(result.affectedBindings());
        String scope = result.scope().name().toLowerCase(java.util.Locale.ROOT);
        return new ModelRefreshResponse(
                result.namespace(),
                scope,
                List.of(),
                refreshedModels,
                result.refreshedCount(),
                0,
                List.of(),
                List.of(),
                result.beforeIdentity() == null
                        ? null
                        : result.beforeIdentity().generation().value(),
                result.afterIdentity().generation().value(),
                result.sourceRevision().value(),
                bindings,
                result.refreshedCount(),
                result.preservedCount(),
                result.durationMs(),
                runtimeCatalogState(result.catalogState())
        );
    }

    private RuntimeModelOperationException failure(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return failure(code, phase, message, model, suggestedNextAction, safeToAutoRepair, RuntimeDiagnostics.empty());
    }

    private RuntimeModelOperationException failure(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            RuntimeDiagnostics diagnostics
    ) {
        return new RuntimeModelOperationException(
                code,
                phase,
                message,
                model,
                suggestedNextAction,
                safeToAutoRepair,
                diagnostics
        );
    }

    private RuntimeModelOperationException refreshFailure(
            CatalogRefreshRequest request,
            CatalogObservation before,
            RuntimeLifecycleErrorCode lifecycleCode,
            String message,
            List<CatalogRefreshDiagnostic> coreDiagnostics
    ) {
        RuntimeCatalogState failureState = observeCatalogStateSafely(
                request.namespace(), before.state());
        List<String> failedTargets = request.targets().stream()
                .map(CatalogModelKey::canonicalName)
                .toList();
        String primaryTarget = failedTargets.isEmpty()
                ? null
                : failedTargets.get(0);
        String diagnosticCode = coreDiagnostics == null
                || coreDiagnostics.isEmpty()
                ? lifecycleCode.name()
                : coreDiagnostics.get(0).code();
        RuntimeLifecycleFailureDiagnostic diagnostic =
                new RuntimeLifecycleFailureDiagnostic(
                        primaryTarget,
                        "refresh",
                        "Catalog refresh published no candidate ("
                                + diagnosticCode + ").",
                        "Fix the requested model or binding and retry the atomic refresh.");
        RuntimeLifecycleFailureContext lifecycle =
                new RuntimeLifecycleFailureContext(
                        request.namespace(),
                        before.catalogGeneration(),
                        null,
                        failureSourceRevision(request.namespace(), before),
                        failureState,
                        bindingSummariesForNamespace(request.namespace()),
                        failedTargets,
                        List.of(diagnostic));
        RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(
                null,
                null,
                List.of("Catalog refresh published no candidate."),
                refreshFailureAttributes(
                        request, failedTargets, lifecycleCode, diagnosticCode));
        return new RuntimeModelOperationException(
                "MODEL_REFRESH_FAILED",
                "models.refresh",
                message,
                primaryTarget,
                "Fix the requested model or binding and retry the atomic refresh.",
                false,
                diagnostics,
                lifecycleCode,
                lifecycle);
    }

    private RuntimeModelOperationException validationFailure(
            String namespace,
            CatalogObservation before,
            ModelValidateResponse response,
            String message
    ) {
        List<ModelValidateIssue> safeIssues = response == null
                ? List.of()
                : response.errors();
        TreeSet<String> failedTargets = new TreeSet<>();
        List<RuntimeLifecycleFailureDiagnostic> lifecycleDiagnostics =
                new ArrayList<>();
        for (ModelValidateIssue issue : safeIssues) {
            String target = issue == null
                    ? null
                    : extractModelName(stringOr(issue.file(), "unknown"));
            if (target != null && !"unknown".equals(target)) {
                failedTargets.add(target);
            }
            String type = issue == null ? "model" : stringOr(issue.type(), "model");
            String code = issue == null ? "VALIDATION_FAILED" : stringOr(
                    issue.code(), "VALIDATION_FAILED");
            lifecycleDiagnostics.add(new RuntimeLifecycleFailureDiagnostic(
                    target,
                    "validate." + type.toLowerCase(java.util.Locale.ROOT),
                    "Detached model validation failed (" + code + ").",
                    "Fix the model definition and retry validation."));
        }
        if (lifecycleDiagnostics.isEmpty()) {
            lifecycleDiagnostics.add(new RuntimeLifecycleFailureDiagnostic(
                    null,
                    "validate",
                    "Detached model validation failed.",
                    "Fix the model definition and retry validation."));
        }
        CatalogObservation current = observeCatalogSafely(namespace);
        RuntimeLifecycleFailureContext lifecycle =
                new RuntimeLifecycleFailureContext(
                        namespace,
                        before.catalogGeneration(),
                        null,
                        before.sourceRevision(),
                        current.state(),
                        bindingSummariesForNamespace(namespace),
                        List.copyOf(failedTargets),
                        lifecycleDiagnostics);
        RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(
                null,
                null,
                List.of("Detached model validation rejected the candidate."),
                validationFailureAttributes(
                        namespace, response, safeIssues));
        return new RuntimeModelOperationException(
                "MODEL_VALIDATE_FAILED",
                "models.validate",
                message,
                failedTargets.isEmpty() ? null : failedTargets.first(),
                "Fix the model definition and retry validation.",
                false,
                diagnostics,
                RuntimeLifecycleErrorCode.CATALOG_VALIDATION_FAILED,
                lifecycle);
    }

    private static Map<String, Object> refreshFailureAttributes(
            CatalogRefreshRequest request,
            List<String> failedTargets,
            RuntimeLifecycleErrorCode lifecycleCode,
            String diagnosticCode
    ) {
        List<Map<String, Object>> failures = failedTargets.stream()
                .map(target -> {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("model", target);
                    failure.put("message", "Catalog refresh published no candidate ("
                            + diagnosticCode + ").");
                    return java.util.Collections.unmodifiableMap(failure);
                })
                .toList();
        Map<String, Object> refresh = new LinkedHashMap<>();
        refresh.put("namespace", request.namespace());
        refresh.put("scope", request.scope().name().toLowerCase(
                java.util.Locale.ROOT));
        refresh.put("clearedCaches", List.of());
        refresh.put("refreshedModels", List.of());
        refresh.put("loadedCount", 0);
        refresh.put("failedCount", failures.size());
        refresh.put("failures", failures);
        refresh.put("warnings", List.of());

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("lifecycleCode", lifecycleCode.name());
        attributes.put("refresh",
                java.util.Collections.unmodifiableMap(refresh));
        return java.util.Collections.unmodifiableMap(attributes);
    }

    private static Map<String, Object> validationFailureAttributes(
            String namespace,
            ModelValidateResponse response,
            List<ModelValidateIssue> issues
    ) {
        List<Map<String, Object>> errors = issues.stream()
                .map(RuntimeModelOperations::validationIssueProjection)
                .toList();
        List<Map<String, Object>> warnings = response == null
                ? List.of()
                : response.warnings().stream()
                .map(RuntimeModelOperations::validationIssueProjection)
                .toList();

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", false);
        validation.put("namespace", response == null
                ? namespace
                : response.namespace());
        validation.put("path", response == null ? null : response.path());
        validation.put("totalFiles", response == null
                ? issues.size()
                : response.totalFiles());
        validation.put("validFiles", response == null
                ? 0
                : response.validFiles());
        validation.put("invalidFiles", response == null
                ? issues.size()
                : response.invalidFiles());
        validation.put("cascadingErrors", response == null
                ? 0
                : response.cascadingErrors());
        validation.put("durationMs", response == null
                ? null
                : response.durationMs());
        validation.put("errors", errors);
        validation.put("warnings", warnings);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("invalidFiles", issues.size());
        attributes.put("validation",
                java.util.Collections.unmodifiableMap(validation));
        return java.util.Collections.unmodifiableMap(attributes);
    }

    private static Map<String, Object> validationIssueProjection(
            ModelValidateIssue issue
    ) {
        Map<String, Object> projection = new LinkedHashMap<>();
        if (issue == null) {
            return java.util.Collections.unmodifiableMap(projection);
        }
        projection.put("file", issue.file());
        projection.put("type", issue.type());
        projection.put("line", issue.line());
        projection.put("column", issue.column());
        projection.put("severity", issue.severity());
        projection.put("code", issue.code());
        projection.put("message", issue.message());
        projection.put("suggestion", issue.suggestion());
        projection.put("category", issue.category());
        projection.put("stackTrace", issue.stackTrace());
        return java.util.Collections.unmodifiableMap(projection);
    }

    private String failureSourceRevision(
            String namespace,
            CatalogObservation before
    ) {
        if (before.sourceRevision() != null) {
            return before.sourceRevision();
        }
        if (catalogSnapshotStore == null) {
            return null;
        }
        try {
            return catalogSnapshotStore.currentSourceRevision(namespace).value();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<DatasourceBindingGenerationSummary> bindingSummariesForNamespace(
            String namespace
    ) {
        if (catalogSnapshotStore == null) {
            return List.of();
        }
        CatalogSnapshot snapshot;
        try {
            snapshot = catalogSnapshotStore.current(namespace).orElse(null);
        } catch (RuntimeException ignored) {
            return List.of();
        }
        if (snapshot == null) {
            return List.of();
        }
        TreeMap<String, DatasourceBindingIdentity> bindings = new TreeMap<>();
        for (ModelProvenance provenance : snapshot.provenance().values()) {
            provenance.datasourceBindings().forEach(bindings::putIfAbsent);
        }
        return bindingSummaries(bindings.values());
    }

    private static List<DatasourceBindingGenerationSummary> bindingSummaries(
            java.util.Collection<DatasourceBindingIdentity> bindings
    ) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(identity -> new DatasourceBindingGenerationSummary(
                        identity.bindingKey(),
                        identity.backendId(),
                        identity.generation().value()))
                .distinct()
                .sorted(java.util.Comparator
                        .comparing(DatasourceBindingGenerationSummary::bindingKey)
                        .thenComparing(DatasourceBindingGenerationSummary::backendId))
                .toList();
    }

    private static CatalogModelKey runtimeModelKey(String model) {
        return model.contains("#")
                ? CatalogModelKey.syntheticQuery(model)
                : CatalogModelKey.query(model);
    }

    private static RuntimeLifecycleErrorCode lifecycleCode(String code) {
        return switch (code) {
            case "SOURCE_REVISION_STALE" ->
                    RuntimeLifecycleErrorCode.SOURCE_REVISION_STALE;
            case "CATALOG_GENERATION_STALE" ->
                    RuntimeLifecycleErrorCode.CATALOG_CANDIDATE_STALE;
            case "DATASOURCE_BINDING_NOT_CURRENT" ->
                    RuntimeLifecycleErrorCode.DATASOURCE_BINDING_NOT_CURRENT;
            case "REFRESH_SCOPE_UNKNOWN" ->
                    RuntimeLifecycleErrorCode.REFRESH_SCOPE_UNKNOWN;
            default -> RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED;
        };
    }

    static RuntimeLifecycleErrorCode lifecycleCode(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String type = current.getClass().getSimpleName();
            if ("ModelBuildCyclicDependencyException".equals(type)) {
                return RuntimeLifecycleErrorCode.SINGLE_FLIGHT_CYCLIC_DEPENDENCY;
            }
            if ("StaleDatasourceBindingException".equals(type)) {
                return RuntimeLifecycleErrorCode.DATASOURCE_BINDING_NOT_CURRENT;
            }
            if ("StaleCatalogBuildException".equals(type)) {
                return RuntimeLifecycleErrorCode.CATALOG_CANDIDATE_STALE;
            }
            if (current instanceof CatalogAdmissionBlockedException blocked) {
                return lifecycleCode(blocked.code());
            }
            current = current.getCause();
        }
        return RuntimeLifecycleErrorCode.CATALOG_BUILD_FAILED;
    }

    private String resolveNamespace(String headerNamespace, String bodyNamespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
    }

    private CatalogObservation observeCatalog(String namespace) {
        if (catalogSnapshotStore == null) {
            return CatalogObservation.absent();
        }

        CatalogAdmissionState admissionState = catalogSnapshotStore.admissionState(namespace);
        CatalogSnapshot snapshot = catalogSnapshotStore.current(namespace).orElse(null);
        CatalogAdmissionState confirmedState = catalogSnapshotStore.admissionState(namespace);
        if (admissionState != confirmedState) {
            admissionState = confirmedState;
            snapshot = catalogSnapshotStore.current(namespace).orElse(null);
        }

        String generation = snapshot == null
                ? null
                : snapshot.identity().generation().value();
        String sourceRevision = snapshot == null
                ? null
                : snapshot.identity().sourceRevision().value();
        return new CatalogObservation(
                generation,
                sourceRevision,
                runtimeCatalogState(admissionState)
        );
    }

    private CatalogObservation observeCatalogSafely(String namespace) {
        try {
            return observeCatalog(namespace);
        } catch (RuntimeException ignored) {
            return CatalogObservation.absent();
        }
    }

    private RuntimeCatalogState observeCatalogStateSafely(
            String namespace,
            RuntimeCatalogState fallback
    ) {
        try {
            return observeCatalog(namespace).state();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static RuntimeCatalogState runtimeCatalogState(
            CatalogAdmissionState admissionState
    ) {
        return switch (admissionState) {
            case ACTIVE -> RuntimeCatalogState.ACTIVE;
            case ACTIVE_OLD_PRESERVED -> RuntimeCatalogState.ACTIVE_OLD_PRESERVED;
            case STALE_ADMISSION_BLOCKED -> RuntimeCatalogState.STALE_ADMISSION_BLOCKED;
            case ABSENT -> RuntimeCatalogState.ABSENT;
        };
    }

    private ModelValidateResponse validateModelDirectory(
            String path,
            String namespace,
            boolean includeStackTrace
    ) {
        Instant startedAt = Instant.now();
        CatalogObservation before = observeCatalog(namespace);
        try (DetachedModelValidationSession validator = detachedModelValidationFactory.open(
                validationBundleName(namespace),
                namespace,
                path
        )) {
            Bundle bundle = validator.sourceBundle();
            List<ModelValidateIssue> errors = new ArrayList<>();
            Set<String> failedTmNames = new HashSet<>();
            int totalFiles = 0;

            BundleResource[] tmResources = findBundleResources(bundle, "**/*.tm");
            totalFiles += tmResources.length;
            for (BundleResource tmResource : tmResources) {
                int beforeSize = errors.size();
                validateTmResource(
                        validator,
                        tmResource,
                        namespace,
                        includeStackTrace,
                        errors
                );
                if (errors.size() > beforeSize) {
                    failedTmNames.add(extractModelName(relativePath(tmResource)));
                }
            }

            BundleResource[] qmResources = findBundleResources(bundle, "**/*.qm");
            totalFiles += qmResources.length;
            for (BundleResource qmResource : qmResources) {
                int beforeSize = errors.size();
                validateQmResource(validator, qmResource, includeStackTrace, errors);
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
                    List.of(),
                    before.catalogGeneration(),
                    before.catalogGeneration(),
                    before.sourceRevision(),
                    List.of(),
                    before.state()
            );
        }
    }

    private void validateTmResource(
            DetachedModelValidationSession validator,
            BundleResource tmResource,
            String namespace,
            boolean includeStackTrace,
            List<ModelValidateIssue> errors
    ) {
        String file = relativePath(tmResource);
        try {
            validator.validateTableModel(tmResource, namespace);
        } catch (Exception e) {
            errors.add(issue(file, "TM", e, "MODEL", includeStackTrace));
        }
    }

    private void validateQmResource(
            DetachedModelValidationSession validator,
            BundleResource qmResource,
            boolean includeStackTrace,
            List<ModelValidateIssue> errors
    ) {
        String file = relativePath(qmResource);
        try {
            validator.validateQueryModel(qmResource);
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

    private static String validationBundleName(String namespace) {
        String normalized = blankToNull(namespace);
        return normalized != null ? "runtime-validation-" + normalized : "runtime-validation";
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

    private record CatalogObservation(
            String catalogGeneration,
            String sourceRevision,
            RuntimeCatalogState state
    ) {
        private static CatalogObservation absent() {
            return new CatalogObservation(null, null, RuntimeCatalogState.ABSENT);
        }
    }
}
