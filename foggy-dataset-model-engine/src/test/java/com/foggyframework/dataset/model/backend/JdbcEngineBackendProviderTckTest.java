package com.foggyframework.dataset.model.backend;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.tck.BackendProviderTck;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class JdbcEngineBackendProviderTckTest
        extends BackendProviderTck<JdbcEngineBackendProvider> {

    @Override
    protected JdbcEngineBackendProvider createProvider() {
        QueryFacade queryFacade = request -> null;
        return new JdbcEngineBackendProvider(
                queryFacade,
                mock(QueryModelLoaderImpl.class),
                mock(CatalogRefreshCoordinator.class));
    }

    @Override
    protected BackendId expectedBackendId() {
        return JdbcQueryBackendProvider.JDBC;
    }

    @Override
    protected Set<BackendCapability> expectedCapabilities() {
        return Set.of(
                BackendCapability.QUERY,
                BackendCapability.MODEL_LOAD,
                BackendCapability.ATOMIC_REFRESH);
    }

    @Override
    protected Class<JdbcEngineBackendProvider> expectedProviderRole() {
        return JdbcEngineBackendProvider.class;
    }

    @Override
    protected void verifyOperationalPort(JdbcEngineBackendProvider provider) {
        assertNotNull(provider.queryFacade());
        assertNotNull(provider.modelLoader());
        assertNotNull(provider.atomicRefresh());
    }
}
