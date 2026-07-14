package com.foggyframework.dataset.db.model.lifecycle.port;

import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;

/** Raised when a publication was built from a source revision that is no longer committed. */
public final class StaleSourceRevisionException extends RuntimeException {

    private final String namespace;
    private final SourceRevision expected;
    private final SourceRevision current;

    public StaleSourceRevisionException(
            String namespace,
            SourceRevision expected,
            SourceRevision current
    ) {
        super("SOURCE_REVISION_STALE: namespace='"
                + CatalogIdentity.canonicalNamespace(namespace) + "'");
        this.namespace = CatalogIdentity.canonicalNamespace(namespace);
        this.expected = expected;
        this.current = current;
    }

    public String namespace() {
        return namespace;
    }

    public SourceRevision expected() {
        return expected;
    }

    public SourceRevision current() {
        return current;
    }
}
