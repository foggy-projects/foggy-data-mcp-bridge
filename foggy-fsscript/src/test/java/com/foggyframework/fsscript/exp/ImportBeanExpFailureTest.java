package com.foggyframework.fsscript.exp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportBeanExpFailureTest {

    @Test
    void beanMethodInvocationShouldNotWrapSeriousErrors() {
        AssertionError seriousError = new AssertionError("serious bean failure");
        ThrowingBean bean = new ThrowingBean(seriousError);

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> ImportBeanExp.apply(ThrowingBean.class, bean, "throwError", new Object[0]));

        assertSame(seriousError, thrown);
    }

    public static class ThrowingBean {
        private final AssertionError seriousError;

        ThrowingBean(AssertionError seriousError) {
            this.seriousError = seriousError;
        }

        public void throwError() {
            throw seriousError;
        }
    }
}
