package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

import java.util.Objects;

/** A detached candidate no longer matches the namespace view it captured. */
public final class StaleCatalogBuildException extends IllegalStateException {

    private final String namespace;
    private final Reason reason;

    public StaleCatalogBuildException(String namespace, Reason reason) {
        super("STALE_CATALOG_BUILD: " + Objects.requireNonNull(reason, "reason")
                + " namespace='" + CatalogIdentity.canonicalNamespace(namespace) + "'");
        this.namespace = CatalogIdentity.canonicalNamespace(namespace);
        this.reason = reason;
    }

    public String namespace() {
        return namespace;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        BASE_CATALOG_CHANGED,
        SOURCE_REVISION_CHANGED
    }
}
