package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryColumn;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SQL 片段
 * <p>
 * 封装生成的 SQL 表达式及其引用的列信息。
 * 通过 referencedColumns 追踪依赖，用于自动 JOIN 分析和计算字段链式依赖。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
public class SqlFragment {

    /**
     * SQL 片段字符串
     */
    private String sql;

    /**
     * 引用的列（包括普通列、formulaDef 列、计算字段）
     * 使用 LinkedHashSet 保持插入顺序
     */
    private Set<DbQueryColumn> referencedColumns = new LinkedHashSet<>();

    /**
     * 推断的列类型
     */
    private DbColumnType inferredType = DbColumnType.UNKNOWN;

    /**
     * 是否包含聚合函数
     * <p>
     * 当表达式中包含 SUM, AVG, COUNT, MAX, MIN 等聚合函数时为 true。
     * 用于 autoGroupBy 判断。
     * </p>
     */
    private boolean hasAggregate = false;

    /**
     * 聚合函数类型（如果是单一顶层聚合）
     * <p>
     * 例如 "sum(a)" → "SUM"
     * 复合表达式如 "sum(a) + count(*)" 时为 null
     * </p>
     */
    private String aggregationType;

    /**
     * 创建字面量片段（数字、字符串等）
     */
    public static SqlFragment ofLiteral(String literal) {
        SqlFragment f = new SqlFragment();
        f.sql = literal;
        f.inferredType = inferLiteralType(literal);
        return f;
    }

    /**
     * 创建带类型的字面量片段
     */
    public static SqlFragment ofLiteral(String literal, DbColumnType type) {
        SqlFragment f = new SqlFragment();
        f.sql = literal;
        f.inferredType = type;
        return f;
    }

    /**
     * 从列创建片段
     *
     * @param column     列对象
     * @param sqlDeclare 列的 SQL 声明（通过 getDeclare() 获取）
     */
    public static SqlFragment ofColumn(DbQueryColumn column, String sqlDeclare) {
        SqlFragment f = new SqlFragment();
        f.sql = sqlDeclare;
        f.referencedColumns.add(column);
        // 从列继承类型
        f.inferredType = inferColumnType(column);
        return f;
    }

    /**
     * 创建二元运算片段
     *
     * @param left     左操作数
     * @param operator 运算符 (+, -, *, /, %, =, <>, etc.)
     * @param right    右操作数
     */
    public static SqlFragment binary(SqlFragment left, String operator, SqlFragment right) {
        SqlFragment f = new SqlFragment();
        f.sql = "(" + left.sql + " " + operator + " " + right.sql + ")";
        f.referencedColumns.addAll(left.referencedColumns);
        f.referencedColumns.addAll(right.referencedColumns);
        // 推断二元运算结果类型
        f.inferredType = inferBinaryType(left.inferredType, operator, right.inferredType);
        // 继承聚合状态
        f.hasAggregate = left.hasAggregate || right.hasAggregate;
        // 复合聚合表达式不设置单一聚合类型
        return f;
    }

    /**
     * 创建一元运算片段
     *
     * @param operator 运算符 (-, NOT, etc.)
     * @param operand  操作数
     */
    public static SqlFragment unary(String operator, SqlFragment operand) {
        SqlFragment f = new SqlFragment();
        f.sql = "(" + operator + " " + operand.sql + ")";
        f.referencedColumns.addAll(operand.referencedColumns);
        // 一元运算通常保持操作数类型，NOT 除外返回布尔
        f.inferredType = "NOT".equalsIgnoreCase(operator) ? DbColumnType.BOOL : operand.inferredType;
        // 继承聚合状态
        f.hasAggregate = operand.hasAggregate;
        f.aggregationType = operand.aggregationType;
        return f;
    }

    /**
     * 创建函数调用片段
     *
     * @param funcName 函数名
     * @param args     参数列表
     */
    public static SqlFragment function(String funcName, List<SqlFragment> args) {
        SqlFragment f = new SqlFragment();
        String argsStr = args.stream()
                .map(SqlFragment::getSql)
                .collect(Collectors.joining(", "));
        f.sql = funcName + "(" + argsStr + ")";
        args.forEach(arg -> f.referencedColumns.addAll(arg.referencedColumns));
        // 推断函数返回类型
        f.inferredType = inferFunctionType(funcName, args);

        // 检测聚合函数
        String upperFuncName = funcName.toUpperCase();
        if (AllowedFunctions.isAggregateFunction(upperFuncName)) {
            f.hasAggregate = true;
            f.aggregationType = upperFuncName;
        } else {
            // 继承子表达式的聚合状态
            f.hasAggregate = args.stream().anyMatch(SqlFragment::isHasAggregate);
            // 复合聚合时不设置单一类型
        }

        return f;
    }

    /**
     * 创建带格式的函数调用片段（用于特殊函数如 CAST）
     *
     * @param template SQL 模板，使用 {0}, {1} 作为占位符
     * @param args     参数列表
     */
    public static SqlFragment template(String template, List<SqlFragment> args) {
        SqlFragment f = new SqlFragment();
        String result = template;
        for (int i = 0; i < args.size(); i++) {
            result = result.replace("{" + i + "}", args.get(i).getSql());
        }
        f.sql = result;
        args.forEach(arg -> f.referencedColumns.addAll(arg.referencedColumns));
        // 模板类型默认继承第一个参数的类型
        f.inferredType = args.isEmpty() ? DbColumnType.UNKNOWN : args.get(0).inferredType;
        // 继承聚合状态
        f.hasAggregate = args.stream().anyMatch(SqlFragment::isHasAggregate);
        return f;
    }

    /**
     * 合并另一个片段的引用
     */
    public void mergeReferences(SqlFragment other) {
        if (other != null && other.referencedColumns != null) {
            this.referencedColumns.addAll(other.referencedColumns);
        }
    }

    /**
     * 添加列引用
     */
    public void addReference(DbQueryColumn column) {
        if (column != null) {
            this.referencedColumns.add(column);
        }
    }

    // ==========================================
    // 类型推断辅助方法
    // ==========================================

    /**
     * 推断字面量类型
     */
    private static DbColumnType inferLiteralType(String literal) {
        if (literal == null || literal.isEmpty()) {
            return DbColumnType.UNKNOWN;
        }
        // 字符串字面量
        if (literal.startsWith("'") && literal.endsWith("'")) {
            return DbColumnType.TEXT;
        }
        // 布尔字面量
        if ("true".equalsIgnoreCase(literal) || "false".equalsIgnoreCase(literal)) {
            return DbColumnType.BOOL;
        }
        // 数值字面量
        try {
            if (literal.contains(".")) {
                Double.parseDouble(literal);
                return DbColumnType.NUMBER;
            } else {
                Long.parseLong(literal);
                return DbColumnType.INTEGER;
            }
        } catch (NumberFormatException e) {
            return DbColumnType.UNKNOWN;
        }
    }

    /**
     * 从列推断类型
     */
    private static DbColumnType inferColumnType(DbQueryColumn column) {
        if (column == null) {
            return DbColumnType.UNKNOWN;
        }
        // 如果列有类型信息
        DbColumnType type = column.getSelectColumn() != null ? column.getSelectColumn().getType() : null;
        if (type != null) {
            return type;
        }
        // 如果有 SqlColumn，从 jdbcType 推断
        if (column.getSelectColumn() != null && column.getSelectColumn().getSqlColumn() != null) {
            int jdbcType = column.getSelectColumn().getSqlColumn().getJdbcType();
            return DbColumnType.fromJdbcType(jdbcType);
        }
        return DbColumnType.UNKNOWN;
    }

    /**
     * 推断二元运算结果类型
     */
    private static DbColumnType inferBinaryType(DbColumnType left, String operator, DbColumnType right) {
        // 比较运算符返回布尔
        if (isComparisonOperator(operator)) {
            return DbColumnType.BOOL;
        }
        // 逻辑运算符返回布尔
        if (isLogicalOperator(operator)) {
            return DbColumnType.BOOL;
        }
        // 算术运算符：如果任一操作数是 NUMBER，结果是 NUMBER
        if (isArithmeticOperator(operator)) {
            if (left == DbColumnType.NUMBER || right == DbColumnType.NUMBER ||
                left == DbColumnType.MONEY || right == DbColumnType.MONEY) {
                return DbColumnType.NUMBER;
            }
            if (left == DbColumnType.INTEGER && right == DbColumnType.INTEGER) {
                // 除法结果可能是小数
                return "/".equals(operator) ? DbColumnType.NUMBER : DbColumnType.INTEGER;
            }
            // 默认算术结果为数值
            return DbColumnType.NUMBER;
        }
        // 字符串连接 (|| 在某些数据库中)
        if ("||".equals(operator)) {
            return DbColumnType.TEXT;
        }
        return DbColumnType.UNKNOWN;
    }

    /**
     * 推断函数返回类型
     */
    private static DbColumnType inferFunctionType(String funcName, List<SqlFragment> args) {
        String upperName = funcName.toUpperCase();

        // 数学函数 -> NUMBER
        if (AllowedFunctions.MATH_FUNCTIONS.contains(upperName)) {
            return DbColumnType.NUMBER;
        }

        // 日期提取函数 -> INTEGER (YEAR, MONTH, DAY, etc.)
        if ("YEAR".equals(upperName) || "MONTH".equals(upperName) || "DAY".equals(upperName) ||
            "HOUR".equals(upperName) || "MINUTE".equals(upperName) || "SECOND".equals(upperName)) {
            return DbColumnType.INTEGER;
        }

        // 日期函数 -> DATETIME
        if ("DATE".equals(upperName) || "NOW".equals(upperName) ||
            "CURRENT_DATE".equals(upperName) || "CURRENT_TIMESTAMP".equals(upperName)) {
            return DbColumnType.DATETIME;
        }

        // 字符串函数 -> TEXT
        if (AllowedFunctions.STRING_FUNCTIONS.contains(upperName)) {
            // LENGTH 返回整数
            if ("LENGTH".equals(upperName) || "CHAR_LENGTH".equals(upperName) ||
                "INSTR".equals(upperName) || "LOCATE".equals(upperName)) {
                return DbColumnType.INTEGER;
            }
            return DbColumnType.TEXT;
        }

        // COALESCE/IFNULL/NVL -> 继承第一个非空参数的类型
        if ("COALESCE".equals(upperName) || "IFNULL".equals(upperName) ||
            "NVL".equals(upperName) || "ISNULL".equals(upperName)) {
            return args.isEmpty() ? DbColumnType.UNKNOWN : args.get(0).inferredType;
        }

        // IF 函数 -> 继承 then 分支的类型（第二个参数）
        if ("IF".equals(upperName) && args.size() >= 2) {
            return args.get(1).inferredType;
        }

        // COUNT -> INTEGER
        if ("COUNT".equals(upperName)) {
            return DbColumnType.INTEGER;
        }

        // SUM/AVG -> NUMBER
        if ("SUM".equals(upperName) || "AVG".equals(upperName)) {
            return DbColumnType.NUMBER;
        }

        // MIN/MAX -> 继承参数类型
        if ("MIN".equals(upperName) || "MAX".equals(upperName)) {
            return args.isEmpty() ? DbColumnType.UNKNOWN : args.get(0).inferredType;
        }

        return DbColumnType.UNKNOWN;
    }

    private static boolean isComparisonOperator(String op) {
        return "=".equals(op) || "<>".equals(op) || "!=".equals(op) ||
               ">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op);
    }

    private static boolean isLogicalOperator(String op) {
        return "AND".equalsIgnoreCase(op) || "OR".equalsIgnoreCase(op);
    }

    private static boolean isArithmeticOperator(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op);
    }

    @Override
    public String toString() {
        return sql;
    }
}
