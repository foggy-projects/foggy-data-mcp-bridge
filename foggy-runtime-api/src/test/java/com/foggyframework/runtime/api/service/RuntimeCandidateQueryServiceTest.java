package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.candidate.CandidateQueryErrorCode;
import com.foggyframework.dataset.model.candidate.CandidateQueryException;
import com.foggyframework.dataset.model.candidate.CandidateQueryFactory;
import com.foggyframework.dataset.model.candidate.CandidateQueryIdentity;
import com.foggyframework.dataset.model.candidate.CandidateQueryResult;
import com.foggyframework.dataset.model.candidate.CandidateQuerySession;
import com.foggyframework.dataset.model.candidate.CandidateQuerySource;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.Phase;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryCommand;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeCandidateQueryServiceTest {

    private static final String SOURCE_BUNDLE = "managed-authoring";
    private static final String NAMESPACE = "sales";
    private static final String MODEL = "DraftOrdersQuery";
    private static final String CANDIDATE_PATH = "/workspace/candidate";
    private static final String BASE_REVISION = "source:7";
    private static final String CANDIDATE_REVISION = "sha256:candidate";

    @Test
    void validateReturnsExactCandidateIdentityAndForwardsAuthorization(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path managedPath = Files.createDirectory(
                tempDirectory.resolve("managed-source"));
        Path relativePath = Path.of("").toAbsolutePath()
                .relativize(managedPath.toAbsolutePath());
        Fixture fixture = fixture(
                true, NAMESPACE, relativePath, managedPath);
        SemanticQueryRequest request = request();
        SemanticQueryResponse response = new SemanticQueryResponse();
        CatalogIdentity catalogIdentity = new CatalogIdentity(
                NAMESPACE,
                new CatalogGeneration("candidate:" + CANDIDATE_REVISION),
                new SourceRevision(BASE_REVISION)
        );
        CandidateQueryIdentity identity = new CandidateQueryIdentity(
                NAMESPACE,
                SOURCE_BUNDLE,
                BASE_REVISION,
                CANDIDATE_REVISION,
                catalogIdentity
        );
        when(fixture.session().validate(eq(MODEL), eq(request), any()))
                .thenReturn(new CandidateQueryResult(
                        response,
                        identity,
                        "validate",
                        List.of("REQUEST_LOCAL_CATALOG_PINNED",
                                "SHARED_L1_CACHE_DISABLED")
                ));

        RuntimeCandidateQueryResult result = fixture.service().query(command(
                request, "opaque-authorization", Phase.VALIDATE));

        assertThat(result.response()).isSameAs(response);
        assertThat(result.sourceBundle()).isEqualTo(SOURCE_BUNDLE);
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.baseSourceRevision()).isEqualTo(BASE_REVISION);
        assertThat(result.candidateRevision()).isEqualTo(CANDIDATE_REVISION);
        assertThat(result.catalogIdentity()).isSameAs(catalogIdentity);
        assertThat(result.phase()).isEqualTo("validate");
        assertThat(result.diagnostics()).containsExactly(
                "REQUEST_LOCAL_CATALOG_PINNED",
                "SHARED_L1_CACHE_DISABLED"
        );

        ArgumentCaptor<CandidateQuerySource> source =
                ArgumentCaptor.forClass(CandidateQuerySource.class);
        verify(fixture.factory()).open(source.capture());
        assertThat(source.getValue()).isEqualTo(new CandidateQuerySource(
                SOURCE_BUNDLE, NAMESPACE, CANDIDATE_PATH, BASE_REVISION));
        ArgumentCaptor<SemanticRequestContext> context =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(fixture.session()).validate(
                eq(MODEL), eq(request), context.capture());
        assertThat(context.getValue().getNamespace()).isEqualTo(NAMESPACE);
        assertThat(context.getValue().getAuthorization())
                .isEqualTo("opaque-authorization");
        verify(fixture.session()).close();
    }

    @Test
    void executeUsesExecutePhaseAndClosesSessionAfterFailure(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(true, NAMESPACE,
                Files.createDirectory(tempDirectory.resolve("managed-source")));
        CandidateQueryException databaseFailure = new CandidateQueryException(
                CandidateQueryErrorCode.CANDIDATE_CONTENT_STALE,
                "execute",
                "Candidate changed while executing",
                MODEL
        );
        when(fixture.session().execute(eq(MODEL), any(), any()))
                .thenThrow(databaseFailure);

        assertThatThrownBy(() -> fixture.service().query(command(
                request(), null, Phase.EXECUTE)))
                .isSameAs(databaseFailure);

        verify(fixture.session()).execute(eq(MODEL), any(), any());
        verify(fixture.session()).close();
    }

    @Test
    void rejectsMissingDisabledAndWrongNamespaceSourcesBeforeFactoryOpen() {
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        SystemBundlesContext liveBundles = mock(SystemBundlesContext.class);
        CandidateQueryFactory factory = mock(CandidateQueryFactory.class);
        RuntimeCandidateQueryService service =
                new RuntimeCandidateQueryService(registry, liveBundles, factory);

        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.empty());
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.of(record(
                false, NAMESPACE)));
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.of(record(
                true, "finance")));
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        verify(factory, never()).open(any());
    }

    @Test
    void rejectsMissingPhaseBeforeFactoryOpen() {
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        SystemBundlesContext liveBundles = mock(SystemBundlesContext.class);
        CandidateQueryFactory factory = mock(CandidateQueryFactory.class);
        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.of(record(
                true, NAMESPACE)));
        RuntimeCandidateQueryService service =
                new RuntimeCandidateQueryService(registry, liveBundles, factory);

        assertInvalidSource(() -> service.query(command(request(), null, null)));

        verify(factory, never()).open(any());
    }

    @Test
    void rejectsConfiguredJarAbsentAndMismatchedLiveSourcesBeforeFactoryOpen(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path managedPath = Files.createDirectory(
                tempDirectory.resolve("managed-source"));
        Path otherPath = Files.createDirectory(
                tempDirectory.resolve("other-source"));
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        SystemBundlesContext liveBundles = mock(SystemBundlesContext.class);
        CandidateQueryFactory factory = mock(CandidateQueryFactory.class);
        RuntimeCandidateQueryService service = new RuntimeCandidateQueryService(
                registry, liveBundles, factory);

        ExternalFileBundle configured = externalBundle(
                liveBundles, SOURCE_BUNDLE, NAMESPACE, managedPath);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(configured);
        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.empty());
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.of(record(
                true, NAMESPACE, managedPath)));
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false)).thenReturn(null);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        Bundle jarBundle = mock(Bundle.class);
        BundleDefinition jarDefinition = mock(BundleDefinition.class);
        when(jarBundle.getName()).thenReturn(SOURCE_BUNDLE);
        when(jarBundle.getDefinition()).thenReturn(jarDefinition);
        when(jarDefinition.getName()).thenReturn(SOURCE_BUNDLE);
        when(jarDefinition.getNamespace()).thenReturn(NAMESPACE);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(jarBundle);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        ExternalFileBundle wrongNamespace = externalBundle(
                liveBundles, SOURCE_BUNDLE, "finance", managedPath);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(wrongNamespace);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        ExternalFileBundle wrongName = externalBundle(
                liveBundles, "configured-authoring", NAMESPACE, managedPath);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(wrongName);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        ExternalFileBundle pathMismatch = externalBundle(
                liveBundles, SOURCE_BUNDLE, NAMESPACE, otherPath);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(pathMismatch);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        Path symlinkPath = tempDirectory.resolve("managed-source-link");
        Files.createSymbolicLink(symlinkPath, managedPath);
        ExternalFileBundle symlinkSource = externalBundle(
                liveBundles, SOURCE_BUNDLE, NAMESPACE, symlinkPath);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(symlinkSource);
        assertInvalidSource(() -> service.query(command(
                request(), null, Phase.VALIDATE)));

        verify(factory, never()).open(any());
    }

    private static void assertInvalidSource(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(CandidateQueryException.class)
                .satisfies(error -> {
                    CandidateQueryException failure =
                            (CandidateQueryException) error;
                    assertThat(failure.code()).isEqualTo(
                            CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID);
                    assertThat(failure.phase()).isEqualTo("open");
                });
    }

    private static Fixture fixture(
            boolean enabled,
            String namespace,
            Path managedPath
    ) {
        return fixture(enabled, namespace, managedPath, managedPath);
    }

    private static Fixture fixture(
            boolean enabled,
            String namespace,
            Path registryPath,
            Path livePath
    ) {
        RuntimeBundleRegistryService registry =
                mock(RuntimeBundleRegistryService.class);
        when(registry.find(SOURCE_BUNDLE)).thenReturn(Optional.of(record(
                enabled, namespace, registryPath)));
        SystemBundlesContext liveBundles = mock(SystemBundlesContext.class);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false)).thenReturn(
                externalBundle(liveBundles, SOURCE_BUNDLE, namespace, livePath));
        CandidateQueryFactory factory = mock(CandidateQueryFactory.class);
        CandidateQuerySession session = mock(CandidateQuerySession.class);
        when(factory.open(any())).thenReturn(session);
        return new Fixture(
                new RuntimeCandidateQueryService(registry, liveBundles, factory),
                factory,
                session
        );
    }

    private static RuntimeBundleRecord record(
            boolean enabled,
            String namespace
    ) {
        return record(enabled, namespace, Path.of("/runtime/managed-source"));
    }

    private static RuntimeBundleRecord record(
            boolean enabled,
            String namespace,
            Path path
    ) {
        return new RuntimeBundleRecord(
                SOURCE_BUNDLE,
                namespace,
                path.toString(),
                false,
                enabled,
                "created",
                "updated"
        );
    }

    private static ExternalFileBundle externalBundle(
            SystemBundlesContext context,
            String name,
            String namespace,
            Path path
    ) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                name, namespace, path.toString(), false);
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(name);
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(path.toString());
        bundle.setRootPath(path.toString());
        return bundle;
    }

    private static RuntimeCandidateQueryCommand command(
            SemanticQueryRequest request,
            String authorization,
            Phase phase
    ) {
        return new RuntimeCandidateQueryCommand(
                SOURCE_BUNDLE,
                NAMESPACE,
                CANDIDATE_PATH,
                BASE_REVISION,
                MODEL,
                request,
                authorization,
                phase
        );
    }

    private static SemanticQueryRequest request() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        return request;
    }

    private record Fixture(
            RuntimeCandidateQueryService service,
            CandidateQueryFactory factory,
            CandidateQuerySession session
    ) {
    }
}
