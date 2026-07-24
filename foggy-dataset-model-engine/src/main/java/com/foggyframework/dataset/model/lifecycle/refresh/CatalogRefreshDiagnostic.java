package com.foggyframework.dataset.model.lifecycle.refresh;

/** Sanitized, credential-free diagnostic produced by the core refresh boundary. */
public record CatalogRefreshDiagnostic(
        String code,
        String target,
        String message
) {

    public CatalogRefreshDiagnostic {
        code = requireText(code, "code");
        target = target == null ? "" : target.trim();
        message = requireText(message, "message");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
