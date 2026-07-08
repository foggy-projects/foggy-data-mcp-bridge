package com.foggyframework.dataset.db.model.engine.compose.compilation;

import java.util.Locale;

final class ComposePaginationRenderer {

    private ComposePaginationRenderer() {
    }

    static boolean isSqlServerDialect(String dialect) {
        String normalized = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return "mssql".equals(normalized) || "sqlserver".equals(normalized);
    }

    static Integer topLimit(String dialect, Integer limit, Integer offset) {
        if (!isSqlServerDialect(dialect) || offset != null) {
            return null;
        }
        return limit;
    }

    static void appendPagination(
            StringBuilder sql,
            String dialect,
            Integer limit,
            Integer offset,
            boolean hasOrderBy,
            String errorPhase,
            String contextDescription
    ) {
        if (isSqlServerDialect(dialect)) {
            appendSqlServerPagination(sql, limit, offset, hasOrderBy, errorPhase, contextDescription);
            return;
        }
        appendLimitOffset(sql, limit, offset);
    }

    private static void appendLimitOffset(StringBuilder sql, Integer limit, Integer offset) {
        if (limit != null) {
            if (offset != null) {
                sql.append("\nLIMIT ").append(limit).append(" OFFSET ").append(offset);
            } else {
                sql.append("\nLIMIT ").append(limit);
            }
        } else if (offset != null) {
            sql.append("\nOFFSET ").append(offset);
        }
    }

    private static void appendSqlServerPagination(
            StringBuilder sql,
            Integer limit,
            Integer offset,
            boolean hasOrderBy,
            String errorPhase,
            String contextDescription
    ) {
        if (offset == null) {
            return;
        }
        if (!hasOrderBy) {
            throw new ComposeCompileException(
                    ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    errorPhase,
                    "SQL Server OFFSET pagination requires an ORDER BY clause in "
                            + contextDescription + ".");
        }
        sql.append("\nOFFSET ").append(offset).append(" ROWS");
        if (limit != null) {
            sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
        }
    }
}
