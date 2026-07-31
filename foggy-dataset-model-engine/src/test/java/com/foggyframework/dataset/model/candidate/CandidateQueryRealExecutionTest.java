package com.foggyframework.dataset.model.candidate;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(CandidateQueryRealExecutionTest.CacheProbeConfiguration.class)
class CandidateQueryRealExecutionTest extends EcommerceTestSupport {

    private static final String NAMESPACE = "";
    private static final String SOURCE_BUNDLE = "candidate-managed-source";
    private static final String EXTERNAL_BUNDLE = "candidate-external-dependency";
    private static final String JAR_BUNDLE = "candidate-jar-dependency";
    private static final String OVERLAY_MODEL = "CandidateOverlayOrderModel";
    private static final String OVERLAY_QUERY = "CandidateOverlayOrderQuery";
    private static final String EXTERNAL_QUERY = "CandidateExternalOrderQuery";
    private static final String JAR_QUERY = "CandidateJarOrderQuery";
    private static final String BROKEN_QUERY = "CandidateBrokenDatabaseQuery";
    private static final String BROKEN_DSL_QUERY = "CandidateBrokenDslQuery";
    private static final String AUTHORIZATION = "candidate-allow";
    private static final String VALIDATION_AUTHORIZATION = "candidate-validate";

    @TempDir
    Path tempDirectory;

    @Resource
    private CandidateQueryFactory candidateQueryFactory;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private CommittedSourceRevisionRegistry sourceRevisionRegistry;

    @Resource
    private CatalogSnapshotStore catalogSnapshotStore;

    @Resource
    private RootFsscriptLoader rootFsscriptLoader;

    @Resource
    private RecordingQueryCacheProvider cacheProvider;

    private SystemBundlesContextImpl liveBundles;
    private List<Bundle> originalBundles;
    private ExternalFileBundle selectedBundle;
    private ExternalFileBundle externalDependency;
    private Path draftRoot;

    @BeforeEach
    void installBundleFixtures() throws Exception {
        liveBundles = (SystemBundlesContextImpl) systemBundlesContext;
        originalBundles = liveBundles.getBundleList();

        Path liveRoot = createLiveSelectedBundle();
        Path externalRoot = createExternalDependencyBundle();
        Path jar = createJarDependencyBundle();
        draftRoot = createDraftBundle();

        selectedBundle = externalBundle(
                SOURCE_BUNDLE, NAMESPACE, liveRoot);
        externalDependency = externalBundle(
                EXTERNAL_BUNDLE, NAMESPACE, externalRoot);
        BundleImpl jarDependency = jarBundle(jar);
        List<Bundle> fixtures = new ArrayList<>();
        fixtures.add(selectedBundle);
        fixtures.add(externalDependency);
        fixtures.add(jarDependency);
        fixtures.addAll(originalBundles);
        liveBundles.setBundleList(fixtures);
        cacheProvider.reset();
    }

    @AfterEach
    void restoreBundleFixtures() {
        if (liveBundles != null && originalBundles != null) {
            liveBundles.setBundleList(originalBundles);
        }
        if (cacheProvider != null) {
            cacheProvider.reset();
        }
    }

    @Test
    void draftOverlayExecutesWithModelPermissionRowFilterAndNoSharedCache() {
        LiveState before = captureLiveState();
        SemanticQueryRequest request = request("orderId", "status");

        try (CandidateQuerySession session = openSession()) {
            CandidateQueryResult validation = session.validate(
                    OVERLAY_QUERY,
                    request,
                    SemanticRequestContext.of(NAMESPACE, VALIDATION_AUTHORIZATION)
                            .withPermissionAction(PermissionAction.EXECUTE)
            );
            CandidateQueryResult execution = session.execute(
                    OVERLAY_QUERY,
                    request,
                    SemanticRequestContext.of(NAMESPACE, AUTHORIZATION)
                            .withPermissionAction(PermissionAction.VALIDATE)
            );

            List<String> expectedIds = jdbcTemplate.queryForList(
                            "SELECT order_id FROM fact_order "
                                    + "WHERE order_status = 'COMPLETED' "
                                    + "ORDER BY order_id LIMIT 20",
                            String.class
                    );
            List<String> actualIds = execution.response().getItems().stream()
                    .map(row -> String.valueOf(row.get("orderId")))
                    .toList();
            assertThat(validation.response().getItems()).isNull();
            assertThat(actualIds).containsExactlyElementsOf(expectedIds);
            assertThat(execution.response().getItems())
                    .allSatisfy(row -> assertThat(row.get("status"))
                            .isEqualTo("COMPLETED"));
            assertThat(execution.identity().catalogIdentity().generation().value())
                    .contains(execution.identity().candidateRevision());
            assertThat(execution.diagnostics())
                    .contains("SHARED_L1_CACHE_DISABLED",
                            "SHARED_L2_CACHE_DISABLED",
                            "PREAGGREGATION_DISABLED");
        }

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    @Test
    void candidateQueriesExternalAndActualJarDependenciesReadOnly() {
        LiveState before = captureLiveState();

        try (CandidateQuerySession session = openSession()) {
            CandidateQueryResult external = session.execute(
                    EXTERNAL_QUERY,
                    request("externalOrderId"),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            );
            CandidateQueryResult jar = session.execute(
                    JAR_QUERY,
                    request("jarOrderId"),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            );

            assertThat(external.response().getItems()).isNotEmpty()
                    .allSatisfy(row -> assertThat(row.get("externalOrderId"))
                            .asString().startsWith("ORD"));
            assertThat(jar.response().getItems()).isNotEmpty()
                    .allSatisfy(row -> assertThat(row.get("jarOrderId"))
                            .asString().startsWith("ORD"));
        }

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    @Test
    void authorizationFieldAndPhysicalColumnFailuresRemainIsolated() {
        LiveState before = captureLiveState();

        try (CandidateQuerySession session = openSession()) {
            assertThatThrownBy(() -> session.execute(
                    OVERLAY_QUERY,
                    request("orderId"),
                    SemanticRequestContext.of(NAMESPACE, "candidate-deny")
            )).isInstanceOfSatisfying(
                    ModelPermissionException.class,
                    denied -> assertThat(denied.getCode())
                            .isEqualTo("MODEL_ACCESS_DENIED")
            );

            SemanticRequestContext fieldDenied = SemanticRequestContext
                    .of(NAMESPACE, AUTHORIZATION)
                    .withGovernance(Set.of("orderId"), null, null);
            assertThatThrownBy(() -> session.execute(
                    OVERLAY_QUERY,
                    request("orderId", "status"),
                    fieldDenied
            )).hasMessageContaining("status");

            SemanticRequestContext physicalDenied = SemanticRequestContext
                    .of(NAMESPACE, AUTHORIZATION)
                    .withGovernance(
                            Set.of("orderId", "status"),
                            List.of(new DeniedPhysicalColumn(
                                    null, "fact_order", "order_id")),
                            null
                    );
            assertThatThrownBy(() -> session.execute(
                    OVERLAY_QUERY,
                    request("orderId"),
                    physicalDenied
            )).hasMessageContaining("orderId");

            assertThatThrownBy(() -> session.execute(
                    BROKEN_QUERY,
                    request("brokenId"),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            )).hasMessageContaining("candidate_missing_table");
        }

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    @Test
    void candidateDslFailureLeavesLiveStateAndSharedCacheUntouched() {
        LiveState before = captureLiveState();

        try (CandidateQuerySession session = openSession()) {
            assertThatThrownBy(() -> session.validate(
                    BROKEN_DSL_QUERY,
                    request("brokenId"),
                    SemanticRequestContext.ofNamespace(NAMESPACE)
            )).isInstanceOf(RuntimeException.class);
        }

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    @Test
    void actualExternalBundleModelCannotBeShadowedByCandidate() throws IOException {
        LiveState before = captureLiveState();
        write(draftRoot.resolve("model/CandidateExternalOrderModel.tm"), tableModel(
                "CandidateExternalOrderModel",
                "../shared/draft-table.fsscript",
                "order_id",
                "externalOrderId"
        ));

        assertThatThrownBy(this::openSession)
                .isInstanceOf(CandidateQueryException.class)
                .satisfies(error -> {
                    CandidateQueryException failure =
                            (CandidateQueryException) error;
                    assertThat(failure.code()).isEqualTo(
                            CandidateQueryErrorCode.CANDIDATE_OVERLAY_FORBIDDEN);
                    assertThat(failure.resource()).isEqualTo(
                            "CandidateExternalOrderModel.tm");
                });

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    @Test
    void actualJarBundleModelCannotBeShadowedByCandidate() throws IOException {
        LiveState before = captureLiveState();
        write(draftRoot.resolve("model/CandidateJarOrderModel.tm"), tableModel(
                "CandidateJarOrderModel",
                "../shared/draft-table.fsscript",
                "order_id",
                "jarOrderId"
        ));

        assertThatThrownBy(this::openSession)
                .isInstanceOf(CandidateQueryException.class)
                .satisfies(error -> {
                    CandidateQueryException failure =
                            (CandidateQueryException) error;
                    assertThat(failure.code()).isEqualTo(
                            CandidateQueryErrorCode.CANDIDATE_OVERLAY_FORBIDDEN);
                    assertThat(failure.resource()).isEqualTo(
                            "CandidateJarOrderModel.tm");
                });

        assertThat(cacheProvider.interactions()).isZero();
        assertLiveStateUnchanged(before);
    }

    private CandidateQuerySession openSession() {
        return candidateQueryFactory.open(new CandidateQuerySource(
                SOURCE_BUNDLE,
                NAMESPACE,
                draftRoot.toString(),
                sourceRevisionRegistry.currentRevision(NAMESPACE)
        ));
    }

    private SemanticQueryRequest request(String... columns) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(columns));
        request.setLimit(20);
        SemanticQueryRequest.OrderItem order =
                new SemanticQueryRequest.OrderItem();
        order.setField(columns[0]);
        order.setDir("asc");
        request.setOrderBy(List.of(order));
        return request;
    }

    private LiveState captureLiveState() {
        return new LiveState(
                catalogSnapshotStore.current(NAMESPACE).orElse(null),
                new LinkedHashMap<>(rootFsscriptLoader.getPath2Fsscript()),
                List.copyOf(liveBundles.getBundleList()),
                new LinkedHashMap<>(selectedBundle.getName2Path()),
                new LinkedHashMap<>(externalDependency.getName2Path()),
                sourceRevisionRegistry.currentRevision(NAMESPACE)
        );
    }

    private void assertLiveStateUnchanged(LiveState before) {
        assertThat(catalogSnapshotStore.current(NAMESPACE).orElse(null))
                .isSameAs(before.catalogSnapshot());
        assertThat(rootFsscriptLoader.getPath2Fsscript())
                .containsExactlyInAnyOrderEntriesOf(before.liveScripts());
        assertThat(liveBundles.getBundleList())
                .containsExactlyElementsOf(before.bundleInventory());
        assertThat(selectedBundle.getName2Path())
                .containsExactlyInAnyOrderEntriesOf(before.selectedCache());
        assertThat(externalDependency.getName2Path())
                .containsExactlyInAnyOrderEntriesOf(before.externalCache());
        assertThat(sourceRevisionRegistry.currentRevision(NAMESPACE))
                .isEqualTo(before.sourceRevision());
    }

    private Path createLiveSelectedBundle() throws IOException {
        Path root = tempDirectory.resolve("live-selected");
        write(root.resolve("shared/live-table.fsscript"),
                "export const tableName = 'candidate_missing_live_table';\n");
        write(root.resolve("model/" + OVERLAY_MODEL + ".tm"), tableModel(
                OVERLAY_MODEL,
                "../shared/live-table.fsscript",
                "order_id",
                "orderId"
        ));
        write(root.resolve("query/" + OVERLAY_QUERY + ".qm"), queryModel(
                OVERLAY_QUERY, OVERLAY_MODEL, "orderId", false));
        return root;
    }

    private Path createExternalDependencyBundle() throws IOException {
        Path root = tempDirectory.resolve("external-dependency");
        write(root.resolve("shared/external-table.fsscript"),
                "export const tableName = 'fact_order';\n");
        write(root.resolve("model/CandidateExternalOrderModel.tm"), tableModel(
                "CandidateExternalOrderModel",
                "../shared/external-table.fsscript",
                "order_id",
                "externalOrderId"
        ));
        return root;
    }

    private Path createDraftBundle() throws IOException {
        Path root = tempDirectory.resolve("candidate-draft");
        write(root.resolve("shared/draft-table.fsscript"),
                "export const tableName = 'fact_order';\n");
        write(root.resolve("model/" + OVERLAY_MODEL + ".tm"), tableModel(
                OVERLAY_MODEL,
                "../shared/draft-table.fsscript",
                "order_id",
                "orderId"
        ));
        write(root.resolve("query/" + OVERLAY_QUERY + ".qm"), queryModel(
                OVERLAY_QUERY, OVERLAY_MODEL, "orderId", true));
        write(root.resolve("query/" + EXTERNAL_QUERY + ".qm"), queryModel(
                EXTERNAL_QUERY,
                "CandidateExternalOrderModel",
                "externalOrderId",
                false
        ));
        write(root.resolve("query/" + JAR_QUERY + ".qm"), queryModel(
                JAR_QUERY,
                "CandidateJarOrderModel",
                "jarOrderId",
                false
        ));
        write(root.resolve("model/CandidateBrokenDatabaseModel.tm"), """
                export const model = {
                    name: 'CandidateBrokenDatabaseModel',
                    type: 'jdbc',
                    tableName: 'candidate_missing_table',
                    properties: [
                        { column: 'broken_id', name: 'brokenId', type: 'STRING' }
                    ],
                    measures: []
                };
                """);
        write(root.resolve("query/" + BROKEN_QUERY + ".qm"), queryModel(
                BROKEN_QUERY,
                "CandidateBrokenDatabaseModel",
                "brokenId",
                false
        ));
        write(root.resolve("query/" + BROKEN_DSL_QUERY + ".qm"), """
                export const queryModel = {
                    name: ;
                };
                """);
        return root;
    }

    private Path createJarDependencyBundle() throws IOException {
        Path jar = tempDirectory.resolve("candidate-dependency.jar");
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream jarOutput = new JarOutputStream(output)) {
            addJarDirectory(jarOutput, "foggy/");
            addJarDirectory(jarOutput, "foggy/templates/");
            addJarDirectory(jarOutput, "foggy/templates/shared/");
            addJarDirectory(jarOutput, "foggy/templates/model/");
            addJarEntry(
                    jarOutput,
                    "foggy/templates/shared/jar-table.fsscript",
                    "export const tableName = 'fact_order';\n"
            );
            addJarEntry(
                    jarOutput,
                    "foggy/templates/model/CandidateJarOrderModel.tm",
                    tableModel(
                            "CandidateJarOrderModel",
                            "../shared/jar-table.fsscript",
                            "order_id",
                            "jarOrderId"
                    )
            );
        }
        return jar;
    }

    private static String tableModel(
            String model,
            String script,
            String column,
            String field
    ) {
        return """
                import { tableName } from '%s';
                export const model = {
                    name: '%s',
                    type: 'jdbc',
                    tableName: tableName,
                    properties: [
                        { column: '%s', name: '%s', type: 'STRING' },
                        { column: 'order_status', name: 'status', type: 'STRING' }
                    ],
                    measures: []
                };
                """.formatted(script, model, column, field);
    }

    private static String queryModel(
            String query,
            String model,
            String primaryField,
            boolean protectedModel
    ) {
        String permissions = protectedModel
                ? """
                    modelPermissions: {
                        mode: 'resolver',
                        resolver: (context) => {
                            if (context.authorization == 'candidate-validate') {
                                return { allow: context.action == 'VALIDATE' };
                            }
                            if (context.authorization != 'candidate-allow'
                                    || context.action != 'EXECUTE') {
                                return { allow: false };
                            }
                            return {
                                allow: true,
                                rowPredicates: [
                                    context.predicate.eq(source.status, 'COMPLETED')
                                ]
                            };
                        }
                    },
                """
                : "";
        return """
                const source = loadTableModel('%s');
                export const queryModel = {
                    name: '%s',
                    model: source,
                    %s
                    columnGroups: [{
                        caption: 'candidate fields',
                        items: [
                            { ref: source.%s },
                            { ref: source.status }
                        ]
                    }],
                    accesses: []
                };
                """.formatted(model, query, permissions, primaryField);
    }

    private ExternalFileBundle externalBundle(
            String name,
            String namespace,
            Path root
    ) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                name, namespace, root.toString(), false);
        ExternalFileBundle bundle = new ExternalFileBundle(liveBundles);
        bundle.setName(name);
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(root.toString());
        bundle.setRootPath(root.toString());
        return bundle;
    }

    private BundleImpl jarBundle(Path jar) {
        URI jarUri = jar.toUri();
        BundleDefinition definition = new TestBundleDefinition(
                JAR_BUNDLE, "candidate.readonly.jar", NAMESPACE);
        BundleImpl bundle = new BundleImpl(liveBundles);
        bundle.setName(definition.getName());
        bundle.setBundleDefinition(definition);
        bundle.setMode(BundleImpl.MODE_JAR);
        bundle.setRootPath("jar:" + jarUri + "!/");
        bundle.setBasePath("jar:" + jarUri + "!/foggy/templates");
        return bundle;
    }

    private static void addJarDirectory(JarOutputStream output, String name)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.closeEntry();
    }

    private static void addJarEntry(
            JarOutputStream output,
            String name,
            String content
    ) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private record LiveState(
            CatalogSnapshot catalogSnapshot,
            Map<String, Fsscript> liveScripts,
            List<Bundle> bundleInventory,
            Map<String, String> selectedCache,
            Map<String, String> externalCache,
            String sourceRevision
    ) {
    }

    private record TestBundleDefinition(
            String name,
            String packageName,
            String namespace
    ) implements BundleDefinition {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPackageName() {
            return packageName;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }
    }

    static final class RecordingQueryCacheProvider implements QueryCacheProvider {

        private final AtomicInteger interactions = new AtomicInteger();

        @Override
        public PagingResultImpl checkL1Cache(
                ModelResultContext context,
                String authorization
        ) {
            interactions.incrementAndGet();
            return null;
        }

        @Override
        public void writeL1Cache(
                ModelResultContext context,
                String authorization,
                PagingResultImpl result
        ) {
            interactions.incrementAndGet();
        }

        @Override
        public PagingResultImpl checkL2Cache(
                String modelName,
                String sql,
                List<?> params,
                ModelResultContext context
        ) {
            interactions.incrementAndGet();
            return null;
        }

        @Override
        public void writeL2Cache(
                String modelName,
                String sql,
                List<?> params,
                PagingResultImpl result,
                ModelResultContext context
        ) {
            interactions.incrementAndGet();
        }

        int interactions() {
            return interactions.get();
        }

        void reset() {
            interactions.set(0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheProbeConfiguration {

        @Bean
        @Primary
        RecordingQueryCacheProvider recordingQueryCacheProvider() {
            return new RecordingQueryCacheProvider();
        }
    }
}
