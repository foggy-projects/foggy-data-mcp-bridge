package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;

import java.util.List;

/**
 * 方言感知的 SQL 函数渲染 utility · v1.4 Step 3.4
 * <p>
 * 对 Formula Spec v1 的 {@code date_diff} / {@code date_add} / {@code now} 三函数做方言分派，
 * 从 {@link SqlExpContext#getDialect()} 取出当前方言的 SQL 构建方法，返回方言特定的
 * {@link SqlFragment}。
 * </p>
 * <p>
 * 此类不是 AST 节点，是 {@link SqlFunctionExp#evalValue} 在函数名匹配时委托的适配器。
 * 把"哪些函数走方言分派"与"具体方言怎么写"两个关注点分离。
 * </p>
 * <p>
 * 方言实现见 {@link FDialect#buildDateDiffExpression}、{@link FDialect#buildDateAddExpression}、
 * {@link FDialect#buildCurrentTimestampExpression}，各方言类按需 override。
 * </p>
 *
 * @since v1.4
 */
public final class DialectAwareFunctionExp {

    private DialectAwareFunctionExp() {
        // utility class
    }

    /**
     * 渲染 {@code date_diff(a, b)} · 返回方言特定的日期差值 SQL。
     *
     * @param ctx          表达式上下文（提供方言）
     * @param argFragments 已求值的参数片段 [a, b]
     * @return 方言特定 SqlFragment，若方言未 override 则 fallback 到 ANSI {@code DATEDIFF(a, b)}
     */
    public static SqlFragment renderDateDiff(SqlExpContext ctx, List<SqlFragment> argFragments) {
        if (argFragments.size() != 2) {
            throw new IllegalArgumentException(
                    "date_diff function requires exactly 2 arguments (a, b), got " + argFragments.size());
        }
        String aSql = argFragments.get(0).getSql();
        String bSql = argFragments.get(1).getSql();

        String dialectSql = null;
        if (ctx != null && ctx.getDialect() != null) {
            dialectSql = ctx.getDialect().buildDateDiffExpression(aSql, bSql);
        }
        if (dialectSql == null) {
            // Fallback：未 override 的方言走通用 DATEDIFF 函数名（多数方言可接受）
            dialectSql = "DATEDIFF(" + aSql + ", " + bSql + ")";
        }
        return SqlFragment.customFunction(dialectSql, "DATE_DIFF", argFragments);
    }

    /**
     * 渲染 {@code hours_between(start, end)} · 返回 end - start 的小数小时数。
     */
    public static SqlFragment renderHoursBetween(SqlExpContext ctx, List<SqlFragment> argFragments) {
        if (argFragments.size() != 2) {
            throw new IllegalArgumentException(
                    "hours_between function requires exactly 2 arguments (start, end), got " + argFragments.size());
        }
        String startSql = argFragments.get(0).getSql();
        String endSql = argFragments.get(1).getSql();

        String dialectSql = buildHoursBetweenSql(ctx, startSql, endSql);
        return SqlFragment.customFunction(dialectSql, "HOURS_BETWEEN", argFragments);
    }

    private static String buildHoursBetweenSql(SqlExpContext ctx, String startSql, String endSql) {
        String productName = ctx != null && ctx.getDialect() != null ? ctx.getDialect().getProductName() : null;
        if (productName == null) {
            return "(TIMESTAMPDIFF(SECOND, " + startSql + ", " + endSql + ") / 3600.0)";
        }
        return switch (productName.toUpperCase()) {
            case "SQLITE" -> "((julianday(" + endSql + ") - julianday(" + startSql + ")) * 24.0)";
            case "POSTGRESQL", "POSTGRES" -> "(EXTRACT(EPOCH FROM (" + endSql + " - " + startSql + ")) / 3600.0)";
            case "SQLSERVER", "SQL_SERVER", "MICROSOFT SQL SERVER" ->
                    "(DATEDIFF(second, " + startSql + ", " + endSql + ") / 3600.0)";
            default -> "(TIMESTAMPDIFF(SECOND, " + startSql + ", " + endSql + ") / 3600.0)";
        };
    }

    /**
     * 渲染 {@code date_add(d, n, unit)} · 返回方言特定的日期加法 SQL。
     * <p>
     * unit 必须是编译期字符串字面量（{@code 'day'} / {@code 'month'} / {@code 'year'}），
     * 否则抛 {@link IllegalArgumentException}（Spec v1 约束）。
     * </p>
     *
     * @param ctx          表达式上下文（提供方言）
     * @param argFragments 已求值的参数片段 [d, n, unit]
     * @return 方言特定 SqlFragment
     */
    public static SqlFragment renderDateAdd(SqlExpContext ctx, List<SqlFragment> argFragments) {
        if (argFragments.size() != 3) {
            throw new IllegalArgumentException(
                    "date_add function requires exactly 3 arguments (d, n, unit), got " + argFragments.size());
        }
        String dSql = argFragments.get(0).getSql();
        String nSql = argFragments.get(1).getSql();
        String unit = extractUnitLiteral(argFragments.get(2).getSql());

        String dialectSql = null;
        if (ctx != null && ctx.getDialect() != null) {
            dialectSql = ctx.getDialect().buildDateAddExpression(dSql, nSql, unit);
        }
        if (dialectSql == null) {
            // Fallback：ANSI-ish 形式（很多方言可以接受 DATE_ADD(d, INTERVAL ? UNIT)）
            dialectSql = "DATE_ADD(" + dSql + ", INTERVAL " + nSql + " " + unit.toUpperCase() + ")";
        }
        return SqlFragment.customFunction(dialectSql, "DATE_ADD", argFragments);
    }

    /**
     * 渲染 {@code now()} · 返回方言特定的当前时间戳 SQL。
     *
     * @param ctx          表达式上下文（提供方言）
     * @param argFragments 参数片段列表（必须为空）
     * @return 方言特定 SqlFragment
     */
    public static SqlFragment renderNow(SqlExpContext ctx, List<SqlFragment> argFragments) {
        if (!argFragments.isEmpty()) {
            throw new IllegalArgumentException(
                    "now() takes no arguments, got " + argFragments.size());
        }
        String sql;
        if (ctx != null && ctx.getDialect() != null) {
            sql = ctx.getDialect().buildCurrentTimestampExpression();
        } else {
            sql = "CURRENT_TIMESTAMP";
        }
        return SqlFragment.customFunction(sql, "NOW", argFragments);
    }

    /**
     * 从渲染后的字面量 SQL 中提取 unit 值。
     * <p>
     * {@link SqlLiteralExp} 把源码里的 {@code 'day'} 渲染成 SQL 字符串字面量 {@code 'day'}
     * （含引号）。这里剥离外层引号。
     * </p>
     *
     * @param literalSql 形如 {@code 'day'} / {@code "day"}
     * @return {@code day}（小写，调用方 dialect 再按需转换）
     * @throws IllegalArgumentException 非字符串字面量形式
     */
    private static String extractUnitLiteral(String literalSql) {
        if (literalSql == null) {
            throw new IllegalArgumentException(
                    "date_add unit argument must be a string literal ('day' / 'month' / 'year')");
        }
        String trimmed = literalSql.trim();
        if (trimmed.length() >= 2
                && trimmed.charAt(0) == '\''
                && trimmed.charAt(trimmed.length() - 1) == '\'') {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        throw new IllegalArgumentException(
                "date_add unit must be a quoted string literal 'day' / 'month' / 'year', got: " + literalSql);
    }
}
