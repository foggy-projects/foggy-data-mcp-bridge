package com.foggyframework.dataset.db.model.mongo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.db.model.impl.mongo.TmMongoModelLoaderImpl;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        after = DbModelAutoConfiguration.class,
        afterName = {
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
                "com.foggyframework.dataset.mongo.DataSetMongoAutoConfiguration"
        })
@ConditionalOnClass(name = {
        "com.mongodb.client.MongoClient",
        "org.springframework.data.mongodb.MongoDatabaseFactory",
        "org.springframework.data.mongodb.core.MongoTemplate"
})
@ConditionalOnProperty(prefix = "foggy.dataset.mongo", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(
        value = {SystemBundlesContext.class, FileFsscriptLoader.class, DataSource.class},
        type = {
                "com.mongodb.client.MongoClient",
                "org.springframework.data.mongodb.MongoDatabaseFactory",
                "org.springframework.data.mongodb.core.MongoTemplate"
        })
public class MongoModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TmMongoModelLoaderImpl.class)
    public TmMongoModelLoaderImpl tmMongoModelLoader(
            SystemBundlesContext systemBundlesContext,
            FileFsscriptLoader fileFsscriptLoader) {
        return new TmMongoModelLoaderImpl(systemBundlesContext, fileFsscriptLoader);
    }
}
