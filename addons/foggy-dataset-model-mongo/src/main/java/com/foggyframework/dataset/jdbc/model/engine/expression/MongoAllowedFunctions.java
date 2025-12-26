package com.foggyframework.dataset.jdbc.model.engine.expression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MongoDB 允许的函数和操作符
 * <p>
 * 定义在计算字段表达式中允许使用的 MongoDB 聚合操作符。
 * 提供从 FSScript/SQL 函数名到 MongoDB 操作符的映射。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public final class MongoAllowedFunctions {

    private MongoAllowedFunctions() {
    }

    /**
     * 算术操作符映射: FSScript -> MongoDB
     */
    public static final Map<String, String> ARITHMETIC_OPERATORS = new HashMap<>();

    static {
        ARITHMETIC_OPERATORS.put("+", "$add");
        ARITHMETIC_OPERATORS.put("-", "$subtract");
        ARITHMETIC_OPERATORS.put("*", "$multiply");
        ARITHMETIC_OPERATORS.put("/", "$divide");
        ARITHMETIC_OPERATORS.put("%", "$mod");
    }

    /**
     * 比较操作符映射: FSScript -> MongoDB
     */
    public static final Map<String, String> COMPARISON_OPERATORS = new HashMap<>();

    static {
        COMPARISON_OPERATORS.put("==", "$eq");
        COMPARISON_OPERATORS.put("===", "$eq");
        COMPARISON_OPERATORS.put("!=", "$ne");
        COMPARISON_OPERATORS.put("!==", "$ne");
        COMPARISON_OPERATORS.put(">", "$gt");
        COMPARISON_OPERATORS.put(">=", "$gte");
        COMPARISON_OPERATORS.put("<", "$lt");
        COMPARISON_OPERATORS.put("<=", "$lte");
    }

    /**
     * 逻辑操作符映射: FSScript -> MongoDB
     */
    public static final Map<String, String> LOGICAL_OPERATORS = new HashMap<>();

    static {
        LOGICAL_OPERATORS.put("&&", "$and");
        LOGICAL_OPERATORS.put("||", "$or");
        LOGICAL_OPERATORS.put("!", "$not");
        LOGICAL_OPERATORS.put("AND", "$and");
        LOGICAL_OPERATORS.put("OR", "$or");
        LOGICAL_OPERATORS.put("NOT", "$not");
    }

    /**
     * 数学函数映射: SQL函数名 -> MongoDB操作符
     */
    public static final Map<String, String> MATH_FUNCTIONS = new HashMap<>();

    static {
        MATH_FUNCTIONS.put("ABS", "$abs");
        MATH_FUNCTIONS.put("CEIL", "$ceil");
        MATH_FUNCTIONS.put("CEILING", "$ceil");
        MATH_FUNCTIONS.put("FLOOR", "$floor");
        MATH_FUNCTIONS.put("ROUND", "$round");
        MATH_FUNCTIONS.put("SQRT", "$sqrt");
        MATH_FUNCTIONS.put("POW", "$pow");
        MATH_FUNCTIONS.put("POWER", "$pow");
        MATH_FUNCTIONS.put("MOD", "$mod");
        MATH_FUNCTIONS.put("TRUNC", "$trunc");
        MATH_FUNCTIONS.put("TRUNCATE", "$trunc");
    }

    /**
     * 日期函数映射: SQL函数名 -> MongoDB操作符
     */
    public static final Map<String, String> DATE_FUNCTIONS = new HashMap<>();

    static {
        DATE_FUNCTIONS.put("YEAR", "$year");
        DATE_FUNCTIONS.put("MONTH", "$month");
        DATE_FUNCTIONS.put("DAY", "$dayOfMonth");
        DATE_FUNCTIONS.put("DAYOFMONTH", "$dayOfMonth");
        DATE_FUNCTIONS.put("HOUR", "$hour");
        DATE_FUNCTIONS.put("MINUTE", "$minute");
        DATE_FUNCTIONS.put("SECOND", "$second");
        DATE_FUNCTIONS.put("DAYOFWEEK", "$dayOfWeek");
        DATE_FUNCTIONS.put("DAYOFYEAR", "$dayOfYear");
        DATE_FUNCTIONS.put("WEEK", "$week");
    }

    /**
     * 字符串函数映射: SQL函数名 -> MongoDB操作符
     */
    public static final Map<String, String> STRING_FUNCTIONS = new HashMap<>();

    static {
        STRING_FUNCTIONS.put("CONCAT", "$concat");
        STRING_FUNCTIONS.put("SUBSTRING", "$substrCP");
        STRING_FUNCTIONS.put("SUBSTR", "$substrCP");
        STRING_FUNCTIONS.put("UPPER", "$toUpper");
        STRING_FUNCTIONS.put("LOWER", "$toLower");
        STRING_FUNCTIONS.put("TRIM", "$trim");
        STRING_FUNCTIONS.put("LTRIM", "$ltrim");
        STRING_FUNCTIONS.put("RTRIM", "$rtrim");
        STRING_FUNCTIONS.put("LENGTH", "$strLenCP");
        STRING_FUNCTIONS.put("CHAR_LENGTH", "$strLenCP");
        STRING_FUNCTIONS.put("REPLACE", "$replaceAll");
    }

    /**
     * 其他函数映射
     */
    public static final Map<String, String> OTHER_FUNCTIONS = new HashMap<>();

    static {
        OTHER_FUNCTIONS.put("COALESCE", "$ifNull");
        OTHER_FUNCTIONS.put("IFNULL", "$ifNull");
        OTHER_FUNCTIONS.put("NVL", "$ifNull");
        OTHER_FUNCTIONS.put("IF", "$cond");
    }

    /**
     * 聚合函数映射
     */
    public static final Map<String, String> AGGREGATE_FUNCTIONS = new HashMap<>();

    static {
        AGGREGATE_FUNCTIONS.put("SUM", "$sum");
        AGGREGATE_FUNCTIONS.put("AVG", "$avg");
        AGGREGATE_FUNCTIONS.put("COUNT", "$sum"); // MongoDB 中 count 在 $group 中用 $sum: 1
        AGGREGATE_FUNCTIONS.put("MAX", "$max");
        AGGREGATE_FUNCTIONS.put("MIN", "$min");
    }

    /**
     * 所有允许的函数名（SQL 风格，大写）
     */
    public static final Set<String> ALLOWED_FUNCTIONS = new HashSet<>();

    static {
        ALLOWED_FUNCTIONS.addAll(MATH_FUNCTIONS.keySet());
        ALLOWED_FUNCTIONS.addAll(DATE_FUNCTIONS.keySet());
        ALLOWED_FUNCTIONS.addAll(STRING_FUNCTIONS.keySet());
        ALLOWED_FUNCTIONS.addAll(OTHER_FUNCTIONS.keySet());
        ALLOWED_FUNCTIONS.addAll(AGGREGATE_FUNCTIONS.keySet());
    }

    /**
     * 检查函数是否允许
     *
     * @param funcName 函数名（大小写不敏感）
     * @return 是否允许
     */
    public static boolean isAllowed(String funcName) {
        if (funcName == null) {
            return false;
        }
        return ALLOWED_FUNCTIONS.contains(funcName.toUpperCase());
    }

    /**
     * 检查是否是聚合函数
     *
     * @param funcName 函数名（可以是 MongoDB 操作符格式或 SQL 格式）
     * @return 是否是聚合函数
     */
    public static boolean isAggregateFunction(String funcName) {
        if (funcName == null) {
            return false;
        }
        String upper = funcName.toUpperCase().replace("$", "");
        return AGGREGATE_FUNCTIONS.containsKey(upper);
    }

    /**
     * 获取 MongoDB 操作符
     *
     * @param funcName SQL 函数名
     * @return MongoDB 操作符，如果不支持返回 null
     */
    public static String getMongoOperator(String funcName) {
        if (funcName == null) {
            return null;
        }
        String upper = funcName.toUpperCase();

        // 按优先级查找
        String result = MATH_FUNCTIONS.get(upper);
        if (result != null) return result;

        result = DATE_FUNCTIONS.get(upper);
        if (result != null) return result;

        result = STRING_FUNCTIONS.get(upper);
        if (result != null) return result;

        result = OTHER_FUNCTIONS.get(upper);
        if (result != null) return result;

        result = AGGREGATE_FUNCTIONS.get(upper);
        return result;
    }

    /**
     * 获取算术运算符的 MongoDB 操作符
     *
     * @param operator FSScript 运算符
     * @return MongoDB 操作符
     */
    public static String getArithmeticOperator(String operator) {
        return ARITHMETIC_OPERATORS.get(operator);
    }

    /**
     * 获取比较运算符的 MongoDB 操作符
     *
     * @param operator FSScript 运算符
     * @return MongoDB 操作符
     */
    public static String getComparisonOperator(String operator) {
        return COMPARISON_OPERATORS.get(operator);
    }

    /**
     * 获取逻辑运算符的 MongoDB 操作符
     *
     * @param operator FSScript 运算符
     * @return MongoDB 操作符
     */
    public static String getLogicalOperator(String operator) {
        String upper = operator.toUpperCase();
        return LOGICAL_OPERATORS.get(upper);
    }

    /**
     * 检查是否是算术运算符
     */
    public static boolean isArithmeticOperator(String operator) {
        return ARITHMETIC_OPERATORS.containsKey(operator);
    }

    /**
     * 检查是否是比较运算符
     */
    public static boolean isComparisonOperator(String operator) {
        return COMPARISON_OPERATORS.containsKey(operator);
    }

    /**
     * 检查是否是逻辑运算符
     */
    public static boolean isLogicalOperator(String operator) {
        return LOGICAL_OPERATORS.containsKey(operator) ||
               LOGICAL_OPERATORS.containsKey(operator.toUpperCase());
    }
}
