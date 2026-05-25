package com.foggyframework.dataviewer.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataViewerAutoConfigurationTest {

    @Test
    void shouldUseFileStoreWhenAutoModeHasNoMongoUri() {
        assertFalse(DataViewerAutoConfiguration.shouldUseMongoListPresetStore(
                DataViewerProperties.ListPresetProperties.Storage.AUTO,
                new MockEnvironment()));
    }

    @Test
    void shouldUseMongoStoreWhenAutoModeHasMongoUri() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.mongodb.uri", "mongodb://localhost:17017/foggy_test");

        assertTrue(DataViewerAutoConfiguration.shouldUseMongoListPresetStore(
                DataViewerProperties.ListPresetProperties.Storage.AUTO,
                environment));
    }

    @Test
    void shouldRespectExplicitStorageMode() {
        MockEnvironment environment = new MockEnvironment();

        assertTrue(DataViewerAutoConfiguration.shouldUseMongoListPresetStore(
                DataViewerProperties.ListPresetProperties.Storage.MONGO,
                environment));
        assertFalse(DataViewerAutoConfiguration.shouldUseMongoListPresetStore(
                DataViewerProperties.ListPresetProperties.Storage.FILE,
                environment));
    }
}
