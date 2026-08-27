package com.foggyframework.analytics.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.api.AnalyticsConsoleController;
import com.foggyframework.analytics.console.api.AnalyticsConsoleAgentController;
import com.foggyframework.analytics.console.api.AnalyticsConsoleExceptionHandler;
import com.foggyframework.analytics.console.api.AnalyticsConsoleFapCallbackController;
import com.foggyframework.analytics.console.api.AnalyticsConsoleFapCallbackExceptionHandler;
import com.foggyframework.analytics.console.api.AnalyticsConsoleFapPublicationController;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentGateway;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAgentService;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleAskRecoveryRepository;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFunctionTraceRepository;
import com.foggyframework.analytics.console.agent.FileAnalyticsConsoleFunctionTraceRepository;
import com.foggyframework.analytics.console.agent.FileAnalyticsConsoleAskRecoveryRepository;
import com.foggyframework.analytics.console.agent.HttpAnalyticsConsoleAgentGateway;
import com.foggyframework.analytics.console.agent.StaticDevAnalyticsConsoleFapBindingResolver;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.catalog.FileAnalyticsConsoleCatalogRepository;
import com.foggyframework.analytics.console.config.AnalyticsConsoleProperties;
import com.foggyframework.analytics.console.security.AnalyticsConsoleSubjectResolver;
import com.foggyframework.analytics.console.security.StaticDevAnalyticsConsoleSubjectResolver;
import com.foggyframework.analytics.console.service.AnalyticsConsoleService;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClients;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionAdapter;
import com.foggyframework.analytics.runtime.api.AnalyticsRuntimeEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;

@AutoConfiguration(afterName =
        "com.foggyframework.analytics.runtime.api.AnalyticsRuntimeApiAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "foggy.analytics-console", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AnalyticsConsoleProperties.class)
@Import({
        AnalyticsConsoleExceptionHandler.class,
        AnalyticsConsoleFapCallbackExceptionHandler.class
})
public class AnalyticsConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleSubjectResolver.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "security-mode",
            havingValue = "static-dev-only")
    AnalyticsConsoleSubjectResolver staticDevAnalyticsConsoleSubjectResolver(
            AnalyticsConsoleProperties properties) {
        return new StaticDevAnalyticsConsoleSubjectResolver(properties);
    }

    @Bean
    AnalyticsConsoleActivationGuard analyticsConsoleActivationGuard(
            AnalyticsConsoleProperties properties,
            AnalyticsConsoleSubjectResolver subjectResolver,
            Environment environment) {
        return new AnalyticsConsoleActivationGuard(properties, subjectResolver, environment);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleFapBindingResolver.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "security-mode",
            havingValue = "static-dev-only")
    AnalyticsConsoleFapBindingResolver staticDevAnalyticsConsoleFapBindingResolver(
            AnalyticsConsoleProperties properties) {
        return new StaticDevAnalyticsConsoleFapBindingResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsFunctionClient.class)
    AnalyticsFunctionClient analyticsConsoleFunctionClient(
            @AnalyticsRuntimeEndpoint AnalyticsFunctionEndpoint endpoint) {
        return AnalyticsFunctionClients.embedded(endpoint);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleCatalogRepository.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "storage-mode",
            havingValue = "file-single-process",
            matchIfMissing = true)
    AnalyticsConsoleCatalogRepository analyticsConsoleCatalogRepository(
            AnalyticsConsoleProperties properties,
            ObjectMapper objectMapper) {
        return new FileAnalyticsConsoleCatalogRepository(
                Path.of(properties.getCatalogPath()), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleFunctionTraceRepository.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "storage-mode",
            havingValue = "file-single-process",
            matchIfMissing = true)
    AnalyticsConsoleFunctionTraceRepository analyticsConsoleFunctionTraceRepository(
            AnalyticsConsoleProperties properties,
            ObjectMapper objectMapper) {
        return new FileAnalyticsConsoleFunctionTraceRepository(
                Path.of(properties.getFunctionTracePath()), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleAskRecoveryRepository.class)
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console",
            name = "storage-mode",
            havingValue = "file-single-process",
            matchIfMissing = true)
    AnalyticsConsoleAskRecoveryRepository analyticsConsoleAskRecoveryRepository(
            AnalyticsConsoleProperties properties,
            ObjectMapper objectMapper) {
        return new FileAnalyticsConsoleAskRecoveryRepository(
                Path.of(properties.getAskRecoveryPath()), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsConsoleService.class)
    AnalyticsConsoleService analyticsConsoleService(
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsBundleStore bundleStore,
            AnalyticsFunctionClient functions,
            AnalyticsConsoleProperties properties) {
        return new AnalyticsConsoleService(
                catalog, bundleStore, functions, properties.getMaxDefinitionBytes());
    }

    @Bean
    @ConditionalOnBean(AnalyticsConsoleSubjectResolver.class)
    AnalyticsConsoleController analyticsConsoleController(
            AnalyticsConsoleService service,
            AnalyticsConsoleSubjectResolver subjects) {
        return new AnalyticsConsoleController(service, subjects);
    }

    @Bean
    @ConditionalOnBean(AnalyticsConsoleSubjectResolver.class)
    AnalyticsConsoleFapPublicationController analyticsConsoleFapPublicationController(
            AnalyticsConsoleSubjectResolver subjects,
            ObjectMapper objectMapper) {
        return new AnalyticsConsoleFapPublicationController(subjects, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console.fap",
            name = "enabled",
            havingValue = "true")
    AnalyticsConsoleAgentGateway analyticsConsoleAgentGateway(
            AnalyticsConsoleProperties properties,
            ObjectMapper objectMapper) {
        AnalyticsConsoleProperties.Fap fap = properties.getFap();
        return new HttpAnalyticsConsoleAgentGateway(
                URI.create(fap.getBaseUrl()),
                Duration.ofSeconds(fap.getTimeoutSeconds()),
                objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console.fap",
            name = "enabled",
            havingValue = "true")
    AnalyticsConsoleAgentService analyticsConsoleAgentService(
            AnalyticsConsoleService console,
            AnalyticsConsoleCatalogRepository catalog,
            AnalyticsConsoleAgentGateway gateway,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleProperties properties,
            AnalyticsFunctionClient functions,
            AnalyticsConsoleFunctionTraceRepository functionTraces,
            AnalyticsConsoleAskRecoveryRepository askRecovery) {
        return new AnalyticsConsoleAgentService(
                console, catalog, gateway, bindings, properties, functions, functionTraces,
                askRecovery);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console.fap",
            name = "enabled",
            havingValue = "true")
    FapAnalyticsFunctionAdapter analyticsConsoleFapFunctionAdapter(
            AnalyticsFunctionClient functions,
            AnalyticsConsoleFapBindingResolver bindings) {
        return new FapAnalyticsFunctionAdapter(
                functions,
                (caller, operation) -> {
                    var subject = bindings.resolveCaller(caller);
                    return new com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority(
                            subject.authorityProvider(), subject.authorityReference());
                });
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console.fap",
            name = "enabled",
            havingValue = "true")
    AnalyticsConsoleAgentController analyticsConsoleAgentController(
            AnalyticsConsoleAgentService agents,
            AnalyticsConsoleSubjectResolver subjects) {
        return new AnalyticsConsoleAgentController(agents, subjects);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "foggy.analytics-console.fap",
            name = "enabled",
            havingValue = "true")
    AnalyticsConsoleFapCallbackController analyticsConsoleFapCallbackController(
            AnalyticsConsoleProperties properties,
            AnalyticsConsoleFapBindingResolver bindings,
            AnalyticsConsoleAgentService agents,
            AnalyticsConsoleService console,
            FapAnalyticsFunctionAdapter adapter,
            AnalyticsConsoleFunctionTraceRepository functionTraces,
            ObjectMapper objectMapper) {
        return new AnalyticsConsoleFapCallbackController(
                properties, bindings, agents, console, adapter, functionTraces, objectMapper);
    }

    @Bean
    AnalyticsConsoleWebMvcConfigurer analyticsConsoleWebMvcConfigurer() {
        return new AnalyticsConsoleWebMvcConfigurer();
    }

    @Bean
    FilterRegistrationBean<AnalyticsConsoleSecurityHeadersFilter>
            analyticsConsoleSecurityHeadersFilter() {
        FilterRegistrationBean<AnalyticsConsoleSecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new AnalyticsConsoleSecurityHeadersFilter());
        registration.setName("analyticsConsoleSecurityHeadersFilter");
        registration.addUrlPatterns("/analytics-console", "/analytics-console/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AnalyticsConsoleRequestGuardFilter>
            analyticsConsoleRequestGuardFilter() {
        FilterRegistrationBean<AnalyticsConsoleRequestGuardFilter> registration =
                new FilterRegistrationBean<>(new AnalyticsConsoleRequestGuardFilter());
        registration.setName("analyticsConsoleRequestGuardFilter");
        registration.addUrlPatterns("/analytics-console/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
