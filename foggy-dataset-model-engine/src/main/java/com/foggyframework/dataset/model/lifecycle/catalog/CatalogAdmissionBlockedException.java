package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

/** Stable fail-closed signal for a source change whose safe scope is not yet rebuilt. */
public final class CatalogAdmissionBlockedException extends RuntimeException {

    private final String namespace;
    private final String code;
    private final String diagnostic;

    public CatalogAdmissionBlockedException(String namespace, String diagnostic) {
        this(namespace, diagnosticCode(diagnostic), diagnostic);
    }

    private CatalogAdmissionBlockedException(
            String namespace,
            String code,
            String diagnostic
    ) {
        super(code + ": catalog admission is blocked for namespace '"
                + CatalogIdentity.canonicalNamespace(namespace) + "'");
        this.namespace = CatalogIdentity.canonicalNamespace(namespace);
        this.code = code;
        this.diagnostic = diagnostic;
    }

    public String namespace() {
        return namespace;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public String code() {
        return code;
    }

    private static String diagnosticCode(String diagnostic) {
        return diagnostic != null
                && diagnostic.startsWith("DATASOURCE_BINDING_NOT_CURRENT:")
                ? "DATASOURCE_BINDING_NOT_CURRENT"
                : "REFRESH_SCOPE_UNKNOWN";
    }
}
