package com.foggyframework.dataset.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.model.impl.utils.QueryObjectSupport;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TableModelLoaderManagerImplDataSourceResolutionTest {

    @Test
    void shouldResolveNamespaceDefaultWhenModelHasNoExplicitDatasource() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        MarkerDataSource namespaceDataSource = new MarkerDataSource("namespace");
        StubNamedDataSourceResolver resolver = new StubNamedDataSourceResolver()
                .withNamespaceDefault("tenant-a", namespaceDataSource);
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, resolver);

        DbModelDef def = modelDef("OrderModel");

        assertSame(namespaceDataSource, manager.resolveDataSource(def, "tenant-a"));
    }

    @Test
    void shouldKeepExplicitDataSourceAheadOfNamespaceDefault() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        MarkerDataSource explicitDataSource = new MarkerDataSource("explicit");
        MarkerDataSource namespaceDataSource = new MarkerDataSource("namespace");
        StubNamedDataSourceResolver resolver = new StubNamedDataSourceResolver()
                .withNamespaceDefault("tenant-a", namespaceDataSource);
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, resolver);

        DbModelDef def = modelDef("OrderModel");
        def.setDataSource(explicitDataSource);

        assertSame(explicitDataSource, manager.resolveDataSource(def, "tenant-a"));
    }

    @Test
    void shouldKeepNamedDataSourceAheadOfExplicitAndNamespaceDefaults() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        MarkerDataSource explicitDataSource = new MarkerDataSource("explicit");
        MarkerDataSource namedDataSource = new MarkerDataSource("named");
        MarkerDataSource namespaceDataSource = new MarkerDataSource("namespace");
        StubNamedDataSourceResolver resolver = new StubNamedDataSourceResolver()
                .withNamed("analytics", namedDataSource)
                .withNamespaceDefault("tenant-a", namespaceDataSource);
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, resolver);

        DbModelDef def = modelDef("OrderModel");
        def.setDataSourceName("analytics");
        def.setDataSource(explicitDataSource);

        assertSame(namedDataSource, manager.resolveDataSource(def, "tenant-a"));
    }

    @Test
    void shouldFailClosedWhenNoNamespaceBindingExists() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, new StubNamedDataSourceResolver());

        DbModelDef def = modelDef("OrderModel");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.resolveDataSource(def, "tenant-a"));

        assertTrue(ex.getMessage().contains("tenant-a"));
        assertTrue(ex.getMessage().contains("OrderModel"));
    }

    @Test
    void shouldRequireResolverForNonDefaultNamespace() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.resolveDataSource(modelDef("OrderModel"), "tenant-a"));

        assertTrue(ex.getMessage().contains("tenant-a"));
        assertTrue(ex.getMessage().contains("OrderModel"));
    }

    @Test
    void shouldAllowExplicitLegacyNamespaceFallback() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        DatasetProperties properties = new DatasetProperties();
        properties.getDatasource().setAllowGlobalFallbackForNamespace(true);
        TableModelLoaderManagerImpl manager = manager(
                defaultDataSource,
                new StubNamedDataSourceResolver(),
                properties
        );

        assertSame(defaultDataSource, manager.resolveDataSource(modelDef("OrderModel"), "tenant-a"));
    }

    @Test
    void legacyNamespaceFallbackMustNotApplyToExplicitNamedDataSource() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        DatasetProperties properties = new DatasetProperties();
        properties.getDatasource().setAllowGlobalFallbackForNamespace(true);
        TableModelLoaderManagerImpl manager = manager(
                defaultDataSource,
                new StubNamedDataSourceResolver(),
                properties
        );
        DbModelDef def = modelDef("OrderModel");
        def.setDataSourceName("missing");

        assertThrows(IllegalArgumentException.class,
                () -> manager.resolveDataSource(def, "tenant-a"));
    }

    @Test
    void resolverFailureMustNotBeConvertedToLegacyFallback() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        DatasetProperties properties = new DatasetProperties();
        properties.getDatasource().setAllowGlobalFallbackForNamespace(true);
        NamedDataSourceResolver failingResolver = new NamedDataSourceResolver() {
            @Override
            public DataSource resolve(String name) {
                throw new IllegalStateException("resolver unavailable");
            }

            @Override
            public DataSource resolveDefault(String namespace) {
                throw new IllegalStateException("resolver unavailable");
            }

            @Override
            public boolean isConfigured(String name) {
                throw new IllegalStateException("resolver unavailable");
            }
        };
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, failingResolver, properties);

        DbModelDef named = modelDef("NamedModel");
        named.setDataSourceName("analytics");
        assertThrows(IllegalStateException.class,
                () -> manager.resolveDataSource(named, "tenant-a"));
        assertThrows(IllegalStateException.class,
                () -> manager.resolveDataSource(modelDef("NamespaceModel"), "tenant-a"));
    }

    @Test
    void shouldFailClosedWhenExplicitNamedDataSourceIsMissing() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, new StubNamedDataSourceResolver());
        DbModelDef def = modelDef("OrderModel");
        def.setDataSourceName("missing");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.resolveDataSource(def, "tenant-a"));

        assertTrue(ex.getMessage().contains("missing"));
        assertTrue(ex.getMessage().contains("tenant-a"));
    }

    @Test
    void shouldRequireResolverForExplicitNamedDataSource() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, null);
        DbModelDef def = modelDef("OrderModel");
        def.setDataSourceName("analytics");

        assertThrows(IllegalStateException.class,
                () -> manager.resolveDataSource(def, "tenant-a"));
    }

    @Test
    void shouldUseGlobalDataSourceForDefaultNamespace() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, new StubNamedDataSourceResolver());

        assertSame(defaultDataSource, manager.resolveDataSource(modelDef("OrderModel"), null));
    }

    @Test
    void legacyResolverMustNotReceiveAnEmptyDefaultNamespaceLookup() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        NamedDataSourceResolver legacyResolver = new StubNamedDataSourceResolver() {
            @Override
            public DataSource resolveDefault(String namespace) {
                if (namespace == null || namespace.isBlank()) {
                    throw new IllegalArgumentException("legacy resolver requires a named namespace");
                }
                return super.resolveDefault(namespace);
            }
        };
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, legacyResolver);

        assertSame(defaultDataSource, manager.resolveDataSource(modelDef("OrderModel"), null));
    }

    @Test
    void optedInResolverCanProvideATrackedProcessLocalDefault() {
        MarkerDataSource globalDataSource = new MarkerDataSource("global");
        MarkerDataSource processLocalDataSource = new MarkerDataSource("process-local");
        TableModelLoaderManagerImpl manager = manager(
                globalDataSource,
                new ProcessLocalResolver(processLocalDataSource));

        assertSame(processLocalDataSource,
                manager.resolveDataSource(modelDef("OrderModel"), ""));
    }

    @Test
    void initializationMustUsePinnedDataSourceWithoutSecondResolverLookup() {
        NamedDataSourceResolver resolver = mock(NamedDataSourceResolver.class);
        TableModelLoaderManagerImpl manager = manager(new MarkerDataSource("default"), resolver);
        MarkerDataSource pinned = new MarkerDataSource("generation-one");
        DbModelDef def = modelDef("OrderModel");
        def.setDataSourceName("analytics");

        TableModel tableModel = mock(TableModel.class);
        TableModelSupport support = mock(TableModelSupport.class);
        QueryObject queryObject = mock(QueryObject.class);
        QueryObjectSupport queryObjectSupport = mock(QueryObjectSupport.class);
        when(tableModel.getDecorate(TableModelSupport.class)).thenReturn(support);
        when(support.getQueryObject()).thenReturn(queryObject);
        when(queryObject.getDecorate(QueryObjectSupport.class)).thenReturn(queryObjectSupport);
        when(support.getDimensions()).thenReturn(List.of());

        assertSame(support, manager.initialization(tableModel, def, mock(Bundle.class), pinned));

        verifyNoInteractions(resolver);
        verify(support).init();
    }

    private static TableModelLoaderManagerImpl manager(
            DataSource defaultDataSource,
            NamedDataSourceResolver resolver
    ) {
        return manager(defaultDataSource, resolver, new DatasetProperties());
    }

    private static TableModelLoaderManagerImpl manager(
            DataSource defaultDataSource,
            NamedDataSourceResolver resolver,
            DatasetProperties properties
    ) {
        TableModelLoaderManagerImpl manager = new TableModelLoaderManagerImpl(
                null,
                null,
                List.of(),
                List.of(),
                resolver,
                properties
        );
        manager.setDataSource(defaultDataSource);
        return manager;
    }

    private static DbModelDef modelDef(String name) {
        DbModelDef def = new DbModelDef();
        def.setName(name);
        return def;
    }

    private static class StubNamedDataSourceResolver implements NamedDataSourceResolver {

        private final Map<String, DataSource> named = new HashMap<>();
        private final Map<String, DataSource> namespaceDefaults = new HashMap<>();

        private StubNamedDataSourceResolver withNamed(String name, DataSource dataSource) {
            named.put(name, dataSource);
            return this;
        }

        private StubNamedDataSourceResolver withNamespaceDefault(String namespace, DataSource dataSource) {
            namespaceDefaults.put(namespace, dataSource);
            return this;
        }

        @Override
        public DataSource resolve(String name) {
            return named.get(name);
        }

        @Override
        public DataSource resolveDefault(String namespace) {
            return namespaceDefaults.get(namespace);
        }

        @Override
        public boolean isConfigured(String name) {
            return named.containsKey(name);
        }
    }

    private static final class ProcessLocalResolver extends StubNamedDataSourceResolver
            implements ProcessLocalDefaultDataSourceResolver {

        private final DataSource processLocalDataSource;

        private ProcessLocalResolver(DataSource processLocalDataSource) {
            this.processLocalDataSource = processLocalDataSource;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(
                    processLocalDataSource,
                    new DatasourceBindingIdentity(
                            "process-local:default",
                            "test-process-local",
                            new DatasourceBindingGeneration("test-generation")));
        }
    }

    private record MarkerDataSource(String id) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLFeatureNotSupportedException(id);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException(id);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException(id);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
