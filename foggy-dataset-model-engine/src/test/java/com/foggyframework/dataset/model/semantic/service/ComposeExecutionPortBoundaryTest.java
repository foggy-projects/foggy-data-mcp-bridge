package com.foggyframework.dataset.model.semantic.service;

import com.foggyframework.dataset.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeExecutionPortBoundaryTest {

    @Test
    void nativeComposeDependsOnTheStableExecutionPort() throws Exception {
        Field field = NativeComposeQueryService.class
                .getDeclaredField("composeExecutionPort");

        assertEquals(ComposeExecutionPort.class, field.getType());
        assertTrue(ComposeExecutionPort.class
                .isAssignableFrom(DefaultComposeExecutionPort.class));
        assertEquals(1, ComposeExecutionPort.class.getDeclaredMethods().length);
    }

    @Test
    void defaultAdapterAllowsAHostProvidedPortToReplaceIt() {
        Method provider = Arrays.stream(DbModelAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("composeExecutionPort"))
                .findFirst()
                .orElseThrow();

        ConditionalOnMissingBean condition =
                provider.getAnnotation(ConditionalOnMissingBean.class);
        assertTrue(condition != null);
    }
}
