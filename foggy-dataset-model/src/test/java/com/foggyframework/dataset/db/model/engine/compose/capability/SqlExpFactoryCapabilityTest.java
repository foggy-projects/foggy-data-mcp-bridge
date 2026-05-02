package com.foggyframework.dataset.db.model.engine.compose.capability;

import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlExpFactory + CapabilityExpContext integration tests.
 *
 * <p>Mirrors Python test_formula_compiler_capabilities.py — verifies
 * sql_scalar success lowering, policy deny, surface deny, dialect deny,
 * invalid renderer return, and default surface unchanged.</p>
 *
 * <p>Since SqlColumnRefExp requires a live queryModel to resolve column names,
 * these tests use numeric literals as arguments (e.g. {@code fiscal_month(1)})
 * to avoid null queryModel issues during evaluation.</p>
 */
class SqlExpFactoryCapabilityTest {

    private CapabilityRegistry registry;
    private CapabilityPolicy policy;

    @BeforeEach
    void setUp() {
        registry = new CapabilityRegistry();

        // Register a sql_scalar function with 1 arg
        registry.registerFunction(
                new FunctionDescriptor(
                        "fiscal_month", "sql_scalar",
                        List.of(Map.of("name", "date", "type", "date", "required", true)),
                        "string", true, "none",
                        List.of("formula", "compose_column"),
                        "test.fiscal_month",
                        List.of("mysql", "sqlite")),
                (args, dialect) -> new CapabilitySqlFragment(
                        "DATE_FORMAT(" + args.get("date") + ", '%Y-%m')", List.of()));

        // Register a function NOT allowed in formula (only compose_runtime)
        registry.registerFunction(
                new FunctionDescriptor(
                        "system_call", "sql_scalar",
                        List.of(), "string", false, "none",
                        List.of("compose_runtime"),
                        "test.system",
                        List.of("mysql")),
                (args, dialect) -> new CapabilitySqlFragment("1", List.of()));

        policy = new CapabilityPolicy(
                Set.of("fiscal_month", "system_call"),
                Map.of(), Set.of());
    }

    @AfterEach
    void tearDown() {
        CapabilityExpContext.clear(null);
    }

    /**
     * Compile and evaluate a formula expression.
     * Uses numeric literals/strings as function arguments to avoid
     * the need for a live queryModel.
     */
    private SqlFragment compile(String expression) {
        Exp compiled = CalculatedFieldService.compileExpression(expression);
        ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(null);
        evaluator.setVar(SqlExpContext.CONTEXT_KEY, new SqlExpContext(null, null, null));
        Object result = compiled.evalResult(evaluator);
        if (result instanceof SqlFragment) {
            return (SqlFragment) result;
        }
        throw new RuntimeException("Expression did not return SqlFragment: " + result);
    }

    /**
     * Compile only (parse phase); throws SecurityException before eval
     * for policy/surface/dialect violations.
     */
    private void compileOnly(String expression) {
        CalculatedFieldService.compileExpression(expression);
    }

    // ---------------------------------------------------------------
    // Success path
    // ---------------------------------------------------------------

    @Test
    void sqlScalarSuccessLowering() {
        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, policy, "mysql");
        try {
            // Use numeric literal arg to avoid column resolution NPE
            SqlFragment result = compile("fiscal_month(42)");
            assertNotNull(result);
            assertTrue(result.getSql().contains("DATE_FORMAT"));
            assertTrue(result.getSql().contains("42"));
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    // ---------------------------------------------------------------
    // Denial paths (policy, surface, dialect — caught at parse time)
    // ---------------------------------------------------------------

    @Test
    void unregisteredFunctionDeny() {
        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, policy, "mysql");
        try {
            assertThrows(SecurityException.class, () -> compileOnly("unregistered_fn(1)"));
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    @Test
    void policyDeny() {
        CapabilityPolicy restrictive = new CapabilityPolicy(
                Set.of(), Map.of(), Set.of());
        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, restrictive, "mysql");
        try {
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> compileOnly("fiscal_month(1)"));
            assertTrue(ex.getMessage().contains("not allowed by the current policy"));
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    @Test
    void surfaceDeny() {
        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, policy, "mysql");
        try {
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> compileOnly("system_call()"));
            assertTrue(ex.getMessage().contains("not allowed in formula/compose_column"));
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    @Test
    void unsupportedDialectDeny() {
        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, policy, "postgres");
        try {
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> compileOnly("fiscal_month(1)"));
            assertTrue(ex.getMessage().contains("does not support dialect 'postgres'"));
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    @Test
    void invalidRendererReturn() {
        registry.registerFunction(
                new FunctionDescriptor(
                        "bad_fn", "sql_scalar",
                        List.of(), "string", true, "none",
                        List.of("formula"),
                        "test.bad",
                        List.of("mysql")),
                (args, dialect) -> null);

        CapabilityPolicy withBadFn = new CapabilityPolicy(
                Set.of("bad_fn"), Map.of(), Set.of());

        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, withBadFn, "mysql");
        try {
            // The null return from renderer triggers SecurityException at eval time
            Exception ex = assertThrows(Exception.class,
                    () -> compile("bad_fn()"));
            // The message should indicate the renderer issue
            assertTrue(ex.getMessage().contains("did not return")
                    || ex.getCause() != null && ex.getCause().getMessage().contains("did not return"),
                    "Expected 'did not return' message but got: " + ex.getMessage());
        } finally {
            CapabilityExpContext.clear(token);
        }
    }

    // ---------------------------------------------------------------
    // Default surface unchanged
    // ---------------------------------------------------------------

    @Test
    void defaultSurfaceUnchanged_noRegistry_standardFunction() {
        // Without registry, ABS(1) still works
        SqlFragment result = compile("ABS(1)");
        assertNotNull(result);
        assertTrue(result.getSql().contains("ABS"));
    }

    @Test
    void defaultSurfaceUnchanged_customFunctionDenied() {
        // Without any registry, custom function fails at parse time
        assertThrows(SecurityException.class, () -> compileOnly("fiscal_month(1)"));
    }

    // ---------------------------------------------------------------
    // Thread-local lifecycle
    // ---------------------------------------------------------------

    @Test
    void threadLocalCleanup() {
        assertNull(CapabilityExpContext.getRegistry());
        assertNull(CapabilityExpContext.getPolicy());
        assertNull(CapabilityExpContext.getDialect());

        CapabilityExpContext.Token token = CapabilityExpContext.set(registry, policy, "mysql");
        assertNotNull(CapabilityExpContext.getRegistry());
        assertNotNull(CapabilityExpContext.getPolicy());
        assertEquals("mysql", CapabilityExpContext.getDialect());

        CapabilityExpContext.clear(token);
        assertNull(CapabilityExpContext.getRegistry());
    }
}
