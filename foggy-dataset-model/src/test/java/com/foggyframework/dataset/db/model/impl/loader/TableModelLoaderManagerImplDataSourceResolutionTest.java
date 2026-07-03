package com.foggyframework.dataset.db.model.impl.loader;

import com.foggyframework.dataset.db.model.def.DbModelDef;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
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
    void shouldFallBackToDefaultWhenNoNamespaceBindingExists() {
        MarkerDataSource defaultDataSource = new MarkerDataSource("default");
        TableModelLoaderManagerImpl manager = manager(defaultDataSource, new StubNamedDataSourceResolver());

        DbModelDef def = modelDef("OrderModel");

        assertSame(defaultDataSource, manager.resolveDataSource(def, "tenant-a"));
    }

    private static TableModelLoaderManagerImpl manager(
            DataSource defaultDataSource,
            NamedDataSourceResolver resolver
    ) {
        TableModelLoaderManagerImpl manager = new TableModelLoaderManagerImpl(
                null,
                null,
                List.of(),
                List.of(),
                resolver
        );
        manager.setDataSource(defaultDataSource);
        return manager;
    }

    private static DbModelDef modelDef(String name) {
        DbModelDef def = new DbModelDef();
        def.setName(name);
        return def;
    }

    private static final class StubNamedDataSourceResolver implements NamedDataSourceResolver {

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
