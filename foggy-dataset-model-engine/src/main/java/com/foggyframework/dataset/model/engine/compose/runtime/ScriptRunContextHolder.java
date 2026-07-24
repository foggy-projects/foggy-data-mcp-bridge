package com.foggyframework.dataset.model.engine.compose.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local holder for the ambient {@link ScriptRunContext}.
 *
 * <p>Uses a {@code ThreadLocal<Deque>} stack to support nested
 * {@code runScript} calls, exactly like {@link ComposeRuntimeHolder}.</p>
 *
 * <p><b>Contract:</b> Every {@link #set(ScriptRunContext)} must be
 * balanced by a {@link #pop(Token)} in a {@code try/finally} block.</p>
 *
 * <p>Mirrors Python {@code _script_run_context: ContextVar}.</p>
 *
 * @since 8.5.0
 */
public final class ScriptRunContextHolder {

    private ScriptRunContextHolder() { /* utility */ }

    private static final ThreadLocal<Deque<ScriptRunContext>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Push a context onto the thread-local stack.
     *
     * @return an opaque {@link Token} for the matching {@link #pop(Token)} call
     */
    public static Token set(ScriptRunContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        Deque<ScriptRunContext> deque = STACK.get();
        int sizeBefore = deque.size();
        deque.push(ctx);
        return new Token(sizeBefore);
    }

    /**
     * Pop contexts down to the level recorded by {@code token}.
     */
    public static void pop(Token token) {
        Deque<ScriptRunContext> deque = STACK.get();
        int target = token.sizeBefore;
        while (deque.size() > target) {
            deque.pop();
        }
    }

    /**
     * Peek at the current context without popping.
     *
     * @return the most-recently-pushed context, or {@code null} if outside any run
     */
    public static ScriptRunContext current() {
        Deque<ScriptRunContext> deque = STACK.get();
        return deque.isEmpty() ? null : deque.peek();
    }

    /** Test-only — drain the entire stack. */
    public static void clearForTesting() {
        STACK.get().clear();
    }

    public static final class Token {
        final int sizeBefore;
        Token(int sizeBefore) { this.sizeBefore = sizeBefore; }
    }
}
