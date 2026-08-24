package com.foggyframework.analytics.console.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Console product metadata pointing to one exact Java Analytics artifact. */
public record AnalyticsConsoleAsset(
        String assetId,
        String title,
        String description,
        String folderId,
        String ownerSubjectRef,
        AnalyticsConsoleAssetKind kind,
        String bundleRef,
        String artifactRef,
        String resourcePath,
        String bundleRevision,
        String validatedBundleRevision,
        AnalyticsConsoleAssetStatus status,
        AnalyticsConsoleVisibility visibility,
        Set<String> viewerSubjectRefs,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {

    public AnalyticsConsoleAsset {
        assetId = required(assetId, "assetId");
        title = required(title, "title");
        description = description == null ? "" : description.strip();
        folderId = optional(folderId);
        ownerSubjectRef = required(ownerSubjectRef, "ownerSubjectRef");
        kind = Objects.requireNonNull(kind, "kind");
        bundleRef = required(bundleRef, "bundleRef");
        artifactRef = required(artifactRef, "artifactRef");
        resourcePath = required(resourcePath, "resourcePath");
        bundleRevision = required(bundleRevision, "bundleRevision");
        validatedBundleRevision = optional(validatedBundleRevision);
        status = Objects.requireNonNull(status, "status");
        visibility = Objects.requireNonNull(visibility, "visibility");
        viewerSubjectRefs = Set.copyOf(Objects.requireNonNull(
                viewerSubjectRefs, "viewerSubjectRefs"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (status == AnalyticsConsoleAssetStatus.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("publishedAt is required for PUBLISHED assets");
        }
    }

    public AnalyticsConsoleAsset withRevision(String revision, Instant now) {
        return new AnalyticsConsoleAsset(
                assetId, title, description, folderId, ownerSubjectRef, kind,
                bundleRef, artifactRef, resourcePath, revision, null, status,
                visibility, viewerSubjectRefs, createdAt, now, publishedAt);
    }

    public AnalyticsConsoleAsset validated(String revision, Instant now) {
        return new AnalyticsConsoleAsset(
                assetId, title, description, folderId, ownerSubjectRef, kind,
                bundleRef, artifactRef, resourcePath, bundleRevision, revision,
                status, visibility, viewerSubjectRefs, createdAt, now, publishedAt);
    }

    public AnalyticsConsoleAsset published(Instant now) {
        return new AnalyticsConsoleAsset(
                assetId, title, description, folderId, ownerSubjectRef, kind,
                bundleRef, artifactRef, resourcePath, bundleRevision,
                validatedBundleRevision, AnalyticsConsoleAssetStatus.PUBLISHED,
                visibility, viewerSubjectRefs, createdAt, now, now);
    }

    public AnalyticsConsoleAsset withAudience(
            AnalyticsConsoleVisibility nextVisibility,
            Set<String> nextViewers,
            Instant now) {
        return new AnalyticsConsoleAsset(
                assetId, title, description, folderId, ownerSubjectRef, kind,
                bundleRef, artifactRef, resourcePath, bundleRevision,
                validatedBundleRevision, status, nextVisibility, nextViewers,
                createdAt, now, publishedAt);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
