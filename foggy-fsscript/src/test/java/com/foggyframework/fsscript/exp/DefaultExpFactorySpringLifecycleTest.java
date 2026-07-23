package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultExpFactorySpringLifecycleTest {

    private static final String USER_FUNCTION_NAME = "LIFECYCLE_MARKER";

    private DefaultExpFactory previousDefault;
    private CountingDefaultExpFactory sharedFactory;
    private CountingFunTable sharedFunTable;

    @BeforeEach
    void installIsolatedSharedRuntime() {
        previousDefault = DefaultExpFactory.DEFAULT;
        sharedFunTable = new CountingFunTable();
        sharedFactory = new CountingDefaultExpFactory();
        sharedFactory.setFunctionSet(sharedFunTable);
        sharedFunTable.append(new MarkerFunDef());
        DefaultExpFactory.DEFAULT = sharedFactory;
    }

    @AfterEach
    void restoreSharedRuntime() {
        DefaultExpFactory.DEFAULT = previousDefault;
    }

    @Test
    void closingOneContextDoesNotDestroyRuntimeSharedWithOtherAndFutureContexts() {
        AnnotationConfigApplicationContext contextA = openContext();
        AnnotationConfigApplicationContext contextB = openContext();
        AnnotationConfigApplicationContext contextC = null;
        try {
            assertSharedRuntime(contextA);
            assertSharedRuntime(contextB);

            contextA.close();

            assertTrue(contextB.isActive());
            assertSharedRuntime(contextB);
            assertEquals("marker", evaluate(sharedFactory, USER_FUNCTION_NAME + "()"));
            assertEquals(1, sharedFactory.destroyCalls);
            assertEquals(0, sharedFactory.clearCalls);
            assertEquals(1, sharedFunTable.destroyCalls);
            assertEquals(0, sharedFunTable.clearCalls);

            contextC = openContext();
            assertSharedRuntime(contextC);
        } finally {
            if (contextC != null) {
                contextC.close();
            }
            contextB.close();
            if (contextA.isActive()) {
                contextA.close();
            }
        }
    }

    @Test
    void independentFactoryAndFunTableStillClearOnDestroy() throws Exception {
        CountingFunTable independentFactoryTable = new CountingFunTable();
        DefaultExpFactory independentFactory = new DefaultExpFactory();
        independentFactory.setFunctionSet(independentFactoryTable);
        assertNotNull(independentFactoryTable.getFun("IIF"));

        independentFactory.destroy();

        assertEquals(1, independentFactoryTable.clearCalls);
        assertNull(independentFactoryTable.getFun("IIF"));

        CountingFunTable independentTable = new CountingFunTable();
        assertNotNull(independentTable.getFun("IIF"));

        independentTable.destroy();

        assertEquals(1, independentTable.destroyCalls);
        assertEquals(1, independentTable.clearCalls);
        assertNull(independentTable.getFun("IIF"));
    }

    private AnnotationConfigApplicationContext openContext() {
        return new AnnotationConfigApplicationContext(SharedRuntimeConfiguration.class);
    }

    private void assertSharedRuntime(AnnotationConfigApplicationContext context) {
        DefaultExpFactory factory = context.getBean("fsscriptExpFactory", DefaultExpFactory.class);
        FunTable funTable = context.getBean("fsscriptFunTable", FunTable.class);
        assertSame(sharedFactory, factory);
        assertSame(sharedFunTable, funTable);
        assertEquals("yes", evaluate(factory, "IIF(true, 'yes', 'no')"));
        assertEquals("marker", evaluate(factory, USER_FUNCTION_NAME + "()"));
    }

    private Object evaluate(DefaultExpFactory factory, String script) {
        Exp exp = new ExpParser(factory).compileEl(script);
        return exp.evalResult(DefaultExpEvaluator.newInstance());
    }

    @Configuration(proxyBeanMethods = false)
    static class SharedRuntimeConfiguration {

        @Bean
        DefaultExpFactory fsscriptExpFactory() {
            return DefaultExpFactory.DEFAULT;
        }

        @Bean
        FunTable fsscriptFunTable(DefaultExpFactory fsscriptExpFactory) {
            return (FunTable) fsscriptExpFactory.getFunctionSet();
        }
    }

    static class CountingDefaultExpFactory extends DefaultExpFactory {
        int clearCalls;
        int destroyCalls;

        @Override
        public void clear() {
            clearCalls++;
            super.clear();
        }

        @Override
        public void destroy() throws Exception {
            destroyCalls++;
            super.destroy();
        }
    }

    static class CountingFunTable extends FunTable {
        int clearCalls;
        int destroyCalls;

        @Override
        public void clear() {
            clearCalls++;
            super.clear();
        }

        @Override
        public void destroy() throws Exception {
            destroyCalls++;
            super.destroy();
        }
    }

    static class MarkerFunDef implements FunDef {

        @Override
        public Object execute(ExpEvaluator ee, Exp[] args) {
            return "marker";
        }

        @Override
        public String getName() {
            return USER_FUNCTION_NAME;
        }
    }
}
