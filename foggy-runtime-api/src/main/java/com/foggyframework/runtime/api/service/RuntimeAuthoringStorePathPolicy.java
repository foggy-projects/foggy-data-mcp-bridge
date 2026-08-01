package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalBundleResourceSupport;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fail-closed path boundary between the authoring store and direct-filesystem
 * Bundle sources. This class compares both lexical absolute paths and identities
 * resolved through the nearest existing ancestor so symlink aliases cannot hide
 * an overlap.
 */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringStorePathPolicy {

    private final FoggyRuntimeApiProperties properties;
    private final SystemBundlesContext bundlesContext;

    public RuntimeAuthoringStorePathPolicy(
            FoggyRuntimeApiProperties properties,
            SystemBundlesContext bundlesContext
    ) {
        this.properties = properties;
        this.bundlesContext = bundlesContext;
    }

    public void assertBundleSourceDisjoint(String source) {
        if (!isDirectFilesystem(source)) {
            return;
        }
        assertDisjoint(configuredStoreRoot(), source);
        assertDisjoint(configuredPublishedRoot(), source);
    }

    public void assertStoreDisjoint(Iterable<RuntimeBundleRecord> records) {
        Path root = configuredStoreRoot();
        Path publishedRoot = configuredPublishedRoot();
        assertDisjoint(root, publishedRoot.toString());
        Map<String, RuntimeBundleRecord> managed = records == null
                ? Map.of()
                : iterableRecords(records).stream()
                .filter(record -> record != null && StringUtils.hasText(record.name()))
                .collect(Collectors.toMap(RuntimeBundleRecord::name,
                        Function.identity(), (first, ignored) -> first));
        List<BundleDefinition> definitions = bundlesContext.listExternalBundles();
        if (definitions != null) {
            for (BundleDefinition definition : definitions) {
                if (definition instanceof ExternalBundleDefinition external
                        && isDirectFilesystem(external.getPath())) {
                    assertDisjoint(root, external.getPath());
                    RuntimeBundleRecord record = managed.get(definition.getName());
                    if (!isOwnedPublication(record, external.getPath(), publishedRoot)) {
                        assertDisjoint(publishedRoot, external.getPath());
                    }
                }
            }
        }
        if (records != null) {
            for (RuntimeBundleRecord record : records) {
                if (record != null && isDirectFilesystem(record.path())) {
                    assertDisjoint(root, record.path());
                    if (!isOwnedPublication(record, record.path(), publishedRoot)) {
                        assertDisjoint(publishedRoot, record.path());
                    }
                }
            }
        }
    }

    private static List<RuntimeBundleRecord> iterableRecords(
            Iterable<RuntimeBundleRecord> records
    ) {
        List<RuntimeBundleRecord> values = new ArrayList<>();
        records.forEach(values::add);
        return values;
    }

    private static boolean isOwnedPublication(
            RuntimeBundleRecord record,
            String source,
            Path publishedRoot
    ) {
        if (record == null || !record.immutablePublication()
                || !StringUtils.hasText(source)
                || !source.equals(record.path())) {
            return false;
        }
        try {
            Path normalized = Path.of(source).toAbsolutePath().normalize();
            return !normalized.equals(publishedRoot)
                    && normalized.startsWith(publishedRoot);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    Path configuredStoreRoot() {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces();
        String value = configured == null ? null : configured.getPath();
        try {
            return Path.of(StringUtils.hasText(value)
                            ? value : ".foggy-runtime/authoring-workspaces")
                    .toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw new PathConflictException();
        }
    }

    Path configuredPublishedRoot() {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces();
        String value = configured == null
                ? null : configured.getPublishedBundlesPath();
        try {
            return Path.of(StringUtils.hasText(value)
                            ? value : ".foggy-runtime/published-bundles")
                    .toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw new PathConflictException();
        }
    }

    private static void assertDisjoint(Path root, String source) {
        try {
            Path normalizedSource = Path.of(source).toAbsolutePath().normalize();
            Path rootIdentity = resolveIdentity(root);
            Path sourceIdentity = resolveIdentity(normalizedSource);
            if (overlaps(root, normalizedSource)
                    || overlaps(rootIdentity, sourceIdentity)) {
                throw new PathConflictException();
            }
        } catch (PathConflictException conflict) {
            throw conflict;
        } catch (IOException | RuntimeException unprovable) {
            throw new PathConflictException();
        }
    }

    private static Path resolveIdentity(Path value) throws IOException {
        Path cursor = value;
        List<Path> missing = new ArrayList<>();
        while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(cursor.getFileName());
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new IOException("path has no existing ancestor");
        }
        Path identity = cursor.toRealPath();
        Collections.reverse(missing);
        for (Path segment : missing) {
            identity = identity.resolve(segment);
        }
        return identity.normalize();
    }

    private static boolean overlaps(Path first, Path second) {
        return first.equals(second)
                || first.startsWith(second)
                || second.startsWith(first);
    }

    private static boolean isDirectFilesystem(String value) {
        return StringUtils.hasText(value)
                && !ExternalBundleResourceSupport.isSpringResourceLocation(value);
    }

    public static final class PathConflictException extends IllegalStateException {
        private PathConflictException() {
            super("Authoring workspace store and Bundle source paths must be disjoint.");
        }
    }
}
