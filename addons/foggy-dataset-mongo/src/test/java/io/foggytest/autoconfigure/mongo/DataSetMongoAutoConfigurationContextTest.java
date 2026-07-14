package io.foggytest.autoconfigure.mongo;

import com.foggyframework.dataset.mongo.DataSetMongoAutoConfiguration;
import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataSetMongoAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSetMongoAutoConfiguration.class))
            .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
            .withBean(FsscriptFileChangeHandler.class, () -> mock(FsscriptFileChangeHandler.class));

    @Test
    void disabledConfigurationCreatesNoLoader() {
        contextRunner
                .withPropertyValues("foggy.dataset.mongo.enabled=false")
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MongoFileFsscriptLoader.class);
                });
    }

    @Test
    void missingMongoConnectionBacksOffWithoutCreatingLoader() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MongoFileFsscriptLoader.class);
        });
    }

    @Test
    void missingMongoClassesSafelySkipAutoConfiguration() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        "org.springframework.data.mongodb", "com.mongodb"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MongoFileFsscriptLoader.class);
                });
    }

    @Test
    void completeConditionsCreateExactlyOneLoaderWithoutConnectingToMongo() {
        contextRunner
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MongoFileFsscriptLoader.class);
                });
    }

    @Test
    void userLoaderBacksOffDefaultLoader() {
        MongoFileFsscriptLoader customLoader = mock(MongoFileFsscriptLoader.class);

        contextRunner
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .withBean(MongoFileFsscriptLoader.class, () -> customLoader)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MongoFileFsscriptLoader.class);
                    assertThat(context.getBean(MongoFileFsscriptLoader.class)).isSameAs(customLoader);
                });
    }

    @Test
    void bootThreeImportsRegistersMongoAutoConfiguration() throws IOException {
        assertThat(autoConfigurationImports())
                .contains(DataSetMongoAutoConfiguration.class.getName());
    }

    private static String autoConfigurationImports() throws IOException {
        var resources = DataSetMongoAutoConfiguration.class.getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        StringBuilder content = new StringBuilder();
        for (URL resource : Collections.list(resources)) {
            content.append(new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8));
            content.append('\n');
        }
        return content.toString();
    }
}
