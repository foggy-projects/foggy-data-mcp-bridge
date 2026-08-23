package com.foggyframework.analytics.function.contract;

/** Bundle identity and state without host path, owner or ACL fields. */
public record AnalyticsBundleDescription(
        String bundleRef,
        String bundleRevision,
        String definitionSchemaVersion,
        String namespaceRef,
        String sourceState,
        String dependencyState,
        boolean writable,
        boolean valid,
        String errorCode) {

    public AnalyticsBundleDescription {
        bundleRef = AnalyticsFunctionValues.requireLogicalRef(
                "bundleRef", bundleRef);
        sourceState = AnalyticsFunctionValues.requireText("sourceState", sourceState);
        if (valid) {
            bundleRevision = AnalyticsFunctionValues.requireRevision(
                    "bundleRevision", bundleRevision);
            definitionSchemaVersion = AnalyticsFunctionValues.requireText(
                    "definitionSchemaVersion", definitionSchemaVersion);
            namespaceRef = AnalyticsFunctionValues.requireText(
                    "namespaceRef", namespaceRef);
            dependencyState = AnalyticsFunctionValues.requireText(
                    "dependencyState", dependencyState);
            if (errorCode != null) {
                throw new IllegalArgumentException(
                        "valid Bundle description must not contain errorCode");
            }
        } else {
            errorCode = AnalyticsFunctionValues.requireText("errorCode", errorCode);
        }
    }
}
