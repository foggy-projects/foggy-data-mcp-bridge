package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.model.candidate.CandidateQueryFactory;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleaseExportRequest;
import com.foggyframework.runtime.api.dto.AuthoringReleaseImportRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePromotionRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePublishRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRecoverRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRollbackRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.Phase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RuntimeAuthoringWorkspaceRealExecutionTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.web-application-type=none"
)
class RuntimeAuthoringWorkspaceRealExecutionTest {

    private static final String NAMESPACE = "workspace-real-sqlite";
    private static final String SOURCE_BUNDLE = "workspace-real-source";
    private static final String TABLE_MODEL = "WorkspaceRealOrderModel";
    private static final String QUERY_MODEL = "WorkspaceRealOrderQuery";
    private static final String SCRIPT = "workspace-real-table.fsscript";

    @TempDir
    Path tempDirectory;

    @jakarta.annotation.Resource
    private SystemBundlesContext bundlesContext;

    @jakarta.annotation.Resource
    private CandidateQueryFactory candidateQueryFactory;

    @jakarta.annotation.Resource
    private DetachedModelValidationFactory validationFactory;

    @jakarta.annotation.Resource
    private CommittedSourceRevisionRegistry sourceRegistry;

    @jakarta.annotation.Resource
    private CatalogSnapshotStore catalogSnapshotStore;

    @jakarta.annotation.Resource
    private RootFsscriptLoader rootFsscriptLoader;

    @jakarta.annotation.Resource
    private ObjectMapper objectMapper;

    @jakarta.annotation.Resource
    private DataSource dataSource;

    @jakarta.annotation.Resource
    private RuntimeModelOperations modelOperations;

    @jakarta.annotation.Resource
    private SemanticQueryServiceV3 semanticQueryService;

    private RuntimeAuthoringWorkspaceService service;
    private RuntimeAuthoringWorkspacePublicationService publicationService;
    private RuntimeAuthoringWorkspaceStore store;
    private RuntimeBundleRegistryService registry;
    private RuntimeBundleInventoryService inventory;
    private ExternalFileBundle selectedBundle;
    private Path livePath;
    private FoggyRuntimeApiProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS workspace_live_rows");
        jdbc.execute("DROP TABLE IF EXISTS workspace_candidate_rows");
        jdbc.execute("DROP TABLE IF EXISTS workspace_candidate_v2_rows");
        jdbc.execute("""
                CREATE TABLE workspace_live_rows (
                    order_id VARCHAR(32) NOT NULL PRIMARY KEY,
                    order_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE workspace_candidate_rows (
                    order_id VARCHAR(32) NOT NULL PRIMARY KEY,
                    order_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE workspace_candidate_v2_rows (
                    order_id VARCHAR(32) NOT NULL PRIMARY KEY,
                    order_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.update("INSERT INTO workspace_live_rows VALUES (?, ?)",
                "LIVE-001", "SHIPPED");
        jdbc.update("INSERT INTO workspace_candidate_rows VALUES (?, ?)",
                "DRAFT-001", "READY");
        jdbc.update("INSERT INTO workspace_candidate_rows VALUES (?, ?)",
                "DRAFT-002", "READY");
        jdbc.update("INSERT INTO workspace_candidate_v2_rows VALUES (?, ?)",
                "DRAFT-V2-001", "APPROVED");

        livePath = Files.createDirectories(tempDirectory.resolve("live-source"));
        write(livePath.resolve("shared/" + SCRIPT),
                "export const tableName = 'workspace_live_rows';\n");
        write(livePath.resolve("model/" + TABLE_MODEL + ".tm"), tableModel());
        write(livePath.resolve("query/" + QUERY_MODEL + ".qm"), queryModel());
        assertThat(bundlesContext.addExternalBundle(
                SOURCE_BUNDLE, NAMESPACE, livePath.toString(), false)).isTrue();
        selectedBundle = (ExternalFileBundle) bundlesContext.getBundleByName(
                SOURCE_BUNDLE, false);

        properties = new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(
                tempDirectory.resolve("runtime-bundles.json").toString());
        properties.getAuthoringWorkspaces().setPath(
                tempDirectory.resolve("workspace-store").toString());
        properties.getAuthoringWorkspaces().setPublishedBundlesPath(
                tempDirectory.resolve("published-bundles").toString());
        registry = new RuntimeBundleRegistryService(
                properties, bundlesContext, objectMapper);
        registry.save(registry.newRecord(
                SOURCE_BUNDLE, NAMESPACE, livePath.toString(), false, true));
        store =
                new RuntimeAuthoringWorkspaceStore(properties, objectMapper);
        inventory = new RuntimeBundleInventoryService(bundlesContext, registry);
        RuntimeCandidateQueryService candidateQuery =
                new RuntimeCandidateQueryService(
                        registry, bundlesContext, candidateQueryFactory);
        service = new RuntimeAuthoringWorkspaceService(
                store, inventory, bundlesContext, provider(sourceRegistry),
                provider(validationFactory), provider(candidateQuery));
        RuntimePublishedBundleArtifactStore artifactStore =
                new RuntimePublishedBundleArtifactStore(
                        properties, objectMapper, null, registry);
        publicationService = new RuntimeAuthoringWorkspacePublicationService(
                properties, store, service, artifactStore, registry, bundlesContext,
                modelOperations, new RuntimeAuthoringPublicationLock(),
                provider(sourceRegistry), provider(catalogSnapshotStore));
    }

    @AfterEach
    void tearDown() {
        if (bundlesContext.containBundle(SOURCE_BUNDLE)) {
            bundlesContext.removeBundle(SOURCE_BUNDLE);
        }
    }

    @Test
    void validatedImmutableWorkspaceRevisionExecutesRealSqliteDraft()
            throws Exception {
        CatalogSnapshot catalogBefore = catalogSnapshotStore.current(NAMESPACE)
                .orElse(null);
        Map<String, Fsscript> scriptsBefore = new LinkedHashMap<>(
                rootFsscriptLoader.getPath2Fsscript());
        List<Bundle> bundlesBefore = List.copyOf(bundlesContext.getBundleList());
        Map<String, String> selectedCacheBefore = new LinkedHashMap<>(
                selectedBundle.getName2Path());
        String sourceRevisionBefore = sourceRegistry.currentRevision(NAMESPACE);

        var created = service.create(NAMESPACE,
                new AuthoringWorkspaceCreateRequest(null, SOURCE_BUNDLE));
        var saved = service.save(created.workspaceId(),
                new AuthoringWorkspaceSaveRequest(
                        created.candidateRevision(),
                        List.of(new AuthoringWorkspaceSaveRequest.ResourceFile(
                                "shared/" + SCRIPT,
                                "export const tableName = 'workspace_candidate_rows';\n"))));
        AuthoringWorkspaceInfo validated;
        try {
            validated = service.validate(
                    saved.workspaceId(), saved.candidateRevision());
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw new AssertionError("Validation evidence: "
                    + service.get(saved.workspaceId()).lastValidation(), failure);
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId", "status"));
        request.setLimit(10);
        SemanticQueryRequest.OrderItem order =
                new SemanticQueryRequest.OrderItem();
        order.setField("orderId");
        order.setDir("asc");
        request.setOrderBy(List.of(order));

        var validation = service.query(
                validated.workspaceId(), QUERY_MODEL,
                validated.candidateRevision(), request,
                "Bearer workspace-data", Phase.VALIDATE);
        var execution = service.query(
                validated.workspaceId(), QUERY_MODEL,
                validated.candidateRevision(), request,
                "Bearer workspace-data", Phase.EXECUTE);

        assertThat(validated.state()).isEqualTo(AuthoringWorkspaceState.VALIDATED);
        assertThat(validation.response().getItems()).isNull();
        assertThat(execution.candidateRevision())
                .isEqualTo(validated.candidateRevision());
        assertThat(execution.baseBundleRevision())
                .isEqualTo(validated.baseBundleRevision());
        assertThat(execution.response().getItems())
                .extracting(row -> row.get("orderId"))
                .containsExactly("DRAFT-001", "DRAFT-002");
        assertThat(execution.response().getItems())
                .allSatisfy(row -> assertThat(row.get("status"))
                        .isEqualTo("READY"));
        assertThat(Files.readString(livePath.resolve("shared/" + SCRIPT)))
                .contains("workspace_live_rows");

        assertThat(catalogSnapshotStore.current(NAMESPACE).orElse(null))
                .isSameAs(catalogBefore);
        assertThat(rootFsscriptLoader.getPath2Fsscript())
                .containsExactlyInAnyOrderEntriesOf(scriptsBefore);
        assertThat(bundlesContext.getBundleList())
                .containsExactlyElementsOf(bundlesBefore);
        assertThat(selectedBundle.getName2Path())
                .containsExactlyInAnyOrderEntriesOf(selectedCacheBefore);
        assertThat(sourceRegistry.currentRevision(NAMESPACE))
                .isEqualTo(sourceRevisionBefore);
    }

    @Test
    void publishedWorkspaceRevisionBecomesTheRealLiveSqliteQuerySource()
            throws Exception {
        var created = service.create(NAMESPACE,
                new AuthoringWorkspaceCreateRequest(null, SOURCE_BUNDLE));
        var saved = service.save(created.workspaceId(),
                new AuthoringWorkspaceSaveRequest(
                        created.candidateRevision(),
                        List.of(new AuthoringWorkspaceSaveRequest.ResourceFile(
                                "shared/" + SCRIPT,
                                "export const tableName = 'workspace_candidate_rows';\n"))));
        AuthoringWorkspaceInfo validated = service.validate(
                saved.workspaceId(), saved.candidateRevision());

        AuthoringWorkspaceInfo published = publicationService.publish(
                validated.workspaceId(),
                new AuthoringWorkspacePublishRequest(
                        validated.candidateRevision(),
                        validated.baseBundleRevision(),
                        validated.baseNamespaceSourceRevision()));

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId", "status"));
        request.setLimit(10);
        SemanticQueryRequest.OrderItem order =
                new SemanticQueryRequest.OrderItem();
        order.setField("orderId");
        order.setDir("asc");
        request.setOrderBy(List.of(order));
        var live = semanticQueryService.queryModel(
                QUERY_MODEL, request, "execute",
                SemanticRequestContext.of(NAMESPACE, "Bearer live-query"));

        assertThat(published.state())
                .isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(published.lastPublication().status())
                .isEqualTo("PUBLISHED");
        assertThat(live.getItems())
                .extracting(row -> row.get("orderId"))
                .containsExactly("DRAFT-001", "DRAFT-002");
        assertThat(registry.find(SOURCE_BUNDLE).orElseThrow()
                .immutablePublication()).isTrue();
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
    }

    @Test
    void releasePackageRequiresProductionRevalidationThenAppliesAndRollsBackRealSqlite()
            throws Exception {
        AuthoringWorkspaceInfo development = validatedCandidate();
        RuntimeAuthoringReleasePackageService releases =
                new RuntimeAuthoringReleasePackageService(
                        properties, service, store, inventory);
        var release = releases.exportPackage(
                development.workspaceId(),
                new AuthoringReleaseExportRequest(
                        development.candidateRevision()));
        properties.getAuthoringWorkspaces().setProductionPromotionEnabled(true);

        AuthoringWorkspaceInfo imported = releases.importPackage(
                NAMESPACE, new AuthoringReleaseImportRequest(
                        NAMESPACE, SOURCE_BUNDLE, release));

        assertThat(imported.state()).isEqualTo(AuthoringWorkspaceState.DRAFT);
        assertThat(imported.lastValidation()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.query(
                        imported.workspaceId(), QUERY_MODEL,
                        imported.candidateRevision(), queryRequest(),
                        "Bearer production-query", Phase.EXECUTE))
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo("WORKSPACE_NOT_VALIDATED"));

        AuthoringWorkspaceInfo productionValidated = service.validate(
                imported.workspaceId(), imported.candidateRevision());
        var candidateQuery = service.query(
                imported.workspaceId(), QUERY_MODEL,
                imported.candidateRevision(), queryRequest(),
                "Bearer production-query", Phase.EXECUTE);
        assertThat(candidateQuery.response().getItems())
                .extracting(row -> row.get("orderId"))
                .containsExactly("DRAFT-001", "DRAFT-002");

        AuthoringWorkspaceInfo promoted = publicationService.promote(
                imported.workspaceId(), new AuthoringWorkspacePromotionRequest(
                        release.packageId(), imported.candidateRevision(),
                        imported.baseBundleRevision(),
                        imported.baseNamespaceSourceRevision()));
        assertThat(promoted.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(liveOrderIds()).containsExactly("DRAFT-001", "DRAFT-002");

        AuthoringWorkspaceInfo rolledBack = publicationService.rollback(
                imported.workspaceId(), new AuthoringWorkspaceRollbackRequest(
                        release.packageId(), imported.candidateRevision(),
                        promoted.lastPublication().attemptId()));
        assertThat(rolledBack.state())
                .isEqualTo(AuthoringWorkspaceState.ROLLED_BACK);
        assertThat(rolledBack.lastPublication().rollback().status())
                .isEqualTo("ROLLED_BACK");
        assertThat(liveOrderIds()).containsExactly("LIVE-001");
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
        assertThat(productionValidated.releaseImport().packageId())
                .isEqualTo(release.packageId());
    }

    @Test
    void secondWorkspacePublishesFromImmutableBaseAndPreservesFirstArtifact()
            throws Exception {
        AuthoringWorkspaceInfo firstValidated = validatedCandidate();
        AuthoringWorkspaceInfo firstPublished = publicationService.publish(
                firstValidated.workspaceId(), publishRequest(firstValidated));
        Path firstArtifact = Path.of(registry.find(SOURCE_BUNDLE)
                .orElseThrow().path());
        Map<String, String> firstArtifactFiles = textFileSnapshot(firstArtifact);

        AuthoringWorkspaceInfo secondValidated = validatedCandidateForTable(
                "workspace_candidate_v2_rows");
        AuthoringWorkspaceInfo secondPublished = publicationService.publish(
                secondValidated.workspaceId(), publishRequest(secondValidated));
        var secondRecord = registry.find(SOURCE_BUNDLE).orElseThrow();

        assertThat(firstPublished.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(secondValidated.baseBundleRevision())
                .isEqualTo(firstValidated.candidateRevision());
        assertThat(secondPublished.state()).isEqualTo(AuthoringWorkspaceState.PUBLISHED);
        assertThat(liveOrderIds()).containsExactly("DRAFT-V2-001");
        assertThat(secondRecord.artifactRevision())
                .isEqualTo(secondValidated.candidateRevision());
        Path secondArtifact = Path.of(secondRecord.path());
        assertThat(secondArtifact).isNotEqualTo(firstArtifact);
        assertThat(Files.readString(secondArtifact.resolve(
                "shared/" + SCRIPT))).contains("workspace_candidate_v2_rows");
        assertThat(Files.readString(secondArtifact.resolve(
                "model/" + TABLE_MODEL + ".tm")))
                .contains("candidate TM: workspace_candidate_v2_rows");
        assertThat(Files.readString(secondArtifact.resolve(
                "query/" + QUERY_MODEL + ".qm")))
                .contains("candidate QM: workspace_candidate_v2_rows");
        assertThat(textFileSnapshot(firstArtifact))
                .containsExactlyInAnyOrderEntriesOf(firstArtifactFiles);
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
    }

    @Test
    void liveQueriesDuringPublicationObserveOnlyCompleteOldOrNewCatalogs()
            throws Exception {
        AuthoringWorkspaceInfo validated = validatedCandidate();
        CountDownLatch sourceApplied = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        SystemBundlesContext controlledContext = spy(bundlesContext);
        doAnswer(invocation -> {
            boolean replaced = bundlesContext.replaceExternalBundle(
                    invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3));
            sourceApplied.countDown();
            if (!releasePublication.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "timed out waiting to release publication test window");
            }
            return replaced;
        }).when(controlledContext).replaceExternalBundle(
                eq(SOURCE_BUNDLE), eq(NAMESPACE), anyString(), anyBoolean());
        RuntimeAuthoringWorkspacePublicationService controlledPublication =
                new RuntimeAuthoringWorkspacePublicationService(
                        store, service, new RuntimePublishedBundleArtifactStore(
                        properties, objectMapper, null, registry), registry,
                        controlledContext, modelOperations,
                        new RuntimeAuthoringPublicationLock(),
                        provider(sourceRegistry), provider(catalogSnapshotStore));
        List<String> observations = new ArrayList<>();
        observations.add(classifyLiveQuery());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var publication = executor.submit(() -> controlledPublication.publish(
                    validated.workspaceId(), publishRequest(validated)));
            assertThat(sourceApplied.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 12; index++) {
                observations.add(classifyLiveQuery());
            }
            releasePublication.countDown();
            assertThat(publication.get(5, TimeUnit.SECONDS).state())
                    .isEqualTo(AuthoringWorkspaceState.PUBLISHED);
            observations.add(classifyLiveQuery());
        } finally {
            releasePublication.countDown();
            executor.shutdownNow();
        }

        assertThat(observations)
                .allMatch(Set.of("old", "new", "not-current")::contains)
                .contains("old", "new");
        assertThat(liveOrderIds()).containsExactly("DRAFT-001", "DRAFT-002");
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
    }

    @Test
    void refreshFailureAutomaticallyRestoresRealLiveSqliteQuery()
            throws Exception {
        AuthoringWorkspaceInfo validated = validatedCandidate();
        RuntimeModelOperations faulting = spy(modelOperations);
        doThrow(new IllegalStateException("injected publish refresh failure"))
                .doCallRealMethod()
                .when(faulting).refreshModels(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(NAMESPACE));
        RuntimeAuthoringWorkspacePublicationService publications =
                publications(faulting);

        Throwable publishFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> publications.publish(validated.workspaceId(),
                        publishRequest(validated)));
        assertThat(publishFailure)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class);
        assertThat(((RuntimeAuthoringWorkspaceException) publishFailure).code())
                .isEqualTo("WORKSPACE_PUBLISH_FAILED");

        assertThat(store.get(validated.workspaceId()).state())
                .isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(store.get(validated.workspaceId()).lastPublication().status())
                .isEqualTo("RECOVERED");
        assertThat(liveOrderIds()).containsExactly("LIVE-001");
        assertThat(registry.find(SOURCE_BUNDLE).orElseThrow().path())
                .isEqualTo(livePath.toString());
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
    }

    @Test
    void explicitRecoveryRestoresRealLiveSqliteQueryAfterRepeatedFailure()
            throws Exception {
        AuthoringWorkspaceInfo validated = validatedCandidate();
        RuntimeModelOperations faulting = spy(modelOperations);
        doThrow(new IllegalStateException("injected publish refresh failure"))
                .doThrow(new IllegalStateException("injected auto recovery failure"))
                .doCallRealMethod()
                .when(faulting).refreshModels(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(NAMESPACE));
        RuntimeAuthoringWorkspacePublicationService publications =
                publications(faulting);
        Throwable publishFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> publications.publish(validated.workspaceId(),
                        publishRequest(validated)));
        assertThat(publishFailure)
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class);
        assertThat(((RuntimeAuthoringWorkspaceException) publishFailure).code())
                .isEqualTo("WORKSPACE_RECOVERY_REQUIRED");
        AuthoringWorkspaceInfo required = service.get(validated.workspaceId());

        AuthoringWorkspaceInfo recovered = publications.recover(
                validated.workspaceId(), new AuthoringWorkspaceRecoverRequest(
                        validated.candidateRevision(),
                        required.lastPublication().attemptId()));

        assertThat(recovered.state()).isEqualTo(AuthoringWorkspaceState.STALE);
        assertThat(recovered.lastPublication().status()).isEqualTo("RECOVERED");
        assertThat(liveOrderIds()).containsExactly("LIVE-001");
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElseThrow()
                .identity().sourceRevision().value())
                .isEqualTo(sourceRegistry.currentRevision(NAMESPACE));
    }

    private AuthoringWorkspaceInfo validatedCandidate() {
        return validatedCandidateForTable("workspace_candidate_rows");
    }

    private AuthoringWorkspaceInfo validatedCandidateForTable(String tableName) {
        var created = service.create(NAMESPACE,
                new AuthoringWorkspaceCreateRequest(null, SOURCE_BUNDLE));
        var saved = service.save(created.workspaceId(),
                new AuthoringWorkspaceSaveRequest(
                        created.candidateRevision(),
                        List.of(
                                new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "shared/" + SCRIPT,
                                        "export const tableName = '" + tableName
                                                + "';\n"),
                                new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "model/" + TABLE_MODEL + ".tm",
                                        "// candidate TM: " + tableName + "\n"
                                                + tableModel()),
                                new AuthoringWorkspaceSaveRequest.ResourceFile(
                                        "query/" + QUERY_MODEL + ".qm",
                                        "// candidate QM: " + tableName + "\n"
                                                + queryModel()))));
        return service.validate(saved.workspaceId(), saved.candidateRevision());
    }

    private String classifyLiveQuery() {
        try {
            List<Object> ids = liveOrderIds();
            if (ids.equals(List.of("LIVE-001"))) {
                return "old";
            }
            if (ids.equals(List.of("DRAFT-001", "DRAFT-002"))) {
                return "new";
            }
            throw new AssertionError("mixed live query result: " + ids);
        } catch (RuntimeException failure) {
            String message = String.valueOf(failure.getMessage());
            assertThat(message)
                    .containsAnyOf("SOURCE_REVISION", "CATALOG_ADMISSION",
                            "not current", "NOT_CURRENT", "STALE");
            return "not-current";
        }
    }

    private RuntimeAuthoringWorkspacePublicationService publications(
            RuntimeModelOperations operations
    ) {
        return new RuntimeAuthoringWorkspacePublicationService(
                properties, store, service, new RuntimePublishedBundleArtifactStore(
                        properties, objectMapper, null, registry), registry,
                bundlesContext, operations, new RuntimeAuthoringPublicationLock(),
                provider(sourceRegistry), provider(catalogSnapshotStore));
    }

    private List<Object> liveOrderIds() {
        SemanticQueryRequest request = queryRequest();
        return semanticQueryService.queryModel(
                        QUERY_MODEL, request, "execute",
                        SemanticRequestContext.of(
                                NAMESPACE, "Bearer live-query"))
                .getItems().stream()
                .map(row -> row.get("orderId"))
                .toList();
    }

    private static SemanticQueryRequest queryRequest() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("orderId", "status"));
        request.setLimit(10);
        SemanticQueryRequest.OrderItem order =
                new SemanticQueryRequest.OrderItem();
        order.setField("orderId");
        order.setDir("asc");
        request.setOrderBy(List.of(order));
        return request;
    }

    private static AuthoringWorkspacePublishRequest publishRequest(
            AuthoringWorkspaceInfo workspace
    ) {
        return new AuthoringWorkspacePublishRequest(
                workspace.candidateRevision(), workspace.baseBundleRevision(),
                workspace.baseNamespaceSourceRevision());
    }

    private static String tableModel() {
        return """
                import { tableName } from '../shared/%s';
                export const model = {
                    name: '%s',
                    type: 'jdbc',
                    tableName: tableName,
                    properties: [
                        { column: 'order_id', name: 'orderId', type: 'STRING' },
                        { column: 'order_status', name: 'status', type: 'STRING' }
                    ],
                    measures: []
                };
                """.formatted(SCRIPT, TABLE_MODEL);
    }

    private static String queryModel() {
        return """
                const source = loadTableModel('%s');
                export const queryModel = {
                    name: '%s',
                    model: source,
                    columnGroups: [{
                        caption: 'workspace fields',
                        items: [
                            { ref: source.orderId },
                            { ref: source.status }
                        ]
                    }],
                    accesses: []
                };
                """.formatted(TABLE_MODEL, QUERY_MODEL);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static Map<String, String> textFileSnapshot(Path root)
            throws IOException {
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                snapshot.put(root.relativize(path).toString(),
                        Files.readString(path));
            }
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFoggyFramework(bundleName = "runtime-authoring-workspace-real-test")
    static class TestApplication {

        @Bean
        DataSource dataSource() throws IOException {
            Path database = Files.createTempFile(
                    "runtime-authoring-workspace-", ".sqlite");
            database.toFile().deleteOnExit();
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setUrl("jdbc:sqlite:" + database.toAbsolutePath());
            return dataSource;
        }

        @Bean
        NamedDataSourceResolver namedDataSourceResolver(DataSource dataSource) {
            return new NamedDataSourceResolver() {
                @Override
                public DataSource resolve(String name) {
                    return null;
                }

                @Override
                public DataSource resolveDefault(String namespace) {
                    return NAMESPACE.equals(namespace) ? dataSource : null;
                }

                @Override
                public boolean isConfigured(String name) {
                    return false;
                }
            };
        }

        @Bean
        RuntimeModelOperations runtimeModelOperations(
                SemanticModelCatalogService catalogService,
                SemanticServiceV3 semanticService,
                DetachedModelValidationFactory validationFactory,
                ObjectProvider<DatasetProperties> datasetProperties,
                ObjectProvider<CatalogSnapshotStore> catalogStore,
                ObjectProvider<CatalogRefreshCoordinator> refreshCoordinator
        ) {
            return new RuntimeModelOperations(
                    catalogService, semanticService, validationFactory,
                    datasetProperties, catalogStore, refreshCoordinator);
        }
    }
}
