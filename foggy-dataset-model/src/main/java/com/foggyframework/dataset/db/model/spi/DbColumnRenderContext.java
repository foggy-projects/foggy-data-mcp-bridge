package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.dialect.FDialect;

public final class DbColumnRenderContext {
    private static final ThreadLocal<FDialect> DIALECT = new ThreadLocal<>();

    private DbColumnRenderContext() {
    }

    public static FDialect getDialect() {
        return DIALECT.get();
    }

    public static Scope useDialect(FDialect dialect) {
        FDialect previous = DIALECT.get();
        if (dialect == null) {
            return new Scope(previous, false);
        }
        DIALECT.set(dialect);
        return new Scope(previous, true);
    }

    public static final class Scope implements AutoCloseable {
        private final FDialect previous;
        private final boolean changed;
        private boolean closed;

        private Scope(FDialect previous, boolean changed) {
            this.previous = previous;
            this.changed = changed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (!changed) {
                return;
            }
            if (previous == null) {
                DIALECT.remove();
            } else {
                DIALECT.set(previous);
            }
        }
    }
}
