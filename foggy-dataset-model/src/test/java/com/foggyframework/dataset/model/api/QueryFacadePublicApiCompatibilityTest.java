package com.foggyframework.dataset.model.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadePublicApiCompatibilityTest {

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

    @Test
    void legacyFacadeRetainsNineDeprecatedCompatibilityMethods() {
        Class<com.foggyframework.dataset.db.model.service.QueryFacade> legacyFacade =
                com.foggyframework.dataset.db.model.service.QueryFacade.class;

        assertTrue(QueryFacade.class.isAssignableFrom(legacyFacade));
        Method[] legacyMethods = legacyFacade.getDeclaredMethods();
        assertEquals(9, legacyMethods.length);
        assertTrue(Arrays.stream(legacyMethods)
                .allMatch(method -> method.isAnnotationPresent(Deprecated.class)));
    }

    private void assertJdkOrPublicDto(Class<?> type, Set<Class<?>> allowedProjectTypes) {
        assertTrue(
                type.isPrimitive()
                        || type.getName().startsWith("java.")
                        || allowedProjectTypes.contains(type),
                () -> "public DTO leaks non-JDK type: " + type.getName()
        );
    }
}
