package com.foggyframework.dataset.db.model.engine.expression;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 允许的函数白名单
 * <p>
 * 定义在计算字段表达式中允许使用的运算符和函数。
 * 用于安全验证，防止 SQL 注入和危险操作。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public final class AllowedFunctions {

    private AllowedFunctions() {
        // 工具类，禁止实例化
    }

    /**
     * 算术运算符
     */
    public static final Set<String> OPERATORS;

    /**
     * 比较运算符
     */
    public static final Set<String> COMPARISON_OPERATORS;

    /**
     * 逻辑运算符
     */
    public static final Set<String> LOGICAL_OPERATORS;

    /**
     * 数学函数
     */
    public static final Set<String> MATH_FUNCTIONS;

    /**
     * 日期函数
     */
    public static final Set<String> DATE_FUNCTIONS;

    /**
     * 字符串函数
     */
    public static final Set<String> STRING_FUNCTIONS;

    /**
     * 其他函数
     */
    public static final Set<String> OTHER_FUNCTIONS;

    /**
     * 聚合函数（需要配合 groupBy 使用）
     */
    public static final Set<String> AGGREGATE_FUNCTIONS;

    /**
     * 所有允许的函数（包含聚合函数）
     */
    public static final Set<String> ALL_ALLOWED;

    /**
     * 所有允许的函数（包含聚合函数，兼容旧名称）
     */
    public static final Set<String> ALL_ALLOWED_WITH_AGGREGATE;

    static {
        Set<String> operators = new HashSet<>();
        operators.add("+");
        operators.add("-");
        operators.add("*");
        operators.add("/");
        operators.add("%");
        OPERATORS = Collections.unmodifiableSet(operators);

        Set<String> comparison = new HashSet<>();
        comparison.add("==");
        comparison.add("===");
        comparison.add("!=");
        comparison.add("!==");
        comparison.add(">");
        comparison.add("<");
        comparison.add(">=");
        comparison.add("<=");
        COMPARISON_OPERATORS = Collections.unmodifiableSet(comparison);

        Set<String> logical = new HashSet<>();
        logical.add("&&");
        logical.add("||");
        logical.add("!");
        logical.add("AND");
        logical.add("OR");
        logical.add("NOT");
        LOGICAL_OPERATORS = Collections.unmodifiableSet(logical);

        Set<String> math = new HashSet<>();
        math.add("ABS");
        math.add("ROUND");
        math.add("CEIL");
        math.add("CEILING");
        math.add("FLOOR");
        math.add("MOD");
        math.add("POWER");
        math.add("POW");
        math.add("SQRT");
        math.add("SIGN");
        math.add("TRUNCATE");
        math.add("TRUNC");
        MATH_FUNCTIONS = Collections.unmodifiableSet(math);

        Set<String> date = new HashSet<>();
        date.add("YEAR");
        date.add("MONTH");
        date.add("DAY");
        date.add("HOUR");
        date.add("MINUTE");
        date.add("SECOND");
        date.add("DATE");
        date.add("TIME");
        date.add("NOW");
        date.add("CURRENT_DATE");
        date.add("CURRENT_TIME");
        date.add("CURRENT_TIMESTAMP");
        date.add("DATE_ADD");
        date.add("DATE_SUB");
        date.add("DATEDIFF");
        date.add("TIMESTAMPDIFF");
        date.add("DATE_FORMAT");
        date.add("STR_TO_DATE");
        date.add("EXTRACT");
        DATE_FUNCTIONS = Collections.unmodifiableSet(date);

        Set<String> string = new HashSet<>();
        string.add("CONCAT");
        string.add("CONCAT_WS");
        string.add("SUBSTRING");
        string.add("SUBSTR");
        string.add("LEFT");
        string.add("RIGHT");
        string.add("UPPER");
        string.add("LOWER");
        string.add("TRIM");
        string.add("LTRIM");
        string.add("RTRIM");
        string.add("LENGTH");
        string.add("CHAR_LENGTH");
        string.add("REPLACE");
        string.add("INSTR");
        string.add("LOCATE");
        string.add("LPAD");
        string.add("RPAD");
        STRING_FUNCTIONS = Collections.unmodifiableSet(string);

        Set<String> other = new HashSet<>();
        other.add("COALESCE");
        other.add("NULLIF");
        other.add("IFNULL");
        other.add("NVL");
        other.add("ISNULL");
        other.add("IF");
        other.add("CASE");
        other.add("CAST");
        other.add("CONVERT");
        OTHER_FUNCTIONS = Collections.unmodifiableSet(other);

        // 合并所有允许的函数（包含聚合函数）
        Set<String> all = new HashSet<>();
        all.addAll(OPERATORS);
        all.addAll(COMPARISON_OPERATORS);
        all.addAll(LOGICAL_OPERATORS);
        all.addAll(MATH_FUNCTIONS);
        all.addAll(DATE_FUNCTIONS);
        all.addAll(STRING_FUNCTIONS);
        all.addAll(OTHER_FUNCTIONS);

        // 聚合函数（需要配合 groupBy/autoGroupBy 使用）
        Set<String> aggregate = new HashSet<>();
        aggregate.add("SUM");
        aggregate.add("AVG");
        aggregate.add("COUNT");
        aggregate.add("MAX");
        aggregate.add("MIN");
        aggregate.add("GROUP_CONCAT");
        AGGREGATE_FUNCTIONS = Collections.unmodifiableSet(aggregate);

        // 直接加入聚合函数
        all.addAll(AGGREGATE_FUNCTIONS);
        ALL_ALLOWED = Collections.unmodifiableSet(all);

        ALL_ALLOWED_WITH_AGGREGATE = ALL_ALLOWED;  // 保持兼容
    }

    /**
     * 检查函数是否允许
     *
     * @param funcName 函数名（不区分大小写）
     * @return 是否允许
     */
    public static boolean isAllowed(String funcName) {
        if (funcName == null) {
            return false;
        }
        return ALL_ALLOWED.contains(funcName.toUpperCase());
    }

    /**
     * 检查是否是运算符
     */
    public static boolean isOperator(String name) {
        return OPERATORS.contains(name) || COMPARISON_OPERATORS.contains(name);
    }

    /**
     * 检查是否是逻辑运算符
     */
    public static boolean isLogicalOperator(String name) {
        if (name == null) {
            return false;
        }
        return LOGICAL_OPERATORS.contains(name.toUpperCase());
    }

    /**
     * 检查是否是聚合函数
     *
     * @param funcName 函数名（不区分大小写）
     * @return 是否是聚合函数
     */
    public static boolean isAggregateFunction(String funcName) {
        if (funcName == null) {
            return false;
        }
        return AGGREGATE_FUNCTIONS.contains(funcName.toUpperCase());
    }

    /**
     * 将 FSScript 运算符转换为 SQL 运算符
     */
    public static String toSqlOperator(String fsOperator) {
        switch (fsOperator) {
            case "==":
            case "===":
                return "=";
            case "!=":
            case "!==":
                return "<>";
            case "&&":
                return "AND";
            case "||":
                return "OR";
            case "!":
                return "NOT";
            default:
                return fsOperator;
        }
    }
}
