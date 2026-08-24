package com.foggyframework.analytics.console.model;

public enum AnalyticsConsoleAssetKind {
    REPORT("report", "reports"),
    DASHBOARD("dashboard", "dashboards");

    private final String runtimeKind;
    private final String resourceDirectory;

    AnalyticsConsoleAssetKind(String runtimeKind, String resourceDirectory) {
        this.runtimeKind = runtimeKind;
        this.resourceDirectory = resourceDirectory;
    }

    public String runtimeKind() {
        return runtimeKind;
    }

    public String resourceDirectory() {
        return resourceDirectory;
    }
}
