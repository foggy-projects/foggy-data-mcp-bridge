package com.foggyframework.analytics.runtime.api.service;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleListResponse;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleSummary;

import java.util.List;
import java.util.Objects;

/** Read/validate operations over host-trusted registrations only. */
public class AnalyticsBundleOperations {

    private final List<AnalyticsBundleRegistration> registrations;
    private final AnalyticsBundleStore bundleStore;

    public AnalyticsBundleOperations(
            List<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleStore bundleStore) {
        this.registrations = List.copyOf(
                Objects.requireNonNull(registrations, "registrations"));
        this.bundleStore = Objects.requireNonNull(bundleStore, "bundleStore");
    }

    public AnalyticsBundleListResponse list() {
        return new AnalyticsBundleListResponse(registrations.stream()
                .map(this::resolveSummary)
                .toList());
    }

    public AnalyticsBundleSummary validate(
            AnalyticsBundleRef bundleRef,
            String expectedBundleRevision) {
        ResolvedAnalyticsBundle resolved = bundleStore.resolve(bundleRef);
        if (expectedBundleRevision != null) {
            if (expectedBundleRevision.isBlank()
                    || !expectedBundleRevision.equals(expectedBundleRevision.trim())) {
                throw new IllegalArgumentException(
                        "expectedBundleRevision must be non-blank and trimmed");
            }
            AnalyticsBundleRevision expected = new AnalyticsBundleRevision(
                    expectedBundleRevision);
            if (!expected.equals(resolved.bundleRevision())) {
                throw new AnalyticsBundleStoreException(
                        AnalyticsBundleStoreException.Code.REVISION_CONFLICT,
                        "Expected Analytics Bundle revision is not current");
            }
        }
        return validSummary(resolved);
    }

    public int configuredBundleCount() {
        return registrations.size();
    }

    private AnalyticsBundleSummary resolveSummary(
            AnalyticsBundleRegistration registration) {
        try {
            return validSummary(bundleStore.resolve(registration.bundleRef()));
        } catch (AnalyticsBundleStoreException unavailable) {
            return new AnalyticsBundleSummary(
                    registration.bundleRef().value(),
                    null,
                    null,
                    null,
                    registration.sourceState().name(),
                    null,
                    false,
                    false,
                    unavailable.code().name());
        }
    }

    private static AnalyticsBundleSummary validSummary(ResolvedAnalyticsBundle resolved) {
        return new AnalyticsBundleSummary(
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
