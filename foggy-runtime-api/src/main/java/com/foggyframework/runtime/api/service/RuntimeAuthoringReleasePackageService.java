package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringReleaseExportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleaseImportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleasePackage;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.ReleaseImportEvidence;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceService.ReleaseCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds and verifies portable text-only authoring release packages. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringReleasePackageService {

    public static final String FORMAT_VERSION = "foggy-authoring-release/v1";

    private final FoggyRuntimeApiProperties properties;
    private final RuntimeAuthoringWorkspaceService workspaces;
    private final RuntimeAuthoringWorkspaceStore store;
    private final RuntimeBundleInventoryService inventory;

    public RuntimeAuthoringReleasePackageService(
            FoggyRuntimeApiProperties properties,
            RuntimeAuthoringWorkspaceService workspaces,
            RuntimeAuthoringWorkspaceStore store,
            RuntimeBundleInventoryService inventory
    ) {
        this.properties = properties;
        this.workspaces = workspaces;
        this.store = store;
        this.inventory = inventory;
    }

    public AuthoringReleasePackage exportPackage(
            String workspaceId,
            AuthoringReleaseExportRequest request
    ) {
        String phase = "workspaces.release.export";
        if (request == null
                || !StringUtils.hasText(request.expectedCandidateRevision())) {
            throw failure("WORKSPACE_INVALID_REQUEST", phase,
                    "Exact candidate revision is required.", true);
        }
        ReleaseCandidate candidate = workspaces.releaseCandidate(
                workspaceId, request.expectedCandidateRevision().trim());
        List<AuthoringReleasePackage.Resource> resources = candidate.snapshot()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> resource(entry.getKey(), entry.getValue()))
                .toList();
        List<AuthoringReleasePackage.Dependency> dependencies = inventory.list()
                .stream()
                .filter(item -> candidate.workspace().namespace().equals(
                        canonical(item.namespace())))
                .map(item -> new AuthoringReleasePackage.Dependency(
                        item.name(), item.sourceType(), item.sourceIdentity(),
                        item.artifactRevision()))
                .sorted(Comparator.comparing(
                        AuthoringReleasePackage.Dependency::bundle))
                .toList();
        AuthoringReleasePackage draft = new AuthoringReleasePackage(
                FORMAT_VERSION, null, properties.getRuntimeApiVersion(),
                candidate.workspace().namespace(),
                candidate.workspace().sourceBundle(),
                candidate.workspace().candidateRevision(),
                candidate.workspace().baseBundleRevision(),
                candidate.workspace().baseSourceRevision(),
                Instant.now().toString(), candidate.workspace().lastValidation(),
                dependencies, resources);
        return withPackageId(draft, calculatePackageId(draft));
    }

    public AuthoringWorkspaceInfo importPackage(
            String headerNamespace,
            AuthoringReleaseImportRequest request
    ) {
        String phase = "workspaces.release.import";
        requirePromotionEnabled(phase);
        if (request == null || !StringUtils.hasText(request.targetBundle())
                || request.releasePackage() == null) {
            throw failure("WORKSPACE_INVALID_REQUEST", phase,
                    "Target Bundle and release package are required.", false);
        }
        ValidatedPackage validated = validatePackage(request.releasePackage());
        AuthoringReleasePackage release = validated.releasePackage();
        ReleaseImportEvidence evidence = new ReleaseImportEvidence(
                release.packageId(), release.formatVersion(),
                release.sourceRuntimeApiVersion(), release.sourceNamespace(),
                release.sourceBundle(), release.candidateRevision(),
                Instant.now().toString());
        return workspaces.importRelease(
                headerNamespace, request.namespace(), request.targetBundle(),
                validated.snapshot(), evidence);
    }

    public ValidatedPackage validatePackage(AuthoringReleasePackage release) {
        String phase = "workspaces.release.import";
        if (release == null || !FORMAT_VERSION.equals(release.formatVersion())
                || !StringUtils.hasText(release.packageId())
                || !StringUtils.hasText(release.sourceRuntimeApiVersion())
                || !StringUtils.hasText(release.sourceNamespace())
                || !StringUtils.hasText(release.sourceBundle())
                || !StringUtils.hasText(release.candidateRevision())
                || !StringUtils.hasText(release.baseBundleRevision())
                || !StringUtils.hasText(release.baseNamespaceSourceRevision())
                || !StringUtils.hasText(release.exportedAt())
                || release.validation() == null || !release.validation().valid()
                || release.resources() == null || release.resources().isEmpty()) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package metadata is incomplete or unsupported.", false);
        }
        validateMetadata(release, phase);
        Map<String, byte[]> snapshot = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        long totalBytes = 0L;
        for (AuthoringReleasePackage.Resource resource : release.resources()) {
            if (resource == null || resource.content() == null) {
                throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                        "Release package contains an incomplete resource.", false);
            }
            String path = store.canonicalResourcePath(resource.path(), phase);
            if (!folded.add(path.toLowerCase(Locale.ROOT))
                    || snapshot.containsKey(path)) {
                throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                        "Release package contains duplicate resource paths.", false);
            }
            byte[] content = strictUtf8(resource.content(), phase);
            totalBytes += content.length;
            if (content.length > store.limits().maxResourceBytes()
                    || totalBytes > store.limits().maxRevisionBytes()
                    || snapshot.size() + 1 > store.limits().maxResourcesPerRevision()
                    || resource.size() != content.length
                    || !resourceType(path).equals(resource.type())
                    || !sha256(content).equals(resource.sha256())) {
                throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                        "Release package resource identity or limits are invalid.", false);
            }
            snapshot.put(path, content);
        }
        String candidateRevision = CandidateContentRevision.calculate(snapshot);
        AuthoringWorkspaceInfo.ValidationEvidence validation = release.validation();
        if (!candidateRevision.equals(release.candidateRevision())
                || !candidateRevision.equals(validation.candidateRevision())
                || !release.baseBundleRevision().equals(
                validation.baseBundleRevision())
                || !release.baseNamespaceSourceRevision().equals(
                validation.baseNamespaceSourceRevision())
                || !release.packageId().equals(calculatePackageId(release))) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package hash or validation provenance is invalid.", false);
        }
        return new ValidatedPackage(release, Map.copyOf(snapshot));
    }

    private void validateMetadata(
            AuthoringReleasePackage release,
            String phase
    ) {
        try {
            Instant.parse(release.exportedAt());
            Instant.parse(release.validation().validatedAt());
        } catch (RuntimeException invalidTimestamp) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package timestamps are invalid.", false);
        }
        if (!safeText(release.sourceRuntimeApiVersion(), 128)
                || !safeText(release.sourceNamespace(), store.limits().maxPathBytes())
                || !safeText(release.sourceBundle(), store.limits().maxPathBytes())
                || !release.candidateRevision().matches("sha256:[0-9a-f]{64}")
                || !release.baseBundleRevision().matches("sha256:[0-9a-f]{64}")
                || !safeText(release.baseNamespaceSourceRevision(), 256)
                || release.validation().totalFiles() <= 0
                || release.validation().validFiles()
                != release.validation().totalFiles()
                || release.validation().invalidFiles() != 0
                || release.validation().cascadingErrors() != 0
                || !release.validation().issues().isEmpty()) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package provenance is invalid.", false);
        }
        List<AuthoringReleasePackage.Dependency> dependencies =
                release.dependencies() == null ? List.of() : release.dependencies();
        if (dependencies.size() > store.limits().maxResourcesPerRevision()) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package dependency inventory exceeds configured limits.",
                    false);
        }
        Set<String> bundles = new HashSet<>();
        for (AuthoringReleasePackage.Dependency dependency : dependencies) {
            if (dependency == null
                    || !safeText(dependency.bundle(), store.limits().maxPathBytes())
                    || !safeText(dependency.sourceType(), 64)
                    || !StringUtils.hasText(dependency.sourceIdentity())
                    || !dependency.sourceIdentity().matches("sha256:[0-9a-f]{64}")
                    || (StringUtils.hasText(dependency.artifactRevision())
                    && !dependency.artifactRevision().matches(
                    "sha256:[0-9a-f]{64}"))
                    || !bundles.add(dependency.bundle().trim()
                    .toLowerCase(Locale.ROOT))) {
                throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                        "Release package dependency inventory is invalid.", false);
            }
        }
    }

    private static boolean safeText(String value, int maxBytes) {
        if (!StringUtils.hasText(value) || value.length() > maxBytes) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }

    private static byte[] strictUtf8(String value, String phase) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException invalid) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID", phase,
                    "Release package resource content is not strict UTF-8.", false);
        }
    }

    public void requirePromotionEnabled(String phase) {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces();
        if (configured == null || !configured.isProductionPromotionEnabled()) {
            throw failure("WORKSPACE_PRODUCTION_PROMOTION_DISABLED", phase,
                    "Production promotion is not enabled on this Runtime.", false);
        }
    }

    public String calculatePackageId(AuthoringReleasePackage release) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, release.formatVersion());
            update(digest, release.sourceRuntimeApiVersion());
            update(digest, release.sourceNamespace());
            update(digest, release.sourceBundle());
            update(digest, release.candidateRevision());
            update(digest, release.baseBundleRevision());
            update(digest, release.baseNamespaceSourceRevision());
            updateValidation(digest, release.validation());
            List<AuthoringReleasePackage.Dependency> dependencies =
                    new ArrayList<>(release.dependencies() == null
                            ? List.of() : release.dependencies());
            dependencies.sort(Comparator.comparing(
                    AuthoringReleasePackage.Dependency::bundle,
                    Comparator.nullsFirst(String::compareTo)));
            update(digest, dependencies.size());
            for (AuthoringReleasePackage.Dependency dependency : dependencies) {
                update(digest, dependency == null ? null : dependency.bundle());
                update(digest, dependency == null ? null : dependency.sourceType());
                update(digest, dependency == null ? null : dependency.sourceIdentity());
                update(digest, dependency == null ? null : dependency.artifactRevision());
            }
            List<AuthoringReleasePackage.Resource> resources =
                    new ArrayList<>(release.resources() == null
                            ? List.of() : release.resources());
            resources.sort(Comparator.comparing(
                    AuthoringReleasePackage.Resource::path,
                    Comparator.nullsFirst(String::compareTo)));
            update(digest, resources.size());
            for (AuthoringReleasePackage.Resource resource : resources) {
                update(digest, resource == null ? null : resource.path());
                update(digest, resource == null ? null : resource.type());
                update(digest, resource == null ? -1L : resource.size());
                update(digest, resource == null ? null : resource.sha256());
                update(digest, resource == null ? null : resource.content());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void updateValidation(
            MessageDigest digest,
            AuthoringWorkspaceInfo.ValidationEvidence validation
    ) {
        if (validation == null) {
            update(digest, "<null>");
            return;
        }
        update(digest, validation.valid());
        update(digest, validation.candidateRevision());
        update(digest, validation.baseBundleRevision());
        update(digest, validation.baseNamespaceSourceRevision());
        update(digest, validation.validatedAt());
        update(digest, validation.totalFiles());
        update(digest, validation.validFiles());
        update(digest, validation.invalidFiles());
        update(digest, validation.cascadingErrors());
        List<AuthoringWorkspaceInfo.ValidationIssue> issues =
                validation.issues() == null ? List.of() : validation.issues();
        update(digest, issues.size());
        for (AuthoringWorkspaceInfo.ValidationIssue issue : issues) {
            update(digest, issue == null ? null : issue.path());
            update(digest, issue == null ? null : issue.type());
            update(digest, issue == null ? null : issue.code());
            update(digest, issue == null ? null : issue.message());
            update(digest, issue == null ? null : issue.category());
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0]
                : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void update(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static AuthoringReleasePackage withPackageId(
            AuthoringReleasePackage value,
            String packageId
    ) {
        return new AuthoringReleasePackage(
                value.formatVersion(), packageId,
                value.sourceRuntimeApiVersion(), value.sourceNamespace(),
                value.sourceBundle(), value.candidateRevision(),
                value.baseBundleRevision(), value.baseNamespaceSourceRevision(),
                value.exportedAt(), value.validation(), value.dependencies(),
                value.resources());
    }

    private static AuthoringReleasePackage.Resource resource(
            String path,
            byte[] content
    ) {
        String type = resourceType(path);
        return new AuthoringReleasePackage.Resource(
                path, type, content.length, sha256(content),
                new String(content, StandardCharsets.UTF_8));
    }

    private static String resourceType(String path) {
        if (path != null && path.endsWith(".tm")) return "TM";
        if (path != null && path.endsWith(".qm")) return "QM";
        return "FSSCRIPT";
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String canonical(String value) {
        return value == null ? "" : value.trim();
    }

    private static RuntimeAuthoringWorkspaceException failure(
            String code,
            String phase,
            String message,
            boolean safeToAutoRepair
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                code, phase, message, null, safeToAutoRepair);
    }

    public record ValidatedPackage(
            AuthoringReleasePackage releasePackage,
            Map<String, byte[]> snapshot
    ) {
    }
}
