package com.foggyframework.core;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@ServletComponentScan(basePackages = "com.foggysource")
//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
//        DataSourceTransactionManagerAutoConfiguration.class,
//        HibernateJpaAutoConfiguration.class,
//
//        SecurityAutoConfiguration.class,
//        SecurityFilterAutoConfiguration.class,
//
//        TransactionAutoConfiguration.class})
//@ImportResource(locations = {"classpath*:/foggy/spring/*.xml"})
@SpringBootApplication()
public class FoggyFrameworkApplication {

    public static void main(String[] args) {
//        BeanPostProcessorChecker c;
        SpringApplication.run(FoggyFrameworkApplication.class, args);
    }



}
