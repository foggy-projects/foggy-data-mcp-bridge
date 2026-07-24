package com.foggyframework.dataset.model.engine.compose.capability;

import java.util.Collections;
import java.util.List;

/**
 * Structured return type for {@code sql_scalar} renderers.
 *
 * <p>Renderers MUST return {@code CapabilitySqlFragment} — never a raw SQL string.
 * This enforces parameterized SQL and prevents user-input concatenation
 * at the type level.</p>
 *
 * <p>Named {@code CapabilitySqlFragment} to avoid collision with the existing
 * engine {@link com.foggyframework.dataset.model.engine.expression.SqlFragment}
 * which tracks column references, type inference, and aggregation state.</p>
 *
 * <p>Mirrors Python {@code SqlFragment} from the capability module.</p>
 *
 * @since 8.4.0
 */
public final class CapabilitySqlFragment {

    private final String sql;
    private final List<Object> params;
    private final String returnType;

    public CapabilitySqlFragment(String sql, List<Object> params) {
        this(sql, params, "string");
    }

    public CapabilitySqlFragment(String sql, List<Object> params, String returnType) {
        if (sql == null) {
            throw new IllegalArgumentException("CapabilitySqlFragment.sql must not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("CapabilitySqlFragment.params must not be null");
        }
        this.sql = sql;
        this.params = Collections.unmodifiableList(List.copyOf(params));
        this.returnType = returnType == null ? "string" : returnType;
    }

    public String getSql()          { return sql; }
    public List<Object> getParams() { return params; }
    public String getReturnType()   { return returnType; }

    @Override
    public String toString() {
        return "CapabilitySqlFragment{sql='" + sql + "', params=" + params + "}";
    }
}
