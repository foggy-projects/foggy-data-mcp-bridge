package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.model.candidate.CandidateQueryFactory;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private RuntimeAuthoringWorkspaceService service;
    private ExternalFileBundle selectedBundle;
    private Path livePath;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS workspace_live_rows");
        jdbc.execute("DROP TABLE IF EXISTS workspace_candidate_rows");
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
        jdbc.update("INSERT INTO workspace_live_rows VALUES (?, ?)",
                "LIVE-001", "SHIPPED");
        jdbc.update("INSERT INTO workspace_candidate_rows VALUES (?, ?)",
                "DRAFT-001", "READY");
        jdbc.update("INSERT INTO workspace_candidate_rows VALUES (?, ?)",
                "DRAFT-002", "READY");

        livePath = Files.createDirectories(tempDirectory.resolve("live-source"));
        write(livePath.resolve("shared/" + SCRIPT),
                "export const tableName = 'workspace_live_rows';\n");
        write(livePath.resolve("model/" + TABLE_MODEL + ".tm"), tableModel());
        write(livePath.resolve("query/" + QUERY_MODEL + ".qm"), queryModel());
        assertThat(bundlesContext.addExternalBundle(
                SOURCE_BUNDLE, NAMESPACE, livePath.toString(), false)).isTrue();
        selectedBundle = (ExternalFileBundle) bundlesContext.getBundleByName(
                SOURCE_BUNDLE, false);

        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(
                tempDirectory.resolve("runtime-bundles.json").toString());
        properties.getAuthoringWorkspaces().setPath(
                tempDirectory.resolve("workspace-store").toString());
        RuntimeBundleRegistryService registry = new RuntimeBundleRegistryService(
                properties, bundlesContext, objectMapper);
        registry.save(registry.newRecord(
                SOURCE_BUNDLE, NAMESPACE, livePath.toString(), false, true));
        RuntimeAuthoringWorkspaceStore store =
                new RuntimeAuthoringWorkspaceStore(properties, objectMapper);
        RuntimeBundleInventoryService inventory =
                new RuntimeBundleInventoryService(bundlesContext, registry);
        RuntimeCandidateQueryService candidateQuery =
                new RuntimeCandidateQueryService(
                        registry, bundlesContext, candidateQueryFactory);
        service = new RuntimeAuthoringWorkspaceService(
                store, inventory, bundlesContext, provider(sourceRegistry),
                provider(validationFactory), provider(candidateQuery));
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
    }
}
