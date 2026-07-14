package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelValidateResponse(
        boolean valid,
        String namespace,
        String path,
        int totalFiles,
        int validFiles,
        int invalidFiles,
        int cascadingErrors,
        Long durationMs,
        List<ModelValidateIssue> errors,
        List<ModelValidateIssue> warnings,
        String beforeCatalogGeneration,
        String afterCatalogGeneration,
        String sourceRevision,
        List<DatasourceBindingGenerationSummary> affectedBindingGenerations,
        RuntimeCatalogState catalogState
) {
    public ModelValidateResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        affectedBindingGenerations = RuntimeLifecycleSanitizer.sortedBindings(
                affectedBindingGenerations);
        if (totalFiles < 0 || validFiles < 0 || invalidFiles < 0
                || cascadingErrors < 0) {
            throw new IllegalArgumentException("validation counts must not be negative");
        }
        if (durationMs != null && durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (catalogState == null) {
            throw new IllegalArgumentException("catalogState must not be null");
        }
    }

    /** Compatibility constructor retaining the original Runtime API Java surface. */
    public ModelValidateResponse(
            boolean valid,
            String namespace,
            String path,
            int totalFiles,
            int validFiles,
            int invalidFiles,
            int cascadingErrors,
            Long durationMs,
            List<ModelValidateIssue> errors,
            List<ModelValidateIssue> warnings
    ) {
        this(valid, namespace, path, totalFiles, validFiles, invalidFiles, cascadingErrors,
                durationMs, errors, warnings, null, null, null, List.of(),
                RuntimeCatalogState.ABSENT);
    }
}
