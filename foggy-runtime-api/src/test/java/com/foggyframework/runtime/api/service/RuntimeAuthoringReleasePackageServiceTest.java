package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringReleaseExportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleaseImportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleasePackage;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.StoredWorkspace;
import com.foggyframework.runtime.api.service.RuntimeBundleInventoryService.WorkspaceSource;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeAuthoringReleasePackageServiceTest {

    private static final String NAMESPACE = "sales";
    private static final String BUNDLE = "managed-sales";

    @TempDir
    Path tempDirectory;

    @Test
    void exportsDeterministicIdentityAndRejectsTamperedPackageBeforeMutation()
            throws Exception {
        Fixture fixture = fixture(true);
        StoredWorkspace validated = validatedCandidate(fixture);

        AuthoringReleasePackage first = fixture.releases().exportPackage(
                validated.workspaceId(),
                new AuthoringReleaseExportRequest(validated.candidateRevision()));
        AuthoringReleasePackage second = fixture.releases().exportPackage(
                validated.workspaceId(),
                new AuthoringReleaseExportRequest(validated.candidateRevision()));

        assertThat(first.packageId()).isEqualTo(second.packageId());
        assertThat(first.packageId()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.exportedAt()).isNotNull();
        assertThat(first.resources()).extracting(
                AuthoringReleasePackage.Resource::path)
                .containsExactly("model/Order.tm");
        assertThat(fixture.releases().validatePackage(first).snapshot())
                .containsOnlyKeys("model/Order.tm");

        AuthoringReleasePackage.Resource original = first.resources().get(0);
        AuthoringReleasePackage tampered = copy(first, List.of(
                new AuthoringReleasePackage.Resource(
                        original.path(), original.type(), original.size(),
                        original.sha256(), original.content() + " tampered")));
        int before = fixture.store().list(null, null, true).size();

        assertCode(() -> fixture.releases().importPackage(
                        NAMESPACE,
                        new AuthoringReleaseImportRequest(
                                NAMESPACE, BUNDLE, tampered)),
                "WORKSPACE_RELEASE_PACKAGE_INVALID");

        assertThat(fixture.store().list(null, null, true)).hasSize(before);
    }

    @Test
    void importsImmutableCandidateWithoutTrustingDevelopmentValidationAndSurvivesRestart()
            throws Exception {
        Fixture fixture = fixture(true);
        StoredWorkspace validated = validatedCandidate(fixture);
        AuthoringReleasePackage release = fixture.releases().exportPackage(
                validated.workspaceId(),
                new AuthoringReleaseExportRequest(validated.candidateRevision()));

        AuthoringWorkspaceInfo imported = fixture.releases().importPackage(
                NAMESPACE,
                new AuthoringReleaseImportRequest(NAMESPACE, BUNDLE, release));

        assertThat(imported.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
        assertThat(imported.lastValidation()).isNull();
        assertThat(imported.releaseImport().packageId())
                .isEqualTo(release.packageId());
        assertThat(imported.baseBundleRevision())
                .isEqualTo(CandidateContentRevision.calculate(fixture.base()));
        assertThat(imported.candidateRevision())
                .isEqualTo(release.candidateRevision());
        assertCode(() -> fixture.store().replace(
                        imported.workspaceId(), imported.candidateRevision(),
                        Map.of("model/Order.tm", bytes("changed"))),
                "WORKSPACE_RELEASE_IMMUTABLE");
        assertCode(() -> fixture.workspaces().save(
                        imported.workspaceId(),
                        new com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest(
                                imported.candidateRevision(), List.of(
                                new com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "model/Order.tm", "changed")))),
                "WORKSPACE_RELEASE_IMMUTABLE");

        RuntimeAuthoringWorkspaceStore restarted = new RuntimeAuthoringWorkspaceStore(
                fixture.properties(), new ObjectMapper());
        StoredWorkspace loaded = restarted.get(imported.workspaceId());
        assertThat(loaded.releaseImport()).isEqualTo(imported.releaseImport());
        assertThat(restarted.snapshot(
                imported.workspaceId(), imported.candidateRevision()))
                .containsOnlyKeys("model/Order.tm");
    }

    @Test
    void promotionDisabledRejectsImportWithoutCreatingWorkspace()
            throws Exception {
        Fixture enabled = fixture(true);
        StoredWorkspace validated = validatedCandidate(enabled);
        AuthoringReleasePackage release = enabled.releases().exportPackage(
                validated.workspaceId(),
                new AuthoringReleaseExportRequest(validated.candidateRevision()));
        Fixture disabled = fixture(false);

        assertCode(() -> disabled.releases().importPackage(
                        NAMESPACE,
                        new AuthoringReleaseImportRequest(NAMESPACE, BUNDLE, release)),
                "WORKSPACE_PRODUCTION_PROMOTION_DISABLED");

        assertThat(disabled.store().list(null, null, true)).isEmpty();
    }

    @Test
    void rejectsDuplicateCaseCollidingAndUnsupportedResourcePaths()
            throws Exception {
        Fixture fixture = fixture(true);
        StoredWorkspace validated = validatedCandidate(fixture);
        AuthoringReleasePackage release = fixture.releases().exportPackage(
                validated.workspaceId(),
                new AuthoringReleaseExportRequest(validated.candidateRevision()));
        AuthoringReleasePackage.Resource resource = release.resources().get(0);
        List<AuthoringReleasePackage.Resource> collision = new ArrayList<>();
        collision.add(resource);
        collision.add(new AuthoringReleasePackage.Resource(
                "model/order.tm", resource.type(), resource.size(),
                resource.sha256(), resource.content()));

        assertCode(() -> fixture.releases().validatePackage(
                        copy(release, collision)),
                "WORKSPACE_RELEASE_PACKAGE_INVALID");
        assertCode(() -> fixture.releases().validatePackage(
                        copy(release, List.of(new AuthoringReleasePackage.Resource(
                                "../Order.tm", resource.type(), resource.size(),
                                resource.sha256(), resource.content())))),
                "WORKSPACE_RESOURCE_PATH_INVALID");
        assertCode(() -> fixture.releases().validatePackage(
                        copy(release, List.of(new AuthoringReleasePackage.Resource(
                                "model/Order.txt", "TM", resource.size(),
                                resource.sha256(), resource.content())))),
                "WORKSPACE_RESOURCE_TYPE_UNSUPPORTED");
        assertCode(() -> fixture.releases().validatePackage(
                        copy(release, List.of(new AuthoringReleasePackage.Resource(
                                "model/Order.tm", "TM", 1,
                                resource.sha256(),
                                String.valueOf((char) 0xD800))))),
                "WORKSPACE_RELEASE_PACKAGE_INVALID");

        fixture.properties().getAuthoringWorkspaces()
                .setMaxResourceBytes(4);
        assertCode(() -> fixture.releases().validatePackage(release),
                "WORKSPACE_RELEASE_PACKAGE_INVALID");
    }

    private StoredWorkspace validatedCandidate(Fixture fixture) {
        String sourceRevision = fixture.sourceRegistry().currentRevision(NAMESPACE);
        StoredWorkspace created = fixture.store().create(
                NAMESPACE, BUNDLE, sourceRevision, fixture.sourceIdentity(),
                fixture.base());
        StoredWorkspace candidate = fixture.store().replace(
                created.workspaceId(), created.candidateRevision(),
                Map.of("model/Order.tm", bytes("candidate model")));
        return fixture.store().recordValidation(
                candidate.workspaceId(), candidate.candidateRevision(),
                new AuthoringWorkspaceInfo.ValidationEvidence(
                        true, candidate.candidateRevision(),
                        candidate.baseBundleRevision(),
                        candidate.baseSourceRevision(), Instant.now().toString(),
                        1, 1, 0, 0, List.of()));
    }

    private Fixture fixture(boolean promotionEnabled) throws Exception {
        Path suffix = tempDirectory.resolve("fixture-" + promotionEnabled
                + "-" + System.nanoTime());
        Path storeRoot = suffix.resolve("store");
        Path liveRoot = Files.createDirectories(suffix.resolve("live/model"))
                .getParent();
        Map<String, byte[]> base = Map.of(
                "model/Order.tm", bytes("production base"));
        Files.write(liveRoot.resolve("model/Order.tm"),
                base.get("model/Order.tm"));
        String sourceIdentity = "sha256:" + "1".repeat(64);

        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        properties.getAuthoringWorkspaces().setProductionPromotionEnabled(
                promotionEnabled);
        RuntimeAuthoringWorkspaceStore store =
                new RuntimeAuthoringWorkspaceStore(properties, new ObjectMapper());
        RuntimeBundleInventoryService inventory =
                mock(RuntimeBundleInventoryService.class);
        RuntimeBundleRecord record = new RuntimeBundleRecord(
                BUNDLE, NAMESPACE, liveRoot.toString(), false, true,
                "created", "updated");
        WorkspaceSource source = new WorkspaceSource(
                record, mock(ExternalFileBundle.class), liveRoot, sourceIdentity);
        when(inventory.requireWorkspaceSource(
                eq(BUNDLE), eq(NAMESPACE), anyString())).thenReturn(source);
        when(inventory.list()).thenReturn(List.of());
        SystemBundlesContext bundles = mock(SystemBundlesContext.class);
        when(bundles.getBundleList()).thenReturn(List.of());
        CommittedSourceRevisionRegistry sourceRegistry =
                new CommittedSourceRevisionRegistry();
        RuntimeAuthoringWorkspaceService workspaces =
                new RuntimeAuthoringWorkspaceService(
                        store, inventory, bundles, provider(sourceRegistry),
                        provider(mock(DetachedModelValidationFactory.class)),
                        provider(mock(RuntimeCandidateQueryService.class)));
        RuntimeAuthoringReleasePackageService releases =
                new RuntimeAuthoringReleasePackageService(
                        properties, workspaces, store, inventory);
        return new Fixture(properties, store, workspaces, releases,
                sourceRegistry, sourceIdentity, base);
    }

    private static AuthoringReleasePackage copy(
            AuthoringReleasePackage source,
            List<AuthoringReleasePackage.Resource> resources
    ) {
        return new AuthoringReleasePackage(
                source.formatVersion(), source.packageId(),
                source.sourceRuntimeApiVersion(), source.sourceNamespace(),
                source.sourceBundle(), source.candidateRevision(),
                source.baseBundleRevision(),
                source.baseNamespaceSourceRevision(), source.exportedAt(),
                source.validation(), source.dependencies(), resources);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo(code));
    }

    private record Fixture(
            FoggyRuntimeApiProperties properties,
            RuntimeAuthoringWorkspaceStore store,
            RuntimeAuthoringWorkspaceService workspaces,
            RuntimeAuthoringReleasePackageService releases,
            CommittedSourceRevisionRegistry sourceRegistry,
            String sourceIdentity,
            Map<String, byte[]> base
    ) {
    }
}
