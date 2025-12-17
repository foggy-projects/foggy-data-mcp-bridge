package com.foggyframework.fsscript.client;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.annotation.Priority;

@Priority(100)
public class FsscriptClientApplicationContextInitializer implements ApplicationContextInitializer {

    FsscriptClientLoader fsscriptClientLoader = new FsscriptClientLoader();

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        configurableApplicationContext.getBeanFactory().registerSingleton("fsscriptClientLoader", fsscriptClientLoader);
    }

}
