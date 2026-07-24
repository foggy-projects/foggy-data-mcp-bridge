package com.foggyframework.dataset.model.lifecycle.namespace;

import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.NamespaceScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class NamespaceScopeTest {

    private static final long TIMEOUT_SECONDS = 5;

    @AfterEach
    void assertNoNamespaceLeak() {
        assertNull(NamespaceContext.getNamespace(), "each test must leave the owning thread unset");
        NamespaceContext.clear();
    }

    @Test
    void explicitScopesPreserveUnsetDefaultAndNamedStates() {
        assertNull(NamespaceContext.getNamespace());

        try (NamespaceScope outer = NamespaceContext.open("  tenant-a  ")) {
            assertEquals("tenant-a", NamespaceContext.getNamespace());

            try (NamespaceScope explicitDefault = NamespaceContext.open(null)) {
                assertEquals("", NamespaceContext.getNamespace());

                try (NamespaceScope innerNamed = NamespaceContext.open(" tenant-b ")) {
                    assertEquals("tenant-b", NamespaceContext.getNamespace());
                }

                assertEquals("", NamespaceContext.getNamespace());
            }

            assertEquals("tenant-a", NamespaceContext.getNamespace());

            try (NamespaceScope blankDefault = NamespaceContext.open(" \t ")) {
                assertEquals("", NamespaceContext.getNamespace());
            }

            assertEquals("tenant-a", NamespaceContext.getNamespace());
        }

        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    void openInheritedPreservesRootNamedAndDefaultStates() {
        try (NamespaceScope rootInherited = NamespaceContext.openInherited()) {
            assertEquals("", NamespaceContext.getNamespace(),
                    "root inheritance must enter explicit default, not remain unset");
            try (NamespaceScope nestedDefault = NamespaceContext.openInherited()) {
                assertEquals("", NamespaceContext.getNamespace());
            }
            assertEquals("", NamespaceContext.getNamespace());
        }
        assertNull(NamespaceContext.getNamespace());

        try (NamespaceScope named = NamespaceContext.open("tenant-a")) {
            try (NamespaceScope inheritedNamed = NamespaceContext.openInherited()) {
                assertEquals("tenant-a", NamespaceContext.getNamespace());
            }
            assertEquals("tenant-a", NamespaceContext.getNamespace());
        }
        assertNull(NamespaceContext.getNamespace());

        try (NamespaceScope explicitDefault = NamespaceContext.open("")) {
            try (NamespaceScope inheritedDefault = NamespaceContext.openInherited()) {
                assertEquals("", NamespaceContext.getNamespace());
            }
            assertEquals("", NamespaceContext.getNamespace());
        }
        assertNull(NamespaceContext.getNamespace());

        NamespaceContext.setNamespace("  legacy-a  ");
        try (NamespaceScope inheritedLegacy = NamespaceContext.openInherited()) {
            assertEquals("legacy-a", NamespaceContext.getNamespace(),
                    "an inherited scope canonicalizes a legacy named value");
        }
        assertEquals("  legacy-a  ", NamespaceContext.getNamespace(),
                "closing must restore the exact legacy value");
        NamespaceContext.clear();
    }

    @Test
    void exceptionAndEarlyReturnRestorePreviousState() {
        IllegalStateException marker = new IllegalStateException("controlled scope failure");

        try (NamespaceScope outer = NamespaceContext.open("tenant-a")) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> throwFromScope("tenant-b", marker));

            assertSame(marker, thrown);
            assertEquals("tenant-a", NamespaceContext.getNamespace());
            assertEquals("", returnFromScope("  "));
            assertEquals("tenant-a", NamespaceContext.getNamespace(),
                    "early return must close its inner scope before returning to the caller");
        }

        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    void wrongThreadCloseFailsWithoutInvalidatingOwnerScope() throws Exception {
        NamespaceScope scope = NamespaceContext.open("tenant-a");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<IllegalStateException> closeAttempt = executor.submit(
                    () -> assertThrows(IllegalStateException.class, scope::close));

            IllegalStateException failure = closeAttempt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals("NAMESPACE_SCOPE_WRONG_THREAD", failure.getMessage());
            assertEquals("tenant-a", NamespaceContext.getNamespace(),
                    "wrong-thread close must not change the owner's current namespace");

            scope.close();
            assertNull(NamespaceContext.getNamespace(),
                    "the owner must still be able to close after a wrong-thread attempt");

            Future<IllegalStateException> closedScopeAttempt = executor.submit(
                    () -> assertThrows(IllegalStateException.class, scope::close));
            assertEquals("NAMESPACE_SCOPE_WRONG_THREAD",
                    closedScopeAttempt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getMessage(),
                    "even a closed scope remains owner-thread bound");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "wrong-thread executor must terminate within the bounded deadline");
            scope.close();
        }
    }

    @Test
    void outOfOrderCloseFailsWithoutMutatingStackAndOwnerCanRecover() {
        NamespaceScope outer = NamespaceContext.open("tenant-a");
        NamespaceScope inner = NamespaceContext.open("tenant-b");

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, outer::close);
            assertEquals("NAMESPACE_SCOPE_OUT_OF_ORDER", failure.getMessage());
            assertEquals("tenant-b", NamespaceContext.getNamespace(),
                    "out-of-order close must leave the top frame untouched");

            inner.close();
            assertEquals("tenant-a", NamespaceContext.getNamespace());

            outer.close();
            assertNull(NamespaceContext.getNamespace());
        } finally {
            inner.close();
            outer.close();
        }
    }

    @Test
    void successfulOwnerDoubleCloseIsIdempotent() {
        NamespaceScope scope = NamespaceContext.open("tenant-a");

        scope.close();
        scope.close();

        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    void closedTokenCannotPopANewerScope() {
        NamespaceScope closed = NamespaceContext.open("tenant-a");
        closed.close();

        try (NamespaceScope current = NamespaceContext.open("tenant-b")) {
            closed.close();
            assertEquals("tenant-b", NamespaceContext.getNamespace(),
                    "a stale successfully-closed token must be an idempotent no-op");
        }

        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    void singleThreadExecutorDoesNotLeakNamespaceBetweenTasks() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<ScopeObservation> first = executor.submit(() -> {
                String before = NamespaceContext.getNamespace();
                String inside;
                try (NamespaceScope ignored = NamespaceContext.open("tenant-a")) {
                    inside = NamespaceContext.getNamespace();
                }
                return new ScopeObservation(before, inside, NamespaceContext.getNamespace());
            });

            ScopeObservation observation = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Future<String> second = executor.submit(NamespaceContext::getNamespace);

            assertAll(
                    () -> assertNull(observation.before()),
                    () -> assertEquals("tenant-a", observation.inside()),
                    () -> assertNull(observation.after()),
                    () -> assertNull(second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                            "the next task on the same worker must start unset")
            );
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "reuse executor must terminate within the bounded deadline");
        }
    }

    @Test
    void activeScopeRejectsLegacyMutationWithoutChangingCurrentOrPreviousState() {
        NamespaceContext.setNamespace("  legacy-outer  ");
        NamespaceScope scope = NamespaceContext.open("tenant-a");

        try {
            IllegalStateException setFailure = assertThrows(
                    IllegalStateException.class,
                    () -> NamespaceContext.setNamespace("illegal"));
            assertEquals("NAMESPACE_SCOPE_LEGACY_MUTATION_WHILE_ACTIVE", setFailure.getMessage());
            assertEquals("tenant-a", NamespaceContext.getNamespace());

            IllegalStateException clearFailure = assertThrows(
                    IllegalStateException.class,
                    NamespaceContext::clear);
            assertEquals("NAMESPACE_SCOPE_LEGACY_MUTATION_WHILE_ACTIVE", clearFailure.getMessage());
            assertEquals("tenant-a", NamespaceContext.getNamespace());
        } finally {
            scope.close();
        }

        assertEquals("  legacy-outer  ", NamespaceContext.getNamespace(),
                "failed legacy mutations must not replace the saved previous state");
        NamespaceContext.clear();
        assertNull(NamespaceContext.getNamespace());
    }

    private void throwFromScope(String namespace, IllegalStateException marker) {
        try (NamespaceScope ignored = NamespaceContext.open(namespace)) {
            assertEquals(namespace, NamespaceContext.getNamespace());
            throw marker;
        }
    }

    private String returnFromScope(String namespace) {
        try (NamespaceScope ignored = NamespaceContext.open(namespace)) {
            return NamespaceContext.getNamespace();
        }
    }

    private record ScopeObservation(String before, String inside, String after) {
    }
}
