package com.foggyframework.dataset.model.semantic.service.impl;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticQueryServiceV3ImplRawSqlRoutingTest {

    @Test
    void executeSqlUsesRouteModelDataSourceInComposeNamespace() {
        SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();
        DataSource defaultDataSource = new FailingDataSource("default-datasource");
        DataSource routeDataSource = new FailingDataSource("route-datasource");
        QueryModelLoader loader = mock(QueryModelLoader.class);
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getDataSource()).thenReturn(routeDataSource);
        when(loader.getJdbcQueryModel(eq("wwi_sales_analysis"), eq("wwi")))
                .thenReturn(queryModel);
        ReflectionTestUtils.setField(service, "dataSource", defaultDataSource);
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);

        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(
                ComposeRuntimeBundle.builder()
                        .ctx(context("wwi"))
                        .semanticService(mock(SemanticQueryServiceV3.class))
                        .build());
        try {
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.executeSql("select * from Fact.Sale", List.of(), "wwi_sales_analysis"));

            assertTrue(ex.getMessage().contains("route-datasource"));
            assertFalse(ex.getMessage().contains("default-datasource"));
            verify(loader).getJdbcQueryModel("wwi_sales_analysis", "wwi");
        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    private static ComposeQueryContext context(String namespace) {
        return ComposeQueryContext.builder()
                .principal(Principal.builder().userId("test").build())
                .namespace(namespace)
                .authorityResolver(request -> AuthorityResolution.builder()
                        .bindings(Map.of())
                        .build())
                .build();
    }

    private static final class FailingDataSource implements DataSource {
        private final String id;

        private FailingDataSource(String id) {
            this.id = id;
        }

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
