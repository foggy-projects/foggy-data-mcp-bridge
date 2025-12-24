package com.foggyframework.dataset.client.test;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.client.annotates.EnableDatasetClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFoggyFramework(bundleName = "foggy-dataset-client-test")
@EnableDatasetClient
public class DatasetClientTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatasetClientTestApplication.class, args);
    }
}
