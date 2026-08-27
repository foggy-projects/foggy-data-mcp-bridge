package com.foggyframework.analytics.console.agent;

import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.AnalyticsConsoleRole;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionInvocation;

import java.util.LinkedHashSet;
import java.util.Objects;

/** Explicit local-only FAP binding; production hosts must provide their own resolver. */
public final class StaticDevAnalyticsConsoleFapBindingResolver
        implements AnalyticsConsoleFapBindingResolver {

    private final AnalyticsConsoleProperties properties;

    public StaticDevAnalyticsConsoleFapBindingResolver(
            AnalyticsConsoleProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public OutboundBinding resolve(AnalyticsConsoleSubject subject) {
        AnalyticsConsoleSubject expected = devSubject();
        if (!expected.subjectRef().equals(subject.subjectRef())) {
            throw forbidden();
        }
        AnalyticsConsoleProperties.Fap fap = properties.getFap();
        return new OutboundBinding(
                required(fap.getDevAuthorization()),
                required(fap.getDevWorkspaceRef()),
                required(fap.getDevModelConfigRef()),
                required(fap.getDevModelVariantId()),
                fap.isWorkspaceFilesEnabled());
    }

    @Override
    public AnalyticsConsoleSubject resolveCaller(
            FapAnalyticsFunctionInvocation.Caller caller) {
        AnalyticsConsoleProperties.Fap fap = properties.getFap();
        AnalyticsConsoleSubject subject = devSubject();
        if (!fap.getProviderRef().equals(caller.providerRef())
                || !required(fap.getDevTenantRef()).equals(caller.tenantRef())
                || !required(fap.getDevProviderSubjectRef()).equals(
                        caller.providerSubjectRef())
                || !subject.subjectRef().equals(caller.externalSubjectRef())) {
            throw forbidden();
        }
        return subject;
    }

    private AnalyticsConsoleSubject devSubject() {
        AnalyticsConsoleProperties.DevSubject dev = properties.getDevSubject();
        LinkedHashSet<AnalyticsConsoleRole> roles = new LinkedHashSet<>(dev.getRoles());
        if (roles.isEmpty()) {
            roles.add(AnalyticsConsoleRole.ADMIN);
        }
        return new AnalyticsConsoleSubject(
                dev.getSubjectRef(),
                dev.getDisplayName(),
                roles,
                dev.getAuthorityProvider(),
                dev.getAuthorityReference());
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw forbidden();
        }
        return value;
    }

    private static AnalyticsConsoleCatalogException forbidden() {
        return new AnalyticsConsoleCatalogException(
                "ANALYTICS_CONSOLE_FAP_CONTEXT_FORBIDDEN",
                "Static development FAP binding was rejected");
    }
}
