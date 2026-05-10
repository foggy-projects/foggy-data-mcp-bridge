package com.foggyframework.dataset.db.model.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Dataset request namespace resolver")
class DatasetRequestNamespaceResolverTest {

    @Test
    @DisplayName("显式 namespace 优先于 request.defaultNamespace")
    void explicitNamespaceWins() {
        DatasetProperties properties = new DatasetProperties();
        properties.getRequest().setDefaultNamespace("tms-ai");

        assertEquals("tms-biz", DatasetRequestNamespaceResolver.resolve(properties, " tms-biz "));
    }

    @Test
    @DisplayName("空 namespace 使用 request.defaultNamespace")
    void blankNamespaceUsesRequestDefault() {
        DatasetProperties properties = new DatasetProperties();
        properties.getRequest().setDefaultNamespace("tms-ai");

        assertEquals("tms-ai", DatasetRequestNamespaceResolver.resolve(properties, null));
        assertEquals("tms-ai", DatasetRequestNamespaceResolver.resolve(properties, " "));
    }

    @Test
    @DisplayName("未配置 request.defaultNamespace 时保持底层默认 namespace 语义")
    void emptyRequestDefaultKeepsStorageDefault() {
        DatasetProperties properties = new DatasetProperties();

        assertNull(DatasetRequestNamespaceResolver.resolve(properties, null));
        assertEquals("", DatasetRequestNamespaceResolver.resolve(properties, ""));
    }
}
