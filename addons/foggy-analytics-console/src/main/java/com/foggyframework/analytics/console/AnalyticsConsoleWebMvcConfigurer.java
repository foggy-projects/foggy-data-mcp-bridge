package com.foggyframework.analytics.console;

import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

final class AnalyticsConsoleWebMvcConfigurer implements WebMvcConfigurer {

    private static final String CONSOLE_RESOURCE_LOCATION =
            "classpath:/META-INF/foggy-analytics-console/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/analytics-console/**")
                .addResourceLocations(CONSOLE_RESOURCE_LOCATION);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/analytics-console", "/analytics-console/");
        registry.addViewController("/analytics-console/")
                .setViewName("forward:/analytics-console/index.html");
    }
}
