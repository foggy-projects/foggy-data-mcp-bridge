package com.foggyframework.analytics.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.FileAnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetKind;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetStatus;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsConsoleServiceTest {

    private static final String REVISION_1 = "sha256:" + "1".repeat(64);
    private static final String REVISION_2 = "sha256:" + "2".repeat(64);

    @TempDir
    Path tempDir;

    private FileAnalyticsConsoleCatalogRepository catalog;
    private AnalyticsBundleStore bundles;
    private AnalyticsFunctionClient functions;
    private AtomicReference<String> currentRevision;
    private AnalyticsConsoleService service;

    @BeforeEach
    void setUp() {
        catalog = new FileAnalyticsConsoleCatalogRepository(
                tempDir.resolve("catalog.json"), new ObjectMapper());
        bundles = mock(AnalyticsBundleStore.class);
        functions = mock(AnalyticsFunctionClient.class);
        currentRevision = new AtomicReference<>(REVISION_1);
        when(bundles.resolve(any())).thenAnswer(ignored -> resolved(currentRevision.get()));
        when(bundles.saveArtifact(any(), any(), anyString(), any())).thenAnswer(ignored -> {
            currentRevision.set(REVISION_2);
            return resolved(REVISION_2);
        });
        AnalyticsFunctionEnvelope<AnalyticsBundleDescription> bundleSuccess =
                success(mock(AnalyticsBundleDescription.class));
        AnalyticsFunctionEnvelope<AnalyticsArtifactDescription> artifactSuccess =
                success(mock(AnalyticsArtifactDescription.class));
        when(functions.validateBundle(any())).thenReturn(bundleSuccess);
        when(functions.describeArtifact(any())).thenReturn(artifactSuccess);
        service = new AnalyticsConsoleService(
                catalog,
                bundles,
                functions,
                1_048_576,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void designerSaveValidateAndPublishUsesExactCurrentRevision() {
        AnalyticsConsoleSubject designer = subject("designer", AnalyticsConsoleRole.DESIGNER);
        AnalyticsConsoleAsset draft = asset("designer", AnalyticsConsoleAssetStatus.DRAFT);
        seed(draft);

        AnalyticsConsoleAsset saved = service.saveDefinition(
                designer, draft.assetId(), REVISION_1, "{\"artifactRef\":\"sales\"}");
        assertThat(saved.bundleRevision()).isEqualTo(REVISION_2);
        assertThat(saved.validatedBundleRevision()).isNull();
        assertThat(catalog.read().assets().get(0).bundleRevision()).isEqualTo(REVISION_2);

        AnalyticsConsoleAsset validated = service.validate(
                designer, draft.assetId(), REVISION_2);
        assertThat(validated.validatedBundleRevision()).isEqualTo(REVISION_2);

        AnalyticsConsoleAsset published = service.publish(
                designer, draft.assetId(), REVISION_2);
        assertThat(published.status()).isEqualTo(AnalyticsConsoleAssetStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
    }

    @Test
    void viewerCannotReadDraftButCanReadConsoleVisiblePublication() {
        AnalyticsConsoleSubject viewer = subject("viewer", AnalyticsConsoleRole.VIEWER);
        AnalyticsConsoleAsset draft = asset("designer", AnalyticsConsoleAssetStatus.DRAFT);
        seed(draft);
        assertThat(service.assets(viewer)).isEmpty();

        AnalyticsConsoleAsset published = new AnalyticsConsoleAsset(
                draft.assetId(), draft.title(), draft.description(), null,
                draft.ownerSubjectRef(), draft.kind(), draft.bundleRef(), draft.artifactRef(),
                draft.resourcePath(), REVISION_1, REVISION_1,
                AnalyticsConsoleAssetStatus.PUBLISHED, AnalyticsConsoleVisibility.CONSOLE,
                Set.of(), draft.createdAt(), draft.updatedAt(), draft.updatedAt());
        replace(published);

        assertThat(service.assets(viewer)).extracting(AnalyticsConsoleAsset::assetId)
                .containsExactly(draft.assetId());
        assertThat(service.asset(viewer, draft.assetId()).definitionContent()).isNull();
        verify(bundles, never()).readArtifact(any(), any(), anyString());
        assertThatThrownBy(() -> service.saveDefinition(
                viewer, draft.assetId(), REVISION_1, "{}"))
                .hasMessageContaining("forbidden");
    }

    @Test
    void publicationRequiresValidationOfCurrentRevision() {
        AnalyticsConsoleSubject designer = subject("designer", AnalyticsConsoleRole.DESIGNER);
        AnalyticsConsoleAsset draft = asset("designer", AnalyticsConsoleAssetStatus.DRAFT);
        seed(draft);

        assertThatThrownBy(() -> service.publish(designer, draft.assetId(), REVISION_1))
                .hasMessageContaining("validated");
    }

    @Test
    void createsAnIncompleteDefinitionAsAConsoleDraftBeforeValidation() {
        AnalyticsConsoleSubject designer = subject("designer", AnalyticsConsoleRole.DESIGNER);

        AnalyticsConsoleAsset created = service.createDraft(
                designer,
                new AnalyticsConsoleService.CreateDraft(
                        "销售草稿", "", null, AnalyticsConsoleAssetKind.REPORT,
                        "sales-bundle", "sales", REVISION_1, "{\"incomplete\":true}"));

        assertThat(created.status()).isEqualTo(AnalyticsConsoleAssetStatus.DRAFT);
        assertThat(created.bundleRevision()).isEqualTo(REVISION_2);
        assertThat(catalog.read().assets()).containsExactly(created);
        verify(functions, never()).describeArtifact(any());
    }

    private void seed(AnalyticsConsoleAsset asset) {
        catalog.update(state -> new AnalyticsConsoleCatalogState(
                state.revision(), state.folders(), List.of(asset), state.conversations()));
    }

    private void replace(AnalyticsConsoleAsset replacement) {
        catalog.update(state -> {
            var assets = new ArrayList<>(state.assets());
            assets.set(0, replacement);
            return new AnalyticsConsoleCatalogState(
                    state.revision(), state.folders(), assets, state.conversations());
        });
    }

    private static AnalyticsConsoleAsset asset(
            String owner,
            AnalyticsConsoleAssetStatus status) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        return new AnalyticsConsoleAsset(
                "analytics-sales", "销售概览", "", null, owner,
                AnalyticsConsoleAssetKind.REPORT, "sales-bundle", "sales",
                "reports/sales.report.json", REVISION_1, null, status,
                AnalyticsConsoleVisibility.PRIVATE, Set.of(), now, now,
                status == AnalyticsConsoleAssetStatus.PUBLISHED ? now : null);
    }

    private static AnalyticsConsoleSubject subject(
            String subjectRef,
            AnalyticsConsoleRole role) {
        return new AnalyticsConsoleSubject(
                subjectRef, subjectRef, Set.of(role), "console", "authority-" + subjectRef);
    }

    private static ResolvedAnalyticsBundle resolved(String revision) {
        ResolvedAnalyticsBundle resolved = mock(ResolvedAnalyticsBundle.class);
        AnalyticsBundleLifecycle lifecycle = mock(AnalyticsBundleLifecycle.class);
        when(resolved.bundleRevision()).thenReturn(new AnalyticsBundleRevision(revision));
        when(resolved.lifecycle()).thenReturn(lifecycle);
        when(lifecycle.isWritable()).thenReturn(true);
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static <T> AnalyticsFunctionEnvelope<T> success(T data) {
        AnalyticsFunctionEnvelope<T> envelope = mock(AnalyticsFunctionEnvelope.class);
        when(envelope.success()).thenReturn(true);
        when(envelope.data()).thenReturn(data);
        return envelope;
    }
}
