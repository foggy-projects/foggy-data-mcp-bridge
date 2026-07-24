package com.foggyframework.dataset.model.jdbc;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.tck.BackendProviderTck;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

class JdbcQueryBackendProviderTckTest extends BackendProviderTck<QueryBackendProvider> {

    private final QueryFacade queryFacade = request -> null;

    @Override
    protected QueryBackendProvider createProvider() {
        return new JdbcQueryBackendProvider(queryFacade);
    }

    @Override
    protected BackendId expectedBackendId() {
        return JdbcQueryBackendProvider.JDBC;
    }

    @Override
    protected Set<BackendCapability> expectedCapabilities() {
        return Set.of(BackendCapability.QUERY);
    }

    @Override
    protected Class<QueryBackendProvider> expectedProviderRole() {
        return QueryBackendProvider.class;
    }

    @Override
    protected void verifyOperationalPort(QueryBackendProvider provider) {
        assertSame(queryFacade, provider.queryFacade());
    }
}
