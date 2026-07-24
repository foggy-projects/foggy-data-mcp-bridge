package com.foggyframework.dataset.model.tck;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;

import java.util.Set;

class BackendProviderTckSelfTest extends BackendProviderTck<QueryBackendProvider> {

    private static final BackendId SELF_TEST = BackendId.of("tck-self-test");

    @Override
    protected QueryBackendProvider createProvider() {
        BackendDescriptor descriptor = new BackendDescriptor(
                SELF_TEST, Set.of(BackendCapability.QUERY));
        QueryFacade queryFacade = request -> null;
        return new QueryBackendProvider() {
            @Override
            public BackendDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public QueryFacade queryFacade() {
                return queryFacade;
            }
        };
    }

    @Override
    protected BackendId expectedBackendId() {
        return SELF_TEST;
    }

    @Override
    protected Set<BackendCapability> expectedCapabilities() {
        return Set.of(BackendCapability.QUERY);
    }

    @Override
    protected Class<QueryBackendProvider> expectedProviderRole() {
        return QueryBackendProvider.class;
    }
}
