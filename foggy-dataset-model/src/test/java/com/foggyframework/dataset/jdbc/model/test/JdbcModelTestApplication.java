package com.foggyframework.dataset.jdbc.model.test;


import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootApplication()
//@ActiveProfiles({"sqlite"})
@EnableFoggyFramework(bundleName = "foggy-framework-dataset-jdbc-model-test")
public class JdbcModelTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(JdbcModelTestApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(name = "mongoTemplate")
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) throws Exception {

        return new MongoTemplate(mongoDatabaseFactory);
    }

    /**
     * MCP 审计日志使用的 MongoTemplate
     * 对应 TM 模型中的 import '@mcpMongoTemplate'
     */
    @Bean
    @ConditionalOnMissingBean(name = "mcpMongoTemplate")
    public MongoTemplate mcpMongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) throws Exception {
        return new MongoTemplate(mongoDatabaseFactory);
    }
}
