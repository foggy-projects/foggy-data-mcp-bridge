package com.foggyframework.analytics.console;

import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

final class AnalyticsConsoleWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/analytics-console", "/analytics-console/");
        registry.addViewController("/analytics-console/")
                .setViewName("forward:/analytics-console/index.html");
    }
}
