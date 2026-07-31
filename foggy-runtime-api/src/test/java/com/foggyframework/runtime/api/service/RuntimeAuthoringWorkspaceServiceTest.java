package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.validation.DetachedModelValidationSession;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDeleteRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceQueryResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.dto.BundleInfo;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimeBundleInventoryService.WorkspaceSource;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.Phase;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryCommand;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeAuthoringWorkspaceServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsImmutableSnapshotAndAppliesAtomicSaveDeleteAndDiff()
            throws Exception {
        Fixture fixture = fixture("resource Order = {};", "query Order = {};");
        var created = fixture.service().create("sales",
                new AuthoringWorkspaceCreateRequest(null, "managed-sales"));

        assertThat(created.workspaceId()).matches("[A-Za-z0-9_-]{32}");
        assertThat(created.baseBundleRevision())
                .isEqualTo(created.candidateRevision());
        assertThat(created.baseNamespaceSourceRevision())
                .isEqualTo(fixture.sourceRegistry().currentRevision("sales"));
        assertThat(created.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
        assertThat(fixture.service().listResources(
                created.workspaceId(), created.candidateRevision()).resources())
                .extracting(resource -> resource.path())
                .containsExactly("model/Order.tm", "query/Order.qm",
                        "shared/common.fsscript");

        var saved = fixture.service().save(created.workspaceId(),
                new AuthoringWorkspaceSaveRequest(
                        created.candidateRevision(),
                        List.of(
                                new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "query/Order.qm", "candidate query"),
                                new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "model/NewOrder.tm", "candidate tm"))));
        assertThat(saved.candidateRevision())
                .isNotEqualTo(created.candidateRevision());
        assertThat(fixture.service().readResource(
                saved.workspaceId(), saved.candidateRevision(),
                "query/Order.qm").content()).isEqualTo("candidate query");

        assertCode(() -> fixture.service().delete(
                        saved.workspaceId(),
                        new AuthoringWorkspaceDeleteRequest(
                                saved.candidateRevision(),
                                List.of("model/NewOrder.tm", "missing.tm"))),
                "WORKSPACE_RESOURCE_NOT_FOUND");
        assertThat(fixture.service().readResource(
                saved.workspaceId(), saved.candidateRevision(),
                "model/NewOrder.tm").content()).isEqualTo("candidate tm");

        var deleted = fixture.service().delete(saved.workspaceId(),
                new AuthoringWorkspaceDeleteRequest(
                        saved.candidateRevision(),
                        List.of("model/Order.tm")));
        assertThat(fixture.service().diff(
                deleted.workspaceId(), deleted.candidateRevision(), false)
                .changes()).extracting(change -> change.path())
                .containsExactly("model/NewOrder.tm", "model/Order.tm",
                        "query/Order.qm");
    }

    @Test
    void marksWorkspaceStaleOnContentRevisionOrIdentityDriftButKeepsDraftReadable()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));

        Files.writeString(fixture.livePath().resolve("model/Order.tm"),
                "changed outside Runtime mutation");
        var stale = fixture.service().get(created.workspaceId());

        assertThat(stale.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertCode(() -> fixture.service().validate(
                        stale.workspaceId(), stale.candidateRevision()),
                "WORKSPACE_STALE");
        var edited = fixture.service().save(stale.workspaceId(),
                new AuthoringWorkspaceSaveRequest(
                        stale.candidateRevision(),
                        List.of(new AuthoringWorkspaceSaveRequest.ResourceFile(
                                "model/Order.tm", "retained stale draft"))));
        assertThat(edited.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(fixture.service().readResource(
                edited.workspaceId(), edited.candidateRevision(),
                "model/Order.tm").content())
                .isEqualTo("retained stale draft");

        Files.writeString(fixture.livePath().resolve("model/Order.tm"),
                "base tm");
        var committedRevisionWorkspace = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        fixture.sourceRegistry().commitKnown(List.of("sales"), () -> true);
        assertThat(fixture.service().get(
                committedRevisionWorkspace.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.STALE);

        var identityWorkspace = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        Path replacement = Files.createDirectories(
                tempDirectory.resolve("replacement-managed"));
        Files.createDirectories(replacement.resolve("model"));
        Files.createDirectories(replacement.resolve("query"));
        Files.createDirectories(replacement.resolve("shared"));
        Files.writeString(replacement.resolve("model/Order.tm"), "base tm");
        Files.writeString(replacement.resolve("query/Order.qm"), "base qm");
        Files.writeString(replacement.resolve("shared/common.fsscript"),
                "const common = true;");
        RuntimeBundleRecord replacementRecord = record(
                "managed-sales", "sales", replacement, true);
        when(fixture.registry().find("managed-sales"))
                .thenReturn(Optional.of(replacementRecord));
        fixture.liveBundle().setBundleDefinition(new ExternalBundleDefinition(
                "managed-sales", "sales", replacement.toString(), false));
        fixture.liveBundle().setBasePath(replacement.toString());
        fixture.liveBundle().setRootPath(replacement.toString());

        assertThat(fixture.service().get(identityWorkspace.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.STALE);
    }

    @Test
    void createRejectsSnapshotDriftWithoutLeavingWorkspace() throws Exception {
        Path livePath = Files.createDirectories(
                tempDirectory.resolve("create-drift-live"));
        Files.createDirectories(livePath.resolve("model"));
        Files.writeString(livePath.resolve("model/Order.tm"), "first");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        ExternalFileBundle live = externalBundle(
                context, "managed-sales", "sales", livePath.toString());
        RuntimeBundleRecord record = record(
                "managed-sales", "sales", livePath, true);
        WorkspaceSource source = new WorkspaceSource(
                record, live, livePath,
                RuntimeBundleInventoryService.sourceIdentity(
                        "managed-sales", "sales",
                        "external-filesystem", livePath));
        RuntimeBundleInventoryService inventory =
                mock(RuntimeBundleInventoryService.class);
        AtomicInteger inspections = new AtomicInteger();
        when(inventory.requireWorkspaceSource(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenAnswer(invocation -> {
                    if (inspections.incrementAndGet() == 2) {
                        Files.writeString(livePath.resolve("model/Order.tm"),
                                "changed during create");
                    }
                    return source;
                });
        when(context.getBundleList()).thenReturn(List.of(live));
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(
                tempDirectory.resolve("create-drift-store").toString());
        RuntimeAuthoringWorkspaceStore store =
                new RuntimeAuthoringWorkspaceStore(
                        properties, new ObjectMapper());
        RuntimeAuthoringWorkspaceService service =
                new RuntimeAuthoringWorkspaceService(
                        store, inventory, context,
                        provider(new CommittedSourceRevisionRegistry()),
                        provider(mock(DetachedModelValidationFactory.class)),
                        provider(mock(RuntimeCandidateQueryService.class)));

        assertCode(() -> service.create(null,
                        new AuthoringWorkspaceCreateRequest(
                                "sales", "managed-sales")),
                "WORKSPACE_STALE");
        assertThat(store.list(null, null, true)).isEmpty();
    }

    @Test
    void forbidsFsscriptOverlayOnSaveValidationAndQuery()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession session = validationSession();
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);
        var validated = fixture.service().validate(
                created.workspaceId(), created.candidateRevision());
        Bundle other = mock(Bundle.class);
        BundleDefinition definition = mock(BundleDefinition.class);
        when(other.getName()).thenReturn("jar-dependency");
        when(other.getDefinition()).thenReturn(definition);
        when(definition.getNamespace()).thenReturn("sales");
        when(other.findResources(anyString())).thenReturn(new Resource[0]);
        Path script = Files.writeString(
                tempDirectory.resolve("shared-foreign.fsscript"), "foreign");
        when(other.findResources("**/foreign.fsscript")).thenReturn(
                new Resource[]{new FileSystemResource(script)});
        when(other.findResources("**/common.fsscript")).thenReturn(
                new Resource[]{new FileSystemResource(script)});
        when(fixture.bundles().getBundleList()).thenReturn(
                List.of(fixture.liveBundle(), other));

        assertCode(() -> fixture.service().save(
                        validated.workspaceId(),
                        new AuthoringWorkspaceSaveRequest(
                                validated.candidateRevision(),
                                List.of(new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "shared/foreign.fsscript", "candidate")))),
                "WORKSPACE_OVERLAY_FORBIDDEN");
        assertCode(() -> fixture.service().validate(
                        validated.workspaceId(), validated.candidateRevision()),
                "WORKSPACE_OVERLAY_FORBIDDEN");
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        assertCode(() -> fixture.service().query(
                        validated.workspaceId(), "Order",
                        validated.candidateRevision(), request,
                        null, Phase.EXECUTE),
                "WORKSPACE_OVERLAY_FORBIDDEN");
        verify(fixture.candidateQueryService(), never()).query(any());
        assertThat(fixture.service().get(validated.workspaceId())
                .candidateRevision()).isEqualTo(validated.candidateRevision());
    }

    @Test
    void fullValidationRunsFsscriptThenTmThenQmAndRecordsExactEvidence()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession session = validationSession();
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);

        var validated = fixture.service().validate(
                created.workspaceId(), created.candidateRevision());

        assertThat(validated.state())
                .isEqualTo(AuthoringWorkspaceState.VALIDATED);
        assertThat(validated.lastValidation().valid()).isTrue();
        assertThat(validated.lastValidation().candidateRevision())
                .isEqualTo(created.candidateRevision());
        assertThat(validated.lastValidation().totalFiles()).isEqualTo(3);
        assertThat(validated.lastValidation().validFiles()).isEqualTo(3);
        InOrder order = inOrder(session);
        order.verify(session).validateFsscript(any(BundleResource.class));
        order.verify(session).validateTableModel(
                any(BundleResource.class), eq("sales"));
        order.verify(session).validateQueryModel(any(BundleResource.class));
        order.verify(session).close();
    }

    @Test
    void validationFailurePersistsSanitizedEvidenceWithoutValidatedState()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession session = validationSession();
        doThrow(new IllegalStateException(
                "jdbc:secret://must-not-be-persisted"))
                .when(session).validateQueryModel(any(BundleResource.class));
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);

        assertCode(() -> fixture.service().validate(
                        created.workspaceId(), created.candidateRevision()),
                "WORKSPACE_VALIDATION_FAILED");
        var failed = fixture.service().get(created.workspaceId());
        assertThat(failed.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
        assertThat(failed.lastValidation().valid()).isFalse();
        assertThat(failed.lastValidation().issues())
                .extracting(issue -> issue.message())
                .allMatch(message -> !message.contains("secret"));
    }

    @Test
    void candidateQueryRequiresExactValidationAndUsesServerOwnedRevisionPath()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));

        assertCode(() -> fixture.service().query(
                        created.workspaceId(), "Order",
                        created.candidateRevision(), request,
                        "Bearer business", Phase.EXECUTE),
                "WORKSPACE_NOT_VALIDATED");
        verify(fixture.candidateQueryService(), never()).query(any());

        DetachedModelValidationSession session = validationSession();
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);
        var validated = fixture.service().validate(
                created.workspaceId(), created.candidateRevision());
        SemanticQueryResponse semanticResponse = new SemanticQueryResponse();
        CatalogIdentity catalogIdentity = new CatalogIdentity(
                "sales", new CatalogGeneration("candidate:test"),
                new SourceRevision(validated.baseNamespaceSourceRevision()));
        when(fixture.candidateQueryService().query(any())).thenReturn(
                new RuntimeCandidateQueryResult(
                        semanticResponse, "managed-sales", "sales",
                        validated.candidateRevision(),
                        validated.baseNamespaceSourceRevision(), catalogIdentity,
                        "execute", List.of("REQUEST_LOCAL_CATALOG_PINNED")));

        AuthoringWorkspaceQueryResponse response = fixture.service().query(
                validated.workspaceId(), "Order",
                validated.candidateRevision(), request,
                "Bearer business", Phase.EXECUTE);

        assertThat(response.response()).isSameAs(semanticResponse);
        assertThat(response.candidateRevision())
                .isEqualTo(validated.candidateRevision());
        ArgumentCaptor<RuntimeCandidateQueryCommand> command =
                ArgumentCaptor.forClass(RuntimeCandidateQueryCommand.class);
        verify(fixture.candidateQueryService()).query(command.capture());
        assertThat(command.getValue().candidatePath())
                .startsWith(tempDirectory.resolve("workspace-store")
                        .toAbsolutePath().toString())
                .isNotEqualTo(fixture.livePath().toString());
        assertThat(command.getValue().authorization())
                .isEqualTo("Bearer business");
        assertThat(fixture.service().get(validated.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.VALIDATED);
    }

    @Test
    void validationCannotPublishLateEvidenceAfterConcurrentHeadChange()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession session = validationSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(session).validateFsscript(any(BundleResource.class));
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> validation = executor.submit(() -> {
                try {
                    fixture.service().validate(
                            created.workspaceId(), created.candidateRevision());
                    return "success";
                } catch (RuntimeAuthoringWorkspaceException failure) {
                    return failure.code();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var saved = fixture.service().save(created.workspaceId(),
                    new AuthoringWorkspaceSaveRequest(
                            created.candidateRevision(),
                            List.of(new AuthoringWorkspaceSaveRequest.ResourceFile(
                                    "model/Order.tm", "new head"))));
            release.countDown();

            assertThat(validation.get(5, TimeUnit.SECONDS))
                    .isEqualTo("WORKSPACE_REVISION_CONFLICT");
            var current = fixture.service().get(created.workspaceId());
            assertThat(current.candidateRevision())
                    .isEqualTo(saved.candidateRevision());
            assertThat(current.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
            assertThat(current.lastValidation()).isNull();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void queryCannotReturnLateSuccessAfterConcurrentDiscard()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession session = validationSession();
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(session);
        var validated = fixture.service().validate(
                created.workspaceId(), created.candidateRevision());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CatalogIdentity identity = new CatalogIdentity(
                "sales", new CatalogGeneration("candidate:discard-race"),
                new SourceRevision(validated.baseNamespaceSourceRevision()));
        when(fixture.candidateQueryService().query(any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return new RuntimeCandidateQueryResult(
                            new SemanticQueryResponse(), "managed-sales", "sales",
                            validated.candidateRevision(),
                            validated.baseNamespaceSourceRevision(), identity,
                            "execute", List.of("REQUEST_LOCAL_CATALOG_PINNED"));
                });
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> query = executor.submit(() -> {
                try {
                    fixture.service().query(
                            validated.workspaceId(), "Order",
                            validated.candidateRevision(), request,
                            "Bearer business", Phase.EXECUTE);
                    return "success";
                } catch (RuntimeAuthoringWorkspaceException failure) {
                    return failure.code();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var discarded = fixture.service().discard(
                    validated.workspaceId(), validated.candidateRevision());
            release.countDown();

            assertThat(query.get(5, TimeUnit.SECONDS))
                    .isEqualTo("WORKSPACE_STATE_INVALID");
            assertThat(discarded.state())
                    .isEqualTo(AuthoringWorkspaceState.DISCARDED);
            assertThat(fixture.service().get(validated.workspaceId()).state())
                    .isEqualTo(AuthoringWorkspaceState.DISCARDED);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void queryCannotReuseValidationEvidenceInvalidatedDuringExecution()
            throws Exception {
        Fixture fixture = fixture("base tm", "base qm");
        var created = fixture.service().create(null,
                new AuthoringWorkspaceCreateRequest(
                        "sales", "managed-sales"));
        DetachedModelValidationSession passing = validationSession();
        when(fixture.validationFactory().open(
                eq("managed-sales"), eq("sales"), anyString()))
                .thenReturn(passing);
        var validated = fixture.service().validate(
                created.workspaceId(), created.candidateRevision());

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CatalogIdentity identity = new CatalogIdentity(
                "sales", new CatalogGeneration("candidate:validation-race"),
                new SourceRevision(validated.baseNamespaceSourceRevision()));
        when(fixture.candidateQueryService().query(any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return new RuntimeCandidateQueryResult(
                            new SemanticQueryResponse(), "managed-sales", "sales",
                            validated.candidateRevision(),
                            validated.baseNamespaceSourceRevision(), identity,
                            "execute", List.of("REQUEST_LOCAL_CATALOG_PINNED"));
                });
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> query = executor.submit(() -> {
                try {
                    fixture.service().query(
                            validated.workspaceId(), "Order",
                            validated.candidateRevision(), request,
                            "Bearer business", Phase.EXECUTE);
                    return "success";
                } catch (RuntimeAuthoringWorkspaceException failure) {
                    return failure.code();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            DetachedModelValidationSession failing = validationSession();
            doThrow(new IllegalStateException("revalidation failed"))
                    .when(failing).validateQueryModel(any(BundleResource.class));
            when(fixture.validationFactory().open(
                    eq("managed-sales"), eq("sales"), anyString()))
                    .thenReturn(failing);
            assertCode(() -> fixture.service().validate(
                            validated.workspaceId(),
                            validated.candidateRevision()),
                    "WORKSPACE_VALIDATION_FAILED");
            release.countDown();

            assertThat(query.get(5, TimeUnit.SECONDS))
                    .isEqualTo("WORKSPACE_NOT_VALIDATED");
            var current = fixture.service().get(validated.workspaceId());
            assertThat(current.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
            assertThat(current.lastValidation().valid()).isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void bundleInventoryReportsLiveKindsAndOnlyExactManagedDirectoryEligible()
            throws Exception {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        Path managedPath = Files.createDirectory(
                tempDirectory.resolve("inventory-managed"));
        Path configuredPath = Files.createDirectory(
                tempDirectory.resolve("inventory-configured"));
        ExternalFileBundle managed = externalBundle(
                context, "managed", "sales", managedPath.toString());
        ExternalFileBundle configured = externalBundle(
                context, "configured", "sales", configuredPath.toString());
        ExternalFileBundle resource = externalBundle(
                context, "resource", "sales", "classpath:/foggy/templates");
        BundleImpl jar = classBundle(context, "jar-bundle", "sales",
                BundleImpl.MODE_JAR, "jar:file:/opaque!/", BundleDefinition.class);
        BundleImpl classpath = classBundle(context, "class-bundle", "sales",
                BundleImpl.MODE_CLASSPATH, "file:/opaque/", BundleDefinition.class);
        RuntimeBundleRecord managedRecord = record(
                "managed", "sales", managedPath, true);
        RuntimeBundleRecord inactiveRecord = record(
                "inactive", "sales", tempDirectory.resolve("inactive"), true);
        when(registry.listRecords()).thenReturn(
                List.of(managedRecord, inactiveRecord));
        when(registry.find(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return Optional.ofNullable("managed".equals(name)
                    ? managedRecord : "inactive".equals(name)
                    ? inactiveRecord : null);
        });
        when(context.getBundleList()).thenReturn(List.of(
                managed, configured, resource, jar, classpath));
        when(context.listExternalBundles()).thenReturn(List.of(
                managed.getDefinition(), configured.getDefinition(),
                resource.getDefinition()));
        when(context.getBundleByName("managed", false)).thenReturn(managed);
        RuntimeBundleInventoryService inventory =
                new RuntimeBundleInventoryService(context, registry);

        Map<String, BundleInfo> infos = inventory.list().stream()
                .collect(Collectors.toMap(BundleInfo::name, Function.identity()));

        assertThat(infos.get("managed").sourceType())
                .isEqualTo("external-filesystem");
        assertThat(infos.get("managed").workspaceEligible()).isTrue();
        assertThat(infos.get("managed").editable()).isTrue();
        assertThat(infos.get("configured").workspaceEligible()).isFalse();
        assertThat(infos.get("resource").sourceType())
                .isEqualTo("external-resource");
        assertThat(infos.get("jar-bundle").sourceType()).isEqualTo("jar");
        assertThat(infos.get("jar-bundle").path()).isNull();
        assertThat(infos.get("class-bundle").sourceType())
                .isEqualTo("classpath");
        assertThat(infos.get("inactive").status()).isEqualTo("inactive");
        assertThat(infos.values()).allMatch(info ->
                info.namespaceBindings().equals(List.of("sales"))
                        && info.sourceIdentity() != null
                        && info.sourceIdentity().startsWith("sha256:"));
        assertCode(() -> inventory.requireWorkspaceSource(
                        "configured", "sales", "test"),
                "WORKSPACE_SOURCE_INELIGIBLE");
    }

    private Fixture fixture(String tableModel, String queryModel)
            throws Exception {
        Path livePath = Files.createDirectories(
                tempDirectory.resolve("live-managed"));
        Files.createDirectories(livePath.resolve("model"));
        Files.createDirectories(livePath.resolve("query"));
        Files.createDirectories(livePath.resolve("shared"));
        Files.writeString(livePath.resolve("model/Order.tm"), tableModel);
        Files.writeString(livePath.resolve("query/Order.qm"), queryModel);
        Files.writeString(livePath.resolve("shared/common.fsscript"),
                "const common = true;");

        SystemBundlesContext context = mock(SystemBundlesContext.class);
        ExternalFileBundle live = externalBundle(
                context, "managed-sales", "sales", livePath.toString());
        when(context.getBundleList()).thenReturn(List.of(live));
        when(context.listExternalBundles()).thenReturn(
                List.of(live.getDefinition()));
        when(context.getBundleByName("managed-sales", false)).thenReturn(live);
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        RuntimeBundleRecord record = record(
                "managed-sales", "sales", livePath, true);
        when(registry.find("managed-sales"))
                .thenReturn(Optional.of(record));
        when(registry.listRecords()).thenReturn(List.of(record));

        FoggyRuntimeApiProperties properties =
                new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(
                tempDirectory.resolve("workspace-store").toString());
        RuntimeAuthoringWorkspaceStore store =
                new RuntimeAuthoringWorkspaceStore(
                        properties, new ObjectMapper());
        RuntimeBundleInventoryService inventory =
                new RuntimeBundleInventoryService(context, registry);
        CommittedSourceRevisionRegistry sourceRegistry =
                new CommittedSourceRevisionRegistry();
        DetachedModelValidationFactory validationFactory =
                mock(DetachedModelValidationFactory.class);
        RuntimeCandidateQueryService candidateQueryService =
                mock(RuntimeCandidateQueryService.class);
        RuntimeAuthoringWorkspaceService service =
                new RuntimeAuthoringWorkspaceService(
                        store, inventory, context, provider(sourceRegistry),
                        provider(validationFactory),
                        provider(candidateQueryService));
        return new Fixture(service, context, live, livePath, registry, sourceRegistry,
                validationFactory, candidateQueryService);
    }

    private static DetachedModelValidationSession validationSession() {
        DetachedModelValidationSession session =
                mock(DetachedModelValidationSession.class);
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        ExternalFileBundle bundle = externalBundle(
                context, "managed-sales", "sales", ".");
        when(session.sourceBundle()).thenReturn(bundle);
        return session;
    }

    private static ExternalFileBundle externalBundle(
            SystemBundlesContext context,
            String name,
            String namespace,
            String path
    ) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                name, namespace, path, false);
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(name);
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(path);
        bundle.setRootPath(path);
        return bundle;
    }

    private static BundleImpl classBundle(
            SystemBundlesContext context,
            String name,
            String namespace,
            int mode,
            String root,
            Class<?> definitionClass
    ) {
        BundleDefinition definition = mock(BundleDefinition.class);
        when(definition.getName()).thenReturn(name);
        when(definition.getNamespace()).thenReturn(namespace);
        doReturn(definitionClass).when(definition).getDefinitionClass();
        BundleImpl bundle = new BundleImpl(context);
        bundle.setName(name);
        bundle.setMode(mode);
        bundle.setRootPath(root);
        bundle.setBasePath(root + "foggy/templates");
        bundle.setBundleDefinition(definition);
        return bundle;
    }

    private static RuntimeBundleRecord record(
            String name,
            String namespace,
            Path path,
            boolean enabled
    ) {
        return new RuntimeBundleRecord(
                name, namespace, path.toString(), false, enabled,
                "created", "updated");
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

    private record Fixture(
            RuntimeAuthoringWorkspaceService service,
            SystemBundlesContext bundles,
            ExternalFileBundle liveBundle,
            Path livePath,
            RuntimeBundleRegistryService registry,
            CommittedSourceRevisionRegistry sourceRegistry,
            DetachedModelValidationFactory validationFactory,
            RuntimeCandidateQueryService candidateQueryService
    ) {
    }
}
