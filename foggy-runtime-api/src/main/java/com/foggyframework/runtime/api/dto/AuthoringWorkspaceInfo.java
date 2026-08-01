package com.foggyframework.runtime.api.dto;

import java.util.List;

public record AuthoringWorkspaceInfo(
        String workspaceId,
        String targetNamespace,
        String sourceBundle,
        String sourceKind,
        String baseBundleRevision,
        String baseNamespaceSourceRevision,
        String candidateRevision,
        AuthoringWorkspaceState state,
        String createdAt,
        String updatedAt,
        ValidationEvidence lastValidation,
        PublicationEvidence lastPublication,
        ReleaseImportEvidence releaseImport,
        List<String> diagnostics
) {
    public AuthoringWorkspaceInfo {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Compatibility constructor retaining the pre-publication Java surface. */
    public AuthoringWorkspaceInfo(
            String workspaceId,
            String targetNamespace,
            String sourceBundle,
            String sourceKind,
            String baseBundleRevision,
            String baseNamespaceSourceRevision,
            String candidateRevision,
            AuthoringWorkspaceState state,
            String createdAt,
            String updatedAt,
            ValidationEvidence lastValidation,
            List<String> diagnostics
    ) {
        this(workspaceId, targetNamespace, sourceBundle, sourceKind,
                baseBundleRevision, baseNamespaceSourceRevision,
                candidateRevision, state, createdAt, updatedAt,
                lastValidation, null, null, diagnostics);
    }

    public record ValidationEvidence(
            boolean valid,
            String candidateRevision,
            String baseBundleRevision,
            String baseNamespaceSourceRevision,
            String validatedAt,
            int totalFiles,
            int validFiles,
            int invalidFiles,
            int cascadingErrors,
            List<ValidationIssue> issues
    ) {
        public ValidationEvidence {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record ValidationIssue(
            String path,
            String type,
            String code,
            String message,
            String category
    ) {
    }

    public record PublicationEvidence(
            String attemptId,
            String status,
            String candidateRevision,
            String baseBundleRevision,
            String appliedBundleRevision,
            String baseNamespaceSourceRevision,
            String publishedNamespaceSourceRevision,
            String beforeCatalogGeneration,
            String afterCatalogGeneration,
            String recoveredCatalogGeneration,
            String startedAt,
            String completedAt,
            List<String> diagnostics,
            RollbackEvidence rollback
    ) {
        public PublicationEvidence {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        /** Compatibility constructor retaining the pre-rollback JSON/Java surface. */
        public PublicationEvidence(
                String attemptId,
                String status,
                String candidateRevision,
                String baseBundleRevision,
                String appliedBundleRevision,
                String baseNamespaceSourceRevision,
                String publishedNamespaceSourceRevision,
                String beforeCatalogGeneration,
                String afterCatalogGeneration,
                String recoveredCatalogGeneration,
                String startedAt,
                String completedAt,
                List<String> diagnostics
        ) {
            this(attemptId, status, candidateRevision, baseBundleRevision,
                    appliedBundleRevision, baseNamespaceSourceRevision,
                    publishedNamespaceSourceRevision, beforeCatalogGeneration,
                    afterCatalogGeneration, recoveredCatalogGeneration,
                    startedAt, completedAt, diagnostics, null);
        }
    }

    public record RollbackEvidence(
            String status,
            String startedAt,
            String rolledBackNamespaceSourceRevision,
            String rolledBackCatalogGeneration,
            String completedAt,
            String forwardRecoveredNamespaceSourceRevision,
            String forwardRecoveredCatalogGeneration,
            List<String> diagnostics
    ) {
        public RollbackEvidence {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    public record ReleaseImportEvidence(
            String packageId,
            String formatVersion,
            String sourceRuntimeApiVersion,
            String sourceNamespace,
            String sourceBundle,
            String exportedCandidateRevision,
            String importedAt
    ) {
    }
}
