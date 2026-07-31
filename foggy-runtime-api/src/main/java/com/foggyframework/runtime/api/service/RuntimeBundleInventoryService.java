package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalBundleResourceSupport;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fact-based inventory shared by Bundle discovery and authoring eligibility. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeBundleInventoryService {

    private final SystemBundlesContext bundlesContext;
    private final RuntimeBundleRegistryService registry;

    public RuntimeBundleInventoryService(
            SystemBundlesContext bundlesContext,
            RuntimeBundleRegistryService registry
    ) {
        this.bundlesContext = bundlesContext;
        this.registry = registry;
    }

    public List<BundleInfo> list() {
        Map<String, RuntimeBundleRecord> records = new LinkedHashMap<>();
        for (RuntimeBundleRecord record : registry.listRecords()) {
            if (record != null && StringUtils.hasText(record.name())) {
                records.put(record.name().trim(), record);
            }
        }

        Map<String, BundleInfo> inventory = new LinkedHashMap<>();
        List<Bundle> liveBundles = bundlesContext.getBundleList();
        if (liveBundles != null) {
            for (Bundle bundle : liveBundles) {
                if (bundle == null || bundle.getDefinition() == null
                        || !StringUtils.hasText(bundle.getName())) {
                    continue;
                }
                String name = bundle.getName().trim();
                inventory.put(name, liveInfo(bundle, records.get(name)));
            }
        }

        // Preserve compatibility with contexts that expose external definitions
        // independently while keeping workspace eligibility tied to a real live Bundle.
        List<BundleDefinition> externalDefinitions = bundlesContext.listExternalBundles();
        if (externalDefinitions != null) {
            for (BundleDefinition definition : externalDefinitions) {
                if (definition == null || !StringUtils.hasText(definition.getName())) {
                    continue;
                }
                String name = definition.getName().trim();
                inventory.putIfAbsent(name,
                        definitionInfo(definition, records.get(name)));
            }
        }

        for (RuntimeBundleRecord record : records.values()) {
            inventory.putIfAbsent(record.name().trim(), inactiveInfo(record));
        }
        return inventory.values().stream()
                .sorted(Comparator.comparing(BundleInfo::name))
                .toList();
    }

    public WorkspaceSource requireWorkspaceSource(
            String bundleName,
            String namespace,
            String phase
    ) {
        String name = canonicalName(bundleName);
        String targetNamespace = canonicalNamespace(namespace);
        if (!StringUtils.hasText(name) || !StringUtils.hasText(targetNamespace)) {
            throw ineligible(phase,
                    "A source Bundle and explicit Namespace are required.");
        }
        RuntimeBundleRecord record = registry.find(name)
                .filter(RuntimeBundleRecord::enabled)
                .orElseThrow(() -> ineligible(phase,
                        "Source must be an enabled Runtime-managed Bundle."));
        if (!targetNamespace.equals(canonicalNamespace(record.namespace()))) {
            throw ineligible(phase,
                    "Source Bundle does not belong to the target Namespace.");
        }

        Bundle live;
        try {
            live = bundlesContext.getBundleByName(name, false);
        } catch (RuntimeException lookupFailure) {
            RuntimeAuthoringWorkspaceException failure = ineligible(
                    phase, "Source Bundle is not active in this Runtime.");
            failure.addSuppressed(lookupFailure);
            throw failure;
        }
        if (!(live instanceof ExternalFileBundle external)
                || !(live.getDefinition()
                instanceof ExternalBundleDefinition definition)
                || ExternalBundleResourceSupport.isSpringResourceLocation(
                definition.getPath())) {
            throw ineligible(phase,
                    "Source must be a live external filesystem Bundle.");
        }
        if (!name.equals(canonicalName(live.getName()))
                || !name.equals(canonicalName(definition.getName()))
                || !targetNamespace.equals(canonicalNamespace(
                definition.getNamespace()))) {
            throw ineligible(phase,
                    "Runtime registry and live Bundle identity do not match.");
        }

        Path managed = realWritableDirectory(record.path(), phase);
        Path defined = realWritableDirectory(definition.getPath(), phase);
        Path base = realWritableDirectory(external.getBasePath(), phase);
        Path root = realWritableDirectory(external.getRootPath(), phase);
        if (!managed.equals(defined) || !managed.equals(base)
                || !managed.equals(root)) {
            throw ineligible(phase,
                    "Runtime registry and live Bundle source do not match.");
        }
        return new WorkspaceSource(
                record, external, managed,
                sourceIdentity(name, targetNamespace,
                        "external-filesystem", managed));
    }

    private BundleInfo liveInfo(Bundle bundle, RuntimeBundleRecord record) {
        BundleDefinition definition = bundle.getDefinition();
        String name = canonicalName(bundle.getName());
        String namespace = canonicalNamespace(definition.getNamespace());
        String sourceType = sourceType(bundle, definition);
        String path = definition instanceof ExternalBundleDefinition external
                ? external.getPath() : null;
        Boolean watch = definition instanceof ExternalBundleDefinition external
                ? external.isWatch() : null;
        boolean eligible = false;
        String identity = sourceIdentity(name, namespace, sourceType,
                stableSourceValue(bundle, definition));
        if (record != null) {
            try {
                WorkspaceSource source = requireWorkspaceSource(
                        name, namespace, "bundles.list");
                eligible = true;
                identity = source.sourceIdentity();
            } catch (RuntimeAuthoringWorkspaceException ignored) {
                // Inventory reports false instead of turning an ineligible row into an error.
            }
        }
        boolean managed = record != null;
        return new BundleInfo(
                name, namespace, path, watch, true,
                managed ? "runtime-registry" : "config",
                managed, managed, managed, "active", null,
                sourceType, eligible, eligible, List.of(namespace), identity);
    }

    private BundleInfo definitionInfo(
            BundleDefinition definition,
            RuntimeBundleRecord record
    ) {
        String name = canonicalName(definition.getName());
        String namespace = canonicalNamespace(definition.getNamespace());
        String path = definition instanceof ExternalBundleDefinition external
                ? external.getPath() : null;
        Boolean watch = definition instanceof ExternalBundleDefinition external
                ? external.isWatch() : null;
        String type = definition instanceof ExternalBundleDefinition external
                && ExternalBundleResourceSupport.isSpringResourceLocation(
                external.getPath())
                ? "external-resource" : "external-filesystem";
        boolean managed = record != null;
        return new BundleInfo(
                name, namespace, path, watch, true,
                managed ? "runtime-registry" : "config",
                managed, managed, managed, "active", null,
                type, false, false, List.of(namespace),
                sourceIdentity(name, namespace, type, path));
    }

    private BundleInfo inactiveInfo(RuntimeBundleRecord record) {
        String namespace = canonicalNamespace(record.namespace());
        return new BundleInfo(
                canonicalName(record.name()), namespace, record.path(),
                record.watch(), record.enabled(), "runtime-registry",
                true, true, true,
                record.enabled() ? "inactive" : "disabled",
                record.enabled()
                        ? "Runtime-managed Bundle is not currently registered."
                        : null,
                ExternalBundleResourceSupport.isSpringResourceLocation(record.path())
                        ? "external-resource" : "external-filesystem",
                false, false, List.of(namespace),
                sourceIdentity(record.name(), namespace, "inactive", record.path()));
    }

    private static String sourceType(Bundle bundle, BundleDefinition definition) {
        if (bundle instanceof ExternalFileBundle
                || definition instanceof ExternalBundleDefinition) {
            String path = definition instanceof ExternalBundleDefinition external
                    ? external.getPath() : bundle.getRootPath();
            return ExternalBundleResourceSupport.isSpringResourceLocation(path)
                    ? "external-resource" : "external-filesystem";
        }
        if (bundle.getMode() == BundleImpl.MODE_JAR) {
            return "jar";
        }
        return "classpath";
    }

    private static Object stableSourceValue(
            Bundle bundle,
            BundleDefinition definition
    ) {
        if (definition instanceof ExternalBundleDefinition external) {
            return external.getPath();
        }
        return List.of(
                nullToEmpty(bundle.getRootPath()),
                definition.getDefinitionClass() == null
                        ? "" : definition.getDefinitionClass().getName());
    }

    private static Path realWritableDirectory(String value, String phase) {
        try {
            if (!StringUtils.hasText(value)
                    || ExternalBundleResourceSupport.isSpringResourceLocation(value)) {
                throw new IOException("not a direct filesystem path");
            }
            Path normalized = Path.of(value).toAbsolutePath().normalize();
            assertNoSymlinkComponents(normalized);
            Path real = normalized.toRealPath();
            if (!normalized.equals(real) || Files.isSymbolicLink(normalized)
                    || !Files.isDirectory(real) || !Files.isReadable(real)
                    || !Files.isWritable(real)) {
                throw new IOException("source is not a readable writable real directory");
            }
            return real;
        } catch (IOException | RuntimeException failure) {
            RuntimeAuthoringWorkspaceException error = ineligible(
                    phase, "Source is not a safe readable and writable directory.");
            error.addSuppressed(failure);
            throw error;
        }
    }

    private static void assertNoSymlinkComponents(Path path) throws IOException {
        Path cursor = path.getRoot();
        if (cursor == null) {
            throw new IOException("source path has no root");
        }
        for (Path segment : cursor.relativize(path)) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw new IOException("source path contains a symbolic link");
            }
        }
    }

    public static String sourceIdentity(
            String name,
            String namespace,
            String sourceType,
            Object source
    ) {
        MessageDigest digest = sha256();
        update(digest, canonicalName(name));
        update(digest, canonicalNamespace(namespace));
        update(digest, nullToEmpty(sourceType));
        if (source instanceof Path path) {
            update(digest, path.toString());
            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                update(digest, String.valueOf(attributes.fileKey()));
                update(digest, attributes.creationTime().toString());
            } catch (IOException ignored) {
                update(digest, "unreadable");
            }
        } else {
            update(digest, String.valueOf(source));
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static RuntimeAuthoringWorkspaceException ineligible(
            String phase,
            String message
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                "WORKSPACE_SOURCE_INELIGIBLE", phase, message,
                null, false);
    }

    private static String canonicalName(String value) {
        return value == null ? "" : value.trim();
    }

    private static String canonicalNamespace(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record WorkspaceSource(
            RuntimeBundleRecord record,
            ExternalFileBundle bundle,
            Path path,
            String sourceIdentity
    ) {
    }
}
