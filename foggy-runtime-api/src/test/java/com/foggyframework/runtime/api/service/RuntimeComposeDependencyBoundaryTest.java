package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.runtime.api.controller.RuntimeComposeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeComposeDependencyBoundaryTest {

    private static final String ENGINE_COMPOSE_PACKAGE =
            "com.foggyframework.dataset.db.model.engine.compose";

    @Test
    void runtimeComposeUsesTheModelSidePortWithoutEngineComposeTypes() throws Exception {
        Field port = RuntimeComposeRunner.class.getDeclaredField("composeExecutionPort");
        assertEquals(ComposeExecutionPort.class, port.getType());

        for (Class<?> type : List.of(
                RuntimeComposeController.class,
                RuntimeComposeContextFactory.class,
                RuntimeComposeRunner.class,
                RuntimeFsscriptCteBridge.class)) {
            for (Field field : type.getDeclaredFields()) {
                assertNotEngineComposeType(type, field.getType());
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    assertNotEngineComposeType(type, parameterType);
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                assertNotEngineComposeType(type, method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNotEngineComposeType(type, parameterType);
                }
            }
        }
    }

    private static void assertNotEngineComposeType(Class<?> owner, Class<?> dependency) {
        assertFalse(dependency.getName().startsWith(ENGINE_COMPOSE_PACKAGE),
                () -> owner.getName() + " exposes engine Compose type " + dependency.getName());
    }
}
