package com.foggyframework.fsscript.exp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MethodFinderTest {

    @Test
    void findMethodShouldCheckArgumentsAfterNullArgument() {
        Method method = MethodFinder.findMethod(
                TargetMethods.class,
                "join",
                new Object[]{null, "not-a-number"}
        );

        assertNull(method);
    }

    @Test
    void findMethodShouldAllowCompatibleArgumentsAfterNullArgument() throws Exception {
        Method method = MethodFinder.findMethod(
                TargetMethods.class,
                "join",
                new Object[]{null, 7}
        );

        assertNotNull(method);
        assertEquals("null:7", method.invoke(new TargetMethods(), null, 7));
    }

    @Test
    void autoFixArgsAndFindMethodShouldCheckArgumentsAfterNullArgument() {
        Method method = MethodFinder.autoFixArgsAndFindMethod(
                TargetMethods.class,
                "join",
                new Object[]{null, "not-a-number"}
        );

        assertNull(method);
    }

    @Test
    void findMethodShouldNotMatchNullToPrimitiveParameter() {
        Method method = MethodFinder.findMethod(
                TargetMethods.class,
                "primitiveCount",
                new Object[]{null}
        );

        assertNull(method);
    }

    @Test
    void autoFixArgsAndFindMethodShouldNotMatchNullToPrimitiveParameter() {
        Method method = MethodFinder.autoFixArgsAndFindMethod(
                TargetMethods.class,
                "primitiveCount",
                new Object[]{null}
        );

        assertNull(method);
    }

    static class TargetMethods {
        public String join(String label, Integer count) {
            return label + ":" + count;
        }

        public String primitiveCount(int count) {
            return String.valueOf(count);
        }
    }
}
