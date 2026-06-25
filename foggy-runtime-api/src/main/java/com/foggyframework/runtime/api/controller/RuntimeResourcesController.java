package com.foggyframework.runtime.api.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ResourceExportRequest;
import com.foggyframework.runtime.api.dto.ResourceExportResponse;
import com.foggyframework.runtime.api.dto.ResourceFileInfo;
import com.foggyframework.runtime.api.dto.ResourceSaveFile;
import com.foggyframework.runtime.api.dto.ResourceSaveRequest;
import com.foggyframework.runtime.api.dto.ResourceSaveResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeResourcesController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final SystemBundlesContext systemBundlesContext;
    private final RuntimeBundleRegistryService registryService;

    public RuntimeResourcesController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            SystemBundlesContext systemBundlesContext,
            RuntimeBundleRegistryService registryService
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.systemBundlesContext = systemBundlesContext;
        this.registryService = registryService;
    }

    @PostMapping("/resources/export")
    public RuntimeEnvelope<ResourceExportResponse> exportResources(
            @RequestBody(required = false) ResourceExportRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String bundle = blankToNull(request != null ? request.bundle() : null);
        if (bundle == null) {
            return fail("INVALID_REQUEST", "resources.export", "Missing required field: bundle",
                    "Provide a bundle name.", false, null);
        }

        BundleLocation location = resolveBundle(bundle);
        if (location == null) {
            return fail("RESOURCE_BUNDLE_NOT_FOUND", "resources.export", "Bundle is not registered: " + bundle,
                    "Register a filesystem bundle before exporting resources.", false, null);
        }
        if (!Files.isDirectory(location.root())) {
            return fail("RESOURCE_EXPORT_UNSUPPORTED", "resources.export", "Bundle root is not a readable directory: " + location.root(),
                    "Use a filesystem external bundle directory.", false, location.root().toString());
        }

        boolean includeContent = request == null || request.includeContent() == null || request.includeContent();
        List<ResourceFileInfo> resources = new ArrayList<>();
        try {
            if (request != null && request.paths() != null && !request.paths().isEmpty()) {
                for (String path : request.paths()) {
                    Path relative = normalizeRelativePath(path);
                    if (!isAllowedResourcePath(relative)) {
                        return fail("RESOURCE_TYPE_NOT_ALLOWED", "resources.export", "Unsupported resource file type: " + path,
                                "Export only .tm, .qm, or model-list resource files.", true, path);
                    }
                    Path target = resolveInsideRoot(location.root(), relative);
                    if (!Files.isRegularFile(target)) {
                        return fail("RESOURCE_NOT_FOUND", "resources.export", "Resource file does not exist: " + path,
                                "Check the relative resource path and retry.", true, path);
                    }
                    resources.add(fileInfo(location.root(), target, includeContent, location.writable()));
                }
            } else {
                try (var stream = Files.walk(location.root())) {
                    stream
                            .filter(Files::isRegularFile)
                            .map(path -> location.root().relativize(path))
                            .filter(RuntimeResourcesController::isAllowedResourcePath)
                            .sorted(Comparator.comparing(RuntimeResourcesController::toUnixPath))
                            .forEach(relative -> resources.add(fileInfo(location.root(), location.root().resolve(relative), includeContent, location.writable())));
                }
            }
        } catch (IllegalArgumentException e) {
            return fail("INVALID_RESOURCE_PATH", "resources.export", e.getMessage(),
                    "Use a relative path inside the bundle directory.", true, null);
        } catch (IOException e) {
            return fail("RESOURCE_EXPORT_FAILED", "resources.export", e.getMessage(),
                    "Check bundle directory readability and retry.", false, location.root().toString());
        }

        String effectiveNamespace = stringOr(request != null ? request.namespace() : null, stringOr(namespace, location.namespace()));
        ResourceExportResponse response = new ResourceExportResponse(
                effectiveNamespace,
                bundle,
                location.root().toString(),
                resources,
                List.of()
        );
        return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
    }

    @PostMapping("/resources/save")
    public RuntimeEnvelope<ResourceSaveResponse> saveResources(
            @RequestBody(required = false) ResourceSaveRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String bundle = blankToNull(request != null ? request.bundle() : null);
        if (bundle == null) {
            return fail("INVALID_REQUEST", "resources.save", "Missing required field: bundle",
                    "Provide a runtime-managed bundle name.", false, null);
        }
        if (request.files() == null || request.files().isEmpty()) {
            return fail("INVALID_REQUEST", "resources.save", "Missing required field: files",
                    "Provide at least one resource file to save.", false, null);
        }

        RuntimeBundleRecord record = registryService.find(bundle).orElse(null);
        if (record == null) {
            return fail("RESOURCE_BUNDLE_NOT_WRITABLE", "resources.save", "Bundle is not managed by Runtime API: " + bundle,
                    "Only runtime-managed bundles can be saved through Runtime API.", false, null);
        }

        Path root = Path.of(record.path()).toAbsolutePath().normalize();
        List<ResourceFileInfo> saved = new ArrayList<>();
        try {
            List<ValidatedResourceSaveFile> validatedFiles = new ArrayList<>();
            for (ResourceSaveFile file : request.files()) {
                if (file == null || !StringUtils.hasText(file.path()) || file.content() == null) {
                    return fail("INVALID_REQUEST", "resources.save", "Each file requires path and content.",
                            "Send UTF-8 text content for each resource.", false, null);
                }
                Path relative = normalizeRelativePath(file.path());
                if (!isAllowedResourcePath(relative)) {
                    return fail("RESOURCE_TYPE_NOT_ALLOWED", "resources.save", "Unsupported resource file type: " + file.path(),
                            "Save only .tm, .qm, or model-list resource files.", true, file.path());
                }
                Path target = resolveInsideRoot(root, relative);
                if (StringUtils.hasText(file.baseSha256()) && Files.exists(target)) {
                    String currentSha = sha256(target);
                    if (!currentSha.equalsIgnoreCase(file.baseSha256())) {
                        return fail("RESOURCE_CONFLICT", "resources.save", "Resource changed since pull: " + file.path(),
                                "Pull the resource again, merge changes, then retry save.", true, file.path());
                    }
                }
                validatedFiles.add(new ValidatedResourceSaveFile(file, target));
            }
            Files.createDirectories(root);
            for (ValidatedResourceSaveFile validatedFile : validatedFiles) {
                writeUtf8Atomically(validatedFile.target(), validatedFile.file().content());
                saved.add(fileInfo(root, validatedFile.target(), false, true));
            }
        } catch (IllegalArgumentException e) {
            return fail("INVALID_RESOURCE_PATH", "resources.save", e.getMessage(),
                    "Use a relative path inside the bundle directory.", true, null);
        } catch (IOException e) {
            return fail("RESOURCE_SAVE_FAILED", "resources.save", e.getMessage(),
                    "Check bundle directory writability and retry.", false, root.toString());
        }

        List<String> warnings = new ArrayList<>();
        if (booleanOr(request.validate(), false)) {
            warnings.add("validate flag accepted but Stage 2 resources API does not run model validation yet; run models validate explicitly.");
        }
        if (booleanOr(request.refresh(), false)) {
            warnings.add("refresh flag accepted but Stage 2 resources API does not run model refresh yet; run models refresh explicitly.");
        }
        String effectiveNamespace = stringOr(request.namespace(), stringOr(namespace, record.namespace()));
        ResourceSaveResponse response = new ResourceSaveResponse(
                effectiveNamespace,
                bundle,
                root.toString(),
                saved.size(),
                saved,
                warnings
        );
        return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
    }

    private BundleLocation resolveBundle(String bundle) {
        RuntimeBundleRecord record = registryService.find(bundle).orElse(null);
        if (record != null) {
            return new BundleLocation(record.namespace(), Path.of(record.path()).toAbsolutePath().normalize(), true);
        }
        for (BundleDefinition definition : systemBundlesContext.listExternalBundles()) {
            if (bundle.equals(definition.getName()) && definition instanceof ExternalBundleDefinition external) {
                String path = external.getPath();
                if (!StringUtils.hasText(path) || isNonFilesystemLocation(path)) {
                    return null;
                }
                return new BundleLocation(definition.getNamespace(), Path.of(path).toAbsolutePath().normalize(), false);
            }
        }
        return null;
    }

    private ResourceFileInfo fileInfo(Path root, Path file, boolean includeContent, boolean writable) {
        try {
            Path relative = root.relativize(file);
            return new ResourceFileInfo(
                    toUnixPath(relative),
                    resourceType(relative),
                    Files.size(file),
                    sha256(file),
                    includeContent ? Files.readString(file, StandardCharsets.UTF_8) : null,
                    writable
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource file: " + file, e);
        }
    }

    private static Path normalizeRelativePath(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Resource path is blank.");
        }
        Path path = Path.of(value.replace('\\', '/')).normalize();
        if (path.isAbsolute() || hasParentTraversal(path)) {
            throw new IllegalArgumentException("Resource path escapes bundle root: " + value);
        }
        return path;
    }

    private static boolean hasParentTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveInsideRoot(Path root, Path relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Resource path escapes bundle root: " + relative);
        }
        return target;
    }

    private static boolean isAllowedResourcePath(Path path) {
        String filename = path.getFileName() != null ? path.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if (filename.endsWith(".tm") || filename.endsWith(".qm")) {
            return true;
        }
        boolean modelList = filename.contains("model-list") || filename.contains("modellist");
        return modelList && (filename.endsWith(".yml") || filename.endsWith(".yaml") || filename.endsWith(".json") || filename.endsWith(".txt"));
    }

    private static String resourceType(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".tm")) {
            return "TM";
        }
        if (filename.endsWith(".qm")) {
            return "QM";
        }
        return "MODEL_LIST";
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static boolean isNonFilesystemLocation(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("classpath:") || lower.startsWith("jar:") || lower.startsWith("http:")
                || lower.startsWith("https:") || lower.startsWith("s3:") || lower.startsWith("obs:");
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static void writeUtf8Atomically(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair,
            String path
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                null,
                null,
                path,
                suggestedNextAction,
                safeToAutoRepair
        );
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, RuntimeDiagnostics.empty());
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

    private record BundleLocation(String namespace, Path root, boolean writable) {
    }

    private record ValidatedResourceSaveFile(ResourceSaveFile file, Path target) {
    }
}
