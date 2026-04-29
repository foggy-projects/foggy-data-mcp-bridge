package com.foggyframework.dataset.db.model.engine.compose.relation;

import java.util.Objects;

/**
 * Capability flags for a {@link CompiledRelation}.
 *
 * @since 8.5.0.beta (S7a)
 */
public final class RelationCapabilities {

    private final boolean canInlineAsSubquery;
    private final boolean canHoistCte;
    private final boolean containsWithItems;
    private final boolean supportsOuterAggregate;
    private final boolean supportsOuterWindow;
    private final boolean requiresTopLevelWith;
    private final String relationWrapStrategy;

    private RelationCapabilities(Builder b) {
        this.canInlineAsSubquery = b.canInlineAsSubquery;
        this.canHoistCte = b.canHoistCte;
        this.containsWithItems = b.containsWithItems;
        this.supportsOuterAggregate = b.supportsOuterAggregate;
        this.supportsOuterWindow = b.supportsOuterWindow;
        this.requiresTopLevelWith = b.requiresTopLevelWith;
        this.relationWrapStrategy = b.relationWrapStrategy != null
                ? b.relationWrapStrategy
                : RelationWrapStrategy.FAIL_CLOSED;
    }

    public boolean canInlineAsSubquery() { return canInlineAsSubquery; }
    public boolean canHoistCte() { return canHoistCte; }
    public boolean containsWithItems() { return containsWithItems; }
    public boolean supportsOuterAggregate() { return supportsOuterAggregate; }
    public boolean supportsOuterWindow() { return supportsOuterWindow; }
    public boolean requiresTopLevelWith() { return requiresTopLevelWith; }
    public String relationWrapStrategy() { return relationWrapStrategy; }

    public static Builder builder() { return new Builder(); }

    /**
     * Determine the appropriate capabilities for a relation given a dialect
     * and whether the relation contains CTE items.
     */
    public static RelationCapabilities forDialect(String dialect, boolean hasWithItems) {
        String dl = dialect == null ? "mysql" : dialect.toLowerCase(java.util.Locale.ROOT);
        boolean cteCapable = "mysql8".equals(dl) || "postgres".equals(dl)
                || "postgresql".equals(dl) || "sqlite".equals(dl);
        boolean isSqlServer = "mssql".equals(dl) || "sqlserver".equals(dl);
        boolean isMysql57 = "mysql".equals(dl) || "mysql57".equals(dl);

        if (!hasWithItems) {
            return builder()
                    .canInlineAsSubquery(true)
                    .canHoistCte(cteCapable || isSqlServer)
                    .containsWithItems(false)
                    .supportsOuterAggregate(true)
                    .supportsOuterWindow(false)
                    .requiresTopLevelWith(false)
                    .relationWrapStrategy(RelationWrapStrategy.INLINE_SUBQUERY)
                    .build();
        }

        if (isMysql57) {
            return builder()
                    .canInlineAsSubquery(false).canHoistCte(false)
                    .containsWithItems(true)
                    .supportsOuterAggregate(false).supportsOuterWindow(false)
                    .requiresTopLevelWith(false)
                    .relationWrapStrategy(RelationWrapStrategy.FAIL_CLOSED)
                    .build();
        }

        if (isSqlServer) {
            return builder()
                    .canInlineAsSubquery(false).canHoistCte(true)
                    .containsWithItems(true)
                    .supportsOuterAggregate(true).supportsOuterWindow(false)
                    .requiresTopLevelWith(true)
                    .relationWrapStrategy(RelationWrapStrategy.HOISTED_CTE)
                    .build();
        }

        return builder()
                .canInlineAsSubquery(false).canHoistCte(true)
                .containsWithItems(true)
                .supportsOuterAggregate(true).supportsOuterWindow(false)
                .requiresTopLevelWith(false)
                .relationWrapStrategy(RelationWrapStrategy.HOISTED_CTE)
                .build();
    }

    public static final class Builder {
        private boolean canInlineAsSubquery;
        private boolean canHoistCte;
        private boolean containsWithItems;
        private boolean supportsOuterAggregate;
        private boolean supportsOuterWindow;
        private boolean requiresTopLevelWith;
        private String relationWrapStrategy;

        public Builder canInlineAsSubquery(boolean v) { this.canInlineAsSubquery = v; return this; }
        public Builder canHoistCte(boolean v) { this.canHoistCte = v; return this; }
        public Builder containsWithItems(boolean v) { this.containsWithItems = v; return this; }
        public Builder supportsOuterAggregate(boolean v) { this.supportsOuterAggregate = v; return this; }
        public Builder supportsOuterWindow(boolean v) { this.supportsOuterWindow = v; return this; }
        public Builder requiresTopLevelWith(boolean v) { this.requiresTopLevelWith = v; return this; }
        public Builder relationWrapStrategy(String v) { this.relationWrapStrategy = v; return this; }
        public RelationCapabilities build() { return new RelationCapabilities(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationCapabilities c)) return false;
        return canInlineAsSubquery == c.canInlineAsSubquery
                && canHoistCte == c.canHoistCte
                && containsWithItems == c.containsWithItems
                && supportsOuterAggregate == c.supportsOuterAggregate
                && supportsOuterWindow == c.supportsOuterWindow
                && requiresTopLevelWith == c.requiresTopLevelWith
                && Objects.equals(relationWrapStrategy, c.relationWrapStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canInlineAsSubquery, canHoistCte, containsWithItems,
                supportsOuterAggregate, supportsOuterWindow,
                requiresTopLevelWith, relationWrapStrategy);
    }

    @Override
    public String toString() {
        return "RelationCapabilities{wrapStrategy=" + relationWrapStrategy
                + ", containsWithItems=" + containsWithItems
                + ", canInline=" + canInlineAsSubquery
                + ", canHoist=" + canHoistCte + "}";
    }
}
