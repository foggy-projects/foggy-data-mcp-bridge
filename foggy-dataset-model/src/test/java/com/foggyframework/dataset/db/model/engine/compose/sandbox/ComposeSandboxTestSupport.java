package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sandbox test support.
 */
public class ComposeSandboxTestSupport {

    protected static void assertSandboxViolation(
            Runnable action, String expectedCode, String expectedLayer, String expectedKind) {
        ComposeSandboxViolationException ex = assertThrows(ComposeSandboxViolationException.class, action::run);
        assertEquals(expectedCode, ex.code());
        assertEquals(expectedLayer, ex.layer());
        assertEquals(expectedKind, ex.kind());
        assertTrue(
            Set.of("script-parse", "script-eval", "plan-build", "schema-derive",
                   "authority-resolve", "compile", "execute").contains(ex.phase()),
            "phase must be in enum: " + ex.phase());
    }
}
