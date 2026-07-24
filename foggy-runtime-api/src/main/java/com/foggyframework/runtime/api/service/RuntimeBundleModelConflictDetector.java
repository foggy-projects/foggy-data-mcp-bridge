package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Performs a read-only ownership check before a Runtime API bundle mutation.
 *
 * <p>Model loaders treat the canonical {@code .tm}/{@code .qm} filename as the
 * catalog model name. Runtime bundle registration must therefore reject a
 * second owner for the same kind/name slot in one namespace before the live
 * bundle registry or catalog source revision is mutated.</p>
 */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeBundleModelConflictDetector {

    private final SystemBundlesContext systemBundlesContext;

    public RuntimeBundleModelConflictDetector(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    public List<ModelNameConflict> findConflicts(
            String candidateBundleName,
            String namespace,
            String path,
            String excludedBundleName
    ) {
        String canonicalNamespace = canonicalNamespace(namespace);
        Map<ModelResourceKey, Set<String>> existingOwners = existingOwners(
                canonicalNamespace, excludedBundleName);
        Map<ModelResourceKey, Integer> candidateOccurrences = candidateOccurrences(
                candidateBundleName, canonicalNamespace, path);

        List<ModelNameConflict> conflicts = new ArrayList<>();
        for (Map.Entry<ModelResourceKey, Integer> candidate : candidateOccurrences.entrySet()) {
            Set<String> owners = existingOwners.get(candidate.getKey());
            int occurrences = candidate.getValue();
            if ((owners == null || owners.isEmpty()) && occurrences == 1) {
                continue;
            }
            conflicts.add(new ModelNameConflict(
                    candidate.getKey().type(),
                    candidate.getKey().modelName(),
                    owners == null ? List.of() : List.copyOf(owners),
                    occurrences
            ));
        }
        return List.copyOf(conflicts);
    }

    private Map<ModelResourceKey, Set<String>> existingOwners(
            String namespace,
            String excludedBundleName
    ) {
        Map<ModelResourceKey, Set<String>> owners = new TreeMap<>();
        List<Bundle> bundles = systemBundlesContext.getBundleList();
        if (bundles == null) {
            return owners;
        }
        for (Bundle bundle : bundles) {
            if (bundle == null || excludedBundleName != null
                    && excludedBundleName.equals(bundle.getName())) {
                continue;
            }
            BundleDefinition definition = bundle.getDefinition();
            if (definition == null
                    || !namespace.equals(canonicalNamespace(definition.getNamespace()))) {
                continue;
            }
            String owner = bundle.getName() == null || bundle.getName().isBlank()
                    ? "<unnamed>"
                    : bundle.getName();
            addOwners(owners, bundle, "TM", ".tm", owner);
            addOwners(owners, bundle, "QM", ".qm", owner);
        }
        return owners;
    }

    private Map<ModelResourceKey, Integer> candidateOccurrences(
            String candidateBundleName,
            String namespace,
            String path
    ) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                candidateBundleName, namespace, path, false);
        ExternalFileBundle candidate = new ExternalFileBundle(systemBundlesContext);
        candidate.setName(definition.getName());
        candidate.setBundleDefinition(definition);
        candidate.setBasePath(definition.getPath());
        candidate.setRootPath(definition.getPath());

        Map<ModelResourceKey, Integer> occurrences = new TreeMap<>();
        addOccurrences(occurrences, candidate, "TM", ".tm");
        addOccurrences(occurrences, candidate, "QM", ".qm");
        return occurrences;
    }

    private static void addOwners(
            Map<ModelResourceKey, Set<String>> owners,
            Bundle bundle,
            String type,
            String suffix,
            String owner
    ) {
        for (ModelResourceKey key : resourceKeys(bundle, type, suffix)) {
            owners.computeIfAbsent(key, ignored -> new TreeSet<>()).add(owner);
        }
    }

    private static void addOccurrences(
            Map<ModelResourceKey, Integer> occurrences,
            Bundle bundle,
            String type,
            String suffix
    ) {
        for (ModelResourceKey key : resourceKeys(bundle, type, suffix)) {
            occurrences.merge(key, 1, Integer::sum);
        }
    }

    private static Collection<ModelResourceKey> resourceKeys(
            Bundle bundle,
            String type,
            String suffix
    ) {
        BundleResource[] resources = bundle.findBundleResources("**/*" + suffix);
        if (resources == null || resources.length == 0) {
            return List.of();
        }
        List<ModelResourceKey> keys = new ArrayList<>(resources.length);
        for (BundleResource resource : resources) {
            String filename = resource == null || resource.getResource() == null
                    ? null
                    : resource.getResource().getFilename();
            if (filename == null || !filename.endsWith(suffix)
                    || filename.length() <= suffix.length()) {
                throw new IllegalStateException(
                        type + " resource must have a canonical " + suffix + " filename");
            }
            keys.add(new ModelResourceKey(
                    type,
                    filename.substring(0, filename.length() - suffix.length())
            ));
        }
        return keys;
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? "" : namespace.trim();
    }

    private record ModelResourceKey(String type, String modelName)
            implements Comparable<ModelResourceKey> {

        @Override
        public int compareTo(ModelResourceKey other) {
            int byType = type.compareTo(other.type);
            return byType != 0 ? byType : modelName.compareTo(other.modelName);
        }
    }

    public record ModelNameConflict(
            String type,
            String modelName,
            List<String> existingBundleNames,
            int candidateOccurrences
    ) {
        public ModelNameConflict {
            existingBundleNames = existingBundleNames == null
                    ? List.of()
                    : List.copyOf(existingBundleNames);
        }
    }
}
