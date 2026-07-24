package com.foggyframework.dataset.model.engine.compose.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObjectFacadeProxy tests — declared method success, undeclared deny,
 * private/reflection deny, timeout, return type validation, error sanitization.
 */
class ObjectFacadeProxyTest {

    // A simple target with declared and undeclared methods
    static class TestService {
        public String getValue() { return "hello"; }
        public int compute(int a, int b) { return a + b; }
        public Object unsafeReturn() { return new Thread(); } // not a safe type
        public String slowMethod() {
            try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "done";
        }
        public String failingMethod() { throw new RuntimeException("internal error details"); }
        private String privateMethod() { return "secret"; }
    }

    private ObjectFacadeDescriptor descriptor;
    private CapabilityPolicy fullPolicy;
    private TestService target;

    @BeforeEach
    void setUp() {
        target = new TestService();
        descriptor = new ObjectFacadeDescriptor("test_svc", List.of(
                new MethodDescriptor("getValue", List.of(), "string", "none", "read", 5000, "t.get"),
                new MethodDescriptor("compute", List.of(
                        Map.of("name", "a", "type", "int"),
                        Map.of("name", "b", "type", "int")
                ), "int", "none", "read", 5000, "t.compute"),
                new MethodDescriptor("unsafeReturn", List.of(), "string", "none", "read", 5000, "t.unsafe"),
                new MethodDescriptor("slowMethod", List.of(), "string", "none", "read", 100, "t.slow"),
                new MethodDescriptor("failingMethod", List.of(), "string", "none", "read", 5000, "t.fail")
        ));

        fullPolicy = new CapabilityPolicy(
                Set.of(),
                Map.of("test_svc", Set.of("getValue", "compute", "unsafeReturn", "slowMethod", "failingMethod")),
                Set.of("read")
        );
    }

    // ---------------------------------------------------------------
    // Success path
    // ---------------------------------------------------------------

    @Test
    void declaredMethodSuccess() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        Object result = proxy.invoke("getValue");
        assertEquals("hello", result);
    }

    @Test
    void declaredMethodWithArgs() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        Object result = proxy.invoke("compute", 3, 7);
        assertEquals(10, result);
    }

    // ---------------------------------------------------------------
    // Denial paths
    // ---------------------------------------------------------------

    @Test
    void undeclaredMethodDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.MethodNotDeclared.class, () ->
                proxy.invoke("undeclaredMethod"));
    }

    @Test
    void privateAccessDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.MethodNotDeclared.class, () ->
                proxy.invoke("_privateField"));
    }

    @Test
    void getClassDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.MethodNotDeclared.class, () ->
                proxy.invoke("getClass"));
    }

    @Test
    void reflectionDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.MethodNotDeclared.class, () ->
                proxy.invoke("hashCode"));
    }

    // ---------------------------------------------------------------
    // Policy denial
    // ---------------------------------------------------------------

    @Test
    void policyMethodDeny() {
        // Policy does not include "getValue"
        CapabilityPolicy restrictive = new CapabilityPolicy(
                Set.of(),
                Map.of("test_svc", Set.of("compute")),
                Set.of("read")
        );
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, restrictive);
        assertThrows(CapabilityException.NotAllowed.class, () ->
                proxy.invoke("getValue"));
    }

    @Test
    void authScopeDeny() {
        // Policy has method but wrong scope
        CapabilityPolicy noScopePolicy = new CapabilityPolicy(
                Set.of(),
                Map.of("test_svc", Set.of("getValue")),
                Set.of()  // no scopes allowed
        );
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, noScopePolicy);
        assertThrows(CapabilityException.NotAllowed.class, () ->
                proxy.invoke("getValue"));
    }

    // ---------------------------------------------------------------
    // Return type validation
    // ---------------------------------------------------------------

    @Test
    void returnTypeDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.ReturnTypeDenied.class, () ->
                proxy.invoke("unsafeReturn"));
    }

    @Test
    void safeReturnTypes() {
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(null));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue("string"));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(42));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(3.14));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(true));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(List.of("a", "b")));
        assertTrue(ObjectFacadeProxy.isSafeReturnValue(Map.of("key", "value")));
        assertFalse(ObjectFacadeProxy.isSafeReturnValue(new Thread()));
        assertFalse(ObjectFacadeProxy.isSafeReturnValue(new Object()));
    }

    // ---------------------------------------------------------------
    // Timeout
    // ---------------------------------------------------------------

    @Test
    void timeoutDeny() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        assertThrows(CapabilityException.Timeout.class, () ->
                proxy.invoke("slowMethod"));
    }

    // ---------------------------------------------------------------
    // Error sanitization
    // ---------------------------------------------------------------

    @Test
    void errorSanitization() {
        ObjectFacadeProxy proxy = new ObjectFacadeProxy(descriptor, target, fullPolicy);
        CapabilityException.MethodNotDeclared ex = assertThrows(
                CapabilityException.MethodNotDeclared.class,
                () -> proxy.invoke("failingMethod"));
        // Must not contain internal error details
        assertFalse(ex.getMessage().contains("internal error details"));
        assertTrue(ex.getMessage().contains("raised an error during execution"));
    }
}
