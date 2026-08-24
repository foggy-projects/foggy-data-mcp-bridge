package com.foggyframework.analytics.console.service;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetKind;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetStatus;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleFolder;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.canAdminister;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.canEdit;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.canView;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.forbidden;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.requireAuthenticated;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.requireDesigner;
import static com.foggyframework.analytics.console.service.AnalyticsConsoleAccessPolicy.requireEditableDraft;

/** Product service: permissions and metadata stay outside Java Analytics definitions. */
public final class AnalyticsConsoleService {

    private final AnalyticsConsoleCatalogRepository catalog;
    private final AnalyticsBundleStore bundleStore;
    private final AnalyticsFunctionClient functions;
    private final long maxDefinitionBytes;
    private final Clock clock;

    public AnalyticsConsoleService(
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsBundleStore bundleStore,
            AnalyticsFunctionClient functions,
            long maxDefinitionBytes) {
        this(catalog, bundleStore, functions, maxDefinitionBytes, Clock.systemUTC());
    }

    AnalyticsConsoleService(
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsBundleStore bundleStore,
            AnalyticsFunctionClient functions,
            long maxDefinitionBytes,
            Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.bundleStore = Objects.requireNonNull(bundleStore, "bundleStore");
        this.functions = Objects.requireNonNull(functions, "functions");
        if (maxDefinitionBytes < 1 || maxDefinitionBytes > 16L * 1024 * 1024) {
            throw new IllegalArgumentException("maxDefinitionBytes is outside the safe range");
        }
        this.maxDefinitionBytes = maxDefinitionBytes;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<AnalyticsConsoleFolder> folders(AnalyticsConsoleSubject subject) {
        requireAuthenticated(subject);
        AnalyticsConsoleCatalogState state = catalog.read();
        Set<String> visibleFolderIds = state.assets().stream()
                .filter(asset -> canView(subject, current(asset)))
                .map(AnalyticsConsoleAsset::folderId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return state.folders().stream()
                .filter(folder -> subject.hasRole(AnalyticsConsoleRole.ADMIN)
                        || folder.ownerSubjectRef().equals(subject.subjectRef())
                        || visibleFolderIds.contains(folder.folderId()))
                .sorted(Comparator.comparing(AnalyticsConsoleFolder::name))
                .toList();
    }

    public AnalyticsConsoleFolder createFolder(
            AnalyticsConsoleSubject subject,
            String name,
            String parentFolderId) {
        requireDesigner(subject);
        String safeName = text(name, "name", 120);
        Instant now = clock.instant();
        AnalyticsConsoleFolder folder = new AnalyticsConsoleFolder(
                "folder-" + UUID.randomUUID(),
                safeName,
                optional(parentFolderId),
                subject.subjectRef(),
                now);
        catalog.update(state -> {
            if (folder.parentFolderId() != null) {
                AnalyticsConsoleFolder parent = state.folders().stream()
                        .filter(value -> value.folderId().equals(folder.parentFolderId()))
                        .findFirst()
                        .orElseThrow(() -> error("ANALYTICS_CONSOLE_FOLDER_NOT_FOUND",
                                "Parent folder was not found"));
                if (!subject.hasRole(AnalyticsConsoleRole.ADMIN)
                        && !parent.ownerSubjectRef().equals(subject.subjectRef())) {
                    throw forbidden();
                }
            }
            List<AnalyticsConsoleFolder> folders = new ArrayList<>(state.folders());
            folders.add(folder);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), folders, state.assets(), state.conversations());
        });
        return folder;
    }

    public List<AnalyticsConsoleAsset> assets(AnalyticsConsoleSubject subject) {
        requireAuthenticated(subject);
        return catalog.read().assets().stream()
                .map(this::current)
                .filter(asset -> canView(subject, asset) || canEdit(subject, asset))
                .sorted(Comparator.comparing(AnalyticsConsoleAsset::updatedAt).reversed())
                .toList();
    }

    public AssetDetail asset(AnalyticsConsoleSubject subject, String assetId) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        boolean editable = canEdit(subject, asset);
        if (!canView(subject, asset) && !editable) {
            throw forbidden();
        }
        if (!editable) {
            return new AssetDetail(asset, null);
        }
        byte[] content = bundleStore.readArtifact(
                new AnalyticsBundleRef(asset.bundleRef()),
                new AnalyticsBundleRevision(asset.bundleRevision()),
                asset.resourcePath());
        return new AssetDetail(asset, new String(content, StandardCharsets.UTF_8));
    }

    public AnalyticsConsoleAsset createDraft(
            AnalyticsConsoleSubject subject,
            CreateDraft command) {
        requireDesigner(subject);
        Objects.requireNonNull(command, "command");
        AnalyticsBundleRef bundleRef = new AnalyticsBundleRef(command.bundleRef());
        AnalyticsArtifactRef artifactRef = new AnalyticsArtifactRef(
                AnalyticsArtifactKind.valueOf(command.kind().name()),
                command.artifactRef());
        AnalyticsBundleRevision expected = new AnalyticsBundleRevision(command.expectedBundleRevision());
        ResolvedAnalyticsBundle resolved = bundleStore.resolve(bundleRef);
        if (!resolved.lifecycle().isWritable()) {
            throw error("ANALYTICS_CONSOLE_BUNDLE_READ_ONLY",
                    "A Console draft requires a runtime-owned Analytics Bundle");
        }
        if (!resolved.bundleRevision().equals(expected)) {
            throw conflict();
        }

        AnalyticsConsoleCatalogState currentState = catalog.read();
        requireFolderAccess(subject, currentState, command.folderId());
        if (currentState.assets().stream().anyMatch(asset ->
                asset.bundleRef().equals(bundleRef.value())
                        && asset.status() != AnalyticsConsoleAssetStatus.DRAFT)) {
            throw error("ANALYTICS_CONSOLE_BUNDLE_ALREADY_PUBLISHED",
                    "A published Console Bundle cannot be reused for a new draft");
        }

        String resourcePath = command.kind().resourceDirectory()
                + "/" + artifactRef.value() + "." + command.kind().runtimeKind() + ".json";
        AnalyticsBundleRevision revision = expected;
        if (command.definitionContent() != null) {
            byte[] bytes = definitionBytes(command.definitionContent());
            revision = bundleStore.saveArtifact(bundleRef, expected, resourcePath, bytes)
                    .bundleRevision();
        } else {
            describe(command.kind(), bundleRef.value(), artifactRef.value(), revision.value());
        }

        Instant now = clock.instant();
        AnalyticsConsoleAsset asset = new AnalyticsConsoleAsset(
                "analytics-" + UUID.randomUUID(),
                text(command.title(), "title", 160),
                command.description(),
                optional(command.folderId()),
                subject.subjectRef(),
                command.kind(),
                bundleRef.value(),
                artifactRef.value(),
                resourcePath,
                revision.value(),
                null,
                AnalyticsConsoleAssetStatus.DRAFT,
                AnalyticsConsoleVisibility.PRIVATE,
                Set.of(),
                now,
                now,
                null);
        catalog.update(state -> {
            List<AnalyticsConsoleAsset> assets = new ArrayList<>(state.assets());
            assets.add(asset);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), state.folders(), assets, state.conversations());
        });
        return asset;
    }

    public AnalyticsConsoleAsset saveDefinition(
            AnalyticsConsoleSubject subject,
            String assetId,
            String expectedBundleRevision,
            String content) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        requireEditableDraft(subject, asset);
        AnalyticsBundleRevision expected = new AnalyticsBundleRevision(expectedBundleRevision);
        if (!asset.bundleRevision().equals(expected.value())) {
            throw conflict();
        }
        if (catalog.read().assets().stream().anyMatch(value ->
                value.bundleRef().equals(asset.bundleRef())
                        && value.status() != AnalyticsConsoleAssetStatus.DRAFT)) {
            throw error("ANALYTICS_CONSOLE_BUNDLE_ALREADY_PUBLISHED",
                    "Published Analytics definitions are immutable in Console");
        }
        AnalyticsBundleRevision next = bundleStore.saveArtifact(
                new AnalyticsBundleRef(asset.bundleRef()),
                expected,
                asset.resourcePath(),
                definitionBytes(content)).bundleRevision();
        AnalyticsConsoleAsset updated = asset.withRevision(next.value(), clock.instant());
        replace(updated);
        return updated;
    }

    public AnalyticsConsoleAsset validate(
            AnalyticsConsoleSubject subject,
            String assetId,
            String expectedBundleRevision) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        requireEditableDraft(subject, asset);
        exactRevision(asset, expectedBundleRevision);
        requireSuccess(functions.validateBundle(new AnalyticsBundleFunctionRequest(
                asset.bundleRef(), asset.bundleRevision(), context())));
        describe(asset.kind(), asset.bundleRef(), asset.artifactRef(), asset.bundleRevision());
        AnalyticsConsoleAsset validated = asset.validated(asset.bundleRevision(), clock.instant());
        replace(validated);
        return validated;
    }

    public AnalyticsRenderResult preview(
            AnalyticsConsoleSubject subject,
            String assetId,
            String expectedBundleRevision,
            Map<String, Object> parameters,
            String timezone,
            String locale) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        if (!canEdit(subject, asset) && !canView(subject, asset)) {
            throw forbidden();
        }
        exactRevision(asset, expectedBundleRevision);
        AnalyticsRenderFunctionRequest request = renderRequest(
                subject, asset, parameters, timezone, locale);
        return requireSuccess(asset.kind() == AnalyticsConsoleAssetKind.REPORT
                ? functions.previewReport(request)
                : functions.previewDashboard(request));
    }

    public AnalyticsConsoleAsset publish(
            AnalyticsConsoleSubject subject,
            String assetId,
            String expectedBundleRevision) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        requireEditableDraft(subject, asset);
        exactRevision(asset, expectedBundleRevision);
        if (!asset.bundleRevision().equals(asset.validatedBundleRevision())) {
            throw error("ANALYTICS_CONSOLE_VALIDATION_REQUIRED",
                    "The current Analytics revision must be validated before publication");
        }
        AnalyticsConsoleAsset published = asset.published(clock.instant());
        replace(published);
        return published;
    }

    public AnalyticsConsoleAsset updateAudience(
            AnalyticsConsoleSubject subject,
            String assetId,
            AnalyticsConsoleVisibility visibility,
            Set<String> viewerSubjectRefs) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        if (!canAdminister(subject, asset)) {
            throw forbidden();
        }
        Set<String> viewers = new LinkedHashSet<>(Objects.requireNonNull(
                viewerSubjectRefs, "viewerSubjectRefs"));
        viewers.forEach(value -> text(value, "viewerSubjectRef", 256));
        AnalyticsConsoleAsset updated = asset.withAudience(
                Objects.requireNonNull(visibility, "visibility"), viewers, clock.instant());
        replace(updated);
        return updated;
    }

    public boolean canInvokeFap(
            AnalyticsConsoleSubject subject,
            String bundleRef,
            String artifactRef) {
        requireAuthenticated(subject);
        if (bundleRef == null) {
            return subject.hasRole(AnalyticsConsoleRole.ADMIN)
                    || subject.hasRole(AnalyticsConsoleRole.DESIGNER);
        }
        return catalog.read().assets().stream()
                .map(this::current)
                .filter(asset -> asset.bundleRef().equals(bundleRef))
                .filter(asset -> artifactRef == null || asset.artifactRef().equals(artifactRef))
                .anyMatch(asset -> canEdit(subject, asset) || canView(subject, asset));
    }

    public AnalyticsConsoleAsset requireAgentAsset(
            AnalyticsConsoleSubject subject,
            String assetId) {
        AnalyticsConsoleAsset asset = current(requireAsset(assetId));
        requireDesigner(subject);
        if (!canEdit(subject, asset)) {
            throw forbidden();
        }
        return asset;
    }

    private AnalyticsConsoleAsset current(AnalyticsConsoleAsset asset) {
        if (asset.status() != AnalyticsConsoleAssetStatus.DRAFT) {
            return asset;
        }
        try {
            String revision = bundleStore.resolve(new AnalyticsBundleRef(asset.bundleRef()))
                    .bundleRevision().value();
            return revision.equals(asset.bundleRevision())
                    ? asset
                    : asset.withRevision(revision, asset.updatedAt());
        } catch (AnalyticsBundleStoreException unavailable) {
            return asset;
        }
    }

    private AnalyticsConsoleAsset requireAsset(String assetId) {
        String expected = text(assetId, "assetId", 256);
        return catalog.read().assets().stream()
                .filter(asset -> asset.assetId().equals(expected))
                .findFirst()
                .orElseThrow(() -> error("ANALYTICS_CONSOLE_ASSET_NOT_FOUND",
                        "Analytics asset was not found"));
    }

    private void requireFolderAccess(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleCatalogState state,
            String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return;
        }
        AnalyticsConsoleFolder folder = state.folders().stream()
                .filter(value -> value.folderId().equals(folderId))
                .findFirst()
                .orElseThrow(() -> error("ANALYTICS_CONSOLE_FOLDER_NOT_FOUND",
                        "Analytics folder was not found"));
        if (!subject.hasRole(AnalyticsConsoleRole.ADMIN)
                && !folder.ownerSubjectRef().equals(subject.subjectRef())) {
            throw forbidden();
        }
    }

    private void replace(AnalyticsConsoleAsset updated) {
        catalog.update(state -> {
            List<AnalyticsConsoleAsset> assets = state.assets().stream()
                    .map(asset -> asset.assetId().equals(updated.assetId()) ? updated : asset)
                    .toList();
            return new AnalyticsConsoleCatalogState(
                    state.revision(), state.folders(), assets, state.conversations());
        });
    }

    private void describe(
            AnalyticsConsoleAssetKind kind,
            String bundleRef,
            String artifactRef,
            String revision) {
        requireSuccess(functions.describeArtifact(new AnalyticsArtifactFunctionRequest(
                bundleRef, kind.runtimeKind(), artifactRef, revision, context())));
    }

    private AnalyticsRenderFunctionRequest renderRequest(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleAsset asset,
            Map<String, Object> parameters,
            String timezone,
            String locale) {
        return new AnalyticsRenderFunctionRequest(
                asset.bundleRef(),
                asset.artifactRef(),
                asset.bundleRevision(),
                parameters == null ? Map.of() : parameters,
                timezone == null || timezone.isBlank() ? "UTC" : timezone,
                locale == null || locale.isBlank() ? "zh-CN" : locale,
                new AnalyticsFunctionAuthority(
                        subject.authorityProvider(), subject.authorityReference()),
                context());
    }

    private AnalyticsFunctionRequestContext context() {
        return new AnalyticsFunctionRequestContext(
                "console-" + UUID.randomUUID(), "console-" + UUID.randomUUID());
    }

    private static <T> T requireSuccess(AnalyticsFunctionEnvelope<T> outcome) {
        if (outcome == null) {
            throw error("ANALYTICS_CONSOLE_RUNTIME_UNAVAILABLE",
                    "Analytics Runtime did not return a result");
        }
        if (!outcome.success()) {
            throw error(outcome.error().code(), outcome.error().message());
        }
        return outcome.data();
    }

    private byte[] definitionBytes(String content) {
        if (content == null || content.isBlank()) {
            throw error("ANALYTICS_CONSOLE_DEFINITION_INVALID",
                    "Analytics definition content is required");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxDefinitionBytes) {
            throw error("ANALYTICS_CONSOLE_DEFINITION_TOO_LARGE",
                    "Analytics definition exceeds the configured size limit");
        }
        return bytes;
    }

    private static void exactRevision(AnalyticsConsoleAsset asset, String revision) {
        String expected = new AnalyticsBundleRevision(revision).value();
        if (!asset.bundleRevision().equals(expected)) {
            throw conflict();
        }
    }

    private static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.length() > maxLength) {
            throw error("ANALYTICS_CONSOLE_REQUEST_INVALID", field + " is invalid");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AnalyticsConsoleCatalogException conflict() {
        return error("ANALYTICS_CONSOLE_REVISION_CONFLICT",
                "Analytics Bundle revision changed; reload before saving");
    }

    private static AnalyticsConsoleCatalogException error(String code, String message) {
        return new AnalyticsConsoleCatalogException(code, message);
    }

    public record CreateDraft(
            String title,
            String description,
            String folderId,
            AnalyticsConsoleAssetKind kind,
            String bundleRef,
            String artifactRef,
            String expectedBundleRevision,
            String definitionContent) {
    }

    /** definitionContent is deliberately absent for read-only consumers. */
    public record AssetDetail(AnalyticsConsoleAsset asset, String definitionContent) {
    }
}
