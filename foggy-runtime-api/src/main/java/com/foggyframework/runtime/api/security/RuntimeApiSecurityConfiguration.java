package com.foggyframework.runtime.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeApiSecurityConfiguration implements WebMvcConfigurer {

    private final RuntimeApiAuthInterceptor authInterceptor;

    public RuntimeApiSecurityConfiguration(RuntimeApiAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**", "/api/bundles/**");
    }
}
