package com.foggyframework.dataset.model.engine.compose.capability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FunctionDescriptor validation tests — mirrors Python test_capability_registry.py
 * descriptor validation section.
 */
class FunctionDescriptorTest {

    // ---------------------------------------------------------------
    // Valid construction
    // ---------------------------------------------------------------

    @Test
    void validSqlScalar() {
        FunctionDescriptor d = new FunctionDescriptor(
                "fiscal_month", "sql_scalar",
                List.of(Map.of("name", "date", "type", "date", "required", true)),
                "string", true, "none",
                List.of("formula", "compose_column"),
                "test.fiscal_month",
                List.of("mysql"));
        assertEquals("fiscal_month", d.getName());
        assertEquals("sql_scalar", d.getKind());
        assertEquals(1, d.getArgsSchema().size());
        assertEquals("mysql", d.getDialects().get(0));
    }

    @Test
    void validPureRuntime() {
        FunctionDescriptor d = new FunctionDescriptor(
                "my_calc", "pure_runtime",
                List.of(), "int", true, "none",
                List.of("compose_runtime"),
                "test.calc",
                null);
        assertEquals("pure_runtime", d.getKind());
        assertTrue(d.getDialects().isEmpty());
    }

    // ---------------------------------------------------------------
    // Name validation
    // ---------------------------------------------------------------

    @Test
    void emptyName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    @Test
    void nullName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor(null, "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    @Test
    void unsafeName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("bad-name", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    @Test
    void reservedName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("eval", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    @Test
    void doubleUnderscoreName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("__hidden", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // Kind validation
    // ---------------------------------------------------------------

    @Test
    void invalidKind() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "unknown_kind", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // Side effect validation
    // ---------------------------------------------------------------

    @Test
    void invalidSideEffect() {
        assertThrows(CapabilityException.SideEffectDenied.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "write", List.of("formula"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // Return type validation
    // ---------------------------------------------------------------

    @Test
    void invalidReturnType() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "complex_object",
                        true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // allowed_in validation
    // ---------------------------------------------------------------

    @Test
    void emptyAllowedIn() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "none", List.of(), "t.t", List.of("mysql")));
    }

    @Test
    void invalidAllowedInSurface() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "none", List.of("invalid_surface"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // sql_scalar must have dialects
    // ---------------------------------------------------------------

    @Test
    void sqlScalarMissingDialects() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", null));
    }

    @Test
    void sqlScalarEmptyDialects() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "t.t", List.of()));
    }

    // ---------------------------------------------------------------
    // audit_tag validation
    // ---------------------------------------------------------------

    @Test
    void emptyAuditTag() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar", List.of(), "string",
                        true, "none", List.of("formula"), "", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // args_schema validation
    // ---------------------------------------------------------------

    @Test
    void argSchemaMissingName() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar",
                        List.of(Map.of("type", "string")),
                        "string", true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    @Test
    void argSchemaMissingType() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new FunctionDescriptor("fn", "sql_scalar",
                        List.of(Map.of("name", "x")),
                        "string", true, "none", List.of("formula"), "t.t", List.of("mysql")));
    }

    // ---------------------------------------------------------------
    // MethodDescriptor validation
    // ---------------------------------------------------------------

    @Test
    void validMethodDescriptor() {
        MethodDescriptor m = new MethodDescriptor(
                "get_value", List.of(), "string", "none", "read", 5000, "test.get");
        assertEquals("get_value", m.getName());
        assertEquals("read", m.getAuthScope());
        assertEquals(5000, m.getTimeoutMs());
    }

    @Test
    void methodEmptyAuthScope() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new MethodDescriptor("m", List.of(), "string", "none", "", 1000, "t.t"));
    }

    @Test
    void methodNegativeTimeout() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new MethodDescriptor("m", List.of(), "string", "none", "read", -1, "t.t"));
    }

    @Test
    void methodZeroTimeout() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new MethodDescriptor("m", List.of(), "string", "none", "read", 0, "t.t"));
    }

    // ---------------------------------------------------------------
    // ObjectFacadeDescriptor validation
    // ---------------------------------------------------------------

    @Test
    void validObjectFacade() {
        MethodDescriptor m = new MethodDescriptor(
                "get_data", List.of(), "string", "none", "read", 5000, "t.g");
        ObjectFacadeDescriptor d = new ObjectFacadeDescriptor("my_service", List.of(m));
        assertEquals("my_service", d.getObjectName());
        assertEquals(1, d.getMethods().size());
    }

    @Test
    void objectFacadeNoMethods() {
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new ObjectFacadeDescriptor("svc", List.of()));
    }

    @Test
    void objectFacadeDuplicateMethod() {
        MethodDescriptor m1 = new MethodDescriptor(
                "get_data", List.of(), "string", "none", "read", 5000, "t.g1");
        MethodDescriptor m2 = new MethodDescriptor(
                "get_data", List.of(), "int", "none", "read", 5000, "t.g2");
        assertThrows(CapabilityException.InvalidDescriptor.class, () ->
                new ObjectFacadeDescriptor("svc", List.of(m1, m2)));
    }
}
