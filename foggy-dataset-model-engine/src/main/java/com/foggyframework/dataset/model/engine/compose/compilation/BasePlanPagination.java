package com.foggyframework.dataset.model.engine.compose.compilation;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.Top;

/** Owns stage-local pagination after base filtering and result-stage lowering. */
final class BasePlanPagination {
    private BasePlanPagination() { }

    static String render(String sql, String dialect, Integer limit, Integer start) {
        boolean sqlServer = ComposePaginationRenderer.isSqlServerDialect(dialect);
        int offset = start == null ? 0 : start;
        if (limit == null && offset == 0 && !sqlServer) return sql;
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql, p -> p.withSquareBracketQuotation(true));
            boolean ordered = select.getOrderByElements() != null && !select.getOrderByElements().isEmpty();
            if (select.getLimit() != null || select.getOffset() != null || select.getFetch() != null
                    || select instanceof PlainSelect plain && plain.getTop() != null) {
                throw new IllegalArgumentException("Base planning port returned an already paginated relation");
            }
            if (sqlServer) {
                if (limit != null && (limit == 0 || offset == 0 && !ordered)) {
                    if (!(select instanceof PlainSelect plain)) {
                        throw new IllegalArgumentException("TOP requires a plain base SELECT");
                    }
                    Top top = new Top();
                    top.setExpression(new LongValue(limit));
                    plain.setTop(top);
                    return plain.toString();
                }
                if (!ordered) {
                    if (offset > 0) throw new IllegalArgumentException("SQL Server start requires ORDER BY");
                    return sql;
                }
                // OFFSET 0 also makes an unbounded ordered base legal inside a derived table.
                return sql + " OFFSET " + offset + " ROWS"
                        + (limit == null ? "" : " FETCH NEXT " + limit + " ROWS ONLY");
            }
            String bound = limit == null
                    ? ("sqlite".equals(dialect) ? "-1" : dialect.startsWith("mysql") ? "18446744073709551615" : null)
                    : limit.toString();
            return sql + (bound == null ? "" : " LIMIT " + bound)
                    + (offset == 0 ? "" : " OFFSET " + offset);
        } catch (Exception e) {
            throw new ComposeCompileException(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                    ComposeCompileErrorCodes.PHASE_PLAN_LOWER,
                    "Cannot preserve base plan pagination for " + dialect + ": " + e.getMessage(), e);
        }
    }
}
