package com.foggyframework.analytics.console;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.StaticDevFoggySemanticRequestContextResolver;
import com.foggyframework.analytics.console.security.StaticDevFoggyComposeCallerResolver;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiAutoConfiguration;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticRequestContextResolver;
import com.foggyframework.analytics.runtime.foggy.FoggyComposeCallerResolver;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Installs the explicit local-only authority bridge before Runtime lane selection. */
@AutoConfiguration(
        before = AnalyticsRuntimeApiAutoConfiguration.class,
        beforeName = "com.foggyframework.dataset.model.DbModelAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "foggy.analytics-console",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(AnalyticsConsoleProperties.class)
public class AnalyticsConsoleStaticDevAuthorityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FoggySemanticRequestContextResolver.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "security-mode",
            havingValue = "static-dev-only")
    FoggySemanticRequestContextResolver staticDevFoggySemanticRequestContextResolver(
            AnalyticsConsoleProperties properties) {
        return new StaticDevFoggySemanticRequestContextResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FoggyComposeCallerResolver.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "security-mode",
            havingValue = "static-dev-only")
    FoggyComposeCallerResolver staticDevFoggyComposeCallerResolver(
            AnalyticsConsoleProperties properties) {
        return new StaticDevFoggyComposeCallerResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorityResolver.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "security-mode",
            havingValue = "static-dev-only")
    AuthorityResolver staticDevAnalyticsComposeAuthorityResolver() {
        return request -> {
            java.util.Map<String, ModelBinding> bindings = new java.util.LinkedHashMap<>();
            request.modelNames().forEach(modelName -> bindings.put(
                    modelName,
                    ModelBinding.builder().build()));
            return AuthorityResolution.builder().bindings(bindings).build();
        };
    }
}
