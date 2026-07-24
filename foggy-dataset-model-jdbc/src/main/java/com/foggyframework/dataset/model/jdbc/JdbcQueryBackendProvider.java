package com.foggyframework.dataset.model.jdbc;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;

import java.util.Objects;
import java.util.Set;

/**
 * Compatibility adapter that publishes the governed JDBC query facade through
 * the small SPI v2 query-provider role.
 */
public final class JdbcQueryBackendProvider implements QueryBackendProvider {

    public static final BackendId JDBC = BackendId.of("jdbc");

    private final BackendDescriptor descriptor;
    private final QueryFacade queryFacade;

    public JdbcQueryBackendProvider(QueryFacade queryFacade) {
        this(JDBC, queryFacade);
    }

    public JdbcQueryBackendProvider(BackendId backendId, QueryFacade queryFacade) {
        this.descriptor = new BackendDescriptor(
                Objects.requireNonNull(backendId, "backendId must not be null"),
                Set.of(BackendCapability.QUERY));
        this.queryFacade = Objects.requireNonNull(queryFacade, "queryFacade must not be null");
    }

    @Override
    public BackendDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public QueryFacade queryFacade() {
        return queryFacade;
    }
}
