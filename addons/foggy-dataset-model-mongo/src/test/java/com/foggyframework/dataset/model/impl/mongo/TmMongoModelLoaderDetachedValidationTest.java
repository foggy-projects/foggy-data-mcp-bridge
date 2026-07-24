package com.foggyframework.dataset.model.impl.mongo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TmMongoModelLoaderDetachedValidationTest {

    @Test
    void detachedBuilderUsesRequestLocalContextAndKeepsMongoDependencies() {
        SystemBundlesContext liveContext = mock(SystemBundlesContext.class);
        FileFsscriptLoader liveFileLoader = mock(FileFsscriptLoader.class);
        TmMongoModelLoaderImpl live = new TmMongoModelLoaderImpl(
                liveContext,
                liveFileLoader
        );
        DataSource dataSource = mock(DataSource.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoClient mongoClient = mock(MongoClient.class);
        live.defaultDataSource = dataSource;
        live.defaultMongoTemplate = mongoTemplate;
        live.defaultMongoClient = mongoClient;

        SystemBundlesContext detachedContext = mock(SystemBundlesContext.class);
        FileFsscriptLoader detachedFileLoader = mock(FileFsscriptLoader.class);
        QueryModelBuilder detached = live.createDetachedQueryModelBuilder(
                mock(TableModelLoaderManager.class),
                detachedContext,
                detachedFileLoader
        );

        assertThat(detached).isInstanceOf(TmMongoModelLoaderImpl.class)
                .isNotSameAs(live);
        assertThat(ReflectionTestUtils.getField(
                detached, "systemBundlesContext")).isSameAs(detachedContext);
        assertThat(ReflectionTestUtils.getField(
                detached, "fileFsscriptLoader")).isSameAs(detachedFileLoader);
        assertThat(ReflectionTestUtils.getField(
                detached, "defaultDataSource")).isSameAs(dataSource);
        assertThat(ReflectionTestUtils.getField(
                detached, "defaultMongoTemplate")).isSameAs(mongoTemplate);
        assertThat(ReflectionTestUtils.getField(
                detached, "defaultMongoClient")).isSameAs(mongoClient);
    }
}
