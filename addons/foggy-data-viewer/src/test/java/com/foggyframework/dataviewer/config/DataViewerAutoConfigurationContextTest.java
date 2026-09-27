package com.foggyframework.dataviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.plugins.LargeResultTruncationStep;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.controller.ViewerPageController;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.config.DatasetProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataViewerAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataViewerAutoConfiguration.class));

    @Test
    void missingMongoDependencySkipsSafely() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.mongodb"))
                .withBean(QueryFacade.class, () -> mock(QueryFacade.class))
                .run(this::assertViewerBeansAreAbsent);
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
