package com.foggyframework.dataviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.plugins.LargeResultTruncationStep;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataviewer.service.SqliteQueryContextStore;
import com.foggyframework.dataviewer.service.MongoQueryContextStore;
import com.foggyframework.dataviewer.service.SavedQueryService;
import com.foggyframework.dataviewer.service.listpreset.FileSystemListPresetStore;
import com.foggyframework.dataviewer.service.listpreset.ListPresetStore;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.config.DatasetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataViewerAutoConfigurationContextTest {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataViewerAutoConfiguration.class));

    @Test
    void defaultSqliteBackendStartsWithoutMongoClasses() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.mongodb"))
                .withPropertyValues("foggy.data-viewer.cache.sqlite-path=" + tempDir.resolve("viewer.sqlite"))
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .withBean(DatasetProperties.class, DatasetProperties::new)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheService.class);
                    assertThat(context).hasSingleBean(SqliteQueryContextStore.class);
                    assertThat(context.getBean(ListPresetStore.class)).isInstanceOf(FileSystemListPresetStore.class);
                    assertThat(context).doesNotHaveBean("savedQueryService");
                });
    }

    @Test
    void defaultSqliteBackendDoesNotSelectMongoStoreWhenMongoIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class,
                        MongoDataAutoConfiguration.class, DataViewerAutoConfiguration.class))
                .withPropertyValues("foggy.data-viewer.cache.sqlite-path=" + tempDir.resolve("default-viewer.sqlite"))
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .withBean(DatasetProperties.class, DatasetProperties::new)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SqliteQueryContextStore.class);
                    assertThat(context).doesNotHaveBean(MongoQueryContextStore.class);
                    assertThat(context.getBean(ListPresetStore.class)).isInstanceOf(FileSystemListPresetStore.class);
                });
    }

    @Test
    void explicitMongoBackendRetainsSavedQueries() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class,
                        MongoDataAutoConfiguration.class, DataViewerAutoConfiguration.class))
                .withPropertyValues("foggy.data-viewer.cache.storage=mongo")
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .withBean(DatasetProperties.class, DatasetProperties::new)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MongoQueryContextStore.class);
                    assertThat(context).hasSingleBean(SavedQueryService.class);
                });
    }

    @Test
    void disabledConfigurationDoesNotPartiallyAssemble() {
        contextRunner
                .withPropertyValues("foggy.data-viewer.enabled=false")
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .run(this::assertViewerBeansAreAbsent);
    }

    @Test
    void missingQueryFacadeDoesNotPartiallyAssemble() {
        contextRunner
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .run(this::assertViewerBeansAreAbsent);
    }

    @Test
    void memoryModeAssemblesViewerWithoutMongo() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataViewerMemoryAutoConfiguration.class))
                .withPropertyValues("foggy.data-viewer.enabled=true", "foggy.data-viewer.cache.store=memory")
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .withBean(DatasetProperties.class, DatasetProperties::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheService.class);
                    assertThat(context).hasSingleBean(ViewerApiController.class);
                    assertThat(context).hasSingleBean(ViewerPageController.class);
                });
    }

    private void assertViewerBeansAreAbsent(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(QueryCacheService.class);
        assertThat(context).doesNotHaveBean(QueryScopeConstraintService.class);
        assertThat(context).doesNotHaveBean(LargeResultTruncationStep.class);
    }
}
