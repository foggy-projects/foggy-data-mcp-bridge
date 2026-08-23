package com.foggyframework.analytics.runtime.api.dto;

/** Bundle identity and validation state without any host filesystem location. */
public record AnalyticsBundleSummary(
        String bundleRef,
        String bundleRevision,
        String definitionSchemaVersion,
        String namespaceRef,
        String sourceState,
        String dependencyState,
        boolean writable,
        boolean valid,
        String errorCode) {
}
