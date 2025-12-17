package com.foggyframework.dataset;


import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableFoggyFramework(bundleName = "foggy-framework-dataset-test")
public class FoggyFrameworkDataSetTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoggyFrameworkDataSetTestApplication.class, args);
    }


}
