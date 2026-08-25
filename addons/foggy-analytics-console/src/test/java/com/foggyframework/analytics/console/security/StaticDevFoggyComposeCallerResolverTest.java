package com.foggyframework.analytics.console.security;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException;
import com.foggyframework.analytics.runtime.foggy.FoggyComposeAuthorityRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticDevFoggyComposeCallerResolverTest {

    @Test
    void acceptsOnlyTheConfiguredOpaqueBindingAndProjectsTheLocalSubject() {
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        properties.getDevSubject().setSubjectRef("local-analyst");
        properties.getDevSubject().setRoles(List.of(
                AnalyticsConsoleRole.ADMIN,
                AnalyticsConsoleRole.VIEWER));
        properties.getDevSubject().setAuthorityProvider("console");
        properties.getDevSubject().setAuthorityReference("local-dev-only");
        StaticDevFoggyComposeCallerResolver resolver =
                new StaticDevFoggyComposeCallerResolver(properties);

        ComposeCaller caller = resolver.resolve(request(
                "console", "local-dev-only"));

        assertThat(caller.userId()).isEqualTo("local-analyst");
        assertThat(caller.roles()).containsExactly("ADMIN", "VIEWER");
        assertThatThrownBy(() -> resolver.resolve(request(
                "console", "forged-subject")))
                .isInstanceOfSatisfying(
                        FoggyAnalyticsAdapterException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH));
    }

    private static FoggyComposeAuthorityRequest request(
            String provider,
            String reference) {
        return new FoggyComposeAuthorityRequest(
                "default",
                new QueryAuthorityBinding(provider, reference),
                "request-1",
                "trace-1");
    }
}
