package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptImportBinding;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceClosureRevision;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceContentRevision;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoggyCatalogStableModelRevisionReadPortTest {

    private static final String QUERY = "SalesQuery";
    private static final String TABLE = "SalesTable";
    private static final String BUNDLE = "stable-model-test";
    private static final String QUERY_PATH = "memory:/SalesQuery.qm";
    private static final String TABLE_PATH = "memory:/SalesTable.tm";
    private static final String IMPORT_PATH = "memory:/shared.fsscript";

    @Test
    void derivesDeterministicRevisionFromModelAndImportContentClosure() {
        RevisionFixture first = fixture("export const scale = 1;");
        RevisionFixture same = fixture("export const scale = 1;");
        RevisionFixture changedImport = fixture("export const scale = 2;");

        AnalyticsModelRevision firstRevision = first.queryRevision().orElseThrow();

        assertEquals(firstRevision, same.queryRevision().orElseThrow());
        assertNotEquals(firstRevision, changedImport.queryRevision().orElseThrow());
        assertNotEquals(firstRevision, first.tableRevision().orElseThrow());
    }

    @Test
    void failsClosedWhenRequestedCatalogIdentityIsNotCurrent() {
        RevisionFixture fixture = fixture("export const scale = 1;");
        FoggyModelRevisionLookup wrongCatalog = new FoggyModelRevisionLookup(
                FoggyAdapterTestFixtures.CATALOG_IDENTITY,
                "qm",
                QUERY);

        assertTrue(fixture.port().findRevision(wrongCatalog).isEmpty());
    }

    @Test
    void failsClosedWhenCatalogSourceClosureRevisionIsUnavailable() {
        RevisionFixture fixture = fixture("export const scale = 1;", false);

        assertTrue(fixture.queryRevision().isEmpty());
    }

    private static RevisionFixture fixture(String importedContent) {
        return fixture(importedContent, true);
    }

    private static RevisionFixture fixture(
            String importedContent,
            boolean includeSourceClosureRevision) {
        Bundle bundle = bundle(BUNDLE);
        TestFsscript imported = script(bundle, IMPORT_PATH, "shared.fsscript", importedContent);
        TestFsscript table = script(
                bundle,
                TABLE_PATH,
                "SalesTable.tm",
                "export const model = { name: 'SalesTable' };",
                imported);
        TestFsscript query = script(
                bundle,
                QUERY_PATH,
                "SalesQuery.qm",
                "const table = loadTableModel('SalesTable'); export const queryModel = { name: 'SalesQuery', model: table };",
                imported);
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot snapshot;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.resetForNamespaceRefresh(Set.of(QUERY));
            ModelProvenance tableProvenance = new ModelProvenance(
                    TABLE,
                    ModelProvenance.ModelKind.TABLE,
                    candidate.sourceRevision(),
                    Set.of(),
                    Map.of(),
                    true,
                    List.of(),
                    new ModelProvenance.ModelSource(
                            BUNDLE,
                            "",
                            TABLE_PATH,
                            includeSourceClosureRevision
                                    ? FsscriptSourceClosureRevision.calculate(table)
                                    .orElseThrow()
                                    : null));
            candidate.putTableModel(
                    TABLE,
                    FoggyAdapterTestFixtures.tableModel(TABLE),
                    tableProvenance);
            ModelProvenance queryProvenance = new ModelProvenance(
                    QUERY,
                    ModelProvenance.ModelKind.QUERY,
                    candidate.sourceRevision(),
                    Set.of(CatalogModelKey.table(TABLE)),
                    Map.of(),
                    true,
                    List.of(),
                    new ModelProvenance.ModelSource(
                            BUNDLE,
                            "",
                            QUERY_PATH,
                            includeSourceClosureRevision
                                    ? FsscriptSourceClosureRevision.calculate(query)
                                    .orElseThrow()
                                    : null));
            candidate.putQueryModel(
                    QUERY,
                    FoggyAdapterTestFixtures.queryModel(
                            QUERY,
                            candidate.aliasFor(QUERY)),
                    queryProvenance);
            snapshot = scope.commit();
        }
        return new RevisionFixture(
                new FoggyCatalogStableModelRevisionReadPort(store),
                snapshot);
    }

    private static TestFsscript script(
            Bundle bundle,
            String path,
            String filename,
            String content,
            TestFsscript... imports) {
        ByteArrayResource resource = new ByteArrayResource(
                content.getBytes(StandardCharsets.UTF_8),
                filename) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new TestFsscript(
                path,
                new ResourceFsscriptClosureDefinitionSpace(
                        new BundleResource(bundle, resource))
                        .newFsscriptClosureDefinition(),
                FsscriptSourceContentRevision.calculate(content),
                Set.of(imports));
    }

    private static Bundle bundle(String name) {
        return (Bundle) Proxy.newProxyInstance(
                Bundle.class.getClassLoader(),
                new Class<?>[]{Bundle.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "Bundle[" + name + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "getMode" -> 0;
                    case "findResources" -> new Resource[0];
                    case "findBundleResources" -> new BundleResource[0];
                    case "getSystemBundlesContext" -> (SystemBundlesContext) null;
                    case "getDefinition" -> (BundleDefinition) null;
                    case "loadFsscript" -> (Fsscript) null;
                    default -> null;
                });
    }

    private record RevisionFixture(
            FoggyCatalogStableModelRevisionReadPort port,
            CatalogSnapshot snapshot) {

        Optional<AnalyticsModelRevision> queryRevision() {
            return port.findRevision(new FoggyModelRevisionLookup(
                    snapshot.identity(),
                    "qm",
                    QUERY));
        }

        Optional<AnalyticsModelRevision> tableRevision() {
            return port.findRevision(new FoggyModelRevisionLookup(
                    snapshot.identity(),
                    "tm",
                    TABLE));
        }
    }

    private static final class TestFsscript implements Fsscript {

        private final String path;
        private final FsscriptClosureDefinition definition;
        private final String sourceContentRevision;
        private final Set<Fsscript> imports;

        private TestFsscript(
                String path,
                FsscriptClosureDefinition definition,
                String sourceContentRevision,
                Set<? extends Fsscript> imports) {
            this.path = path;
            this.definition = definition;
            this.sourceContentRevision = sourceContentRevision;
            this.imports = Set.copyOf(new LinkedHashSet<>(imports));
        }

        @Override
        public Object eval(ExpEvaluator evaluator) {
            return null;
        }

        @Override
        public FsscriptClosureDefinition getFsscriptClosureDefinition() {
            return definition;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Optional<String> getSourceContentRevision() {
            return Optional.of(sourceContentRevision);
        }

        @Override
        public List<FsscriptImportBinding> getDirectImportBindings() {
            return imports.stream()
                    .map(imported -> new FsscriptImportBinding(
                            imported.getPath(),
                            imported))
                    .toList();
        }

        @Override
        public ExpEvaluator newInstance(org.springframework.context.ApplicationContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasImport(Fsscript script) {
            return imports.contains(script)
                    || imports.stream().anyMatch(imported -> imported.hasImport(script));
        }
    }
}
