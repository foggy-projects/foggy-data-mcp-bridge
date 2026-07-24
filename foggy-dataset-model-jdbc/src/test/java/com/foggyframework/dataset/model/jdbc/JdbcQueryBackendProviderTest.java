package com.foggyframework.dataset.model.jdbc;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcQueryBackendProviderTest {

    @Test
    void publishesStableFacadeThroughTypedQueryRole() {
        QueryFacade facade = request -> null;
        JdbcQueryBackendProvider provider = new JdbcQueryBackendProvider(facade);
        BackendProviderCatalog catalog = BackendProviderCatalog.of(List.of(provider));

        QueryBackendProvider resolved = catalog.require(
                JdbcQueryBackendProvider.JDBC,
                BackendCapability.QUERY,
                QueryBackendProvider.class);

        assertSame(provider, resolved);
        assertSame(facade, resolved.queryFacade());
        assertEquals(BackendId.of("jdbc"), provider.descriptor().backendId());
        assertEquals(java.util.Set.of(BackendCapability.QUERY), provider.descriptor().capabilities());
    }

    @Test
    void acceptsExplicitDialectIdentityAndRejectsMissingFacade() {
        QueryFacade facade = request -> null;
        BackendId mysql = BackendId.of("jdbc.mysql");
        JdbcQueryBackendProvider provider = new JdbcQueryBackendProvider(mysql, facade);

        assertEquals(mysql, provider.descriptor().backendId());
        assertThrows(NullPointerException.class, () -> new JdbcQueryBackendProvider(null));
    }
}
