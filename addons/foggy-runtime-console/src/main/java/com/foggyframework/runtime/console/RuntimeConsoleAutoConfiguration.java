package com.foggyframework.runtime.console;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

@AutoConfiguration(afterName = "com.foggyframework.runtime.api.RuntimeApiAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "foggy.runtime-console", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RuntimeConsoleProperties.class)
public class RuntimeConsoleAutoConfiguration {

    @Bean
    RuntimeConsoleActivationGuard runtimeConsoleActivationGuard(Environment environment) {
        return new RuntimeConsoleActivationGuard(environment);
    }

    @Bean
    RuntimeConsoleWebMvcConfigurer runtimeConsoleWebMvcConfigurer() {
        return new RuntimeConsoleWebMvcConfigurer();
    }

    @Bean
    FilterRegistrationBean<RuntimeConsoleSecurityHeadersFilter> runtimeConsoleSecurityHeadersFilter() {
        FilterRegistrationBean<RuntimeConsoleSecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new RuntimeConsoleSecurityHeadersFilter());
        registration.setName("runtimeConsoleSecurityHeadersFilter");
        registration.addUrlPatterns("/console", "/console/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
