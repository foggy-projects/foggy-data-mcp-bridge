package com.foggyframework.analytics.console.security;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException;
import com.foggyframework.analytics.runtime.foggy.FoggyComposeAuthorityRequest;
import com.foggyframework.analytics.runtime.foggy.FoggyComposeCallerResolver;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;

import java.util.Objects;

/** Local-only Compose caller bridge; managed deployments provide a product resolver. */
public final class StaticDevFoggyComposeCallerResolver
        implements FoggyComposeCallerResolver {

    private final QueryAuthorityBinding expectedBinding;
    private final String subjectRef;
    private final java.util.List<String> roles;

    public StaticDevFoggyComposeCallerResolver(
            AnalyticsConsoleProperties properties) {
        AnalyticsConsoleProperties.DevSubject subject = Objects.requireNonNull(
                properties, "properties").getDevSubject();
        this.expectedBinding = new QueryAuthorityBinding(
                subject.getAuthorityProvider(), subject.getAuthorityReference());
        this.subjectRef = subject.getSubjectRef();
        this.roles = subject.getRoles().stream().map(Enum::name).toList();
    }

    @Override
    public ComposeCaller resolve(FoggyComposeAuthorityRequest request) {
        Objects.requireNonNull(request, "request");
        if (!expectedBinding.equals(request.binding())) {
            throw new FoggyAnalyticsAdapterException(
                    FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH,
                    "Analytics authority binding does not match the local development subject");
        }
        return new ComposeCaller(
                subjectRef,
                null,
                roles,
                null,
                null,
                null);
    }
}
