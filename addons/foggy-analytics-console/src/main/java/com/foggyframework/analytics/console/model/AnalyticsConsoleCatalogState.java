package com.foggyframework.analytics.console.model;

import java.util.List;
import java.util.Objects;

/** Single-process atomic catalog snapshot owned only by Analytics Console. */
public record AnalyticsConsoleCatalogState(
        long revision,
        List<AnalyticsConsoleFolder> folders,
        List<AnalyticsConsoleAsset> assets,
        List<AnalyticsConsoleConversation> conversations) {

    public AnalyticsConsoleCatalogState {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        folders = List.copyOf(Objects.requireNonNull(folders, "folders"));
        assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
        conversations = List.copyOf(Objects.requireNonNull(conversations, "conversations"));
    }

    public static AnalyticsConsoleCatalogState empty() {
        return new AnalyticsConsoleCatalogState(0, List.of(), List.of(), List.of());
    }
}
