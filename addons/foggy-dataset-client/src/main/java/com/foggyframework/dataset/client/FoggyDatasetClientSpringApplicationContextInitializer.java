package com.foggyframework.dataset.client;

import jakarta.annotation.Priority;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Priority(100)
public class FoggyDatasetClientSpringApplicationContextInitializer implements ApplicationContextInitializer {

    FoggyDatasetClientLoader foggyDatasetClientLoader = new FoggyDatasetClientLoader();

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        configurableApplicationContext.getBeanFactory().registerSingleton("foggyDatasetClientLoader", foggyDatasetClientLoader);
    }
}
