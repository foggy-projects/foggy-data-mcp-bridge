package com.foggyframework.analytics.console.security;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaticDevFoggySemanticRequestContextResolverTest {

    @Test
    void acceptsOnlyTheConfiguredOpaqueBindingAndUsesTheResolvedNamespace() {
        AnalyticsConsoleProperties properties = new AnalyticsConsoleProperties();
        properties.getDevSubject().setAuthorityProvider("console");
        properties.getDevSubject().setAuthorityReference("local-dev-only");
        StaticDevFoggySemanticRequestContextResolver resolver =
                new StaticDevFoggySemanticRequestContextResolver(properties);
        CatalogResolution<QueryModel> resolution = resolution("tenant-a");

        SemanticRequestContext context = resolver.resolve(
                request("console", "local-dev-only"),
                resolution);

        assertThat(context.getNamespace()).isEqualTo("tenant-a");
        assertThatThrownBy(() -> resolver.resolve(
                request("console", "forged-subject"),
                resolution))
                .isInstanceOfSatisfying(
                        FoggyAnalyticsAdapterException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH));
    }

    private static QueryAuthorityRequest request(String provider, String reference) {
        return new QueryAuthorityRequest(
                new AnalyticsModelDependency(
                        new AnalyticsNamespaceRef("tenant-a"),
                        "qm",
                        "Orders",
                        AnalyticsModelRevision.fromSha256Hex("a".repeat(64))),
                new QueryAuthorityBinding(provider, reference),
                "request-1",
                "trace-1");
    }

    @SuppressWarnings("unchecked")
    private static CatalogResolution<QueryModel> resolution(String namespace) {
        CatalogResolution<QueryModel> resolution = mock(CatalogResolution.class);
        CatalogIdentity identity = mock(CatalogIdentity.class);
        when(identity.namespace()).thenReturn(namespace);
        when(resolution.catalogIdentity()).thenReturn(identity);
        return resolution;
    }
}
