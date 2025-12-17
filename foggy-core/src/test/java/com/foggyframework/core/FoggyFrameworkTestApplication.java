package com.foggyframework.core;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;

//@ServletComponentScan(basePackages = "com.foggysource")
@SpringBootApplication()

//@ImportResource(locations = {"classpath*:/foggy/spring/*.xml"})
public class FoggyFrameworkTestApplication {

    public static void main(String[] args) {
//        BeanPostProcessorChecker c;
        SpringApplication.run(FoggyFrameworkTestApplication.class, args);
    }


}
