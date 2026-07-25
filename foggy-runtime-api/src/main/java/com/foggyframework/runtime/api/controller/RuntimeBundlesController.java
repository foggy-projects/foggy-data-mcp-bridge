package com.foggyframework.runtime.api.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.dto.BundleListResponse;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimeBundleAdmissionException;
import com.foggyframework.runtime.api.service.RuntimeBundleAdmissionService;
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
    private final RuntimeBundleAdmissionService admissionService;

    public RuntimeBundlesController(
            RuntimeApiResponseFactory responses,
            SystemBundlesContext systemBundlesContext,
            RuntimeBundleRegistryService registryService,
            RuntimeBundleAdmissionService admissionService
    ) {
        this.responses = responses;
        this.systemBundlesContext = systemBundlesContext;
        this.registryService = registryService;
        this.admissionService = admissionService;
    }

    @GetMapping(RuntimeApiRoutes.V1.BUNDLES)
    public RuntimeEnvelope<BundleListResponse> listBundles() {
        Map<String, RuntimeBundleRecord> managedRecords = new LinkedHashMap<>();
        for (RuntimeBundleRecord record : registryService.listRecords()) {
            managedRecords.put(record.name(), record);
        }

        Map<String, BundleInfo> bundles = new LinkedHashMap<>();
        for (BundleDefinition definition : systemBundlesContext.listExternalBundles()) {
            BundleInfo info = infoFromDefinition(definition, managedRecords.containsKey(definition.getName()), "active", null);
            bundles.put(info.name(), info);
        }

        for (RuntimeBundleRecord record : managedRecords.values()) {
            if (!bundles.containsKey(record.name())) {
                bundles.put(record.name(), infoFromRecord(record, "inactive", "Runtime-managed bundle is not currently registered."));
            }
        }

        return responses.ok(new BundleListResponse(List.copyOf(bundles.values()), List.of()));
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

        try {
            registryService.remove(normalizedName);
        } catch (RuntimeException failure) {
            return fail("BUNDLE_REGISTRY_PERSIST_FAILED", "bundles.remove",
                    "Bundle registry update failed: " + normalizedName,
                    "Restore registry storage availability and retry.", false);
        }
        boolean removed = true;
        if (systemBundlesContext.containBundle(normalizedName)) {
            removed = systemBundlesContext.removeBundle(normalizedName);
        }
        if (!removed) {
            if (!restoreRegistryRecord(record)) {
                return fail("BUNDLE_ROLLBACK_FAILED", "bundles.remove",
                        "Bundle removal and registry rollback both failed: "
                                + normalizedName,
                        "Stop mutations, restore registry storage, and restart the service.",
                        false);
            }
            return fail("BUNDLE_REMOVE_FAILED", "bundles.remove", "Bundle removal failed: " + normalizedName,
                    "Check whether the bundle is an external runtime-managed bundle.", false);
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
                && !existingRecord.namespace().equals(namespace)) {
            return fail("BUNDLE_NAMESPACE_CHANGE_UNSUPPORTED",
                    update ? "bundles.update" : "bundles.add",
                    "Replacing a bundle cannot change its namespace: " + name,
                    "Remove the bundle and register it in the new namespace explicitly.", false);
        }
        boolean watch = booleanOr(request != null ? request.watch() : null, existingRecord != null && existingRecord.watch());
        boolean enabled = booleanOr(request != null ? request.enabled() : null, true);
        try {
            if (enabled && booleanOr(request != null ? request.validate() : null, true)) {
                admissionService.validate(
                        name,
                        namespace,
                        path,
                        existsInRuntime ? name : null);
            }
        } catch (RuntimeBundleAdmissionException failure) {
            return fail(failure.code(), update ? "bundles.update" : "bundles.add",
                    failure.getMessage(),
                    "Fix the candidate bundle and retry.", false);
        }

        RuntimeBundleRecord candidateRecord =
                registryService.newRecord(name, namespace, path, watch, enabled);
        RuntimeBundleRecord record;
        try {
            record = registryService.save(candidateRecord);
        } catch (RuntimeException failure) {
            return fail("BUNDLE_REGISTRY_PERSIST_FAILED",
                    update ? "bundles.update" : "bundles.add",
                    "Bundle registry update failed: " + name,
                    "Restore registry storage availability and retry.", false);
        }

        boolean registered;
        if (!enabled) {
            registered = !existsInRuntime
                    || systemBundlesContext.removeBundle(name);
        } else {
            registered = existsInRuntime
                    ? systemBundlesContext.replaceExternalBundle(
                    name, namespace, path, watch)
                    : systemBundlesContext.addExternalBundle(
                    name, namespace, path, watch);
        }
        if (!registered) {
            if (!rollbackRegistryRecord(name, existingRecord)) {
                return fail("BUNDLE_ROLLBACK_FAILED",
                        update ? "bundles.update" : "bundles.add",
                        "Bundle source mutation and registry rollback both failed: "
                                + name,
                        "Stop mutations, restore registry storage, and restart the service.",
                        false);
            }
            String failureCode = !enabled
                    ? "BUNDLE_DISABLE_FAILED"
                    : existsInRuntime
                    ? "BUNDLE_REPLACE_FAILED"
                    : "BUNDLE_ADD_FAILED";
            return fail(failureCode,
                    update ? "bundles.update" : "bundles.add",
                    "Bundle registration failed without publication: " + name,
                    "Check validation diagnostics and retry.", false);
        }

        List<String> warnings = new ArrayList<>();
        if (booleanOr(request != null ? request.refresh() : null, false)) {
            warnings.add("Bundle lifecycle already published an atomic namespace refresh; refresh=true is redundant.");
        }
        BundleInfo info = infoFromRecord(record, enabled ? "active" : "disabled", null);
        return responses.ok(new BundleMutationResponse(info, warnings));
    }

    private boolean rollbackRegistryRecord(
            String name,
            RuntimeBundleRecord previous
    ) {
        try {
            if (previous == null) {
                registryService.remove(name);
            } else {
                registryService.save(previous);
            }
            return true;
        } catch (RuntimeException rollbackFailure) {
            return false;
        }
    }

    private boolean restoreRegistryRecord(RuntimeBundleRecord record) {
        try {
            registryService.save(record);
            return true;
        } catch (RuntimeException rollbackFailure) {
            return false;
        }
    }

    private BundleInfo infoFromDefinition(BundleDefinition definition, boolean managed, String status, String message) {
        String path = null;
        Boolean watch = null;
        if (definition instanceof ExternalBundleDefinition external) {
            path = external.getPath();
            watch = external.isWatch();
        }
        return new BundleInfo(
                definition.getName(),
                definition.getNamespace(),
                path,
                watch,
                true,
                managed ? "runtime-registry" : "config",
                managed,
                managed,
                managed,
                status,
                message
        );
    }

    private BundleInfo infoFromRecord(RuntimeBundleRecord record, String status, String message) {
        return new BundleInfo(
                record.name(),
                record.namespace(),
                record.path(),
                record.watch(),
                record.enabled(),
                "runtime-registry",
                true,
                true,
                true,
                status,
                message
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
}
