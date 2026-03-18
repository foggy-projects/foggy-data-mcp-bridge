package com.foggyframework.dataset.mcp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for authentication interceptor.
 *
 * <p>Only registers the interceptor when foggy.auth.token is configured.
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "foggy.auth.token")
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/actuator/health",
                        "/actuator/info",
                        "/healthz",
                        "/readyz",
                        "/api/v1/health"
                );
    }
}