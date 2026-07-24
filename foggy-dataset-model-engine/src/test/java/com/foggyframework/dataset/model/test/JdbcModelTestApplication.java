package com.foggyframework.dataset.model.test;


import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@SpringBootApplication()
//@ActiveProfiles({"sqlite"})
@EnableFoggyFramework(bundleName = "foggy-framework-dataset-jdbc-model-test")
public class JdbcModelTestApplication {

    @Bean
    NamedDataSourceResolver testNamedDataSourceResolver(DataSource dataSource) {
        return new NamedDataSourceResolver() {
            @Override
            public DataSource resolve(String name) {
                return "odoo".equals(name) ? dataSource : null;
            }

            @Override
            public boolean isConfigured(String name) {
                return "odoo".equals(name);
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(JdbcModelTestApplication.class, args);
    }

}
