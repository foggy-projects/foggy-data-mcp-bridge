package com.foggyframework.dataset.model.api.compatibility;

import com.foggyframework.dataset.model.api.QueryFacade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyQueryFacadeCompatibilityTest {

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
}
