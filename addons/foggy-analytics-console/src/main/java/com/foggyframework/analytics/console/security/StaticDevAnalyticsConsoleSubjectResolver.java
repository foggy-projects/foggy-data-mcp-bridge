package com.foggyframework.analytics.console.security;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import jakarta.servlet.http.HttpServletRequest;

import java.util.EnumSet;

/** Explicit local-only subject resolver; never enabled by default. */
public final class StaticDevAnalyticsConsoleSubjectResolver
        implements AnalyticsConsoleSubjectResolver {

    private final AnalyticsConsoleSubject subject;

    public StaticDevAnalyticsConsoleSubjectResolver(AnalyticsConsoleProperties properties) {
        AnalyticsConsoleProperties.DevSubject configured = properties.getDevSubject();
        this.subject = new AnalyticsConsoleSubject(
                configured.getSubjectRef(),
                configured.getDisplayName(),
                configured.getRoles().isEmpty()
                        ? EnumSet.of(
                                AnalyticsConsoleRole.ADMIN,
                                AnalyticsConsoleRole.DESIGNER,
                                AnalyticsConsoleRole.VIEWER)
                        : EnumSet.copyOf(configured.getRoles()),
                configured.getAuthorityProvider(),
                configured.getAuthorityReference());
    }

    @Override
    public AnalyticsConsoleSubject resolve(HttpServletRequest request) {
        return subject;
    }
}
