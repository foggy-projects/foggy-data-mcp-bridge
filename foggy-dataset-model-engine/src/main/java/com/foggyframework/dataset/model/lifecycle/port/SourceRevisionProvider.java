package com.foggyframework.dataset.model.lifecycle.port;

import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;

/** Read-only model-owned port for the committed source view. */
public interface SourceRevisionProvider {

    SourceRevision currentSourceRevision(String namespace);
}
