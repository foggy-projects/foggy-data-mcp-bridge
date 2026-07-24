package com.foggyframework.dataset.model.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelWebDependencyBoundaryTest {

    @Test
    void webModuleDoesNotPullJdbcOrLegacyAggregateImplementations() {
        ClassLoader classLoader = ModelWebDependencyBoundaryTest.class.getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass(
                "com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider"));
        assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass(
                "com.foggyframework.dataset.model.config.DbModelAutoConfiguration"));
    }
}
