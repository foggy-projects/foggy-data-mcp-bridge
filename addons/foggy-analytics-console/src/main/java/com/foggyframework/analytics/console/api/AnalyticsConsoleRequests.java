package com.foggyframework.analytics.console.api;

import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetKind;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;

/** Browser-safe request DTOs; owner, role, authority and FAP selections are absent. */
public final class AnalyticsConsoleRequests {

    private AnalyticsConsoleRequests() {
    }

    public record CreateFolder(@NotBlank String name, String parentFolderId) {
    }

    public record CreateDraft(
            @NotBlank String title,
            String description,
            String folderId,
            @NotNull AnalyticsConsoleAssetKind kind,
            @NotBlank String bundleRef,
            @NotBlank String artifactRef,
            @NotBlank String expectedBundleRevision,
            String definitionContent) {
    }

    public record SaveDefinition(
            @NotBlank String expectedBundleRevision,
            @NotBlank String definitionContent) {
    }

    public record ExactRevision(@NotBlank String expectedBundleRevision) {
    }

    public record Preview(
            @NotBlank String expectedBundleRevision,
            Map<String, Object> parameters,
            String timezone,
            String locale) {
    }

    public record UpdateAudience(
            @NotNull AnalyticsConsoleVisibility visibility,
            Set<String> viewerSubjectRefs) {
    }
}
