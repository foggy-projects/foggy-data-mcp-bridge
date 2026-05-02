package com.foggyframework.dataset.db.model.engine.compose.capability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CapabilityRegistry tests — default empty, registration, duplicate rejection,
 * lookup, and missing renderer/handler validation.
 */
class CapabilityRegistryTest {

    private FunctionDescriptor sqlScalar(String name) {
        return new FunctionDescriptor(
                name, "sql_scalar",
                List.of(Map.of("name", "x", "type", "string", "required", true)),
                "string", true, "none",
                List.of("formula"), "test." + name,
                List.of("mysql"));
    }

    private FunctionDescriptor pureRuntime(String name) {
        return new FunctionDescriptor(
                name, "pure_runtime",
                List.of(), "int", true, "none",
                List.of("compose_runtime"), "test." + name,
                null);
    }

    // ---------------------------------------------------------------
    // Default empty
    // ---------------------------------------------------------------

    @Test
    void defaultEmpty() {
        CapabilityRegistry reg = new CapabilityRegistry();
        assertTrue(reg.isEmpty());
        assertFalse(reg.hasFunction("anything"));
        assertFalse(reg.hasObject("anything"));
    }

    // ---------------------------------------------------------------
    // sql_scalar registration
    // ---------------------------------------------------------------

    @Test
    void registerSqlScalarSuccess() {
        CapabilityRegistry reg = new CapabilityRegistry();
        reg.registerFunction(sqlScalar("fiscal_month"),
                (args, dialect) -> new CapabilitySqlFragment("1", List.of()));
        assertTrue(reg.hasFunction("fiscal_month"));
        assertFalse(reg.isEmpty());
    }

    @Test
    void registerSqlScalarMissingRenderer() {
        CapabilityRegistry reg = new CapabilityRegistry();
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerFunction(sqlScalar("fn"), (CapabilityFunctionRenderer) null));
    }

    @Test
    void registerSqlScalarWithHandlerOverloadRejectsWrongKind() {
        CapabilityRegistry reg = new CapabilityRegistry();
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerFunction(sqlScalar("fn"), (args) -> "x"));
    }

    // ---------------------------------------------------------------
    // pure_runtime registration
    // ---------------------------------------------------------------

    @Test
    void registerPureRuntimeSuccess() {
        CapabilityRegistry reg = new CapabilityRegistry();
        reg.registerFunction(pureRuntime("my_calc"), (args) -> 42);
        assertTrue(reg.hasFunction("my_calc"));
    }

    @Test
    void registerPureRuntimeMissingHandler() {
        CapabilityRegistry reg = new CapabilityRegistry();
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerFunction(pureRuntime("fn"), (CapabilityFunctionHandler) null));
    }

    // ---------------------------------------------------------------
    // Duplicate name rejection
    // ---------------------------------------------------------------

    @Test
    void duplicateFunctionName() {
        CapabilityRegistry reg = new CapabilityRegistry();
        reg.registerFunction(sqlScalar("fn"),
                (args, dialect) -> new CapabilitySqlFragment("1", List.of()));
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerFunction(sqlScalar("fn"),
                        (args, dialect) -> new CapabilitySqlFragment("2", List.of())));
    }

    @Test
    void functionNameConflictsWithObject() {
        CapabilityRegistry reg = new CapabilityRegistry();
        MethodDescriptor m = new MethodDescriptor(
                "get_data", List.of(), "string", "none", "read", 5000, "t.g");
        ObjectFacadeDescriptor obj = new ObjectFacadeDescriptor("my_fn", List.of(m));

        // Create a target with the method
        reg.registerObjectFacade(obj, new Object() {
            public String get_data() { return "x"; }
        });

        // Now try to register a function with the same name
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerFunction(sqlScalar("my_fn"),
                        (args, dialect) -> new CapabilitySqlFragment("1", List.of())));
    }

    // ---------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------

    @Test
    void lookupUnregisteredFunctionThrows() {
        CapabilityRegistry reg = new CapabilityRegistry();
        assertThrows(CapabilityException.NotRegistered.class, () ->
                reg.getFunction("nonexistent"));
    }

    @Test
    void lookupRegisteredFunction() {
        CapabilityRegistry reg = new CapabilityRegistry();
        CapabilityFunctionRenderer renderer =
                (args, dialect) -> new CapabilitySqlFragment("RESULT", List.of());
        reg.registerFunction(sqlScalar("fn"), renderer);
        CapabilityRegistry.FunctionEntry entry = reg.getFunction("fn");
        assertNotNull(entry);
        assertEquals("fn", entry.getDescriptor().getName());
        assertSame(renderer, entry.getRenderer());
    }

    // ---------------------------------------------------------------
    // Object facade registration
    // ---------------------------------------------------------------

    @Test
    void registerObjectFacadeSuccess() {
        CapabilityRegistry reg = new CapabilityRegistry();
        MethodDescriptor m = new MethodDescriptor(
                "getValue", List.of(), "string", "none", "read", 5000, "t.g");
        ObjectFacadeDescriptor desc = new ObjectFacadeDescriptor("my_svc", List.of(m));
        reg.registerObjectFacade(desc, new Object() {
            public String getValue() { return "hello"; }
        });
        assertTrue(reg.hasObject("my_svc"));
    }

    @Test
    void registerObjectFacadeMethodNotOnTarget() {
        CapabilityRegistry reg = new CapabilityRegistry();
        MethodDescriptor m = new MethodDescriptor(
                "nonExistentMethod", List.of(), "string", "none", "read", 5000, "t.g");
        ObjectFacadeDescriptor desc = new ObjectFacadeDescriptor("my_svc", List.of(m));
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerObjectFacade(desc, new Object()));
    }

    @Test
    void duplicateObjectName() {
        CapabilityRegistry reg = new CapabilityRegistry();
        MethodDescriptor m = new MethodDescriptor(
                "getValue", List.of(), "string", "none", "read", 5000, "t.g");
        ObjectFacadeDescriptor desc = new ObjectFacadeDescriptor("svc", List.of(m));
        Object target = new Object() {
            public String getValue() { return "x"; }
        };
        reg.registerObjectFacade(desc, target);
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                reg.registerObjectFacade(desc, target));
    }
}
