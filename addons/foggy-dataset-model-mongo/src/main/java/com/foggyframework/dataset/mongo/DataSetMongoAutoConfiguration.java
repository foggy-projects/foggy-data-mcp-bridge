package com.foggyframework.dataset.mongo;

import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB Dataset 自动配置
 * 仅当 MongoTemplate 存在时才启用
 */
@Configuration
@ConditionalOnClass(MongoTemplate.class)
public class DataSetMongoAutoConfiguration {

    @Bean
    public MongoFileFsscriptLoader mongoFileFsscriptLoader(ApplicationContext appCtx, FileFsscriptLoader parent) {
        MongoFileFsscriptLoader loader = new MongoFileFsscriptLoader(appCtx, parent, null);
        MongoFileFsscriptLoader.setInstance(loader);
        return loader;
    }
}
