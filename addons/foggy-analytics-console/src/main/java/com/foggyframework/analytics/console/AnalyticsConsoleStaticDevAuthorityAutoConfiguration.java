package com.foggyframework.analytics.console;

import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.StaticDevFoggySemanticRequestContextResolver;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiAutoConfiguration;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticRequestContextResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Installs the explicit local-only authority bridge before Runtime lane selection. */
@AutoConfiguration(before = AnalyticsRuntimeApiAutoConfiguration.class)
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
}
