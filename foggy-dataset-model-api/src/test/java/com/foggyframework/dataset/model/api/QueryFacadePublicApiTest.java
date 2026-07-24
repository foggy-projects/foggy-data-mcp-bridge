package com.foggyframework.dataset.model.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadePublicApiTest {

    @Test
    void publicFacadeExposesOnlyStableRequestAndResultDtos() throws Exception {
        Method[] methods = QueryFacade.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        Method query = QueryFacade.class.getMethod("query", QueryFacadeRequest.class);
        assertEquals(QueryFacadeResult.class, query.getReturnType());
        assertTrue(Modifier.isPublic(query.getModifiers()));
    }

    @Test
    void publicDtosDependOnlyOnJdkValueTypes() {
        Set<Class<?>> allowedProjectTypes = Set.of(QueryFacadeRequest.class, QueryFacadeResult.class);
        Arrays.stream(QueryFacadeRequest.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> assertJdkOrPublicDto(field.getType(), allowedProjectTypes));
        Arrays.stream(QueryFacadeResult.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> assertJdkOrPublicDto(field.getType(), allowedProjectTypes));
    }

    private void assertJdkOrPublicDto(Class<?> type, Set<Class<?>> allowedProjectTypes) {
        assertTrue(type.isPrimitive() || type.getName().startsWith("java.")
                        || allowedProjectTypes.contains(type),
                () -> "public DTO leaks non-JDK type: " + type.getName());
    }
}
