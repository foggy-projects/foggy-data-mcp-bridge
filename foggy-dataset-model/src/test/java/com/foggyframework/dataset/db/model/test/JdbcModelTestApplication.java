package com.foggyframework.dataset.db.model.test;


import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootApplication()
//@ActiveProfiles({"sqlite"})
@EnableFoggyFramework(bundleName = "foggy-framework-dataset-jdbc-model-test")
public class JdbcModelTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(JdbcModelTestApplication.class, args);
    }

}
