package com.foggyframework.dataset.db.model.engine.compose.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local holder for the ambient {@link ComposeRuntimeBundle}.
 *
 * <p>Uses a {@code ThreadLocal<Deque>} stack to support nested
 * {@code ScriptRuntime.runScript} calls (parent bundle is preserved
 * during the inner call and restored on pop).</p>
 *
 * <p><b>Contract:</b> Every {@link #setBundle(ComposeRuntimeBundle)}
 * <em>must</em> be balanced by a {@link #popBundle(Token)} in a
 * {@code try/finally} block.</p>
 *
 * <p>Cross-repo invariant: mirrors Python {@code _compose_runtime: ContextVar}
 * from {@code foggy.dataset_model.engine.compose.runtime}. Java uses
 * ThreadLocal+Deque instead of ContextVar because Java async tasks do
 * not automatically inherit context vars.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeRuntimeHolder {

    private ComposeRuntimeHolder() { /* utility */ }

    private static final ThreadLocal<Deque<ComposeRuntimeBundle>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Push a bundle onto the thread-local stack and return an opaque
     * {@link Token} for the matching {@link #popBundle(Token)} call.
     */
    public static Token setBundle(ComposeRuntimeBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle must not be null");
        }
        Deque<ComposeRuntimeBundle> deque = STACK.get();
        int sizeBefore = deque.size();
        deque.push(bundle);
        return new Token(sizeBefore);
    }

    /**
     * Pop bundles down to the level recorded by {@code token}.
     *
     * @throws IllegalStateException if the deque is already smaller
     *         than the token's recorded size (over-pop)
     */
    public static void popBundle(Token token) {
        Deque<ComposeRuntimeBundle> deque = STACK.get();
        int target = token.sizeBefore;
        if (deque.size() <= target) {
            throw new IllegalStateException(
                    "ComposeRuntimeHolder.popBundle: stack already at or below "
                            + "token level " + target + " (current size " + deque.size()
                            + "); likely double-pop or missing setBundle");
        }
        while (deque.size() > target) {
            deque.pop();
        }
    }

    /**
     * Peek at the current bundle without popping.
     *
     * @return the most-recently-pushed bundle, or {@code null} if the
     *         stack is empty (i.e. we are outside any {@code runScript} call)
     */
    public static ComposeRuntimeBundle currentBundle() {
        Deque<ComposeRuntimeBundle> deque = STACK.get();
        return deque.isEmpty() ? null : deque.peek();
    }

    /**
     * Drain the entire stack for this thread. <b>Test-only</b> — never
     * call from production code. Provided to simplify {@code @AfterEach}
     * cleanup in unit tests.
     */
    public static void clearForTesting() {
        STACK.get().clear();
    }

    /**
     * Opaque token returned by {@link #setBundle} and consumed by
     * {@link #popBundle}. Holds the stack size before the push so that
     * pop restores to exactly that level.
     */
    public static final class Token {
        final int sizeBefore;

        Token(int sizeBefore) {
            this.sizeBefore = sizeBefore;
        }
    }
}
