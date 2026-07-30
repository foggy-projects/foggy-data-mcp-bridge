package com.foggyframework.runtime.console;

import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

final class RuntimeConsoleWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/console", "/console/");
        registry.addViewController("/console/")
                .setViewName("forward:/console/index.html");
    }
}
