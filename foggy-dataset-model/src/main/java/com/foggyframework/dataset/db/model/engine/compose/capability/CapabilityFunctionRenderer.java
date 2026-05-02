package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.util.Map;

/**
 * Functional interface for {@code sql_scalar} function renderers.
 *
 * <p>A renderer receives the compiled SQL fragments for each argument
 * (keyed by argument name from {@code argsSchema}), the current SQL
 * dialect name, and must return a {@link CapabilitySqlFragment}.</p>
 *
 * @since 8.4.0
 */
@FunctionalInterface
public interface CapabilityFunctionRenderer {

    /**
     * Render a sql_scalar function call into parameterized SQL.
     *
     * @param args    argument name → compiled SQL string
     * @param dialect SQL dialect name (e.g. "mysql", "postgres", "sqlserver", "sqlite")
     * @return a structured SQL fragment; never null
     */
    CapabilitySqlFragment render(Map<String, String> args, String dialect);
}
