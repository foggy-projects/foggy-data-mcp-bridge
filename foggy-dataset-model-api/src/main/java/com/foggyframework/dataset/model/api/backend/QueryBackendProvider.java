package com.foggyframework.dataset.model.api.backend;

import com.foggyframework.dataset.model.api.QueryFacade;

/** Small provider role for backends that expose governed query execution. */
public interface QueryBackendProvider extends BackendProvider {

    QueryFacade queryFacade();
}
