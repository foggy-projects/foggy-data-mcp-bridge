package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.core.AnalyticsBundleIndex;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.AnalyticsDefinitionResolver;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException;

import java.util.List;
import java.util.Objects;

/** Read/validate operations over host-trusted Bundle registrations only. */
public final class AnalyticsBundleFunctionOperations {

    private final List<AnalyticsBundleRegistration> registrations;
    private final AnalyticsBundleStore bundleStore;
    private final AnalyticsDefinitionResolver definitionResolver;

    public AnalyticsBundleFunctionOperations(
            List<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleStore bundleStore) {
        this(registrations, bundleStore, null);
    }

    public AnalyticsBundleFunctionOperations(
            List<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleStore bundleStore,
            AnalyticsDefinitionResolver definitionResolver) {
        this.registrations = List.copyOf(Objects.requireNonNull(
                registrations, "registrations"));
        this.bundleStore = Objects.requireNonNull(bundleStore, "bundleStore");
        this.definitionResolver = definitionResolver;
    }

    public AnalyticsBundleList list() {
        return new AnalyticsBundleList(registrations.stream()
                .map(this::resolveDescription)
                .toList());
    }

    public AnalyticsBundleDescription validate(
            String bundleRef,
            String expectedBundleRevision) {
        return resolveExact(bundleRef, expectedBundleRevision);
    }

    public AnalyticsBundleDescription describe(
            String bundleRef,
            String expectedBundleRevision) {
        return resolveExact(bundleRef, expectedBundleRevision);
    }

    public AnalyticsArtifactDescription describeArtifact(
            String bundleRef,
            String artifactKind,
            String artifactRef,
            String expectedBundleRevision) {
        if (definitionResolver == null) {
            throw new IllegalStateException(
                    "Analytics artifact inspection is unavailable");
        }
        AnalyticsArtifactKind kind = switch (artifactKind) {
            case "report" -> AnalyticsArtifactKind.REPORT;
            case "dashboard" -> AnalyticsArtifactKind.DASHBOARD;
            default -> throw new IllegalArgumentException(
                    "artifactKind must be report or dashboard");
        };
        AnalyticsArtifactRef exactArtifact = new AnalyticsArtifactRef(kind, artifactRef);
        AnalyticsBundleIndex index = definitionResolver.resolve(
                new AnalyticsBundleRef(bundleRef),
                new AnalyticsBundleRevision(expectedBundleRevision));
        switch (kind) {
            case REPORT -> index.report(exactArtifact).orElseThrow(() ->
                    new AnalyticsRenderException(
                            AnalyticsRenderException.Code.REPORT_NOT_FOUND,
                            "Analytics Report does not exist"));
            case DASHBOARD -> index.dashboard(exactArtifact).orElseThrow(() ->
                    new AnalyticsRenderException(
                            AnalyticsRenderException.Code.DASHBOARD_NOT_FOUND,
                            "Analytics Dashboard does not exist"));
        }
        return new AnalyticsArtifactDescription(
                index.manifest().bundleRef().value(),
                index.bundleRevision().value(),
                artifactKind,
                artifactRef);
    }

    public boolean artifactInspectionAvailable() {
        return definitionResolver != null;
    }

    public int configuredBundleCount() {
        return registrations.size();
    }

    private AnalyticsBundleDescription resolveExact(
            String bundleRef,
            String expectedBundleRevision) {
        ResolvedAnalyticsBundle resolved = bundleStore.resolve(
                new AnalyticsBundleRef(bundleRef));
        if (expectedBundleRevision != null) {
            AnalyticsBundleRevision expected = new AnalyticsBundleRevision(
                    expectedBundleRevision);
            if (!expected.equals(resolved.bundleRevision())) {
                throw new AnalyticsBundleStoreException(
                        AnalyticsBundleStoreException.Code.REVISION_CONFLICT,
                        "Expected Analytics Bundle revision is not current");
            }
        }
        return validDescription(resolved);
    }

    private AnalyticsBundleDescription resolveDescription(
            AnalyticsBundleRegistration registration) {
        try {
            return validDescription(bundleStore.resolve(registration.bundleRef()));
        } catch (AnalyticsBundleStoreException unavailable) {
            return new AnalyticsBundleDescription(
                    registration.bundleRef().value(),
                    null,
                    null,
                    null,
                    registration.sourceState().name(),
                    null,
                    false,
                    false,
                    AnalyticsFunctionFailureMapper.bundleErrorCode(
                            unavailable.code()));
        }
    }

    private static AnalyticsBundleDescription validDescription(
            ResolvedAnalyticsBundle resolved) {
        return new AnalyticsBundleDescription(
                resolved.manifest().bundleRef().value(),
                resolved.manifest().bundleRevision().value(),
                resolved.manifest().schemaVersion().value(),
                resolved.manifest().namespaceRef().value(),
                resolved.lifecycle().sourceState().name(),
                resolved.lifecycle().dependencyState().name(),
                resolved.lifecycle().isWritable(),
                true,
                null);
    }
}
