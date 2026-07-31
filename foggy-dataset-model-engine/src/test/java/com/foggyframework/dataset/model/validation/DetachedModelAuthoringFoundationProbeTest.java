package com.foggyframework.dataset.model.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.dataset.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.proxy.TableModelProxy;
import com.foggyframework.dataset.model.spi.DetachedQueryModelBuilderFactory;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.TableModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;

import javax.sql.DataSource;
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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Focused technical probes for the 9.5.3 Runtime model-authoring foundation.
 *
 * <p>The fixture deliberately uses the production detached validation factory,
 * production filesystem/JAR Bundle implementations, production FSScript
 * loaders, and production TM/QM loaders. Only the terminal TM/QM builders are
 * lightweight test doubles so no physical database is required.</p>
 */
class DetachedModelAuthoringFoundationProbeTest {

    private static final String NAMESPACE = "authoring-probe";

    private GenericApplicationContext applicationContext;

    @AfterEach
    void closeApplicationContext() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void draftBundleImportsOwnFsscriptAndExplicitlyOverlaysLiveResource(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path liveDirectory = createLiveExternalBundle(tempDirectory);
        Path jar = createJarBundle(tempDirectory);
        Fixture fixture = fixture(liveDirectory, jar);
        LiveState before = fixture.captureLiveState();

        Path draft = tempDirectory.resolve("draft-own-and-overlay");
        write(draft.resolve("shared/draft-marker.fsscript"), """
                export const tableMarker = 'draft-own-script';
                """);
        write(draft.resolve("shared/draft-query-marker.fsscript"), """
                export const queryName = 'DraftOwnScriptQuery';
                """);
        write(draft.resolve("model/DraftOwnScriptModel.tm"), """
                import { tableMarker } from '../shared/draft-marker.fsscript';
                export const model = {
                    name: 'DraftOwnScriptModel',
                    type: 'jdbc',
                    tableName: tableMarker
                };
                """);
        write(draft.resolve("model/OverlayModel.tm"), """
                export const model = {
                    name: 'OverlayModel',
                    type: 'jdbc',
                    tableName: 'draft-overlay'
                };
                """);
        write(draft.resolve("query/DraftOwnScriptQuery.qm"), """
                import { queryName } from '../shared/draft-query-marker.fsscript';
                const dependency = loadTableModel('DraftOwnScriptModel');
                export const queryModel = {
                    name: queryName,
                    model: dependency
                };
                """);

        BundleResource liveOverlay = fixture.liveBundles().findResourceByName(
                "OverlayModel.tm", NAMESPACE, true);
        assertThat(liveOverlay.getBundle().getName()).isEqualTo("live-external");

        try (DetachedModelValidationSession session = fixture.factory().open(
                "draft-authoring", NAMESPACE, draft.toString())) {
            Bundle source = session.sourceBundle();
            session.validateTableModel(
                    source.findBundleResource("DraftOwnScriptModel.tm", true),
                    NAMESPACE);
            session.validateTableModel(
                    source.findBundleResource("OverlayModel.tm", true),
                    NAMESPACE);
            session.validateQueryModel(
                    source.findBundleResource("DraftOwnScriptQuery.qm", true));
        }

        assertThat(fixture.probeTableLoader().loadOf("DraftOwnScriptModel"))
                .extracting(LoadObservation::bundleName, LoadObservation::tableName)
                .containsExactly("draft-authoring", "draft-own-script");
        assertThat(fixture.probeTableLoader().loadOf("OverlayModel"))
                .extracting(LoadObservation::bundleName, LoadObservation::tableName)
                .containsExactly("draft-authoring", "draft-overlay");
        assertLiveStateUnchanged(fixture, before);
    }

    @Test
    void draftQueryResolvesLiveExternalModelAndItsFsscript(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path liveDirectory = createLiveExternalBundle(tempDirectory);
        Path jar = createJarBundle(tempDirectory);
        Fixture fixture = fixture(liveDirectory, jar);
        LiveState before = fixture.captureLiveState();
        fixture.probeTableLoader().clearObservations();

        Path draft = tempDirectory.resolve("draft-external-dependency");
        write(draft.resolve("query/DraftExternalDependencyQuery.qm"), """
                const dependency = loadTableModel('LiveExternalModel');
                export const queryModel = {
                    name: 'DraftExternalDependencyQuery',
                    model: dependency
                };
                """);

        try (DetachedModelValidationSession session = fixture.factory().open(
                "draft-external-query", NAMESPACE, draft.toString())) {
            session.validateQueryModel(session.sourceBundle().findBundleResource(
                    "DraftExternalDependencyQuery.qm", true));
        }

        assertThat(fixture.probeTableLoader().loadOf("LiveExternalModel"))
                .extracting(LoadObservation::bundleName, LoadObservation::tableName)
                .containsExactly("live-external", "live-external-script");
        assertThat(fixture.liveCatalog().current(NAMESPACE).orElseThrow()
                .queryModels()).doesNotContainKey("DraftExternalDependencyQuery");
        assertThat(fixture.liveBundles().findResourceByName(
                "DraftExternalDependencyQuery.qm", NAMESPACE, false)).isNull();
        assertLiveStateUnchanged(fixture, before);
    }

    @Test
    void draftQueryResolvesActualJarModelAndItsFsscript(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path liveDirectory = createLiveExternalBundle(tempDirectory);
        Path jar = createJarBundle(tempDirectory);
        Fixture fixture = fixture(liveDirectory, jar);
        LiveState before = fixture.captureLiveState();
        fixture.probeTableLoader().clearObservations();

        BundleResource jarResource = fixture.liveBundles().findResourceByName(
                "JarDependencyModel.tm", NAMESPACE, true);
        assertThat(jarResource.getBundle().getMode()).isEqualTo(BundleImpl.MODE_JAR);
        assertThat(jarResource.getResource().isFile()).isFalse();
        assertThat(jarResource.getResource().getURL().getProtocol()).isEqualTo("jar");
        assertThat(fixture.liveBundles().listExternalBundles())
                .extracting(BundleDefinition::getName)
                .contains("live-external")
                .doesNotContain("read-only-jar");
        assertThat(fixture.liveBundles().findResourceByName(
                "JarDependencyModel.tm", "another-namespace", false)).isNull();

        Path draft = tempDirectory.resolve("draft-jar-dependency");
        write(draft.resolve("query/DraftJarDependencyQuery.qm"), """
                const dependency = loadTableModel('JarDependencyModel');
                export const queryModel = {
                    name: 'DraftJarDependencyQuery',
                    model: dependency
                };
                """);

        try (DetachedModelValidationSession session = fixture.factory().open(
                "draft-jar-query", NAMESPACE, draft.toString())) {
            session.validateQueryModel(session.sourceBundle().findBundleResource(
                    "DraftJarDependencyQuery.qm", true));
        }

        assertThat(fixture.probeTableLoader().loadOf("JarDependencyModel"))
                .extracting(LoadObservation::bundleName, LoadObservation::tableName)
                .containsExactly("read-only-jar", "jar-read-only-script");
        assertLiveStateUnchanged(fixture, before);
    }

    @Test
    void failedDetachedValidationDoesNotMutateLiveCatalogOrScriptCache(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path liveDirectory = createLiveExternalBundle(tempDirectory);
        Path jar = createJarBundle(tempDirectory);
        Fixture fixture = fixture(liveDirectory, jar);
        LiveState before = fixture.captureLiveState();

        Path draft = tempDirectory.resolve("draft-broken");
        write(draft.resolve("model/BrokenDraftModel.tm"), """
                export const model = {
                    name: ;
                };
                """);

        assertThatThrownBy(() -> {
            try (DetachedModelValidationSession session = fixture.factory().open(
                    "broken-draft", NAMESPACE, draft.toString())) {
                session.validateTableModel(session.sourceBundle().findBundleResource(
                        "BrokenDraftModel.tm", true), NAMESPACE);
            }
        }).isInstanceOf(RuntimeException.class);

        assertLiveStateUnchanged(fixture, before);
    }

    private Fixture fixture(Path liveDirectory, Path jar) {
        applicationContext = new GenericApplicationContext();
        SystemBundlesContextImpl liveBundles = new SystemBundlesContextImpl(List.of());
        liveBundles.setAppCtx(applicationContext);
        applicationContext.registerBean(
                com.foggyframework.bundle.SystemBundlesContext.class,
                () -> liveBundles);
        applicationContext.refresh();

        ExternalFileBundle external = externalBundle(
                liveBundles, "live-external", NAMESPACE, liveDirectory);
        BundleImpl jarBundle = jarBundle(liveBundles, jar);
        liveBundles.setBundleList(new ArrayList<>(List.of(external, jarBundle)));

        RootFsscriptLoader liveRootLoader = new RootFsscriptLoader(applicationContext);
        FileFsscriptLoader liveFileLoader =
                new FileFsscriptLoader(applicationContext, liveRootLoader, null);
        DataSource dataSource = mock(DataSource.class);
        ProbeTableModelLoader tableLoader = new ProbeTableModelLoader(dataSource);
        NamedDataSourceResolver dataSourceResolver = new NamedDataSourceResolver() {
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
        CatalogSnapshotStore liveCatalog = new CatalogSnapshotStore();
        TableModelLoaderManagerImpl liveTableManager = new TableModelLoaderManagerImpl(
                liveBundles,
                liveFileLoader,
                List.of(),
                List.of(tableLoader),
                dataSourceResolver,
                new DatasetProperties(),
                liveCatalog
        );
        liveTableManager.setDataSource(dataSource);

        ProbeQueryModelBuilder queryBuilder =
                new ProbeQueryModelBuilder(liveTableManager, dataSource);
        QueryModelLoaderImpl liveQueryLoader = new QueryModelLoaderImpl(
                liveTableManager,
                liveBundles,
                liveFileLoader,
                List.of(queryBuilder),
                liveCatalog
        );
        liveQueryLoader.setDefaultDataSource(dataSource);

        // Warm a real live TM and its relative FSScript import. This gives the
        // isolation assertions a non-empty catalog and script cache to protect.
        liveTableManager.load("LiveExternalModel", NAMESPACE);
        return new Fixture(
                new DefaultDetachedModelValidationFactory(
                        liveBundles, liveTableManager, liveQueryLoader),
                liveBundles,
                liveCatalog,
                liveRootLoader,
                external,
                tableLoader
        );
    }

    private static ExternalFileBundle externalBundle(
            SystemBundlesContextImpl context,
            String name,
            String namespace,
            Path root
    ) {
        ExternalBundleDefinition definition =
                new ExternalBundleDefinition(name, namespace, root.toString(), false);
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(name);
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(root.toString());
        bundle.setRootPath(root.toString());
        return bundle;
    }

    private static BundleImpl jarBundle(SystemBundlesContextImpl context, Path jar) {
        URI jarUri = jar.toUri();
        BundleDefinition definition = new ProbeBundleDefinition(
                "read-only-jar", "probe.readonly.jar", NAMESPACE);
        BundleImpl bundle = new BundleImpl(context);
        bundle.setName(definition.getName());
        bundle.setBundleDefinition(definition);
        bundle.setMode(BundleImpl.MODE_JAR);
        bundle.setRootPath("jar:" + jarUri + "!/");
        bundle.setBasePath("jar:" + jarUri + "!/foggy/templates");
        return bundle;
    }

    private static Path createLiveExternalBundle(Path tempDirectory) throws IOException {
        Path root = tempDirectory.resolve("live-external");
        write(root.resolve("shared/live-marker.fsscript"), """
                export const tableMarker = 'live-external-script';
                """);
        write(root.resolve("model/LiveExternalModel.tm"), """
                import { tableMarker } from '../shared/live-marker.fsscript';
                export const model = {
                    name: 'LiveExternalModel',
                    type: 'jdbc',
                    tableName: tableMarker
                };
                """);
        write(root.resolve("model/OverlayModel.tm"), """
                export const model = {
                    name: 'OverlayModel',
                    type: 'jdbc',
                    tableName: 'live-overlay'
                };
                """);
        return root;
    }

    private static Path createJarBundle(Path tempDirectory) throws IOException {
        Path jar = tempDirectory.resolve("read-only-models.jar");
        try (OutputStream output = Files.newOutputStream(jar);
             JarOutputStream jarOutput = new JarOutputStream(output)) {
            addJarDirectory(jarOutput, "foggy/");
            addJarDirectory(jarOutput, "foggy/templates/");
            addJarDirectory(jarOutput, "foggy/templates/shared/");
            addJarDirectory(jarOutput, "foggy/templates/model/");
            addJarEntry(jarOutput, "foggy/templates/shared/jar-marker.fsscript", """
                    export const tableMarker = 'jar-read-only-script';
                    """);
            addJarEntry(jarOutput, "foggy/templates/model/JarDependencyModel.tm", """
                    import { tableMarker } from '../shared/jar-marker.fsscript';
                    export const model = {
                        name: 'JarDependencyModel',
                        type: 'jdbc',
                        tableName: tableMarker
                    };
                    """);
        }
        return jar;
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

    private static void assertLiveStateUnchanged(Fixture fixture, LiveState before) {
        CatalogSnapshot after = fixture.liveCatalog().current(NAMESPACE).orElseThrow();
        assertThat(after).isSameAs(before.catalogSnapshot());
        assertThat(after.identity()).isEqualTo(before.catalogSnapshot().identity());
        assertThat(fixture.liveRootLoader().getPath2Fsscript())
                .containsExactlyInAnyOrderEntriesOf(before.liveScripts());
        assertThat(fixture.liveExternal().getName2Path())
                .containsExactlyInAnyOrderEntriesOf(before.externalPathCache());
        assertThat(fixture.liveBundles().getBundleList())
                .containsExactlyElementsOf(before.liveBundles());
        assertThat(fixture.liveBundles().getSourceRevisionRegistry()
                .currentRevision(NAMESPACE)).isEqualTo(before.sourceRevision());
    }

    private record Fixture(
            DetachedModelValidationFactory factory,
            SystemBundlesContextImpl liveBundles,
            CatalogSnapshotStore liveCatalog,
            RootFsscriptLoader liveRootLoader,
            ExternalFileBundle liveExternal,
            ProbeTableModelLoader probeTableLoader
    ) {
        private LiveState captureLiveState() {
            return new LiveState(
                    liveCatalog.current(NAMESPACE).orElseThrow(),
                    new LinkedHashMap<>(liveRootLoader.getPath2Fsscript()),
                    new LinkedHashMap<>(liveExternal.getName2Path()),
                    List.copyOf(liveBundles.getBundleList()),
                    liveBundles.getSourceRevisionRegistry().currentRevision(NAMESPACE)
            );
        }
    }

    private record LiveState(
            CatalogSnapshot catalogSnapshot,
            Map<String, Fsscript> liveScripts,
            Map<String, String> externalPathCache,
            List<Bundle> liveBundles,
            String sourceRevision
    ) {
    }

    private record ProbeBundleDefinition(
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

    private record LoadObservation(
            String modelName,
            String bundleName,
            String tableName
    ) {
    }

    private static final class ProbeTableModelLoader implements TableModelLoader {

        private final DataSource dataSource;
        private final List<LoadObservation> observations = new ArrayList<>();

        private ProbeTableModelLoader(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public TableModel load(Fsscript fsscript, DbModelDef definition, Bundle bundle) {
            observations.add(new LoadObservation(
                    definition.getName(), bundle.getName(), definition.getTableName()));
            DbTableModelImpl model = new DbTableModelImpl(dataSource, fsscript);
            definition.apply(model);
            model.setQueryObject(new TableQueryObject(
                    new SqlTable(definition.getTableName()), definition.getSchema()));
            return model;
        }

        @Override
        public String getTypeName() {
            return "jdbc";
        }

        private LoadObservation loadOf(String modelName) {
            return observations.stream()
                    .filter(observation -> modelName.equals(observation.modelName()))
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }

        private void clearObservations() {
            observations.clear();
        }
    }

    private static final class ProbeQueryModelBuilder
            implements QueryModelBuilder, DetachedQueryModelBuilderFactory {

        private final TableModelLoaderManager tableModelLoaderManager;
        private final DataSource dataSource;
        private final SqlFormulaService sqlFormulaService;

        private ProbeQueryModelBuilder(
                TableModelLoaderManager tableModelLoaderManager,
                DataSource dataSource
        ) {
            this(tableModelLoaderManager, dataSource, mock(SqlFormulaService.class));
        }

        private ProbeQueryModelBuilder(
                TableModelLoaderManager tableModelLoaderManager,
                DataSource dataSource,
                SqlFormulaService sqlFormulaService
        ) {
            this.tableModelLoaderManager = tableModelLoaderManager;
            this.dataSource = dataSource;
            this.sqlFormulaService = sqlFormulaService;
        }

        @Override
        public QueryModelSupport build(DbQueryModelDef definition, Fsscript fsscript) {
            TableModelProxy proxy = definition.getModel();
            TableModel tableModel = tableModelLoaderManager.load(
                    proxy.getModelName(), NamespaceContext.getNamespace());
            return new JdbcQueryModelImpl(
                    List.of(tableModel), fsscript, sqlFormulaService, dataSource);
        }

        @Override
        public QueryModelBuilder createDetachedQueryModelBuilder(
                TableModelLoaderManager detachedTableModelLoaderManager,
                com.foggyframework.bundle.SystemBundlesContext detachedBundlesContext,
                FileFsscriptLoader detachedFileFsscriptLoader
        ) {
            return new ProbeQueryModelBuilder(
                    detachedTableModelLoaderManager, dataSource, sqlFormulaService);
        }
    }
}
