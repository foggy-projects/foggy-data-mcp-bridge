package com.foggyframework.runtime.api.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalBundleResourceSupport;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.dto.BundleListResponse;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringStorePathPolicy;
import com.foggyframework.runtime.api.service.RuntimeBundleModelConflictDetector;
import com.foggyframework.runtime.api.service.RuntimeBundleModelConflictDetector.ModelNameConflict;
import com.foggyframework.runtime.api.service.RuntimeBundleInventoryService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeBundlesController {

    private final RuntimeApiResponseFactory responses;
    private final SystemBundlesContext systemBundlesContext;
    private final RuntimeBundleRegistryService registryService;
    private final RuntimeBundleModelConflictDetector modelConflictDetector;
    private final RuntimeBundleInventoryService inventoryService;
    private final RuntimeAuthoringStorePathPolicy authoringPathPolicy;

    public RuntimeBundlesController(
            RuntimeApiResponseFactory responses,
            SystemBundlesContext systemBundlesContext,
            RuntimeBundleRegistryService registryService,
            RuntimeBundleModelConflictDetector modelConflictDetector,
            RuntimeAuthoringStorePathPolicy authoringPathPolicy
    ) {
        this.responses = responses;
        this.systemBundlesContext = systemBundlesContext;
        this.registryService = registryService;
        this.modelConflictDetector = modelConflictDetector;
        this.authoringPathPolicy = authoringPathPolicy;
        this.inventoryService = new RuntimeBundleInventoryService(
                systemBundlesContext, registryService);
    }

    @GetMapping(RuntimeApiRoutes.V1.BUNDLES)
    public RuntimeEnvelope<BundleListResponse> listBundles() {
        return responses.ok(new BundleListResponse(
                inventoryService.list(), List.of()));
    }

    @PostMapping(RuntimeApiRoutes.V1.BUNDLES)
    public RuntimeEnvelope<BundleMutationResponse> addBundle(
            @RequestBody(required = false) BundleRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return upsertBundle(null, request, namespace, false);
    }

    @PutMapping(RuntimeApiRoutes.V1.BUNDLE_BY_NAME)
    public RuntimeEnvelope<BundleMutationResponse> updateBundle(
            @PathVariable String name,
            @RequestBody(required = false) BundleRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return upsertBundle(name, request, namespace, true);
    }

    @DeleteMapping(RuntimeApiRoutes.V1.BUNDLE_BY_NAME)
    public RuntimeEnvelope<BundleMutationResponse> removeBundle(@PathVariable String name) {
        String normalizedName = blankToNull(name);
        if (normalizedName == null) {
            return fail("INVALID_REQUEST", "bundles.remove", "Missing required path variable: name",
                    "Provide a runtime-managed bundle name.", false);
        }
        RuntimeBundleRecord record = registryService.find(normalizedName).orElse(null);
        if (record == null) {
            return fail("BUNDLE_NOT_MANAGED", "bundles.remove", "Bundle is not managed by Runtime API: " + normalizedName,
                    "Only runtime-managed bundles can be removed through Runtime API.", false);
        }

        SourceState sourceBefore = captureSourceState(normalizedName);
        boolean removed = !sourceBefore.present()
                || systemBundlesContext.removeBundle(normalizedName);
        if (!removed) {
            return fail("BUNDLE_REMOVE_FAILED", "bundles.remove", "Bundle removal failed: " + normalizedName,
                    "Check whether the bundle is an external runtime-managed bundle.", false);
        }
        try {
            registryService.remove(normalizedName);
        } catch (RuntimeException persistenceFailure) {
            if (!restoreSourceState(normalizedName, sourceBefore)) {
                return fail("BUNDLE_ROLLBACK_FAILED", "bundles.remove",
                        "Bundle registry persistence failed and source rollback also failed: "
                                + normalizedName,
                        "Inspect runtime source and registry state before retrying.", false);
            }
            return fail("BUNDLE_REGISTRY_PERSIST_FAILED", "bundles.remove",
                    "Bundle registry persistence failed; source removal was rolled back: "
                            + normalizedName,
                    "Fix registry path permissions or storage availability, then retry.", true);
        }
        BundleInfo info = infoFromRecord(record, "removed", null);
        return responses.ok(new BundleMutationResponse(info, List.of()));
    }

    private RuntimeEnvelope<BundleMutationResponse> upsertBundle(
            String pathName,
            BundleRequest request,
            String headerNamespace,
            boolean update
    ) {
        String name = blankToNull(pathName != null ? pathName : request != null ? request.name() : null);
        if (name == null) {
            return fail("INVALID_REQUEST", update ? "bundles.update" : "bundles.add", "Missing required field: name",
                    "Provide a bundle name.", false);
        }
        String path = blankToNull(request != null ? request.path() : null);
        if (path == null) {
            return fail("INVALID_REQUEST", update ? "bundles.update" : "bundles.add", "Missing required field: path",
                    "Provide an external bundle directory path.", false);
        }
        try {
            authoringPathPolicy.assertBundleSourceDisjoint(path);
        } catch (RuntimeAuthoringStorePathPolicy.PathConflictException conflict) {
            return fail("BUNDLE_PATH_CONFLICT",
                    update ? "bundles.update" : "bundles.add",
                    "Bundle source overlaps the authoring workspace store.",
                    "Choose a Bundle source and authoring store root that are fully disjoint.",
                    false);
        }

        RuntimeBundleRecord existingRecord = registryService.find(name).orElse(null);
        boolean existsInRuntime = systemBundlesContext.containBundle(name);
        if (existsInRuntime && existingRecord == null) {
            return fail("BUNDLE_NAME_CONFLICT", update ? "bundles.update" : "bundles.add",
                    "Bundle name is already used by a configured or unmanaged bundle: " + name,
                    "Choose a different name. Runtime API cannot mutate configured bundles.", false);
        }

        boolean replace = update || booleanOr(request != null ? request.replace() : null, false);
        if (existsInRuntime && !replace) {
            return fail("BUNDLE_ALREADY_EXISTS", "bundles.add", "Runtime-managed bundle already exists: " + name,
                    "Use replace=true or bundles update.", true);
        }

        String namespace = stringOr(
                request != null ? request.namespace() : null,
                stringOr(headerNamespace, existingRecord != null ? existingRecord.namespace() : "")
        );
        if (existingRecord != null
                && !canonicalNamespace(existingRecord.namespace())
                .equals(canonicalNamespace(namespace))) {
            return fail("BUNDLE_NAMESPACE_CHANGE_UNSUPPORTED",
                    update ? "bundles.update" : "bundles.add",
                    "Runtime bundle replacement cannot change namespace: " + name,
                    "Remove and re-add the bundle under the new namespace.", false);
        }
        boolean watch = booleanOr(request != null ? request.watch() : null, existingRecord != null && existingRecord.watch());
        boolean enabled = booleanOr(request != null ? request.enabled() : null, true);
        if (enabled) {
            List<ModelNameConflict> conflicts;
            try {
                conflicts = modelConflictDetector.findConflicts(
                        name,
                        namespace,
                        path,
                        replace ? name : null
                );
            } catch (RuntimeException inspectionFailure) {
                return fail("BUNDLE_MODEL_CONFLICT_CHECK_FAILED",
                        update ? "bundles.update" : "bundles.add",
                        "Unable to verify model-name ownership before bundle registration.",
                        "Check that the candidate and active bundle resources are readable, then retry.",
                        false);
            }
            if (!conflicts.isEmpty()) {
                return fail("BUNDLE_MODEL_NAME_CONFLICT",
                        update ? "bundles.update" : "bundles.add",
                        conflictMessage(namespace, conflicts),
                        "Rename the conflicting TM/QM resources or remove the existing owning bundle.",
                        false);
            }
        }

        SourceState sourceBefore = captureSourceState(name);
        boolean sourceCommitted;
        if (enabled) {
            sourceCommitted = sourceBefore.present()
                    ? systemBundlesContext.replaceExternalBundle(
                    name, namespace, path, watch)
                    : systemBundlesContext.addExternalBundle(
                    name, namespace, path, watch);
        } else {
            sourceCommitted = !sourceBefore.present()
                    || systemBundlesContext.removeBundle(name);
        }
        if (!sourceCommitted) {
            return fail(enabled ? "BUNDLE_ADD_FAILED" : "BUNDLE_REMOVE_FAILED",
                    update ? "bundles.update" : "bundles.add",
                    "Bundle source mutation failed: " + name,
                    "Check path readability, namespace, watcher setup, and bundle state, then retry.",
                    false);
        }

        RuntimeBundleRecord record = registryService.newRecord(name, namespace, path, watch, enabled);
        try {
            record = registryService.save(record);
        } catch (RuntimeException persistenceFailure) {
            if (!restoreSourceState(name, sourceBefore)) {
                return fail("BUNDLE_ROLLBACK_FAILED",
                        update ? "bundles.update" : "bundles.add",
                        "Bundle registry persistence failed and source rollback also failed: "
                                + name,
                        "Inspect runtime source and registry state before retrying.", false);
            }
            return fail("BUNDLE_REGISTRY_PERSIST_FAILED",
                    update ? "bundles.update" : "bundles.add",
                    "Bundle registry persistence failed; source mutation was rolled back: "
                            + name,
                    "Fix registry path permissions or storage availability, then retry.", true);
        }
        List<String> warnings = new ArrayList<>();
        if (booleanOr(request != null ? request.validate() : null, false)) {
            warnings.add("validate flag accepted but Stage 1 bundle API does not run model validation yet; run models validate explicitly.");
        }
        if (booleanOr(request != null ? request.refresh() : null, false)) {
            warnings.add("refresh flag accepted but Stage 1 bundle API does not run model refresh yet; run models refresh explicitly.");
        }
        RuntimeBundleRecord savedRecord = record;
        BundleInfo info = inventoryService.list().stream()
                .filter(candidate -> name.equals(candidate.name()))
                .findFirst()
                .orElseGet(() -> infoFromRecord(
                        savedRecord, enabled ? "inactive" : "disabled", null));
        return responses.ok(new BundleMutationResponse(info, warnings));
    }

    private static String conflictMessage(
            String namespace,
            List<ModelNameConflict> conflicts
    ) {
        List<String> descriptions = new ArrayList<>(conflicts.size());
        for (ModelNameConflict conflict : conflicts) {
            String ownerDescription = conflict.existingBundleNames().isEmpty()
                    ? "duplicated within candidate bundle"
                    : "owned by bundle(s) " + String.join(", ", conflict.existingBundleNames());
            descriptions.add(conflict.type() + " " + conflict.modelName()
                    + " (" + ownerDescription + ")");
        }
        String displayNamespace = StringUtils.hasText(namespace)
                ? namespace.trim()
                : "<default>";
        return "Model-name conflict in namespace '" + displayNamespace + "': "
                + String.join("; ", descriptions);
    }

    private SourceState captureSourceState(String name) {
        if (!systemBundlesContext.containBundle(name)) {
            return SourceState.absent();
        }
        BundleDefinition definition =
                systemBundlesContext.getBundleDefinitionByName(name);
        if (!(definition instanceof ExternalBundleDefinition external)) {
            return new SourceState(true, "", null, false);
        }
        return new SourceState(
                true,
                canonicalNamespace(external.getNamespace()),
                external.getPath(),
                external.isWatch());
    }

    private boolean restoreSourceState(String name, SourceState sourceBefore) {
        try {
            boolean presentNow = systemBundlesContext.containBundle(name);
            if (!sourceBefore.present()) {
                return !presentNow || systemBundlesContext.removeBundle(name);
            }
            if (!StringUtils.hasText(sourceBefore.path())) {
                return false;
            }
            if (presentNow) {
                return systemBundlesContext.replaceExternalBundle(
                        name,
                        sourceBefore.namespace(),
                        sourceBefore.path(),
                        sourceBefore.watch());
            }
            return systemBundlesContext.addExternalBundle(
                    name,
                    sourceBefore.namespace(),
                    sourceBefore.path(),
                    sourceBefore.watch());
        } catch (RuntimeException rollbackFailure) {
            return false;
        }
    }

    private BundleInfo infoFromRecord(RuntimeBundleRecord record, String status, String message) {
        String namespace = canonicalNamespace(record.namespace());
        String sourceType = ExternalBundleResourceSupport
                .isSpringResourceLocation(record.path())
                ? "external-resource" : "external-filesystem";
        return new BundleInfo(
                record.name(),
                namespace,
                record.path(),
                record.watch(),
                record.enabled(),
                "runtime-registry",
                true,
                true,
                true,
                status,
                message,
                sourceType,
                false,
                false,
                List.of(namespace),
                RuntimeBundleInventoryService.sourceIdentity(
                        record.name(), namespace, sourceType, record.path())
        );
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return responses.fail(
                code,
                phase,
                message,
                null,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String stringOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static boolean booleanOr(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static String canonicalNamespace(String namespace) {
        return StringUtils.hasText(namespace) ? namespace.trim() : "";
    }

    private record SourceState(
            boolean present,
            String namespace,
            String path,
            boolean watch
    ) {
        private static SourceState absent() {
            return new SourceState(false, "", null, false);
        }
    }
}
