package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePublishRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRecoverRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.dto.RuntimeCatalogState;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.StoredWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RuntimeAuthoringWorkspacePublishServiceTest {

    private static final String NAMESPACE = "sales";
    private static final String BUNDLE = "managed-sales";

    @TempDir
    Path tempDirectory;

    @Test
    void publishesExactValidatedRevisionAsImmutableBundleAndTerminalWorkspace()
            throws Exception {
        Fixture fixture = fixture("success");
        StoredWorkspace validated = validatedCandidate(fixture);
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(fixture, "catalog-published"));

        AuthoringWorkspaceInfo published = fixture.publications().publish(
                validated.workspaceId(), request(validated));

        assertThat(published.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(published.lastPublication().status()).isEqualTo("PUBLISHED");
        assertThat(published.lastPublication().candidateRevision())
                .isEqualTo(validated.candidateRevision());
        var record = fixture.registry().find(BUNDLE).orElseThrow();
        assertThat(record.immutablePublication()).isTrue();
        assertThat(record.watch()).isFalse();
        assertThat(record.artifactRevision())
                .isEqualTo(validated.candidateRevision());
        assertThat(Path.of(record.path()).resolve("shared/common.fsscript"))
                .hasContent("candidate-script");
        assertThat(fixture.liveBundle().getRootPath()).isEqualTo(record.path());

        assertCode(() -> fixture.store().replace(
                        validated.workspaceId(), validated.candidateRevision(),
                        Map.of("Order.tm", bytes("mutate"))),
                "WORKSPACE_STATE_INVALID");
        assertCode(() -> fixture.store().discard(
                        validated.workspaceId(), validated.candidateRevision()),
                "WORKSPACE_STATE_INVALID");
        assertCode(() -> fixture.workspaceService().validate(
                        validated.workspaceId(), validated.candidateRevision()),
                "WORKSPACE_STATE_INVALID");
        assertCode(() -> fixture.workspaceService().query(
                        validated.workspaceId(), "Order",
                        validated.candidateRevision(),
                        new SemanticQueryRequest(), null,
                        RuntimeCandidateQueryService.Phase.VALIDATE),
                "WORKSPACE_NOT_VALIDATED");
        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_NOT_VALIDATED");
        assertCode(() -> fixture.publications().recover(
                        validated.workspaceId(),
                        new AuthoringWorkspaceRecoverRequest(
                                validated.candidateRevision(),
                                published.lastPublication().attemptId())),
                "WORKSPACE_STATE_INVALID");
        assertThat(fixture.workspaceService().listResources(
                validated.workspaceId(), validated.candidateRevision())
                .resources()).isNotEmpty();
        assertThat(fixture.workspaceService().diff(
                validated.workspaceId(), validated.candidateRevision(), false)
                .candidateRevision()).isEqualTo(validated.candidateRevision());
        assertThat(fixture.workspaceService().get(validated.workspaceId())
                .lastValidation()).isNotNull();

        AuthoringWorkspaceInfo next = fixture.workspaceService().create(
                NAMESPACE, new AuthoringWorkspaceCreateRequest(null, BUNDLE));
        assertThat(next.baseBundleRevision())
                .isEqualTo(validated.candidateRevision());
        assertThat(next.baseBundleRevision()).isEqualTo(next.candidateRevision());
    }

    @Test
    void publishesASecondWorkspaceFromTheOwnedImmutablePublication()
            throws Exception {
        Fixture fixture = fixture("second-publication");
        StoredWorkspace firstCandidate = validatedCandidate(fixture);
        AtomicInteger refreshes = new AtomicInteger();
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(
                        fixture, "catalog-published-" + refreshes.incrementAndGet()));

        AuthoringWorkspaceInfo firstPublished = fixture.publications().publish(
                firstCandidate.workspaceId(), request(firstCandidate));
        var firstRecord = fixture.registry().find(BUNDLE).orElseThrow();
        Path firstArtifact = Path.of(firstRecord.path());
        byte[] firstScript = Files.readAllBytes(
                firstArtifact.resolve("shared/common.fsscript"));

        StoredWorkspace secondValidated = validatedCandidateFromCurrentSource(
                fixture, "second-script");

        AuthoringWorkspaceInfo secondPublished = fixture.publications().publish(
                secondValidated.workspaceId(), request(secondValidated));

        var secondRecord = fixture.registry().find(BUNDLE).orElseThrow();
        assertThat(firstPublished.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(secondValidated.baseBundleRevision())
                .isEqualTo(firstCandidate.candidateRevision());
        assertThat(secondPublished.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(secondRecord.artifactRevision())
                .isEqualTo(secondValidated.candidateRevision());
        assertThat(Path.of(secondRecord.path())).isNotEqualTo(firstArtifact);
        assertThat(firstArtifact).isDirectory();
        assertThat(firstArtifact.resolve("shared/common.fsscript"))
                .hasBinaryContent(firstScript);
        assertThat(Path.of(secondRecord.path()).resolve("shared/common.fsscript"))
                .hasContent("second-script");
        assertThat(refreshes).hasValue(2);
    }

    @Test
    void failedSecondPublicationRestoresTheExactImmutableBaseArtifact()
            throws Exception {
        Fixture fixture = fixture("second-publication-recovery");
        StoredWorkspace firstCandidate = validatedCandidate(fixture);
        AtomicInteger refreshes = new AtomicInteger();
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(
                        fixture, "catalog-first-" + refreshes.incrementAndGet()))
                .thenThrow(new IllegalStateException(
                        "injected second publication refresh failure"))
                .thenAnswer(invocation -> refresh(
                        fixture, "catalog-recovered-" + refreshes.incrementAndGet()));

        fixture.publications().publish(
                firstCandidate.workspaceId(), request(firstCandidate));
        var firstRecord = fixture.registry().find(BUNDLE).orElseThrow();
        Path firstArtifact = Path.of(firstRecord.path());
        StoredWorkspace secondCandidate = validatedCandidateFromCurrentSource(
                fixture, "second-script");

        assertCode(() -> fixture.publications().publish(
                        secondCandidate.workspaceId(), request(secondCandidate)),
                "WORKSPACE_PUBLISH_FAILED");

        StoredWorkspace recovered = fixture.store().get(
                secondCandidate.workspaceId());
        assertThat(recovered.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(recovered.lastPublication().status()).isEqualTo("RECOVERED");
        assertThat(fixture.registry().find(BUNDLE).orElseThrow())
                .isEqualTo(firstRecord);
        assertThat(fixture.liveBundle().getRootPath())
                .isEqualTo(firstArtifact.toString());
        assertThat(firstArtifact.resolve("shared/common.fsscript"))
                .hasContent("candidate-script");
        try (var artifacts = Files.list(fixture.publishedRoot().resolve("artifacts"))) {
            assertThat(artifacts.filter(Files::isDirectory).count()).isEqualTo(2);
        }
        assertThat(refreshes).hasValue(2);
    }

    @Test
    void tamperedImmutableBaseFailsBeforeNewArtifactAttemptOrLiveMutation()
            throws Exception {
        Fixture fixture = fixture("tampered-immutable-base");
        StoredWorkspace firstCandidate = validatedCandidate(fixture);
        AtomicInteger refreshes = new AtomicInteger();
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(
                        fixture, "catalog-published-" + refreshes.incrementAndGet()));

        fixture.publications().publish(
                firstCandidate.workspaceId(), request(firstCandidate));
        var firstRecord = fixture.registry().find(BUNDLE).orElseThrow();
        Path firstArtifact = Path.of(firstRecord.path());
        StoredWorkspace secondCandidate = validatedCandidateFromCurrentSource(
                fixture, "second-script");
        long artifactCount = entryCount(
                fixture.publishedRoot().resolve("artifacts"));
        long attemptCount = entryCount(
                fixture.publishedRoot().resolve("attempts"));
        String sourceRevision = fixture.sourceRegistry().currentRevision(NAMESPACE);

        Files.writeString(firstArtifact.resolve("shared/common.fsscript"),
                "tampered-script");

        assertCode(() -> fixture.publications().publish(
                        secondCandidate.workspaceId(), request(secondCandidate)),
                "WORKSPACE_ARTIFACT_CORRUPT");

        assertThat(entryCount(fixture.publishedRoot().resolve("artifacts")))
                .isEqualTo(artifactCount);
        assertThat(entryCount(fixture.publishedRoot().resolve("attempts")))
                .isEqualTo(attemptCount);
        assertThat(fixture.store().get(secondCandidate.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.VALIDATED);
        assertThat(fixture.registry().find(BUNDLE).orElseThrow())
                .isEqualTo(firstRecord);
        assertThat(fixture.liveBundle().getRootPath())
                .isEqualTo(firstArtifact.toString());
        assertThat(fixture.sourceRegistry().currentRevision(NAMESPACE))
                .isEqualTo(sourceRevision);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void refreshFailureAutomaticallyRestoresExactBaseSourceRegistryAndWorkspace()
            throws Exception {
        Fixture fixture = fixture("auto-recovery");
        StoredWorkspace validated = validatedCandidate(fixture);
        String basePath = fixture.livePath().toString();
        var baseRecord = fixture.registry().find(BUNDLE).orElseThrow();
        AtomicInteger refreshes = new AtomicInteger();
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> {
                    if (refreshes.incrementAndGet() == 1) {
                        throw new IllegalStateException("injected refresh failure");
                    }
                    return refresh(fixture, "catalog-recovered");
                });

        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_PUBLISH_FAILED");

        StoredWorkspace recovered = fixture.store().get(validated.workspaceId());
        assertThat(recovered.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(recovered.lastPublication().status()).isEqualTo("RECOVERED");
        assertThat(fixture.registry().find(BUNDLE).orElseThrow())
                .isEqualTo(baseRecord);
        assertThat(fixture.liveBundle().getRootPath()).isEqualTo(basePath);
        assertThat(Files.readString(
                fixture.livePath().resolve("shared/common.fsscript")))
                .isEqualTo("base-script");
        assertThat(refreshes).hasValue(2);
    }

    @Test
    void failedAutomaticRecoveryRequiresPinnedExplicitRecoveryAndIsIdempotent()
            throws Exception {
        Fixture fixture = fixture("explicit-recovery");
        StoredWorkspace validated = validatedCandidate(fixture);
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenThrow(new IllegalStateException("publish refresh failed"))
                .thenThrow(new IllegalStateException("recovery refresh failed"));

        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_RECOVERY_REQUIRED");
        StoredWorkspace required = fixture.store().get(validated.workspaceId());
        assertThat(required.state())
                .isEqualTo(AuthoringWorkspaceState.RECOVERY_REQUIRED);
        assertThat(required.lastPublication().status())
                .isEqualTo("RECOVERY_REQUIRED");

        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(fixture, "catalog-explicit"));
        AuthoringWorkspaceRecoverRequest recoveryRequest =
                new AuthoringWorkspaceRecoverRequest(
                        validated.candidateRevision(),
                        required.lastPublication().attemptId());
        AuthoringWorkspaceInfo recovered = fixture.publications().recover(
                validated.workspaceId(), recoveryRequest);
        AuthoringWorkspaceInfo repeated = fixture.publications().recover(
                validated.workspaceId(), recoveryRequest);

        assertThat(recovered.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(recovered.lastPublication().status()).isEqualTo("RECOVERED");
        assertThat(repeated).isEqualTo(recovered);
        assertThat(fixture.registry().find(BUNDLE).orElseThrow().path())
                .isEqualTo(fixture.livePath().toString());
    }

    @Test
    void identityMismatchFailsBeforeArtifactOrLiveMutation() throws Exception {
        Fixture fixture = fixture("preflight");
        StoredWorkspace validated = validatedCandidate(fixture);
        String basePath = fixture.liveBundle().getRootPath();
        String sourceRevision = fixture.sourceRegistry().currentRevision(NAMESPACE);

        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(),
                        new AuthoringWorkspacePublishRequest(
                                validated.candidateRevision(),
                                validated.baseBundleRevision(),
                                "source:wrong")),
                "WORKSPACE_REVISION_CONFLICT");

        assertThat(fixture.store().get(validated.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.VALIDATED);
        assertThat(fixture.publishedRoot()).doesNotExist();
        assertThat(fixture.liveBundle().getRootPath()).isEqualTo(basePath);
        assertThat(fixture.sourceRegistry().currentRevision(NAMESPACE))
                .isEqualTo(sourceRevision);
    }

    @Test
    void finalWorkspaceEvidenceFailureAutomaticallyRestoresBase()
            throws Exception {
        Fixture fixture = fixture("final-evidence-failure");
        StoredWorkspace validated = validatedCandidate(fixture);
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(
                        fixture, "catalog-" + System.nanoTime()));
        doThrow(new RuntimeAuthoringWorkspaceException(
                "WORKSPACE_STORE_FAILURE", "workspaces.publish.commit",
                "Injected final evidence persistence failure.", null, false))
                .when(fixture.store()).markPublished(
                        eq(validated.workspaceId()), anyString(), any());

        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_PUBLISH_FAILED");

        StoredWorkspace recovered = fixture.store().get(validated.workspaceId());
        assertThat(recovered.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(recovered.lastPublication().status()).isEqualTo("RECOVERED");
        assertThat(fixture.registry().find(BUNDLE).orElseThrow().path())
                .isEqualTo(fixture.livePath().toString());
        assertThat(fixture.liveBundle().getRootPath())
                .isEqualTo(fixture.livePath().toString());
    }

    @Test
    void registryPersistenceFailureAutomaticallyRestoresBase()
            throws Exception {
        Fixture fixture = fixture("registry-failure");
        StoredWorkspace validated = validatedCandidate(fixture);
        doThrow(new IllegalStateException("injected registry failure"))
                .when(fixture.registry()).save(argThat(
                        RuntimeBundleRegistryService.RuntimeBundleRecord
                                ::immutablePublication));
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> refresh(fixture, "catalog-recovered"));

        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_PUBLISH_FAILED");

        assertThat(fixture.store().get(validated.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(fixture.registry().find(BUNDLE).orElseThrow().path())
                .isEqualTo(fixture.livePath().toString());
        assertThat(fixture.liveBundle().getRootPath())
                .isEqualTo(fixture.livePath().toString());
    }

    @Test
    void explicitRecoveryRejectsThirdPartyLiveSourceDriftWithoutOverwrite()
            throws Exception {
        Fixture fixture = fixture("third-party-drift");
        StoredWorkspace validated = validatedCandidate(fixture);
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenThrow(new IllegalStateException("publish refresh failed"))
                .thenThrow(new IllegalStateException("recovery refresh failed"));
        assertCode(() -> fixture.publications().publish(
                        validated.workspaceId(), request(validated)),
                "WORKSPACE_RECOVERY_REQUIRED");
        StoredWorkspace required = fixture.store().get(validated.workspaceId());
        Path thirdParty = Files.createDirectories(
                tempDirectory.resolve("third-party-live"));
        Path sentinel = Files.writeString(
                thirdParty.resolve("Order.tm"), "third-party");
        setSource(fixture.liveBundle(), thirdParty.toString(), false);

        assertUnsafeCode(() -> fixture.publications().recover(
                        validated.workspaceId(),
                        new AuthoringWorkspaceRecoverRequest(
                                validated.candidateRevision(),
                                required.lastPublication().attemptId())),
                "WORKSPACE_RECOVERY_CONFLICT");

        assertThat(fixture.store().get(validated.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.RECOVERY_REQUIRED);
        assertThat(fixture.liveBundle().getRootPath())
                .isEqualTo(thirdParty.toString());
        assertThat(sentinel).hasContent("third-party");
        assertThat(fixture.registry().find(BUNDLE).orElseThrow().path())
                .isEqualTo(fixture.livePath().toString());
    }

    @Test
    void concurrentPublishHasExactlyOneLinearizedWinner() throws Exception {
        Fixture fixture = fixture("concurrent-publish");
        StoredWorkspace validated = validatedCandidate(fixture);
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger refreshes = new AtomicInteger();
        when(fixture.modelOperations().refreshModels(any(), eq(NAMESPACE)))
                .thenAnswer(invocation -> {
                    refreshes.incrementAndGet();
                    refreshEntered.countDown();
                    assertThat(releaseRefresh.await(5, TimeUnit.SECONDS)).isTrue();
                    return refresh(fixture, "catalog-published");
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> publishResult(
                    fixture, validated));
            assertThat(refreshEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> {
                secondStarted.countDown();
                return publishResult(fixture, validated);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();
            releaseRefresh.countDown();

            assertThat(Set.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            "PUBLISHED", "WORKSPACE_NOT_VALIDATED");
            assertThat(refreshes).hasValue(1);
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture fixture(String name) throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve(name));
        Path livePath = Files.createDirectories(root.resolve("live"));
        Files.writeString(livePath.resolve("Order.tm"), "base-tm");
        Files.writeString(livePath.resolve("Order.qm"), "base-qm");
        Files.createDirectories(livePath.resolve("shared"));
        Files.writeString(livePath.resolve("shared/common.fsscript"),
                "base-script");

        SystemBundlesContext context = mock(SystemBundlesContext.class);
        ExternalFileBundle live = new ExternalFileBundle(context);
        live.setName(BUNDLE);
        setSource(live, livePath.toString(), false);
        when(context.getBundleList()).thenReturn(List.of(live));
        when(context.getBundleByName(BUNDLE, false)).thenReturn(live);
        when(context.containBundle(BUNDLE)).thenReturn(true);
        when(context.getBundleDefinitionByName(BUNDLE))
                .thenAnswer(invocation -> live.getDefinition());
        when(context.listExternalBundles())
                .thenAnswer(invocation -> List.of(live.getDefinition()));

        CommittedSourceRevisionRegistry sourceRegistry =
                new CommittedSourceRevisionRegistry();
        when(context.replaceExternalBundle(
                eq(BUNDLE), eq(NAMESPACE), any(String.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(2);
                    boolean watch = invocation.getArgument(3);
                    setSource(live, path, watch);
                    sourceRegistry.commitKnown(List.of(NAMESPACE), () -> true);
                    return true;
                });

        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(
                root.resolve("runtime-bundles.json").toString());
        properties.getAuthoringWorkspaces().setPath(
                root.resolve("workspaces").toString());
        Path publishedRoot = root.resolve("published");
        properties.getAuthoringWorkspaces().setPublishedBundlesPath(
                publishedRoot.toString());
        ObjectMapper mapper = new ObjectMapper();
        RuntimeBundleRegistryService baseRegistry =
                new RuntimeBundleRegistryService(properties, context, mapper);
        baseRegistry.save(baseRegistry.newRecord(
                BUNDLE, NAMESPACE, livePath.toString(), false, true));
        RuntimeBundleRegistryService registry = spy(baseRegistry);
        RuntimeAuthoringWorkspaceStore store = spy(
                new RuntimeAuthoringWorkspaceStore(properties, mapper));
        RuntimeBundleInventoryService inventory =
                new RuntimeBundleInventoryService(context, registry);
        RuntimeAuthoringWorkspaceService workspaceService =
                new RuntimeAuthoringWorkspaceService(
                        store, inventory, context, provider(sourceRegistry),
                        provider(mock(DetachedModelValidationFactory.class)),
                        provider(mock(RuntimeCandidateQueryService.class)));
        RuntimePublishedBundleArtifactStore artifactStore =
                new RuntimePublishedBundleArtifactStore(
                        properties, mapper, null, registry);
        RuntimeModelOperations modelOperations = mock(RuntimeModelOperations.class);
        RuntimeAuthoringWorkspacePublicationService publications =
                new RuntimeAuthoringWorkspacePublicationService(
                        store, workspaceService, artifactStore, registry,
                        context, modelOperations,
                        new RuntimeAuthoringPublicationLock(),
                        provider(sourceRegistry),
                        provider((CatalogSnapshotStore) null));
        return new Fixture(store, workspaceService, publications, registry,
                modelOperations, sourceRegistry, live, livePath, publishedRoot);
    }

    private static StoredWorkspace validatedCandidate(Fixture fixture) {
        AuthoringWorkspaceInfo created = fixture.workspaceService().create(
                NAMESPACE, new AuthoringWorkspaceCreateRequest(null, BUNDLE));
        Map<String, byte[]> candidate = new LinkedHashMap<>(
                fixture.store().snapshot(
                        created.workspaceId(), created.candidateRevision()));
        candidate.put("shared/common.fsscript", bytes("candidate-script"));
        StoredWorkspace updated = fixture.store().replace(
                created.workspaceId(), created.candidateRevision(), candidate);
        String now = Instant.now().toString();
        return fixture.store().recordValidation(
                updated.workspaceId(), updated.candidateRevision(),
                new AuthoringWorkspaceInfo.ValidationEvidence(
                        true, updated.candidateRevision(),
                        updated.baseBundleRevision(), updated.baseSourceRevision(),
                        now, candidate.size(), candidate.size(), 0, 0, List.of()));
    }

    private static StoredWorkspace validatedCandidateFromCurrentSource(
            Fixture fixture,
            String script
    ) {
        AuthoringWorkspaceInfo created = fixture.workspaceService().create(
                NAMESPACE, new AuthoringWorkspaceCreateRequest(null, BUNDLE));
        Map<String, byte[]> candidate = new LinkedHashMap<>(
                fixture.store().snapshot(
                        created.workspaceId(), created.candidateRevision()));
        candidate.put("shared/common.fsscript", bytes(script));
        StoredWorkspace updated = fixture.store().replace(
                created.workspaceId(), created.candidateRevision(), candidate);
        String now = Instant.now().toString();
        return fixture.store().recordValidation(
                updated.workspaceId(), updated.candidateRevision(),
                new AuthoringWorkspaceInfo.ValidationEvidence(
                        true, updated.candidateRevision(),
                        updated.baseBundleRevision(), updated.baseSourceRevision(),
                        now, candidate.size(), candidate.size(), 0, 0, List.of()));
    }

    private static AuthoringWorkspacePublishRequest request(
            StoredWorkspace workspace
    ) {
        return new AuthoringWorkspacePublishRequest(
                workspace.candidateRevision(), workspace.baseBundleRevision(),
                workspace.baseSourceRevision());
    }

    private static ModelRefreshResponse refresh(
            Fixture fixture,
            String generation
    ) {
        return new ModelRefreshResponse(
                NAMESPACE, "namespace", List.of(), List.of("Order"),
                1, 0, List.of(), List.of(), "catalog-before", generation,
                fixture.sourceRegistry().currentRevision(NAMESPACE),
                List.of(), 1, 0, 1L, RuntimeCatalogState.ACTIVE);
    }

    private static void setSource(
            ExternalFileBundle bundle,
            String path,
            boolean watch
    ) {
        bundle.setBundleDefinition(new ExternalBundleDefinition(
                BUNDLE, NAMESPACE, path, watch));
        bundle.setBasePath(path);
        bundle.setRootPath(path);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static long entryCount(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries.count();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo(code));
    }

    private static void assertUnsafeCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> {
                    RuntimeAuthoringWorkspaceException typed =
                            (RuntimeAuthoringWorkspaceException) error;
                    assertThat(typed.code()).isEqualTo(code);
                    assertThat(typed.safeToAutoRepair()).isFalse();
                });
    }

    private static String publishResult(
            Fixture fixture,
            StoredWorkspace workspace
    ) {
        try {
            return fixture.publications().publish(
                    workspace.workspaceId(), request(workspace)).state().name();
        } catch (RuntimeAuthoringWorkspaceException failure) {
            return failure.code();
        }
    }

    private record Fixture(
            RuntimeAuthoringWorkspaceStore store,
            RuntimeAuthoringWorkspaceService workspaceService,
            RuntimeAuthoringWorkspacePublicationService publications,
            RuntimeBundleRegistryService registry,
            RuntimeModelOperations modelOperations,
            CommittedSourceRevisionRegistry sourceRegistry,
            ExternalFileBundle liveBundle,
            Path livePath,
            Path publishedRoot
    ) {
    }
}
