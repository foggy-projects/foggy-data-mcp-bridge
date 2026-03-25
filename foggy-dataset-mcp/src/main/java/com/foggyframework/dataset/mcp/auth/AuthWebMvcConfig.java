package com.foggyframework.dataset.mcp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for authentication interceptor.
 *
 * <p>Registers the interceptor when AuthProperties indicates auth is enabled.
 * The interceptor itself checks if auth is enabled before validating tokens.
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AuthProperties authProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only register interceptor when auth is enabled (token is non-blank)
        if (!authProperties.isEnabled()) {
            return;
        }
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