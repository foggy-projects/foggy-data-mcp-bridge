package com.foggyframework.dataset.db.model.engine.compose.capability;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CapabilityPolicy tests — default empty, explicit allow, method/scope checking.
 */
class CapabilityPolicyTest {

    @Test
    void defaultEmptyDeniesAll() {
        CapabilityPolicy p = CapabilityPolicy.empty();
        assertFalse(p.isFunctionAllowed("anything"));
        assertFalse(p.isObjectAllowed("anything"));
        assertFalse(p.isMethodAllowed("obj", "method"));
        assertFalse(p.isScopeAllowed("read"));
    }

    @Test
    void explicitFunctionAllow() {
        CapabilityPolicy p = new CapabilityPolicy(
                Set.of("fn1", "fn2"), Map.of(), Set.of());
        assertTrue(p.isFunctionAllowed("fn1"));
        assertTrue(p.isFunctionAllowed("fn2"));
        assertFalse(p.isFunctionAllowed("fn3"));
    }

    @Test
    void objectAndMethodAllow() {
        CapabilityPolicy p = new CapabilityPolicy(
                Set.of(),
                Map.of("svc", Set.of("get", "set")),
                Set.of());
        assertTrue(p.isObjectAllowed("svc"));
        assertTrue(p.isMethodAllowed("svc", "get"));
        assertTrue(p.isMethodAllowed("svc", "set"));
        assertFalse(p.isMethodAllowed("svc", "delete"));
        assertFalse(p.isObjectAllowed("other"));
    }

    @Test
    void scopeAllow() {
        CapabilityPolicy p = new CapabilityPolicy(
                Set.of(), Map.of(), Set.of("read", "write"));
        assertTrue(p.isScopeAllowed("read"));
        assertTrue(p.isScopeAllowed("write"));
        assertFalse(p.isScopeAllowed("admin"));
    }
}
