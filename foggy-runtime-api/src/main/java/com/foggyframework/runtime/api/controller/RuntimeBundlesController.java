package com.foggyframework.runtime.api.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.dto.BundleListResponse;
import com.foggyframework.runtime.api.dto.BundleMutationResponse;
import com.foggyframework.runtime.api.dto.BundleRequest;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
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
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeBundlesController {

    private final RuntimeApiResponseFactory responses;
    private final SystemBundlesContext systemBundlesContext;
    private final RuntimeBundleRegistryService registryService;

    public RuntimeBundlesController(
            RuntimeApiResponseFactory responses,
            SystemBundlesContext systemBundlesContext,
            RuntimeBundleRegistryService registryService
    ) {
        this.responses = responses;
        this.systemBundlesContext = systemBundlesContext;
        this.registryService = registryService;
    }

    @GetMapping("/bundles")
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

    @PostMapping("/bundles")
    public RuntimeEnvelope<BundleMutationResponse> addBundle(
            @RequestBody(required = false) BundleRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return upsertBundle(null, request, namespace, false);
    }

    @PutMapping("/bundles/{name}")
    public RuntimeEnvelope<BundleMutationResponse> updateBundle(
            @PathVariable String name,
            @RequestBody(required = false) BundleRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return upsertBundle(name, request, namespace, true);
    }

    @DeleteMapping("/bundles/{name}")
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

        boolean removed = true;
        if (systemBundlesContext.containBundle(normalizedName)) {
            removed = systemBundlesContext.removeBundle(normalizedName);
        }
        if (!removed) {
            return fail("BUNDLE_REMOVE_FAILED", "bundles.remove", "Bundle removal failed: " + normalizedName,
                    "Check whether the bundle is an external runtime-managed bundle.", false);
        }
        registryService.remove(normalizedName);
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
        boolean removedExisting = false;
        if (existsInRuntime) {
            if (!systemBundlesContext.removeBundle(name)) {
                return fail("BUNDLE_REMOVE_FAILED", update ? "bundles.update" : "bundles.add",
                        "Existing runtime-managed bundle could not be removed: " + name,
                        "Inspect bundle state and retry.", false);
            }
            removedExisting = true;
        }

        String namespace = stringOr(
                request != null ? request.namespace() : null,
                stringOr(headerNamespace, existingRecord != null ? existingRecord.namespace() : "")
        );
        boolean watch = booleanOr(request != null ? request.watch() : null, existingRecord != null && existingRecord.watch());
        boolean enabled = booleanOr(request != null ? request.enabled() : null, true);
        boolean registered = !enabled || systemBundlesContext.addExternalBundle(name, namespace, path, watch);
        if (!registered) {
            restoreRemovedExistingBundle(removedExisting, existingRecord);
            return fail("BUNDLE_ADD_FAILED", update ? "bundles.update" : "bundles.add",
                    "Bundle registration failed: " + name,
                    "Check path readability and bundle name, then retry.", false);
        }

        RuntimeBundleRecord record = registryService.newRecord(name, namespace, path, watch, enabled);
        record = registryService.save(record);
        List<String> warnings = new ArrayList<>();
        if (booleanOr(request != null ? request.validate() : null, false)) {
            warnings.add("validate flag accepted but Stage 1 bundle API does not run model validation yet; run models validate explicitly.");
        }
        if (booleanOr(request != null ? request.refresh() : null, false)) {
            warnings.add("refresh flag accepted but Stage 1 bundle API does not run model refresh yet; run models refresh explicitly.");
        }
        BundleInfo info = infoFromRecord(record, enabled ? "active" : "disabled", null);
        return responses.ok(new BundleMutationResponse(info, warnings));
    }

    private void restoreRemovedExistingBundle(boolean removedExisting, RuntimeBundleRecord existingRecord) {
        if (!removedExisting || existingRecord == null) {
            return;
        }
        try {
            systemBundlesContext.addExternalBundle(
                    existingRecord.name(),
                    existingRecord.namespace(),
                    existingRecord.path(),
                    existingRecord.watch()
            );
        } catch (Exception ignored) {
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
