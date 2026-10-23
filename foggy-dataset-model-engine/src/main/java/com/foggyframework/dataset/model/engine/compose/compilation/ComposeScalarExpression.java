package com.foggyframework.dataset.model.engine.compose.compilation;

import com.foggyframework.dataset.db.dialect.DateDifferenceSql;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.expression.AllowedFunctions;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;

import java.util.*;
import java.util.function.UnaryOperator;

/** Closed scalar grammar: dependencies and SQL come from the same parsed tree. */
public final class ComposeScalarExpression {
    private ComposeScalarExpression() {}

    public static List<String> references(String text) {
        Set<String> refs = new LinkedHashSet<>();
        render(text, null, name -> { refs.add(name); return name; });
        return List.copyOf(refs);
    }

    public static String render(String text, String dialect, UnaryOperator<String> columns) {
        try {
            String bare = text.trim();
            if (Set.of("NULL", "TRUE", "FALSE").contains(bare.toUpperCase(Locale.ROOT))) return bare.toUpperCase(Locale.ROOT);
            // A whole identifier can be an already bound alias named SELECT/FROM/etc.
            // It is still resolved as a column and quoted by the caller, never emitted as SQL syntax.
            if (bare.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*")) return columns.apply(bare);
            // allowPartialParse=false: trailing SQL must never be silently discarded.
            return render(CCJSqlParserUtil.parseExpression(text, false,
                    parser -> parser.withSquareBracketQuotation(true)), dialect, columns);
        } catch (ComposeCompileException | DateDifferenceSql.InvalidCall e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Invalid scalar expression: " + text);
        }
    }

    private static String render(Expression e, String dialect, UnaryOperator<String> columns) {
        if (e instanceof Column c) {
            if (c.getTable() != null && c.getTable().getName() != null) throw invalid("Use a plan-qualified field reference, not a SQL table qualifier");
            return columns.apply(unquote(c.getColumnName()));
        }
        if (e instanceof StringValue literal && dialect != null && dialect.startsWith("mysql"))
            return literal.toString().replace("\\", "\\\\");
        if (e instanceof LongValue || e instanceof DoubleValue || e instanceof StringValue
                || e instanceof NullValue || e instanceof DateValue || e instanceof TimeValue || e instanceof TimestampValue) return e.toString();
        if (e instanceof SignedExpression s) return s.getSign() + render(s.getExpression(), dialect, columns);
        if (e instanceof NotExpression n) return "NOT " + render(n.getExpression(), dialect, columns);
        if (e instanceof LikeExpression like) {
            String operator = like.getLikeKeyWord().toString();
            if (!Set.of("LIKE", "ILIKE").contains(operator)) throw invalid("Unsupported pattern operator: " + operator);
            return render(like.getLeftExpression(), dialect, columns) + (like.isNot() ? " NOT " : " ")
                    + operator + (like.isUseBinary() ? " BINARY " : " ") + render(like.getRightExpression(), dialect, columns)
                    + (like.getEscape() == null ? "" : " ESCAPE " + render(like.getEscape(), dialect, columns));
        }
        if (e instanceof BinaryExpression b) {
            String op = b.getStringExpression().toUpperCase(Locale.ROOT);
            if (!Set.of("+", "-", "*", "/", "%", "=", "!=", "<>", "<", ">", "<=", ">=", "AND", "OR", "||", "LIKE", "NOT LIKE").contains(op)) throw invalid("Unsupported scalar operator: " + op);
            return render(b.getLeftExpression(), dialect, columns) + " " + op + " " + render(b.getRightExpression(), dialect, columns);
        }
        if (e instanceof ParenthesedExpressionList<?> list) {
            List<String> items = new ArrayList<>();
            for (Expression item : list) items.add(render(item, dialect, columns));
            return "(" + String.join(", ", items) + ")";
        }
        if (e instanceof Function f) {
            String name = f.getName().toUpperCase(Locale.ROOT);
            if (!AllowedFunctions.isAllowed(name) || f.getAttribute() != null || f.getNamedParameters() != null
                    || f.getOrderByElements() != null || f.getLimit() != null || f.getHavingClause() != null
                    || f.getNullHandling() != null || f.isUnique()) throw invalid("Unsupported scalar function: " + name);
            List<String> args = new ArrayList<>();
            if (f.getParameters() != null) for (Expression arg : f.getParameters()) {
                if (name.equals("TIMESTAMPDIFF") && args.isEmpty() && arg instanceof Column unit
                        && (unit.getTable() == null || unit.getTable().getName() == null)) args.add("'" + unit.getColumnName() + "'");
                else if (arg instanceof AllColumns && name.equals("COUNT")) args.add("*");
                else args.add(render(arg, dialect, columns));
            }
            if (f.isAllColumns() && args.isEmpty() && name.equals("COUNT")) args.add("*");
            if (DateDifferenceSql.supports(name) && dialect != null) return DateDifferenceSql.render(dialect(dialect), name, args);
            return name + "(" + (f.isDistinct() ? "DISTINCT " : "") + String.join(", ", args) + ")";
        }
        if (e instanceof AnalyticExpression a) {
            String name = a.getName().toUpperCase(Locale.ROOT);
            if ((!AllowedFunctions.isAllowed(name) && !AllowedFunctions.isWindowFunction(name))
                    || a.getKeep() != null || a.getFilterExpression() != null || a.getHavingClause() != null
                    || a.getLimit() != null || a.getWindowName() != null || a.getFuncOrderBy() != null
                    || a.getNullHandling() != null || a.isIgnoreNulls() || a.isUnique()
                    || !"OVER".equals(a.getType().name())) throw invalid("Unsupported window expression");
            List<String> args = new ArrayList<>();
            if (a.getExpression() != null) args.add(a.getExpression() instanceof AllColumns && name.equals("COUNT") ? "*" : render(a.getExpression(), dialect, columns));
            if (a.getOffset() != null) args.add(render(a.getOffset(), dialect, columns));
            if (a.getDefaultValue() != null) args.add(render(a.getDefaultValue(), dialect, columns));
            List<String> clauses = new ArrayList<>();
            if (a.getPartitionExpressionList() != null && !a.getPartitionExpressionList().isEmpty()) {
                List<String> fields = new ArrayList<>();
                for (Expression field : a.getPartitionExpressionList()) fields.add(render(field, dialect, columns));
                clauses.add("PARTITION BY " + String.join(", ", fields));
            }
            if (a.getOrderByElements() != null && !a.getOrderByElements().isEmpty()) {
                List<String> orders = new ArrayList<>();
                for (var order : a.getOrderByElements()) orders.add(render(order.getExpression(), dialect, columns)
                        + (order.isAscDescPresent() ? order.isAsc() ? " ASC" : " DESC" : "")
                        + (order.getNullOrdering() == null ? "" : " " + order.getNullOrdering().name().replace('_', ' ')));
                clauses.add("ORDER BY " + String.join(", ", orders));
            }
            if (a.getWindowElement() != null) {
                WindowElement frame = a.getWindowElement();
                String bounds = frame.getRange() == null ? windowOffset(frame.getOffset(), dialect, columns)
                        : "BETWEEN " + windowOffset(frame.getRange().getStart(), dialect, columns) + " AND " + windowOffset(frame.getRange().getEnd(), dialect, columns);
                clauses.add(frame.getType() + " " + bounds);
            }
            return name + "(" + (a.isDistinct() ? "DISTINCT " : "") + String.join(", ", args) + ") OVER (" + String.join(" ", clauses) + ")";
        }
        if (e instanceof CaseExpression c) {
            StringBuilder sql = new StringBuilder("CASE");
            if (c.getSwitchExpression() != null) sql.append(" ").append(render(c.getSwitchExpression(), dialect, columns));
            for (WhenClause w : c.getWhenClauses()) sql.append(" WHEN ").append(render(w.getWhenExpression(), dialect, columns)).append(" THEN ").append(render(w.getThenExpression(), dialect, columns));
            if (c.getElseExpression() != null) sql.append(" ELSE ").append(render(c.getElseExpression(), dialect, columns));
            return sql.append(" END").toString();
        }
        if (e instanceof IsNullExpression n) return render(n.getLeftExpression(), dialect, columns) + (n.isNot() ? " IS NOT NULL" : " IS NULL");
        if (e instanceof Between b) return render(b.getLeftExpression(), dialect, columns) + (b.isNot() ? " NOT BETWEEN " : " BETWEEN ") + render(b.getBetweenExpressionStart(), dialect, columns) + " AND " + render(b.getBetweenExpressionEnd(), dialect, columns);
        if (e instanceof InExpression in) return render(in.getLeftExpression(), dialect, columns) + (in.isNot() ? " NOT IN " : " IN ") + render(in.getRightExpression(), dialect, columns);
        if (e instanceof CastExpression cast) {
            String type = cast.getColDataType().toString();
            if (!type.matches("(?i)(DATE|TIME|TIMESTAMP|DATETIME2?|INTEGER|INT|BIGINT|DECIMAL|NUMERIC|VARCHAR|CHAR|TEXT|REAL|DOUBLE|FLOAT)(\\s*\\(\\d+(\\s*,\\s*\\d+)?\\))?")) throw invalid("Unsupported scalar cast type: " + type);
            return "CAST(" + render(cast.getLeftExpression(), dialect, columns) + " AS " + type + ")";
        }
        // In particular, SELECT/subquery, variables and unknown extension nodes fail closed.
        throw invalid("Unsupported scalar expression node: " + e.getClass().getSimpleName());
    }

    private static String windowOffset(WindowOffset offset, String dialect, UnaryOperator<String> columns) {
        if (offset == null) throw invalid("Missing window frame bound");
        if (offset.getExpression() != null) render(offset.getExpression(), dialect, columns);
        // Frame grammar is restricted to numeric offsets or CURRENT ROW / UNBOUNDED.
        String value = offset.toString().trim();
        if (!value.matches("(?i)(CURRENT ROW|UNBOUNDED (PRECEDING|FOLLOWING)|[0-9]+(\\.[0-9]+)? (PRECEDING|FOLLOWING))")) throw invalid("Unsupported window frame offset");
        return value;
    }

    public static String unquote(String name) {
        if (name.length() > 1 && ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("`") && name.endsWith("`")) || (name.startsWith("[") && name.endsWith("]")))) return name.substring(1, name.length() - 1);
        return name;
    }

    private static FDialect dialect(String name) {
        if (name.startsWith("mysql")) return FDialect.MYSQL_DIALECT;
        if (name.startsWith("postgres")) return FDialect.POSTGRES_DIALECT;
        if (name.equals("mssql") || name.equals("sqlserver")) return FDialect.SQLSERVER_DIALECT;
        return FDialect.SQLITE_DIALECT;
    }

    private static ComposeCompileException invalid(String message) {
        return new ComposeCompileException(ComposeCompileErrorCodes.UNSUPPORTED_PLAN_SHAPE,
                ComposeCompileErrorCodes.PHASE_PLAN_LOWER, "COMPOSE_SCALAR_EXPRESSION_UNSUPPORTED: " + message);
    }
}
