package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates a Bundle against the complete target namespace before it can
 * become visible.
 */
@Service
public class RuntimeBundleAdmissionService {

    private final SystemBundlesContext bundlesContext;
    private final RuntimeModelOperations modelOperations;

    public RuntimeBundleAdmissionService(
            SystemBundlesContext bundlesContext,
            RuntimeModelOperations modelOperations
    ) {
        this.bundlesContext = bundlesContext;
        this.modelOperations = modelOperations;
    }

    public void validate(
            String bundleName,
            String namespace,
            String path,
            String replacedBundleName
    ) {
        try {
            modelOperations.validateModels(
                    new ModelValidateRequest(path, namespace, false, false, false),
                    namespace);
        } catch (RuntimeModelOperationException failure) {
            throw new RuntimeBundleAdmissionException(
                    "BUNDLE_VALIDATION_FAILED",
                    "Bundle model validation failed: " + bundleName);
        }

        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                bundleName, canonicalNamespace(namespace), path, false);
        ExternalFileBundle candidate = new ExternalFileBundle(bundlesContext);
        candidate.setName(bundleName);
        candidate.setBundleDefinition(definition);
        candidate.setBasePath(definition.getPath());
        candidate.setRootPath(definition.getPath());

        Map<ModelKey, String> owners = namespaceModelOwners(
                namespace, replacedBundleName);
        collectCandidateModels(candidate, owners);
    }

    private Map<ModelKey, String> namespaceModelOwners(
            String namespace,
            String replacedBundleName
    ) {
        String canonicalNamespace = canonicalNamespace(namespace);
        Map<ModelKey, String> owners = new LinkedHashMap<>();
        for (Bundle bundle : bundlesContext.getBundleList()) {
            if (bundle == null
                    || bundle.getDefinition() == null
                    || bundle.getName().equals(replacedBundleName)
                    || !canonicalNamespace.equals(canonicalNamespace(
                    bundle.getDefinition().getNamespace()))) {
                continue;
            }
            collect(bundle, "**/*.tm", "TM", owners, bundle.getName(), false);
            collect(bundle, "**/*.qm", "QM", owners, bundle.getName(), false);
        }
        return owners;
    }

    private void collectCandidateModels(
            Bundle candidate,
            Map<ModelKey, String> owners
    ) {
        collect(candidate, "**/*.tm", "TM", owners, candidate.getName(), true);
        collect(candidate, "**/*.qm", "QM", owners, candidate.getName(), true);
    }

    private void collect(
            Bundle bundle,
            String pattern,
            String kind,
            Map<ModelKey, String> owners,
            String bundleName,
            boolean rejectConflict
    ) {
        BundleResource[] resources = bundle.findBundleResources(pattern);
        if (resources == null) {
            return;
        }
        for (BundleResource resource : resources) {
            String filename = resource == null || resource.getResource() == null
                    ? null
                    : resource.getResource().getFilename();
            if (filename == null || filename.length() <= 3) {
                throw new RuntimeBundleAdmissionException(
                        "BUNDLE_SOURCE_INVALID",
                        "Bundle contains a model resource without a valid filename: "
                                + bundleName);
            }
            ModelKey key = new ModelKey(
                    kind,
                    filename.substring(0, filename.length() - 3));
            String existingOwner = owners.putIfAbsent(key, bundleName);
            if (existingOwner != null && (rejectConflict
                    || !existingOwner.equals(bundleName))) {
                throw new RuntimeBundleAdmissionException(
                        "BUNDLE_MODEL_CONFLICT",
                        "Model conflict in namespace '"
                                + canonicalNamespace(bundle.getDefinition().getNamespace())
                                + "': " + kind + " " + key.name()
                                + " is already owned by bundle " + existingOwner
                                + "; candidate bundle=" + bundleName);
            }
        }
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? "" : namespace.trim();
    }

    private record ModelKey(String kind, String name) {
    }
}
