package com.foggyframework.dataset.db.model.engine.compose.capability;

/**
 * Thread-local holder for {@link CapabilityRegistry} and {@link CapabilityPolicy}
 * during expression compilation.
 *
 * <p>Mirrors the existing {@code ComposeRuntimeHolder} pattern. Set before
 * expression compilation; cleared after. This allows {@code SqlExpFactory}
 * to resolve registered {@code sql_scalar} functions without polluting
 * the global {@code AllowedFunctions} static whitelist.</p>
 *
 * @since 8.4.0
 */
public final class CapabilityExpContext {

    private CapabilityExpContext() { /* utility */ }

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    /**
     * Set the capability context for the current thread.
     *
     * @return a token to be passed to {@link #clear(Token)} in a finally block
     */
    public static Token set(CapabilityRegistry registry, CapabilityPolicy policy, String dialect) {
        Context prev = HOLDER.get();
        HOLDER.set(new Context(registry, policy, dialect));
        return new Token(prev);
    }

    /**
     * Clear the capability context, restoring the previous state.
     */
    public static void clear(Token token) {
        if (token != null && token.previous != null) {
            HOLDER.set(token.previous);
        } else {
            HOLDER.remove();
        }
    }

    /**
     * Get the current capability registry, or null if none set.
     */
    public static CapabilityRegistry getRegistry() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.registry;
    }

    /**
     * Get the current capability policy, or null if none set.
     */
    public static CapabilityPolicy getPolicy() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.policy;
    }

    /**
     * Get the current SQL dialect, or null if none set.
     */
    public static String getDialect() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.dialect;
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private static final class Context {
        final CapabilityRegistry registry;
        final CapabilityPolicy policy;
        final String dialect;

        Context(CapabilityRegistry registry, CapabilityPolicy policy, String dialect) {
            this.registry = registry;
            this.policy = policy;
            this.dialect = dialect;
        }
    }

    public static final class Token {
        final Context previous;

        Token(Context previous) {
            this.previous = previous;
        }
    }
}
