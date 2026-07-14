package com.foggyframework.dataset.mongo;

import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * MongoDB Dataset 自动配置
 * 仅当 MongoTemplate 存在时才启用
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "com.foggyframework.fsscript.FoggyFscriptAutoConfiguration"
})
@ConditionalOnClass(name = {
        "com.mongodb.client.MongoClient",
        "org.springframework.data.mongodb.MongoDatabaseFactory",
        "org.springframework.data.mongodb.core.MongoTemplate"
})
@ConditionalOnProperty(prefix = "foggy.dataset.mongo", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(
        value = {FileFsscriptLoader.class, FsscriptFileChangeHandler.class},
        type = {
                "com.mongodb.client.MongoClient",
                "org.springframework.data.mongodb.MongoDatabaseFactory",
                "org.springframework.data.mongodb.core.MongoTemplate"
        })
public class DataSetMongoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MongoFileFsscriptLoader.class)
    public MongoFileFsscriptLoader mongoFileFsscriptLoader(
            ApplicationContext appCtx,
            FileFsscriptLoader parent,
            FsscriptFileChangeHandler changeHandler) {
        return new MongoFileFsscriptLoader(appCtx, parent, changeHandler);
    }
}
