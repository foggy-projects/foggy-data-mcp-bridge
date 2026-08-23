package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;

import java.util.List;
import java.util.Objects;

/** Read/validate operations over host-trusted Bundle registrations only. */
public final class AnalyticsBundleFunctionOperations {

    private final List<AnalyticsBundleRegistration> registrations;
    private final AnalyticsBundleStore bundleStore;

    public AnalyticsBundleFunctionOperations(
            List<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleStore bundleStore) {
        this.registrations = List.copyOf(Objects.requireNonNull(
                registrations, "registrations"));
        this.bundleStore = Objects.requireNonNull(bundleStore, "bundleStore");
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
