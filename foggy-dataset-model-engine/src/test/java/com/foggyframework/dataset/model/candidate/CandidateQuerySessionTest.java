package com.foggyframework.dataset.model.candidate;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.validation.DetachedModelValidationSession;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateQuerySessionTest {

    private static final String NAMESPACE = "candidate-unit";
    private static final String SOURCE_BUNDLE = "managed-authoring";
    private static final String MODEL = "DraftOrdersQuery";

    @Test
    void contentRevisionIsStableAndChangesWithSourceBytes(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);

        String first;
        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            first = session.identity().candidateRevision();
        }
        String second;
        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            second = session.identity().candidateRevision();
        }
        Files.writeString(tempDirectory.resolve("query/" + MODEL + ".qm"),
                "export const queryModel = { name: 'changed' };\n");
        String queryChanged;
        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            queryChanged = session.identity().candidateRevision();
        }
        Files.writeString(tempDirectory.resolve("shared/marker.fsscript"),
                "export const marker = 'changed';\n");
        String scriptChanged;
        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            scriptChanged = session.identity().candidateRevision();
        }

        assertThat(first).startsWith("sha256:").isEqualTo(second);
        assertThat(queryChanged).startsWith("sha256:").isNotEqualTo(first);
        assertThat(scriptChanged).startsWith("sha256:")
                .isNotEqualTo(queryChanged);
    }

    @Test
    void validateAndExecuteReuseOneCandidateModelResolution(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        SemanticRequestContext validateContext = SemanticRequestContext.of(
                        NAMESPACE, "opaque-token")
                .withPermissionAction(PermissionAction.EXECUTE);
        SemanticRequestContext executeContext = SemanticRequestContext.of(
                        NAMESPACE, "opaque-token")
                .withPermissionAction(PermissionAction.VALIDATE);

        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            CandidateQueryResult validated = session.validate(
                    MODEL, request, validateContext);
            CandidateQueryResult executed = session.execute(
                    MODEL, request, executeContext);

            assertThat(validated.identity().candidateRevision())
                    .isEqualTo(executed.identity().candidateRevision());
            assertThat(validated.identity().baseSourceRevision())
                    .isEqualTo(fixture.source().baseSourceRevision());
            assertThat(executed.diagnostics()).containsExactly(
                    "REQUEST_LOCAL_CATALOG_PINNED",
                    "SHARED_L1_CACHE_DISABLED",
                    "SHARED_L2_CACHE_DISABLED",
                    "PREAGGREGATION_DISABLED"
            );
        }

        ArgumentCaptor<SemanticRequestContext> contexts =
                ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(fixture.semanticService()).queryModel(
                eq(MODEL), eq(request), eq("validate"), contexts.capture());
        verify(fixture.semanticService()).queryModel(
                eq(MODEL), eq(request), eq("execute"), contexts.capture());
        assertThat(contexts.getAllValues()).hasSize(2);
        assertThat(contexts.getAllValues().get(0).getCatalogResolution())
                .isSameAs(contexts.getAllValues().get(1).getCatalogResolution());
        assertThat(contexts.getAllValues().get(0).getCatalogResolution().model())
                .isSameAs(fixture.model());
        assertThat(contexts.getAllValues().get(0).getExecutionBundlesContext())
                .isSameAs(fixture.detachedBundles());
        assertThat(contexts.getAllValues().get(0).getAuthorization())
                .isEqualTo("opaque-token");
        assertThat(contexts.getAllValues().get(0).getPermissionAction())
                .isEqualTo(PermissionAction.VALIDATE);
        assertThat(contexts.getAllValues().get(1).getPermissionAction())
                .isEqualTo(PermissionAction.EXECUTE);
    }

    @Test
    void committedSourceDriftFailsClosedBeforeSemanticExecution(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        CandidateQuerySession session = fixture.factory().open(fixture.source());
        fixture.sourceRegistry().commitKnown(List.of(NAMESPACE), () -> true);

        assertThatThrownBy(() -> session.execute(
                MODEL, ordinaryRequest(), SemanticRequestContext.ofNamespace(NAMESPACE)))
                .isInstanceOf(CandidateQueryException.class)
                .extracting(failure -> ((CandidateQueryException) failure).code())
                .isEqualTo(CandidateQueryErrorCode.CANDIDATE_SOURCE_STALE);
        verify(fixture.semanticService(), never()).queryModel(
                anyString(), any(), anyString(), any());
        session.close();
    }

    @Test
    void committedSourceDriftDuringExecutionWindowFailsClosed(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        when(fixture.semanticService().queryModel(
                eq(MODEL), any(), eq("execute"), any()))
                .thenAnswer(invocation -> {
                    fixture.sourceRegistry().commitKnown(
                            List.of(NAMESPACE), () -> true);
                    return new SemanticQueryResponse();
                });

        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            assertThatThrownBy(() -> session.execute(
                    MODEL,
                    ordinaryRequest(),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            )).isInstanceOf(CandidateQueryException.class)
                    .extracting(failure -> ((CandidateQueryException) failure).code())
                    .isEqualTo(CandidateQueryErrorCode.CANDIDATE_SOURCE_STALE);
        }

        verify(fixture.semanticService()).queryModel(
                eq(MODEL), any(), eq("execute"), any());
    }

    @Test
    void activeSessionRejectsCandidateContentDrift(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        CandidateQuerySession session = fixture.factory().open(fixture.source());
        Files.writeString(tempDirectory.resolve("model/DraftOrders.tm"),
                "export const model = { name: 'drifted' };\n");

        assertThatThrownBy(() -> session.validate(
                MODEL, ordinaryRequest(), SemanticRequestContext.ofNamespace(NAMESPACE)))
                .isInstanceOf(CandidateQueryException.class)
                .extracting(failure -> ((CandidateQueryException) failure).code())
                .isEqualTo(CandidateQueryErrorCode.CANDIDATE_CONTENT_STALE);
        verify(fixture.semanticService(), never()).queryModel(
                anyString(), any(), anyString(), any());
        session.close();
    }

    @Test
    void configuredJarAndOtherManagedOverlaysAreRejectedBeforeDetachedOpen(
            @TempDir Path tempDirectory
    ) throws Exception {
        for (String owner : List.of(
                "runtime-managed-other",
                "configured-external",
                "read-only-jar"
        )) {
            Fixture fixture = fixture(tempDirectory.resolve(owner));
            Bundle otherBundle = mock(Bundle.class);
            when(otherBundle.getName()).thenReturn(owner);
            BundleResource otherResource = mock(BundleResource.class);
            when(otherResource.getBundle()).thenReturn(otherBundle);
            when(fixture.liveBundles().findResourceByName(
                    "DraftOrders.tm", NAMESPACE, false))
                    .thenReturn(otherResource);

            assertThatThrownBy(() -> fixture.factory().open(fixture.source()))
                    .as("overlay owned by %s", owner)
                    .isInstanceOf(CandidateQueryException.class)
                    .extracting(failure -> ((CandidateQueryException) failure).code())
                    .isEqualTo(CandidateQueryErrorCode.CANDIDATE_OVERLAY_FORBIDDEN);
            verify(fixture.validationFactory(), never()).open(
                    anyString(), anyString(), anyString());
        }
    }

    @Test
    void overlayOwnedBySelectedSourceBundleIsAllowed(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        Bundle selectedBundle = mock(Bundle.class);
        when(selectedBundle.getName()).thenReturn(SOURCE_BUNDLE);
        BundleResource selectedResource = mock(BundleResource.class);
        when(selectedResource.getBundle()).thenReturn(selectedBundle);
        when(fixture.liveBundles().findResourceByName(
                "DraftOrders.tm", NAMESPACE, false))
                .thenReturn(selectedResource);

        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            assertThat(session.identity().sourceBundle()).isEqualTo(SOURCE_BUNDLE);
        }

        verify(fixture.validationFactory()).open(
                eq(SOURCE_BUNDLE), eq(NAMESPACE), anyString());
    }

    @Test
    void advancedModesAndClosedSessionsFailWithStableCodes(
            @TempDir Path tempDirectory
    ) throws Exception {
        Fixture fixture = fixture(tempDirectory);
        CandidateQuerySession session = fixture.factory().open(fixture.source());
        SemanticQueryRequest pivot = ordinaryRequest();
        pivot.setPivot(mock(PivotRequest.class));
        SemanticQueryRequest semanticSql = ordinaryRequest();
        semanticSql.setSemanticSql("select orderId from DraftOrdersQuery");
        SemanticQueryRequest composeCte = ordinaryRequest();
        composeCte.setRoute("DSL_CTE");
        SemanticQueryRequest memoryGrid = ordinaryRequest();
        memoryGrid.setMemoryGridPlan(Map.of("operation", "select"));

        for (SemanticQueryRequest unsupported : List.of(
                pivot, semanticSql, composeCte, memoryGrid)) {
            assertThatThrownBy(() -> session.execute(
                    MODEL,
                    unsupported,
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            )).isInstanceOf(CandidateQueryException.class)
                    .extracting(failure -> ((CandidateQueryException) failure).code())
                    .isEqualTo(CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED);
        }
        assertThatThrownBy(() -> session.execute(
                MODEL + "#synthetic",
                ordinaryRequest(),
                SemanticRequestContext.ofNamespace(NAMESPACE)
        )).isInstanceOf(CandidateQueryException.class)
                .extracting(failure -> ((CandidateQueryException) failure).code())
                .isEqualTo(CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED);

        session.close();
        assertThatThrownBy(() -> session.execute(
                MODEL, ordinaryRequest(), SemanticRequestContext.ofNamespace(NAMESPACE)))
                .isInstanceOf(CandidateQueryException.class)
                .extracting(failure -> ((CandidateQueryException) failure).code())
                .isEqualTo(CandidateQueryErrorCode.CANDIDATE_SESSION_CLOSED);
        verify(fixture.semanticService(), never()).queryModel(
                anyString(), any(), anyString(), any());
    }

    @Test
    void nonJdbcResolvedModelsFailBeforeSemanticExecution(
            @TempDir Path tempDirectory
    ) throws Exception {
        QueryModel unsupported = mock(QueryModel.class);
        Fixture fixture = fixture(tempDirectory, unsupported);

        try (CandidateQuerySession session = fixture.factory().open(fixture.source())) {
            assertUnsupported(() -> session.validate(
                    MODEL,
                    ordinaryRequest(),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            ));
            assertUnsupported(() -> session.execute(
                    MODEL,
                    ordinaryRequest(),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            ));
        }

        verify(fixture.semanticService(), never()).queryModel(
                anyString(), any(), anyString(), any());
    }

    private static void assertUnsupported(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(CandidateQueryException.class)
                .extracting(failure -> ((CandidateQueryException) failure).code())
                .isEqualTo(CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED);
    }

    private static SemanticQueryRequest ordinaryRequest() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId"));
        return request;
    }

    private static Fixture fixture(Path root) throws Exception {
        return fixture(root, mock(JdbcQueryModelImpl.class));
    }

    private static Fixture fixture(Path root, QueryModel model) throws Exception {
        Files.createDirectories(root.resolve("model"));
        Files.createDirectories(root.resolve("query"));
        Files.createDirectories(root.resolve("shared"));
        Files.writeString(root.resolve("model/DraftOrders.tm"),
                "export const model = { name: 'DraftOrders' };\n");
        Files.writeString(root.resolve("query/" + MODEL + ".qm"),
                "export const queryModel = { name: '" + MODEL + "' };\n");
        Files.writeString(root.resolve("shared/marker.fsscript"),
                "export const marker = 'candidate';\n");

        CommittedSourceRevisionRegistry registry =
                new CommittedSourceRevisionRegistry();
        SystemBundlesContext liveBundles = mock(SystemBundlesContext.class);
        Bundle sourceBundle = mock(Bundle.class);
        BundleDefinition sourceDefinition = mock(BundleDefinition.class);
        when(sourceDefinition.getNamespace()).thenReturn(NAMESPACE);
        when(sourceBundle.getName()).thenReturn(SOURCE_BUNDLE);
        when(sourceBundle.getDefinition()).thenReturn(sourceDefinition);
        when(liveBundles.getBundleByName(SOURCE_BUNDLE, false))
                .thenReturn(sourceBundle);
        when(liveBundles.findResourceByName(
                anyString(), eq(NAMESPACE), eq(false))).thenReturn(null);

        DetachedModelValidationFactory validationFactory =
                mock(DetachedModelValidationFactory.class);
        DetachedModelValidationSession detached =
                mock(DetachedModelValidationSession.class);
        Bundle detachedSource = mock(Bundle.class);
        BundleResource queryResource = mock(BundleResource.class);
        when(detachedSource.findBundleResource(MODEL + ".qm", false))
                .thenReturn(queryResource);
        when(detached.sourceBundle()).thenReturn(detachedSource);
        SystemBundlesContext detachedBundles = mock(SystemBundlesContext.class);
        when(detached.executionBundlesContext()).thenReturn(detachedBundles);

        when(model.getName()).thenReturn(MODEL);
        CatalogResolution<QueryModel> detachedResolution =
                new CatalogResolution<>(
                        MODEL,
                        model,
                        new CatalogIdentity(
                                NAMESPACE,
                                new CatalogGeneration("detached-catalog"),
                                new SourceRevision("detached-source")
                        ),
                        Map.of(),
                        true
                );
        when(detached.resolveQueryModel(MODEL, NAMESPACE))
                .thenReturn(detachedResolution);
        when(validationFactory.open(
                eq(SOURCE_BUNDLE), eq(NAMESPACE), anyString()))
                .thenReturn(detached);

        SemanticQueryServiceV3 semanticService =
                mock(SemanticQueryServiceV3.class);
        when(semanticService.queryModel(
                eq(MODEL), any(), anyString(), any()))
                .thenReturn(new SemanticQueryResponse());

        CandidateQuerySource source = new CandidateQuerySource(
                SOURCE_BUNDLE,
                NAMESPACE,
                root.toString(),
                registry.currentRevision(NAMESPACE)
        );
        CandidateQueryFactory factory = new DefaultCandidateQueryFactory(
                liveBundles,
                validationFactory,
                semanticService,
                registry
        );
        return new Fixture(
                factory,
                source,
                registry,
                liveBundles,
                validationFactory,
                semanticService,
                detachedBundles,
                model
        );
    }

    private record Fixture(
            CandidateQueryFactory factory,
            CandidateQuerySource source,
            CommittedSourceRevisionRegistry sourceRegistry,
            SystemBundlesContext liveBundles,
            DetachedModelValidationFactory validationFactory,
            SemanticQueryServiceV3 semanticService,
            SystemBundlesContext detachedBundles,
            QueryModel model
    ) {
    }
}
