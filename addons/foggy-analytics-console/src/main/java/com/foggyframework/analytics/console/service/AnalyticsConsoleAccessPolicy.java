package com.foggyframework.analytics.console.service;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAssetStatus;
import com.foggyframework.analytics.console.model.AnalyticsConsoleVisibility;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;

/** Central product authorization rules; Java Analytics data authority stays downstream. */
final class AnalyticsConsoleAccessPolicy {

    static void requireEditableDraft(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleAsset asset) {
        if (asset.status() != AnalyticsConsoleAssetStatus.DRAFT || !canEdit(subject, asset)) {
            throw forbidden();
        }
    }

    static boolean canAdminister(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleAsset asset) {
        return subject.hasRole(AnalyticsConsoleRole.ADMIN)
                || asset.ownerSubjectRef().equals(subject.subjectRef());
    }

    static boolean canEdit(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleAsset asset) {
        return subject.hasRole(AnalyticsConsoleRole.ADMIN)
                || subject.hasRole(AnalyticsConsoleRole.DESIGNER)
                && asset.ownerSubjectRef().equals(subject.subjectRef());
    }

    static boolean canView(
            AnalyticsConsoleSubject subject,
            AnalyticsConsoleAsset asset) {
        if (asset.status() != AnalyticsConsoleAssetStatus.PUBLISHED) {
            return false;
        }
        return canAdminister(subject, asset)
                || asset.visibility() == AnalyticsConsoleVisibility.CONSOLE
                || asset.viewerSubjectRefs().contains(subject.subjectRef());
    }

    static void requireDesigner(AnalyticsConsoleSubject subject) {
        requireAuthenticated(subject);
        if (!subject.hasRole(AnalyticsConsoleRole.ADMIN)
                && !subject.hasRole(AnalyticsConsoleRole.DESIGNER)) {
            throw forbidden();
        }
    }

    static void requireAuthenticated(AnalyticsConsoleSubject subject) {
        if (subject == null) {
            throw forbidden();
        }
    }

    static AnalyticsConsoleCatalogException forbidden() {
        return new AnalyticsConsoleCatalogException(
                "ANALYTICS_CONSOLE_FORBIDDEN",
                "Analytics Console access is forbidden");
    }

    private AnalyticsConsoleAccessPolicy() {
    }
}
