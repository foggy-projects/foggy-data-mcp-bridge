package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

import java.util.List;
import java.util.Objects;

/** Stable core failure envelope for a refresh that published no candidate. */
public final class CatalogRefreshException extends RuntimeException {

    private final String code;
    private final CatalogRefreshRequest request;
    private final CatalogIdentity beforeIdentity;
    private final CatalogAdmissionState catalogState;
    private final List<CatalogRefreshDiagnostic> diagnostics;

    public CatalogRefreshException(
            String code,
            CatalogRefreshRequest request,
            CatalogIdentity beforeIdentity,
            CatalogAdmissionState catalogState,
            List<CatalogRefreshDiagnostic> diagnostics,
            Throwable cause
    ) {
        super(requireCode(code) + ": namespace='"
                + Objects.requireNonNull(request, "request").namespace() + "'", cause);
        this.code = code;
        this.request = request;
        this.beforeIdentity = beforeIdentity;
        this.catalogState = Objects.requireNonNull(catalogState, "catalogState");
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public String code() {
        return code;
    }

    public CatalogRefreshRequest request() {
        return request;
    }

    public CatalogIdentity beforeIdentity() {
        return beforeIdentity;
    }

    public CatalogAdmissionState catalogState() {
        return catalogState;
    }

    public List<CatalogRefreshDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code.trim();
    }
}
